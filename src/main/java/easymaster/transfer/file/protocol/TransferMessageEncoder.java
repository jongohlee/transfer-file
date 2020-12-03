/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.protocol;

import static easymaster.transfer.file.util.TransferConstants.COLON;
import static easymaster.transfer.file.util.TransferConstants.CR;
import static easymaster.transfer.file.util.TransferConstants.LF;
import static easymaster.transfer.file.util.TransferConstants.SP;
import static easymaster.transfer.file.util.TransferMessageUtil.writeAscii;
import static easymaster.transfer.file.util.TransferMessageUtil.writeUtf8;
import static io.netty.buffer.Unpooled.directBuffer;
import static io.netty.buffer.Unpooled.unreleasableBuffer;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.CharsetUtil;

/**
 * @author Jongoh Lee
 *
 */

public class TransferMessageEncoder extends MessageToMessageEncoder<TransferObject>
{
    private Logger logger= LoggerFactory.getLogger( TransferMessageEncoder.class);
    
    private static final int CRLF_SHORT= ( CR<< 8) | LF;
    private static final byte[] ZERO_CRLF_CRLF= { '0', CR, LF, CR, LF };
    private static final ByteBuf CRLF_BUF= unreleasableBuffer( directBuffer( 2).writeByte( CR).writeByte( LF));
    private static final ByteBuf ZERO_CRLF_CRLF_BUF= unreleasableBuffer( directBuffer( ZERO_CRLF_CRLF.length).writeBytes( ZERO_CRLF_CRLF));
    private static final int COLON_AND_SPACE_SHORT= ( COLON<< 8) | SP;
    private static final char SLASH= '/';
    private static final int SLASH_AND_SPACE_SHORT= ( SLASH<< 8) | SP;
    private static final int SPACE_SLASH_AND_SPACE_MEDIUM= ( SP<< 16) | SLASH_AND_SPACE_SHORT;

    private static final float HEADERS_WEIGHT_NEW = 1/ 5f;
    private static final float HEADERS_WEIGHT_HISTORICAL= 1- HEADERS_WEIGHT_NEW;
    private static float headersEncodedSizeAccumulator= 4096;

    @Override
    public boolean acceptOutboundMessage( Object msg) throws Exception
    {
        return msg instanceof TransferObject;
    }

    @Override
    public void encode( ChannelHandlerContext ctx, TransferObject msg, List<Object> out) throws Exception
    {
        if( msg instanceof TransferMessage)
        {
            TransferMessage message= (TransferMessage)msg;
            ByteBuf buf= ctx.alloc().buffer( (int)headersEncodedSizeAccumulator);
            encodeInitialLine( buf, message);

            encodeHeaders( message.headers(), buf);
            ByteBufUtil.writeShortBE( buf, CRLF_SHORT);

            headersEncodedSizeAccumulator= HEADERS_WEIGHT_NEW* padSizeForAccumulation( buf.readableBytes()) +
                    HEADERS_WEIGHT_HISTORICAL* headersEncodedSizeAccumulator;

            out.add( buf);

            logger.debug( "Agent Headers encoded. headers: [{}]", message.headers());

            if( message.content()!= null && message.content().definedLength()> 0)
            {
                out.add( message.content().retain().content());
//                logger.debug( "fixed data encoded. file: [{}]", message.content().getFile());
            }
        }

        if( msg instanceof TransferContent)
        {
            TransferContent content= (TransferContent)msg;
            encodeChunkedContent( ctx, content, out);
//          logger.debug( "Chunked data encoded: [{}]", content.content().readableBytes());
        }
    }

    private int padSizeForAccumulation( int readableBytes)
    {
        return ( readableBytes<< 2)/ 3;
    }

    private void encodeHeaders( TransferHeaders headers, ByteBuf buf)
    {
        Iterator<Entry<CharSequence, CharSequence>> iter= headers.iteratorCharSequence();
        while( iter.hasNext())
        {
            Entry<CharSequence, CharSequence> header= iter.next();
            encodeHeader( header.getKey(), header.getValue(), buf);
        }
    }

    private void encodeHeader( CharSequence name, CharSequence value, ByteBuf buf)
    {
        final int nameLen= name.length();
        final int valueLen= ByteBufUtil.utf8MaxBytes( value);
        final int entryLen= nameLen+ valueLen+ 4;
        buf.ensureWritable( entryLen);
        int offset= buf.writerIndex();
        writeAscii( buf, offset, name);
        offset+= nameLen;
        ByteBufUtil.setShortBE( buf, offset, COLON_AND_SPACE_SHORT);
        offset+= 2;
        int written= writeUtf8( buf, offset, value);
        offset+= written;
        ByteBufUtil.setShortBE( buf, offset, CRLF_SHORT);
        offset+= 2;
        buf.writerIndex( offset);
    }

    private void encodeInitialLine( ByteBuf buf, TransferMessage message) throws Exception
    {
        ByteBufUtil.copy( message.command().asciiName(), buf);
        String uri= message.uri();

        if( uri== null || uri.isEmpty())
            ByteBufUtil.writeMediumBE( buf, SPACE_SLASH_AND_SPACE_MEDIUM);
        else
        {
            CharSequence uriCharSequence= uri;
            buf.writeByte( SP).writeCharSequence( uriCharSequence, CharsetUtil.UTF_8);
            buf.writeByte( SP);
        }

        ByteBufUtil.writeShortBE( buf, CRLF_SHORT);
    }

    private void encodeChunkedContent( ChannelHandlerContext ctx, TransferContent content, List<Object> out)
    {
        long contentLength= content.content().readableBytes();
        if( contentLength> 0)
        {
            String lengthHex= Long.toHexString( contentLength);
//            ByteBuf buf= ctx.alloc().buffer( lengthHex.length()+ 2);
            ByteBuf buf= Unpooled.buffer( lengthHex.length()+ 2);
            buf.writeCharSequence( lengthHex, CharsetUtil.US_ASCII);
            ByteBufUtil.writeShortBE( buf, CRLF_SHORT);
            out.add( buf);
            out.add( content.retain().content());
            out.add( CRLF_BUF.duplicate());
        }

        if( content instanceof LastTransferContent)
            out.add( ZERO_CRLF_CRLF_BUF.duplicate());
        else if( contentLength== 0)
            out.add( content.retain().content());
    }

}
