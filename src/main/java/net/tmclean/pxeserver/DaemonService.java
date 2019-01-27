package net.tmclean.pxeserver;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Service;

@Service
public abstract class DaemonService implements Callable<Void> {

	private final ExecutorService service = Executors.newSingleThreadExecutor();

    private Future<?> shutdown = null;
    
	@PostConstruct
	public void init() {
		this.shutdown = service.submit( this );
	}
	
	@PreDestroy 
	public void destroy() {
		this.service.shutdownNow();
		try {
			shutdown.get();
		}
		catch( ExecutionException | InterruptedException e ) {
			e.printStackTrace();
		}
	}
}
