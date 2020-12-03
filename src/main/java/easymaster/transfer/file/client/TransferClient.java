/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.client;

import static easymaster.transfer.file.handler.TransferAction.MERGE_;
import static easymaster.transfer.file.handler.TransferAction.SESSION_;
import static easymaster.transfer.file.handler.TransferAction.SHUTDOWN_;
import static easymaster.transfer.file.handler.TransferInfo.EXIST_;
import static easymaster.transfer.file.handler.TransferInfo.HEALTH_;
import static easymaster.transfer.file.handler.TransferInfo.INFO_;
import static easymaster.transfer.file.protocol.ResponseCode.SUCCESS;
import static easymaster.transfer.file.protocol.TransferCommand.ACTION;
import static easymaster.transfer.file.protocol.TransferCommand.DELETE;
import static easymaster.transfer.file.protocol.TransferCommand.GET;
import static easymaster.transfer.file.protocol.TransferCommand.INFO;
import static easymaster.transfer.file.protocol.TransferCommand.LIST;
import static easymaster.transfer.file.protocol.TransferCommand.PUT;
import static easymaster.transfer.file.protocol.TransferCommand.TRANSFER;
import static easymaster.transfer.file.protocol.TransferHeaderNames.CONTENT_LENGTH;
import static easymaster.transfer.file.protocol.TransferHeaderNames.DESTINATION_AGENT;
import static easymaster.transfer.file.protocol.TransferHeaderNames.MERGE_RESOURCE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.REASON;
import static easymaster.transfer.file.protocol.TransferHeaderNames.SESSION_ID;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFERRED_RESOURCE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_DESTINATION_URI;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_ENCODING;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_SOURCE_URI;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_TIMEOUT_SECONDS;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_VALIDATION;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CHUNKED;
import static easymaster.transfer.file.protocol.TransferHeaderValues.VALIDATION_OFF;
import static easymaster.transfer.file.protocol.TransferHeaderValues.VALIDATION_ON;
import static easymaster.transfer.file.protocol.TransferResponseCode.ALREADY_EXIST;
import static easymaster.transfer.file.protocol.TransferResponseCode.BAD_RESPONSE;
import static easymaster.transfer.file.protocol.TransferResponseCode.SOURCE_FILE_NOT_FOUND;
import static easymaster.transfer.file.protocol.TransferResponseCode.TIMEOUT_OCCURRED;
import static easymaster.transfer.file.protocol.TransferResponseCode.TRANSFER_FAILED;
import static easymaster.transfer.file.util.OptionParameter.DELET_ON_EXIT;
import static easymaster.transfer.file.util.OptionParameter.ON_EXIST;
import static easymaster.transfer.file.util.OptionParameter.SITE;
import static easymaster.transfer.file.util.OptionParameterValues.FAIL_ONEXIST;
import static easymaster.transfer.file.util.OptionParameterValues.TRUE;
import static easymaster.transfer.file.util.TransferMessageUtil.PATH_SEPARATOR;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.springframework.boot.actuate.health.Status.DOWN;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import easymaster.transfer.file.client.ChunkedNioFileBuf.SplittedBuf;
import easymaster.transfer.file.handler.RequestHandlerException;
import easymaster.transfer.file.protocol.FileData;
import easymaster.transfer.file.protocol.ResponseCode;
import easymaster.transfer.file.protocol.TransferChunkedContentEncoder;
import easymaster.transfer.file.protocol.TransferHeaderNames;
import easymaster.transfer.file.protocol.TransferMessage;
import easymaster.transfer.file.protocol.TransferMessageClientCodec;
import easymaster.transfer.file.protocol.TransferParallelContentEncoder;
import easymaster.transfer.file.protocol.TransferResponseCode;
import easymaster.transfer.file.util.FileUtil;
import easymaster.transfer.file.util.OptionParameter;
import easymaster.transfer.file.util.TransferMessageUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.ObjectUtil;

/**
 * @author Jongoh Lee
 *
 */

public class TransferClient
{
    private Logger logger= LoggerFactory.getLogger( TransferClient.class);

    public static Channel THROWAWAY= null;

