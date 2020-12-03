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

public final class TransferHeaderNames
{
    private TransferHeaderNames() {}
    
    public static final AsciiString RESPONSE_CODE= AsciiString.cached( "Response-Code");

    public static final AsciiString REASON= AsciiString.cached( "Reason");

    public static final AsciiString AGENT= AsciiString.cached( "Agent");

    public static final AsciiString REMOTE= AsciiString.cached( "Remote");

    public static final AsciiString AGENT_TYPE= AsciiString.cached( "Agent-Type");

    public static final AsciiString CONNECTION= AsciiString.cached( "Connection");

    public static final AsciiString CONTENT_LENGTH= AsciiString.cached( "Content-Length");

    public static final AsciiString DELETED_COUNT= AsciiString.cached( "Deleted-Count");

    public static final AsciiString TRANSFER_ENCODING= AsciiString.cached( "Transfer-Encoding");

    public static final AsciiString TRANSFER_SOURCE_URI= AsciiString.cached( "Transfer-Source-Uri");

    public static final AsciiString TRANSFER_DESTINATION_URI= AsciiString.cached( "Transfer-Destination-Uri");

    public static final AsciiString DESTINATION_AGENT= AsciiString.cached( "Destination-Agent");

    public static final AsciiString TRANSFER_VALIDATION= AsciiString.cached( "Transfer-Validation");

    public static final AsciiString TRANSFER_TIMEOUT_SECONDS= AsciiString.cached( "Transfer-Timeout-Seconds");

    public static final AsciiString TRANSFER_INTERCEPTOR= AsciiString.cached( "Transfer-Interceptor");

    public static final AsciiString MERGE_RESOURCE= AsciiString.cached( "Merge-Resource");

    public static final AsciiString SESSION_ID= AsciiString.cached( "Session-Id");

    public static final AsciiString RESOURCE_LENGTH= AsciiString.cached( "Resource-Length");

    public static final AsciiString TRANSFERRED_RESOURCE= AsciiString.cached( "Transferred-Resource");
}
