/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.handler;

import static easymaster.transfer.file.protocol.TransferCommand.TRANSFER;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT_TYPE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.DESTINATION_AGENT;
import static easymaster.transfer.file.protocol.TransferHeaderNames.REASON;
import static easymaster.transfer.file.protocol.TransferHeaderNames.REMOTE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFERRED_RESOURCE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_DESTINATION_URI;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_INTERCEPTOR;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_SOURCE_URI;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_TIMEOUT_SECONDS;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_VALIDATION;
import static easymaster.transfer.file.protocol.TransferHeaderValues.VALIDATION_ON;
import static easymaster.transfer.file.protocol.TransferResponseCode.BAD_REQUEST;
import static easymaster.transfer.file.protocol.TransferResponseCode.SOURCE_FILE_NOT_FOUND;
import static easymaster.transfer.file.protocol.TransferResponseCode.TIMEOUT_OCCURRED;
import static easymaster.transfer.file.protocol.TransferResponseCode.TRANSFER_FAILED;
import static easymaster.transfer.file.util.OptionParameter.SITE;
import static easymaster.transfer.file.util.TransferConstants.BIND_ADDRESS;

import java.io.File;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.PathMatcher;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import easymaster.transfer.file.config.TransferCommandExecutor;
import easymaster.transfer.file.config.TransferEnvironment;
import easymaster.transfer.file.config.TransferEnvironment.Site;
import easymaster.transfer.file.handler.TransferExecutor.Task;
import easymaster.transfer.file.interceptors.TransferContext;
import easymaster.transfer.file.interceptors.TransferInterceptor;
import easymaster.transfer.file.protocol.TransferMessage;
import easymaster.transfer.file.util.OptionParameter;
import easymaster.transfer.file.util.TransferMessageUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.ObjectUtil;

/**
 * @author Jongoh Lee
 *
 */

public class TransferCommandRequestHandler extends AbstractRequestHandler
{
    private Logger logger= LoggerFactory.getLogger( TransferCommandRequestHandler.class);

    private final ChannelHandlerContext context;

    private final PathMatcher pathMatcher= new AntPathMatcher();

    public TransferCommandRequestHandler( 
            ChannelHandlerContext context, ApplicationContext applicationContext, TransferEnvironment environment)
    {
        super( applicationContext, environment);
        this.context= context;
    }

    @Override
    public void handleCommand( final TransferMessage request)
    {
        ObjectUtil.checkNotNull( request, "request");
        logger.info( "transferCommand request accepted, from: [{}:{}]",
                request.headers().get( AGENT_TYPE),
                request.headers().get( AGENT));

        response( context, ()->{ return handleTransferCommandRequest( request);}, false);
    }

