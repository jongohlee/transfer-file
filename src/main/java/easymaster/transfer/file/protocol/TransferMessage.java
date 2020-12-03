/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.protocol;

import easymaster.transfer.file.util.TransferMessageUtil;
import io.netty.handler.codec.DecoderResult;
import io.netty.util.internal.ObjectUtil;

/**
 * @author Jongoh Lee
 *
 */

public class TransferMessage implements TransferObject
{
    private static final int HASH_CODE_PRIME = 31;

    private final TransferCommand command;

    private final TransferHeaders headers;

    private String uri= "/";

    private FileData content;

    private DecoderResult decoderResult= DecoderResult.SUCCESS;

    public TransferMessage( TransferCommand command)
    {
        this( command, new TransferHeaders());
    }

    public TransferMessage( TransferCommand command, TransferHeaders headers)
    {
        ObjectUtil.checkNotNull( command, "command");
        ObjectUtil.checkNotNull( headers, "headers");
        this.command= command;
        this.headers= headers;
    }

    public TransferCommand command()
    {
        return this.command;
    }

    public TransferMessage setUri( String uri)
    {
        ObjectUtil.checkNotNull( uri, "uri").trim();
        this.uri= uri;
        return this;
    }

    public String uri()
    {
        return this.uri;
    }

    public TransferHeaders headers()
    {
        return this.headers;
    }

    public TransferMessage setContent( FileData content)
    {
        ObjectUtil.checkNotNull( content, "content");
        this.content= content;
        return this;
    }

    public FileData content()
    {
        return this.content;
    }

    @Override
    public DecoderResult decoderResult()
    {
        return this.decoderResult;
    }

    @Override
    public void setDecoderResult( DecoderResult result)
    {
        if( result== null)
            throw new NullPointerException( "decoderResult");
        this.decoderResult= result;
    }

    @Override
    public int hashCode()
    {
        int result= 1;
        result= HASH_CODE_PRIME* result+ command.hashCode();
        result= HASH_CODE_PRIME* result+ uri.hashCode();
        result= HASH_CODE_PRIME* result+ headers.hashCode();
        return result;
    }

    @Override
    public boolean equals( Object o)
    {
        if( !( o instanceof TransferMessage))
            return false;

        TransferMessage other= (TransferMessage)o;
        return command().equals( other.command()) &&
                uri().equalsIgnoreCase( other.uri()) &&
                headers().equals( other.headers());
     }

    @Override
    public String toString()
    {
        return TransferMessageUtil.appendMessage( new StringBuilder( 256), this).toString();
    }
}
