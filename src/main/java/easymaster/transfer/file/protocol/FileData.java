/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.protocol;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import easymaster.transfer.file.TransferServer;
import easymaster.transfer.file.config.TransferEnvironment;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelException;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.ObjectUtil;

/**
 * @author Jongoh Lee
 */

public class FileData extends AbstractReferenceCounted implements Comparable<FileData>, ByteBufHolder
{
    private Logger logger= LoggerFactory.getLogger( FileData.class);
    
    public static boolean deleteOnExitTemporaryFile= true;

    public static final String prefix= "FUp_";

    public static final String postfix= ".tmp";

    private final File basedir;

    private final String name;

    private final long definedSize;

    private File file;

    private FileChannel fileChannel;

    private boolean renamed;

    private long size;

    private boolean completed;

    private long maxSize= -1;
    
    public FileData( long definedSize)
    {
        this( UUID.randomUUID().toString(), definedSize);
    }

    public FileData( long definedSize, File basedir)
    {
        this( UUID.randomUUID().toString(), definedSize, basedir);
    }
    
    public FileData( long definedSize, TransferEnvironment environment)
    {
        this( UUID.randomUUID().toString(), definedSize, new File( environment.getRepository().getBaseDir()));
    }
    
    private FileData( String name, long definedSize)
    {
        this.name= name;
        this.definedSize= definedSize;
        this.basedir= new File( "");
    }
    
    private FileData( String name, long definedSize, File basedir)
    {
        ObjectUtil.checkNotNull( name, "name");
        if( basedir.exists() && !basedir.isDirectory())
            throw new IllegalArgumentException( "only directory is allowed");
        
        synchronized( TransferServer.class)
        {
            if( !basedir.mkdirs() && !basedir.exists())
                
                throw new IllegalArgumentException( "can not create basedir");
        }
        this.name= name;
        this.definedSize= definedSize;
        this.basedir= basedir;
    }
    
    public String getName()
    {
        return this.name;
    }
    
    @Override
    public ByteBuf content()
    {
        try { return getByteBuf();}
        catch( IOException e)
        {
            throw new ChannelException( e);
        }
    }

    @Override
    public FileData copy()
    {
        final ByteBuf content= content();
        return replace( content!= null ? content.copy() : null);
    }
    
    @Override
    public FileData duplicate()
    {
        final ByteBuf content= content();
        return replace( content!= null ? content.duplicate() : null);
    }
    
    @Override
    public FileData retainedDuplicate()
    {
        ByteBuf content= content();
        if( content!= null)
        {
            content= content.retainedDuplicate();
            boolean success= false;
            try
            {
                FileData duplicated= replace( content);
                success= true;
                return duplicated;
            }
            finally
            {
                if( !success)
                    content.release();
            }
        }
        else
            return replace( null);
    }

    @Override
    public FileData replace( ByteBuf content)
    {
        FileData data= new FileData( getName(), definedLength(), this.basedir);
        if( content!= null)
        {
            try{ data.setContent( content);}
            catch( IOException e)
            {
                throw new ChannelException( e);
            }
        }

        logger.debug( "FileData content replaced.");

        return data;
    }
    
    @Override
    public void deallocate()
    {
        delete();
        logger.debug( "AgentFileData deallocated.");
    }

    @Override
    public FileData retain()
    {
        logger.debug( "FileData retained.");
        super.retain();
        return this;
    }

    @Override
    public FileData retain( int increment)
    {
        logger.debug( "FileData retained [{}].", increment);
        super.retain( increment);
        return this;
    }

    @Override
    public boolean release()
    {
        logger.debug( "FileData released.");
        return super.release();
    }

    @Override
    public boolean release( int decrement)
    {
        logger.debug( "FileData released [{}].", decrement);
        return super.release( decrement);
    }

    @Override
    public FileData touch()
    {
        return this;
    }

    @Override
    public FileData touch( Object hint)
    {
        return this;
    }
    
    @Override
    public int hashCode()
    {
        return this.getName().hashCode();
    }

    @Override
    public boolean equals( Object o)
    {
        return o instanceof FileData && getName().equals( ( (FileData)o).getName());
    }

