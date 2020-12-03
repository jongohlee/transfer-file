/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.interceptors;

/**
 * @author Jongoh Lee
 *
 */

public interface AgentInterceptor extends Interceptor
{
    default boolean preProcess( TransferContext context) throws Exception { return true;}
    
    default void postProcess( TransferContext context) throws Exception {}
    
    @Override
    default void afterCompletion( TransferContext context) throws Exception {}
}
