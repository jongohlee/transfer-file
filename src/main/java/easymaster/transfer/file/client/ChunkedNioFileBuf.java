/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.client;

import static easymaster.transfer.file.util.FileUtil.PARALLEL_SPLIT_SUFFIX_FORMAT;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.stream.ChunkedNioFile;

/**
 * @author Jongoh Lee
 *
 */

public class ChunkedNioFileBuf
{
    private final ChunkedNioFile chunked;
    
    private int split;
    
    ChunkedNioFileBuf( ChunkedNioFile chunked)
    {
        this.chunked= chunked;
    }
    
    synchronized SplittedBuf nextBuf( ByteBufAllocator allocator) throws Exception
    {
        SplittedBuf buf= new SplittedBuf();
        buf.buf= chunked.readChunk( allocator);
        buf.suffix= String.format( PARALLEL_SPLIT_SUFFIX_FORMAT, split++);
        return buf;
    }
    
    static class SplittedBuf
    {
        ByteBuf buf;
        String suffix;
    }
    
}