    @Override
    public int compareTo( FileData o)
    {
        return getName().compareToIgnoreCase( o.getName());
    }
    
    public void setContent( File file) throws IOException
    {
        if( this.file!= null)
            delete();
        this.file= file;
        this.size= file.length();
        this.renamed= true;
        setCompleted();
        
        logger.debug( "setContent file[{}] is completed", file);
    }
    
    public void setContent( InputStream instream) throws IOException
    {
        ObjectUtil.checkNotNull( instream, "inputstream");
        
        if( this.file!= null)
            delete();
        
        this.file= tempFile();
        
        int written= 0;
        try( FileOutputStream fout= new FileOutputStream( this.file))
        {
            FileChannel fch= fout.getChannel();
            byte[] bytes= new byte[16* 1024];
            ByteBuffer byteBuffer= ByteBuffer.wrap( bytes);
            int read= instream.read( bytes);
            while( read> 0)
            {
                byteBuffer.position( read).flip();
                written+= fch.write( byteBuffer);
                read= instream.read( bytes);
            }
            fch.force( false);
        }
        
        this.size= written;
        if( this.definedSize> 0 && this.definedSize< this.size)
        {
            if( !this.file.delete())
                logger.warn( "Failed to delete: {}", this.file);
            this.file= null;
            throw new IOException( "Out of size: "+ this.size+ "> "+ this.definedSize);
        }
        renamed= true;
        setCompleted();
    }
    
    public void setContent( ByteBuf content) throws IOException
    {
        ObjectUtil.checkNotNull( content, "content");
        
        try
        {
            this.size= content.readableBytes();
            if( this.definedSize> 0 && this.definedSize< size)
                throw new IOException( "Out of size: "+ this.size+ "> "+ this.definedSize);
            
            if( this.file== null)
                this.file= tempFile();
            
            if( content.readableBytes()== 0)
            {
                if( !this.file.createNewFile())
                {
                    if( this.file.length()== 0)
                        return;
                    else
                    {
                        if( !this.file.delete() || !this.file.createNewFile())
                            throw new IOException( "file exists already: "+ this.file);
                    }
                }
                return;
            }
            
            try( FileOutputStream fout= new FileOutputStream( this.file))
            {
                FileChannel fch= fout.getChannel();
                ByteBuffer byteBuffer= content.nioBuffer();
                int written= 0;
                while( written< this.size)
                   written+= fch.write( byteBuffer);
                content.readerIndex( content.readerIndex()+ written);
                fch.force( false);
            }
            setCompleted();
            
        }
        finally
        {
            content.release();
        }
        
        logger.debug( "setContenet byteBuf is completed");
    }
    
    public void addContent( ByteBuf content, boolean last) throws IOException
    {
        if( content!= null)
        {
            try
            {
                int localsize= content.readableBytes();
                if( this.definedSize> 0 && this.definedSize< this.size+ localsize)
                    throw new IOException( "Out of size: "+ ( this.size+ localsize)+ "> "+ this.definedSize);
                
                ByteBuffer byteBuffer= content.nioBufferCount()== 1 ? content.nioBuffer() : content.copy().nioBuffer();
                int written= 0;
                if( this.file== null)
                    this.file= tempFile();
                if( this.fileChannel== null)
                    this.fileChannel= FileChannel.open( Paths.get( this.file.toURI()), CREATE, READ, WRITE);
                
                while( written< localsize)
                    written+= this.fileChannel.write( byteBuffer);
                
                this.size+= localsize;
            }
            finally
            {
                content.release();
            }
        }
        
        if( last)
        {
            if( this.file== null)
                this.file= tempFile();
            if( this.fileChannel== null)
                this.fileChannel= FileChannel.open( Paths.get( this.file.toURI()), CREATE, READ, WRITE);
            
            this.fileChannel.force( false);
            this.fileChannel.close();
            this.fileChannel= null;
            setCompleted();
        }
        else
            ObjectUtil.checkNotNull( content, "content");
    }
    
    public File getFile() throws IOException
    {
        return this.file;
    }
    
    public boolean isCompleted()
    {
        return this.completed;
    }
    
    public void setCompleted()
    {
        this.completed= true;
    }
    
