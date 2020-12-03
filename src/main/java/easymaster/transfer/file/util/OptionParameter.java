/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.util;

import java.util.List;
import java.util.Map;

import io.netty.util.internal.ObjectUtil;


/**
 * @author Jongoh Lee
 *
 */

public class OptionParameter
{
    public static final String SITE= "site";

    public static final String CREATE_ACK= "createAck";

    public static final String AFTER_TRANSFER= "afterTransfer";

    public static final String INTERCEPTOR= "interceptor";

    public static final String DELET_ON_EXIT= "deleteOnExit";

    public static final String ON_EXIST= "onExist";
    
    private final String name;

    private final String value;

    private OptionParameter( String name, String value)
    {
        this.name= name;
        this.value= value;
    }

    public static OptionParameter param( String name, String value)
    {
        return new OptionParameter( name, value);
    }

    public static boolean contains( Map<String, List<String>> options, String name)
    {
        ObjectUtil.checkNotNull( options, "options");
        ObjectUtil.checkNonEmpty( options.keySet(), "options");
        ObjectUtil.checkNotNull( name, "name");
        return options.containsKey( name);
    }

    public static boolean contains( Map<String, List<String>> options, String name,
            final String value, final boolean ignoreCase)
    {
        ObjectUtil.checkNotNull( options, "options");
        ObjectUtil.checkNotNull( name, "name");
        ObjectUtil.checkNotNull( value, "value");
        List<String> opts= null;
        if( !options.isEmpty() && ( opts= options.get( name))!= null && !opts.isEmpty())
            return opts.stream().filter(  v->{
                return ignoreCase ? v.equalsIgnoreCase( value) : v.equalsIgnoreCase( value);}).count()> 0;

        return false;
    }

    public static String first( Map<String, List<String>> options, String name)
    {
        ObjectUtil.checkNotNull( options, "options");
        ObjectUtil.checkNotNull( name, "name");
        List<String> opts= null;
        if( !options.isEmpty() && ( opts= options.get( name))!= null && !opts.isEmpty())
            return opts.get( 0);

        return null;
    }
    
    public static String first( Map<String, List<String>> options, String name, String defaultValue)
    {
        ObjectUtil.checkNotNull( options, "options");
        ObjectUtil.checkNotNull( name, "name");
        ObjectUtil.checkNotNull( defaultValue, "defalutValue");
        List<String> opts= null;
        if( !options.isEmpty() && ( opts= options.get( name))!= null && !opts.isEmpty())
            return opts.get( 0);

        return defaultValue;
    }

    public String name()
    {
        return this.name;
    }

    public String value()
    {
        return this.value;
    }
}
