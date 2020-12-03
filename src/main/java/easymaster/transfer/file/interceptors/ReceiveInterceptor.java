/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.interceptors;

/**
 * @author Jongoh Lee
 *
 */

public interface ReceiveInterceptor extends Interceptor
{
    default boolean preReceive( TransferContext context) throws Exception { return true; }

    default void postReceive( TransferContext context, Exception cause) throws Exception {}

    @Override
    default void afterCompletion( TransferContext context) throws Exception {}
}