    public static final int MAX_WORKERS= 10;
    
    public static final int MIN_PARALLEL_CHUNK= 1* 1024* 1024;
    
    public static final int MAX_PARALLEL_CHUNK= 256* 1024* 1024;

    private NioEventLoopGroup workerGroup;

    private final File baseDir;

    private final int connectTimeout;

    private final boolean ssl;

    private final int chunkSize;

    InetSocketAddress remote;

    private Bootstrap bootstrap;

    public static TransferClient create( String host, int port)
    {
        return create( ".", 2, 5000, true, host, port, MIN_PARALLEL_CHUNK);
    }

    public static TransferClient create( String host, int port, int workers)
    {
        return create( ".", workers, 5000, true, host, port, MIN_PARALLEL_CHUNK);
    }
    
    public static TransferClient create( String basedir, int workers, int connectTimeoutMillis, boolean supportSsl,
            String host, int port)
    {
        return new TransferClient( basedir, workers, connectTimeoutMillis, supportSsl, host, port, MIN_PARALLEL_CHUNK);
    }

    public static TransferClient create( String basedir, int workers, int connectTimeoutMillis, boolean supportSsl,
            String host, int port, int chunkSize)
    {
        return new TransferClient( basedir, workers, connectTimeoutMillis, supportSsl, host, port, chunkSize);
    }

    private TransferClient( String basedir, int workers, int connectTimeout, boolean ssl, String host, int port, int chunkSize)
    {
        ObjectUtil.checkNotNull( basedir, "basedir");
        this.baseDir= new File( basedir);
        if( baseDir.exists() && !baseDir.isDirectory())
            throw new IllegalArgumentException( "only directory is allowed");
        if( !baseDir.mkdirs() && !baseDir.exists())
            throw new IllegalArgumentException( "cannot create basedir");
        this.workerGroup= new NioEventLoopGroup( Math.min( MAX_WORKERS, workers));
        this.connectTimeout= connectTimeout;
        this.ssl= ssl;
        this.remote= new InetSocketAddress( host, port);
        this.chunkSize= chunkSize;
        bootstrap();
    }

    private boolean bootstrap()
    {
        bootstrap= new Bootstrap();
        bootstrap.group( workerGroup).channel( NioSocketChannel.class)
            .option( ChannelOption.SO_KEEPALIVE, true)
            .option( ChannelOption.CONNECT_TIMEOUT_MILLIS, this.connectTimeout)
            .handler( new ChannelInitializer<Channel>(){
                @Override
                protected void initChannel( Channel ch) throws Exception
                {
                    ChannelPipeline pipeline= ch.pipeline();
                    if( ssl)
                        pipeline.addLast( SslContextBuilder.forClient().trustManager( 
                                InsecureTrustManagerFactory.INSTANCE).build().newHandler( ch.alloc()));
                    pipeline
                        .addLast( ZlibCodecFactory.newZlibEncoder( ZlibWrapper.GZIP))
                        .addLast( ZlibCodecFactory.newZlibDecoder( ZlibWrapper.GZIP))
                        .addLast( new TransferMessageClientCodec())
                        .addLast( new ChunkedWriteHandler())
                        .addLast( new TransferClientHandler( baseDir));
                }
            });

        return true;
    }

    public Channel connect() throws RequestHandlerException
    {
        try{ return bootstrap.connect( remote).sync().channel();}
        catch( Exception e)
        {
            throw new RequestHandlerException( TIMEOUT_OCCURRED, e.getMessage(), e);
        }
    }

    public <R> R request( Channel channel, TransferMessage request, ResponseConsumer<TransferMessage, R> consumer)
        throws RequestHandlerException, ResponseHandlerException
    {
        return request( channel, request, consumer, -1);
    }

