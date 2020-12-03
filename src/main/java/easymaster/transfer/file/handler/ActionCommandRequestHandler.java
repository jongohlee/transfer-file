/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.handler;


import static easymaster.transfer.file.handler.TransferAction.MERGE_;
import static easymaster.transfer.file.handler.TransferAction.NOOP_;
import static easymaster.transfer.file.handler.TransferAction.SESSION_;
import static easymaster.transfer.file.handler.TransferAction.SHUTDOWN_;
import static easymaster.transfer.file.protocol.TransferCommand.ACTION;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT_TYPE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.MERGE_RESOURCE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.REASON;
import static easymaster.transfer.file.protocol.TransferHeaderNames.SESSION_ID;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_DESTINATION_URI;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_SOURCE_URI;
import static easymaster.transfer.file.protocol.TransferResponseCode.ALREADY_EXIST;
import static easymaster.transfer.file.protocol.TransferResponseCode.BAD_REQUEST;
import static easymaster.transfer.file.protocol.TransferResponseCode.DELETE_FAILED;
import static easymaster.transfer.file.protocol.TransferResponseCode.FILE_PERMISSION_ERROR;
import static easymaster.transfer.file.protocol.TransferResponseCode.MERGE_FAILED;
import static easymaster.transfer.file.util.FileUtil.PARALLEL_SPLIT_SUFFIX;
import static easymaster.transfer.file.util.OptionParameter.CREATE_ACK;
import static easymaster.transfer.file.util.OptionParameter.INTERCEPTOR;
import static easymaster.transfer.file.util.OptionParameter.ON_EXIST;
import static easymaster.transfer.file.util.OptionParameter.SITE;
import static easymaster.transfer.file.util.OptionParameterValues.FAIL_ONEXIST;
import static easymaster.transfer.file.util.OptionParameterValues.OVERWRITE_ONEXIST;
import static easymaster.transfer.file.util.TransferConstants.BIND_ADDRESS;

import java.io.File;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringUtils;

import easymaster.transfer.file.TransferServer;
import easymaster.transfer.file.config.TransferEnvironment;
import easymaster.transfer.file.config.TransferEnvironment.Site;
import easymaster.transfer.file.interceptors.ReceiveInterceptor;
import easymaster.transfer.file.interceptors.TransferContext;
import easymaster.transfer.file.protocol.TransferMessage;
import easymaster.transfer.file.protocol.TransferResponseCode;
import easymaster.transfer.file.session.ResourceSession;
import easymaster.transfer.file.session.ResourceSessionManager;
import easymaster.transfer.file.util.FileUtil;
import easymaster.transfer.file.util.OptionParameter;
import easymaster.transfer.file.util.TransferMessageUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.ObjectUtil;

/**
 * @author Jongoh Lee
 *
 */

public class ActionCommandRequestHandler extends AbstractRequestHandler
{
    private final ChannelHandlerContext context;
    
    private final PathMatcher pathMatcher= new AntPathMatcher();
    
    public ActionCommandRequestHandler( ChannelHandlerContext context, ApplicationContext applicationContext, 
            TransferEnvironment environment)
    {
        super( applicationContext, environment);
        this.context= context;
    }

