package net.tmclean.pxeserver.image;

import java.util.List;

public interface ImageRepository {

	List<Image> getAllImages();
	Image getImage( String name );
	Image getImage( long id );
}
