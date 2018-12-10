package net.tmclean.pxeserver;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.tmclean.pxeserver.image.AggregateImageContentRepository;
import net.tmclean.pxeserver.image.Image;
import net.tmclean.pxeserver.image.ImageContentRepository;
import net.tmclean.pxeserver.image.ImageRepository;
import net.tmclean.pxeserver.image.ImageRepositoryImpl;
import net.tmclean.pxeserver.nfs.NfsService;
import net.tmclean.pxeserver.tftp.TFTPServer;

public class Main {

	private static final ExecutorService servers  = Executors.newFixedThreadPool( 2 );

	
	public static void main( String[] args ) throws IOException {

		try{
			ImageRepository imageRepository = new ImageRepositoryImpl();
			ImageContentRepository imageContentRepository = new AggregateImageContentRepository();
			
			for( Image image : imageRepository.getAllImages() ) {
				imageContentRepository.initImage( image );
			}
			
			TFTPServer tftpServer = new TFTPServer( imageRepository, imageContentRepository );
			NfsService nfsServer = new NfsService( imageRepository, imageContentRepository );
			
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