    public <R> R request( final Channel channel, TransferMessage request, ResponseConsumer<TransferMessage, R> consumer, long timeout) 
            throws RequestHandlerException, ResponseHandlerException
    {
        Channel writtable= channel!= THROWAWAY ? channel : connect();
        writtable.writeAndFlush( request);
        TransferClientHandler handler= (TransferClientHandler)writtable.pipeline().last();
        Future<TransferMessage> future= null;

        try
        {
            future= handler.sync();
            TransferMessage response= timeout< 0 ? future.get() : future.get( timeout, TimeUnit.MILLISECONDS);
            TransferResponseCode rsCode= response.headers().getResponseCode();

            if( SUCCESS!= ResponseCode.valueOf( rsCode.code()))
            {
                StringBuilder sb= new StringBuilder();
                List<String> reasons= response.headers().getAll( REASON);
                
                if( !CollectionUtils.isEmpty( reasons))
                {
                    for( String reason: reasons)
                        sb.append( reason).append( "\n");
                }
                throw new ResponseHandlerException( rsCode, sb.toString());
            }

            return consumer.accept( response);
        }
        catch( TimeoutException | CancellationException | InterruptedException | ExecutionException te)
        {
            if( future!= null)
                future.cancel( true);
            throw new RequestHandlerException( TIMEOUT_OCCURRED, "timed out while transfer", te);
        }
        catch( ResponseHandlerException re)
        {
            logger.error( "response handler failed", re);
            throw re;
        }
        catch( Exception e)
        {
            logger.error( "response handler failed", e);
            throw new ResponseHandlerException( BAD_RESPONSE, e.getMessage(), e);
        }
        finally
        {
            if( THROWAWAY== channel)
                writtable.close();
        }
    }

    public Map<String, String> requestServerInfo( Channel channel) throws Exception
    {
        return requestServerInfo( channel, -1);
    }

    public Map<String, String> requestServerInfo( Channel channel, long timeout) throws Exception
    {
        TransferMessage request= new TransferMessage( INFO);
        request.setUri( INFO_);
        return request( channel, request, response->{
            Map<String, String> infos= new LinkedHashMap<String, String>();
            response.headers().forEach( entry->{
                infos.put( entry.getKey(), entry.getValue());
            });
            return infos;
        } , timeout);
    }

    public Health requestHealth( Channel channel) throws Exception
    {
        return requestHealth( channel, -1);
    }

    public Health requestHealth( Channel channel, long timeout) throws Exception
    {
        Channel writtable= channel!= THROWAWAY ? channel : connect();
        TransferMessage request= new TransferMessage( INFO);
        request.setUri( HEALTH_);
        writtable.writeAndFlush( request);

        TransferClientHandler handler= (TransferClientHandler)writtable.pipeline().last();
        Future<TransferMessage> future= null;
        try
        {
            future= handler.sync();
            TransferMessage response= timeout< 0 ? future.get() : future.get( timeout, TimeUnit.MILLISECONDS);
            TransferResponseCode rsCode= response.headers().getResponseCode();
            if( SUCCESS== ResponseCode.valueOf( rsCode.code()))
            {
                String statusCode= response.headers().get( REASON);
                return Health.status( statusCode).build();
            }
            return Health.status( DOWN).build();
        }
        catch( TimeoutException | CancellationException | InterruptedException | ExecutionException te)
        {
            if( future!= null)
                future.cancel( true);
            throw new RequestHandlerException( TIMEOUT_OCCURRED, "timed out while transfer", te);
        }
        catch( Exception e)
        {
            return Health.status( DOWN).build();
        }
    }

    public boolean requestResourceExist( Channel channel, String path, String site) throws Exception
    {
        return requestResourceExist( channel, path, site, -1);
    }

    public boolean requestResourceExist( Channel channel, String path, String site, long timeout) throws Exception
    {
        TransferMessage request= new TransferMessage( INFO);
        request.setUri( EXIST_);
        String uri= TransferMessageUtil.encodedUri( remote.getAddress().getHostAddress(), remote.getPort(), path,
                StringUtils.hasText( site) ? new OptionParameter[] { OptionParameter.param( SITE, site)} : new OptionParameter[] {});
        request.headers().add( TRANSFER_SOURCE_URI, uri);

        return request( channel, request, response->{
            return !response.headers().getResponseCode().equals( TransferResponseCode.NOT_EXIST);
        } , timeout);
    }

