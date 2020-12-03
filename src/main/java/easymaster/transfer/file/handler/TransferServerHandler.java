/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.handler;

import static easymaster.transfer.file.protocol.TransferCommand.ACTION_;
import static easymaster.transfer.file.protocol.TransferCommand.DELETE_;
import static easymaster.transfer.file.protocol.TransferCommand.GET_;
import static easymaster.transfer.file.protocol.TransferCommand.INFO_;
import static easymaster.transfer.file.protocol.TransferCommand.LIST_;
import static easymaster.transfer.file.protocol.TransferCommand.PUT_;
import static easymaster.transfer.file.protocol.TransferCommand.TRANSFER_;
import static easymaster.transfer.file.protocol.TransferHeaderNames.REASON;
import static easymaster.transfer.file.protocol.TransferHeaderNames.RESPONSE_CODE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_ENCODING;
import static easymaster.transfer.file.protocol.TransferHeaderValues.CHUNKED;
import static easymaster.transfer.file.protocol.TransferResponseCode.BAD_REQUEST;
import static easymaster.transfer.file.protocol.TransferResponseCode.INTERNAL_SERVER_ERROR;
import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.channel.ChannelFutureListener.CLOSE;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import easymaster.transfer.file.config.TransferEnvironment;
import easymaster.transfer.file.interceptors.AgentInterceptor;
import easymaster.transfer.file.interceptors.TransferContext;
import easymaster.transfer.file.protocol.FileData;
import easymaster.transfer.file.protocol.LastTransferContent;
import easymaster.transfer.file.protocol.TransferContent;
import easymaster.transfer.file.protocol.TransferHeaderNames;
import easymaster.transfer.file.protocol.TransferHeaderValues;
import easymaster.transfer.file.protocol.TransferHeaders;
import easymaster.transfer.file.protocol.TransferMessage;
import easymaster.transfer.file.protocol.TransferObject;
import easymaster.transfer.file.protocol.TransferResponseCode;
import easymaster.transfer.file.util.TransferMessageUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.util.internal.ObjectUtil;

/**
 * @author Jongoh Lee
 *
 */

public class TransferServerHandler extends SimpleChannelInboundHandler<TransferObject>
{
    private Logger logger= LoggerFactory.getLogger( TransferServerHandler.class);
    
    private final ApplicationContext applicationContext;
    
    private final TransferEnvironment environment;
    
    private final Map<String, AgentInterceptor> interceptors;
    
    private TransferMessage request;
    
    public TransferServerHandler( ApplicationContext applicationContext, TransferEnvironment environment)
    {
        ObjectUtil.checkNotNull( applicationContext, "applicationContext");
        ObjectUtil.checkNotNull( environment, "environment");
        this.applicationContext= applicationContext;
        this.environment= environment;
        this.interceptors= applicationContext.getBeansOfType( AgentInterceptor.class);
    }
    
    @Override
    public void channelRead0( ChannelHandlerContext context, TransferObject message) throws Exception
    {
//      if( logger.isDebugEnabled())
//      {
//          DecoderResult decoderResult= message.decoderResult();
//          logger.debug( "decoderResult: [{}].", decoderResult.isSuccess());
//          logger.debug( "readed message {}", message.getClass());
//      }
        
        if( message instanceof LastTransferContent)
        {
            logger.debug( "LastTransferContent detected...");
            
            if( request== null)
                return;

            if( request.headers().contains( TRANSFER_ENCODING, CHUNKED, true) && request.content()!= null)
                request.content().addContent( EMPTY_BUFFER, true);
            
            logger.debug( "request command: {}", request.command());
            
            if( !preProcesses( context))
                return;
            
            switch( request.command().name())
            {
                case ACTION_:
                    ActionCommandRequestHandler actHandler= new ActionCommandRequestHandler( context, applicationContext, environment);
                    actHandler.handleCommand( request);
                    break;
                case INFO_:
                    InfoCommandRequestHandler infHandle= new InfoCommandRequestHandler( context, applicationContext, environment);
                    infHandle.handleCommand( request);
                    break;
                case PUT_:
                case DELETE_:
                case GET_:
                case LIST_:
                    ResourceCommandRequestHandler rsHandler= new ResourceCommandRequestHandler( context, applicationContext, environment);
                    rsHandler.handleCommand( request);
                    break;
                case TRANSFER_:
                    TransferCommandRequestHandler trHandler= new TransferCommandRequestHandler( context, applicationContext, environment);
                    trHandler.handleCommand( request);
                    break;
            }
            
            if( !postProcesses( context))
                return;
            if( !afterCompletions( context))
                return;

            if( closeRequired( request.headers()))
                context.writeAndFlush( EMPTY_BUFFER).addListener( CLOSE);
            return;
        }
        
        if( message instanceof TransferMessage)
        {
            request= (TransferMessage)message;
//          logger.debug( "command: [{}], uri: [{}]", request.command(), request.uri());
//          logger.debug( "request headers: [{}]", request.headers());
        }
        else if( message instanceof TransferContent)
        {
            if( request== null)
                throw new RequestHandlerException( BAD_REQUEST, "request message not found before AgentContent");

            TransferContent content= (TransferContent)message;
            ByteBuf buf= content.retain().content();

            if( request.content()== null)
                request.setContent( new FileData( TransferMessageUtil.getContentLength( request, 0), environment));
            request.content().addContent( buf, false);
        }
    }
    
