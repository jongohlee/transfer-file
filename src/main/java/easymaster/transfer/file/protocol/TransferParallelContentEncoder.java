/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.protocol;

import static easymaster.transfer.file.protocol.LastTransferContent.EMPTY_LAST_CONTENT;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.stream.ChunkedInput;

/**
 * @author Jongoh Lee
 *
 */

public class TransferParallelContentEncoder implements ChunkedInput<TransferContent>
{
    private Logger logger= LoggerFactory.getLogger( TransferParallelContentEncoder.class);

    private final ByteBuf content;

    private final int chunkSize;

    private long definedLength;

    private boolean lastChunkSent;

    private long progress;

    public TransferParallelContentEncoder( ByteBuf content, int chunkSize)
    {
        this.content= content;
        this.definedLength= content.readableBytes();
        this.chunkSize= chunkSize;
    }

    @Override
    @Deprecated
    public TransferContent readChunk( ChannelHandlerContext ctx) throws Exception
    {
        return readChunk( ctx.alloc());
    }

    @Override
    public TransferContent readChunk( ByteBufAllocator allocator) throws Exception
    {
        if( lastChunkSent)
            return null;

//        logger.debug( "readable: {}", content.readableBytes());
        
        if( progress>= definedLength)
        {
            lastChunkSent= true;
            content.release();
            return EMPTY_LAST_CONTENT;
        }

        int bufSize= (int)Math.min( chunkSize, definedLength- progress);
        ByteBuf buffer= content.readRetainedSlice( bufSize);
        progress+= bufSize;

//        logger.debug( "definedlength: {}", definedLength);
//        logger.debug( "readChunk current bufSize / progress: {} / {}", bufSize, progress);

        return new TransferContent( buffer);
    }


    @Override
    public boolean isEndOfInput() throws Exception
    {
        return lastChunkSent;
    }

    @Override
    public void close() throws Exception
    {
        logger.debug( "TransferParallelContentEncoder closed");
    }

    @Override
    public long length()
    {
        return this.content.readableBytes();
    }

    @Override
    public long progress()
    {
        return progress;
    }
}
