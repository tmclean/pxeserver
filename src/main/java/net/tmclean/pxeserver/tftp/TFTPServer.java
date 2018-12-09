package net.tmclean.pxeserver.tftp;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.net.tftp.TFTP;
import org.apache.commons.net.tftp.TFTPAckPacket;
import org.apache.commons.net.tftp.TFTPErrorPacket;
import org.apache.commons.net.tftp.TFTPPacket;
import org.apache.commons.net.tftp.TFTPPacketException;
import org.apache.commons.net.tftp.TFTPReadRequestPacket;
import net.tmclean.pxeserver.iso.ImageRepository;

public class TFTPServer implements Callable<Void>{

	private static String buildSessionStr( TFTPPacket packet ) {
		return packet.getAddress().getHostAddress() + ":" + packet.getPort();
	}

	private final ImageRepository imageRepository;
	
	private final TFTP tftp = new TFTP();
	private final Map<String, TFTPSendContext> sendContextMap = new ConcurrentHashMap<>();
	
	public TFTPServer( ImageRepository imageRepository ) {
		this.imageRepository = imageRepository;
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
							t.getMessage() 
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
		String imageName   = reqFilename.substring( 0, reqFilename.indexOf( '/' ) );
		String filePath    = reqFilename.substring( reqFilename.indexOf( "/" )+1 );
		
		if( !this.imageRepository.imageFilePathExists( imageName, filePath ) ) {
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
			this.imageRepository,
			readReq.getAddress(), 
			readReq.getPort(),
			imageName,
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
