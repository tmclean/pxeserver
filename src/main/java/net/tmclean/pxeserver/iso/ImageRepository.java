package net.tmclean.pxeserver.iso;

import java.io.IOException;
import java.util.List;

import com.github.stephenc.javaisotools.loopfs.iso9660.Iso9660FileEntry;

public interface ImageRepository {

	List<Image> getAllImages();
	Image getImage( String name );
	Image getImage( long id );

	int getImageFileSize( String imageName, String filePath ) throws IOException;
	
	boolean imageFilePathExists( String imageName, String filePath ) throws IOException;
	
	Iso9660FileEntry getFileEntry( String imageName, String filePath ) throws IOException;

	long filePathToId( String imageName, String filePath ) throws IOException;
	String idToFilePath( long id ) throws IOException;

	boolean isImageRootId( long id ) throws IOException;
	long imageFileIdToImageId( long id ) throws IOException;
	
	List<String> listImagePath( String imageName, String filePath ) throws IOException;
	
	default int readImageFile( String imageName, String filePath, byte[] data ) throws IOException {
		return readImageFile( imageName, filePath, data, 0, data.length );
	}
	
	int readImageFile( String imageName, String filePath, byte[] data, long offset, int length ) throws IOException;
	
	void destroy() throws IOException;
}
