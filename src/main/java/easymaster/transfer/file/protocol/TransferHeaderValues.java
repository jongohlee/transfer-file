/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.protocol;

import io.netty.util.AsciiString;

/**
 * @author Jongoh Lee
 *
 */

public final class TransferHeaderValues
{
    private TransferHeaderValues() {}

    public static final AsciiString CHUNKED= AsciiString.cached( "chunked");

    public static final AsciiString VALIDATION_ON= AsciiString.cached( "on");

    public static final AsciiString VALIDATION_OFF= AsciiString.cached( "off");

    public static final AsciiString CLIENT= AsciiString.cached( "Client");

    public static final AsciiString AGENT= AsciiString.cached( "Agent");

    public static final AsciiString KEEP_ALIVE= AsciiString.cached( "keep-alive");

    public static final AsciiString CLOSE= AsciiString.cached( "close");
}
