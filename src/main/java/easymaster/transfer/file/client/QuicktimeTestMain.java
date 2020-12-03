/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.client;

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
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.util.ResourceUtils;

import easymaster.transfer.file.util.OptionParameter;
import io.netty.channel.Channel;

/**
 * @author Jongoh Lee
 */

public class QuicktimeTestMain
{
    private static final Logger logger= LoggerFactory.getLogger( QuicktimeTestMain.class);
    
    public static void main( String[] args)
    {
        if( args== null || args.length< 3)
        {
            System.out.println( "Usage: java -cp \"./lib/*:./lib\" easymaster.transfer.file.client.QuicktimeTestMain \n"
                    + "args: \n"
                    + "put host port resource \n"
                    + "putParallel host port resource \n"
                    + "get host port resource \n"
                    + "exist host port resource \n"
                    + "transfer host port host port\n"
                    + "health host port\n"
                    + "info host port\n"
                    + "shutdown host port");
            return;
        }
        
        switch( args[0])
        {
            case "put":
                putResourceTest( args[1], Integer.parseInt( args[2]), args[3]);
                break;
            case "putParallel":
                putParallelResourceTest( args[1], Integer.parseInt( args[2]), args[3]);
                break;
            case "get":
                getResourceTest( args[1], Integer.parseInt( args[2]));
                break;
            case "exist":
                existResourceTest( args[1], Integer.parseInt( args[2]));
                break;
            case "transfer":
                transferResourceTest( args[1], Integer.parseInt( args[2]), args[3], Integer.parseInt( args[4]));
                break;
            case "health":
                healthTest( args[1], Integer.parseInt( args[2]));
                break;    
            case "info":
                infoTest( args[1], Integer.parseInt( args[2]));
                break;    
            case "shutdown":
                shutdownTest( args[1], Integer.parseInt( args[2]));
                break;
            default:
                System.out.println( "invalid arguments");
        }
    }
    
    private static void putResourceTest( String host, int port, String path)
    {
        long now= System.currentTimeMillis();
        logger.info( "=============================================================================");
        logger.info( "Transfer Server put resource Test");
        logger.info( "");
        logger.info( "TransferClient client= TransferClient.create( host, port);");
        logger.info( "try");
        logger.info( "{");
        logger.info( "\tFile resource= ResourceUtils.getFile( path);");
        logger.info( "\tboolean answer= client.requestPutResource( THROWAWAY, resource, \"/20201010/serial-content.zip\", \"biz1\",");
        logger.info( "\tOptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST),");
        logger.info( "\tOptionParameter.param( INTERCEPTOR, \"simpleCustomReceiveInterceptor\"));");
        logger.info( "}");
        logger.info( "catch( Exception e){ logger.error( e.getMessage(), e);}");
        logger.info( "finally{ client.shutdown();}");
        logger.info( "");
        
        TransferClient client= TransferClient.create( host, port);
        try
        {
            File resource= ResourceUtils.getFile( path);
            boolean answer= client.requestPutResource( THROWAWAY, resource, "/20201010/serial-content.zip", "biz1", 
                    OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST),
                    OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"));
            logger.info( "result: {}", answer);
        }
        catch( Exception e){ logger.error( e.getMessage(), e);}
        finally{ client.shutdown();}
        
        logger.info( "elapsed time: {}", System.currentTimeMillis()- now);
        logger.info( "=============================================================================");
    }
    
    private static void putParallelResourceTest( String host, int port, String path)
    {
        long now= System.currentTimeMillis();
        logger.info( "=============================================================================");
        logger.info( "Transfer Server put Parallel resource Test");
        logger.info( "");
        logger.info( "TransferClient client= TransferClient.create( host, port);");
        logger.info( "try");
        logger.info( "{");
        logger.info( "\tFile resource= ResourceUtils.getFile( path);");
        logger.info( "\tboolean answer= client.requestPutParallelResource( resource, \"/20201010/parallel-content.zip\", \"biz1\",");
        logger.info( "\tOptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST),");
        logger.info( "\tOptionParameter.param( CREATE_ACK, \".ack\"),");
        logger.info( "\tOOptionParameter.param( INTERCEPTOR, \"simpleCustomReceiveInterceptor\"));");
        logger.info( "}");
        logger.info( "catch( Exception e){ logger.error( e.getMessage(), e);}");
        logger.info( "finally{ client.shutdown();}");
        logger.info( "");
        
        TransferClient client= TransferClient.create( host, port);
        try
        {
            File resource= ResourceUtils.getFile( path);
            boolean answer= client.requestPutParallelResource( resource, "/20201010/parallel-content.zip", "biz1",
                    OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST),
                    OptionParameter.param( CREATE_ACK, ".ack"),
                    OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"));
            logger.info( "result: {}", answer);
        }
        catch( Exception e){ logger.error( e.getMessage(), e);}
        finally{ client.shutdown();}
        
        logger.info( "elapsed time: {}", System.currentTimeMillis()- now);
        logger.info( "=============================================================================");
    }
    
