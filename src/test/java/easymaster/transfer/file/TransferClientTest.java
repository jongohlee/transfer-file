/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file;

import static easymaster.transfer.file.client.TransferClient.THROWAWAY;
import static easymaster.transfer.file.util.OptionParameter.CREATE_ACK;
import static easymaster.transfer.file.util.OptionParameter.INTERCEPTOR;
import static easymaster.transfer.file.util.OptionParameter.ON_EXIST;
import static easymaster.transfer.file.util.OptionParameterValues.OVERWRITE_ONEXIST;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.springframework.boot.actuate.health.Status.UP;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ResourceUtils;

import easymaster.transfer.file.client.TransferClient;
import easymaster.transfer.file.config.TransferEnvironment;
import easymaster.transfer.file.config.TransferServerConfiguration;
import easymaster.transfer.file.util.FileUtil;
import easymaster.transfer.file.util.OptionParameter;
import io.netty.channel.Channel;

/**
 * @author Jongoh Lee
 *
 */

@RunWith( SpringRunner.class)
@SpringBootTest(
        classes= { TransferServerConfiguration.class},
        properties= { "spring.config.name=transfer-file-test",
                "spring.jmx.enabled=true",
                "transfer.validation=off",
                "transfer.repository.base-dir=./src/test/resources",
                "transfer.repository.sites.biz1.base-dir=./src/test/resources",
                "transfer.ssl=on", 
                "transfer.chunk-size=8192", 
                "transfer.tcp-port=8025", 
                "transfer.bind=127.0.0.1"})
@ActiveProfiles( "test")
public class TransferClientTest
{
    private Logger logger= LoggerFactory.getLogger( TransferClientTest.class);

    @Autowired
    private TransferEnvironment environment;

    @Test
    @DirtiesContext
    public void clientHealthTest() throws Exception
    {
        TransferClient client= TransferClient.create(
                environment.getRepository().getBaseDir(),
                1,
                (int)environment.getConnectTimeout().toSeconds(),
                environment.isSsl(),
                environment.getBind(),
                environment.getTcpPort(),
                environment.getChunkSize());
        try
        {
            Channel channel= client.connect();
            logger.debug( "new channel: {}", channel);

            Health health= client.requestHealth( channel, -1);
            logger.debug( "server health: {}", health);

            assertThat( health, equalTo( Health.status( UP).build()));
            channel.close();
        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
            fail();
        }
        finally
        {
            client.shutdown();
            FileUtil.removeDir( new File( "./src/test/resources/tmp"));
        }
        
        logger.info( "clientHealthTest test... passed");
    }

    @Test
    @DirtiesContext
    public void clientHealthInfoTest() throws Exception
    {
        TransferClient client= TransferClient.create(
                environment.getRepository().getBaseDir(),
                1,
                (int)environment.getConnectTimeout().toSeconds(),
                environment.isSsl(),
                environment.getBind(),
                environment.getTcpPort(),
                environment.getChunkSize());
        try
        {
            logger.debug( "client connected to {}:{}", environment.getBind(), environment.getTcpPort());

            Channel channel= client.connect();
            logger.debug( "new channel: {}", channel);

            Health health= client.requestHealth( channel, 1000);
            logger.debug( "server health: {}", health);
            assertThat( health, equalTo( Health.status( UP).build()));

            Map<String, String> infos= client.requestServerInfo( channel);
            logger.debug( "server info: {}", infos);

            assertThat( infos.containsKey( "transfer.bind"), is( true));

            channel.close();
        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
            fail();
        }
        finally
        {
            client.shutdown();
            FileUtil.removeDir( new File( "./src/test/resources/tmp"));
        }
        
        logger.info( "clientHealthInfoTest test... passed");
    }

