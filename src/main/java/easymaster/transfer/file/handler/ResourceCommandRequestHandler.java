/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.handler;

import static easymaster.transfer.file.protocol.TransferCommand.DELETE;
import static easymaster.transfer.file.protocol.TransferCommand.DELETE_;
import static easymaster.transfer.file.protocol.TransferCommand.GET;
import static easymaster.transfer.file.protocol.TransferCommand.GET_;
import static easymaster.transfer.file.protocol.TransferCommand.LIST;
import static easymaster.transfer.file.protocol.TransferCommand.LIST_;
import static easymaster.transfer.file.protocol.TransferCommand.PUT;
import static easymaster.transfer.file.protocol.TransferCommand.PUT_;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT_TYPE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.CONTENT_LENGTH;
import static easymaster.transfer.file.protocol.TransferHeaderNames.DELETED_COUNT;
import static easymaster.transfer.file.protocol.TransferHeaderNames.REASON;
import static easymaster.transfer.file.protocol.TransferHeaderNames.REMOTE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.RESPONSE_CODE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.SESSION_ID;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_DESTINATION_URI;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_ENCODING;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_SOURCE_URI;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CHUNKED;
import static easymaster.transfer.file.protocol.TransferResponseCode.ALREADY_EXIST;
import static easymaster.transfer.file.protocol.TransferResponseCode.BAD_REQUEST;
import static easymaster.transfer.file.protocol.TransferResponseCode.DELETE_FAILED;
import static easymaster.transfer.file.protocol.TransferResponseCode.FILE_PERMISSION_ERROR;
import static easymaster.transfer.file.protocol.TransferResponseCode.INTERNAL_SERVER_ERROR;
import static easymaster.transfer.file.protocol.TransferResponseCode.OK;
import static easymaster.transfer.file.protocol.TransferResponseCode.SOURCE_FILE_NOT_FOUND;
import static easymaster.transfer.file.protocol.TransferResponseCode.TRANSFER_FAILED;
import static easymaster.transfer.file.util.OptionParameter.CREATE_ACK;
import static easymaster.transfer.file.util.OptionParameter.DELET_ON_EXIT;
import static easymaster.transfer.file.util.OptionParameter.INTERCEPTOR;
import static easymaster.transfer.file.util.OptionParameter.ON_EXIST;
import static easymaster.transfer.file.util.OptionParameter.SITE;
import static easymaster.transfer.file.util.OptionParameterValues.APPEND_ONEXIST;
import static easymaster.transfer.file.util.OptionParameterValues.FAIL_ONEXIST;
import static easymaster.transfer.file.util.OptionParameterValues.TRUE;
import static easymaster.transfer.file.util.TransferConstants.BIND_ADDRESS;
import static org.springframework.util.ResourceUtils.FILE_URL_PREFIX;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import easymaster.transfer.file.TransferServer;
import easymaster.transfer.file.config.TransferEnvironment;
import easymaster.transfer.file.config.TransferEnvironment.Site;
import easymaster.transfer.file.interceptors.ReceiveInterceptor;
import easymaster.transfer.file.interceptors.TransferContext;
import easymaster.transfer.file.interceptors.TransferInterceptor;
import easymaster.transfer.file.protocol.FileData;
import easymaster.transfer.file.protocol.TransferChunkedContentEncoder;
import easymaster.transfer.file.protocol.TransferMessage;
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

public class ResourceCommandRequestHandler extends AbstractRequestHandler
{
    private Logger logger= LoggerFactory.getLogger( ResourceCommandRequestHandler.class);

    private final ChannelHandlerContext context;

    private final PathMatcher pathMatcher= new AntPathMatcher();

    public ResourceCommandRequestHandler(
            ChannelHandlerContext context, ApplicationContext applicationContext, TransferEnvironment environment)
    {
        super( applicationContext, environment);
        this.context= context;
    }


    @Override
    public void handleCommand( final TransferMessage request)
    {
        ObjectUtil.checkNotNull( request, "request");
        logger.info( "resourceCommand request accepted, from: [{}:{}]",
                request.headers().get( AGENT_TYPE),
                request.headers().get( AGENT));

        switch( request.command().name())
        {
            case PUT_:
                response( context, ()->{ return handlePutCommandRequest( request);}, false);
                break;
            case DELETE_:
                response( context, ()->{ return handleDeleteCommandRequest( request);}, false);
                break;
            case LIST_:
                response( context, ()->{ return handleListCommandRequest( request);}, false);
                break;
            case GET_:
                response( context, ()->{ return handleGetCommandRequest( request);}, false);
                break;
            default:
                response( context, ()-> {
                    TransferMessage response= badRequestResponse( request.command(),
                            new RequestHandlerException( BAD_REQUEST, "request transfer command ["+ request.uri()+"] is not allowed"));
                    return new HandlerResponse( response);
                }, false);
        }
    }

