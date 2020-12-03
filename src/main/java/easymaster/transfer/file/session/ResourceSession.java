/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.session;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import easymaster.transfer.file.util.FileUtil;

/**
 * @author Jongoh Lee
 *
 */
public class ResourceSession
{
    private Logger logger= LoggerFactory.getLogger( ResourceSession.class);

    private final String sessionId;
    
    private final long createdTime;
    
    private final Map<String, Resource> toDeletes;
    
    private ResourceSessionStatus status;
    
    private long activatedTime= -1L;

    private long accessTime;

    private boolean activated;

    private boolean processing;
    
    private ResourceSession()
    {
        this.sessionId= UUID.randomUUID().toString();
        this.toDeletes= Collections.synchronizedMap( new HashMap<String, Resource>());
        this.createdTime= System.currentTimeMillis();
        
        logger.info( "Resource Session [{}] is created.", sessionId);
    }
        
    static ResourceSession createSession( Resource resource) throws IOException
    {
        ResourceSession session= new ResourceSession();
        session.status= ResourceSessionStatus.ACTIVE;
        session.accessTime= session.createdTime;
        session.toDeletes.put( resource.getFile().getAbsolutePath(), resource);
        return session;
    }
    
    static ResourceSession createSession( File file)
    {
        ResourceSession session= new ResourceSession();
        session.status= ResourceSessionStatus.ACTIVE;
        session.accessTime= session.createdTime;
        session.toDeletes.put( file.getAbsolutePath(), new FileSystemResource( file));
        return session;
    }
    
    static ResourceSession createSession()
    {
        ResourceSession session= new ResourceSession();
        session.status= ResourceSessionStatus.ACTIVE;
        session.accessTime= session.createdTime;
        return session;
    }
    
    synchronized void invalidate()
    {
        this.status= ResourceSessionStatus.INVALIDATED;
        this.toDeletes.values().parallelStream().forEach( rs->{
            try
            {
                File fs = rs.getFile();
                if( fs.exists() && fs.isFile())
                {
                    logger.info( "invalidated session's resource [{}] will be deleted", fs.getAbsolutePath());
                    FileUtil.deleteFile( fs);
                }
            }
            catch( IOException e)
            {
                logger.warn( "invalidated resource session's file[{}] is not exist", rs);
            }
        });
        this.toDeletes.clear();
    }
    
    public String getSessionId()
    {
        return this.sessionId;
    }

    public synchronized boolean isProcession()
    {
        this.accessTime= System.currentTimeMillis();
        return this.processing;
    }

    public synchronized boolean isActivated()
    {
        this.accessTime= System.currentTimeMillis();
        return this.activated;
    }

    public synchronized void activate()
    {
        this.activatedTime= System.currentTimeMillis();
        this.activated= true;
        this.accessTime= activatedTime;
    }

    public synchronized void expire()
    {
        this.toDeletes.clear();
        this.accessTime= System.currentTimeMillis();
        ResourceSessionManager.removeSession( sessionId);
    }

    public synchronized void processing( Resource resource) throws IOException
    {
        this.toDeletes.put( resource.getFile().getAbsolutePath(), resource);
        this.processing= true;
        this.accessTime= System.currentTimeMillis();
    }

    public synchronized void processing( File file)
    {
        this.toDeletes.put( file.getAbsolutePath(), new FileSystemResource( file));
        this.processing= true;
        this.accessTime= System.currentTimeMillis();
    }

    public synchronized void completed( Resource resource) throws IOException
    {
        this.toDeletes.remove( resource.getFile().getAbsolutePath());
        this.accessTime= System.currentTimeMillis();
    }

    public synchronized void completed( File file)
    {
        this.toDeletes.remove( file.getAbsolutePath());
        this.accessTime= System.currentTimeMillis();
    }

    public synchronized void processing()
    {
        this.processing= true;
        this.accessTime= System.currentTimeMillis();
    }

    public synchronized long accessTime()
    {
        return this.accessTime;
    }

    public synchronized ResourceSessionStatus status()
    {
        return this.status;
    }
}
