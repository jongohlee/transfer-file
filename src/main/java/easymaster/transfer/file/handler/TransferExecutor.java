/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.handler;

import static easymaster.transfer.file.client.TransferClient.MAX_WORKERS;
import static easymaster.transfer.file.client.TransferClient.MIN_PARALLEL_CHUNK;
import static easymaster.transfer.file.client.TransferClient.THROWAWAY;
import static easymaster.transfer.file.protocol.TransferResponseCode.ALREADY_EXIST;
import static easymaster.transfer.file.protocol.TransferResponseCode.TRANSFER_FAILED;
import static easymaster.transfer.file.util.OptionParameter.AFTER_TRANSFER;
import static easymaster.transfer.file.util.OptionParameter.ON_EXIST;
import static easymaster.transfer.file.util.OptionParameter.SITE;
import static easymaster.transfer.file.util.OptionParameterValues.BACKUP;
import static easymaster.transfer.file.util.OptionParameterValues.DELETE;
import static easymaster.transfer.file.util.OptionParameterValues.FAIL_ONEXIST;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import easymaster.transfer.file.client.TransferClient;
import easymaster.transfer.file.config.TransferEnvironment;
import easymaster.transfer.file.config.TransferEnvironment.Site;
import easymaster.transfer.file.util.FileUtil;
import easymaster.transfer.file.util.OptionParameter;
import easymaster.transfer.file.util.TransferMessageUtil;
import io.netty.channel.Channel;

/**
 * 파일 전송을 처리하는 Executor
 * 수신 서버가 목록으로 전달된 경우 순차적으로 연결을 시도하여 전송 가능한 Agent Server에 전송한다.
 * 전송 Task가 목록으로 전달된 경우 ThreadPool을 생성하여 병렬로 파일을 전송을 처리한다.
 * @author Jongoh Lee
 *
 */

public class TransferExecutor
{
    private Logger logger= LoggerFactory.getLogger( TransferExecutor.class);

    private final List<Task> tasks;

    private final TransferEnvironment agentEnv;
    
    private Map<List<String>, List<Task>> group;

    public TransferExecutor( List<Task> tasks, TransferEnvironment environment)
    {
        this.tasks= tasks;
        this.agentEnv= environment;
    }

    public boolean prepare() throws Exception
    {
        // 파일을 전송할 수신 Agent Server별로 전송 대상을 정리한다. 
        this.group= tasks.stream().collect( groupingBy( Task::agentAddresses, toList()));
        logger.debug( "destination agent groups: {}", group);
        
        for( Map.Entry<List<String>, List<Task>> entry: group.entrySet())
        {
            List<String> agents= entry.getKey();
            List<Task> tasks= entry.getValue();
            final TransferClient client= tryConnect( agents, 1);
            if( client== null)
                throw new RequestHandlerException( TRANSFER_FAILED, "agent: "+ agents+ " are not responding");
            
            // 수신 Agent 서버에 파일이 이미 존재하고 FailOnExist Option인 경우 fast-fail 처리한다.
            for( Task task: tasks)
            {
                if( OptionParameter.contains( task.destOptions, ON_EXIST, FAIL_ONEXIST, true)
                        && client.requestResourceExist( THROWAWAY, task.destinationPath, OptionParameter.first( task.destOptions, SITE)))
                    throw new RequestHandlerException( ALREADY_EXIST, "target file["+ task.destinationPath+ "] is already exist");
            }
        }
        return true;
    }
    
