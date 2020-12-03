/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.handler;

import java.util.function.Consumer;

import easymaster.transfer.file.protocol.TransferResponseCode;

/**
 * @author Jongoh Lee
 *
 */

public class RequestHandlerException extends Exception
{
    private static final long serialVersionUID= -5437887089307953851L;

    private TransferResponseCode rsCode;

    Consumer<Exception> postProcess;
    
    Consumer<Void> afterComplete;
    
    public RequestHandlerException()
    {
        this( TransferResponseCode.INTERNAL_SERVER_ERROR);
    }

    public RequestHandlerException( TransferResponseCode rsCode)
    {
        this( rsCode, null, null);
    }

    public RequestHandlerException( TransferResponseCode rsCode, String message)
    {
        this( rsCode, message, null);
    }

    public RequestHandlerException( TransferResponseCode rsCode, String message, Throwable cause)
    {
        super( message, cause);
        this.rsCode= rsCode;
    }

    public void setResponseCode( TransferResponseCode rsCode)
    {
        this.rsCode= rsCode;
    }

    public TransferResponseCode getResponseCode()
    {
        return this.rsCode;
    }

    @Override
    public String toString()
    {
        StringBuilder sb= new StringBuilder();
        sb.append( this.rsCode!= null ? "responseCode: ["+ this.rsCode.toString()+ "]\n" : "");
        sb.append( super.toString());
        return sb.toString();
    }
    
}
