/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.client;

import static easymaster.transfer.file.protocol.TransferResponseCode.INTERNAL_SERVER_ERROR;

import easymaster.transfer.file.protocol.TransferResponseCode;

/**
 * @author Jongoh Lee
 *
 */

public class ResponseHandlerException extends Exception
{
    private static final long serialVersionUID= 8277585441985150005L;

    private TransferResponseCode rsCode;
    
    public ResponseHandlerException()
    {
        this( INTERNAL_SERVER_ERROR);
    }
    
    public ResponseHandlerException( TransferResponseCode rsCode)
    {
        this( rsCode, null, null);
    }
    
    public ResponseHandlerException( TransferResponseCode rsCode, String message)
    {
        this( rsCode, message, null);
    }

    public ResponseHandlerException( TransferResponseCode rsCode, String message, Throwable cause)
    {
        super( message, cause);
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
