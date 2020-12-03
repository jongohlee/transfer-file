/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file;

import static easymaster.transfer.file.protocol.ResponseCode.CLIENT_ERROR;
import static easymaster.transfer.file.protocol.ResponseCode.SERVER_ERROR;
import static easymaster.transfer.file.protocol.TransferCommand.TRANSFER;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT_TYPE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.CONNECTION;
import static easymaster.transfer.file.protocol.TransferHeaderNames.DESTINATION_AGENT;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_DESTINATION_URI;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_INTERCEPTOR;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_SOURCE_URI;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_TIMEOUT_SECONDS;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_VALIDATION;
import static easymaster.transfer.file.protocol.TransferHeaderValues.AGENT;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CLIENT;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CLOSE;
import static easymaster.transfer.file.protocol.TransferHeaderValues.VALIDATION_ON;
import static easymaster.transfer.file.protocol.TransferResponseCode.SOURCE_FILE_NOT_FOUND;
import static easymaster.transfer.file.util.OptionParameter.AFTER_TRANSFER;
import static easymaster.transfer.file.util.OptionParameter.CREATE_ACK;
import static easymaster.transfer.file.util.OptionParameter.INTERCEPTOR;
import static easymaster.transfer.file.util.OptionParameter.ON_EXIST;
import static easymaster.transfer.file.util.OptionParameterValues.BACKUP;
import static easymaster.transfer.file.util.OptionParameterValues.OVERWRITE_ONEXIST;
import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static io.netty.channel.ChannelOption.SO_KEEPALIVE;
import static io.netty.channel.ChannelOption.TCP_NODELAY;
import static io.netty.handler.codec.compression.ZlibWrapper.GZIP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import easymaster.transfer.file.config.TransferEnvironment;
import easymaster.transfer.file.config.TransferServerConfiguration;
import easymaster.transfer.file.handler.HandlerResponse;
import easymaster.transfer.file.handler.TransferCommandRequestHandler;
import easymaster.transfer.file.protocol.LastTransferContent;
import easymaster.transfer.file.protocol.ResponseCode;
import easymaster.transfer.file.protocol.TransferMessage;
import easymaster.transfer.file.protocol.TransferMessageClientCodec;
import easymaster.transfer.file.protocol.TransferObject;
import easymaster.transfer.file.protocol.TransferResponseCode;
import easymaster.transfer.file.util.FileUtil;
import easymaster.transfer.file.util.OptionParameter;
import easymaster.transfer.file.util.OptionParameterValues;
import easymaster.transfer.file.util.TransferMessageUtil;
import io.netty.bootstrap.Bootstrap;
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
                "transfer.repository.backup-dir=./src/test/resources/backup",
                "transfer.repository.sites.biz1.base-dir=./src/test/resources",
                "transfer.ssl=on", 
                "transfer.chunk-size=1048576", 
                "transfer.tcp-port=8025", 
                "transfer.bind=127.0.0.1"})
@ActiveProfiles( "test")
public class TransferTransferRequestHandlerTest
{

    private Logger logger= LoggerFactory.getLogger( TransferTransferRequestHandlerTest.class);

    @Autowired
    private ApplicationContext applicationContext;

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

                                if( msg instanceof TransferMessage)
                                    response= (TransferMessage)msg;

                                if( msg instanceof LastTransferContent)
                                {
                                    if( response!= null && response.headers().contains( CONNECTION, CLOSE, true))
                                        ctx.close();

                                    Thread.sleep( 3000L);
                                    
                                    latch.countDown();
                                }
                            }

                            @Override
                            public void exceptionCaught( ChannelHandlerContext ctx, Throwable cause)
                            {
                                logger.error( cause.getMessage(), cause);
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

//    @Test
    @DirtiesContext
    public void notExistTransferRequest() throws Exception
    {
        Channel client= newClient( new InetSocketAddress( "127.0.0.1", 8025));
        latch= new CountDownLatch( 1);

        TransferMessage request= new TransferMessage( TRANSFER);
        request.headers().add( AGENT_TYPE, CLIENT);

        String srcUri1= TransferMessageUtil.encodedUri( "192.168.12.15", 8025, "not-exist1.file",
                OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"),
                OptionParameter.param( AFTER_TRANSFER, BACKUP));

        String destUri1= TransferMessageUtil.encodedUri( "192.168.12.15", 8025, "20201010/not-exist1.gzip",
                OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"),
                OptionParameter.param( CREATE_ACK, ".done"),
                OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST));

        String srcUri2= TransferMessageUtil.encodedUri( "192.168.12.15", 8025, "not-exist2.file",
                OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"),
                OptionParameter.param( AFTER_TRANSFER, BACKUP));

        String destUri2= TransferMessageUtil.encodedUri( "192.168.12.15", 8025, "20201010/not-exist.gzip",
                OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"),
                OptionParameter.param( CREATE_ACK, ".done"),
                OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST));

        request.headers()
            .add( AGENT_TYPE, AGENT)
            .add( TRANSFER_SOURCE_URI, srcUri1)
            .add( TRANSFER_DESTINATION_URI, destUri1)
            .add( TRANSFER_SOURCE_URI, srcUri2)
            .add( TRANSFER_DESTINATION_URI, destUri2)
            .add( TRANSFER_VALIDATION, VALIDATION_ON)
            .add( TRANSFER_TIMEOUT_SECONDS, 900)
            .add( TRANSFER_INTERCEPTOR, "simpleCustomTransferInterceptor");

        try
        {
            client.writeAndFlush( request).syncUninterruptibly();

            assertThat( latch.await( 20, SECONDS), is( true));


            TransferResponseCode rsCode= response.headers().getResponseCode();
            assertThat( ResponseCode.valueOf( rsCode.code()), equalTo( CLIENT_ERROR));
            assertThat( rsCode, equalTo( SOURCE_FILE_NOT_FOUND));

        }
        finally
        {
            client.close();
            GROUP.shutdownGracefully();
        }
        
        logger.info( "notExistTransferRequest test... passed");
    }

