/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.util;

import static easymaster.transfer.file.protocol.TransferHeaderNames.CONTENT_LENGTH;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_ENCODING;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CHUNKED;
import static easymaster.transfer.file.protocol.TransferHeaders.HIGHEST_INVALID_VALUE_CHAR_MASK;
import static easymaster.transfer.file.util.TransferConstants.AGENT_URL_PREFIX;
import static easymaster.transfer.file.util.TransferConstants.DEFAULT_CHARSET;
import static io.netty.util.CharsetUtil.US_ASCII;
import static io.netty.util.CharsetUtil.UTF_8;
import static io.netty.util.internal.StringUtil.EMPTY_STRING;
import static io.netty.util.internal.StringUtil.NEWLINE;
import static io.netty.util.internal.StringUtil.SPACE;
import static io.netty.util.internal.StringUtil.decodeHexByte;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import easymaster.transfer.file.protocol.TransferHeaders;
import easymaster.transfer.file.protocol.TransferMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

/**
 * @author Jongoh Lee
 *
 */

public final class TransferMessageUtil
{
    private static Logger logger= LoggerFactory.getLogger( TransferMessageUtil.class);
    
    public static final String PATH_SEPARATOR= "/";
    
    public static final char PATH_SEPARATOR_CHAR= '/';
    
    private TransferMessageUtil() {}
    
    public static String encodedUri( String host, int port, String path, OptionParameter... options) throws Exception
    {
        if( StringUtil.isNullOrEmpty( host))
            throw new IllegalArgumentException( "host value is null or empty");
        if( StringUtil.isNullOrEmpty( path))
            throw new IllegalArgumentException( "path value is null or empty");
        ObjectUtil.checkPositive( port, "port");
        
        if( path.startsWith( PATH_SEPARATOR))
            path= path.substring( 1);
        StringBuilder uriBuilder= new StringBuilder();
        uriBuilder
            .append( AGENT_URL_PREFIX).append( "//").append( host).append( ":").append( port)
            .append( PATH_SEPARATOR).append( URLEncoder.encode( path, DEFAULT_CHARSET.name()));
        
        if( options!= null && options.length> 0)
        {
            uriBuilder.append( '?');
            for( OptionParameter option: options)
            {
                encodeComponent( option.name(), DEFAULT_CHARSET, uriBuilder);
                uriBuilder.append( '=');
                encodeComponent( option.value(), DEFAULT_CHARSET, uriBuilder);
                uriBuilder.append( '&');
            }
        }

        if( uriBuilder.charAt( uriBuilder.length()- 1)== '&')
            uriBuilder.deleteCharAt( uriBuilder.length()- 1);

        if( logger.isDebugEnabled())
            logger.debug( "encoded uri: {}", uriBuilder.toString());

        return uriBuilder.toString();
    }
    
    public static String encodedUri( String path, OptionParameter... options) throws Exception
    {
        if( StringUtil.isNullOrEmpty( path))
            throw new IllegalArgumentException( "path value is null or empty");
        
        if( path.startsWith( PATH_SEPARATOR))
            path= path.substring( 1);
        StringBuilder uriBuilder= new StringBuilder();
        uriBuilder.append( PATH_SEPARATOR).append( URLEncoder.encode( path, DEFAULT_CHARSET.name()));

        if( options!= null && options.length> 0)
        {
            uriBuilder.append( '?');
            for( OptionParameter option: options)
            {
                encodeComponent( option.name(), DEFAULT_CHARSET, uriBuilder);
                uriBuilder.append( '=');
                encodeComponent( option.value(), TransferConstants.DEFAULT_CHARSET, uriBuilder);
                uriBuilder.append( '&');
            }
        }

        if( uriBuilder.charAt( uriBuilder.length()- 1)== '&')
            uriBuilder.deleteCharAt( uriBuilder.length()- 1);

        if( logger.isDebugEnabled())
            logger.debug( "encoded uri: {}", uriBuilder.toString());

        return uriBuilder.toString();
    }

    public static String decodeUri( String uri, Map<String, List<String>> options) throws Exception
    {
        int pathEndIdx= findPathEndIndex( uri);
        String path= decodeComponent( uri, 0, pathEndIdx, DEFAULT_CHARSET, true);

        path= new URI( path).getPath();
        path= StringUtils.cleanPath( path);

        StringBuilder pathBuilder= new StringBuilder( path);
        if( pathBuilder.charAt( 0)!= PATH_SEPARATOR_CHAR)
            pathBuilder.insert( 0, PATH_SEPARATOR_CHAR);

        if( logger.isDebugEnabled())
            logger.debug( "decoded path: {}", path);

        if( options!= null)
        {
            decodeParameters( uri, pathEndIdx, DEFAULT_CHARSET, options);
            if( logger.isDebugEnabled())
                logger.debug( "decoded params: {}", options);
        }

        return path;
    }

