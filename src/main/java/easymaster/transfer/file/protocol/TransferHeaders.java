/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.protocol;

import static io.netty.util.AsciiString.CASE_INSENSITIVE_HASHER;
import static io.netty.util.AsciiString.CASE_SENSITIVE_HASHER;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.springframework.util.StringUtils;

import io.netty.handler.codec.CharSequenceValueConverter;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.DefaultHeaders;
import io.netty.handler.codec.DefaultHeadersImpl;
import io.netty.handler.codec.HeadersUtils;
import io.netty.handler.codec.ValueConverter;
import io.netty.handler.codec.DefaultHeaders.NameValidator;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;
import io.netty.util.internal.PlatformDependent;

/**
 * @author Jongoh Lee
 *
 */

public class TransferHeaders implements Iterable<Map.Entry<String, String>>
{
    // contol ascii charactors are not allowed
    public static final int HIGHEST_INVALID_VALUE_CHAR_MASK = ~15;
    
    private final DefaultHeaders<CharSequence, CharSequence, ?> headers;
    
    private static final ByteProcessor HEADER_NAME_VALIDATOR= new ByteProcessor(){

        @Override
        public boolean process( byte value) throws Exception
        {
            validateHeaderNameElement( value);
            return true;
        }
    };
    
    static final NameValidator<CharSequence> AgentNameValidator= new NameValidator<CharSequence>(){

        @Override
        public void validateName( CharSequence name)
        {
            if( name== null || name.length()== 0)
                throw new IllegalArgumentException( "empty headers are not allowed ["+ name+ "]");

            if( name instanceof AsciiString)
            {
                try{ ( (AsciiString)name).forEachByte( HEADER_NAME_VALIDATOR);}
                catch( Exception e)
                {
                    PlatformDependent.throwException( e);
                }
            }
            else
            {
                for( int i= 0; i< name.length(); i++)
                    validateHeaderNameElement( name.charAt( i));
            }
        }
    };
    
    public TransferHeaders()
    {
        this( true);
    }
    
    
    public TransferHeaders( boolean validate)
    {
        this( validate, nameValidator( validate));
    }
    
    protected TransferHeaders( boolean validate, NameValidator<CharSequence> nameValidator)
    {
        this( new DefaultHeadersImpl<CharSequence, CharSequence>( CASE_INSENSITIVE_HASHER, valueConverter( validate), nameValidator));
    }
    
    protected TransferHeaders( DefaultHeaders<CharSequence, CharSequence, ?> headers)
    {
        this.headers= headers;
    }

    public static ValueConverter<CharSequence> valueConverter( boolean validate)
    {
        return validate ? HeaderValueConverterAndValidator.INSTANCE : HeaderValueConverter.INSTANCE;
    }

    @SuppressWarnings( "unchecked")
    public static NameValidator<CharSequence> nameValidator( boolean validate)
    {
        return validate ? AgentNameValidator : NameValidator.NOT_NULL;
    }

    private static void validateHeaderNameElement( byte value)
    {
        switch( value)
        {
            case 0x00:
            case '\t':
            case '\n':
            case 0x0b:
            case '\f':
            case '\r':
            case ' ':
            case ',':
            case ':':
            case ';':
            case '=':
                throw new IllegalArgumentException(
                   "a header name cannot contain the following prohibited characters: =,;: \\t\\r\\n\\v\\f: "+ value);
            default:
                // Check to see if the character is not an ASCII character, or invalid
                if( value< 0)
                    throw new IllegalArgumentException( "a header name cannot contain non-ASCII character: "+ value);
        }
    }

    private static void validateHeaderNameElement( char value)
    {
        switch( value)
        {
            case 0x00:
            case '\t':
            case '\n':
            case 0x0b:
            case '\f':
            case '\r':
            case ' ':
            case ',':
            case ':':
            case ';':
            case '=':
                throw new IllegalArgumentException(
                   "a header name cannot contain the following prohibited characters: =,;: \\t\\r\\n\\v\\f: "+ value);
            default:
                // Check to see if the character is not an ASCII character, or invalid
                if( value> 127)
                    throw new IllegalArgumentException( "a header name cannot contain non-ASCII character: "+ value);
        }
    }

