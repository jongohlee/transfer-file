/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.config;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.context.ShutdownEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.event.EventListener;

import easymaster.transfer.file.TransferServer;
import easymaster.transfer.file.TransferServerAgent;
import easymaster.transfer.file.handler.TransferServerInitializer;
import easymaster.transfer.file.session.ResourceSessionManager;
import easymaster.transfer.file.util.TransferConstants;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * @author Jongoh Lee
 */

@Configuration
@ImportResource( "${context.location}/custom-context.xml")
@EnableConfigurationProperties( TransferEnvironment.class)
public class TransferServerConfiguration
{
    private Logger logger= LoggerFactory.getLogger( TransferServerConfiguration.class);
    
    @Autowired
    private TransferEnvironment environment;
    
    private NioEventLoopGroup bossGroup;
    
    private NioEventLoopGroup workerGroup;
    
    @Bean
    public TransferServerAgent serverAgent( ApplicationContext applicationContext)
    {
        if( environment.isValidation())
            environment.validateRepository();
        
        this.bossGroup= new NioEventLoopGroup( environment.getBossCount());
        this.workerGroup= new NioEventLoopGroup( environment.getWorkerCount());
        ServerBootstrap bootstrap= new ServerBootstrap();
        bootstrap.group( this.bossGroup, this.workerGroup)
            .channel( NioServerSocketChannel.class)
            .handler( new LoggingHandler( LogLevel.INFO))
            .childHandler( applicationContext.getBean( ChannelInitializer.class))
            .option( ChannelOption.SO_BACKLOG, environment.getBacklog())
            .childOption( ChannelOption.SO_KEEPALIVE, environment.isKeepAlive());

        InetSocketAddress address= new InetSocketAddress( environment.getBind(), environment.getTcpPort());
        environment.getCustom().put( TransferConstants.BIND_ADDRESS, address.getAddress().getHostAddress());
        
        return new TransferServerAgent( bootstrap, new InetSocketAddress( environment.getBind(), environment.getTcpPort()));
    }
    
    @Bean
    public ChannelInitializer<SocketChannel> channelInitializer( ApplicationContext applicationContext) throws Exception
    {
        SslContext sslContext= null;
        if( environment.isSsl())
        {
            // self-signed인증서를 사용하고 있다. 
            // sign인증서를 사용해야 하는 경우 사용자 지정 인증서를 사용할 수 있도록 설정 항목을 추가하고 아래 내용을 변경한다.
            SelfSignedCertificate ssc= new SelfSignedCertificate();
            sslContext= SslContextBuilder.forServer( ssc.certificate(), ssc.privateKey()).build();
        }
        return new TransferServerInitializer( applicationContext, environment, sslContext);
    }
    
    @Bean( name= "agentControl")
    public SmartLifecycle agentServerContext( ApplicationContext applicationContext)
    {
        return new SmartLifecycle(){
            
            CountDownLatch latch;
            AtomicBoolean started= new AtomicBoolean();
            TransferServerAgent agent;

            // File TransferServer Life Cycle : Step 1 - start
            @Override
            public void start()
            {
                logger.info( "File Transfer Server is starting............................................");
                logger.info( "binding address : {}", environment.getBind());
                logger.info( "binding port : {}", environment.getTcpPort());
                logger.info( "boss cound : {}", environment.getBossCount());
                logger.info( "worker count : {}", environment.getWorkerCount());
                logger.info( "backlog : {}", environment.getBacklog());
                logger.info( "keep-alive : {}", environment.isKeepAlive());
                
                agent= applicationContext.getBean( TransferServerAgent.class);
                try
                {
                    agent.start();
                    ResourceSessionManager.start( environment.getSessionTimeout().toMillis());
                    TransferCommandExecutor.start( environment.getWorkerCount());
                    started.getAndSet( true);
                    latch= new CountDownLatch( 2);
                    logger.debug( "File Transfer Server is started [{}].", started.get());
                }
                catch( Exception e)
                {
                    logger.error( "v staring is failed.", e);
                }            
            }

            // File TransferServer Life Cycle : Step 2 - Ready
            // Netty ServerChannel is Listening
            @EventListener
            public void ready( ApplicationReadyEvent ready) throws Exception
            {
                Thread awaitThread= new Thread( "server") {
                    @Override public void run()
                    {
                        try
                        {
                            logger.debug( "File Transfer Server is listening on localhost:[{}]", environment.getTcpPort());
                            latch.countDown();
                            agent.block();
                        }
                        catch( Exception e) { /* ignore */} 
                    }
                };
                awaitThread.setDaemon( false);
                awaitThread.start();
            }
            
            // File TransferServer Life Cycle : Step 3 - Running
            // SpringApplication wait for terminate signal
            @EventListener
            public void await( TransferServer.AgentServerRunning running) throws Exception
            {
                latch.await();
            }

            @Override
            public boolean isRunning()
            {
                return started.get();
            }

            // File TransferServer Life Cycle : Step 4 - Stop
            // SpringApplication stopping with terminate signal
            @Override
            public void stop( Runnable runnable)
            {
                stop();
                runnable.run();
            }
            
            @Override
            public void stop()
            {
                if( !started.get())
                    return;
                logger.info( "File Transfer Server will be stopped............................................");
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
                ResourceSessionManager.shutdown();
                TransferCommandExecutor.shutdown();
                started.getAndSet( false);
                latch.countDown();
            }
            
            @Override
            public int getPhase(){ return Integer.MAX_VALUE; }

            @Override
            public boolean isAutoStartup(){ return true; }
        };
    }
    
    /**
     * HeaderIndicator for JMX
     * @param context
     * @return health indicator for JMX
     */
    @Bean( "agentHealthIndicator")
    public HealthIndicator fileAgentHealthIndicator( final ApplicationContext context)
    {
        return new HealthIndicator(){

            @Override
            public Health health()
            {
                if( context.getBean( SmartLifecycle.class).isRunning())
                    return Health.up().build();
                else
                    return Health.down().build();
            }
        };
    }

    @Bean
    @ConditionalOnAvailableEndpoint
    public ShutdownEndpoint shutdownEndpoint( final ConfigurableApplicationContext applicationContext) {

        return new ShutdownEndpoint() {
            @Override
            @WriteOperation
            public Map<String, String> shutdown()
            {
                try
                {
                    return Collections.unmodifiableMap( Collections.singletonMap( "message", "File Transfer Server Shutting down, bye..."));
                }
                finally
                {
                    logger.info( "applicationContext will be closed.");
                    Thread thread = new Thread(this::performShutdown);
                    thread.setContextClassLoader(getClass().getClassLoader());
                    thread.start();
                }
            }

            private void performShutdown() {
                try{ Thread.sleep(500L); }catch (InterruptedException ex) { Thread.currentThread().interrupt();}
                applicationContext.close();
            }
        };
    }

}
