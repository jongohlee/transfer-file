/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.handler;

import org.springframework.context.ApplicationContext;

import easymaster.transfer.file.config.TransferEnvironment;
import easymaster.transfer.file.protocol.TransferMessageServerCodec;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * @author Jongoh Lee
 *
 */

public class TransferServerInitializer extends ChannelInitializer<SocketChannel>
{
    private final ApplicationContext applicationContext;
    
    private final TransferEnvironment environment;
    
    private SslContext sslContext;
    
    public TransferServerInitializer( ApplicationContext applicationContext, TransferEnvironment environment, SslContext sslContext)
    {
        this.applicationContext= applicationContext;
        this.environment= environment;
        this.sslContext= sslContext;
    }
    
    @Override
    protected void initChannel( SocketChannel channel) throws Exception
    {
        ChannelPipeline pipeLine= channel.pipeline();
        if( this.sslContext!= null)
            pipeLine.addLast( this.sslContext.newHandler( channel.alloc()));
        
        pipeLine
            .addLast( ZlibCodecFactory.newZlibDecoder( ZlibWrapper.GZIP))
            .addLast( ZlibCodecFactory.newZlibEncoder( ZlibWrapper.GZIP))
            .addLast( new TransferMessageServerCodec())
            .addLast( new ChunkedWriteHandler())
            .addLast( new TransferServerHandler( this.applicationContext, this.environment));
    }
}
