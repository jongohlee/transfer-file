/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 
 * @author Jongoh Lee
 *
 */
@SpringBootApplication
public class TransferServer 
{
	public static void main(String[] args) 
	{
	    ConfigurableApplicationContext context= SpringApplication.run( TransferServer.class, args);
	    context.publishEvent( new AgentServerRunning( context));
	    SpringApplication.exit( context, ()-> 0);
	}

	public static class AgentServerRunning extends ApplicationEvent
	{
        private static final long serialVersionUID= 7010766024908978522L;

        public AgentServerRunning( ApplicationContext context)
	    {
	        super( context);
	    }
	}
}
