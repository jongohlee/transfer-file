/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.client;

/**
 * @author Jongoh Lee
 *
 */

@FunctionalInterface
public interface ResponseConsumer<TransferMessage, R>
{
    R accept( TransferMessage t) throws Exception;
}