    public Result transfer() throws Exception
    {
        Result result= new Result();
        ExecutorService executor= null;
        try
        {
            // Agent Server별로 접속하여 서버별 전송 파일을 전송한다.
            for( Map.Entry<List<String>, List<Task>> entry: group.entrySet())
            {
                List<String> agents= entry.getKey();
                List<Task> tasks= entry.getValue();
                
                // 하나의 AgentServer에 전달할 worker count를 계산한다. 
                // 대용량 파일이 포함된 경우 최적화하여 계산되고 전체적으로 max size를 초과할 수 없다. 
                int workers= Math.min( MAX_WORKERS, optimizedWorkers( tasks));
                logger.debug( "optimized worker count: {}", workers);

                // 수신 Agent Server에 연결을 시도한다. 목록으로 전달된 경우 다음 연결을 시도한다.
                final TransferClient client= tryConnect( agents, workers);
                if( client== null)
                {
                    tasks.forEach( t->{
                        result.failed.getAndIncrement();
                        result.reasons.add( "resource ["+ t.sourceUri+ "] transfering is failed. cause: agent is not responding");
                    });
                    continue;
                }

                // ThreadPool을 생성하여 작업을 요청한다.
                // TaskRunner에서는 전달된 Client에 putParallelRequest를 호출한다.
                executor= Executors.newFixedThreadPool( workers);
                CountDownLatch latch= new CountDownLatch( tasks.size());
                try
                {
                    for( Task task: tasks)
                        executor.submit( new TaskRunner( client, task, latch, result));

                    // 전체 처리가 완료될 때 까지 대기한다.
                    latch.await();
                }
                finally
                {
                    client.shutdown();
                    if( executor!= null)
                        executor.shutdown();
                }
            }
            return result;
        }
        finally
        {
            if( executor!= null)
                executor.shutdownNow();
        }
    }

    // TransferClient에 전달할 worker count를 계산한다.
    // 기본적으로 파일 수 만큼 증가한다. 
    // 전송할 파일 중 대용량 파일에 대해서는 ParallelTransfer요청이 발생하므로 하나의 Thread를 더 사용하도록 한다.
    private int optimizedWorkers( List<Task> tasks)
    {
        return tasks.stream()
                .map( task-> { return new File( task.sourcePath);})
                .mapToInt( f->{
                    int workers= 1;
                    if( f.length()>= MIN_PARALLEL_CHUNK)
                        workers= Math.max( 2, (int)Math.ceil( (float)f.length()/ MIN_PARALLEL_CHUNK));
                    return workers;
                }).sum();
    }

    private TransferClient tryConnect( List<String> agents, int workers)
    {
        for( String agent: agents)
        {
            Channel channel= null;
            String[] address= StringUtils.tokenizeToStringArray( agent, ":");
            TransferClient client= TransferClient.create( agentEnv.getRepository().getBaseDir(), workers,
                    (int)agentEnv.getConnectTimeout().toMillis(), agentEnv.isSsl(),
                    address[0], Integer.parseInt( address[1]), agentEnv.getChunkSize());
            try
            {
                channel= client.connect();
                return client;
            }
            catch( Exception e)
            {
                logger.warn( "agent [{}:{}] is not responding at this moment. try another agent",
                        address[0], address[1]);
                client.shutdown();
                continue;
            }
            finally
            {
                if( channel!= null)
                    channel.close();
            }
        }
        return null;
    }

    class TaskRunner implements Runnable
    {
        private final TransferClient client;

        private final CountDownLatch latch;

        private final Task task;

        private final Result result;

        TaskRunner( TransferClient client, Task task, CountDownLatch latch, Result result)
        {
            this.client= client;
            this.task= task;
            this.latch= latch;
            this.result= result;
        }