    public static URI decodeRawUri( String uri, Map<String, List<String>> options) throws Exception
    {
        int pathEndIdx= findPathEndIndex( uri);
        String path= decodeComponent( uri, 0, pathEndIdx, DEFAULT_CHARSET, true);

        URI raw= new URI( path);

        if( logger.isDebugEnabled())
            logger.debug( "decoded uri: {}", uri);

        if( options!= null)
        {
            decodeParameters( uri, pathEndIdx, DEFAULT_CHARSET, options);
            if( logger.isDebugEnabled())
                logger.debug( "decoded params: {}", options);
        }

        return raw;
    }

    public static boolean isChunked( TransferMessage message)
    {
        return message.headers().contains( TRANSFER_ENCODING, CHUNKED, true);
    }

    public static long getContentLength( TransferMessage message, long defaultValue)
    {
        String value= message.headers().get( CONTENT_LENGTH);
        if( value!= null)
            return Long.parseLong( value);
        return defaultValue;
    }

    public static StringBuilder appendMessage( StringBuilder buf, TransferMessage message)
    {
        appendCommon( buf, message);
        appendInitialLine( buf, message);
        appendHeaders( buf, message.headers());
        removeLastNewLine( buf);
        return buf;
    }

    public static void writeAscii( ByteBuf buf, int offset, CharSequence value)
    {
        if( value instanceof AsciiString)
            ByteBufUtil.copy( (AsciiString)value, 0, buf, offset, value.length());
        else
            buf.setCharSequence( offset, value, US_ASCII);
    }
    
    public static int writeUtf8( ByteBuf buf, int offset, CharSequence value)
    {
        return buf.setCharSequence( offset, value, UTF_8);
    }

    public static boolean isExposableProperty( String name)
    {
        return name.startsWith( "transfer")
                || name.startsWith( "spring")
                || name.equals( "PID")
                || name.equals( "os.name")
                || name.equals( "HOSTNAME")
                || name.equals( "basedir");
    }

    public static String validateHeaderValue( CharSequence seq)
    {
        StringBuilder builder= new StringBuilder();
        int state= 0;
        for( int i= 0; i< seq.length(); i++)
        {
            char c= seq.charAt( i);

            if( ( c & HIGHEST_INVALID_VALUE_CHAR_MASK)== 0 && ( c== 0x0 || c== 0x0b || c== '\f'))
            {
                builder.append( ' ');
                continue;
            }

            if( state== 0)
            {
                if( c== '\r')
                    state= 1;
                else if( c== '\n')
                    state= 2;
                builder.append( c);
            }
            else if( state== 1)
            {
                if( c== '\n')
                {
                    state= 2;
                    builder.append( c);
                }
                else
                {
                    state= 0;
                    builder.append( '\n').append( c);
                }
            }
            else if( state== 2)
            {
                if( c== '\t' || c== ' ')
                    builder.append( c);
                else
                    builder.append( '\t').append( c);
                state= 0;
            }
        }
        if( state!= 0)
            builder.deleteCharAt( builder.length()- 1);
        
        if( '\r'== builder.charAt( builder.length()- 1))
            builder.deleteCharAt( builder.length()- 1);
                

        return builder.toString();
    }

    private static void appendCommon( StringBuilder buf, TransferMessage message)
    {
        buf.append( StringUtil.simpleClassName( message));
        buf.append( "(decodeResult: ");
        buf.append( message.decoderResult());
        buf.append( ", content: ");
        buf.append( message.content());
        buf.append( ')');
        buf.append( NEWLINE);
    }

    private static void appendInitialLine( StringBuilder buf, TransferMessage message)
    {
        buf.append( message.command());
        buf.append( ' ');
        buf.append( message.uri());
        buf.append( NEWLINE);
    }

    private static void appendHeaders( StringBuilder buf, TransferHeaders headers)
    {
        for( Map.Entry<String, String> e: headers)
        {
            buf.append( e.getKey());
            buf.append( ": ");
            buf.append( e.getValue());
            buf.append( NEWLINE);
        }
    }

    private static void removeLastNewLine( StringBuilder buf)
    {
        buf.setLength( buf.length()- NEWLINE.length());
    }

