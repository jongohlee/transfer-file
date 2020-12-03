/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.protocol;

import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.DecoderResultProvider;

/**
 * @author Jongoh Lee
 *
 */

public interface TransferObject extends DecoderResultProvider
{
    @Override
    DecoderResult decoderResult();
}
