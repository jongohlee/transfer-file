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

public enum ResponseCode
{
    /**
    *
       Response-Code:
       Response Only
       200= OK
       400= Bad Request
       404= Source File Not Found
       500= Internal Server Error
       510= Destination Not Responding
       520= Transfer Failed
       530= File Permission Error
       540= TimeOut Occurred
    */

    SUCCESS( 200, 300, "Success"),

    CLIENT_ERROR( 400, 500, "Client Error"),

    SERVER_ERROR( 500, 600, "Server Error"),

    UNKNOWN( 0, 0, "Unknown Status")
    {
        @Override
        public boolean contains( int code)
        {
            return code< 200 || code>= 600;
        }
    };

    private final int min;
    private final int max;
    private final AsciiString defaultReasonPhrase;

    public static ResponseCode valueOf( int code)
    {
        if( SUCCESS.contains( code))
            return SUCCESS;
        if( CLIENT_ERROR.contains( code))
            return CLIENT_ERROR;
        if( SERVER_ERROR.contains( code))
            return SERVER_ERROR;

        return UNKNOWN;
    }

    public ResponseCode valueOf( CharSequence code)
    {
        if( code!= null && code.length()== 3)
        {
            char c0= code.charAt( 0);
            return isDigit( c0) && isDigit( code.charAt( 1)) && isDigit( code.charAt( 2)) ?
                    valueOf( digit( c0)* 100) : UNKNOWN;
        }
        return UNKNOWN;
    }

    ResponseCode( int min, int max, String defaultReasonPhrase)
    {
        this.min= min;
        this.max= max;
        this.defaultReasonPhrase= AsciiString.cached( defaultReasonPhrase);
    }

    private static int digit( char c)
    {
        return c- '0';
    }

    private static boolean isDigit( char c)
    {
        return c>= '0' && c<= '9';
    }

    AsciiString defaultReasonPhrase()
    {
        return this.defaultReasonPhrase;
    }

    public boolean contains( int code)
    {
        return code>= min && code< max;
    }
}