    @Override
    protected void handleCommand( TransferMessage request)
    {
        ObjectUtil.checkNotNull( request, "request");
        logger.info( "actionCommand request is accepted. from: [{}:{}]",
                request.headers().get( AGENT_TYPE),
                request.headers().get( AGENT));
      
        TransferAction action= TransferAction.valueOf( request.uri());

        switch( action.uri())
        {
            case NOOP_:
                break;
            case SHUTDOWN_:
                response( context, ()-> {
                    TransferMessage response= new TransferMessage( ACTION);
                    response.setUri( request.uri());
                    response.headers().add( REASON, "agentServet["+ environment.getCustom().get( BIND_ADDRESS)+ ":"
                            + environment.getTcpPort()+ "] will be shutdown");
                    return new HandlerResponse( response);
                }, true);

                try{ Thread.sleep( 500L); }
                catch (InterruptedException ex){ Thread.currentThread().interrupt();}
                
                ( (ConfigurableApplicationContext)applicationContext).close();
                break;
            case SESSION_:
                response( context, ()->{
                    TransferMessage response= new TransferMessage( ACTION);
                    response.setUri( request.uri());
                    ResourceSession session= ResourceSessionManager.createResourceSession();
                    response.headers().add( SESSION_ID, session.getSessionId());
                    return new HandlerResponse( response);
                }, false);
                break;
            case MERGE_:
                response( context, ()->{
                    TransferMessage response= new TransferMessage( ACTION);
                    response.setUri( request.uri());
                   
                    Map<String, List<String>> srcOpts= new LinkedHashMap<String, List<String>>();
                    Map<String, List<String>> targetOpts= new LinkedHashMap<String, List<String>>();
                    
                    List<String> resources= request.headers().getAll( MERGE_RESOURCE);
                    String sessionId= request.headers().get( SESSION_ID);
                    String source= request.headers().get( TRANSFER_SOURCE_URI);
                    String target= request.headers().get( TRANSFER_DESTINATION_URI);
                    
                    if( !StringUtils.hasText( source))
                        throw new RequestHandlerException( BAD_REQUEST, TRANSFER_SOURCE_URI+ " not found");
                    if( !StringUtils.hasText( target))
                        throw new RequestHandlerException( BAD_REQUEST, TRANSFER_DESTINATION_URI+ " not found");

                    TransferContext transfer= null;
                    Site site= null;
                    try
                    {
                        source= TransferMessageUtil.decodeUri( source, srcOpts);
                        target= TransferMessageUtil.decodeUri( target, targetOpts);

                        StringBuilder pathBuilder= new StringBuilder( target);
                        StringBuilder mergeFrom= new StringBuilder();
                        String siteOp= OptionParameter.first( targetOpts, SITE);
                        if( StringUtils.hasText( siteOp) && ( site= environment.getRepository().getSites().get( siteOp))!= null)
                        {
                            pathBuilder.insert( 0, site.getBaseDir());
                            mergeFrom.insert( 0, site.getBaseDir());
                        }
                        else
                        {
                            pathBuilder.insert( 0, environment.getRepository().getBaseDir());
                            mergeFrom.insert( 0, environment.getRepository().getBaseDir());
                        }
                        target= pathBuilder.toString();
                        logger.debug( "merge target path: {} from: {}", target, mergeFrom.toString());

                        resources= resources.stream()
                                    .map( r-> { return mergeFrom.toString()+ r;})
                                    .sorted( Comparator.comparingInt( p->{
                                    String n= FileUtil.onlyExt( (String)p).replace( PARALLEL_SPLIT_SUFFIX, "");
                                    return Integer.parseInt( n);
                                })).collect( Collectors.toList());
                        
                        if( pathMatcher.isPattern( target))
                            throw new RequestHandlerException( BAD_REQUEST, "pattern uri is not allowed");

                        synchronized( TransferServer.class)
                        {
                            File dir= new File( FileUtil.onlyPath( target));
                            if( !dir.mkdirs() && !dir.exists())
                                throw new RequestHandlerException( FILE_PERMISSION_ERROR, "Can not create dest dir");
                        }
                        File dest= new File( target);
                        if( dest.exists())
                        {
                            String onExist= OptionParameter.first( targetOpts, ON_EXIST, FAIL_ONEXIST);
                            if( FAIL_ONEXIST.equalsIgnoreCase( onExist))
                                throw new RequestHandlerException( ALREADY_EXIST, "target file["+ target+ "] is already exist");
                            else if( OVERWRITE_ONEXIST.equalsIgnoreCase( onExist) && !dest.delete())
                                throw new RequestHandlerException( DELETE_FAILED, 
                                        "target file["+ target+ "] is already exist, deletion for overwrite is failed");
                        }

                        ResourceSession session= StringUtils.hasText( sessionId) ? ResourceSessionManager.getSession( sessionId) : null;
                        FileUtil.mergeWithLock( resources, target, fs->{ if( session!= null) session.completed( fs);});

                        if( session!= null)
                            session.expire();

                        transfer= TransferContext.createTransferContext( 
                                applicationContext, request.headers(), new String[] {target});
                        preProcess( transfer, targetOpts.get( INTERCEPTOR), request.headers(), site, ReceiveInterceptor.class);

                        final TransferContext trCtx= transfer;
                        final Site trSite= site;
                        Consumer<Void> postProcess= ( Void)->{
                            try
                            {
                                postProcess( trCtx, targetOpts.get( INTERCEPTOR), request.headers(), trSite, ReceiveInterceptor.class, null);
                            }
                            catch( Throwable th) { logger.warn( "postProcess execution is failed", th);}
                        };
                        
                        Consumer<Void> afterCompletion= ( Void)->{
                            try { afterCompletion( trCtx, targetOpts.get( INTERCEPTOR));}
                            catch( Throwable th) { logger.warn( "afterCompletion execution is failed", th);}
                        };

                        response.headers().add( REASON, "file ["+ target+ "] is merged");

                        String ack= null;
                        if( ( ack= OptionParameter.first( targetOpts, CREATE_ACK))!= null)
                        {
                            ack= !ack.startsWith( ".") ? "."+ ack : ack;
                            ack= FileUtil.stripExt( target)+ ack;
                            File ackFs= new File( ack);
                            ackFs.createNewFile();
                        }
                        
                        return new HandlerResponse( response, postProcess, afterCompletion);
                    }
                    catch( Exception e)
                    {
                        final TransferContext trCtx= transfer;
                        final Site trSite= site;
                        Consumer<Exception> postProcess= ( er)->{
                            try
                            {
                                postProcess( trCtx, targetOpts.get( INTERCEPTOR), request.headers(), trSite, ReceiveInterceptor.class, er);
                            }
                            catch( Throwable th) { logger.warn( "postProcess execution is failed", th);}
                        };
                        
                        Consumer<Void> afterCompletion= ( Void)->{
                            try { afterCompletion( trCtx, targetOpts.get( INTERCEPTOR));}
                            catch( Throwable th) { logger.warn( "afterCompletion execution is failed", th);}
                        };
                        
                        RequestHandlerException he= e instanceof RequestHandlerException ? (RequestHandlerException)e :
                            new RequestHandlerException( MERGE_FAILED, e.getMessage(), e);
                        he.postProcess= postProcess;
                        he.afterComplete= afterCompletion;
                        throw he;
                    }
                    finally
                    {
                        resources.stream().map( File::new).forEach( fs-> { FileUtil.deleteFile( fs); });
                    }
                }, false);
                break;
            default:
                response( null, ()-> {
                    TransferMessage response= badRequestResponse( request.command(),
                            new RequestHandlerException( TransferResponseCode.BAD_REQUEST,
                                    "request action command ["+ request.uri()+"] is not allowed"));
                    return new HandlerResponse( response);
                }, false);
        }
    }

}
