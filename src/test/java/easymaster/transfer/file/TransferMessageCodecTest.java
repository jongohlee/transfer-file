/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file;

import static easymaster.transfer.file.protocol.TransferCommand.PUT;
import static easymaster.transfer.file.protocol.TransferCommand.TRANSFER;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT_TYPE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.CONTENT_LENGTH;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_DESTINATION_URI;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_ENCODING;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_SOURCE_URI;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_VALIDATION;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CHUNKED;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CLIENT;
import static easymaster.transfer.file.protocol.TransferHeaderValues.VALIDATION_ON;
import static easymaster.transfer.file.util.TransferConstants.CR;
import static easymaster.transfer.file.util.TransferConstants.LF;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import easymaster.transfer.file.protocol.FileData;
import easymaster.transfer.file.protocol.TransferChunkedContentEncoder;
import easymaster.transfer.file.protocol.TransferContent;
import easymaster.transfer.file.protocol.TransferHeaderValues;
import easymaster.transfer.file.protocol.TransferMessage;
import easymaster.transfer.file.protocol.TransferMessageDecoder;
import easymaster.transfer.file.protocol.TransferMessageEncoder;
import easymaster.transfer.file.protocol.TransferObject;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * @author Jongoh Lee
 *
 */

public class TransferMessageCodecTest
{
    private Logger logger= LoggerFactory.getLogger( TransferMessageCodecTest.class);
    
    private EmbeddedChannel encChannel;
    private EmbeddedChannel decChannel;
    
    @Before
    public void setup() throws Exception
    {
        encChannel= new EmbeddedChannel( new TransferMessageEncoder(), new ChunkedWriteHandler());
        decChannel= new EmbeddedChannel( new TransferMessageDecoder());
        
    }
    
    @After
    public void teardown() throws Exception
    {
        encChannel.finishAndReleaseAll();
        decChannel.finishAndReleaseAll();
    }
    
    @Test
    public void shouldCommandRequest() throws IOException
    {
        TransferMessage message= new TransferMessage( TRANSFER);
        message.headers()
            .add( AGENT, "127.0.0.1")
            .add( AGENT_TYPE, CLIENT)
            .add( TRANSFER_VALIDATION, VALIDATION_ON)
            .add(  TRANSFER_SOURCE_URI, "/data/account1.gzip>/data/account1.gzip")
            .add(  TRANSFER_SOURCE_URI, "/data/account2.gzip>/data/account2.gzip")
            .add( "Request-AgentUser", "easymaster");
        
        boolean answer= encChannel.writeOutbound( message);
        assertThat( answer, is( true));

        ByteBuf written= encChannel.readOutbound();
        
        assertThat( written.readableBytes(), greaterThan( 128));
        assertThat( (int)written.getByte( written.readableBytes()- 2), is( (int)CR));
        assertThat( (int)written.getByte( written.readableBytes()- 1), is( (int)LF));
        
        decChannel.writeInbound( written);
        TransferMessage request= decChannel.readInbound();
        
        assertThat( request, equalTo( message));
        encChannel.finishAndReleaseAll();
        decChannel.finishAndReleaseAll();

        logger.info( "shouldCommandRequest test... passed");
    }
    
    @Test
    public void shouldFixedContentRequest() throws IOException
    {
        TransferMessage message= new TransferMessage( PUT);
        message.headers()
            .add( AGENT, "127.0.0.1")
            .add( AGENT_TYPE, TransferHeaderValues.AGENT)
            .add( TRANSFER_SOURCE_URI, "file://127.0.0.1:8024/account1.gzip")
            .add( TRANSFER_DESTINATION_URI, "file://127.0.0.2:8024/account1.gzip")
            .add( "Request-AgentUser", "easymaster");

        String res= getClass().getClassLoader().getResource( "fixed-content.txt").getFile();
        File fs= new File( res);
        FileData fdata= new FileData( fs.length());
        fdata.setContent( fs);
        message.setContent( fdata);

        message.headers().add( CONTENT_LENGTH, fs.length());
        boolean answer= encChannel.writeOutbound( message);
        assertThat( answer, is( true));

        ByteBuf written= encChannel.readOutbound();
        assertThat( written.readableBytes(), greaterThan( 128));
        assertThat( (int)written.getByte( written.readableBytes()- 2), is( (int)CR));
        assertThat( (int)written.getByte( written.readableBytes()- 1), is( (int)LF));

        decChannel.writeInbound( written);

        written= encChannel.readOutbound();
        assertThat( written.readableBytes(), is( (int)fs.length()));

        decChannel.writeInbound( written);

        TransferObject request= null;
        int readable= 0;
        while( ( request= decChannel.readInbound())!= null)
        {
            if( request instanceof TransferMessage)
                assertThat( request, equalTo( message));
            else if( request instanceof TransferContent)
            {
                TransferContent content= (TransferContent)request;
                assertThat( content.decoderResult().isSuccess(), is( true));
                readable+= content.content().readableBytes();
            }
        }
        assertThat( readable, equalTo( (int)fs.length()));
        encChannel.finishAndReleaseAll();
        decChannel.finishAndReleaseAll();
        
        logger.info( "shouldFixedContentRequest test... passed");
    }
    
    @Test
    public void shouldChunkedContentRequest() throws Exception
    {
        TransferMessage message= new TransferMessage( PUT);
        message.headers()
            .add( AGENT, "127.0.0.1")
            .add( AGENT_TYPE, TransferHeaderValues.AGENT)
            .add( TRANSFER_SOURCE_URI, "file://127.0.0.1:8024/account테스트1.gzip")
            .add( TRANSFER_DESTINATION_URI, "file://127.0.0.2:8024/account1.gzip")
            .add( TRANSFER_ENCODING, CHUNKED)
            .add( "Request-AgentUser", "easymaster");

        String res= getClass().getClassLoader().getResource( "chunked-content.jar").getFile();
        File fs= new File( res);

        message.headers().add( CONTENT_LENGTH, fs.length());

        boolean answer= encChannel.writeOutbound( message);
        assertThat( answer, is( true));

        FileData fdata= new FileData( fs.length());
        fdata.setContent( fs);
        TransferChunkedContentEncoder chunk= new TransferChunkedContentEncoder( fdata, 8* 1024);
        encChannel.writeOutbound( chunk);
        chunk.close();

        ByteBuf written= encChannel.readOutbound();
        assertThat( written.readableBytes(), greaterThan( 128));
        assertThat( (int)written.getByte( written.readableBytes()- 2), is( (int)CR));
        assertThat( (int)written.getByte( written.readableBytes()- 1), is( (int)LF));

        decChannel.writeInbound( written);

        int readLen= 0;
        while( ( written= encChannel.readOutbound())!= null)
        {
            readLen+= written.readableBytes();
            decChannel.writeInbound( written);
        }

        // file content length+ chunk length+ chunk delimiter(s)
        assertThat( readLen, greaterThan( (int)fs.length()));
        encChannel.finishAndReleaseAll();

        TransferObject request= null;
        int readable= 0;
        while( ( request= decChannel.readInbound())!= null)
        {
            if( request instanceof TransferMessage)
                assertThat( request, equalTo( message));
            else if( request instanceof TransferContent)
            {
                TransferContent content= (TransferContent)request;
                assertThat( content.decoderResult().isSuccess(), is( true));
                readable+= content.content().readableBytes();
            }
        }
        assertThat( readable, equalTo( (int)fs.length()));
        encChannel.finishAndReleaseAll();
        decChannel.finishAndReleaseAll();
        
        logger.info( "shouldChunkedContentRequest test... passed");
    }

}
