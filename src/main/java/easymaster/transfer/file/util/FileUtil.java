/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.util;

import static java.io.File.separatorChar;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;

import easymaster.transfer.file.protocol.FileData;
import io.netty.util.internal.ObjectUtil;

/**
 * @author Jongoh Lee
 */

public class FileUtil
{

    public static final int BUFFER_SIZE= 128* 1024;

    public static final String PARALLEL_SPLIT_SUFFIX_FORMAT= ".split%d";
    
    public static final String PARALLEL_SPLIT_SUFFIX= "split";

    private static final Logger logger= LoggerFactory.getLogger( FileUtil.class);

    /**
     * The System property key for the user directory.
     */
    private static final String USER_DIR_KEY= "user.dir";
    private static final File USER_DIR= new File( System.getProperty( USER_DIR_KEY));
    private static boolean windowsOs= initWindowsOs();

    private FileUtil(){}

    private static boolean initWindowsOs()
    {
        // initialize once as System.getProperty is not fast
        String osName= System.getProperty( "os.name").toLowerCase( Locale.ENGLISH);
        return osName.contains( "windows");
    }

    public static File getUserDir()
    {
        return USER_DIR;
    }

    public static boolean isAbsolutePath( String root)
    {
        return root.contains( ":") || root.startsWith( "/");
    }

    /**
     * Normalizes the path to cater for Windows and other platforms
     */
    public static String normalizePath( String path)
    {
        if( path== null)
            return null;

        if( isWindows())
        {
            // special handling for Windows where we need to convert / to \\
            return path.replace( '/', '\\');
        }
        else
        {
            // for other systems make sure we use / as separators
            return path.replace( '\\', '/');
        }
    }

    /**
     * Returns true, if the OS is windows
     */
    public static boolean isWindows()
    {
        return windowsOs;
    }

    public static File createTempFile( String prefix, String suffix, File parentDir) throws IOException
    {
        ObjectUtil.checkNotNull( parentDir, "parentDir");

        if( suffix== null)
            suffix= ".tmp";
        if( prefix== null)
            prefix= ".agent";
        else if( prefix.length()< 3)
            prefix= prefix+ "agent";

        // create parent folder
        parentDir.mkdirs();

        return File.createTempFile( prefix, suffix, parentDir);
    }

    public static String stripLeadingSeparator( String name)
    {
        if( name== null)
            return null;
        while( name.startsWith( "/") || name.startsWith( File.separator))
            name= name.substring( 1);
        return name;
    }

    public static boolean hasLeadingSeparator( String name)
    {
        if( name== null)
            return false;
        if( name.startsWith( "/")|| name.startsWith( File.separator))
            return true;
        return false;
    }

    public static String stripFirstLeadingSeparator( String name)
    {
        if( name== null)
            return null;
        if( name.startsWith( "/") || name.startsWith( File.separator))
            name= name.substring( 1);
        return name;
    }

    public static String stripTrailingSeparator( String name)
    {
        if( !StringUtils.hasLength( name))
            return name;

        String s= name;

        // there must be some leading text, as we should only remove trailing separators
        while( s.endsWith( "/") || s.endsWith( File.separator))
            s= s.substring( 0, s.length()- 1);

        // if the string is empty, that means there was only trailing slashes, and no
        // leading text
        // and so we should then return the original name as is
        if( !StringUtils.hasLength( s))
            return name;
        else
        {
            // return without trailing slashes
            return s;
        }
    }

    public static String stripPath( String name)
    {
        if( name== null)
            return null;
        int posUnix= name.lastIndexOf( '/');
        int posWin= name.lastIndexOf( '\\');
        int pos= Math.max( posUnix, posWin);

        if( pos!= -1)
            return name.substring( pos+ 1);
        return name;
    }

    public static String stripExt( String name)
    {
        return stripExt( name, false);
    }

    public static String stripExt( String name, boolean singleMode)
    {
        if( name== null)
            return null;

        // the name may have a leading path
        int posUnix= name.lastIndexOf( '/');
        int posWin= name.lastIndexOf( '\\');
        int pos= Math.max( posUnix, posWin);

        if( pos> 0)
        {
            String onlyName= name.substring( pos+ 1);
            int pos2= singleMode ? onlyName.lastIndexOf( '.') : onlyName.indexOf( '.');
            if( pos2> 0)
                return name.substring( 0, pos+ pos2+ 1);
        }
        else
        {
            // if single ext mode, then only return last extension
            int pos2= singleMode ? name.lastIndexOf( '.') : name.indexOf( '.');
            if( pos2> 0)
                return name.substring( 0, pos2);
        }

        return name;
    }