    private static void getResourceTest( String host, int port)
    {
        long now= System.currentTimeMillis();
        logger.info( "=============================================================================");
        logger.info( "Transfer Server get resource Test");
        logger.info( "");
        logger.info( "TransferClient client= TransferClient.create( host, port);");
        logger.info( "try");
        logger.info( "{");
        logger.info( "\tboolean answer= client.requestGetResource( THROWAWAY, \"/20201010/parallel-content.zip\", \"biz1\", c->{");
        logger.info( "\t\ttry{ c.renameTo( new File( \"./get.zip\")); return true; }");
        logger.info( "\t\tcatch( Exception e) { return false; }");
        logger.info( "\t});");
        logger.info( "}");
        logger.info( "catch( Exception e){ logger.error( e.getMessage(), e);}");
        logger.info( "finally{ client.shutdown();}");
        logger.info( "");
        
        TransferClient client= TransferClient.create( host, port);
        try
        {
            boolean answer= client.requestGetResource( THROWAWAY, "/20201010/parallel-content.zip", "biz1", c->{ 
                try{ c.renameTo( new File( "./get.zip")); return true; }
                catch( Exception e) { return false; }
            });
            logger.info( "result: {}", answer);
        }
        catch( Exception e){ logger.error( e.getMessage(), e);}
        finally{ client.shutdown();}
        
        logger.info( "elapsed time: {}", System.currentTimeMillis()- now);
        logger.info( "=============================================================================");
    }
    
    private static void existResourceTest( String host, int port)
    {
        long now= System.currentTimeMillis();
        logger.info( "=============================================================================");
        logger.info( "Transfer Server exist resource Test");
        logger.info( "");
        logger.info( "TransferClient client= TransferClient.create( host, port);");
        logger.info( "try");
        logger.info( "{");
        logger.info( "\tboolean answer= client.requestResourceExist( THROWAWAY, \"/20201010/parallel-content.zip\", \"biz1\");");
        logger.info( "}");
        logger.info( "catch( Exception e){ logger.error( e.getMessage(), e);}");
        logger.info( "finally{ client.shutdown();}");
        logger.info( "");
        
        TransferClient client= TransferClient.create( host, port);
        try
        {
            boolean answer= client.requestResourceExist( THROWAWAY, "/20201010/parallel-content.zip", "biz1");
            logger.info( "result: {}", answer);
        }
        catch( Exception e){ logger.error( e.getMessage(), e);}
        finally{ client.shutdown();}
        
        logger.info( "elapsed time: {}", System.currentTimeMillis()- now);
        logger.info( "=============================================================================");
    }

    private static void transferResourceTest( String hosts, int ports, String hostd, int portd)
    {
        long now= System.currentTimeMillis();
        logger.info( "=============================================================================");
        logger.info( "Transfer Server transfer resource Test");
        logger.info( "");
        logger.info( "TransferClient client= TransferClient.create( host, port);");
        logger.info( "try");
        logger.info( "{");
        logger.info( "\tList<TransferRequest> trans= new ArrayList<TransferRequest>();");
        logger.info( "\ttrans.add( new TransferRequest()");
        logger.info( "\t\t.from( client)");
        logger.info( "\t\t.resource( \"/20201010/parallel-content.zip\",");
        logger.info( "\t\t\tOptionParameter.param( AFTER_TRANSFER, BACKUP),");
        logger.info( "\t\t\tOptionParameter.param( SITE, \"biz1\"))");
        logger.info( "\t\t.to( hostd, portd)");
        logger.info( "\t\t.path( \"/20201010/parallel-content.zip\",");
        logger.info( "\t\t\tOptionParameter.param( INTERCEPTOR, \"simpleCustomReceiveInterceptor\"),");
        logger.info( "\t\t\tOptionParameter.param( CREATE_ACK, \".done\"),");
        logger.info( "\t\t\tOptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST)));");
        logger.info( "\tboolean answer= client.requestTransferResources( THROWAWAY, trans, true, -1,");
        logger.info( "\t\tOptionParameter.param( INTERCEPTOR, \"simpleCustomTransferInterceptor\"));");
        logger.info( "}");
        logger.info( "catch( Exception e){ logger.error( e.getMessage(), e);}");
        logger.info( "finally{ client.shutdown();}");
        logger.info( "");
        
        TransferClient client= TransferClient.create( hosts, ports);
        try
        {
            List<TransferRequest> trans= new ArrayList<TransferRequest>();
            trans.add( new TransferRequest()
                    .from( client)
                    .resource( "/20201010/parallel-content.zip",
                            OptionParameter.param( AFTER_TRANSFER, BACKUP),
                            OptionParameter.param( SITE, "biz1"))
                    .to( hostd, portd)
                    .path( "/20201010/parallel-content.zip",
                            OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"),
                            OptionParameter.param( CREATE_ACK, ".done"),
                            OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST)));

            boolean answer= client.requestTransferResources( THROWAWAY, trans, true, -1,
                    OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"));
            logger.info( "result: {}", answer);
        }
        catch( Exception e){ logger.error( e.getMessage(), e);}
        finally{ client.shutdown();}
        
        logger.info( "elapsed time: {}", System.currentTimeMillis()- now);
        logger.info( "=============================================================================");
    }
    
