package net.tmclean.pxeserver.tftp;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.net.tftp.TFTP;
import org.apache.commons.net.tftp.TFTPAckPacket;
import org.apache.commons.net.tftp.TFTPErrorPacket;
import org.apache.commons.net.tftp.TFTPPacket;
import org.apache.commons.net.tftp.TFTPPacketException;
import org.apache.commons.net.tftp.TFTPReadRequestPacket;
import org.springframework.stereotype.Service;

import net.tmclean.pxeserver.DaemonService;
import net.tmclean.pxeserver.image.Image;
import net.tmclean.pxeserver.image.ImageRepository;
import net.tmclean.pxeserver.image.aggregate.ImageContentDirectory;

@Service
public class TFTPServer extends DaemonService {

	private static String buildSessionStr( TFTPPacket packet ) {
		return packet.getAddress().getHostAddress() + ":" + packet.getPort();
	}

	private final ImageRepository imageRepository;
	private final ImageContentDirectory contentDirectory;
	
	private final TFTP tftp = new TFTP();
	private final Map<String, TFTPSendContext> sendContextMap = new ConcurrentHashMap<>();
	
	public TFTPServer( ImageRepository imageRepository, ImageContentDirectory contentDirectory ) {
		this.imageRepository = imageRepository;
		this.contentDirectory = contentDirectory;
	}
	
	public Void call() throws IOException, TFTPPacketException {
		try {
			tftp.open( TFTP.DEFAULT_PORT );
			tftp.setDefaultTimeout( 0 );
			tftp.setSoTimeout( TFTP.DEFAULT_TIMEOUT );

			eventLoop();
		}
		finally {
			tftp.close();
		}
		
		return null;
	}
	
	private void eventLoop() throws IOException, TFTPPacketException {
		while( true ) {
			TFTPPacket packet = null;
			try {
				packet = tftp.receive();
				System.out.println( "Got packet of type " + packet.getClass() );
				
				if( isPacketOfType( packet, TFTPReadRequestPacket.class ) ) {
					processReadRequest( (TFTPReadRequestPacket)packet );
				}
				else if( isPacketOfType( packet, TFTPAckPacket.class ) ) {
					processAckPacket( (TFTPAckPacket)packet );
				}
			}
			catch( SocketTimeoutException e ) {
				// Ignore and restart loop
			}
			catch( Throwable t ) {
				
				t.printStackTrace();
				
				if( packet != null ) {
					TFTPErrorPacket error = 
						new TFTPErrorPacket( 
							packet.getAddress(), 
							packet.getPort(), 
							TFTPErrorPacket.ERROR, 
							"ERROR"
						);
					
					tftp.send( error );
				}
			}
		}
	}
	
	private boolean isPacketOfType( TFTPPacket packet, Class<? extends TFTPPacket> clazz ) {
		return packet.getClass().isAssignableFrom( clazz );
	}
	
	private void processReadRequest( TFTPReadRequestPacket readReq ) throws IOException {

		String sessionStr = buildSessionStr( readReq );
		
		String modeStr = TFTP.getModeName( readReq.getMode() );
		System.out.println( "Got read request for " + readReq.getFilename() + " with mode " + modeStr  );
		
		String reqFilename = readReq.getFilename();
		reqFilename = reqFilename.replace( "//", "/" );

		String imageName = reqFilename.substring( 0, reqFilename.indexOf( '/' ) );
		String filePath  = reqFilename.substring( reqFilename.indexOf( "/" )+1 );
		
		if( "".equals( imageName ) ) {
			imageName = "/";
		}

		Image image = this.imageRepository.getImage( imageName );
		
		if( image == null || !contentDirectory.imageFilePathExists( image, filePath ) ) {
			imageName = filePath.substring( 0, filePath.indexOf( '/' ) );
			filePath  = filePath.substring( filePath.indexOf( "/" )+1 );
			
			image = imageRepository.getImage( imageName );
		}

		System.out.println( "Resolved image name "  + imageName + " and file path " + filePath );		
		
		if( !this.contentDirectory.imageFilePathExists( image, filePath ) ) {
			System.out.println( "File path " + filePath + " does not exist" );
			
			tftp.send( 
				new TFTPErrorPacket( 
					readReq.getAddress(), 
					readReq.getPort(), 
					TFTPErrorPacket.FILE_NOT_FOUND, 
					"Failed to locate file " + readReq.getFilename() 
				) 
			);
			
			return;
		}
		
		TFTPSendContext sendCtx = new TFTPSendContext( 
			this.contentDirectory,
			readReq.getAddress(), 
			readReq.getPort(),
			image,
			filePath
		);
		
		sendContextMap.put( sessionStr, sendCtx );
		
		if( sendCtx.hasMore() ) {
			sendCtx.sendNextBlock( tftp );
		}
	}
	
	private void processAckPacket( TFTPAckPacket ack ) throws IOException {
		
		String sessionStr = buildSessionStr( ack );

		if( sendContextMap.containsKey( sessionStr ) ) {
			TFTPSendContext sendCtx = sendContextMap.get( sessionStr );
			if( sendCtx.hasMore() ) {
				sendCtx.sendNextBlock( tftp );
			}
		
			if( !sendCtx.hasMore() ) {
				sendContextMap.remove( sessionStr );
			}
		}
	}
}
