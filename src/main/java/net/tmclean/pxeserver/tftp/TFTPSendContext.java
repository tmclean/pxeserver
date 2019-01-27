package net.tmclean.pxeserver.tftp;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.commons.net.tftp.TFTP;
import org.apache.commons.net.tftp.TFTPDataPacket;
import org.apache.commons.net.tftp.TFTPPacket;

import net.tmclean.pxeserver.image.Image;
import net.tmclean.pxeserver.image.aggregate.ImageContentDirectory;

public class TFTPSendContext {
	
	private final InetAddress address;
	private final int port;
	
	private final ImageContentDirectory contentDirectory;
	private final Image image;
	private final String filePath;

	private int block = 0;
	private int sent = 0;
	
	public TFTPSendContext( ImageContentDirectory contentDirectory, InetAddress address, int port, Image image, String filePath ) {
		this.contentDirectory = contentDirectory;
		this.address = address;
		this.port = port;
		this.image = image;
		this.filePath = filePath;
	}
	
	public boolean hasMore() throws IOException {
		
		long fileBytes = this.contentDirectory.getImageFileSize( this.image, this.filePath );
		
		long remaining = fileBytes - sent;
		
		boolean more = remaining > 0;
		
		if( more ) {
			System.out.println( "Send context has " + remaining + " more bytes to send" );
		}
		else {
			System.out.println( "No more data to send in context" );
		}
		
		return more;
	}
	
	public void sendNextBlock( TFTP tftp ) throws IOException {

		byte[] data = new byte[ TFTPPacket.SEGMENT_SIZE ];		
		int count = this.contentDirectory.readImageFile( this.image, this.filePath, data, sent, data.length );

		sent += count;
		++block;
		
		TFTPDataPacket dataResp = new TFTPDataPacket( address, port, block, data, 0, count );
		tftp.send( dataResp );
	}
}
