/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file;

import static easymaster.transfer.file.handler.TransferInfo.HEALTH_;
import static easymaster.transfer.file.protocol.ResponseCode.SUCCESS;
import static easymaster.transfer.file.protocol.TransferCommand.DELETE;
import static easymaster.transfer.file.protocol.TransferCommand.INFO;
import static easymaster.transfer.file.protocol.TransferCommand.LIST;
import static easymaster.transfer.file.protocol.TransferCommand.PUT;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT_TYPE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.CONNECTION;
import static easymaster.transfer.file.protocol.TransferHeaderNames.CONTENT_LENGTH;
import static easymaster.transfer.file.protocol.TransferHeaderNames.REASON;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_DESTINATION_URI;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_ENCODING;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_SOURCE_URI;
import static easymaster.transfer.file.protocol.TransferHeaderValues.AGENT;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CHUNKED;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CLOSE;
import static easymaster.transfer.file.util.OptionParameter.CREATE_ACK;
import static easymaster.transfer.file.util.OptionParameter.INTERCEPTOR;
import static easymaster.transfer.file.util.OptionParameter.ON_EXIST;
import static easymaster.transfer.file.util.OptionParameter.SITE;
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
import static org.springframework.boot.actuate.health.Status.UP;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ResourceUtils;

import easymaster.transfer.file.config.TransferEnvironment;
import easymaster.transfer.file.config.TransferServerConfiguration;
import easymaster.transfer.file.protocol.FileData;
import easymaster.transfer.file.protocol.LastTransferContent;
import easymaster.transfer.file.protocol.ResponseCode;
import easymaster.transfer.file.protocol.TransferChunkedContentEncoder;
import easymaster.transfer.file.protocol.TransferMessage;
import easymaster.transfer.file.protocol.TransferMessageClientCodec;
import easymaster.transfer.file.protocol.TransferObject;
import easymaster.transfer.file.protocol.TransferResponseCode;
import easymaster.transfer.file.util.FileUtil;
import easymaster.transfer.file.util.OptionParameter;
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
                "transfer.repository.sites.biz1.base-dir=./src/test/resources",
                "transfer.ssl=on", 
                "transfer.tcp-port=8025", 
                "transfer.bind=127.0.0.1"})
@ActiveProfiles( "test")
public class TransferPutDeleteRequestHandlerTest
{
    private Logger logger= LoggerFactory.getLogger( TransferPutDeleteRequestHandlerTest.class);
    
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

                                    latch.countDown();
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
    public void putFixedContentRequest() throws Exception
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
                String statusCode= response.headers().get( REASON);
                Health health= Health.status( statusCode).build();
                if( health.getStatus().equals( UP))
                {
                    request= new TransferMessage( PUT);

                    File fs= ResourceUtils.getFile( environment.getRepository().getBaseDir()+ File.separator+ "fixed-content.txt");
                    FileData fdata= new FileData( fs.length());
                    fdata.setContent( fs);
                    request.setContent( fdata);

                    String srcUri= TransferMessageUtil.encodedUri( "127.0.0.1", 8025, "fixed-content.txt", new OptionParameter[] {});

                    String targetUri= TransferMessageUtil.encodedUri( "localhost", 8025, 
                            "/backup/"+ new SimpleDateFormat( "yyyyMMdd").format( new Date())+ "/fixed-content.bak",
                            OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"),
                            OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"),
                            OptionParameter.param( SITE, "biz1"));

                    request.headers()
                        .add( AGENT_TYPE, AGENT)
                        .add( CONTENT_LENGTH, fs.length())
                        .add( TRANSFER_SOURCE_URI, srcUri)
                        .add( TRANSFER_DESTINATION_URI, targetUri);

                    client.writeAndFlush( request).syncUninterruptibly();
                    assertThat( latch.await( 15, SECONDS), is( true));

                    String dest= "./src/test/resources/backup/"+ new SimpleDateFormat( "yyyyMMdd").format( new Date())+ "/fixed-content.bak";
                    File valid= new File( dest);

                    assertThat( valid.exists(), is( true));

                    FileUtil.removeDir( new File( "./src/test/resources/backup"));
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
        
        logger.info( "putFixedContentRequest test... passed");
    }

    @Test
    @DirtiesContext
    public void putChunkedContentRequest() throws Exception
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
                String statusCode= response.headers().get( REASON);
                Health health= Health.status( statusCode).build();
                if( health.getStatus().equals( UP))
                {
                    request= new TransferMessage( PUT);

                    File fs= ResourceUtils.getFile( environment.getRepository().getBaseDir()+ File.separator+ "chunked-content.jar");

                    String srcUri= TransferMessageUtil.encodedUri( "127.0.0.1", 8025, "chunked-content.jar", new OptionParameter[] {});

                    String targetUri= TransferMessageUtil.encodedUri( "localhost", 8025,
                            "/backup/"+ new SimpleDateFormat( "yyyyMMdd").format( new Date())+ "/chunked-content.bak",
                            OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"),
                            OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST),
                            OptionParameter.param( CREATE_ACK, ".ack"));

