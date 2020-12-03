/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.config;


import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

/**
 * @author Jongoh Lee
 */

public class TransferEnvironmentPostProcessor 
    implements EnvironmentPostProcessor, Ordered, ApplicationListener<ApplicationEvent>
{
    public static final String APPLICATION_NAME= "transfer-file";
    
    private static final String DEFAULT_PROPERTY_SOURCE_NAME= "defaultProperties";
    
    static final DeferredLog logger= new DeferredLog();
    
    private int order= Ordered.HIGHEST_PRECEDENCE;
    
    @Override
    public int getOrder()
    {
        return this.order;
    }

    @Override
    public void postProcessEnvironment( ConfigurableEnvironment environment, SpringApplication application)
    {
        Map<String, Object> properties= new HashMap<String, Object>();
        
        // default properties 
        if( !StringUtils.hasLength( environment.getProperty( "spring.application.name")))
            properties.put( "spring.application.name", APPLICATION_NAME);

        addOrReplace( environment.getPropertySources(), properties);
    }
    
    private void addOrReplace( MutablePropertySources sources, Map<String, Object> properties)
    {
        MapPropertySource target= null;
        PropertySource<?> source= sources.get( DEFAULT_PROPERTY_SOURCE_NAME);
        if( source!= null && source instanceof MapPropertySource)
        {
            target= (MapPropertySource)source;
            for( String key: properties.keySet())
            {
                if( !target.containsProperty( key))
                    target.getSource().put( key, properties.get( key));
            }
        }
        if( target== null)
            target= new MapPropertySource( DEFAULT_PROPERTY_SOURCE_NAME, properties);
        if( !sources.contains( DEFAULT_PROPERTY_SOURCE_NAME))
            sources.addLast( target);
    }

    @Override
    public void onApplicationEvent( ApplicationEvent event)
    {
        logger.replayTo( TransferEnvironmentPostProcessor.class);
        
    }

}
