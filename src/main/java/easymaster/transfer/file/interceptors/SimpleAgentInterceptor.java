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

public class SimpleAgentInterceptor implements AgentInterceptor
{
    private Logger logger= LoggerFactory.getLogger( SimpleAgentInterceptor.class);
    
    @Override
    public boolean preProcess( TransferContext context) throws Exception
    {
        logger.debug( "{} interceptor preProcess ........................... executed", getClass());
        return true;
    }

    @Override
    public void postProcess( TransferContext context) throws Exception
    {
        logger.debug( "{} interceptor postProcess ........................... executed", getClass());
    }

    @Override
    public void afterCompletion( TransferContext context) throws Exception
    {
        logger.debug( "{} interceptor afterCompletion ........................... executed", getClass());
    }
    
}