    public <R> R requestGetResource( Channel channel, String path, String site, Function<FileData, R> operator, OptionParameter... options) 
            throws Exception
    {
        return requestGetResource( channel, path, site, operator, -1, options);
    }

    public <R> R requestGetResource( Channel channel, String path, String site,
            Function<FileData, R> operator, long timeout, OptionParameter... options) throws Exception
    {
        TransferMessage request= new TransferMessage( GET);
        List<OptionParameter> opts= new ArrayList<OptionParameter>();
        if( StringUtils.hasText( site))
            opts.add( OptionParameter.param( SITE, site));
        CollectionUtils.mergeArrayIntoCollection( options, opts);

        String uri= TransferMessageUtil.encodedUri( remote.getAddress().getHostAddress(), remote.getPort(), path,
                opts.toArray( new OptionParameter[opts.size()]));
        request.headers().add( TRANSFER_SOURCE_URI, uri);

        return request( channel, request, response->{
            R result= operator.apply( response.content());
            response.content().release();
            return result;
        } , timeout);
    }

    public List<String> requestDeleteResources( Channel channel, String path, String site, OptionParameter... options)
            throws Exception
    {
        return requestDeleteResources( channel, path, site, -1, options);
    }

    public List<String> requestDeleteResources( Channel channel, String path, String site, long timeout, OptionParameter... options) 
            throws Exception
    {
        TransferMessage request= new TransferMessage( DELETE);
        List<OptionParameter> opts= new ArrayList<OptionParameter>();
        if( StringUtils.hasText( site))
            opts.add( OptionParameter.param( SITE, site));
        CollectionUtils.mergeArrayIntoCollection( options, opts);

        String uri= TransferMessageUtil.encodedUri( remote.getAddress().getHostAddress(), remote.getPort(), path,
                opts.toArray( new OptionParameter[opts.size()]));
        request.headers().add( TRANSFER_SOURCE_URI, uri);

        return request( channel, request, response->{
            return response.headers().getAll( REASON);
        } , timeout);
    }
    
    public List<String> requestListResources( Channel channel, String path, String site, OptionParameter... options)
            throws Exception
    {
        return requestListResources( channel, path, site, -1, options);
    }

    public List<String> requestListResources( Channel channel, String path, String site,
            long timeout, OptionParameter... options) throws Exception
    {
        TransferMessage request= new TransferMessage( LIST);
        List<OptionParameter> opts= new ArrayList<OptionParameter>();
        if( StringUtils.hasText( site))
            opts.add( OptionParameter.param( SITE, site));
        CollectionUtils.mergeArrayIntoCollection( options, opts);

        String uri= TransferMessageUtil.encodedUri( remote.getAddress().getHostAddress(), remote.getPort(), path,
                opts.toArray( new OptionParameter[opts.size()]));
        request.headers().add( TRANSFER_SOURCE_URI, uri);

        return request( channel, request, response->{
            return response.headers().getAll( REASON);
        } , timeout);
    }

    public boolean requestPutResource( Channel channel,
            File resource, String path, String site, OptionParameter... options) throws Exception
    {
        return requestPutResource( channel, resource, path, site, -1, options);
    }

