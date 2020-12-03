/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.client;

import static easymaster.transfer.file.util.TransferConstants.AGENT_URL_PREFIX;

import org.springframework.util.StringUtils;

import easymaster.transfer.file.util.OptionParameter;
import easymaster.transfer.file.util.TransferMessageUtil;

/**
 * @author Jongoh Lee
 *
 */

public class TransferRequest
{

    String fromAgent;

    String encodedSourceUri;

    String toAgent;

    String encodedDestinationUri;

    public TransferRequest(){}

    public TransferRequest from( String address, int port)
    {
        fromAgent= address+ ":"+ port;
        return this;
    }

    public TransferRequest from( TransferClient client)
    {
        fromAgent= client.remote.getAddress().getHostAddress()+ ":"+ client.remote.getPort();
        return this;
    }

    public TransferRequest resource( String path, OptionParameter... options) throws Exception
    {
        encodedSourceUri= TransferMessageUtil.encodedUri( path, options);
        return this;
    }

    public TransferRequest to( String address, int port)
    {
        if( StringUtils.hasText( toAgent))
            toAgent+= ";"+ address+ ":"+ port;
        else
            toAgent= address+ ":"+ port;
        return this;
    }

    public TransferRequest path( String path, OptionParameter... options) throws Exception
    {
        encodedDestinationUri= TransferMessageUtil.encodedUri( path, options);
        return this;
    }

    public String transferDestinationAgent()
    {
        return toAgent;
    }

    public String transferDestinationUri()
    {
        return encodedDestinationUri;
    }

    public String transferSourceUri()
    {
        return AGENT_URL_PREFIX +"//"+ fromAgent+ encodedSourceUri;
    }


}