    public static String onlyExt( String name)
    {
        return onlyExt( name, false);
    }

    public static String onlyExt( String name, boolean singleMode)
    {
        if( name== null)
            return null;
        name= stripPath( name);

        // extension is the first dot, as a file may have double extension such as
        // .tar.gz
        // if single ext mode, then only return last extension
        int pos= singleMode ? name.lastIndexOf( '.') : name.indexOf( '.');
        if( pos!= -1)
            return name.substring( pos+ 1);
        return null;
    }

    public static String onlyPath( String name)
    {
        if( name== null)
            return null;

        int posUnix= name.lastIndexOf( '/');
        int posWin= name.lastIndexOf( '\\');
        int pos= Math.max( posUnix, posWin);

        if( pos> 0)
            return name.substring( 0, pos);
        else if( pos== 0)
        {
            // name is in the root path, so extract the path as the first char
            return name.substring( 0, 1);
        }
        // no path in name
        return null;
    }

    public static String compactPath( String path)
    {
        return compactPath( path, ""+ separatorChar);
    }

    public static String compactPath( String path, char separator)
    {
        return compactPath( path, ""+ separator);
    }

    public static String compactPath( String path, String separator)
    {
        if( path== null)
            return null;

        // only normalize if contains a path separator
        if( path.indexOf( '/')== -1 && path.indexOf( '\\')== -1)
            return path;

        // need to normalize path before compacting
        path= normalizePath( path);

        // preserve ending slash if given in input path
        boolean endsWithSlash= path.endsWith( "/") || path.endsWith( "\\");

        // preserve starting slash if given in input path
        boolean startsWithSlash= path.startsWith( "/") || path.startsWith( "\\");

        Deque<String> stack= new ArrayDeque<>();

        // separator can either be windows or unix style
        String separatorRegex= "\\\\|/";
        String[] parts= path.split( separatorRegex);
        for( String part: parts)
        {
            if( part.equals( "..") && !stack.isEmpty() && !"..".equals( stack.peek()))
            {
                // only pop if there is a previous path, which is not a ".." path either
                stack.pop();
            }
            else if( part.equals( ".") || part.isEmpty())
            {
                // do nothing because we don't want a path like foo/./bar or foo//bar
            }
            else
                stack.push( part);
        }

        // build path based on stack
        StringBuilder sb= new StringBuilder();

        if( startsWithSlash)
            sb.append( separator);

        // now we build back using FIFO so need to use descending
        for( Iterator<String> it= stack.descendingIterator(); it.hasNext();)
        {
            sb.append( it.next());
            if( it.hasNext())
                sb.append( separator);
        }

        if( endsWithSlash&& stack.size()> 0)
            sb.append( separator);

        return sb.toString();
    }

    public static void removeDir( File d)
    {
        FileSystemUtils.deleteRecursively( d);
    }

    public static boolean tryRenameFile( File from, File to, boolean copyAndDeleteOnRenameFail) throws IOException
    {
        // do not try to rename non existing files
        if( !from.exists())
            return false;

        // some OS such as Windows can have problem doing rename IO operations so we may
        // need to
        // retry a couple of times to let it work
        boolean renamed= false;
        int count= 0;
        while( !renamed && count< 3)
        {
            if( logger.isDebugEnabled() && count> 0)
                logger.debug( "Retrying attempt {} to rename file from: {} to: {}", new Object[]{ count, from, to });

            renamed= from.renameTo( to);
            if( !renamed && count> 0)
            {
                try
                {
                    Thread.sleep( 1000);
                }
                catch( InterruptedException e)
                {
                    // ignore
                }
            }
            count++;
        }

        // we could not rename using renameTo, so lets fallback and do a copy/delete
        // approach.
        // for example if you move files between different file systems (linux ->
        // windows etc.)
        if( !renamed && copyAndDeleteOnRenameFail)
        {
            // now do a copy and delete as all rename attempts failed
            logger.debug( "Cannot rename file from: {} to: {}, will now use a copy/delete approach instead", from, to);
            renamed= renameFileUsingCopy( from, to);
        }

        if( logger.isDebugEnabled() && count> 0)
            logger.debug( "Tried {} to rename file: {} to: {} with result: {}", new Object[]{ count, from, to, renamed });
        return renamed;
    }

