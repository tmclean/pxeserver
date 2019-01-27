package net.tmclean.pxeserver.image.aggregate;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import net.tmclean.pxeserver.image.Image;
import net.tmclean.pxeserver.image.ImageContentRepository;
import net.tmclean.pxeserver.image.ImageFileEntry;
import net.tmclean.pxeserver.image.ImageFormat;
import net.tmclean.pxeserver.image.ImageRepository;
import net.tmclean.pxeserver.image.dir.DirectoryImageContentRepository;
import net.tmclean.pxeserver.image.iso.IsoImageContentRepository;

@Component
public class AggregateImageContentRepository implements ImageContentDirectory {

	private final ImageRepository imageRepo;
	
	private Map<String, ImageContentRepository> repoMap = new ConcurrentHashMap<>();
	
	public AggregateImageContentRepository( ImageRepository imageRepo ) {
		this.imageRepo = imageRepo;
	}
	
	@Override
	@PostConstruct
	public void init() throws IOException {
		for( Image image : imageRepo.getAllImages() ) {
			this.initImage( image );
		}
	}

	@Override
	@PreDestroy
	public void destroy() throws IOException {
		for( ImageContentRepository repo : this.repoMap.values() ) {
			repo.destroy();
		}
	}
	
	private void initImage( Image image ) throws IOException {		
		if( this.repoMap.containsKey( image.getName() ) ) {
			throw new IllegalArgumentException( "Image " + image.getName() + " already exists" );
		}
		
		ImageContentRepository contentRepo = null;
		
		if( image.getFormat() == ImageFormat.LOCAL_DIR ) {
			contentRepo = new DirectoryImageContentRepository( image );
		}
		else if( image.getFormat() == ImageFormat.LOCAL_ISO ) {
			contentRepo = new IsoImageContentRepository( image );
		}
		else {
			throw new IllegalArgumentException( "Image " + image.getName() + " is of unsupported format " + image.getFormat() );
		}
		
		contentRepo.init();
		this.repoMap.put( image.getName(), contentRepo );
	}

	private ImageContentRepository getRepo( Image image ) {
		if( !repoMap.containsKey( image.getName() ) ) {
			throw new IllegalArgumentException( "Unknown image  " + image.getName() );
		}
		
		return repoMap.get( image.getName() );
	}
	
	@Override
	public long getImageFileSize( Image image, String filePath ) throws IOException {
		return getRepo( image ).getFileSize( filePath );
	}

	@Override
	public boolean imageFilePathExists( Image image, String filePath ) throws IOException {
		return getRepo( image ).filePathExists( filePath );
	}

	@Override
	public ImageFileEntry getFileEntry( Image image, String filePath ) throws IOException {
		return getRepo( image ).getFileEntry( filePath );
	}

	@Override
	public long filePathToId( Image image, String filePath ) throws IOException {
		return getRepo( image ).filePathToId( filePath );
	}

	@Override
	public String idToFilePath( Image image, long id ) throws IOException {
		return getRepo( image ).idToFilePath( id );
	}

	@Override
	public List<String> listImagePath( Image image, String filePath ) throws IOException {
		return getRepo( image ).listPath( filePath );
	}

	@Override
	public int readImageFile( Image image, String filePath, byte[] data, int offset, int length ) throws IOException {
		return getRepo( image ).readFile( filePath, data, offset, length );
	}
}
