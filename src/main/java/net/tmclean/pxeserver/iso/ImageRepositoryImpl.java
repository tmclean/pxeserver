package net.tmclean.pxeserver.iso;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.github.stephenc.javaisotools.loopfs.iso9660.Iso9660FileEntry;
import com.github.stephenc.javaisotools.loopfs.iso9660.Iso9660FileSystem;

public class ImageRepositoryImpl implements ImageRepository {

	private static final String ISO_NAME = "debian-9-5-0";
	private static final String ISO_FILE = "C:/users/tjrag/desktop/debian-9.5.0-amd64-netinst.iso";

	private final List<Image> images = new ArrayList<>();
	
	private final Map<Long,   Image> idToImageMap   = new ConcurrentHashMap<>();
	private final Map<String, Image> nameToImageMap = new ConcurrentHashMap<>();
	
	private final Map<String, Iso9660FileSystem>             imagesIsoFsMap      = new ConcurrentHashMap<>();
	private final Map<String, Map<String, Iso9660FileEntry>> imagesToIsoContents = new ConcurrentHashMap<>();

	private final Map<String, Map<String, Long>> imageFilenameToId = new ConcurrentHashMap<>();
	private final Map<String, Map<Long, String>> imageIdToFilename = new ConcurrentHashMap<>();
	
	public ImageRepositoryImpl() throws IOException {

		long imageId = 1L << 24;
		
		Image debian950 = new Image();
		debian950.setName( "debian-9-5-0" );
		debian950.setDescription( "Debian Stretch 9.5.0 ISO" );
		debian950.setId( imageId );
		
		images.add( debian950 );
		idToImageMap.put( debian950.getId(), debian950 );
		nameToImageMap.put( debian950.getName(), debian950 );
		
		File isoFile = new File( ISO_FILE );
		Iso9660FileSystem isoFs = new Iso9660FileSystem( isoFile, true );
		imagesIsoFsMap.put( ISO_NAME, isoFs );

		Map<String, Long> filenameToId    = new ConcurrentHashMap<>();
		Map<Long, String> idToFilename    = new ConcurrentHashMap<>();
		Map<String, Iso9660FileEntry> toc = new ConcurrentHashMap<>();
		
		imagesToIsoContents.put( ISO_NAME, toc );
		
		this.imageFilenameToId.put( ISO_NAME, filenameToId );
		this.imageIdToFilename.put( ISO_NAME, idToFilename );

		AtomicLong i = new AtomicLong( 0 );
		isoFs.forEach( e -> {
			String path = e.getPath();
			
			if( path.endsWith( "/" ) ) {
				path = path.substring( 0, path.length()-1 );
			}
			
			toc.put( path, e );
			
			long fileId = i.incrementAndGet() | imageId;
			System.out.println( path + " ::: " + String.format( "%016x", fileId ) );
			
			filenameToId.put( path,   fileId );
			idToFilename.put( fileId, path   );
		});
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
	public long filePathToId( String imageName, String filePath ) throws IOException {
		if( !imageFilePathExists( imageName, filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + imageName );
		}
		
		return this.imageFilenameToId.get( imageName ).get( filePath ).longValue();
	}

	@Override
	public String idToFilePath( long id ) throws IOException {
		long imgId  = (id >> 24 ) << 24;
		Image image = this.idToImageMap.get( imgId );
		
		return this.imageIdToFilename.get( image.getName() ).get( id );
	}

	public boolean imageExists( String imageName ) {
		return this.nameToImageMap.containsKey( imageName );
	}
	
	@Override
	public boolean imageFilePathExists( String imageName, String filePath ) throws IOException {
		if( !imageExists( imageName ) ) {
			throw new IOException( "Unknown image " + imageName );
		}
		
		return this.imagesToIsoContents.get( imageName ).containsKey( filePath );
	}
	
	@Override
	public Iso9660FileEntry getFileEntry( String imageName, String filePath ) throws IOException {
		if( !imageFilePathExists( imageName, filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + imageName );
		}
		
		return this.imagesToIsoContents.get( imageName ).get( filePath );
	}

	@Override
	public int getImageFileSize( String imageName, String filePath ) throws IOException {
		if( !imageFilePathExists( imageName, filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + imageName );
		}
		
		
		return (int) imagesToIsoContents.get( imageName ).get( filePath ).getSize();
	}

	@Override
	public int readImageFile( String imageName, String filePath, byte[] data, long offset, int length ) throws IOException {
		if( !imageFilePathExists( imageName, filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + imageName );
		}
		
		Iso9660FileEntry entry = this.imagesToIsoContents.get( imageName ).get( filePath );
		Iso9660FileSystem fs = this.imagesIsoFsMap.get( imageName );
		
		try( InputStream is = fs.getInputStream( entry ) ) {
			is.skip( offset );
			return is.read( data );
		}
	}
	
	@Override
	public void destroy() throws IOException {
		this.imagesIsoFsMap.values().stream().forEach( fs -> {
			try {
				fs.close();
			}
			catch( IOException e ) {
				e.printStackTrace();
			}
		});
	}

	@Override
	public List<String> listImagePath( String imageName, String filePath ) throws IOException {
		 Set<String> paths = this.imagesToIsoContents.get( imageName ).keySet();
		 List<String> result = paths.stream().collect( Collectors.toList() );
		 
		return result
			.stream()
			.filter( p -> matchesPrefix( p, filePath ) )
			.collect( Collectors.toList() );
	}
	
	@Override
	public boolean isImageRootId( long id ) throws IOException {
    	return (id & 0x00ffffffL) == 0;
	}
	
	@Override
	public long imageFileIdToImageId(long id) throws IOException {
    	return id & 0xff000000L;
    }
	
	private boolean matchesPrefix( String actualPath, String prefix ) {
		if( prefix != null && "/".equals( prefix ) ) {
			prefix = "";
		}
		
		if( prefix == null || "".equals( prefix.trim() ) ) {
			return !actualPath.trim().isEmpty() && !actualPath.contains( "/" );
		}
		else if( !actualPath.equals( prefix ) && actualPath.indexOf( '/', prefix.length() + 1 ) < 0 ) {
			return  actualPath.trim().startsWith( prefix.trim() );
		}
		
		return false;
	}

}
