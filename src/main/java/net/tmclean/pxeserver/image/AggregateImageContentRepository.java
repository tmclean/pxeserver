package net.tmclean.pxeserver.image;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.tmclean.pxeserver.image.dir.DirectoryImageContentRepository;
import net.tmclean.pxeserver.image.iso.IsoImageContentRepository;

public class AggregateImageContentRepository implements ImageContentRepository {

	private Map<ImageFormat, ImageContentRepository> repoMap = new ConcurrentHashMap<>();
	
	public AggregateImageContentRepository() {
		repoMap.put( ImageFormat.LOCAL_ISO, new IsoImageContentRepository() );
		repoMap.put( ImageFormat.LOCAL_DIR, new DirectoryImageContentRepository() );
	}
	
	private ImageContentRepository getRepo( Image image ) {
		if( !repoMap.containsKey( image.getFormat() ) ) {
			throw new IllegalArgumentException( "Unsupported image format " + image.getFormat() );
		}
		
		return repoMap.get( image.getFormat() );
	}
	
	@Override
	public void initImage( Image image ) throws IOException {
		getRepo( image ).initImage( image );
	}

	@Override
	public long getImageFileSize( Image image, String filePath ) throws IOException {
		return getRepo( image ).getImageFileSize( image, filePath );
	}

	@Override
	public boolean imageFilePathExists( Image image, String filePath ) throws IOException {
		return getRepo( image ).imageFilePathExists( image, filePath );
	}

	@Override
	public ImageFileEntry getFileEntry( Image image, String filePath ) throws IOException {
		return getRepo( image ).getFileEntry( image, filePath );
	}

	@Override
	public long filePathToId( Image image, String filePath ) throws IOException {
		return getRepo( image ).filePathToId( image, filePath );
	}

	@Override
	public String idToFilePath( Image image, long id ) throws IOException {
		return getRepo( image ).idToFilePath( image, id );
	}

	@Override
	public List<String> listImagePath( Image image, String filePath ) throws IOException {
		return getRepo( image ).listImagePath( image, filePath );
	}

	@Override
	public int readImageFile( Image image, String filePath, byte[] data, int offset, int length ) throws IOException {
		return getRepo( image ).readImageFile( image, filePath, data, offset, length );
	}

	@Override
	public void destroy() {
		this.repoMap.values().stream().forEach( ImageContentRepository::destroy );
	}

}