    @Test
    @DirtiesContext
    public void clientResourceExistTest() throws Exception
    {
        TransferClient client= TransferClient.create(
                environment.getRepository().getBaseDir(),
                2,
                (int)environment.getConnectTimeout().toSeconds(),
                environment.isSsl(),
                environment.getBind(),
                environment.getTcpPort(),
                environment.getChunkSize());
        try
        {
            boolean exist= client.requestResourceExist( THROWAWAY, "fixed-content.done", null);
            assertThat( exist, is( true));

            boolean nexist= client.requestResourceExist( THROWAWAY, "fixed-content.noexist", null);
            assertThat( nexist, is( false));

        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
            fail();
        }
        finally
        {
            client.shutdown();
            FileUtil.removeDir( new File( "./src/test/resources/tmp"));
        }
        
        logger.info( "clientResourceExistTest test... passed");
    }

    @Test
    @DirtiesContext
    public void clientResourceGetTest() throws Exception
    {
        TransferClient client= TransferClient.create(
                environment.getRepository().getBaseDir(),
                1,
                (int)environment.getConnectTimeout().toSeconds(),
                environment.isSsl(),
                environment.getBind(),
                environment.getTcpPort(),
                environment.getChunkSize());
        try
        {
            final File valid= new File( environment.getRepository().getBaseDir()+ File.separator+ "received-content.dat");
            boolean result= client.requestGetResource(
                    TransferClient.THROWAWAY, "chunked-content.jar", null, content->{
                        try
                        {
                            content.renameTo( valid);
                            return true;
                        }
                        catch( IOException e)
                        {
                            logger.error( e.getMessage(), e);
                            return false;
                        }
                    }, OptionParameter.param( INTERCEPTOR, "simpleCustomTransferInterceptor"));

            assertThat( result, is( true));
            assertThat( valid.exists(), is( true));

            FileUtil.deleteFile( valid);
            FileUtil.removeDir( new File( "./src/test/resources/tmp"));
        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
            fail();
        }
        finally
        {
            client.shutdown();
            FileUtil.removeDir( new File( "./src/test/resources/tmp"));
        }
        
        logger.info( "clientResourceGetTest test... passed");
    }

    @Test
    @DirtiesContext
    public void clientResourceDeleteTest() throws Exception
    {
        TransferClient client= TransferClient.create(
                environment.getRepository().getBaseDir(),
                1,
                (int)environment.getConnectTimeout().toSeconds(),
                environment.isSsl(),
                environment.getBind(),
                environment.getTcpPort(),
                environment.getChunkSize());
        try
        {
            File dir= new File( "./src/test/resources/backup/"+ new SimpleDateFormat( "yyyyMMdd").format( new Date()));
            dir.mkdirs();
            File checkFs1= new File( dir, "check1.done");
            checkFs1.createNewFile();

            File checkFs2= new File( dir, "check2.done");
            checkFs2.createNewFile();
            assertThat( checkFs1.exists(), is( true));
            assertThat( checkFs2.exists(), is( true));

            List<String> deleteds= client.requestDeleteResources(
                    TransferClient.THROWAWAY, "/backup/**/*.done", null, new OptionParameter[] {});

            logger.debug( "deleted: {}", deleteds);

            assertThat( checkFs1.exists(), is( false));
            assertThat( checkFs2.exists(), is( false));

            FileUtil.removeDir( new File( "./src/test/resources/backup"));
            FileUtil.removeDir( new File( "./src/test/resources/tmp"));
        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
            fail();
        }
        finally
        {
            client.shutdown();
            FileUtil.removeDir( new File( "./src/test/resources/tmp"));
        }
        
        logger.info( "clientResourceDeleteTest test... passed");
    }
    
    @Test
    @DirtiesContext
    public void clientResourceListTest() throws Exception
    {
        TransferClient client= TransferClient.create(
                environment.getRepository().getBaseDir(),
                1,
                (int)environment.getConnectTimeout().toSeconds(),
                environment.isSsl(),
                environment.getBind(),
                environment.getTcpPort(),
                environment.getChunkSize());
        try
        {
            File dir= new File( "./src/test/resources/backup/"+ new SimpleDateFormat( "yyyyMMdd").format( new Date()));
            dir.mkdirs();
            File checkFs1= new File( dir, "check1.done");
            checkFs1.createNewFile();

            File checkFs2= new File( dir, "check2.done");
            checkFs2.createNewFile();
            assertThat( checkFs1.exists(), is( true));
            assertThat( checkFs2.exists(), is( true));

            List<String> resources= client.requestListResources(
                    TransferClient.THROWAWAY, "/backup/**/*", null, new OptionParameter[] {});

            logger.debug( "resources: {}", resources);

            assertThat( checkFs1.exists(), is( true));
            assertThat( checkFs2.exists(), is( true));

            FileUtil.removeDir( new File( "./src/test/resources/backup"));
            FileUtil.removeDir( new File( "./src/test/resources/tmp"));
        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
            fail();
        }
        finally
        {
            client.shutdown();
            FileUtil.removeDir( new File( "./src/test/resources/tmp"));
        }
        
        logger.info( "clientResourceListTest test... passed");
    }

