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

public class SimpleCustomTransferInterceptor implements TransferInterceptor
{
    private Logger logger= LoggerFactory.getLogger( SimpleCustomTransferInterceptor.class);
    
    @Override
    public boolean preTransfer( TransferContext context) throws Exception
    {
        logger.debug( "custom interceptoer {} preTransfer", SimpleCustomTransferInterceptor.class);
        // check if required
        return true;
    }

    @Override
    public void postTransfer( TransferContext context, Exception cause) throws Exception
    {
        logger.debug( "custom interceptoer {} postTransfer", SimpleCustomTransferInterceptor.class);
        // process if required
    }

    @Override
    public void afterCompletion( TransferContext context) throws Exception
    {
        logger.debug( "custom interceptoer {} afterCompletion", SimpleCustomTransferInterceptor.class);
        // process if required
    }
    
}
