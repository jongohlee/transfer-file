/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.DecoderResult;
import io.netty.util.internal.ObjectUtil;

/**
 * @author Jongoh Lee
 *
 */

public class TransferContent implements TransferObject, ByteBufHolder
{
    private static final int HASH_CODE_PRIME= 31;

    private DecoderResult decoderResult= DecoderResult.SUCCESS;

    private final ByteBuf content;

    public TransferContent( ByteBuf content)
    {
        ObjectUtil.checkNotNull( content, "content");
        this.content= content;
    }

    @Override
    public ByteBuf content()
    {
        return this.content;
    }

    @Override
    public TransferContent copy()
    {
        return replace( content.copy());
    }

    @Override
    public TransferContent duplicate()
    {
        return replace( content.duplicate());
    }

    @Override
    public TransferContent retainedDuplicate()
    {
        return replace( content.retainedDuplicate());
    }

    @Override
    public TransferContent replace( ByteBuf content)
    {
        return new TransferContent( content);
    }

    @Override
    public int refCnt()
    {
        return content.refCnt();
    }

    @Override
    public boolean release()
    {
        return content.release();
    }

    @Override
    public boolean release( int decrement)
    {
        return content.release( decrement);
    }

    @Override
    public TransferContent retain()
    {
        content.retain();
        return this;
    }

    @Override
    public TransferContent retain( int increment)
    {
        content.retain( increment);
        return this;
    }

    @Override
    public TransferContent touch()
    {
        content.touch();
        return this;
    }

    @Override
    public TransferContent touch( Object hint)
    {
        content.touch( hint);
        return this;
    }

    @Override
    public DecoderResult decoderResult()
    {
        return decoderResult;
    }

    @Override
    public void setDecoderResult( DecoderResult decoderResult)
    {
        ObjectUtil.checkNotNull( decoderResult, "decoderResult");
        this.decoderResult= decoderResult;
    }

    @Override
    public int hashCode()
    {
        int result= 1;
        result= HASH_CODE_PRIME* result+ decoderResult.hashCode();
        return result;
    }

    @Override
    public boolean equals( Object o)
    {
        if( !( o instanceof TransferContent))
            return false;

        TransferContent other= (TransferContent)o;
        return decoderResult().equals( other.decoderResult());
    }

}
