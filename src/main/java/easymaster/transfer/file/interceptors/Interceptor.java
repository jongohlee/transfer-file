/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.interceptors;

/**
 * @author Jongoh Lee
 *
 */

public interface Interceptor
{
    void afterCompletion( TransferContext context) throws Exception;
}