    public boolean requestPutResource( Channel channel, File resource, String path, String site, long timeout, OptionParameter... options) 
            throws Exception
    {
        if( !resource.exists() || resource.isDirectory())
            throw new RequestHandlerException( SOURCE_FILE_NOT_FOUND, resource.getAbsolutePath());

        TransferMessage request= new TransferMessage( PUT);
        List<OptionParameter> opts= new ArrayList<OptionParameter>();
        if( StringUtils.hasText( site))
            opts.add( OptionParameter.param( OptionParameter.SITE, site));
        CollectionUtils.mergeArrayIntoCollection( options, opts);

        FileData fdata= new FileData( resource.length());
        fdata.setContent( resource);

        Channel writtable= channel!= THROWAWAY ? channel : connect();
        InetSocketAddress local= (InetSocketAddress)writtable.localAddress();
        String srcUri= TransferMessageUtil.encodedUri( local.getAddress().getHostAddress(), local.getPort(), 
                FileUtil.stripPath( resource.getAbsolutePath()), new OptionParameter[] {});

        String destUri= TransferMessageUtil.encodedUri( remote.getAddress().getHostAddress(), remote.getPort(), path, 
                opts.toArray( new OptionParameter[opts.size()]));

        request.headers()
            .add( CONTENT_LENGTH, resource.length())
            .add( TRANSFER_SOURCE_URI, srcUri)
            .add( TRANSFER_DESTINATION_URI, destUri);

        if( resource.length()< chunkSize)
        {
            request.setContent( fdata);
            writtable.writeAndFlush( request);
        }
        else
        {
            request.headers().add( TRANSFER_ENCODING, CHUNKED);
            writtable.writeAndFlush( request);
            TransferChunkedContentEncoder chunk= new TransferChunkedContentEncoder( fdata, chunkSize);
            writtable.writeAndFlush( chunk);
        }

        TransferClientHandler handler= (TransferClientHandler)writtable.pipeline().last();
        Future<TransferMessage> future= null;
        try
        {
            future= handler.sync();
            TransferMessage response= timeout< 0 ? future.get() : future.get( timeout, MILLISECONDS);
            TransferResponseCode rsCode= response.headers().getResponseCode();

            if( SUCCESS!= ResponseCode.valueOf( rsCode.code()))
            {
                StringBuilder sb= new StringBuilder();
                List<String> reasons= response.headers().getAll( REASON);
                if( !CollectionUtils.isEmpty( reasons))
                {
                    for( String reason: reasons)
                        sb.append( reason).append( "\n");
                }
                throw new ResponseHandlerException( rsCode, sb.toString());
            }
            return true;
        }
        catch( TimeoutException | CancellationException | InterruptedException | ExecutionException te)
        {
            if( future!= null)
                future.cancel( true);
            throw new RequestHandlerException( TIMEOUT_OCCURRED, "timed out while transfer", te);
        }
        catch( ResponseHandlerException re)
        {
            logger.error( "response handler failed", re);
            throw re;
        }
        catch( Exception e)
        {
            logger.error( "response handler failed", e);
            throw new ResponseHandlerException( BAD_RESPONSE, e.getMessage(), e);
        }
        finally
        {
            if( THROWAWAY== channel)
                writtable.close();
        }
    }

