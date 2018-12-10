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

	private final Map<String, File>              imagesFsDirMap      = new ConcurrentHashMap<>();
	private final Map<String, Map<String, File>> imagesToDirContents = new ConcurrentHashMap<>();

	private final Map<String, Map<String, Long>> imageFilenameToId = new ConcurrentHashMap<>();
	private final Map<String, Map<Long, String>> imageIdToFilename = new ConcurrentHashMap<>();
	
	@Override
	public void initImage( Image image ) throws IOException {
		String imageName = image.getName();
		
		File baseDir = new File( image.getLocation() );
		imagesFsDirMap.put( imageName, baseDir );

		Map<String, Long> filenameToId = new ConcurrentHashMap<>();
		Map<Long, String> idToFilename = new ConcurrentHashMap<>();
		Map<String, File> toc          = new ConcurrentHashMap<>();
		
		imagesToDirContents.put( imageName, toc );
		
		this.imageFilenameToId.put( imageName, filenameToId );
		this.imageIdToFilename.put( imageName, idToFilename );

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
			
			toc.put( path, new File( baseDir, path ) );
			
			long fileId = i.incrementAndGet() | image.getId();
			System.out.println( path + " ::: " + String.format( "%016x", fileId ) );
			
			filenameToId.put( path,   fileId );
			idToFilename.put( fileId, path   );
		});
	}

	@Override
	public long getImageFileSize( Image image, String filePath ) throws IOException {
		if( !imageFilePathExists( image, filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + image.getName() );
		}
		
		return this.imagesToDirContents.get( image.getName() ).get( filePath ).length();
	}

	@Override
	public boolean imageFilePathExists( Image image, String filePath ) throws IOException {
		if( !this.imagesToDirContents.containsKey( image.getName() ) ) {
			throw new IOException( "File " + filePath + " not found in image " + image.getName() );
		}
		
		return this.imagesToDirContents.get( image.getName() ).containsKey( filePath );
	}

	@Override
	public long filePathToId( Image image, String filePath ) throws IOException {
		if( !imageFilePathExists( image, filePath ) ) {
			throw new IOException( "File " + filePath + " not found in image " + image.getName() );
		}
		
		return this.imageFilenameToId.get( image.getName() ).get( filePath );
	}

	@Override
	public String idToFilePath( Image image, long id ) throws IOException {
		return this.imageIdToFilename.get( image.getName() ).get( id );
	}

	@Override
	public int readImageFile( Image image, String filePath, byte[] data, int offset, int length ) throws IOException {
		File file = this.imagesToDirContents.get( image.getName() ).get( filePath );
		try( InputStream is = new FileInputStream( file ) ){
			is.skip( offset );
			return is.read( data );
		}
	}

	@Override
	public ImageFileEntry getFileEntry( Image image, String filePath ) throws IOException {

		File file = this.imagesToDirContents.get( image.getName() ).get( filePath );
		
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
	public List<String> listImagePath( Image image, String filePath ) throws IOException {
		if( !this.imagesToDirContents.containsKey( image.getName() ) ) {
			throw new IOException();
		}
		
		 Set<String> paths = this.imagesToDirContents.get( image.getName() ).keySet();
		 List<String> result = paths.stream().collect( Collectors.toList() );
		 
		return result
			.stream()
			.filter( p -> matchesPrefix( p, filePath ) )
			.collect( Collectors.toList() );
	}

	@Override
	public void destroy() {}

	
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