    private static Map<String, List<String>> decodeParameters( String s, int from, Charset charset, Map<String, List<String>> parameters)
    {
        int len= s.length();
        if( from>= len)
            return Collections.emptyMap();
        if( s.charAt( from)== '?')
            from++;
        int nameStart= from;
        int valueStart= -1;
        int i;
        loop: for( i= from; i< len; i++)
        {
            switch( s.charAt( i))
            {
                case '=':
                    if( nameStart== i)
                        nameStart= i+ 1;
                    else if( valueStart< nameStart)
                        valueStart= i+ 1;
                    break;
                case '&':
                case ';':
                    addParameter( s, nameStart, valueStart, i, parameters, charset);
                    nameStart= i+ 1;
                    break;
                case '#':
                    break loop;
                default:
                    // continue
            }
        }
        addParameter( s, nameStart, valueStart, i, parameters, charset);
        return parameters;
    }

    private static boolean addParameter( String s, int nameStart, int valueStart, int valueEnd, 
            Map<String, List<String>> parameters, Charset charset)
    {
        if( nameStart>= valueEnd)
            return false;
        if( valueStart<= nameStart)
            valueStart= valueEnd+ 1;
        String name= decodeComponent( s, nameStart, valueStart- 1, charset, false);
        String value= decodeComponent( s, valueStart, valueEnd, charset, false);
        List<String> values= parameters.get( name);
        if( values== null)
        {
            values= new ArrayList<String>( 1); // Often there's only 1 value.
            parameters.put( name, values);
        }
        values.add( value);
        return true;
    }

    private static void encodeComponent( String s, Charset charset, StringBuilder sb)
    {
        try{ s= URLEncoder.encode( s, charset.name());}
        catch( UnsupportedEncodingException ignored)
        {
            throw new UnsupportedCharsetException( charset.name());
        }
        // replace all '+' with "%20"
        int idx= s.indexOf( '+');
        if( idx== -1)
        {
            sb.append( s);
            return;
        }
        sb.append( s, 0, idx).append( "%20");
        int size= s.length();
        idx++;
        for( ; idx< size; idx++)
        {
            char c= s.charAt( idx);
            if( c!= '+')
                sb.append( c);
            else
                sb.append( "%20");
        }
    }

    private static String decodeComponent( String s, int from, int toExcluded, Charset charset, boolean isPath)
    {
        int len= toExcluded- from;
        if( len<= 0)
            return EMPTY_STRING;
        int firstEscaped= -1;
        for( int i= from; i< toExcluded; i++)
        {
            char c= s.charAt( i);
            if( c== '%'|| c== '+' && !isPath)
            {
                firstEscaped= i;
                break;
            }
        }
        if( firstEscaped== -1)
            return s.substring( from, toExcluded);

        CharsetDecoder decoder= CharsetUtil.decoder( charset);

        // Each encoded byte takes 3 characters (e.g. "%20")
        int decodedCapacity= ( toExcluded- firstEscaped)/ 3;
        ByteBuffer byteBuf= ByteBuffer.allocate( decodedCapacity);
        CharBuffer charBuf= CharBuffer.allocate( decodedCapacity);

        StringBuilder strBuf= new StringBuilder( len);
        strBuf.append( s, from, firstEscaped);

        for( int i= firstEscaped; i< toExcluded; i++)
        {
            char c= s.charAt( i);
            if( c!= '%')
            {
                strBuf.append( c!= '+' || isPath ? c : SPACE);
                continue;
            }

            byteBuf.clear();
            do
            {
                if( i+ 3> toExcluded)
                    throw new IllegalArgumentException( "unterminated escape sequence at index "+ i+ " of: "+ s);
                byteBuf.put( decodeHexByte( s, i+ 1));
                i+= 3;
            }
            while( i< toExcluded&& s.charAt( i)== '%');
            
            i--;

            byteBuf.flip();
            charBuf.clear();
            CoderResult result= decoder.reset().decode( byteBuf, charBuf, true);
            try
            {
                if( !result.isUnderflow())
                    result.throwException();
                result= decoder.flush( charBuf);
                if( !result.isUnderflow())
                    result.throwException();
            }
            catch( CharacterCodingException ex)
            {
                throw new IllegalStateException( ex);
            }
            strBuf.append( charBuf.flip());
        }
        return strBuf.toString();
    }

    private static int findPathEndIndex( String uri)
    {
        int len= uri.length();
        for( int i= 0; i< len; i++)
        {
            char c= uri.charAt( i);
            if( c== '?' || c== '#')
                return i;
        }
        return len;
    }
}
