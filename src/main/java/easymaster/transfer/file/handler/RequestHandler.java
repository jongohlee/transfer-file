/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.handler;

/**
 * @author Jongoh Lee
 *
 */

@FunctionalInterface
public interface RequestHandler<T>
{
    T handle() throws RequestHandlerException;
}
