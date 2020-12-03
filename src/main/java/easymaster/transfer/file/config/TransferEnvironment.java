/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.config;

import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.util.StringUtils;

/**
 * @author Jongoh Lee
 */

@ConfigurationProperties( prefix="transfer")
public class TransferEnvironment
{
    private Logger logger= LoggerFactory.getLogger( TransferEnvironment.class);
    
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS= 90;
    public static final long DEFAULT_SESSION_TIMEOUT_SECONDS= 30* 60* 1000;
    public static final int DEFAULT_TRANSFER_TIMEOUT_SECONDS= 10;
    
    
    public String bindAddress;
    
    @DurationUnit( ChronoUnit.MILLIS)
    private Duration connectTimeout= Duration.ofSeconds( DEFAULT_CONNECT_TIMEOUT_SECONDS);

    @DurationUnit( ChronoUnit.MILLIS)
    private Duration sessionTimeout= Duration.ofSeconds( DEFAULT_SESSION_TIMEOUT_SECONDS);
    
    @DurationUnit( ChronoUnit.MILLIS)
    private Duration transferTimeout= Duration.ofSeconds( DEFAULT_TRANSFER_TIMEOUT_SECONDS);
    
    private boolean ssl= false;
    
    private String bind;
    
    private int tcpPort= 8024;
    
    private int bossCount= 1;
    
    private int workerCount= 10;

    private boolean keepAlive= true;
    
    private int backlog= 100;
    
    private int chunkSize= 1024* 1024;
    
    private boolean validation;
    
    private Repository repository;
    
    @NestedConfigurationProperty
    private Map<String, String> custom= new HashMap<String, String>();
    
    public Duration getConnectTimeout()
    {
        return connectTimeout;
    }

    public void setConnectTimeout( Duration connectTimeout)
    {
        this.connectTimeout= connectTimeout;
    }

    public Duration getSessionTimeout()
    {
        return sessionTimeout;
    }

    public void setSessionTimeout( Duration sessionTimeout)
    {
        this.sessionTimeout= sessionTimeout;
    }

    public Duration getTransferTimeout()
    {
        return transferTimeout;
    }

    public void setTransferTimeout( Duration transferTimeout)
    {
        this.transferTimeout= transferTimeout;
    }

    public boolean isSsl()
    {
        return ssl;
    }

    public void setSsl( boolean ssl)
    {
        this.ssl= ssl;
    }

    public String getBind()
    {
        return bind;
    }

    public void setBind( String bind)
    {
        this.bind= bind;
    }

    public int getTcpPort()
    {
        return tcpPort;
    }

    public void setTcpPort( int tcpPort)
    {
        this.tcpPort= tcpPort;
    }

    public int getBossCount()
    {
        return bossCount;
    }

    public void setBossCount( int bossCount)
    {
        this.bossCount= bossCount;
    }

    public int getWorkerCount()
    {
        return workerCount;
    }

    public void setWorkerCount( int workerCount)
    {
        this.workerCount= workerCount;
    }

    public boolean isKeepAlive()
    {
        return keepAlive;
    }

    public void setKeepAlive( boolean keepAlive)
    {
        this.keepAlive= keepAlive;
    }

    public int getBacklog()
    {
        return backlog;
    }

    public void setBacklog( int backlog)
    {
        this.backlog= backlog;
    }

    public int getChunkSize()
    {
        return chunkSize;
    }

    public void setChunkSize( int chunkSize)
    {
        this.chunkSize= chunkSize;
    }

    public boolean isValidation()
    {
        return validation;
    }

    public void setValidation( boolean validation)
    {
        this.validation= validation;
    }

    public Repository getRepository()
    {
        return repository;
    }

    public void setRepository( Repository repository)
    {
        this.repository= repository;
    }
    