    @Override
    public void exceptionCaught( ChannelHandlerContext ctx, Throwable cause)
    {
        if( cause instanceof PrematureChannelClosureException && request!= null && request.content()!= null)
        {
            logger.info( "uncompleted resource[{}] will be deleted", request.content().getName());
            request.content().delete();
        }
        else
            logger.error( "TransferServerHandler failed.", cause);

        ctx.close();
    }
    
    private boolean preProcesses( ChannelHandlerContext ctx)
    {
        boolean answer= false;
        TransferMessage response= null;
        for( AgentInterceptor interceptor: interceptors.values())
        {
            try
            {
                if( !( answer= interceptor.preProcess( TransferContext.createTransferContext( applicationContext, request.headers()))))
                {
                    response= new TransferMessage( request.command());
                    response.setUri( request.uri());
                    response.headers().add( RESPONSE_CODE, INTERNAL_SERVER_ERROR);
                    response.headers().add( REASON, "requst rejected in interceptor ["+ interceptor.getClass()+ "]");
                    break;
                }
            }
            catch( Exception e)
            {
                answer= false;
                logger.error( "interceptor [{}] execution is failed", interceptor);
                response= new TransferMessage( request.command());
                response.setUri( request.uri());
                response.headers().add( RESPONSE_CODE, INTERNAL_SERVER_ERROR);
                response.headers().add( REASON, e.getMessage());
            }
        }

        if( !answer && response!= null)
        {
            ctx.writeAndFlush( response);
            logger.debug( "requestFailedResponse is sent: {}", response);
        }

        if( closeRequired( request.headers()))
            ctx.writeAndFlush( EMPTY_BUFFER).addListener( CLOSE);

        return answer;
    }

    private boolean postProcesses( ChannelHandlerContext ctx) throws Exception
    {
        boolean answer= false;
        TransferMessage response= null;
        for( AgentInterceptor interceptor: interceptors.values())
        {
            try
            {
                interceptor.postProcess( TransferContext.createTransferContext( applicationContext, request.headers()));
                answer= true;
            }
            catch( Exception e)
            {
                answer= false;
                logger.error( "interceptor [{}] execution is failed", interceptor);
                response= new TransferMessage( request.command());
                response.setUri( request.uri());
                response.headers().add( TransferHeaderNames.RESPONSE_CODE, TransferResponseCode.INTERNAL_SERVER_ERROR);
                response.headers().add( TransferHeaderNames.REASON, e.getMessage());
            }
        }

        if( !answer && response!= null)
        {
            ctx.writeAndFlush( response);
            logger.debug( "requestFailedResponse is sent: {}", response);
        }

        if( closeRequired( request.headers()))
            ctx.writeAndFlush( Unpooled.EMPTY_BUFFER).addListener( ChannelFutureListener.CLOSE);

        return answer;
    }

    private boolean afterCompletions( ChannelHandlerContext ctx) throws Exception
    {
        boolean answer= false;
        TransferMessage response= null;
        for( AgentInterceptor interceptor: interceptors.values())
        {
            try
            {
                interceptor.afterCompletion( TransferContext.createTransferContext( applicationContext, request.headers()));
            }
            catch( Exception e)
            {
                answer= false;
                logger.error( "interceptor [{}] execution is failed", interceptor);
                response= new TransferMessage( request.command());
                response.setUri( request.uri());
                response.headers().add( TransferHeaderNames.RESPONSE_CODE, TransferResponseCode.INTERNAL_SERVER_ERROR);
                response.headers().add( TransferHeaderNames.REASON, e.getMessage());
            }
        }

        if( !answer && response!= null)
        {
            ctx.writeAndFlush( response);
            logger.debug( "requestFailedResponse is sent: {}", response);
        }

        if( closeRequired( request.headers()))
            ctx.writeAndFlush( Unpooled.EMPTY_BUFFER).addListener( ChannelFutureListener.CLOSE);

        return answer;
    }

    private boolean closeRequired( TransferHeaders headers)
    {
        return headers.contains( TransferHeaderNames.CONNECTION, TransferHeaderValues.CLOSE, true);
    }
}
