/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.handler;

import static easymaster.transfer.file.handler.TransferInfo.EXIST_;
import static easymaster.transfer.file.handler.TransferInfo.HEALTH_;
import static easymaster.transfer.file.handler.TransferInfo.INFO_;
import static easymaster.transfer.file.handler.TransferInfo.NOOP_;
import static easymaster.transfer.file.protocol.TransferCommand.INFO;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT;
import static easymaster.transfer.file.protocol.TransferHeaderNames.AGENT_TYPE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.REASON;
import static easymaster.transfer.file.protocol.TransferHeaderNames.RESOURCE_LENGTH;
import static easymaster.transfer.file.protocol.TransferHeaderNames.RESPONSE_CODE;
import static easymaster.transfer.file.protocol.TransferHeaderNames.TRANSFER_SOURCE_URI;
import static easymaster.transfer.file.protocol.TransferResponseCode.BAD_REQUEST;
import static easymaster.transfer.file.protocol.TransferResponseCode.NOT_EXIST;
import static easymaster.transfer.file.util.OptionParameter.SITE;
import static easymaster.transfer.file.util.TransferConstants.AGENT_HEALTHINDICATOR;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.util.StringUtils;

import easymaster.transfer.file.config.TransferEnvironment;
import easymaster.transfer.file.config.TransferEnvironment.Site;
import easymaster.transfer.file.protocol.TransferHeaders;
import easymaster.transfer.file.protocol.TransferMessage;
import easymaster.transfer.file.util.OptionParameter;
import easymaster.transfer.file.util.TransferMessageUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.ObjectUtil;

/**
 * @author Jongoh Lee
 *
 */

public class InfoCommandRequestHandler extends AbstractRequestHandler
{
    private final ChannelHandlerContext context;

    public InfoCommandRequestHandler(
            ChannelHandlerContext context, ApplicationContext applicationContext, TransferEnvironment environment)
    {
        super( applicationContext, environment);
        this.context= context;
    }

    @Override
    public void handleCommand( final TransferMessage request)
    {
        ObjectUtil.checkNotNull( request, "request");
        logger.info( "infoCommand request is accepted. from: [{}:{}]",
                request.headers().get( AGENT_TYPE),
                request.headers().get( AGENT));
        TransferInfo info= TransferInfo.valueOf( request.uri());

        switch( info.uri())
        {
            case HEALTH_:
                HealthIndicator indicator= applicationContext.getBean( AGENT_HEALTHINDICATOR, HealthIndicator.class);
                response( context, ()-> {
                    TransferMessage response= new TransferMessage( INFO);
                    response.setUri( request.uri());
                    response.headers().add( REASON, indicator.health().getStatus().toString());
                    return new HandlerResponse( response);
                }, false);
                break;
            case INFO_:
                response( context, ()->{
                    TransferMessage response= new TransferMessage( INFO);
                    response.setUri( request.uri());
                    ConfigurableEnvironment environment= (ConfigurableEnvironment)applicationContext.getEnvironment();
                    MutablePropertySources mps= environment.getPropertySources();
                    mps.forEach( ps-> {
                        if( ps instanceof MapPropertySource)
                        {
                            for( String pname: ( (MapPropertySource)ps).getPropertyNames())
                            {
                                if( !TransferMessageUtil.isExposableProperty( pname))
                                    continue;
                                if( response.headers().contains( pname))
                                    continue;

                                try
                                {
                                    TransferHeaders.nameValidator( true).validateName( pname);
                                    Object pv= TransferHeaders.valueConverter( true).convertObject( environment.getProperty( pname));
                                    response.headers().add( pname, pv);
                                }
                                catch( Exception e) { /* ignore it */}
                            }
                        }
                    });
                    return new HandlerResponse( response);
                }, false);
                break;
            case EXIST_:
                response( context, ()->{
                    TransferMessage response= new TransferMessage( INFO);
                    response.setUri( request.uri());

                    String location= request.headers().get( TRANSFER_SOURCE_URI);
                    Map<String, List<String>> options= new LinkedHashMap<String, List<String>>();
                    try
                    {
                        location= TransferMessageUtil.decodeUri( location, options);
                    }
                    catch( Exception e)
                    {
                        throw new RequestHandlerException( BAD_REQUEST, e.getMessage(), e);
                    }

                    StringBuilder pathBuilder= new StringBuilder( location);
                    String siteOp= OptionParameter.first( options, SITE);
                    Site site= null;
                    if(  StringUtils.hasText( siteOp) && ( site= environment.getRepository().getSites().get( siteOp))!= null)
                        pathBuilder.insert( 0, site.getBaseDir());
                    else
                        pathBuilder.insert( 0, environment.getRepository().getBaseDir());
                    location= pathBuilder.toString();

                    logger.debug( "exist check file path: {}", location);

                    File dest= new File( location);
                    if( dest.exists())
                    {
                        response.headers().add( REASON, "EXIST");
                        response.headers().add( RESOURCE_LENGTH, dest.length());
                    }
                    else
                    {
                        response.headers().add( RESPONSE_CODE, NOT_EXIST);
                        response.headers().add( REASON, "NOT EXIST");
                    }
                    return new HandlerResponse( response);
                }, false);
                break;
            case NOOP_:
                break;
            default:
                response( context, ()-> {
                    TransferMessage response= badRequestResponse( request.command(),
                            new RequestHandlerException( BAD_REQUEST, "request info command ["+ request.uri()+"] is not allowed"));
                    return new HandlerResponse( response);
                }, false);
        }
    }
}
