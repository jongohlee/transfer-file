/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file;

import static easymaster.transfer.file.handler.TransferInfo.HEALTH_;
import static easymaster.transfer.file.protocol.ResponseCode.SUCCESS;
import static easymaster.transfer.file.protocol.TransferCommand.GET;
import static easymaster.transfer.file.protocol.TransferCommand.GET_;
import static easymaster.transfer.file.protocol.TransferCommand.INFO;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT_TYPE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.CONNECTION;
import static easymaster.transfer.file.protocol.TransferHeaderNames.REASON;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_ENCODING;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_SOURCE_URI;
import static easymaster.transfer.file.protocol.TransferHeaderValues.AGENT;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CHUNKED;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CLOSE;
import static easymaster.transfer.file.protocol.TransferResponseCode.BAD_RESPONSE;
import static easymaster.transfer.file.util.OptionParameter.INTERCEPTOR;
import static easymaster.transfer.file.util.OptionParameter.SITE;
import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static io.netty.channel.ChannelOption.SO_KEEPALIVE;
import static io.netty.channel.ChannelOption.TCP_NODELAY;
import static io.netty.handler.codec.compression.ZlibWrapper.GZIP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.springframework.boot.actuate.health.Status.UP;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import easymaster.transfer.file.config.TransferEnvironment;
import easymaster.transfer.file.config.TransferServerConfiguration;
import easymaster.transfer.file.handler.RequestHandlerException;
import easymaster.transfer.file.protocol.FileData;
import easymaster.transfer.file.protocol.LastTransferContent;
import easymaster.transfer.file.protocol.ResponseCode;
import easymaster.transfer.file.protocol.TransferCommand;
import easymaster.transfer.file.protocol.TransferContent;
import easymaster.transfer.file.protocol.TransferHeaderNames;
import easymaster.transfer.file.protocol.TransferMessage;
import easymaster.transfer.file.protocol.TransferMessageClientCodec;
import easymaster.transfer.file.protocol.TransferObject;
import easymaster.transfer.file.protocol.TransferResponseCode;
import easymaster.transfer.file.util.FileUtil;
import easymaster.transfer.file.util.OptionParameter;
import easymaster.transfer.file.util.TransferMessageUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * @author Jongoh Lee
 *
 */

@RunWith( SpringRunner.class)
@SpringBootTest(
        classes= { TransferServerConfiguration.class},
        properties= { "spring.config.name=transfer-file-test",
                "transfer.validation=off",
                "transfer.repository.base-dir=./src/test/resources",
                "transfer.repository.sites.biz1.base-dir=./src/test/resources",
                "transfer.ssl=on", 
                "transfer.chunk-size=8192", 
                "transfer.tcp-port=8025", 
                "transfer.bind=127.0.0.1"})
@ActiveProfiles( "test")
public class TransferGetRequestHandlerTest
{
    private Logger logger= LoggerFactory.getLogger( TransferGetRequestHandlerTest.class);
    
    @Autowired
    private TransferEnvironment environment;
    
    private EventLoopGroup GROUP;

    private CountDownLatch latch;

    private TransferMessage response;
    
