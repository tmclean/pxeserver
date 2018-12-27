package net.tmclean.pxeserver.image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ImageRepositoryImpl implements ImageRepository {

	private final List<Image> images = new ArrayList<>();
	
	private final Map<Long,   Image> idToImageMap   = new ConcurrentHashMap<>();
	private final Map<String, Image> nameToImageMap = new ConcurrentHashMap<>();
	
	private Image rootImage;
	
	public ImageRepositoryImpl() throws IOException {
		String jsonDbFile = System.getProperty( "pxeserver.jsonDb" );
		byte[] jsonData = Files.readAllBytes( Paths.get( jsonDbFile ) );
		ObjectMapper mapper = new ObjectMapper();
		Image[] images = mapper.readValue( jsonData, Image[].class );
		
		for( Image image : images ) {
			this.addImage( image );
		}
	}
	
	private void addImage( Image image ) {
		images.add( image );
		idToImageMap.put( image.getId(), image );
		nameToImageMap.put( image.getName(), image );
		if( image.isRoot() ) {
			this.rootImage = image;
		}
	}
	
	@Override
	public List<Image> getAllImages() {
		return Collections.unmodifiableList( this.images );
	}
	
	@Override
	public Image getImage( long id ) {
		return this.idToImageMap.get( id );
	}
	
	@Override
	public Image getImage( String name ) {
		return this.nameToImageMap.get( name );
	}
	
	@Override
	public Image getRootImage() {
		return this.rootImage;
	}
}