        @Override
        public void run()
        {
            boolean ret= false;
            String reason= null;
            try
            {
                String site= null;
                List<String> sites= task.destOptions.remove( SITE);
                if( sites!= null && sites.size()> 0)
                    site= sites.get( 0);

                List<OptionParameter> params= new LinkedList<OptionParameter>();
                task.destOptions.entrySet().stream().forEach( entry->{
                    entry.getValue().forEach( pv-> {
                        params.add( OptionParameter.param( entry.getKey(), pv));});
                });

                File resource= new File( task.sourcePath);
                // 대용량 파일인 경우 판단하여 처리할 수 있도록 requestPutParallelResource를 호출한다. 
                ret= client.requestPutParallelResource( resource, task.destinationPath, site,
                        params.toArray( new OptionParameter[params.size()]));

                String after= OptionParameter.first( task.srcOptions, AFTER_TRANSFER);
                if( BACKUP.equals( after))
                {
                    String backupDir= agentEnv.getRepository().getBackupDir();
                    String siteOp= OptionParameter.first( task.srcOptions, SITE);
                    Site srcSite= null;
                    if( StringUtils.hasText( siteOp) && ( srcSite= agentEnv.getRepository().getSites().get( siteOp))!= null)
                        backupDir= srcSite.getBackupDir();

                    File to= new File( backupDir+ File.separator+ resource.getName());
                    Files.move( resource.toPath(), to.toPath(), REPLACE_EXISTING);
                    
                    logger.debug( "resource[{}] is moved to [{}]", resource.getAbsolutePath(), to.getAbsolutePath());

                }
                else if( DELETE.equals( after))
                {
                    FileUtil.deleteFile( resource);
                    logger.debug( "resource[{}] is deleted");
                }
            }
            catch( Exception e)
            {
                logger.error( "request put resource processing is failed.", e);
                ret= false;
                reason= e.getMessage();
                reason= StringUtils.hasText( reason) ? TransferMessageUtil.validateHeaderValue( reason) : "unknown";
                client.shutdown();
            }
            finally
            {
                if( ret)
                {
                    result.succeed.getAndIncrement();
                    result.transferred.offer( task.sourcePath);
                    result.reasons.offer( "resource ["+ task.sourceUri+ "] transfering is succeeded");
                }
                else
                {
                    result.failed.getAndIncrement();
                    result.reasons.offer( "resource ["+ task.sourceUri+ "] transfering is failed. cause: "+ reason);
                }
                latch.countDown();
            }
        }
    }

    static class Task
    {
        String fromAgent;

        String[] toAgents;

        String sourceUri;

        String sourcePath;

        String destinationUri;

        String destinationPath;

        Map<String, List<String>> srcOptions;

        Map<String, List<String>> destOptions;

        List<String> agentAddresses()
        {
            return Arrays.asList( toAgents);
        }

        Task fromAgent( String fromAgent)
        {
            this.fromAgent= fromAgent;
            return this;
        }

        Task toAgents( String... toAgents)
        {
            this.toAgents= toAgents;
            return this;
        }

        Task sourceUri( String uri)
        {
            this.sourceUri= uri;
            return this;
        }

        Task sourcePath( String path)
        {
            this.sourcePath= path;
            return this;
        }

        Task destinationUri( String uri)
        {
            this.destinationUri= uri;
            return this;
        }

        Task destinationPath( String path)
        {
            this.destinationPath= path;
            return this;
        }

        Task srcOptions( Map<String, List<String>> options)
        {
            this.srcOptions= options;
            return this;
        }

        Task destOptions( Map<String, List<String>> options)
        {
            this.destOptions= options;
            return this;
        }

        @Override
        public String toString()
        {
            StringBuilder sb= new StringBuilder();
            sb.append( "from: [").append( fromAgent).append( "] resource: [").append( sourceUri)
            .append( "(").append( sourcePath).append( ")]\n")
            .append( "options: [").append( srcOptions).append( "\n");
            if( toAgents!= null && toAgents.length> 0)
            {
                sb.append( "to: [");
                for( String to: toAgents)
                    sb.append( to).append( " ");
                sb.append( "] ");
            }
            sb.append( "resource: [").append( destinationUri).append( "]\n");
            sb.append( "options: [").append( destOptions).append( "\n");
            return sb.toString();
        }
    }

    static class Result
    {
        AtomicInteger succeed= new AtomicInteger();

        AtomicInteger failed= new AtomicInteger();

        Queue<String> transferred= new ConcurrentLinkedQueue<String>();

        Queue<String> reasons= new ConcurrentLinkedQueue<String>();
    }

}
