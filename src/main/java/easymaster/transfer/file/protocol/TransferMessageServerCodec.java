/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.protocol;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import easymaster.transfer.file.util.TransferMessageUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.CombinedChannelDuplexHandler;
import io.netty.handler.codec.PrematureChannelClosureException;

/**
 * @author Jongoh Lee
 *
 */

public class TransferMessageServerCodec extends CombinedChannelDuplexHandler<TransferMessageDecoder, TransferMessageEncoder>
{
    private Logger logger= LoggerFactory.getLogger( TransferMessageServerCodec.class);
    
    private final AtomicLong requestResponseCounter= new AtomicLong();

    private final boolean failOnMissinResponse;

    public TransferMessageServerCodec()
    {
        this( true);
    }
    
    public TransferMessageServerCodec( boolean failOnMissingResponse)
    {
        this.failOnMissinResponse= failOnMissingResponse;
        init( new Decoder(), new Encoder());
    }

    @Override
    public void channelActive( ChannelHandlerContext ctx) throws Exception
    {
        super.channelActive( ctx);
    }
    
    @Override
    public void channelInactive( ChannelHandlerContext ctx) throws Exception
    {
        super.channelInactive( ctx);

        long missingResponses= requestResponseCounter.get();
        if( failOnMissinResponse && missingResponses> 0)
        {
            logger.warn( "channel gone inactive with {} missing response(s)", missingResponses);
            ctx.fireExceptionCaught( new PrematureChannelClosureException( "channel gone inactive with "+
                    missingResponses+ " missing response(s)"));
        }
    }
    
    private final class Encoder extends TransferMessageEncoder
    {
        @Override
        public void encode( ChannelHandlerContext ctx, TransferObject msg, List<Object> out) throws Exception
        {
            if( msg instanceof TransferMessage)
            {
                // 실제 연결 정보를 기준으로 요청 Agent 정보를 갱신한다. 
                TransferMessage message= (TransferMessage)msg;
                long contentLength= TransferMessageUtil.getContentLength( message, -1);
                if( contentLength< 0)
                    message.headers().remove( TransferHeaderNames.TRANSFER_ENCODING);
                
                if( message.headers().contains( TransferHeaderNames.AGENT))
                    message.headers().remove( TransferHeaderNames.AGENT);
                SocketAddress local= ctx.channel().localAddress();
                if( local instanceof InetSocketAddress)
                    message.headers().add( TransferHeaderNames.AGENT, ( (InetSocketAddress)local).getAddress().getHostAddress());
                else
                    message.headers().add( TransferHeaderNames.AGENT, InetAddress.getLocalHost().getHostAddress());
             
                SocketAddress remote= ctx.channel().remoteAddress();
                if( remote instanceof InetSocketAddress)
                    message.headers().add( TransferHeaderNames.REMOTE, ( (InetSocketAddress)remote).getAddress().getHostAddress());
            
                message.headers().add( TransferHeaderNames.AGENT_TYPE, TransferHeaderValues.AGENT);
            }
            
            // 전송 데이터를 메시지로 변환한다.
            super.encode( ctx, msg, out);
            if( failOnMissinResponse && msg instanceof TransferMessage)
                requestResponseCounter.decrementAndGet();
        }
    }
    
    private final class Decoder extends TransferMessageDecoder
    {
        @Override
        public void decode( ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception
        {
            int before= out.size();
            // 전달된 메시지를 해석한다.
            super.decode( ctx, in, out);
            if( failOnMissinResponse)
            {
                int after= out.size();
                for( int i= before; i< after; i++)
                {
                    Object msg= out.get( i);
                    if( msg!= null && msg instanceof TransferMessage)
                        requestResponseCounter.incrementAndGet();
                }
            }
        }
        
        @Override
        public void channelInactive( ChannelHandlerContext ctx) throws Exception
        {
            super.channelInactive( ctx);
        }
    }
}