    private static void healthTest( String host, int port)
    {
        long now= System.currentTimeMillis();
        logger.info( "=============================================================================");
        logger.info( "Transfer Server healthcheck Test");
        logger.info( "");
        logger.info( "TransferClient client= TransferClient.create( host, port);");
        logger.info( "try");
        logger.info( "{");
        logger.info( "\tHealth health= client.requestHealth( THROWAWAY);");
        logger.info( "}");
        logger.info( "catch( Exception e){ logger.error( e.getMessage(), e);}");
        logger.info( "finally{ client.shutdown();}");
        logger.info( "");
        
        TransferClient client= TransferClient.create( host, port);
        try
        {
            Health health= client.requestHealth( THROWAWAY);
            logger.info( "result: {}", health);
        }
        catch( Exception e){ logger.error( e.getMessage(), e);}
        finally{ client.shutdown();}
        
        logger.info( "elapsed time: {}", System.currentTimeMillis()- now);
        logger.info( "=============================================================================");
    }
    
    private static void infoTest( String host, int port)
    {
        long now= System.currentTimeMillis();
        logger.info( "=============================================================================");
        logger.info( "Transfer Server healthcheck Test");
        logger.info( "");
        logger.info( "TransferClient client= TransferClient.create( host, port);");
        logger.info( "try");
        logger.info( "{");
        logger.info( "\tMap<String, String> info= client.requestServerInfo( THROWAWAY);");
        logger.info( "}");
        logger.info( "catch( Exception e){ logger.error( e.getMessage(), e);}");
        logger.info( "finally{ client.shutdown();}");
        logger.info( "");
        
        TransferClient client= TransferClient.create( host, port);
        try
        {
            Map<String, String> info= client.requestServerInfo( THROWAWAY);
            info.entrySet().stream().forEach( e-> {
                logger.info( "{} : {}", e.getKey(), e.getValue());
            });
            
            //logger.info( "result: {}", info);
        }
        catch( Exception e){ logger.error( e.getMessage(), e);}
        finally{ client.shutdown();}
        
        logger.info( "elapsed time: {}", System.currentTimeMillis()- now);
        logger.info( "=============================================================================");
    }
    
    private static void shutdownTest( String host, int port)
    {
        logger.info( "=============================================================================");
        logger.info( "Transfer Server Management Shutdown Endpoint Test");
        logger.info( "");
        logger.info( "TransferClient client= TransferClient.create( host, port);");
        logger.info( "try");
        logger.info( "{");
        logger.info( "\tChannel channel= client.connect();");
        logger.info( "\tHealth health= client.requestHealth( channel);");
        logger.info( "\tboolean result= client.requestShutdownCommand( channel);");
        logger.info( "\tchannel.close();");
        logger.info( "}");
        logger.info( "catch( Exception e){ logger.error( e.getMessage(), e); }");
        logger.info( "finally{ client.shutdown();}");
        logger.info( "");
        
        TransferClient client= TransferClient.create( host, port);

        try
        {
            Channel channel= client.connect();
            Health health= client.requestHealth( channel);
            logger.info( "server health: {}", health);
            boolean result= client.requestShutdownCommand( channel);
            logger.info( "send shutdownCommand to TransferServer management endpoint");
            logger.info( "server response: {}", result);
            channel.close();
        }
        catch( Exception e){ logger.error( e.getMessage(), e); }
        finally{ client.shutdown();}
        
        logger.info( "=============================================================================");
    }
}
