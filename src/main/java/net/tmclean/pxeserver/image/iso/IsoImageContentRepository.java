package net.tmclean.pxeserver.image.iso;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import com.github.stephenc.javaisotools.loopfs.iso9660.Iso9660FileEntry;
import com.github.stephenc.javaisotools.loopfs.iso9660.Iso9660FileSystem;

import net.tmclean.pxeserver.image.Image;
import net.tmclean.pxeserver.image.ImageContentRepository;
import net.tmclean.pxeserver.image.ImageFileEntry;

public class IsoImageContentRepository implements ImageContentRepository {

	private final Map<String, Iso9660FileSystem>             imagesIsoFsMap      = new ConcurrentHashMap<>();
	private final Map<String, Map<String, Iso9660FileEntry>> imagesToIsoContents = new ConcurrentHashMap<>();

	private final Map<String, Map<String, Long>> imageFilenameToId = new ConcurrentHashMap<>();
	private final Map<String, Map<Long, String>> imageIdToFilename = new ConcurrentHashMap<>();
	
	@Override
	public void initImage( Image image ) throws IOException {

		String imageName = image.getName();
		
		File isoFile = new File( image.getLocation() );
		Iso9660FileSystem isoFs = new Iso9660FileSystem( isoFile, true );
		imagesIsoFsMap.put( imageName, isoFs );

		Map<String, Long> filenameToId    = new ConcurrentHashMap<>();
		Map<Long, String> idToFilename    = new ConcurrentHashMap<>();
		Map<String, Iso9660FileEntry> toc = new ConcurrentHashMap<>();
		
		imagesToIsoContents.put( imageName, toc );
		
		this.imageFilenameToId.put( imageName, filenameToId );
		this.imageIdToFilename.put( imageName, idToFilename );

		AtomicLong i = new AtomicLong( 0 );
		isoFs.forEach( e -> {
			String path = e.getPath();
			
			if( path.endsWith( "/" ) ) {
				path = path.substring( 0, path.length()-1 );
			}
			
			toc.put( path, e );
			
			long fileId = i.incrementAndGet() | image.getId();
			System.out.println( path + " ::: " + String.format( "%016x", fileId ) );
			
			filenameToId.put( path,   fileId );
			idToFilename.put( fileId, path   );
		});
	}
	

	@Override
	public long filePathToId( Image image, String filePath ) throws IOException {
		if( !imageFilePathExists( image, filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + image.getName() );
		}
		
		return this.imageFilenameToId.get( image.getName() ).get( filePath ).longValue();
	}

	@Override
	public String idToFilePath( Image image, long id ) throws IOException {
		return this.imageIdToFilename.get( image.getName() ).get( id );
	}

	@Override
	public boolean imageFilePathExists( Image image, String filePath ) throws IOException {
		if( !this.imagesToIsoContents.containsKey( image.getName() ) ) {
			return false;
		}
		return this.imagesToIsoContents.get( image.getName() ).containsKey( filePath );
	}
	
	@Override
	public ImageFileEntry getFileEntry( Image image, String filePath ) throws IOException {
		if( !imageFilePathExists( image, filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + image.getName() );
		}
		
		Iso9660FileEntry entry = this.imagesToIsoContents.get( image.getName() ).get( filePath );

		ImageFileEntry result = new ImageFileEntry();
		result.setName( entry.getName() );
		result.setPath( entry.getPath() );
		result.setLength( entry.getSize() );
		result.setLastModified( entry.getLastModifiedTime() );
		result.setDirectory( entry.isDirectory() );
		
		return result;
	}

	@Override
	public long getImageFileSize( Image image, String filePath ) throws IOException {
		if( !imageFilePathExists( image, filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + image.getName() );
		}
		
		
		return imagesToIsoContents.get( image.getName() ).get( filePath ).getSize();
	}

	@Override
	public int readImageFile( Image image, String filePath, byte[] data, int offset, int length ) throws IOException {
		if( !imageFilePathExists( image, filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + image.getName() );
		}
		
		Iso9660FileEntry entry = this.imagesToIsoContents.get( image.getName() ).get( filePath );
		Iso9660FileSystem fs = this.imagesIsoFsMap.get( image.getName() );
		
		try( InputStream is = fs.getInputStream( entry ) ) {
			is.skip( offset );
			return is.read( data );
		}
	}

	@Override
	public List<String> listImagePath( Image image, String filePath ) throws IOException {
		
		if( !this.imagesToIsoContents.containsKey( image.getName() ) ) {
			throw new IOException();
		}
		
		 Set<String> paths = this.imagesToIsoContents.get( image.getName() ).keySet();
		 List<String> result = paths.stream().collect( Collectors.toList() );
		 
		return result
			.stream()
			.filter( p -> matchesPrefix( p, filePath ) )
			.collect( Collectors.toList() );
	}
	
	@Override
	public void destroy() {
		this.imagesIsoFsMap.values().stream().forEach( fs -> {
			try {
				fs.close();
			}
			catch( IOException e ) {
				e.printStackTrace();
			}
		});
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