    public long length()
    {
        return this.size;
    }
    
    public long definedLength()
    {
        return this.definedSize;
    }
        
    public byte[] get() throws IOException
    {
        if( this.file== null)
            return EmptyArrays.EMPTY_BYTES;
        
        return readFrom( this.file);
    }
    
    public ByteBuf getByteBuf() throws IOException
    {
        if( this.file== null)
            return Unpooled.EMPTY_BUFFER;
        byte[] bytes= readFrom( this.file);
        return Unpooled.wrappedBuffer( bytes);
    }
    
    public ByteBuf getChunk( int length) throws IOException
    {
        if( this.file== null || length== 0)
            return EMPTY_BUFFER;
        if( this.fileChannel== null)
            this.fileChannel= FileChannel.open( Paths.get( this.file.toURI()), CREATE, READ, WRITE);
        
        int read= 0;
        ByteBuffer buf= ByteBuffer.allocate( length);
        while( read< length)
        {
            int now= this.fileChannel.read( buf);
            if( now== -1)
            {
                this.fileChannel.close();
                this.fileChannel= null;
                break;
            }
            else
                read+= now;
        }
        
        if( read== 0)
            return Unpooled.EMPTY_BUFFER;
        
        buf.flip();
        ByteBuf wrapped= Unpooled.wrappedBuffer( buf);
        wrapped.readerIndex( 0);
        wrapped.writerIndex( read);
        return wrapped;
    }
    
    public boolean renameTo( File dest) throws IOException
    {
        ObjectUtil.checkNotNull( dest, "destination");
        if( this.file== null)
            throw new IOException( "No file defined so can not be renamed");
        
        if( this.fileChannel!= null)
        {
            try
            {
                this.fileChannel.force( false);
                this.fileChannel.close();
                this.fileChannel= null;
                logger.debug( "file channel for [{}] is closed before renameTo", this.file);
            }
            catch( IOException e)
            {
                logger.warn( "Failed to close a filechannel.", e);
            }
        }
        
        Files.move( this.file.toPath(), dest.toPath(),  StandardCopyOption.REPLACE_EXISTING);
        
        this.file= dest;
        renamed= true;
        return true;
    }
    
    public void delete()
    {
        if( this.fileChannel!= null)
        {
            try
            {
                this.fileChannel.force( false);
                this.fileChannel.close();
                logger.debug( "file channel for [{}] is closed before renameTo", this.file);
            }
            catch( IOException e)
            {
                logger.warn( "Failed to close a filechannel.", e);
            }
        }

        if( !renamed)
        {
            if( this.file!= null && this.file.exists())
            {
                if( !this.file.delete())
                    logger.warn( "Failed to delete: {}", this.file);
            }
            this.file= null;
        }
    }
    
    private byte[] readFrom( File src) throws IOException
    {
        long srcsize= src.length();
        if( srcsize> Integer.MAX_VALUE)
            throw new IllegalArgumentException( "File is too big to be loaded in memory");

        byte[] bytes= new byte[(int)srcsize];
        try( FileInputStream inStream= new FileInputStream( src))
        {
            FileChannel fch= inStream.getChannel();
            ByteBuffer buf= ByteBuffer.wrap( bytes);
            int read= 0;
            while( read< srcsize)
                read+= fch.read( buf);
        }
        logger.debug( "read from file [{}] length [{}] ... completed.", src, srcsize);
        return bytes;
    }
    
    private File tempFile() throws IOException
    {
        File tempDir= new File( this.basedir, "tmp");
        tempDir.mkdir();
        File tempFile= tempDir.exists() && tempDir.isDirectory() ?
                File.createTempFile( prefix, postfix, tempDir) : File.createTempFile( prefix, postfix);

        if( deleteOnExitTemporaryFile)
            tempFile.deleteOnExit();

        logger.debug( "Temporary File[{}] is created.", tempFile);
        return tempFile;
    }
    
    @Override
    public String toString()
    {
        return "FileData [name="+ name+ ", definedSize="+ definedSize+
                ", file="+ file+ ", size="+ size+ ", completed="+ completed+ ", maxSize=" + maxSize+ "]";
    }

}
