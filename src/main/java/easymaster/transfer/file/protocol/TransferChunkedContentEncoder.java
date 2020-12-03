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

public class TransferChunkedContentEncoder implements ChunkedInput<TransferContent>
{
    private Logger logger= LoggerFactory.getLogger( TransferChunkedContentEncoder.class);

    private final FileData content;

    private final int chunkSize;

    private boolean lastChunkSent;

    private long progress;

    public TransferChunkedContentEncoder( FileData content, int chunkSize)
    {
        this.content= content;
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

        if( progress>= content.definedLength())
        {
            lastChunkSent= true;
            content.release();
            return EMPTY_LAST_CONTENT;
        }

        int bufSize= (int)Math.min( chunkSize, content.definedLength()- progress);
        ByteBuf buffer= content.getChunk( bufSize);
        progress+= bufSize;

//        logger.debug( "readChunk current bufSize / progress: {} / {}", bufSize, progress);

        return new TransferContent( buffer);
    }

    @Override
    public long length()
    {
        return content.definedLength();
    }

    @Override
    public long progress()
    {
        return progress;
    }

    @Override
    public boolean isEndOfInput() throws Exception
    {
        return lastChunkSent;
    }

    @Override
    public void close() throws Exception
    {
        logger.debug( "TransferChunkedContentEncoder closed");
    }
}
