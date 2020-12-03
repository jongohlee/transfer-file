/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.protocol;

import static io.netty.util.internal.MathUtil.findNextPositivePowerOfTwo;

import io.netty.util.AsciiString;
import io.netty.util.internal.ObjectUtil;

/**
 * @author Jongoh Lee
 *
 */

public class TransferCommand implements Comparable<TransferCommand>
{
    /**
     * TRANSFER | PUT | GET | LIST | INFO /health | ACTION /shutdown
     */

    public static final String TRANSFER_= "TRANSFER";

    public static final String PUT_= "PUT";

    public static final String DELETE_= "DELETE";

    public static final String GET_= "GET";

    public static final String LIST_= "LIST";

    public static final String INFO_= "INFO";

    public static final String ACTION_= "ACTION";

    public static final TransferCommand TRANSFER= new TransferCommand( TRANSFER_);

    public static final TransferCommand PUT= new TransferCommand( PUT_);

    public static final TransferCommand DELETE= new TransferCommand( DELETE_);

    public static final TransferCommand GET= new TransferCommand( GET_);

    public static final TransferCommand LIST= new TransferCommand( LIST_);

    public static final TransferCommand INFO= new TransferCommand( INFO_);

    public static final TransferCommand ACTION= new TransferCommand( ACTION_);

    private static final EnumNameMap<TransferCommand> commandMap;

    private final AsciiString name;

    static
    {
        commandMap= new EnumNameMap<TransferCommand>( 
                new EnumNameMap.Node<TransferCommand>( TRANSFER.toString(), TRANSFER),
                new EnumNameMap.Node<TransferCommand>( PUT.toString(), PUT),
                new EnumNameMap.Node<TransferCommand>( DELETE.toString(), DELETE),
                new EnumNameMap.Node<TransferCommand>( GET.toString(), GET),
                new EnumNameMap.Node<TransferCommand>( LIST.toString(), LIST),
                new EnumNameMap.Node<TransferCommand>( INFO.toString(), INFO),
                new EnumNameMap.Node<TransferCommand>( ACTION.toString(), ACTION));
    }

    public TransferCommand( String name)
    {
        name= ObjectUtil.checkNotNull( name, "name").trim();
        if( name.isEmpty())
            throw new IllegalArgumentException( "empty name");

        for( int i= 0; i< name.length(); i++)
        {
            char c= name.charAt( i);
            if( Character.isISOControl( c)|| Character.isWhitespace( c))
                throw new IllegalArgumentException( "invalid character in name");
        }

        this.name= AsciiString.cached( name);
    }

    public static TransferCommand valueOf( String name)
    {
        TransferCommand command= commandMap.get( name);
        return command!= null ? command : new TransferCommand( name);
    }

    public String name()
    {
        return this.name.toString();
    }

    public AsciiString asciiName()
    {
        return this.name;
    }

    @Override
    public int hashCode()
    {
        return name().hashCode();
    }

    @Override
    public boolean equals( Object o)
    {
        if( !( o instanceof TransferCommand))
            return false;

        TransferCommand other= (TransferCommand)o;
        return name().equals( other.name());
    }

    @Override
    public String toString()
    {
        return name.toString();
    }

    @Override
    public int compareTo( TransferCommand o)
    {
        return name().compareTo( o.name());
    }

    private static final class EnumNameMap<T>
    {
        private final EnumNameMap.Node<T>[] values;
        private final int valuesMask;

        @SafeVarargs
        @SuppressWarnings( "unchecked")
        EnumNameMap( EnumNameMap.Node<T>... nodes)
        {
            values= new EnumNameMap.Node[findNextPositivePowerOfTwo( nodes.length)];
            valuesMask= values.length- 1;
            for( EnumNameMap.Node<T> node: nodes)
            {
                int i= hashCode( node.key) & valuesMask;
                if( values[i]!= null)
                    throw new IllegalArgumentException(
                            "index "+ i+ " collision between values: ["+ values[i].key+ ", "+ node.key+ ']');
                values[i]= node;
            }
        }

        T get( String name)
        {
            EnumNameMap.Node<T> node= values[hashCode( name)& valuesMask];
            return node== null || !node.key.equals( name) ? null : node.value;
        }

        private static int hashCode( String name)
        {
            // HashSet / HashMap등의 자료구조에서 TransferCommand를 찾을 때,
            // 발생할 수 있는 Command의 종류가 위와 같이 명백한 경우
            // hasCode비교가 Fast-Fail되도록 한 후 String equals로 넘어가도록 한다.
            return name.hashCode()>>> 8;
        }

        private static final class Node<T>
        {
            final String key;
            final T value;

            Node( String key, T value)
            {
                this.key= key;
                this.value= value;
            }
        }
    }
}