    public boolean requestPutParallelResource( File resource, String path, String site, OptionParameter... options)
            throws Exception
    {
        if( !resource.exists() || resource.isDirectory())
            throw new RequestHandlerException( SOURCE_FILE_NOT_FOUND, resource.getAbsolutePath());

        if( resource.length()< chunkSize* 10)
            return requestPutResource( THROWAWAY, resource, path, site, options);

        List<OptionParameter> opts= new ArrayList<OptionParameter>();
        if( StringUtils.hasText( site))
            opts.add( OptionParameter.param( SITE, site));
        CollectionUtils.mergeArrayIntoCollection( options, opts);

        int concurrent= Math.max( 2, workerGroup.executorCount());
        int parallelChunk= Math.min( MAX_PARALLEL_CHUNK, (int)( resource.length() / concurrent));
        
        ExecutorService service= Executors.newFixedThreadPool( concurrent);
        CountDownLatch latch= new CountDownLatch( (int)Math.ceil( (float)resource.length()/ parallelChunk));
        ChunkedNioFile chunked= new ChunkedNioFile( resource, parallelChunk);
        ChunkedNioFileBuf reader= new ChunkedNioFileBuf( chunked);

        int tasks= (int)latch.getCount();
        logger.debug( "content:{}, concurrent: {}, chunkSize: {}, tasks: {}", 
                new Object[] { resource.length(), concurrent, parallelChunk, tasks});

        final List<String> splits= Collections.synchronizedList( new LinkedList<String>());
        final List<Exception> causes= Collections.synchronizedList( new LinkedList<Exception>());
        try
        {
            if( opts.stream().filter( opt->{ return opt.name().equals( ON_EXIST) 
                    && opt.value().equalsIgnoreCase( FAIL_ONEXIST);}).count()!= 0
                    && requestResourceExist( THROWAWAY, path, site))
                throw new RequestHandlerException( ALREADY_EXIST, "target file["+ path+ "] is already exist");
            
            TransferMessage session= new TransferMessage( ACTION);
            session.setUri( SESSION_);
            String sessionId= request( THROWAWAY, session, response->{
                return response.headers().get( SESSION_ID);
            });

            logger.debug( "sessionId: {}, for tasks: {}", sessionId, tasks);
            for( int i= tasks; i> 0 ; i--)
            {
                service.execute( new Runnable(){
                    @Override
                    public void run()
                    {
                        String suffix= null;
                        String splitname= null;
                        ByteBuf buf= null;
                        Channel channel= null;
                        try
                        {
                            channel= connect();
                            InetSocketAddress local= (InetSocketAddress)channel.localAddress();
                            SplittedBuf splitted= reader.nextBuf( channel.alloc());
                            suffix= splitted.suffix;
                            splitname= FileUtil.onlyPath( path)+ PATH_SEPARATOR+ UUID.randomUUID().toString().replace( '-', '_')+ suffix;
                            buf= splitted.buf;

                            logger.debug( "parallel chunk- readable: {}, progress: {}", buf.readableBytes(), chunked.progress());
                            
                            TransferMessage request= new TransferMessage( PUT);
                            String srcUri= TransferMessageUtil.encodedUri( local.getAddress().getHostAddress(), local.getPort(),
                                    FileUtil.stripPath( resource.getAbsolutePath()), new OptionParameter[] {});

                            String destUri= TransferMessageUtil.encodedUri(
                                    remote.getAddress().getHostAddress(), remote.getPort(), splitname,
                                    StringUtils.hasText( site) ? new OptionParameter[]
                                            {
                                                OptionParameter.param( SITE, site),
                                                OptionParameter.param( DELET_ON_EXIT, TRUE)
                                            }
                                            : new OptionParameter[] {OptionParameter.param( DELET_ON_EXIT, TRUE)});

                            request.headers()
                                .add( SESSION_ID, sessionId)
                                .add( CONTENT_LENGTH, buf.readableBytes())
                                .add( TRANSFER_SOURCE_URI, srcUri)
                                .add( TRANSFER_DESTINATION_URI, destUri)
                                .add( TRANSFER_ENCODING, CHUNKED);

                            channel.writeAndFlush( request);
                            TransferParallelContentEncoder chunk= new TransferParallelContentEncoder( buf, chunkSize);
                            channel.writeAndFlush( chunk);

                            TransferClientHandler handler= (TransferClientHandler)channel.pipeline().last();
                            logger.debug( "sync for future response");
                            Future<TransferMessage> future= handler.sync();
                            TransferMessage response= future.get();
                            logger.debug( "awaked for future response");
                            TransferResponseCode rsCode= response.headers().getResponseCode();

                            if( SUCCESS!= ResponseCode.valueOf( rsCode.code()))
                            {
                                StringBuilder sb= new StringBuilder();
                                List<String> reasons= response.headers().getAll( REASON);
                                if( !CollectionUtils.isEmpty( reasons))
                                {
                                    for( String reason: reasons)
                                        sb.append( reason).append( "\n");
                                }
                                throw new ResponseHandlerException( rsCode, sb.toString());
                            }

                            splits.add( splitname);
                            logger.debug( "resource: {} transfer succeeded.", splitname);
                        }
                        catch( Exception e)
                        {
                            logger.error( "parall put reqeust[{}} processing is failed", splitname, e);
                            causes.add( e);
                        }
                        finally
                        {
                            if( channel!= null) channel.close();
                            latch.countDown();
                            logger.debug( "latch countdown: {}", suffix);
                        }
                    }
                });
            }

            latch.await();

            logger.debug( "--------------------------------------------");
            logger.debug( "awaked for merge request");
            logger.debug( "--------------------------------------------");

            if( !causes.isEmpty())
                throw causes.get( 0);

            Channel channel= connect();
            InetSocketAddress local= (InetSocketAddress)channel.localAddress();
            try
            {
                TransferMessage request= new TransferMessage( ACTION);
                request.setUri( MERGE_);
                String srcUri= TransferMessageUtil.encodedUri( local.getAddress().getHostAddress(), local.getPort(),
                        FileUtil.stripPath( resource.getAbsolutePath()), new OptionParameter[] {});

                String destUri= TransferMessageUtil.encodedUri( remote.getAddress().getHostAddress(), remote.getPort(),
                        path, opts.toArray( new OptionParameter[opts.size()]));

                request.headers()
                    .add( SESSION_ID, sessionId)
                    .add( TRANSFER_SOURCE_URI, srcUri)
                    .add( TRANSFER_DESTINATION_URI, destUri);
                splits.forEach( split->{ request.headers().add( MERGE_RESOURCE, split); });

                return request( channel, request, response->{ return true; });
            }
            finally
            {
                if( chunked!= null) chunked.close();
                if( channel!= null) channel.close();
            }
        }
        catch( ResponseHandlerException re)
        {
            logger.error( "response handler failed", re);
            throw re;
        }
        catch( Exception e)
        {
            logger.error( "response handler failed", e);
            throw new ResponseHandlerException( BAD_RESPONSE, e.getMessage(), e);
        }
        catch( Throwable th)
        {
            logger.error( "response handler failed", th);
            throw new ResponseHandlerException( BAD_RESPONSE, th.getMessage(), th);
        }
        finally
        {
            logger.debug( "service shutdown now");
            if( service!= null) service.shutdownNow();
        }
    }

