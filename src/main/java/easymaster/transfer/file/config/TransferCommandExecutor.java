/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 파일 전송을 비동기로 처리하기 위한 Executor
 *  
 * @author Jongoh Lee
 *
 */
public class TransferCommandExecutor
{
    private static TransferCommandExecutor EXECUTOR;
    
    private final ExecutorService executor;
    
    private TransferCommandExecutor( int count)
    {
        this.executor= Executors.newFixedThreadPool( count);
    }
    
    static void start( int count)
    {
        EXECUTOR= new TransferCommandExecutor( count);
    }
    
    static void shutdown()
    {
        EXECUTOR.executor.shutdownNow();
        EXECUTOR= null;
    }
    
    public static ExecutorService transferExecutor()
    {
        return EXECUTOR.executor;
    }
    
    
}