    private Channel newClient( SocketAddress server) throws Exception
    {
        GROUP= new NioEventLoopGroup();
        final SslContext sslCtx= SslContextBuilder.forClient().trustManager( InsecureTrustManagerFactory.INSTANCE).build();
        Bootstrap bootstrap= new Bootstrap();
        bootstrap.group( GROUP)
            .channel( NioSocketChannel.class)
            .option( TCP_NODELAY, true)
            .option( SO_KEEPALIVE, true)
            .option( CONNECT_TIMEOUT_MILLIS, 1000)
            .handler( new ChannelInitializer<Channel>(){
                @Override
                protected void initChannel( Channel ch) throws Exception
                {
                    ch.pipeline()
                        .addLast( sslCtx.newHandler( ch.alloc()))
                        .addLast( ZlibCodecFactory.newZlibEncoder( GZIP))
                        .addLast( ZlibCodecFactory.newZlibDecoder( GZIP))
                        .addLast( new TransferMessageClientCodec())
                        .addLast( new ChunkedWriteHandler())
                        .addLast( new SimpleChannelInboundHandler<TransferObject>() {

                            @Override
                            protected void channelRead0( ChannelHandlerContext ctx, TransferObject msg) throws Exception
                            {
                                logger.debug( "reader message type: [{}]", msg.getClass());
                                logger.debug( "readed message: [{}]", msg);

                                if( msg instanceof LastTransferContent)
                                {
                                    logger.debug( "LastTransferContent detected....");
                                    if( response== null)
                                        return;

                                    if( response.headers().contains( TRANSFER_ENCODING, CHUNKED, true) && response.content()!= null)
                                        response.content().addContent( EMPTY_BUFFER, true);

                                    switch( response.command().name())
                                    {
                                        case GET_:

                                            logger.debug( response.headers().get( TRANSFER_SOURCE_URI));
                                            try
                                            {
                                                logger.debug( "{}", URLDecoder.decode( 
                                                        response.headers().get( TRANSFER_SOURCE_URI), Charset.defaultCharset().name()));
                                            }
                                            catch( Exception e) {}

                                            if( response.content()!= null)
                                            {
                                                response.content().renameTo( new File( environment.getRepository().getBaseDir()+
                                                                File.separator+ "received-content.dat"));
                                                response.content().release();
                                            }
                                            break;
                                    }

                                    if( response!= null && response.headers().contains( CONNECTION, CLOSE, true))
                                        ctx.close();

                                    latch.countDown();
                                    return;
                                }

                                if( msg instanceof TransferMessage)
                                    response= (TransferMessage)msg;
                                else if( msg instanceof TransferContent)
                                {
                                    if( response== null)
                                        throw new RequestHandlerException( BAD_RESPONSE, "response message not found before AgentContent");

                                    TransferContent content= (TransferContent)msg;
                                    ByteBuf buf= content.retain().content();
                                    if( response.content()== null)
                                        response.setContent( new FileData( TransferMessageUtil.getContentLength( response, 0), environment));
                                    response.content().addContent( buf, false);
                                }
                            }

                            @Override
                            public void exceptionCaught( ChannelHandlerContext ctx, Throwable cause)
                            {
                                logger.error( "exception caught", cause);
                                ctx.channel().close();
                                fail();
                            }
                        });
                }

            });

        return bootstrap.connect( server)
                .syncUninterruptibly()
                .channel();
    }

    
    @Test
    @DirtiesContext
    public void getFixedContentRequest() throws Exception
    {
        Channel client= newClient( new InetSocketAddress( "127.0.0.1", 8025));
        latch= new CountDownLatch( 1);

        TransferMessage request= new TransferMessage( INFO);
        request.setUri( HEALTH_).headers().add( AGENT_TYPE, AGENT);

        try
        {
            client.writeAndFlush( request);
            latch.await();

            latch= new CountDownLatch( 1);

            TransferResponseCode rsCode= response.headers().getResponseCode();
            if( SUCCESS== ResponseCode.valueOf( rsCode.code()))
            {
                String statusCode= response.headers().get( TransferHeaderNames.REASON);
                Health health= Health.status( statusCode).build();
                if( health.getStatus().equals( Status.UP))
                {
                    request= new TransferMessage( TransferCommand.GET);

                    String uri= TransferMessageUtil.encodedUri( "localhost", 8025, "fixed-content.txt",
                            OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"),
                            OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"),
                            OptionParameter.param( SITE, "biz1"));

                    request.headers().add( AGENT_TYPE, AGENT).add( TRANSFER_SOURCE_URI, uri);

                    client.writeAndFlush( request).syncUninterruptibly();
                    assertThat( latch.await( 8, SECONDS), is( true));

                    File valid= new File( environment.getRepository().getBaseDir()+ File.separator+ "received-content.dat");
                    assertThat( valid.exists(), is( true));

                    FileUtil.deleteFile( valid);
                    FileUtil.removeDir( new File( "./src/test/resources/tmp"));
                }
            }
            else
                fail();
        }
        finally
        {
            logger.debug( "localhost {}", ( (InetSocketAddress)client.localAddress()).getAddress().getHostAddress());
            logger.debug( "remote {}", ( (InetSocketAddress)client.remoteAddress()).getAddress().getHostAddress());

            client.close();
            GROUP.shutdownGracefully();
        }
        
        logger.info( "getFixedContentRequest test... passed");
    }
    
    @Test
    @DirtiesContext
    public void getChunkedContentRequest() throws Exception
    {
        Channel client= newClient( new InetSocketAddress( "127.0.0.1", 8025));
        latch= new CountDownLatch( 1);

        TransferMessage request= new TransferMessage( INFO);
        request.setUri( HEALTH_).headers().add( AGENT_TYPE, AGENT);

        try
        {
            client.writeAndFlush( request);
            latch.await();

            latch= new CountDownLatch( 1);

            TransferResponseCode rsCode= response.headers().getResponseCode();
            if( ResponseCode.SUCCESS== ResponseCode.valueOf( rsCode.code()))
            {
                String statusCode= response.headers().get( REASON);
                Health health= Health.status( statusCode).build();
                if( health.getStatus().equals( UP))
                {
                    request= new TransferMessage( GET);

                    String uri= TransferMessageUtil.encodedUri( "localhost", 8025, "chunked-content.jar",
                            OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"),
                            OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"),
                            OptionParameter.param( SITE, "biz1"));

                    request.headers().add( AGENT_TYPE, AGENT).add( TRANSFER_SOURCE_URI, uri);

                    client.writeAndFlush( request).syncUninterruptibly();
                    assertThat( latch.await( 8, SECONDS), is( true));

                    File valid= new File( environment.getRepository().getBaseDir()+ File.separator+ "received-content.dat");
                    assertThat( valid.exists(), is( true));
                    assertThat( valid.length(), equalTo( new File( environment.getRepository().getBaseDir()+
                                    File.separator+ "chunked-content.jar").length()));

                    FileUtil.deleteFile( valid);
                    FileUtil.removeDir( new File( "./src/test/resources/tmp"));
                }
            }
            else
                fail();
        }
        finally
        {
            client.close();
            GROUP.shutdownGracefully();
        }
        
        logger.info( "getChunkedContentRequest test... passed");
    }
}