    public void validateRepository()
    {
        File root= new File( repository.baseDir);
            logger.debug( "basedir: {}", repository.getBaseDir());
        if( !root.mkdirs() && !root.exists())
            throw new IllegalArgumentException( "invalid base-dir ["+ root+ "]");
        if( root.exists() && !root.isDirectory())
            throw new IllegalArgumentException( "invalid base-dir ["+ root+ "]");

        if( StringUtils.hasText( repository.backupDir))
        {
            root= new File( repository.backupDir);
                logger.debug( "backupdir: {}", repository.getBackupDir());
            if( !root.mkdirs() && !root.exists())
                throw new IllegalArgumentException( "invalid backup-dir ["+ root+ "]");
            if( root.exists() && !root.isDirectory())
                throw new IllegalArgumentException( "invalid backup-dir ["+ root+ "]");
        }

        repository.sites.values().forEach( v-> {
            File loc= new File( v.baseDir);
                logger.debug( "site basedir: {}-{}", v.name, v.baseDir);
            if( !loc.mkdirs() && !loc.exists())
                throw new IllegalArgumentException( "invalid repository base-dir ["+ loc+ "]");
            if( loc.exists() && !loc.isDirectory())
                throw new IllegalArgumentException( "invalid repository base-dir ["+ loc+ "]");

            if( StringUtils.hasText( v.backupDir))
            {
                loc= new File( v.backupDir);
                    logger.debug( "site backupdir: {}-{}", v.name, v.backupDir);
                if( !loc.mkdirs() && !loc.exists())
                    throw new IllegalArgumentException( "invalid repository backup-dir ["+ loc+ "]");
                if( loc.exists() && !loc.isDirectory())
                    throw new IllegalArgumentException( "invalid repository backup-dir ["+ loc+ "]");
            }
        });
    }

    public Map<String, String> getCustom()
    {
        return custom;
    }

    public void setCustom( Map<String, String> custom)
    {
        this.custom= custom;
    }
    
    @Override
    public String toString()
    {
        return "TransferEnvironment [bindAddress="+ bindAddress+ ", connectTimeout="+ connectTimeout
                + ", sessionTimeout="+ sessionTimeout+ ", transferTimeout="+ transferTimeout+ ", ssl="+ ssl+ ", bind="
                + bind+ ", tcpPort="+ tcpPort+ ", bossCount="+ bossCount+ ", workerCount="+ workerCount+ ", keepAlive="
                + keepAlive+ ", backlog="+ backlog+ ", chunkSize="+ chunkSize+ ", validation="+ validation
                + ", repository="+ repository+ ", custom="+ custom+ "]";
    }



    public static class Repository
    {
        private String baseDir;

        private String backupDir;

        private Map<String, Site> sites= new HashMap<String, Site>();

        public String getBaseDir()
        {
            return baseDir;
        }

        public void setBaseDir( String baseDir)
        {
            this.baseDir= StringUtils.cleanPath( new File( baseDir).getAbsolutePath());
        }

        public String getBackupDir()
        {
            return backupDir;
        }

        public void setBackupDir( String backupDir)
        {
            this.backupDir= StringUtils.cleanPath( new File( backupDir).getAbsolutePath());
        }

        public Map<String, Site> getSites()
        {
            return sites;
        }

        public void setSites( Map<String, Site> sites)
        {
            this.sites= sites;
        }

        @Override
        public String toString()
        {
            return "Repository [baseDir="+ baseDir+ ", backupDir="+ backupDir+ ", sites="+ sites+ "]";
        }
        
    }

    public static class Site
    {
        private String name;

        private String baseDir;

        private String backupDir;

        public String getName()
        {
            return name;
        }

        public void setName( String name)
        {
            this.name= name;
        }

        public String getBaseDir()
        {
            return baseDir;
        }

        public void setBaseDir( String baseDir)
        {
            this.baseDir= StringUtils.cleanPath( new File( baseDir).getAbsolutePath());
        }

        public String getBackupDir()
        {
            return backupDir;
        }

        public void setBackupDir( String backupDir)
        {
            this.backupDir= StringUtils.cleanPath( new File( backupDir).getAbsolutePath());
        }

        @Override
        public String toString()
        {
            return "Site [name="+ name+ ", baseDir="+ baseDir+ ", backupDir="+ backupDir+ "]";
        }
    }
    
}
