/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.handler;

import java.util.HashMap;
import java.util.Map;

import io.netty.util.AsciiString;
import io.netty.util.internal.ObjectUtil;


/**
 * @author Jongoh Lee
 *
 */

public class TransferInfo implements Comparable<TransferInfo>
{
    public static final String NOOP_= "/";

    public static final String HEALTH_= "/health";

    public static final String INFO_= "/info";

    public static final String EXIST_= "/exist";

    public static final TransferInfo NOOP= new TransferInfo( NOOP_);

    public static final TransferInfo HEALTH= new TransferInfo( HEALTH_);

    public static final TransferInfo INFO= new TransferInfo( INFO_);

    public static final TransferInfo EXIST= new TransferInfo( EXIST_);

    private static final Map<String, TransferInfo> infoMap;

    private final AsciiString uri;

    static
    {
        infoMap= new HashMap<String, TransferInfo>(){
            private static final long serialVersionUID= -4080044692351274344L;
            {
                put( NOOP_, NOOP);
                put( HEALTH_, HEALTH);
                put( INFO_, INFO);
                put( EXIST_, EXIST);
            }};
    }

    public TransferInfo( String uri)
    {
        uri= ObjectUtil.checkNotNull( uri, "info").trim();
        if( uri.isEmpty())
            throw new IllegalArgumentException( "empty info");

        for( int i= 0; i< uri.length(); i++)
        {
            char c= uri.charAt( i);
            if( Character.isISOControl( c) || Character.isWhitespace( c))
                throw new IllegalArgumentException( "invalid character in info");
        }

        this.uri= AsciiString.cached( uri);
    }

    public static TransferInfo valueOf( String uri)
    {
        TransferInfo ainfo= infoMap.get( uri);
        return ainfo!= null ? ainfo: new TransferInfo( uri);
    }

    public String uri()
    {
        return this.uri.toString();
    }

    public AsciiString asciiInfo()
    {
        return this.uri;
    }

    @Override
    public int hashCode()
    {
        return uri().hashCode();
    }

    @Override
    public boolean equals( Object o)
    {
        if( !( o instanceof TransferInfo))
            return false;

        TransferInfo other= (TransferInfo)o;
        return uri().equals( other.uri());
    }

    @Override
    public String toString()
    {
        return uri.toString();
    }

    @Override
    public int compareTo( TransferInfo o)
    {
        return uri().compareTo( o.uri());
    }

}
