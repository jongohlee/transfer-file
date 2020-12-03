/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.session;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;


/**
 * @author Jongoh Lee
 */

public class ResourceSessionManager
{
    private Logger logger= LoggerFactory.getLogger( ResourceSessionManager.class);
    
    private static ResourceSessionManager MANAGER;
    
    private final Map<String, ResourceSession> sessions= new ConcurrentHashMap<String, ResourceSession>();
    
    private final ScheduledExecutorService service;
    
    private final long sessionTimeout;

    public static void start( long sessionTimeoutMillis)
    {
        MANAGER= new ResourceSessionManager( sessionTimeoutMillis);
    }
    
    public static void shutdown()
    {
        MANAGER.service.shutdownNow();
        MANAGER= null;
    }
    
    private ResourceSessionManager( long sessionTimeoutMillis)
    {
        this.sessionTimeout= sessionTimeoutMillis;
        this.service= Executors.newSingleThreadScheduledExecutor();
        this.service.scheduleAtFixedRate( new Runnable() {
            public void run()
            {
                sessions.entrySet().removeIf( entry->{
                    ResourceSession session= entry.getValue();
                    if( ( System.currentTimeMillis()- session.accessTime())> ResourceSessionManager.this.sessionTimeout)
                    {
                        session.invalidate();
                        logger.info( "session [{}] is invalidated", session.getSessionId());
                        return true;
                    }
                    else
                        return false;
                });   
            }
        }, 5* 60, 5* 60, TimeUnit.SECONDS);
    }

    
    public static ResourceSession createResourceSession( Resource resource) throws IOException
    {
        return MANAGER.createResourceSession0( resource);
    }

    public static ResourceSession createResourceSession( File file)
    {
        return MANAGER.createResourceSession0( file);
    }

    public static ResourceSession createResourceSession()
    {
        return MANAGER.createResourceSession0();
    }

    public static ResourceSession getSession( String sessionId)
    {
        return MANAGER.getSession0( sessionId);
    }

    public static ResourceSession removeSession( String sessionId)
    {
        return MANAGER.removeSession0( sessionId);
    }


    private synchronized ResourceSession createResourceSession0( Resource resource) throws IOException
    {
        ResourceSession session= ResourceSession.createSession( resource);
        sessions.put( session.getSessionId(), session);
        logger.info( "resourceSession [{}] is created", session.getSessionId());
        return session;
    }

    private synchronized ResourceSession createResourceSession0( File file)
    {
        ResourceSession session= ResourceSession.createSession( file);
        sessions.put( session.getSessionId(), session);
        logger.info( "resourceSession [{}] is created", session.getSessionId());
        return session;
    }

    private synchronized ResourceSession createResourceSession0()
    {
        ResourceSession session= ResourceSession.createSession();
        sessions.put( session.getSessionId(), session);
        logger.info( "resourceSession [{}] is created", session.getSessionId());
        return session;
    }

    private synchronized ResourceSession getSession0( String sessionId)
    {
        return sessions.get( sessionId);
    }

    private synchronized ResourceSession removeSession0( String sessionId)
    {
        logger.info( "resourceSession [{}] is removed", sessionId);
        return sessions.remove( sessionId);
    }
    
}
