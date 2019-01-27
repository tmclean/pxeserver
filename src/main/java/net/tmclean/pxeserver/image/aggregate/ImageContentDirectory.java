package net.tmclean.pxeserver.image.aggregate;

import java.io.IOException;
import java.util.List;

import net.tmclean.pxeserver.image.Image;
import net.tmclean.pxeserver.image.ImageFileEntry;

public interface ImageContentDirectory {

	void init() throws IOException;
	void destroy() throws IOException;
	
	default boolean isImageRootId( long id ) throws IOException {
    	return (id & 0x00ffffffL) == 0;
	}
	
	default long imageFileIdToImageId( long id ) throws IOException {
    	return id & 0xff000000L;
    }

	long getImageFileSize( Image image, String filePath ) throws IOException;
	
	boolean imageFilePathExists( Image image, String filePath ) throws IOException;
	
	ImageFileEntry getFileEntry( Image image, String filePath ) throws IOException;

	long filePathToId( Image image, String filePath ) throws IOException;
	String idToFilePath( Image image, long id ) throws IOException;

	List<String> listImagePath( Image image, String filePath ) throws IOException;
	
	default int readImageFile( Image image, String filePath, byte[] data ) throws IOException {
		return readImageFile( image, filePath, data, 0, data.length );
	}
	
	int readImageFile( Image image, String filePath, byte[] data, int offset, int length ) throws IOException;
}
