/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.handler;

import java.util.function.Consumer;

import easymaster.transfer.file.protocol.TransferMessage;

/**
 * @author Jongoh Lee
 *
 */

public class HandlerResponse
{
    TransferMessage response;
    
    Consumer<Void> postProcess;
    
    Consumer<Void> afterComplete;
    
    HandlerResponse( TransferMessage response)
    {
        this( response, null, null);
    }
    
    HandlerResponse( TransferMessage response, Consumer<Void> postProcess, Consumer<Void> afterComplete)
    {
        this.response= response;
        this.postProcess= postProcess;
        this.afterComplete= afterComplete;
    }
    
    public TransferMessage getResponse()
    {
        return this.response;
    }
}