//    @Test
    @DirtiesContext
    public void notExistAgentTransferRequest() throws Exception
    {
        Channel client= newClient( new InetSocketAddress( "127.0.0.1", 8025));
        latch= new CountDownLatch( 1);

        TransferMessage request= new TransferMessage( TRANSFER);
        request.headers().add( AGENT_TYPE, CLIENT);

        String srcUri1= TransferMessageUtil.encodedUri( "192.168.12.15", 8025, "fixed-content.txt",
                OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"),
                OptionParameter.param( AFTER_TRANSFER, BACKUP));

        String destUri1= TransferMessageUtil.encodedUri( "20201010/fixed-content.gzip",
                OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"),
                OptionParameter.param( CREATE_ACK, ".done"),
                OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST));

        String srcUri2= TransferMessageUtil.encodedUri( "192.168.12.15", 8025, "fixed-content.txt",
                OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"),
                OptionParameter.param( AFTER_TRANSFER, BACKUP));

        String destUri2= TransferMessageUtil.encodedUri( "20201010/fixed-content.gzip",
                OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"),
                OptionParameter.param( CREATE_ACK, ".done"),
                OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST));


        request.headers()
            .add( AGENT_TYPE, AGENT)
            .add( TRANSFER_SOURCE_URI, srcUri1)
            .add( DESTINATION_AGENT, "192.168.12.15:8024;192.168.12.16:8024")
            .add( TRANSFER_DESTINATION_URI, destUri1)
            .add( TRANSFER_SOURCE_URI, srcUri2)
            .add( DESTINATION_AGENT, "192.168.12.15:8024;192.168.12.16:8024")
            .add( TRANSFER_DESTINATION_URI, destUri2)
            .add( TRANSFER_VALIDATION, VALIDATION_ON)
            .add( TRANSFER_TIMEOUT_SECONDS, 1);

        try
        {
            client.writeAndFlush( request).syncUninterruptibly();
            assertThat( latch.await( 20, SECONDS), is( true));


            TransferResponseCode rsCode= response.headers().getResponseCode();
            assertThat( ResponseCode.valueOf( rsCode.code()), equalTo( SERVER_ERROR));
//            assertThat( rsCode, equalTo( TransferResponseCode.TIMEOUT_OCCURRED));

        }
        finally
        {
            client.close();
            GROUP.shutdownGracefully();
        }
        
        logger.info( "notExistAgentTransferRequest test... passed");
    }

    @Test
    @DirtiesContext
    public void existAgentTransferRequest() throws Exception
    {
        TransferCommandRequestHandler handler= new TransferCommandRequestHandler( null, applicationContext, environment);

        Method md= handler.getClass().getDeclaredMethod( "handleTransferCommandRequest", new Class[] { TransferMessage.class});
        md.setAccessible( true);

        TransferMessage request= new TransferMessage( TRANSFER);
        request.headers().add( AGENT_TYPE, CLIENT);

        String srcUri1= TransferMessageUtil.encodedUri( "localhost", 8025, "chunked-content.jar",
                OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"));

        String destUri1= TransferMessageUtil.encodedUri( "/backup/20201010/chunked-content.bak",
                OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"),
                OptionParameter.param( CREATE_ACK, ".done"),
                OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST));

        String srcUri2= TransferMessageUtil.encodedUri( "localhost", 8025, "fixed-content.txt",
                OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"));

        String destUri2= TransferMessageUtil.encodedUri( "/backup/20201010/fixed-content.bak",
                OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"),
                OptionParameter.param( CREATE_ACK, ".done"),
                OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST));


        request.headers()
            .add( AGENT_TYPE, AGENT)
            .add( TRANSFER_SOURCE_URI, srcUri1)
            .add( DESTINATION_AGENT, "192.168.12.15:8025;127.0.0.1:8025")
            .add( TRANSFER_DESTINATION_URI, destUri1)
            .add( TRANSFER_SOURCE_URI, srcUri2)
            .add( DESTINATION_AGENT, "192.168.12.15:8025;127.0.0.1:8025")
            .add( TRANSFER_DESTINATION_URI, destUri2)
            .add( TRANSFER_VALIDATION, VALIDATION_ON)
            .add( TRANSFER_TIMEOUT_SECONDS, 30);

        try
        {
            HandlerResponse answer= (HandlerResponse)md.invoke( handler, new Object[] { request});
            TransferMessage response= answer.getResponse();
            logger.debug( "response: {}", response);

            File valid1= new File( environment.getRepository().getBaseDir()+ "/backup/20201010/chunked-content.bak");
            File ack1= new File( environment.getRepository().getBaseDir()+ "/backup/20201010/chunked-content.done");

            File valid2= new File( environment.getRepository().getBaseDir()+ "/backup/20201010/fixed-content.bak");
            File ack2= new File( environment.getRepository().getBaseDir()+ "/backup/20201010/fixed-content.done");

            assertThat( valid1.exists(), is( true));
            assertThat( ack1.exists(), is( true));
            assertThat( valid2.exists(), is( true));
            assertThat( ack2.exists(), is( true));

        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
            throw e;
        }
        finally
        {
            FileUtil.removeDir( new File( "./src/test/resources/backup"));
            FileUtil.removeDir( new File( "./src/test/resources/tmp"));
        }
        
        logger.info( "existAgentTransferRequest test... passed");
    }

    @Test
    @DirtiesContext
    public void existAgentTransferRequestUris() throws Exception
    {
        TransferCommandRequestHandler handler= new TransferCommandRequestHandler( null, applicationContext, environment);

        Method md= handler.getClass().getDeclaredMethod( "handleTransferCommandRequest", new Class[] { TransferMessage.class});
        md.setAccessible( true);

        TransferMessage request= new TransferMessage( TRANSFER);

        String srcUri1= TransferMessageUtil.encodedUri( "localhost", 8025, "parallel-content.zip");
        String destUri1= TransferMessageUtil.encodedUri( "127.0.0.1", 8025, "/backup/20201010/parallel-content.bak",
                OptionParameter.param( OptionParameter.ON_EXIST, OptionParameterValues.OVERWRITE_ONEXIST));

        String srcUri2= TransferMessageUtil.encodedUri( "localhost", 8025, "fixed-content.txt");
        String destUri2= TransferMessageUtil.encodedUri( "127.0.0.1", 8025, "/backup/20201010/fixed-content.bak",
                OptionParameter.param( OptionParameter.ON_EXIST, OptionParameterValues.OVERWRITE_ONEXIST));
//                OptionParameter.param( OptionParameter.OVERWRITE, OptionParameterValues.NOT_OVERWRITE));

        request.headers()
            .add( AGENT_TYPE, CLIENT)
            .add( TRANSFER_SOURCE_URI, srcUri1)
            .add( TRANSFER_DESTINATION_URI, destUri1)
            .add( TRANSFER_SOURCE_URI, srcUri2)
            .add( TRANSFER_DESTINATION_URI, destUri2)
            .add( TRANSFER_VALIDATION, VALIDATION_ON)
//            .add( TransferHeaderNames.TRANSFER_VALIDATION, TransferHeaderValues.VALIDATION_OFF)
            .add( TRANSFER_TIMEOUT_SECONDS, 30);

        try
        {
            HandlerResponse answer= (HandlerResponse)md.invoke( handler, new Object[] { request});
            TransferMessage response= answer.getResponse();
            logger.debug( "response: {}", response);

            File valid1= new File( environment.getRepository().getBaseDir()+ "/backup/20201010/parallel-content.bak");
            File valid2= new File( environment.getRepository().getBaseDir()+ "/backup/20201010/fixed-content.bak");

            assertThat( valid1.exists(), is( true));
            assertThat( valid2.exists(), is( true));

        }
        catch( Exception e)
        {
            logger.error( "error caught", e);
            throw e;
        }
        finally
        {
//            Thread.currentThread().sleep( 30000L);

            FileUtil.removeDir( new File( "./src/test/resources/backup"));
            FileUtil.removeDir( new File( "./src/test/resources/tmp"));
        }
        
        logger.info( "existAgentTransferRequestUris test... passed");
    }
}