    public boolean requestTransferResources( Channel channel, List<TransferRequest> trans, boolean sync, OptionParameter... options) 
            throws Exception
    {
        return requestTransferResources( channel, trans, sync, -1, options);
    }

    public boolean requestTransferResources( Channel channel, List<TransferRequest> trans, boolean sync, long timeout, 
            OptionParameter... options) throws Exception
    {
        ObjectUtil.checkNonEmpty( trans, "trans");

        TransferMessage request= new TransferMessage( TRANSFER);
        if( timeout> 0)
            request.headers().add( TRANSFER_TIMEOUT_SECONDS, timeout* 1000);
        request.headers().add( TRANSFER_VALIDATION, sync ? VALIDATION_ON : VALIDATION_OFF);

        if( options!= null && options.length> 0)
        {
            for( OptionParameter opt: options)
            {
                if( opt.name().equals( OptionParameter.INTERCEPTOR))
                    request.headers().add( TransferHeaderNames.TRANSFER_INTERCEPTOR, opt.value());
            }
        }

        trans.forEach( tr->{
            request.headers().add( TRANSFER_SOURCE_URI, tr.transferSourceUri())
            .add( DESTINATION_AGENT, tr.transferDestinationAgent())
            .add( TRANSFER_DESTINATION_URI, tr.transferDestinationUri());
        });

        return request( channel, request, response->{
            if( !sync)
                return true;
            else
            {
                List<String> transferred= response.headers().getAll( TRANSFERRED_RESOURCE);
                if( transferred.size()== trans.size())
                    return true;
                else
                {
                    List<String> reasons= response.headers().getAll( REASON);
                    StringBuilder sb= new StringBuilder();
                    reasons.stream().forEach( rs->{ sb.append( rs).append( '\n'); });
                    throw new RequestHandlerException( TRANSFER_FAILED, sb.toString());
                }
            }
        } , -1);
    }

    public boolean requestShutdownCommand( Channel channel) throws Exception
    {
        TransferMessage request= new TransferMessage( ACTION);
        request.setUri( SHUTDOWN_);
        return request( channel, request, response->{ return true; });
    }

    public void shutdown()
    {
        File tempDir= new File( this.baseDir, "tmp");
        if( tempDir.exists())
            tempDir.delete();
        if( workerGroup!= null)
            workerGroup.shutdownGracefully();
    }

    public void shutdown( long quietPeriod, long timeout, TimeUnit unit)
    {
        File tempDir= new File( this.baseDir, "tmp");
        if( tempDir.exists())
            tempDir.delete();
        if( workerGroup!= null)
            workerGroup.shutdownGracefully( quietPeriod, timeout, unit);
    }

}
