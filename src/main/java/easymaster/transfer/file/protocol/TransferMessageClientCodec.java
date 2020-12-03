/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.protocol;

import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT_TYPE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.REMOTE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.RESPONSE_CODE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_ENCODING;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CLIENT;

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

public class TransferMessageClientCodec extends CombinedChannelDuplexHandler<TransferMessageDecoder, TransferMessageEncoder>
{
    private Logger logger= LoggerFactory.getLogger( TransferMessageClientCodec.class);
    
    private final AtomicLong requestResponseCounter= new AtomicLong();
    
    private final boolean failOnMissingResponse;
    
    public TransferMessageClientCodec()
    {
        this( true);
    }
    
    public TransferMessageClientCodec( boolean failOnMissingResponse)
    {
        this.failOnMissingResponse= failOnMissingResponse;
        init( new Decoder(), new Encoder());
    }

    private final class Encoder extends TransferMessageEncoder
    {
        @Override
        public void encode( ChannelHandlerContext ctx, TransferObject msg, List<Object> out) throws Exception
        {
            if( msg instanceof TransferMessage)
            {
                TransferMessage message= (TransferMessage)msg;
                long contentLength= TransferMessageUtil.getContentLength( message, -1);
                if( contentLength<= 0)
                    message.headers().remove( TRANSFER_ENCODING);

                message.headers().remove( RESPONSE_CODE);

                if( message.headers().contains( AGENT))
                    message.headers().remove( AGENT);

                SocketAddress local= ctx.channel().localAddress();
                if( local instanceof InetSocketAddress)
                    message.headers().add( AGENT, ( (InetSocketAddress)local).getAddress().getHostAddress());
                else
                    message.headers().add( AGENT, InetAddress.getLocalHost().getHostAddress());

                SocketAddress remote= ctx.channel().remoteAddress();
                if( remote instanceof InetSocketAddress)
                    message.headers().add( REMOTE, ( (InetSocketAddress)remote).getAddress().getHostAddress());

                message.headers().add( AGENT_TYPE, CLIENT);
            }

            super.encode( ctx, msg, out);
            // request encoded !!!!
            if( failOnMissingResponse && msg instanceof TransferMessage)
                requestResponseCounter.incrementAndGet();
        }

    }

    private final class Decoder extends TransferMessageDecoder
    {
        @Override
        public void decode( ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception
        {
            int before= out.size();
            super.decode( ctx, in, out);
            if( failOnMissingResponse)
            {
                int after= out.size();
                for( int i= before; i< after; i++)
                {
                    Object msg= out.get( i);
                    // response for request accepted (finally)
                    if( msg!= null && msg instanceof LastTransferContent)
                        requestResponseCounter.decrementAndGet();
                }
            }
        }

        @Override
        public void channelInactive( ChannelHandlerContext ctx) throws Exception
        {
            super.channelInactive( ctx);

            long missingResponses= requestResponseCounter.get();
            if( failOnMissingResponse && missingResponses> 0)
            {
                logger.warn( "channel gone inactive with {} missing response(s)", missingResponses);
                ctx.fireExceptionCaught( new PrematureChannelClosureException( "channel gone inactive with "+
                        missingResponses+ " missing response(s)"));
            }
        }
    }
}
