package net.tmclean.pxeserver;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableAutoConfiguration
public class Main {
	
	@Bean
	public CountDownLatch closeLatch() {
		return new CountDownLatch( 1 );
	}
	
	public static void main( String[] args ) throws IOException, InterruptedException {
		ConfigurableApplicationContext ctx = SpringApplication.run( Main.class, args );
		
		CountDownLatch closeLatch = ctx.getBean( CountDownLatch.class );
		Runtime.getRuntime().addShutdownHook( new Thread() {
			@Override
			public void run() {
				closeLatch.countDown();
			}
		});
		
		closeLatch.await();
	}
}
