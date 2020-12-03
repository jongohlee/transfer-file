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

public class TransferAction implements Comparable<TransferAction>
{
    public static final String NOOP_= "/";

    public static final String SESSION_= "/session";

    public static final String MERGE_= "/merge";

    public static final String SHUTDOWN_= "/shutdown";

    public static final TransferAction NOOP= new TransferAction( NOOP_);

    public static final TransferAction MERGE= new TransferAction( MERGE_);

    public static final TransferAction SESSION= new TransferAction( SESSION_);

    public static final TransferAction SHUTDOWN= new TransferAction( SHUTDOWN_);

    private static final Map<String, TransferAction> actionMap;

    private final AsciiString uri;

    static
    {
        actionMap= new HashMap<String, TransferAction>(){
            private static final long serialVersionUID= -842750548586315765L;
            {
                put( NOOP_, NOOP);
                put( SESSION_, SESSION);
                put( MERGE_, MERGE);
                put( SHUTDOWN_, SHUTDOWN);
            }};
    }

    public TransferAction( String uri)
    {
        uri= ObjectUtil.checkNotNull( uri, "uri").trim();
        if( uri.isEmpty())
            throw new IllegalArgumentException( "empty uri");

        for( int i= 0; i< uri.length(); i++)
        {
            char c= uri.charAt( i);
            if( Character.isISOControl( c) || Character.isWhitespace( c))
                throw new IllegalArgumentException( "invalid character in uri");
        }

        this.uri= AsciiString.cached( uri);
    }

    public static TransferAction valueOf( String uri)
    {
        TransferAction action= actionMap.get( uri);
        return action!= null ? action : new TransferAction( uri);
    }

    public String uri()
    {
        return this.uri.toString();
    }

    public AsciiString asciiUri()
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
        if( !( o instanceof TransferAction))
            return false;

        TransferAction other= (TransferAction)o;
        return uri().equals( other.uri());
    }

    @Override
    public String toString()
    {
        return uri.toString();
    }

    @Override
    public int compareTo( TransferAction o)
    {
        return uri().compareTo( o.uri());
    }
}