    private HandlerResponse handlePutCommandRequest( final TransferMessage request) throws RequestHandlerException
    {
        TransferMessage response= new TransferMessage( PUT);
        response.setUri( request.uri());
        
        Map<String, List<String>> srcOpts= new LinkedHashMap<String, List<String>>();
        Map<String, List<String>> targetOpts= new LinkedHashMap<String, List<String>>();
        
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
            String siteOp= OptionParameter.first( targetOpts, SITE);
            if( StringUtils.hasText( siteOp) && ( site= environment.getRepository().getSites().get( siteOp))!= null)
                pathBuilder.insert( 0, site.getBaseDir());
            else
                pathBuilder.insert( 0, environment.getRepository().getBaseDir());
            target= pathBuilder.toString();
            logger.debug( "put target path: {}", target);

            if( pathMatcher.isPattern( target))
                throw new RequestHandlerException( BAD_REQUEST, "pattern uri is not allowed");

            synchronized( TransferServer.class)
            {
                File dir= new File( FileUtil.onlyPath( target));
                if( !dir.mkdirs() && !dir.exists())
                    throw new RequestHandlerException( FILE_PERMISSION_ERROR, "Can not create dest dir ["+ dir+ "]");
            }

            File dest= new File( target);
            String onExist= OptionParameter.first( targetOpts, ON_EXIST, FAIL_ONEXIST);
            if( dest.exists() && FAIL_ONEXIST.equalsIgnoreCase( onExist))
                    throw new RequestHandlerException( ALREADY_EXIST, "target file["+ target+ "] is already exist");

            transfer= TransferContext.createTransferContext( applicationContext, request.headers(), new String[] {target});
            preProcess( transfer, targetOpts.get( INTERCEPTOR), request.headers(), site, ReceiveInterceptor.class);

            ResourceSession session= StringUtils.hasText( sessionId) ? ResourceSessionManager.getSession( sessionId) : null;
                    
            if( dest.exists() && APPEND_ONEXIST.equals( onExist))
            {
                try
                {
                    FileUtil.mergeWithLock( Arrays.asList( new String[] { request.content().getFile().getAbsolutePath()}), 
                            target, fs->{ if( session!= null) session.processing( fs);});
                }
                finally
                {
                    request.content().release();
                }
            }
            else
                FileUtil.renameWithLock( request.content(), dest, fs->{ if( session!= null) session.processing( fs);});
            
            if( OptionParameter.contains( targetOpts, DELET_ON_EXIT, TRUE, true))
                dest.deleteOnExit();
            
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

            response.headers().add( REASON, "file content ["+ target+ "] is transferred");

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
                try{ postProcess( trCtx, targetOpts.get( INTERCEPTOR), request.headers(), trSite, ReceiveInterceptor.class, er);}
                catch( Throwable th) { logger.warn( "postProcess execution is failed", th);}
            };
            
            Consumer<Void> afterCompletion= ( Void)->{
                try { afterCompletion( trCtx, targetOpts.get( INTERCEPTOR));}
                catch( Throwable th) { logger.warn( "afterCompletion execution is failed", th);}
            };
            
