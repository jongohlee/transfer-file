/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file;

import static easymaster.transfer.file.handler.TransferAction.SHUTDOWN_;
import static easymaster.transfer.file.handler.TransferInfo.EXIST_;
import static easymaster.transfer.file.handler.TransferInfo.HEALTH_;
import static easymaster.transfer.file.handler.TransferInfo.INFO_;
import static easymaster.transfer.file.protocol.TransferCommand.ACTION;
import static easymaster.transfer.file.protocol.TransferCommand.INFO;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT_TYPE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.CONNECTION;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_SOURCE_URI;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CLIENT;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CLOSE;
import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static io.netty.channel.ChannelOption.SO_KEEPALIVE;
import static io.netty.channel.ChannelOption.TCP_NODELAY;
import static io.netty.handler.codec.compression.ZlibWrapper.GZIP;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import easymaster.transfer.file.config.TransferServerConfiguration;
import easymaster.transfer.file.protocol.LastTransferContent;
import easymaster.transfer.file.protocol.TransferMessage;
import easymaster.transfer.file.protocol.TransferMessageClientCodec;
import easymaster.transfer.file.protocol.TransferObject;
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
public class TransferInfoRequestHandlerTest
{

    private Logger logger= LoggerFactory.getLogger( TransferInfoRequestHandlerTest.class);

    private EventLoopGroup GROUP;

    private CountDownLatch latch;

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

                            private TransferMessage response;
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

    @Test
    @DirtiesContext
    public void infoHealthRequest() throws Exception
    {
        Channel client= newClient( new InetSocketAddress( "127.0.0.1", 8025));
        latch= new CountDownLatch( 1);

        TransferMessage request= new TransferMessage( INFO);
        request.setUri( HEALTH_);
        request.headers().add( AGENT_TYPE, CLIENT);

        try
        {
            client.writeAndFlush( request).syncUninterruptibly();
            assertThat( latch.await( 2, SECONDS), is( true));
        }
        finally
        {
            client.close();
            GROUP.shutdownGracefully();
        }
        
        logger.info( "infoHealthRequest test... passed");
    }

    @Test
    @DirtiesContext
    public void infoInfoRequest() throws Exception
    {
        Channel client= newClient( new InetSocketAddress( "127.0.0.1", 8025));
        latch= new CountDownLatch( 1);

        TransferMessage request= new TransferMessage( INFO);
        request.setUri( INFO_);
        request.headers().add( AGENT_TYPE, CLIENT);

        try
        {
            client.writeAndFlush( request).syncUninterruptibly();
            assertThat( latch.await( 3, SECONDS), is( true));
        }
        finally
        {
            client.close();
            GROUP.shutdownGracefully();
        }
        
        logger.info( "infoInfoRequest test... passed");
    }

    @Test
    @DirtiesContext
    public void infoExistRequest() throws Exception
    {
        Channel client= newClient( new InetSocketAddress( "127.0.0.1", 8025));
        latch= new CountDownLatch( 1);

        TransferMessage request= new TransferMessage( INFO);
        request.setUri( EXIST_);
        request.headers().add( AGENT_TYPE, CLIENT);
        String uri= TransferMessageUtil.encodedUri( "127.0.0.1", 8025, "/fixed-content.done", new OptionParameter[] {});
        request.headers().add( TRANSFER_SOURCE_URI, uri);

        try
        {
            client.writeAndFlush( request).syncUninterruptibly();
            assertThat( latch.await( 2, SECONDS), is( true));
        }
        finally
        {
            client.close();
            GROUP.shutdownGracefully();
        }
        
        logger.info( "infoExistRequest test... passed");
    }

    @Test
    @DirtiesContext
    public void infoNotExistRequest() throws Exception
    {
        Channel client= newClient( new InetSocketAddress( "127.0.0.1", 8025));
        latch= new CountDownLatch( 1);

        TransferMessage request= new TransferMessage( INFO);
        request.setUri( EXIST_);
        request.headers().add( AGENT_TYPE, CLIENT);
        String uri= TransferMessageUtil.encodedUri( "127.0.0.1", 8025, "/20201010/fixed-content.done",
                OptionParameter.param( "site", "biz1"));
        request.headers().add( TRANSFER_SOURCE_URI, uri);

        try
        {
            client.writeAndFlush( request).syncUninterruptibly();
            assertThat( latch.await( 2, SECONDS), is( true));
        }
        finally
        {
            client.close();
            GROUP.shutdownGracefully();
        }
        
        logger.info( "infoNotExistRequest test... passed");
    }
    
    @Test
    @DirtiesContext
    public void actionShutdownRequest() throws Exception
    {
        Channel client= newClient( new InetSocketAddress( "127.0.0.1", 8025));
        latch= new CountDownLatch( 1);

        TransferMessage request= new TransferMessage( ACTION);
        request.setUri( SHUTDOWN_);
        request.headers().add( AGENT_TYPE, CLIENT);

        try
        {
            client.writeAndFlush( request).syncUninterruptibly();
            assertThat( latch.await( 2, SECONDS), is( true));
        }
        finally
        {
            client.close();
            GROUP.shutdownGracefully();
        }
        
        logger.info( "actionShutdownRequest test... passed");
    }

}
