package net.tmclean.pxeserver.image.dir;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import net.tmclean.pxeserver.image.Image;
import net.tmclean.pxeserver.image.ImageContentRepository;
import net.tmclean.pxeserver.image.ImageFileEntry;

public class DirectoryImageContentRepository implements ImageContentRepository {

	private final Image image;
	
	private File baseDir;
	
	private Map<String, File> tableOfContents = null;
	private Map<String, Long> filenameToId = null;
	private Map<Long, String> idToFilename = null;
	
	public DirectoryImageContentRepository( Image image ) throws IOException {
		this.image = image;
	}
	
	public void init() throws IOException {
		
		this.baseDir = new File( image.getLocation() );

		this.filenameToId    = new ConcurrentHashMap<>();
		this.idToFilename    = new ConcurrentHashMap<>();
		this.tableOfContents = new ConcurrentHashMap<>();

		AtomicLong i = new AtomicLong( 0 );
		
		Files.walk( baseDir.toPath(), FileVisitOption.FOLLOW_LINKS ).forEach( e -> {
			String path = e.toString();
			path = path.replace( '\\', '/' );
			path = path.replace( baseDir.getAbsolutePath().replace( '\\', '/' ), "" );
			
			if( path.endsWith( "/" ) ) {
				path = path.substring( 0, path.length()-1 );
			}
			
			if( path.startsWith( "/" ) ) {
				path = path.substring( 1 );
			}
			
			if( path.trim().isEmpty() ) {
				return;
			}
			
			this.tableOfContents.put( path, new File( baseDir, path ) );
			
			long fileId = i.incrementAndGet() | image.getId();
			System.out.println( path + " ::: " + String.format( "%016x", fileId ) );
			
			filenameToId.put( path,   fileId );
			idToFilename.put( fileId, path   );
		});
	}

	@Override
	public void destroy() {}
	
	@Override
	public long getFileSize( String filePath ) throws IOException {
		return this.tableOfContents.get( filePath ).length();
	}

	@Override
	public boolean filePathExists( String filePath ) throws IOException {
		
		return this.tableOfContents.containsKey( filePath );
	}

	@Override
	public long filePathToId( String filePath ) throws IOException {
		if( !filePathExists( filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + image.getName() );
		}
		
		return this.filenameToId.get( filePath );
	}

	@Override
	public String idToFilePath( long id ) throws IOException {
		return this.idToFilename.get( id );
	}

	@Override
	public int readFile( String filePath, byte[] data, int offset, int length ) throws IOException {
		File file = this.tableOfContents.get( filePath );
		try( InputStream is = new FileInputStream( file ) ){
			is.skip( offset );
			return is.read( data );
		}
	}

	@Override
	public ImageFileEntry getFileEntry( String filePath ) throws IOException {

		File file = this.tableOfContents.get( filePath );
		
		if( file == null ) {
			throw new IOException( "Failed to locate file " + filePath + " in image " + image.getName() );
		}
		
		ImageFileEntry result = new ImageFileEntry();
		result.setName( file.getName() );
		result.setPath( filePath );
		result.setLength( file.length() );
		result.setLastModified( file.lastModified() );
		result.setDirectory( file.isDirectory() );
		
		return result;
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
