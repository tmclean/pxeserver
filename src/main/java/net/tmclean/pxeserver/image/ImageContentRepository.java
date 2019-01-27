package net.tmclean.pxeserver.image;

import java.io.IOException;
import java.util.List;

public interface ImageContentRepository {

	void init() throws IOException;
	void destroy() throws IOException;

	default boolean isImageRootId( long id ) throws IOException {
    	return (id & 0x00ffffffL) == 0;
	}
	
	default long imageFileIdToImageId( long id ) throws IOException {
    	return id & 0xff000000L;
    }

	long getFileSize( String filePath ) throws IOException;
	
	boolean filePathExists( String filePath ) throws IOException;
	
	ImageFileEntry getFileEntry( String filePath ) throws IOException;

	long filePathToId( String filePath ) throws IOException;
	String idToFilePath( long id ) throws IOException;

	List<String> listPath( String filePath ) throws IOException;
	
	default int readImageFile( String filePath, byte[] data ) throws IOException {
		return readFile( filePath, data, 0, data.length );
	}
	
	int readFile( String filePath, byte[] data, int offset, int length ) throws IOException;
	
}
