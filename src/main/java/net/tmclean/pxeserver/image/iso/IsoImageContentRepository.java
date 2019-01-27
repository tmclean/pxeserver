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

	private final Image image;
	
	private Iso9660FileSystem isoFs = null;
	private Map<String, Iso9660FileEntry> tableOfContents = null;
	private Map<String, Long> filenameToId = null;
	private Map<Long, String> idToFilename = null;
	
	public IsoImageContentRepository( Image image ) {
		this.image = image;
	}
	
	@Override
	public void init() throws IOException {

		File isoFile = new File( image.getLocation() );
		
		this.isoFs           = new Iso9660FileSystem( isoFile, true );
		this.filenameToId    = new ConcurrentHashMap<>();
		this.idToFilename    = new ConcurrentHashMap<>();
		this.tableOfContents = new ConcurrentHashMap<>();

		AtomicLong i = new AtomicLong( 0 );
		isoFs.forEach( e -> {
			String path = e.getPath();
			
			if( path.endsWith( "/" ) ) {
				path = path.substring( 0, path.length()-1 );
			}
			
			tableOfContents.put( path, e );
			
			long fileId = i.incrementAndGet() | image.getId();
			System.out.println( path + " ::: " + String.format( "%016x", fileId ) );
			
			filenameToId.put( path,   fileId );
			idToFilename.put( fileId, path   );
		});
	}

	@Override
	public void destroy() throws IOException {
		this.isoFs.close();
	}
	

	@Override
	public long filePathToId( String filePath ) throws IOException {
		if( !filePathExists( filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + image.getName() );
		}
		
		return this.filenameToId.get( filePath ).longValue();
	}

	@Override
	public String idToFilePath( long id ) throws IOException {
		return this.idToFilename.get( id );
	}

	@Override
	public boolean filePathExists( String filePath ) throws IOException {
		return this.tableOfContents.containsKey( filePath );
	}
	
	@Override
	public ImageFileEntry getFileEntry( String filePath ) throws IOException {
		if( !filePathExists( filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + image.getName() );
		}
		
		Iso9660FileEntry entry = this.tableOfContents.get( filePath );

		ImageFileEntry result = new ImageFileEntry();
		result.setName( entry.getName() );
		result.setPath( entry.getPath() );
		result.setLength( entry.getSize() );
		result.setLastModified( entry.getLastModifiedTime() );
		result.setDirectory( entry.isDirectory() );
		
		return result;
	}

	@Override
	public long getFileSize( String filePath ) throws IOException {
		if( !filePathExists( filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + image.getName() );
		}
		
		
		return tableOfContents.get( filePath ).getSize();
	}

	@Override
	public int readFile( String filePath, byte[] data, int offset, int length ) throws IOException {
		if( !filePathExists( filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + image.getName() );
		}
		
		Iso9660FileEntry entry = this.tableOfContents.get( filePath );
		
		try( InputStream is = this.isoFs.getInputStream( entry ) ) {
			is.skip( offset );
			return is.read( data );
		}
	}

	@Override
	public List<String> listPath( String filePath ) throws IOException {

		 Set<String> paths = this.tableOfContents.keySet();
		 List<String> result = paths.stream().collect( Collectors.toList() );
		 
		return result
			.stream()
			.filter( p -> matchesPrefix( p, filePath ) )
			.collect( Collectors.toList() );
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
