/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.handler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import easymaster.transfer.file.config.TransferEnvironment;
import easymaster.transfer.file.config.TransferEnvironment.Site;
import easymaster.transfer.file.interceptors.Interceptor;
import easymaster.transfer.file.interceptors.ReceiveInterceptor;
import easymaster.transfer.file.interceptors.TransferContext;
import easymaster.transfer.file.interceptors.TransferInterceptor;
import easymaster.transfer.file.protocol.TransferCommand;
import easymaster.transfer.file.protocol.TransferHeaderNames;
import easymaster.transfer.file.protocol.TransferHeaderValues;
import easymaster.transfer.file.protocol.TransferHeaders;
import easymaster.transfer.file.protocol.TransferMessage;
import easymaster.transfer.file.protocol.TransferResponseCode;
import easymaster.transfer.file.util.TransferMessageUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.ObjectUtil;

/**
 * @author Jongoh Lee
 *
 */

public abstract class AbstractRequestHandler
{
    protected Logger logger= LoggerFactory.getLogger( AbstractRequestHandler.class);

    protected final ApplicationContext applicationContext;

    protected  final TransferEnvironment environment;

    protected AbstractRequestHandler( ApplicationContext context, TransferEnvironment environment)
    {
        ObjectUtil.checkNotNull( context, "contxt");
        ObjectUtil.checkNotNull( environment, "environment");
        this.applicationContext= context;
        this.environment= environment;
    }

    protected abstract void handleCommand( final TransferMessage request);

    protected void response( ChannelHandlerContext ctx, RequestHandler<HandlerResponse> handler, boolean closeRequired)
    {
        ObjectUtil.checkNotNull( handler, "handler");
        HandlerResponse answer= null;
        try
        {
            answer= handler.handle();
            if( answer.response!= null)
                requestCompletedResponse( ctx, answer.response, closeRequired).get();
            if( answer.postProcess!= null)
                answer.postProcess.accept( null);
        }
        catch( RequestHandlerException he)
        {
            logger.error( "handle request is failed. RequestHandlerException", he);
            requestFailedResponse( ctx, he.getResponseCode(), he, true);
            if( he.postProcess!= null)
                he.postProcess.accept( he);
        }
        catch( Exception e)
        {
            logger.error( "handle request is failed. Exception", e);
            requestFailedResponse( ctx, TransferResponseCode.INTERNAL_SERVER_ERROR, e, true);
        }
        finally
        {
            if( answer!= null && answer.afterComplete!= null)
                answer.afterComplete.accept( null);
        }
    }
    
    protected TransferMessage badRequestResponse( TransferCommand command, Exception cause)
    {
        ObjectUtil.checkNotNull( command, "command");
        TransferMessage response= new TransferMessage( command);
        response.headers().add( TransferHeaderNames.RESPONSE_CODE, TransferResponseCode.BAD_REQUEST);

        if( cause!= null && StringUtils.hasText( cause.getMessage()))
            response.headers().add( TransferHeaderNames.REASON, cause.getMessage());

        logger.debug( "badRequestResponse created: {}", response);

        return response;
    }

    protected void preProcess( TransferContext tranCtx, List<String> interceptors, TransferHeaders headers,
            Site site, Class< ? extends Interceptor> type) throws Exception
    {
        if( CollectionUtils.isEmpty( interceptors))
            return;

        for( String interceptor: interceptors)
        {
            if( ReceiveInterceptor.class.isAssignableFrom( type))
            {
                if( !( ( ReceiveInterceptor)applicationContext.getBean( interceptor, type)).preReceive( tranCtx))
                    throw new RequestHandlerException(
                            TransferResponseCode.INTERNAL_SERVER_ERROR, "preProcessor execution result is false");
            }
            else if( TransferInterceptor.class.isAssignableFrom( type))
            {
                if( !( ( TransferInterceptor)applicationContext.getBean( interceptor, type)).preTransfer( tranCtx))
                    throw new RequestHandlerException(
                            TransferResponseCode.INTERNAL_SERVER_ERROR, "preProcessor execution result is false");
            }
            else
                throw new IllegalArgumentException( "invalid interceptor type");
        }
    }

    protected void postProcess( TransferContext tranCtx, List<String> interceptors, TransferHeaders headers,
            Site site, Class< ? extends Interceptor> type, Exception cause) throws Exception
    {
        if( CollectionUtils.isEmpty( interceptors))
            return;

        for( String interceptor: interceptors)
        {
            if( ReceiveInterceptor.class.isAssignableFrom( type))
                ( ( ReceiveInterceptor)applicationContext.getBean( interceptor, type)).postReceive( tranCtx, cause);
            else if( TransferInterceptor.class.isAssignableFrom( type))
                ( ( TransferInterceptor)applicationContext.getBean( interceptor, type)).postTransfer( tranCtx, cause);
            else
                throw new IllegalArgumentException( "invalid interceptor type");
        }
    }

    protected void afterCompletion( TransferContext tranCtx, List<String> interceptors) throws Exception
    {
        if( CollectionUtils.isEmpty( interceptors))
            return;
        
        for( String interceptor: interceptors)
        {
            Interceptor bean= applicationContext.getBean( interceptor, Interceptor.class);
            bean.afterCompletion( tranCtx);
        }
    }

    private ChannelFuture requestCompletedResponse( ChannelHandlerContext ctx, TransferMessage response, boolean closeRequired)
    {
        ObjectUtil.checkNotNull( ctx, "ctx");
        ObjectUtil.checkNotNull( response, "response");

        if( !response.headers().contains( TransferHeaderNames.RESPONSE_CODE))
            response.headers().add( TransferHeaderNames.RESPONSE_CODE, TransferResponseCode.OK);
        if( closeRequired)
            response.headers().add( TransferHeaderNames.CONNECTION, TransferHeaderValues.CLOSE);

        logger.debug( "requestCompletedResponse is sent: {}", response);
        return ctx.writeAndFlush( response);
    }

    private ChannelFuture requestFailedResponse(
            ChannelHandlerContext ctx, TransferResponseCode rsCode, Exception cause, boolean closeRequired)
    {
        ObjectUtil.checkNotNull( ctx, "ctx");
        ObjectUtil.checkNotNull( rsCode, "responseCode");

        TransferMessage response= new TransferMessage( TransferCommand.INFO);
        response.headers().add( TransferHeaderNames.RESPONSE_CODE, rsCode);

        if( cause!= null && StringUtils.hasText( cause.getMessage()))
            response.headers().add( TransferHeaderNames.REASON, TransferMessageUtil.validateHeaderValue( cause.getMessage()));

        if( closeRequired)
            response.headers().add( TransferHeaderNames.CONNECTION, TransferHeaderValues.CLOSE);

        logger.debug( "requestFailedResponse is sent: {}", response);
        return ctx.writeAndFlush( response);
    }
}