    public TransferHeaders add( TransferHeaders headers)
    {
        this.headers.add( headers.headers);
        return this;
    }

    public TransferHeaders set( TransferHeaders headers)
    {
        this.headers.set( headers.headers);
        return this;
    }

    public TransferHeaders add( String name, Object value)
    {
        headers.addObject( name, value);
        return this;
    }

    public TransferHeaders add( CharSequence name, Object value)
    {
        headers.addObject( name, value);
        return this;
    }

    public TransferHeaders add( String name, Iterable<?> values)
    {
        headers.addObject( name, values);
        return this;
    }

    public TransferHeaders add( CharSequence name, Iterable<?> values)
    {
        headers.addObject( name, values);
        return this;
    }

    public TransferHeaders addInt( CharSequence name, int value)
    {
        headers.addInt( name, value);
        return this;
    }

    public TransferHeaders addShort( CharSequence name, short value)
    {
        headers.addShort( name, value);
        return this;
    }

    public TransferHeaders remove( String name)
    {
        headers.remove( name);
        return this;
    }

    public TransferHeaders remove( CharSequence name)
    {
        headers.remove( name);
        return this;
    }

    public TransferHeaders set( String name, Object value)
    {
        headers.setObject( name, value);
        return this;
    }

    public TransferHeaders set( CharSequence name, Object value)
    {
        headers.setObject( name, value);
        return this;
    }

    public TransferHeaders set( String name, Iterable<?> values)
    {
        headers.setObject( name, values);
        return this;
    }

    public TransferHeaders set( CharSequence name, Iterable<?> values)
    {
        headers.setObject( name, values);
        return this;
    }

    public TransferHeaders setInt( CharSequence name, int value)
    {
        headers.setInt( name, value);
        return this;
    }

    public TransferHeaders setShort( CharSequence name, short value)
    {
        headers.setShort( name, value);
        return this;
    }

    public TransferHeaders clear()
    {
        headers.clear();
        return this;
    }

    public TransferResponseCode getResponseCode()
    {
        String codeStr= get( TransferHeaderNames.RESPONSE_CODE);
        String[] phs= null;

        if( StringUtils.hasText( codeStr) && ( phs= StringUtils.split( codeStr, " ")).length> 1)
            return TransferResponseCode.newResponse( Integer.parseInt( phs[0]), phs[1]);
        return null;
    }

    public String get( String name)
    {
        return get( (CharSequence) name);
    }

    public String get( CharSequence name)
    {
        return HeadersUtils.getAsString( headers, name);
    }

    public Integer getInt( CharSequence name)
    {
        return headers.getInt( name);
    }

    public int getInt( CharSequence name, int defaultValue)
    {
        return headers.getInt( name, defaultValue);
    }

    public Short getShort( CharSequence name)
    {
        return headers.getShort( name);
    }

    public short getShort( CharSequence name, short defaultValue)
    {
        return headers.getShort( name, defaultValue);
    }

    public Long getTimeMillis( CharSequence name)
    {
        return headers.getTimeMillis( name);
    }

    public long getTimeMillis( CharSequence name, long defaultValue)
    {
        return headers.getTimeMillis( name, defaultValue);
    }

    public List<String> getAll( String name)
    {
        return getAll( (CharSequence)name);
    }

    public List<String> getAll( CharSequence name)
    {
        return HeadersUtils.getAllAsString( headers, name);
    }

    public List<Entry<String, String>> entries()
    {
        if( isEmpty())
            return Collections.emptyList();
        List<Entry<String, String>> entriesConverted= new ArrayList<Entry<String, String>>( headers.size());
        for( Entry<String, String> entry : this)
            entriesConverted.add( entry);
        return entriesConverted;
    }

    public boolean contains( String name)
    {
        return headers.contains( name);
    }

    public boolean contains( CharSequence name)
    {
        return headers.contains( name);
    }

    public boolean contains( String name, String value, boolean ignoreCase)
    {
        return contains( (CharSequence)name, (CharSequence)value, ignoreCase);
    }

    public boolean contains( CharSequence name, CharSequence value, boolean ignoreCase)
    {
        return headers.contains( name, value, ignoreCase ? CASE_INSENSITIVE_HASHER : CASE_SENSITIVE_HASHER);
    }