    private HandlerResponse handleTransferCommandRequest( final TransferMessage request) throws RequestHandlerException
    {
        TransferMessage response= new TransferMessage( TRANSFER);
        response.setUri( request.uri());

        String remoteAgent= request.headers().get( REMOTE);
        final String remote= StringUtils.hasText( remoteAgent) ? remoteAgent : environment.getCustom().get( BIND_ADDRESS);

        List<String> sources= request.headers().getAll( TRANSFER_SOURCE_URI);
        List<String> destinations= request.headers().getAll( TRANSFER_DESTINATION_URI);
        final List<String> toAgents= request.headers().getAll( DESTINATION_AGENT);
        final boolean validation= request.headers().contains( TRANSFER_VALIDATION, VALIDATION_ON, true);
        final long timeout= request.headers().getInt( TRANSFER_TIMEOUT_SECONDS, (int)environment.getTransferTimeout().toSeconds());

        if( CollectionUtils.isEmpty( sources) || CollectionUtils.isEmpty( destinations) || sources.size()!= destinations.size())
            throw new RequestHandlerException( BAD_REQUEST, "source uris, destination uris are not matched or empty");

        Future<TransferExecutor.Result> handler= null;
        TransferContext transfer= null;
        try
        {
            final List<TransferExecutor.Task> tasks= new LinkedList<TransferExecutor.Task>();
            Iterator<String> dests= destinations.iterator();
            Iterator<String> tos= toAgents.iterator();

            // 전송 파일의 갯수 만큼 TransferExecutor.Task 생성
            long count= sources.stream().filter( StringUtils::hasText).map( s-> {
                Map<String, List<String>> srcOpts= new LinkedHashMap<String, List<String>>();
                Map<String, List<String>> targetOpts= new LinkedHashMap<String, List<String>>();
                try
                {
                    URI source= TransferMessageUtil.decodeRawUri( s, srcOpts);
                    StringBuilder pathBuilder= cleanPath( source.getPath());
                    applyEnvironment( pathBuilder, srcOpts);
                    String path= pathBuilder.toString();
                    TransferExecutor.Task task= new TransferExecutor.Task();
                    task.sourceUri( s).sourcePath( path).srcOptions( srcOpts);

                    if( StringUtils.hasText( source.getHost()) && source.getPort()!= -1)
                        task.fromAgent( source.getHost()+ ":"+ source.getPort());
                    else
                        task.fromAgent( remote+ ":"+ environment.getTcpPort());

                    String dest= dests.next();
                    URI target= TransferMessageUtil.decodeRawUri( dest, targetOpts);
                    task.destinationUri( dest).destinationPath( target.getPath()).destOptions( targetOpts);

                    if( StringUtils.hasText( target.getHost()) && target.getPort()!= -1)
                        task.toAgents( target.getHost()+ ":"+ target.getPort());
                    else
                        task.toAgents( StringUtils.tokenizeToStringArray( tos.next(), ";"));

                    tasks.add( task);
                    return task.sourcePath;
                }
                catch( Exception e)
                {
                    /* invalid uris matching */
                    logger.error( "invalid request uris", e);
                    return s;

                }}).count();

            if( logger.isDebugEnabled())
                logger.debug( "filtered: {}, sourceUris: {}, destinationUris: {}, toAgents: {}, toIterators: {}",
                        new Object[] { count, sources.size(), destinations.size(), toAgents.size(), tos.hasNext()});

            // 전송 파일의 요청과 수신 파일 경로가 일치하지 않는 경우 오류 처리
            if( count!= sources.size() || count!= destinations.size() || count!= tasks.size() || tos.hasNext())
                throw new RequestHandlerException( BAD_REQUEST, "uris( count, format) are invalid");

            logger.debug( "excutable tasks: {}", tasks);

            // Tasks목록을 병렬로 처리할 수 있도록 TransferExecutor를 생성
            TransferExecutor trans= new TransferExecutor( tasks, environment);
            List<String> paths= new LinkedList<String>();
            for( Task task: tasks)
            {
                if( pathMatcher.isPattern( task.sourcePath))
                    throw new RequestHandlerException( BAD_REQUEST, "pattern uri is not allowed");

                File fs= ResourceUtils.getFile( task.sourcePath);
                if( !fs.exists())
                    throw new RequestHandlerException( SOURCE_FILE_NOT_FOUND, fs.getAbsolutePath()+ " is not found");
                paths.add( task.sourcePath);
            }
            trans.prepare();

            // 선후처리기에서 사용할 주요 정보를 담고 있는 TransferContext 생성
            transfer= TransferContext.createTransferContext( applicationContext, request.headers(), 
                    paths.toArray( new String[paths.size()]));

            preProcess( transfer, request.headers().getAll( TRANSFER_INTERCEPTOR), request.headers(), null, TransferInterceptor.class);

            // 전송 처리를 비동기로 처리하기 위해 별도의 Thread Executor를 사용한다. 
            // validation option이 false인 경우 비동기로 전송 요청을 처리하고 전송 처리가 진행중임을 응답으로 전송한다.
            // validation option이 true인 경우 비동기로 실행된 전송 요청의 처리 결과를 대기하여 전송 처리 완료 여부를 응답으로 전송한다. 
            handler= TransferCommandExecutor.transferExecutor().submit( new Callable<TransferExecutor.Result>(){
                @Override
                public TransferExecutor.Result call() throws Exception
                {
                    return trans.transfer();
                }});

            // validation option에 따른 완료 후 응답 또는 즉시 응답
            if( validation)
            {
                TransferExecutor.Result result= timeout!= -1 ? handler.get( timeout, TimeUnit.SECONDS) : handler.get();
                response.headers().add( REASON, result.succeed.get()+ " files are transferred, " + result.failed.get()+ " files are failed");
                result.transferred.forEach( tr->{ response.headers().add( TRANSFERRED_RESOURCE, tr);});
                result.reasons.forEach( rs->{ response.headers().add( REASON, rs);});
            }
            else
                response.headers().add( REASON, sources.size()+ " files are being transferred");

            final TransferContext trCtx= transfer;
            Consumer<Void> postProcess= ( Void)->{
                try
                {
                    postProcess( trCtx, request.headers().getAll( TRANSFER_INTERCEPTOR), 
                            request.headers(), null, TransferInterceptor.class, null);
                }
                catch( Throwable th) { logger.warn( "postProcess execution is failed", th);}
            };
            
            Consumer<Void> afterCompletion= ( Void)->{
                try { afterCompletion( trCtx, request.headers().getAll( TRANSFER_INTERCEPTOR));}
                catch( Throwable th) { logger.warn( "afterCompletion execution is failed", th);}
            };

            return new HandlerResponse( response, postProcess, afterCompletion);
        }
        catch( TimeoutException | CancellationException | InterruptedException | ExecutionException te)
        {
            if( handler!= null)
                handler.cancel( true);
            throw new RequestHandlerException( TIMEOUT_OCCURRED, "timed out while transfer", te);
        }
        catch( Exception e)
        {
            final TransferContext trCtx= transfer;
            Consumer<Exception> postProcess= ( er)->{
                try
                {
                    postProcess( trCtx, request.headers().getAll( TRANSFER_INTERCEPTOR), 
                            request.headers(), null, TransferInterceptor.class, er);
                }
                catch( Throwable th) { logger.warn( "postProcess execution is failed", th);}
            };
            
            Consumer<Void> afterCompletion= ( Void)->{
                try { afterCompletion( trCtx, request.headers().getAll( TRANSFER_INTERCEPTOR));}
                catch( Throwable th) { logger.warn( "afterCompletion execution is failed", th);}
            };
            
            RequestHandlerException he= e instanceof RequestHandlerException ? (RequestHandlerException)e :
                new RequestHandlerException( TRANSFER_FAILED, e.getMessage(), e);
            he.postProcess= postProcess;
            he.afterComplete= afterCompletion;
            throw he;
        }
    }

    private StringBuilder cleanPath( String path)
    {
        StringBuilder pathBuilder= new StringBuilder( StringUtils.cleanPath( path));
        if( pathBuilder.charAt( 0)!= '/')
            pathBuilder.insert( 0, '/');
        return pathBuilder;
    }

    private void applyEnvironment( StringBuilder pathBuilder, Map<String, List<String>> options)
    {
        String siteOp= OptionParameter.first( options, SITE);
        Site site= null;
        if( StringUtils.hasText( siteOp) && ( site= environment.getRepository().getSites().get( siteOp))!= null)
            pathBuilder.insert( 0, site.getBaseDir());
        else
            pathBuilder.insert( 0, environment.getRepository().getBaseDir());
    }
}
