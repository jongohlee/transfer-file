/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.client;

import static easymaster.transfer.file.protocol.TransferHeaderNames.CONNECTION;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_ENCODING;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CHUNKED;
import static easymaster.transfer.file.protocol.TransferResponseCode.BAD_RESPONSE;
import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.channel.ChannelFutureListener.CLOSE;
import static io.netty.util.concurrent.GlobalEventExecutor.INSTANCE;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import easymaster.transfer.file.protocol.FileData;
import easymaster.transfer.file.protocol.LastTransferContent;
import easymaster.transfer.file.protocol.TransferContent;
import easymaster.transfer.file.protocol.TransferHeaderValues;
import easymaster.transfer.file.protocol.TransferHeaders;
import easymaster.transfer.file.protocol.TransferMessage;
import easymaster.transfer.file.protocol.TransferObject;
import easymaster.transfer.file.util.TransferMessageUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;

/**
 * @author Jongoh Lee
 *
 */

public class TransferClientHandler extends SimpleChannelInboundHandler<TransferObject>
{

    private Logger logger= LoggerFactory.getLogger( TransferClientHandler.class);

    private TransferMessage response;

    private DefaultPromise<TransferMessage> responseFuture;

    private final File baseDir;

    public TransferClientHandler( File basedir)
    {
        this.baseDir= basedir;
    }

    @Override
    public void channelRead0( ChannelHandlerContext ctx, TransferObject message) throws Exception
    {
//        DecoderResult decoderResult= message.decoderResult();
//        if( logger.isDebugEnabled())
//        {
//            logger.debug( "decoderResult: [{}]", decoderResult.isSuccess());
//            logger.debug( "readed message {}", message.getClass());
//        }

        if( message instanceof LastTransferContent)
        {
            logger.debug( "LastTransferContent detected....");
            if( response== null)
                return;

            if( response.headers().contains( TRANSFER_ENCODING, CHUNKED, true) && response.content()!= null)
                response.content().addContent( EMPTY_BUFFER, true);

            logger.debug( "response commmand: {}", response.command());

            if( !responseFuture.trySuccess( response) && response.content()!= null)
            {
                logger.warn( "trySuccess failed. response content will be released");
                ReferenceCountUtil.release( response.content().release());
            }

            if( closeRequired( response.headers()))
                ctx.writeAndFlush( EMPTY_BUFFER).addListener( CLOSE);

            return;
        }

        if( message instanceof TransferMessage)
        {
            response= (TransferMessage)message;
            logger.debug( "command: [{}], uri: [{}]", response.command(), response.uri());
            logger.debug( "response message: [{}]", response.headers());
        }
        else if( message instanceof TransferContent)
        {
            if( response== null)
                throw new ResponseHandlerException( BAD_RESPONSE, "response message not found before TransferContent");

            TransferContent content= (TransferContent)message;
            ByteBuf buf= content.retain().content();
            
            if( response.content()== null)
                response.setContent( new FileData( TransferMessageUtil.getContentLength( response, 0), baseDir));

            response.content().addContent( buf, false);
        }
    }

    public Future<TransferMessage> sync() throws InterruptedException
    {
        return ( responseFuture= new DefaultPromise<TransferMessage>( INSTANCE));
    }


    @Override
    public void exceptionCaught( ChannelHandlerContext ctx, Throwable cause)
    {
        logger.error( "TransferClientHandler failed.", cause);
        responseFuture.setFailure( cause);
        ctx.close();
    }


    private boolean closeRequired( TransferHeaders headers)
    {
        return headers.contains( CONNECTION, TransferHeaderValues.CLOSE, true);
    }

}
