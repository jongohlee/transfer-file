/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.interceptors;

import org.springframework.beans.TypeConverter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import easymaster.transfer.file.protocol.TransferHeaders;


/**
 * @author Jongoh Lee
 *
 */

public class TransferContext
{
    private final ApplicationContext applicationContext;

    private final TransferHeaders headers;

    private String[] handlingFilePaths;

    private String currentHandlingFilePath;

    protected TransferContext( ApplicationContext context, TransferHeaders headers, String currentHandlingFilePath,
            String[] handlingFilePaths)
    {
        this.applicationContext= context;
        this.headers= headers;
        this.currentHandlingFilePath= currentHandlingFilePath;
        this.handlingFilePaths= handlingFilePaths;
    }

    public static TransferContext createTransferContext( ApplicationContext context, TransferHeaders headers)
    {
        return new TransferContext( context, headers, null, null);
    }

    public static TransferContext createTransferContext( ApplicationContext context, TransferHeaders headers,
            String[] handlingFilePaths)
    {
        return new TransferContext( context, headers, null, handlingFilePaths);
    }

    public static TransferContext createTransferContext( ApplicationContext context, TransferHeaders headers,
            String currentHandlingFilePath)
    {
        return new TransferContext( context, headers, currentHandlingFilePath, null);
    }

    public ApplicationContext getApplicationContext()
    {
        return this.applicationContext;
    }

    public TransferHeaders getHeaders()
    {
        return this.headers;
    }

    public String getHeader( CharSequence name)
    {
        return this.headers.get( name);
    }

    public int getHeaderInt( CharSequence name)
    {
        return this.headers.getInt( name);
    }

    public int getHeaderInt( CharSequence name, int defaultValue)
    {
        return this.headers.getInt( name, defaultValue);
    }

    public short getHeaderShort( CharSequence name)
    {
        return this.headers.getShort( name);
    }

    public short getHeaderShort( CharSequence name, short defaultValue)
    {
        return this.headers.getShort( name, defaultValue);
    }

    public long getTimeMillis( CharSequence name, long defaultValue)
    {
        return this.headers.getTimeMillis( name, defaultValue);
    }

    public Class<?> loadClass( String name) throws Exception
    {
        return ( (ConfigurableApplicationContext)applicationContext)
                .getBeanFactory().getBeanClassLoader().loadClass( name);
    }

    public Resource getResource( String location)
    {
        return applicationContext.getResource( location);
    }

    public Resource[] getResources( String location) throws Exception
    {
        return applicationContext.getResources( location);
    }

    public TypeConverter getTypeConverter()
    {
        return ( (ConfigurableApplicationContext)applicationContext).getBeanFactory().getTypeConverter();
    }

    public String[] getHandlingFilePaths()
    {
        return handlingFilePaths!= null ? handlingFilePaths :
            StringUtils.hasText( currentHandlingFilePath) ? new String[] {currentHandlingFilePath} : new String[]{};
    }

    public void setHandlingFilePaths( String[] handlingFilePaths)
    {
        this.handlingFilePaths= handlingFilePaths;
    }

    public String getCurrentHandlingFilePath()
    {
        return currentHandlingFilePath;
    }

    public void setCurrentHandlingFilePath( String currentHandlingFilePath)
    {
        this.currentHandlingFilePath= currentHandlingFilePath;
    }
}
