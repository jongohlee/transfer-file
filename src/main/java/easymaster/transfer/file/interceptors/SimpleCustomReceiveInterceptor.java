/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.interceptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jongoh Lee
 *
 */

public class SimpleCustomReceiveInterceptor implements ReceiveInterceptor
{
    private Logger logger= LoggerFactory.getLogger( SimpleCustomReceiveInterceptor.class);
 
    @Override
    public boolean preReceive( TransferContext context) throws Exception
    {
        logger.debug( "custom interceptoer {} preReceive", SimpleCustomReceiveInterceptor.class);
        // check if required
        return true;
    }

    @Override
    public void postReceive( TransferContext context, Exception cause) throws Exception
    {
        logger.debug( "custom interceptoer {} postReceive", SimpleCustomReceiveInterceptor.class);
        logger.info( "decryption processing if required");
        // process if required
    }

    @Override
    public void afterCompletion( TransferContext context) throws Exception
    {
        logger.debug( "custom interceptoer {} afterCompletion", SimpleCustomReceiveInterceptor.class);
        // process if required
    }

}
