/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.util;

import java.nio.charset.Charset;

import io.netty.util.CharsetUtil;

/**
 * @author Jongoh Lee
 *
 */

public final class TransferConstants
{
    public static final String BIND_ADDRESS= "BIND_ADDRESS";
    
    public static final String AGENT_URL_PREFIX= "agent:";

    public static final String URL_PROTOCOL_AGENT= "agent";

    public static final String AGENT_HEALTHINDICATOR= "agentHealthIndicator";
    
    private TransferConstants() {}
    
    /**
     * Horizontal space
     */
    public static final byte SP = 32;

    /**
     * Horizontal tab
     */
    public static final byte HT = 9;

    /**
     * Carriage return
     */
    public static final byte CR = 13;

    /**
     * Equals '='
     */
    public static final byte EQUALS = 61;

    /**
     * Line feed character
     */
    public static final byte LF = 10;

    /**
     * Colon ':'
     */
    public static final byte COLON = 58;

    /**
     * Semicolon ';'
     */
    public static final byte SEMICOLON = 59;

    /**
     * Comma ','
     */
    public static final byte COMMA = 44;

    /**
     * Double quote '"'
     */
    public static final byte DOUBLE_QUOTE = '"';

    /**
     * Default character set (UTF-8)
     */
    public static final Charset DEFAULT_CHARSET = CharsetUtil.UTF_8;

    /**
     * Horizontal space
     */
    public static final char SP_CHAR = (char) SP;

    
}
