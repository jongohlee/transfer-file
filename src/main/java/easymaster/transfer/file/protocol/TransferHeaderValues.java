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

    // 파일 전송 처리시 비동기 전송 후 처리중 응답 또는 전송 완료 후 결과 응답 여부
    public static final AsciiString VALIDATION_ON= AsciiString.cached( "on");

    public static final AsciiString VALIDATION_OFF= AsciiString.cached( "off");

    public static final AsciiString CLIENT= AsciiString.cached( "Client");

    public static final AsciiString AGENT= AsciiString.cached( "Agent");

    public static final AsciiString KEEP_ALIVE= AsciiString.cached( "keep-alive");

    public static final AsciiString CLOSE= AsciiString.cached( "close");
}
