package net.tmclean.pxeserver.image;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImageRepositoryImpl implements ImageRepository {

	private final List<Image> images = new ArrayList<>();
	
	private final Map<Long,   Image> idToImageMap   = new ConcurrentHashMap<>();
	private final Map<String, Image> nameToImageMap = new ConcurrentHashMap<>();
	
	public ImageRepositoryImpl() throws IOException {

		Image debian950 = new Image();
		debian950.setName( "debian-9-5-0" );
		debian950.setDescription( "Debian Stretch 9.5.0 ISO" );
		debian950.setId( 1L << 24 );
		debian950.setFormat( ImageFormat.LOCAL_ISO );
		debian950.setLocation( "C:/users/tjrag/desktop/debian-9.5.0-amd64-netinst.iso" );
		
		Image pxelinux = new Image();
		pxelinux.setName( "pxelinux" );
		pxelinux.setDescription( "PXELINUX" );
		pxelinux.setId( 2L << 24 );
		pxelinux.setFormat( ImageFormat.LOCAL_DIR );
		pxelinux.setLocation( "C:/users/tjrag/desktop/pxelinux" );
		
		addImage( debian950 );
		addImage( pxelinux );
	}
	
	private void addImage( Image image ) {
		images.add( image );
		idToImageMap.put( image.getId(), image );
		nameToImageMap.put( image.getName(), image );
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
}
