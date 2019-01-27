package net.tmclean.pxeserver;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import net.tmclean.pxeserver.nfs.NfsService;
import net.tmclean.pxeserver.tftp.TFTPServer;

@Service
public class PxeServer {

//	private final ExecutorService servers  = Executors.newFixedThreadPool( 2 );
//	
//	private final NfsService nfsService;
//	private final TFTPServer tftpServer;
//	
//	public PxeServer(  NfsService nfsService, TFTPServer tftpServer ) {
//		this.nfsService = nfsService;
//		this.tftpServer = tftpServer;
//	}
	
//	@PostConstruct
//	public void init() throws IOException {
//		waitForShutdown(
//			servers.submit( tftpServer ),
//			servers.submit( nfsService )
//		);
//	}
	
//	private void waitForShutdown( Future<?> ... futures ) {
//		for( Future<?> future : futures ) {
//			try {
//				future.get();
//			}
//			catch( ExecutionException | InterruptedException e ) {
//				e.printStackTrace();
//			}
//		}
//	}
//
//	@PreDestroy
//	public void destroy() {
//		servers.shutdownNow();
//	}
}
