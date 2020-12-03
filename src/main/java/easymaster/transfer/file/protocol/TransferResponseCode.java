/*
 * Copyright 2020 the original author or authors.
 *
 */

package easymaster.transfer.file.protocol;

import easymaster.transfer.file.util.TransferConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

/**
 * @author Jongoh Lee
 *
 */

public class TransferResponseCode implements Comparable<TransferResponseCode>
{

    /**
    *
       Response-Code:
       Response Only
       200= OK
       210= Not Exist
       400= Bad Request
       402= Bad Response
       404= Source File Not Found
       405= Already Exist
       500= Internal Server Error
       510= Destination Not Responding
       520= Transfer Failed
       525= Delete Failed
       530= File Permission Error
       540= TimeOut Occurred
    */
    
    public static final TransferResponseCode OK= newResponse( 200, "OK");

    public static final TransferResponseCode NOT_EXIST= newResponse( 210, "Not Exist");

    public static final TransferResponseCode BAD_REQUEST= newResponse( 400, "Bad Request");

    public static final TransferResponseCode BAD_RESPONSE= newResponse( 402, "Bad Response");

    public static final TransferResponseCode SOURCE_FILE_NOT_FOUND= newResponse( 404, "Source File Not Found");

    public static final TransferResponseCode ALREADY_EXIST= newResponse( 405, "Already Exist");

    public static final TransferResponseCode INTERNAL_SERVER_ERROR= newResponse( 500, "Internal Server Error");

    public static final TransferResponseCode DESTINATION_FILE_NOT_FOUND= newResponse( 510, "Destination File Not Found");

    public static final TransferResponseCode TRANSFER_FAILED= newResponse( 520, "Transfer Failed");

    public static final TransferResponseCode MERGE_FAILED= newResponse( 522, "Merge Failed");

    public static final TransferResponseCode DELETE_FAILED= newResponse( 525, "Delete Failed");

    public static final TransferResponseCode FILE_PERMISSION_ERROR= newResponse( 530, "File Permission Error");

    public static final TransferResponseCode TIMEOUT_OCCURRED= newResponse( 540, "Timeout Occurred");

    private final int code;

    private final AsciiString codeAsText;

    private ResponseCode codeType;

    private final String reseonPhrase;

    private final byte[] bytes;


    public static TransferResponseCode newResponse( int responseCode, String reason)
    {
        return new TransferResponseCode( responseCode, reason, true);
    }

    public static TransferResponseCode valueOf( int code)
    {
        TransferResponseCode resCode= valueOf0( code);
        return resCode!= null ? resCode: new TransferResponseCode( code);
    }

    public static TransferResponseCode valueOf( int code, String reasonPhrase)
    {
        TransferResponseCode resCode= valueOf0( code);
        return resCode!= null && resCode.reasonPhrase().contentEquals( reasonPhrase) ?
                resCode : new TransferResponseCode( code, reasonPhrase);
    }

    private static TransferResponseCode valueOf0( int code)
    {
        switch( code)
        {
            case 200:
                return OK;
            case 210:
                return NOT_EXIST;
            case 400:
                return BAD_REQUEST;
            case 402:
                return BAD_RESPONSE;
            case 404:
                return SOURCE_FILE_NOT_FOUND;
            case 405:
                return ALREADY_EXIST;
            case 500:
                return INTERNAL_SERVER_ERROR;
            case 510:
                return DESTINATION_FILE_NOT_FOUND;
            case 520:
                return TRANSFER_FAILED;
            case 522:
                return MERGE_FAILED;
            case 525:
                return DELETE_FAILED;
            case 530:
                return FILE_PERMISSION_ERROR;
            case 540:
                return TIMEOUT_OCCURRED;
        }
        return null;
    }

    private TransferResponseCode( int code)
    {
        this( code, ResponseCode.valueOf( code).defaultReasonPhrase()+ " ("+ code+ ')', false);
    }

    private TransferResponseCode( int code, String reasonPhrase)
    {
        this( code, reasonPhrase, false);
    }

    private TransferResponseCode( int code, String resonPhrase, boolean bytes)
    {
        if( code< 0)
            throw new IllegalArgumentException( "code: "+ code+ " (expected: 0+)");

        if( resonPhrase== null)
            throw new NullPointerException( "reasonPhrase");

        for( int i= 0; i< resonPhrase.length(); i++)
        {
            char c= resonPhrase.charAt( i);
            switch( c)
            {
                case '\n': case '\r':
                    throw new IllegalArgumentException(
                            "reasonPhrase contains one of the following prohibited characters: "+
                            "\\r\\n: "+ resonPhrase);
            }
        }

        this.code= code;
        String codeString= Integer.toString( code);
        codeAsText= new AsciiString( codeString);
        this.reseonPhrase= resonPhrase;
        if( bytes)
            this.bytes= ( codeString+ ' '+ resonPhrase).getBytes( CharsetUtil.US_ASCII);
        else
            this.bytes= null;
    }

    public int code()
    {
        return this.code;
    }

    public AsciiString codeAsText()
    {
        return this.codeAsText;
    }

    public String reasonPhrase()
    {
        return this.reseonPhrase;
    }

    public ResponseCode responseCode()
    {
        ResponseCode type= this.codeType;
        if( type== null)
            this.codeType= type= ResponseCode.valueOf( this.code);
        return type;
    }

    @Override
    public int hashCode()
    {
        return code();
    }

    @Override
    public boolean equals( Object o)
    {
        if( !( o instanceof TransferResponseCode))
            return false;
        return code()== ( (TransferResponseCode)o).code();
    }

    @Override
    public int compareTo( TransferResponseCode o)
    {
        return code()- o.code();
    }

    @Override
    public String toString()
    {
        return new StringBuffer( this.reseonPhrase.length()+ 4)
                .append( this.codeAsText)
                .append( ' ')
                .append( this.reseonPhrase)
                .toString();
    }

    void encode( ByteBuf buf)
    {
        if( this.bytes== null)
        {
            ByteBufUtil.copy( this.codeAsText, buf);
            buf.writeByte( TransferConstants.SP);
            buf.writeCharSequence( this.reseonPhrase, CharsetUtil.US_ASCII);
        }
        else
            buf.writeBytes( this.bytes);
    }

}
