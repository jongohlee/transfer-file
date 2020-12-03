/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file;

import static easymaster.transfer.file.client.TransferClient.THROWAWAY;
import static easymaster.transfer.file.util.OptionParameter.AFTER_TRANSFER;
import static easymaster.transfer.file.util.OptionParameter.CREATE_ACK;
import static easymaster.transfer.file.util.OptionParameter.INTERCEPTOR;
import static easymaster.transfer.file.util.OptionParameter.ON_EXIST;
import static easymaster.transfer.file.util.OptionParameter.SITE;
import static easymaster.transfer.file.util.OptionParameterValues.BACKUP;
import static easymaster.transfer.file.util.OptionParameterValues.OVERWRITE_ONEXIST;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.util.ResourceUtils;

import easymaster.transfer.file.client.TransferClient;
import easymaster.transfer.file.client.TransferRequest;
import easymaster.transfer.file.util.OptionParameter;
import io.netty.channel.Channel;

/**
 * @author Jongoh Lee
 *
 */

public class StandaloneTestMain
{
    private static Logger logger= LoggerFactory.getLogger( StandaloneTestMain.class);
    
    public static void main( String[] args)
    {
        StandaloneTestMain main= new StandaloneTestMain();
        long now= System.currentTimeMillis();
        
//        main.putResourceTest();
//        main.putParallesResourceTest();
//        main.getResourceTest();
//        main.existResourceTest();
//        main.existResourceTestNotReuse();
        main.transferResourceTest();
//        main.shutdownTest();
        
        logger.info( "===============================================");
        logger.info( "===============================================");
        logger.info( "elapsed time: {}", System.currentTimeMillis()- now);
        logger.info( "===============================================");
        logger.info( "===============================================");
    }
    
    private void putResourceTest()
    {
        TransferClient client= TransferClient.create( "localhost", 8024);
        try
        {
            File resource= ResourceUtils.getFile( "./src/test/resources/parallel-content.zip");
            boolean answer= client.requestPutResource( THROWAWAY, resource, "/backup/20201010/parallel-content.zip", "biz1", 
                    OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST),
                    OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"));
            logger.info( "result: {}", answer);
        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
        }
        finally
        {
            client.shutdown();
        }
    }
    
    private void putParallesResourceTest()
    {
        TransferClient client= TransferClient.create( "localhost", 8024);

        try
        {
            File resource= ResourceUtils.getFile( "./src/test/resources/parallel-content.zip");

            boolean answer= client.requestPutParallelResource( resource, "/backup/20201010/parallel-content.zip", "biz1",
                    OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST),
//                    OptionParam.parameter( ON_EXIST, OptionParameterValues.APPEND_ONEXIST),
                    OptionParameter.param( CREATE_ACK, ".ack"),
                    OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"));

            logger.info( "result: {}", answer);
        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
        }
        finally
        {
            client.shutdown();
        }
    }
    
    private void getResourceTest()
    {
        TransferClient client= TransferClient.create( "localhost", 8024);
        try
        {
            boolean answer= client.requestGetResource( THROWAWAY, "/backup/20201010/parallel-content.zip", "biz1", c->{ 
                try
                {
                    c.renameTo( new File( "/Workspace/Repositories/transfer/get.zip"));
                    return true;
                }
                catch( Exception e)
                {
                    logger.error( e.getMessage(), e);
                    return false;
                }
            });
            logger.info( "result: {}", answer);
        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
        }
    }
    
    private void existResourceTest()
    {
        TransferClient client= TransferClient.create( "localhost", 8024);

        try
        {
            boolean answer= client.requestResourceExist( TransferClient.THROWAWAY, "/backup/20201010/parallel-content.zip", "biz1");
            logger.info( "result: {}", answer);
        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
        }
        finally
        {
            client.shutdown();
        }
    }

    private void existResourceTestNotReuse()
    {
        TransferClient client= TransferClient.create( "localhost", 8024);
        Channel ch= null;
        try
        {
            ch= client.connect();
            boolean answer= client.requestResourceExist( ch, "/backup/20201010/parallel-content.zip", "biz1");
            logger.info( "result: {}", answer);
            answer= client.requestResourceExist( ch, "/backup/20201010/parallel-content.zip", "biz1");
            logger.info( "result: {}", answer);
            answer= client.requestResourceExist( ch, "/backup/20201010/parallel-content.zip", "biz1");
            logger.info( "result: {}", answer);
        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
        }
        finally
        {
            if( ch!= null)
                ch.close();
            client.shutdown();
        }
    }
    
    private void transferResourceTest()
    {
        TransferClient client= TransferClient.create( "localhost", 8024);

        try
        {
            List<TransferRequest> trans= new ArrayList<TransferRequest>();
            trans.add( new TransferRequest()
                    .from( client)
                    .resource( "/backup/20201010/parallel-content.zip",
                            OptionParameter.param( AFTER_TRANSFER, BACKUP),
                            OptionParameter.param( SITE, "biz1"))
                    .to( "localhost", 8025)
//                    .to( "192.168.219.141", 8024)
                    .path( "/backup/20201010/parallel-content.zip",
                            OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"),
                            OptionParameter.param( CREATE_ACK, ".done"),
                            OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST)));

            boolean answer= client.requestTransferResources( THROWAWAY, trans, true, -1,
                    OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"));
            logger.info( "result: {}", answer);
        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
        }
        finally
        {
            client.shutdown();
        }
    }
    
    private void shutdownTest()
    {
        TransferClient client= TransferClient.create( "localhost", 8024);

        try
        {
            Channel channel= client.connect();
            logger.debug( "new channel: {}", channel);
            Health health= client.requestHealth( channel);
            logger.debug( "server health: {}", health);
            boolean result= client.requestShutdownCommand( channel);
            logger.debug( "server response: {}", result);
            channel.close();
        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
        }
        finally
        {
            client.shutdown();
        }

    }
    
}