            RequestHandlerException he= e instanceof RequestHandlerException ? (RequestHandlerException)e :
                new RequestHandlerException( TRANSFER_FAILED, e.getMessage(), e);
            he.postProcess= postProcess;
            he.afterComplete= afterCompletion;
            throw he;
        }
    }

    private HandlerResponse handleDeleteCommandRequest( final TransferMessage request) throws RequestHandlerException
    {
        TransferMessage response= new TransferMessage( DELETE);
        response.setUri( request.uri());
        Map<String, List<String>> options= new LinkedHashMap<String, List<String>>();
        String target= request.headers().get( TRANSFER_SOURCE_URI);
        if( !StringUtils.hasText( target))
            throw new RequestHandlerException( BAD_REQUEST, TRANSFER_SOURCE_URI+ " not found");

        TransferContext transfer= null;
        Site site= null;
        try
        {
            target= TransferMessageUtil.decodeUri( target, options);
            StringBuilder pathBuilder= new StringBuilder( target);
            String siteOp= OptionParameter.first( options, SITE);
            if( StringUtils.hasText( siteOp) && ( site= environment.getRepository().getSites().get( siteOp))!= null)
                pathBuilder.insert( 0, site.getBaseDir());
            else
                pathBuilder.insert( 0, environment.getRepository().getBaseDir());
            target= pathBuilder.toString();
            logger.debug( "delete target path: {}", target);

            transfer= TransferContext.createTransferContext( applicationContext, request.headers(), new String[] {target});

            preProcess( transfer, options.get( INTERCEPTOR), request.headers(), site, ReceiveInterceptor.class);

            int deleted= 0;
            Resource[] resources= applicationContext.getResources( FILE_URL_PREFIX+ target);
            if( resources!= null && resources.length> 0)
            {
                for( int i= 0; i< resources.length; i++)
                {
                    File toDelete= resources[i].getFile();
                    FileUtil.deleteFile( toDelete);
                    deleted++;
                    response.headers().add( REASON, toDelete.getAbsolutePath());
                    logger.debug( "rsources: {} is deleted", toDelete);
                }
            }
            
            final TransferContext trCtx= transfer;
            final Site trSite= site;
            Consumer<Void> postProcess= ( Void)->{
                try{ postProcess( trCtx, options.get( INTERCEPTOR), request.headers(), trSite, ReceiveInterceptor.class, null);}
                catch( Throwable th) { logger.warn( "postProcess execution is failed", th);}
            };
            
            Consumer<Void> afterCompletion= ( Void)->{
                try { afterCompletion( trCtx, options.get( INTERCEPTOR));}
                catch( Throwable th) { logger.warn( "afterCompletion execution is failed", th);}
            };
            
            response.headers().add( DELETED_COUNT, deleted);
            return new HandlerResponse( response, postProcess, afterCompletion);
        }
        catch( Exception e)
        {
            final TransferContext trCtx= transfer;
            final Site trSite= site;
            Consumer<Exception> postProcess= ( er)->{
                try
                {
                    postProcess( trCtx, options.get( INTERCEPTOR), request.headers(), trSite, ReceiveInterceptor.class, er);
                }
                catch( Throwable th) { logger.warn( "postProcess execution is failed", th);}
            };
            
            Consumer<Void> afterCompletion= ( Void)->{
                try { afterCompletion( trCtx, options.get( INTERCEPTOR));}
                catch( Throwable th) { logger.warn( "afterCompletion execution is failed", th);}
            };
            
            RequestHandlerException he= e instanceof RequestHandlerException ? (RequestHandlerException)e :
                new RequestHandlerException( DELETE_FAILED, e.getMessage(), e);
            he.postProcess= postProcess;
            he.afterComplete= afterCompletion;
            throw he;
        }
    }
    
    private HandlerResponse handleListCommandRequest( final TransferMessage request) throws RequestHandlerException
    {
        TransferMessage response= new TransferMessage( LIST);
        response.setUri( request.uri());
        Map<String, List<String>> options= new LinkedHashMap<String, List<String>>();
        String target= request.headers().get( TRANSFER_SOURCE_URI);
        if( !StringUtils.hasText( target))
            throw new RequestHandlerException( BAD_REQUEST, TRANSFER_SOURCE_URI+ " not found");

        try
        {
            target= TransferMessageUtil.decodeUri( target, options);
            StringBuilder pathBuilder= new StringBuilder( target);
            Site site= null;
            String siteOp= OptionParameter.first( options, SITE);
            if( StringUtils.hasText( siteOp) && ( site= environment.getRepository().getSites().get( siteOp))!= null)
                pathBuilder.insert( 0, site.getBaseDir());
            else
                pathBuilder.insert( 0, environment.getRepository().getBaseDir());
            target= pathBuilder.toString();
            logger.debug( "list target path: {}", target);
            
            Resource[] resources= applicationContext.getResources( FILE_URL_PREFIX+ target);
            if( resources!= null && resources.length> 0)
            {
                for( int i= 0; i< resources.length; i++)
                {
                    File res= resources[i].getFile();
                    if( res.exists() && res.isFile())
                    {
                        response.headers().add( REASON, res.getAbsolutePath());
                        logger.debug( "rsources: {} is added", res);
                    }
                }
            }
            return new HandlerResponse( response);
        }
        catch( Exception e)
        {
            RequestHandlerException he= e instanceof RequestHandlerException ? (RequestHandlerException)e :
                new RequestHandlerException( INTERNAL_SERVER_ERROR, e.getMessage(), e);
            throw he;
        }
    }

    private HandlerResponse handleGetCommandRequest( final TransferMessage request) throws RequestHandlerException
    {
        TransferMessage response= new TransferMessage( GET);
        response.setUri( request.uri());
        Map<String, List<String>> options= new LinkedHashMap<String, List<String>>();
        String source= request.headers().get( TRANSFER_SOURCE_URI);
        if( !StringUtils.hasText( source))
            throw new RequestHandlerException( BAD_REQUEST, TRANSFER_SOURCE_URI+ " not found");

        TransferContext transfer= null;
        Site site= null;
        try
        {
            source= TransferMessageUtil.decodeUri( source, options);
            String remote= request.headers().get( REMOTE);
            remote= StringUtils.hasText( remote) ? remote : environment.getCustom().get( BIND_ADDRESS);
            String sourceUri= TransferMessageUtil.encodedUri( remote, environment.getTcpPort(), source, new OptionParameter[] {});
            response.headers().add( TRANSFER_SOURCE_URI, sourceUri);

            StringBuilder pathBuilder= new StringBuilder( source);
            String siteOp= OptionParameter.first( options, SITE);
            if( StringUtils.hasText( siteOp) && ( site= environment.getRepository().getSites().get( siteOp))!= null)
                pathBuilder.insert( 0, site.getBaseDir());
            else
                pathBuilder.insert( 0, environment.getRepository().getBaseDir());
            source= pathBuilder.toString();
            logger.debug( "get source path: {}", source);

            if( pathMatcher.isPattern( source))
                throw new RequestHandlerException( BAD_REQUEST, "pattern uri is not allowed");

            File fs= ResourceUtils.getFile( source);
            if( !fs.exists())
                throw new RequestHandlerException( SOURCE_FILE_NOT_FOUND, fs.getAbsolutePath()+ " is not found");

            transfer= TransferContext.createTransferContext( applicationContext, request.headers(), new String[] {source});

            preProcess( transfer, options.get( INTERCEPTOR), request.headers(), site, TransferInterceptor.class);

            long contentLength= fs.length();
            response.headers().add( CONTENT_LENGTH, contentLength);
            response.headers().add( REASON, "file content ["+ source+ "] is transferred");
            FileData fdata= new FileData( contentLength, environment);
            fdata.setContent( fs);

            if( contentLength< environment.getChunkSize())
                response.setContent( fdata);
            else
            {
                response.headers().add( RESPONSE_CODE, OK);
                response.headers().add( TRANSFER_ENCODING, CHUNKED);
                context.writeAndFlush( response);
                TransferChunkedContentEncoder chunk= new TransferChunkedContentEncoder( fdata, environment.getChunkSize());
//                context.writeAndFlush( chunk).get();
                context.writeAndFlush( chunk);
                response= null;
            }

            final TransferContext trCtx= transfer;
            final Site trSite= site;
            Consumer<Void> postProcess= ( Void)->{
                try{ postProcess( trCtx, options.get( INTERCEPTOR), request.headers(), trSite, TransferInterceptor.class, null);}
                catch( Throwable th) { logger.warn( "postProcess execution is failed", th);}
            };
            
            Consumer<Void> afterCompletion= ( Void)->{
                try { afterCompletion( trCtx, options.get( INTERCEPTOR));}
                catch( Throwable th) { logger.warn( "afterCompletion execution is failed", th);}
            };
            
            return new HandlerResponse( response, postProcess, afterCompletion);
        }
        catch( Exception e)
        {
            final TransferContext trCtx= transfer;
            final Site trSite= site;
            Consumer<Exception> postProcess= ( er)->{
                try{ postProcess( trCtx, options.get( INTERCEPTOR), request.headers(), trSite, TransferInterceptor.class, er);}
                catch( Throwable th) { logger.warn( "postProcess execution is failed", th);}
            };
            
            Consumer<Void> afterCompletion= ( Void)->{
                try { afterCompletion( trCtx, options.get( INTERCEPTOR));}
                catch( Throwable th) { logger.warn( "afterCompletion execution is failed", th);}
            };
            
            RequestHandlerException he= e instanceof RequestHandlerException ? (RequestHandlerException)e :
                new RequestHandlerException( TRANSFER_FAILED, e.getMessage(), e);
            he.postProcess= postProcess;
            he.afterComplete= afterCompletion;
            throw he;
        }
    }
    
}
