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
        
        // 네트워크 구간의 부하를 감소시키기 위해 압축 송수신을 사용
        // 빠른 응답을 위해 Chunk단위 전송
        // 1. 압축 / 해제 : ZibDecoder, ZipEncoder
        // 2. 메시지를 해석하여 처리 가능한 타입으로 변환 : TransferMessageServerCodec
        // 3. 해석된 메시지를 이용하여 사용자 명령을 처리 : TransferServerHandler
        pipeLine
            .addLast( ZlibCodecFactory.newZlibDecoder( ZlibWrapper.GZIP))
            .addLast( ZlibCodecFactory.newZlibEncoder( ZlibWrapper.GZIP))
            .addLast( new TransferMessageServerCodec())
            .addLast( new ChunkedWriteHandler())
            .addLast( new TransferServerHandler( this.applicationContext, this.environment));
    }
}
