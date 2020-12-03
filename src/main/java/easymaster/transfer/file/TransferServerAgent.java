/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file;

import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;

/**
 * @author Jongoh Lee
 */

public class TransferServerAgent
{
    private Logger logger= LoggerFactory.getLogger( TransferServerAgent.class);
    
    private InetSocketAddress port;
    
    private ServerBootstrap bootstrap;
    
    private Channel serverChannel;
    
    public TransferServerAgent( ServerBootstrap bootstrap, InetSocketAddress port)
    {
        this.port= port;
        this.bootstrap= bootstrap;
    }
    
    public void start() throws Exception
    {
        serverChannel= bootstrap.bind( port).sync().channel();
        logger.info( "File Transfer Server is started ....................................................");
    }
    
    public void block() throws Exception
    {
        serverChannel.closeFuture().sync();
    }
    
    public void destory() throws Exception
    {
        if( serverChannel!= null)
        {
            serverChannel.close();
            serverChannel.parent().close();
        }
        logger.info( "File Transfer Server is stopped ....................................................");
    }
}
