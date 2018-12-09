package net.tmclean.pxeserver;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.tmclean.pxeserver.iso.ImageRepository;
import net.tmclean.pxeserver.iso.ImageRepositoryImpl;
import net.tmclean.pxeserver.nfs.NfsService;
import net.tmclean.pxeserver.tftp.TFTPServer;

public class Main {

	private static final ExecutorService servers  = Executors.newFixedThreadPool( 2 );

	
	public static void main( String[] args ) throws IOException {

		try{
			ImageRepository imageRepository = new ImageRepositoryImpl();
			
			TFTPServer tftpServer = new TFTPServer( imageRepository );
			NfsService nfsServer = new NfsService( imageRepository );
			
			waitForShutdown(
				servers.submit( tftpServer ),
				servers.submit( nfsServer )
			);
		}
		finally {
			servers.shutdownNow();
		}
	}
	
	@SafeVarargs
	private static void waitForShutdown( Future<Void> ...futures ) {
		for( Future<?> future : futures ) {
			try {
				future.get();
			}
			catch( ExecutionException | InterruptedException e ) {
				e.printStackTrace();
			}
		}
	}
}