    public Set<String> names()
    {
        return HeadersUtils.namesAsString( headers);
    }

    public boolean isEmpty()
    {
        return headers.isEmpty();
    }

    public int size()
    {
        return headers.size();
    }

    public Iterator<Entry<CharSequence, CharSequence>> iteratorCharSequence()
    {
        return headers.iterator();
    }

    public Iterator<String> valueStringIterator( CharSequence name)
    {
        final Iterator<CharSequence> iter= valueCharSequenceIterator( name);
        return new Iterator<String>(){
            @Override
            public boolean hasNext()
            {
                return iter.hasNext();
            }

            @Override
            public String next()
            {
                return iter.next().toString();
            }

            @Override
            public void remove()
            {
                iter.remove();
            }
        };
    }

    public Iterator<CharSequence> valueCharSequenceIterator( CharSequence name)
    {
        return headers.valueIterator( name);
    }

    @Override
    public Iterator<Entry<String, String>> iterator()
    {
        return HeadersUtils.iteratorAsString( headers);
    }

    @Override
    public int hashCode()
    {
        return headers.hashCode( CASE_SENSITIVE_HASHER);
    }

    @Override
    public boolean equals( Object o)
    {
        return o instanceof TransferHeaders && headers.equals( ( (TransferHeaders)o).headers, CASE_SENSITIVE_HASHER);
    }

    @Override
    public String toString()
    {
        return HeadersUtils.toString( getClass(), iteratorCharSequence(), size());
    }


    private static class HeaderValueConverter extends CharSequenceValueConverter
    {
        static final HeaderValueConverter INSTANCE= new HeaderValueConverter();

        @Override
        public CharSequence convertObject( Object value)
        {
            if( value instanceof CharSequence)
                return (CharSequence)value;
            if( value instanceof Date)
                return DateFormatter.format( (Date)value);
            if( value instanceof Calendar)
                return DateFormatter.format( ( (Calendar)value).getTime());

            return value.toString();
        }
    }

    private static final class HeaderValueConverterAndValidator extends HeaderValueConverter
    {
        static final HeaderValueConverterAndValidator INSTANCE= new HeaderValueConverterAndValidator();

        @Override
        public CharSequence convertObject( Object value)
        {
            CharSequence seq= super.convertObject( value);
            int state= 0;
            // Start looping through each of the character
            for( int i= 0; i< seq.length(); i++)
                state= validateValueChar( seq, state, seq.charAt( i));

            if( state!= 0)
                throw new IllegalArgumentException( "a header value must not end with '\\r' or '\\n':"+ seq);
            return seq;
        }

        private static int validateValueChar( CharSequence seq, int state, char character)
        {
            /*
             * State:
             * 0: Previous character was neither CR nor LF
             * 1: The previous character was CR
             * 2: The previous character was LF
             */
            if( (character & HIGHEST_INVALID_VALUE_CHAR_MASK)== 0)
            {
                // Check the absolutely prohibited characters.
                switch( character)
                {
                    case 0x0: // NULL
                        throw new IllegalArgumentException(
                                "a header value contains a prohibited character '\0': "+ seq);
                    case 0x0b: // Vertical tab
                        throw new IllegalArgumentException(
                                "a header value contains a prohibited character '\\v': "+ seq);
                    case '\f':
                        throw new IllegalArgumentException(
                                "a header value contains a prohibited character '\\f': "+ seq);
                }
            }

            // Check the CRLF (HT | SP) pattern
            switch( state)
            {
                case 0:
                    switch( character)
                    {
                        case '\r':
                            return 1;
                        case '\n':
                            return 2;
                    }
                    break;
                case 1:
                    switch( character)
                    {
                        case '\n':
                            return 2;
                        default:
                            throw new IllegalArgumentException( "only '\\n' is allowed after '\\r': "+ seq);
                    }
                case 2:
                    switch( character)
                    {
                        case '\t':
                        case ' ':
                            return 0;
                        default:
                            throw new IllegalArgumentException( "only ' ' and '\\t' are allowed after '\\n': "+ seq);
                    }
            }
            return state;
        }
    }
}