    @Test
    @DirtiesContext
    public void clientResourcePutTest() throws Exception
    {
        TransferClient client= TransferClient.create(
                environment.getRepository().getBaseDir(),
                1,
                (int)environment.getConnectTimeout().toSeconds(),
                environment.isSsl(),
                environment.getBind(),
                environment.getTcpPort(),
                environment.getChunkSize());
        try
        {
            File resource= ResourceUtils.getFile(
                    environment.getRepository().getBaseDir()+ File.separator+ "parallel-content.zip");

            boolean result= client.requestPutResource( THROWAWAY, resource,
                    "/backup/"+ new SimpleDateFormat( "yyyyMMdd").format( new Date())+ "/parallel-content.bak", null,
                    OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST),
                    OptionParameter.param( CREATE_ACK, ".ack"));

            File valid= new File( environment.getRepository().getBaseDir()+ "/backup/"+
                    new SimpleDateFormat( "yyyyMMdd").format( new Date())+ "/parallel-content.bak");
            File ack= new File( environment.getRepository().getBaseDir()+ "/backup/"+
                    new SimpleDateFormat( "yyyyMMdd").format( new Date())+ "/parallel-content.ack");

            assertThat( result, is( true));
            assertThat( valid.exists(), is( true));
            assertThat( ack.exists(), is( true));
        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
            fail();
        }
        finally
        {
            client.shutdown();
            FileUtil.removeDir( new File( "./src/test/resources/backup"));
            FileUtil.removeDir( new File( "./src/test/resources/tmp"));
        }
        
        logger.info( "clientResourcePutTest test... passed");
    }

    @Test
    @DirtiesContext
    public void clientResourcePutParallelTest() throws Exception
    {
        TransferClient client= TransferClient.create(
                environment.getRepository().getBaseDir(),
                5,
                (int)environment.getConnectTimeout().toSeconds(),
                environment.isSsl(),
                environment.getBind(),
                environment.getTcpPort(),
                environment.getChunkSize());
        try
        {
            File resource= ResourceUtils.getFile(
                    environment.getRepository().getBaseDir()+ File.separator+ "parallel-content.zip");

            boolean answer= client.requestPutParallelResource( resource,
                    "/backup/"+ new SimpleDateFormat( "yyyyMMdd").format( new Date())+ "/parallel-content.bak", "biz1",
                    OptionParameter.param( ON_EXIST, OVERWRITE_ONEXIST),
                    OptionParameter.param( CREATE_ACK, ".ack"),
                    OptionParameter.param( INTERCEPTOR, "simpleCustomReceiveInterceptor"));

            File valid= new File( environment.getRepository().getBaseDir()+ "/backup/"+
                    new SimpleDateFormat( "yyyyMMdd").format( new Date())+ "/parallel-content.bak");
            File ack= new File( environment.getRepository().getBaseDir()+ "/backup/"+
                    new SimpleDateFormat( "yyyyMMdd").format( new Date())+ "/parallel-content.ack");

            assertThat( answer, is( true));
            assertThat( valid.exists(), is( true));
            assertThat( ack.exists(), is( true));
        }
        catch( Exception e)
        {
            logger.error( e.getMessage(), e);
            fail();
        }
        finally
        {
            client.shutdown();
            FileUtil.removeDir( new File( "./src/test/resources/backup"));
            FileUtil.removeDir( new File( "./src/test/resources/tmp"));
        }
        
        logger.info( "clientResourcePutParallelTest test... passed");
    }

}