    public static boolean renameFileUsingCopy( File from, File to) throws IOException
    {
        // do not try to rename non existing files
        if( !from.exists())
            return false;

        logger.debug( "Rename file '{}' to '{}' using copy/delete strategy.", from, to);

        copyFile( from, to);
        if( !deleteFile( from))
            throw new IOException( "Renaming file from '"+ from+ "' to '"+ to+ "' failed: Cannot delete file '"+ from
                    + "' after copy succeeded");

        return true;
    }

    public static void copyFile( File from, File to) throws IOException
    {
        Files.copy( from.toPath(), to.toPath(), REPLACE_EXISTING);
    }

    public static boolean deleteFile( File file)
    {
        // do not try to delete non existing files
        if( !file.exists())
            return false;

        // some OS such as Windows can have problem doing delete IO operations so we may
        // need to
        // retry a couple of times to let it work
        boolean deleted= false;
        int count= 0;
        while( !deleted&& count< 3)
        {
            logger.debug( "Retrying attempt {} to delete file: {}", count, file);

            deleted= file.delete();
            if( !deleted&& count> 0)
            {
                try
                {
                    Thread.sleep( 1000);
                }
                catch( InterruptedException e)
                {
                    // ignore
                }
            }
            count++;
        }

        if( logger.isDebugEnabled()&& count> 0)
            logger.debug( "Tried {} to delete file: {} with result: {}", new Object[]{ count, file, deleted });
        return deleted;
    }

    public static boolean isAbsolute( File file)
    {
        if( isWindows())
        {
            // special for windows
            String path= file.getPath();
            if( path.startsWith( File.separator))
                return true;
        }
        return file.isAbsolute();
    }
    
    public static boolean renameWithLock( FileData content, File dest, Consumer<File> completed) throws Exception
    {
        FileLock flock= null;
        RandomAccessFile raFile=  null;
        String lock= FileUtil.stripExt( dest.getAbsolutePath())+ ".lock";
        try
        {
            raFile= new RandomAccessFile( lock, "rw");
            flock= raFile.getChannel().lock( 0, Long.MAX_VALUE, false);
            content.renameTo( dest);
            if( completed!= null)
                completed.accept( dest);
        }
        catch( Exception e)
        {
            logger.error( "{}/{}", dest.getAbsolutePath(), content.getFile() , e);
            throw e;
        }
        finally
        {
            if( flock!= null)
                flock.release();
            if( raFile!= null)
                raFile.close();
            content.release();
            deleteFile( new File( lock));
        }
        
        return true;
    }
    
    public static boolean mergeWithLock( List<String> resources, String target, Consumer<File> completed) throws Exception
    {
        final Queue<Exception> causes= new ArrayDeque<Exception>();
        final RandomAccessFile raFile= new RandomAccessFile( target, "rw");
        FileLock flock= null;
        try
        {
            flock= raFile.getChannel().lock( 0, Long.MAX_VALUE, false);
            resources.stream().map( File::new).forEach( fs->{
                try( FileInputStream fin= new FileInputStream( fs))
                {
                    raFile.seek( raFile.length());
                    byte[] bytes= new byte[BUFFER_SIZE];
                    ByteBuffer byteBuffer= ByteBuffer.wrap( bytes);
                    int read= fin.read( bytes);
                    while( read> 0)
                    {
                        byteBuffer.position( read).flip();
                        raFile.getChannel().write( byteBuffer);
                        read= fin.read( bytes);
                    }
                    if( completed!= null)
                        completed.accept( fs);
                    logger.debug( "file:{} is merged", fs);
                }
                catch( Exception e)
                {
                    logger.error( "file:{} merge failed", fs, e);
                    causes.offer( e);
                }
            });
            
            if( !causes.isEmpty())
                throw causes.poll();
            return true;
        }
        finally
        {
            if( flock!= null)
                flock.release();
            if( raFile!= null)
                raFile.close();
        }
    }

    public static boolean createNewFile( File file) throws IOException
    {
        // need to check first
        if( file.exists())
            return false;
        try
        {
            return file.createNewFile();
        }
        catch( IOException e)
        {
            // and check again if the file was created as createNewFile may create the file
            // but throw a permission error afterwards when using some NAS
            if( file.exists())
                return true;
            else
                throw e;
        }
    }
}
