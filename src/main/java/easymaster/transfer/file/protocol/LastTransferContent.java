/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * @author Jongoh Lee
 *
 */

public class LastTransferContent extends TransferContent
{
    static LastTransferContent EMPTY_LAST_CONTENT= new LastTransferContent( Unpooled.EMPTY_BUFFER);

    private LastTransferContent( ByteBuf content)
    {
        super( content);
    }

}
