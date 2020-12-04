/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.protocol;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import easymaster.transfer.file.util.TransferConstants;
import easymaster.transfer.file.util.TransferMessageUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderResult;
import io.netty.util.ByteProcessor;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.AppendableCharSequence;
import io.netty.util.internal.ObjectUtil;

/**
 * @author Jongoh Lee
 *
 */

public class TransferMessageDecoder extends ByteToMessageDecoder
{
    private static Logger logger= LoggerFactory.getLogger( TransferMessageDecoder.class);
    
    private static final String EMPTY_VALUE = "";

    private TransferMessage message;

    private int chunkSize;

    private enum State
    {
        SKIP_CONTROL_CHARS,
        READ_INITIAL,
        READ_HEADER,
        READ_FIXED_LENGTH_CONTENT,
        READ_CHUNK_SIZE,
        READ_CHUNKED_CONTENT,
        READ_CHUNKED_DELIMITER,
        BAD_MESSAGE
    }

    private State currentState= State.SKIP_CONTROL_CHARS;

    @Override
    public void decode( ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception
    {
//        logger.debug( "TransferMessageDecoder currentState: {}", currentState);

        switch( currentState)
        {
            case BAD_MESSAGE:
                in.skipBytes( in.readableBytes());
                break;
            case SKIP_CONTROL_CHARS:
                // ASCII Control Character가 전송된 경우 skip한다.
                if( !skipControlCharacters( in))
                    break;
                currentState= State.READ_INITIAL;
            case READ_INITIAL:
                try
                {
                    HeaderLineParser parser= new HeaderLineParser( new AppendableCharSequence( 128));
                    AppendableCharSequence line= parser.parse( in);
                    if( line== null)
                        return;
                    String[] initialLine= splitInitialLine( line);
                    if( initialLine.length< 1)
                    {
                        currentState= State.SKIP_CONTROL_CHARS;
                        return;
                    }
                    message= createMessage( initialLine);
                    currentState= State.READ_HEADER;
                }
                catch( Exception e)
                {
                    logger.error( "read initial failed.", e);
                    out.add( invalidMessage( in, message!= null ? message.command() : TransferCommand.INFO, e));
                    out.add( LastTransferContent.EMPTY_LAST_CONTENT);
                    return;
                }
            case READ_HEADER:
                try
                {
                    currentState= readHeaders( in);
                    chunkSize= (int)contentLength();
                    out.add( message);

                    if( currentState== State.SKIP_CONTROL_CHARS)
                        out.add( LastTransferContent.EMPTY_LAST_CONTENT);

                    return;
                }
                catch( Exception e)
                {
                    logger.error( "read header failed.", e);
                    out.add( invalidMessage( in, message!= null ? message.command() : TransferCommand.INFO, e));
                    out.add( LastTransferContent.EMPTY_LAST_CONTENT);
                    return;
                }
            case READ_FIXED_LENGTH_CONTENT:
                try
                {
                    int readable= in.readableBytes();
//                    logger.debug( "READ_FIXED_LENGTH_CONTENT- readable:{} / chunksize:{}", readable, chunkSize);
                    readable= Math.min( readable, chunkSize);

                    if( readable== 0)
                    {
                        out.add( LastTransferContent.EMPTY_LAST_CONTENT);
                        currentState= State.SKIP_CONTROL_CHARS;
                        return;
                    }

                    out.add( new TransferContent( in.readRetainedSlice( readable)));
                    if( ( chunkSize-= readable)== 0)
                    {
                        out.add( LastTransferContent.EMPTY_LAST_CONTENT);
                        currentState= State.SKIP_CONTROL_CHARS;
                    }

                    return;
                }
                catch( Exception e)
                {
                    logger.error( "read fixed_length content failed.", e);
                    out.add( invalidMessage( in, message!= null ? message.command() : TransferCommand.INFO, e));
                    out.add( LastTransferContent.EMPTY_LAST_CONTENT);
                    return;
                }
            case READ_CHUNK_SIZE:
                try
                {
                    HeaderLineParser parser= new HeaderLineParser( new AppendableCharSequence( 128));
                    AppendableCharSequence line= parser.parse( in);
                    if( line== null)
                        return;
                    chunkSize= getChunkSize( line.toString());
//                    logger.debug( "READ_CHUNK_SIZE- chunksize:{}", chunkSize);

                    if( chunkSize== 0)
                    {
                        out.add( LastTransferContent.EMPTY_LAST_CONTENT);
                        currentState= State.SKIP_CONTROL_CHARS;
                        return;
                    }

                    currentState= State.READ_CHUNKED_CONTENT;
                }
                catch( Exception e)
                {
                    logger.error( "read chunksize failed.", e);
                    out.add( invalidMessage( in, message!= null ? message.command() : TransferCommand.INFO, e));
                    out.add( LastTransferContent.EMPTY_LAST_CONTENT);
                    return;
                }
            case READ_CHUNKED_CONTENT:
                try
                {
                    int readable= in.readableBytes();
//                    logger.debug( "READ_CHUNKED_CONTENT- readable:{} / chunksize:{}", readable, chunkSize);
                    readable= Math.min( readable, chunkSize);
                    out.add( new TransferContent( in.readRetainedSlice( readable)));

                    if( ( chunkSize-= readable)!= 0)
                        return;

                    currentState= State.READ_CHUNKED_DELIMITER;
                }
                catch( Exception e)
                {
                    logger.error( "read chunked content failed.", e);
                    out.add( invalidMessage( in, message!= null ? message.command() : TransferCommand.INFO, e));
                    out.add( LastTransferContent.EMPTY_LAST_CONTENT);
                    return;
                }
            case READ_CHUNKED_DELIMITER:
                final int wIdx= in.writerIndex();
                int rIdx= in.readerIndex();
                while( wIdx> rIdx)
                {
                    byte next= in.getByte( rIdx++);
                    if( next== TransferConstants.LF)
                    {
                        currentState= State.READ_CHUNK_SIZE;
                        break;
                    }
                }
                in.readerIndex( rIdx);
                return;
        }
    }

    @Override
    public void decodeLast( ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception
    {
        super.decodeLast( ctx, in, out);
    }

    private static boolean skipControlCharacters( ByteBuf buffer)
    {
        boolean skiped= false;
        final int wIdx= buffer.writerIndex();
        int rIdx= buffer.readerIndex();
        while( wIdx> rIdx)
        {
            int c= buffer.getUnsignedByte( rIdx++);
            if( !Character.isISOControl( c) && !Character.isWhitespace( c))
            {
                rIdx--;
                skiped= true;
                break;
            }
        }
        buffer.readerIndex( rIdx);
        return skiped;
    }

    private State readHeaders( ByteBuf buffer)
    {
        HeaderElementParser parser= new HeaderElementParser();
        AppendableCharSequence line= parser.parse( buffer);
        
        CharSequence name= null;
        CharSequence value= null;
        if( line== null)
            return null;
        if( line.length()> 0)
        {
            do
            {
                char firstChar= line.charAt( 0);
                if( name!= null && ( firstChar== ' ' || firstChar== '\t'))
                {
                  //please do not make one line from below code
                    //as it breaks +XX:OptimizeStringConcat optimization
                    String trimmmedLine= line.toString().trim();
                    String valueStr= String.valueOf( value);
                    value= valueStr+ ' '+ trimmmedLine;
                }
                else
                {
                    if( name!= null)
                        message.headers().add( name, value);
                    HeaderNameValue hnv= splitHeader( line);
                    name= hnv.name();
                    value= hnv.value();
                }

                line= parser.parse( buffer);
                
                if( line== null)
                    return null;
            }
            while( line.length()> 0);
        }

        if( name!= null)
            message.headers().add( name, value);

        logger.debug( "decoded message headers {}", message.headers());

        return contentLength()<= 0 ? State.SKIP_CONTROL_CHARS :
            message.headers().contains( TransferHeaderNames.TRANSFER_ENCODING, TransferHeaderValues.CHUNKED, true) ?
                    State.READ_CHUNK_SIZE : State.READ_FIXED_LENGTH_CONTENT;
    }

    private int getChunkSize( String hex)
    {
        hex= hex.trim();
        for( int i= 0; i< hex.length(); i++)
        {
            char c= hex.charAt( i);
            if( c== ';' || Character.isWhitespace( c) || Character.isISOControl( c))
            {
                hex= hex.substring( 0, i);
                break;
            }
        }

        return Integer.parseInt( hex, 16);
    }

    private long contentLength()
    {
        return TransferMessageUtil.getContentLength( message, -1);
    }

    private TransferMessage createMessage( String[] initialLine)
    {
        TransferMessage message= new TransferMessage( TransferCommand.valueOf( initialLine[0]));
        message.setUri( initialLine[1]);
        return message;
    }

    private TransferMessage invalidMessage( ByteBuf in, TransferCommand command, Exception cause)
    {
        currentState= State.BAD_MESSAGE;
        in.skipBytes( in.readableBytes());
        if( message== null)
            message= badRequestResponse( command, cause);
        message.setDecoderResult( DecoderResult.failure( cause));

        TransferMessage ret= message;
        message= null;
        return ret;
    }

    private TransferMessage badRequestResponse( TransferCommand command, Exception cause)
    {
        ObjectUtil.checkNotNull( command, "command");
        TransferMessage response= new TransferMessage( command);
        response.headers().add( TransferHeaderNames.RESPONSE_CODE, TransferResponseCode.BAD_REQUEST);

        if( cause!= null && StringUtils.hasText( cause.getMessage()))
            response.headers().add( TransferHeaderNames.REASON, cause.getMessage());

        return response;
    }

    private String[] splitInitialLine( AppendableCharSequence sb)
    {
        int commandaStart, commandEnd, uriStart, uriEnd;

        commandaStart= findNotWhitespace( sb, 0);
        commandEnd= findWhitespace( sb, commandaStart);

        uriStart= findNotWhitespace( sb, commandEnd);
        uriEnd= findEndOfString( sb);

        return new String[] {
                sb.subStringUnsafe( commandaStart, commandEnd),
                uriStart< uriEnd ? sb.subStringUnsafe( uriStart, uriEnd) : ""
        };
    }

    private HeaderNameValue splitHeader( AppendableCharSequence sb)
    {
        CharSequence name= null;
        CharSequence value= null;
        final int length= sb.length();
        int nameStart;
        int nameEnd;
        int colonEnd;
        int valueStart;
        int valueEnd;

        nameStart= findNotWhitespace( sb, 0);
        for( nameEnd= nameStart; nameEnd< length; nameEnd++)
        {
            char ch= sb.charAt( nameEnd);
            if( ch== ':' || Character.isWhitespace( ch))
                break;
        }

        for( colonEnd= nameEnd; colonEnd< length; colonEnd++)
        {
            if( sb.charAt( colonEnd)== ':')
            {
                colonEnd++;
                break;
            }
        }

        name= sb.subStringUnsafe( nameStart, nameEnd);
        valueStart= findNotWhitespace( sb, colonEnd);
        if( valueStart== length)
            value= EMPTY_VALUE;
        else
        {
            valueEnd= findEndOfString( sb);
            value= sb.subStringUnsafe( valueStart, valueEnd);
        }

        return new HeaderNameValue( name, value);

    }

    private static int findNotWhitespace( AppendableCharSequence sb, int offset)
    {
        for( int i= offset; i< sb.length(); ++i)
        {
            if( !Character.isWhitespace( sb.charAtUnsafe( i)))
                return i;
        }
        return sb.length();
    }

    private static int findWhitespace( AppendableCharSequence sb, int offset)
    {
        for( int i= offset; i< sb.length(); ++i)
        {
            if( Character.isWhitespace( sb.charAtUnsafe( i)))
                return i;
        }
        return sb.length();
    }

    private static int findEndOfString( AppendableCharSequence sb)
    {
        for( int i= sb.length()- 1; i> 0; --i)
        {
            if( !Character.isWhitespace( sb.charAtUnsafe( i)))
                return i+ 1;
        }
        return 0;
    }

    private class HeaderNameValue
    {
        private CharSequence name;
        private CharSequence value;

        HeaderNameValue( CharSequence name, CharSequence value)
        {
            this.name= name;
            this.value= value;
        }

        CharSequence name()
        {
            return this.name;
        }

        CharSequence value()
        {
            return this.value;
        }

        @Override
        public String toString()
        {
            return "HeaderNameValue [name="+ name+ ", value="+ value+ "]";
        }
    }

    private static class HeaderLineParser implements ByteProcessor
    {
        private final AppendableCharSequence seq;
        
        HeaderLineParser( AppendableCharSequence seq)
        {
            this.seq= seq;
        }

        public AppendableCharSequence parse( ByteBuf buffer)
        {
            seq.reset();
            int i= buffer.forEachByte( this);
            if( i== -1)
                return null;
            buffer.readerIndex( i+ 1);
            return seq;
        }

        @Override
        public boolean process( byte value) throws Exception
        {
            char nextByte= (char)( value & 0xFF);
            if( nextByte== TransferConstants.CR)
                return true;
            if( nextByte== TransferConstants.LF)
                return false;
            
            seq.append( nextByte);
            return true;
        }
    }

    private static class HeaderElementParser implements ByteProcessor
    {
        private final ByteArrayOutputStream bout;
        
        private final AppendableCharSequence seq;
        
        HeaderElementParser()
        {
            this.bout= new ByteArrayOutputStream();
            this.seq= new AppendableCharSequence( 4096);
        }

        public AppendableCharSequence parse( ByteBuf buffer)
        {
            bout.reset();
            seq.reset();
            int i= buffer.forEachByte( this);
            if( i== -1)
                return null;
            buffer.readerIndex( i+ 1);

            String line= new String( bout.toByteArray(), CharsetUtil.UTF_8);
            seq.append( line);
            return seq;
        }

        @Override
        public boolean process( byte value) throws Exception
        {
            char nextByte= (char)( value & 0xFF);
            if( nextByte== TransferConstants.CR)
                return true;
            if( nextByte== TransferConstants.LF)
                return false;
            
            bout.write( value);
            return true;
        }
    }
    
}