                    request.headers()
                        .add( AGENT_TYPE, AGENT)
                        .add( CONTENT_LENGTH, fs.length())
                        .add( TRANSFER_SOURCE_URI, srcUri)
                        .add( TRANSFER_DESTINATION_URI, targetUri)
                        .add( TRANSFER_ENCODING, CHUNKED);


                    client.writeAndFlush( request);

                    FileData fdata= new FileData( fs.length());
                    fdata.setContent( fs);
                    TransferChunkedContentEncoder chunk= new TransferChunkedContentEncoder( fdata, 1024* 1024);
                    client.writeAndFlush( chunk).syncUninterruptibly();

                    assertThat( latch.await( 15, SECONDS), is( true));

                    String dest= "./src/test/resources/backup/"+
                    new SimpleDateFormat( "yyyyMMdd").format( new Date())+ "/chunked-content.bak";
                    File valid= new File( dest);

                    assertThat( valid.exists(), is( true));
                    assertThat( valid.length(), equalTo( fs.length()));

                    FileUtil.removeDir( new File( "./src/test/resources/backup"));
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
        
        logger.info( "putChunkedContentRequest test... passed");
    }

    @Test
    @DirtiesContext
    public void deleteRequest() throws Exception
    {
        Channel client= newClient( new InetSocketAddress( "127.0.0.1", 8025));
        latch= new CountDownLatch( 1);

        TransferMessage request= new TransferMessage( INFO);
        request.setUri( HEALTH_).headers().add( AGENT_TYPE, AGENT);

        try
        {
            File dir= new File( "./src/test/resources/backup/"+ new SimpleDateFormat( "yyyyMMdd").format( new Date()));
            dir.mkdirs();
            File checkFs1= new File( dir, "check1.done");
            checkFs1.createNewFile();

            File checkFs2= new File( dir, "check2.done");
            checkFs2.createNewFile();
            assertThat( checkFs1.exists(), is( true));
            assertThat( checkFs2.exists(), is( true));

            client.writeAndFlush( request);
            latch.await();

            latch= new CountDownLatch( 1);

            TransferResponseCode rsCode= response.headers().getResponseCode();
            if( SUCCESS== ResponseCode.valueOf( rsCode.code()))
            {
                String statusCode= response.headers().get( REASON);
                Health health= Health.status( statusCode).build();
                if( health.getStatus().equals( UP))
                {
                    request= new TransferMessage( DELETE);
                    String uri= TransferMessageUtil.encodedUri( "localhost", 8025, "/backup/**/*.done", new OptionParameter[] {});

                    request.headers().add( TRANSFER_SOURCE_URI, uri);

                    client.writeAndFlush( request).syncUninterruptibly();
                    assertThat( latch.await( 10, SECONDS), is( true));

                    assertThat( checkFs1.exists(), is( false));
                    assertThat( checkFs2.exists(), is( false));

                    FileUtil.removeDir( new File( "./src/test/resources/backup"));
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
        
        logger.info( "deleteRequest test... passed");
    }

    @Test
    @DirtiesContext
    public void listRequest() throws Exception
    {
        Channel client= newClient( new InetSocketAddress( "127.0.0.1", 8025));
        latch= new CountDownLatch( 1);

        TransferMessage request= new TransferMessage( INFO);
        request.setUri( HEALTH_).headers().add( AGENT_TYPE, AGENT);

        try
        {
            File dir= new File( "./src/test/resources/backup/"+ new SimpleDateFormat( "yyyyMMdd").format( new Date()));
            dir.mkdirs();
            File checkFs1= new File( dir, "check1.done");
            checkFs1.createNewFile();

            File checkFs2= new File( dir, "check2.done");
            checkFs2.createNewFile();
            assertThat( checkFs1.exists(), is( true));
            assertThat( checkFs2.exists(), is( true));

            client.writeAndFlush( request);
            latch.await();

            latch= new CountDownLatch( 1);

            TransferResponseCode rsCode= response.headers().getResponseCode();
            if( SUCCESS== ResponseCode.valueOf( rsCode.code()))
            {
                String statusCode= response.headers().get( REASON);
                Health health= Health.status( statusCode).build();
                if( health.getStatus().equals( UP))
                {
                    request= new TransferMessage( LIST);
                    String uri= TransferMessageUtil.encodedUri( "localhost", 8025, "/backup/**/*", new OptionParameter[] {});

                    request.headers().add( TRANSFER_SOURCE_URI, uri);

                    client.writeAndFlush( request).syncUninterruptibly();
                    assertThat( latch.await( 10, SECONDS), is( true));

                    assertThat( checkFs1.exists(), is( true));
                    assertThat( checkFs2.exists(), is( true));

                    FileUtil.removeDir( new File( "./src/test/resources/backup"));
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
        
        logger.info( "listRequest test... passed");
    }
}
