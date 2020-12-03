/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.interceptors;

/**
 * @author Jongoh Lee
 *
 */

public interface TransferInterceptor extends Interceptor
{
    default boolean preTransfer( TransferContext context) throws Exception { return true;}

    default void postTransfer( TransferContext context, Exception cause) throws Exception {}

    @Override
    default void afterCompletion( TransferContext context) throws Exception {}
}
