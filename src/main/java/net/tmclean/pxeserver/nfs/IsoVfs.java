package net.tmclean.pxeserver.nfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.security.auth.Subject;

import org.dcache.nfs.status.NoEntException;
import org.dcache.nfs.v4.NfsIdMapping;
import org.dcache.nfs.v4.SimpleIdMap;
import org.dcache.nfs.v4.xdr.nfsace4;
import org.dcache.nfs.vfs.AclCheckable;
import org.dcache.nfs.vfs.DirectoryEntry;
import org.dcache.nfs.vfs.DirectoryStream;
import org.dcache.nfs.vfs.FsStat;
import org.dcache.nfs.vfs.Inode;
import org.dcache.nfs.vfs.Stat;
import org.dcache.nfs.vfs.VirtualFileSystem;
import org.springframework.stereotype.Component;
import org.dcache.nfs.vfs.Stat.Type;

import com.google.common.primitives.Longs;

import net.tmclean.pxeserver.image.Image;
import net.tmclean.pxeserver.image.ImageFileEntry;
import net.tmclean.pxeserver.image.ImageRepository;
import net.tmclean.pxeserver.image.aggregate.ImageContentDirectory;

@Component
public class IsoVfs implements VirtualFileSystem {

	private static final long ROOT_INODE = 1L;
	
    private final NfsIdMapping _idMapper = new SimpleIdMap();

    private final ImageRepository imageRepository;
    private final ImageContentDirectory contentDirectory;
    
    public IsoVfs( ImageRepository imageRepository, ImageContentDirectory contentDirectory ) {
    	this.imageRepository = imageRepository;
    	this.contentDirectory = contentDirectory;
	}

	@Override
	public FsStat getFsStat() throws IOException {
		return new FsStat( 0, -1, 0, -1 );
	}

	@Override
	public Inode lookup( Inode parent, String path ) throws IOException {
		System.out.println( "Looking up path " + path );
        
		long   parentInode = getInodeNumber( parent );
        String parentPath  = resolveInode( parentInode );
        
        String childPath   = parentPath.isEmpty() ? path : parentPath + "/" + path;
        long   childInode  = resolvePath( childPath );
        
        return toFh( childInode );
	}
		
	@Override
	public DirectoryStream list( Inode inode, byte[] verifier, long l ) throws IOException {

        long inodeNumber = getInodeNumber( inode );
        
        long cookie = 2;
        final List<DirectoryEntry> list = new ArrayList<>();
        
        System.out.println( "Listing dir children for inode " + String.format( "%016x", inodeNumber ) );
        
        if( inodeNumber == ROOT_INODE ) {    
        	
        	for( Image image : this.imageRepository.getAllImages() ) {
        		if( image.isRoot() ) {
                    for( String p : this.contentDirectory.listImagePath( image, "/" ) ) {
            			if( cookie++ > l ) {
            		        list.add( imagePathToDirEntry( p, inodeNumber, cookie ) );
            			}
                    }
        		}
        		else {
	    			if( cookie++ > l ) {
	    		        list.add( imageNameToDirEntry( image.getName(), inodeNumber, cookie ) );
	    			}
        		}
        	}
        }
        else {
        	Image image = null;
        	String filePath  = "";
        	String resolvedPath = resolveInode( inodeNumber );
        	
        	if( this.contentDirectory.isImageRootId( inodeNumber ) ) {
        		image = this.imageRepository.getImage( resolvedPath );
                System.out.println( "Getting root for image named " + image.getName() );
        	}
        	else {
        		String imageName = resolvedPath.substring( 0, resolvedPath.indexOf( '/' ) );
        		image    = this.imageRepository.getImage( imageName );
        		filePath = resolvedPath.substring( resolvedPath.indexOf( "/" ) + 1 );
        	}
        	
        	if( image == null ) { 
        		throw new NoEntException( "Failed to locate image for path " + resolvedPath );
        	}

            System.out.println( "Getting path " + filePath + " for image named " + image.getName() );
            
            for( String p : this.contentDirectory.listImagePath( image, filePath ) ) {
            	System.out.println( "   Found path " + p + " under dir " + filePath + " in image " + image.getName() );
    			cookie++;
    			if( cookie > l ) {
    		        list.add( imagePathToDirEntry( (image.isRoot() ? "" : image.getName()) + "/" + p, inodeNumber, cookie ) );
    			}
            }
        }

        return new DirectoryStream( verifier, list );
	}

	@Override
	public Inode parentOf( Inode inode ) throws IOException {
        long inodeNumber = getInodeNumber( inode );
        
        if( inodeNumber == 1 ) {
            throw new NoEntException("no parent"); //its the root
        }
        
        String path        = resolveInode( inodeNumber );
        String parentPath  = getParent( path );
        long   parentInode = resolvePath( parentPath );
        return toFh( parentInode );
	}
	
	private String getParent( String path ) {
		if( path.isEmpty() || "/".equals( path ) ) {
			return "";
		}
		
		return path.substring( 0, path.lastIndexOf( "/" )-1 );
	}

	@Override
	public int read( Inode inode, byte[] data, long offset, int count ) throws IOException {
        long inodeNo = getInodeNumber( inode );
        
        String path      = resolveInode( inodeNo );
		String imageName = path.substring( 0, path.indexOf( '/' ) );
		String filePath  = path.substring( path.indexOf( "/" )+1 );
    	
		Image image = this.imageRepository.getImage( imageName );
		
        return this.contentDirectory.readImageFile( image, filePath, data, (int)offset, count );
	}

	@Override
	public Stat getattr( Inode inode ) throws IOException {
		long inodeNo = getInodeNumber( inode );
		String path = resolveInode( inodeNo );
		return statPath( path, inodeNo );
	}

	@Override
	public Inode getRootInode() throws IOException {
		return toFh( ROOT_INODE );
	}

	@Override
	public byte[] directoryVerifier(Inode inode) throws IOException {
		 return DirectoryStream.ZERO_VERIFIER;
	}

	@Override
	public AclCheckable getAclCheckable() {
		return AclCheckable.UNDEFINED_ALL;
	}

	@Override
	public NfsIdMapping getIdMapper() {
		 return _idMapper;
	}
	
	@Override
	public int access( Inode inode, int mode ) throws IOException { return mode; }

	@Override
	public Inode create(Inode parent, Type type, String name, Subject subject, int mode) throws IOException { throw new IOException(); }

	@Override
	public Inode link( Inode parent, Inode link, String name, Subject subject ) throws IOException { throw new IOException(); }
	
	@Override
	public Inode mkdir(Inode parent, String name, Subject subject, int mode) throws IOException { throw new IOException(); }
	
	@Override
	public boolean move( Inode src, String oldName, Inode dest, String newName ) throws IOException { throw new IOException(); }
	
	@Override
	public String readlink(Inode inode) throws IOException { throw new IOException(); }

	@Override
	public void remove(Inode parent, String name) throws IOException { throw new IOException(); }

	@Override
	public Inode symlink(Inode parent, String name, String link, Subject subject, int mode) throws IOException { throw new IOException(); }

	@Override
	public WriteResult write(Inode inode, byte[] data, long offset, int count, StabilityLevel stabilityLevel) throws IOException { throw new IOException(); }

	@Override
	public void commit(Inode inode, long offset, int count) throws IOException { throw new IOException(); }

	@Override
	public void setattr(Inode inode, Stat stat) throws IOException { throw new IOException(); }

	@Override
	public nfsace4[] getAcl(Inode inode) throws IOException { return new nfsace4[0]; }

	@Override
	public void setAcl(Inode inode, nfsace4[] acl) throws IOException {}

	@Override
	public boolean hasIOLayout(Inode inode) throws IOException { return false; }

    private Stat statPath( String path, long inodeNumber ) throws IOException {

        Stat stat = new Stat();
        
        System.out.println( "Stating path " + path + " with inode " + String.format( "%016x", inodeNumber ) );
        
    	if( inodeNumber == ROOT_INODE ||				// Matches root inode constant 
    		path == null || path.trim().isEmpty() || 	// Is null or empty, implying root
    		"/".equals( path.trim() )   				// Is explicitly the root path
    		//!path.contains( "/" ) 						// The path contains no separators, implying the root of an images
    	) {
    		//
    		// This path or inode is a virtual/bogus directory
    		//
            stat.setMode( Stat.S_IFDIR | 0707 );
            stat.setATime( 0L );
            stat.setCTime( 0L );
            stat.setMTime( 0L );
            stat.setSize( 0 );
            stat.setGeneration( 0L );
    	}
    	else if( !path.contains( "/" ) ) {
    		Image image = getImage( path );
    		if( image == null ) {
    			image = getImage( "/" );
        		ImageFileEntry entry = this.contentDirectory.getFileEntry( image, path );
        		
        		System.out.println( path + " is a root " + (entry.isDirectory() ? "dir" : "file ") );
        		
                stat.setMode( entry.isDirectory() ? 0x4707 : 0x8707 );
                stat.setATime( entry.getLastModified() );
                stat.setCTime( entry.getLastModified() );
                stat.setMTime( entry.getLastModified() );
                stat.setSize( entry.getLength() );
                stat.setGeneration( entry.getLastModified() );
    		}
    		else {
                stat.setMode( Stat.S_IFDIR | 0707 );
                stat.setATime( 0L );
                stat.setCTime( 0L );
                stat.setMTime( 0L );
                stat.setSize( 0 );
                stat.setGeneration( 0L );
    		}
    	}
    	else {
    		String imageName = path.substring( 0, path.indexOf( '/' ) );
    		String filePath  = path.substring( path.indexOf( "/" )+1 );

    		Image image = this.imageRepository.getImage( imageName );
    		
    		ImageFileEntry entry = null;
    		if( image != null ) {
    			entry = this.contentDirectory.getFileEntry( image, filePath );	
    		}
    		else {
    			image = getImage( "/" );
    			entry = this.contentDirectory.getFileEntry( image, filePath );
    		}
    		
            stat.setMode( entry.isDirectory() ? 0x4707 : 0x8707 );
            stat.setATime( entry.getLastModified() );
            stat.setCTime( entry.getLastModified() );
            stat.setMTime( entry.getLastModified() );
            stat.setSize( entry.getLength() );
            stat.setGeneration( entry.getLastModified() );
    	}

        stat.setGid( 0 );
        stat.setUid( 0 );
        stat.setNlink( 1 );
        stat.setDev( 17 );
        stat.setIno( (int) inodeNumber );
        stat.setRdev( 17 );
        stat.setFileid( (int) inodeNumber );

        return stat;
    }
    
    private String resolveInode( long inodeNumber ) throws IOException  {
    	
    	System.out.println( "Resolving inode " + String.format( "%016x", inodeNumber ) );
    	
    	if( inodeNumber == ROOT_INODE ) {
    		return "";
    	}
    	else if( this.contentDirectory.isImageRootId( inodeNumber ) ) {
    		return getImage( inodeNumber ).getName();
    	}
    	else {
    		long imageId = this.contentDirectory.imageFileIdToImageId( inodeNumber );
    		Image image = getImage( imageId );
    		
	    	String imageName = image.isRoot() ? "" : image.getName();
	    	String filePath  = this.contentDirectory.idToFilePath( image, inodeNumber );
    	
	    	return imageName + "/" + filePath;
    	}
    }
    
    private Image getImage( long id ) throws NoEntException {
		Image image = imageRepository.getImage( id );
		if( image != null ) {
			return image;
		}

		throw new NoEntException( "Failed to locate image with ID " + id );
    }
    
    private Image getImage( String name ) throws NoEntException {
		Image image = imageRepository.getImage( name );
		if( image != null ) {
			return image;
		}

		return null;
//		throw new NoEntException( "Failed to locate image with name " + name );
    }
    
    private long resolvePath( String path ) throws IOException {

    	System.out.println( path );

		try {	
	    	if( "".equals( path ) || "/".equals( path ) ) {
	    		System.out.println( "The path " + path + " is the root path" );
	    		return ROOT_INODE;
	    	}
	    	else if( !path.contains( "/" ) ) {
	    		System.out.println( "The path " + path + " may be an image name" );
	    		Image image = getImage( path );
	    		
	    		if( image != null ) {
		    		System.out.println( "   The path " + path + " is an image name" );
		    		return getImage( path ).getId(); 
	    		}
	    		else {
		    		System.out.println( "   The path " + path + " is probably a child of the root image" );
	    			image = getImage( "/" );
	    			return this.contentDirectory.filePathToId( image, path );
	    		}
	    	}
	    	else {
	    		System.out.println( "The path " + path + " is complicated and may be either a child of a root or non-root image" );
	    		
				String imageName = path.substring( 0, path.indexOf( '/' ) );
				String filePath  = path.substring( path.indexOf( "/" )+1 );
				
				System.out.println( "resolvePath :: Image Name " + imageName );
				
				Image image = this.imageRepository.getImage( imageName );
				
				if( image != null ) {
					return this.contentDirectory.filePathToId( image, filePath );
				}
				else {
	    			image = getImage( "/" );
	    			return this.contentDirectory.filePathToId( image, path );
				}
	    	}
    	}
		catch( IOException e ) {
			throw new NoEntException( "Failed to resolve path " + path, e );
		}
    }


	private DirectoryEntry imageNameToDirEntry( String imageName, long inodeNumber, long n ) throws IOException {
		Image image = getImage( imageName );
		long ino = image.getId();
		
		long time = System.currentTimeMillis();
		
        Stat stat = new Stat();
        stat.setMode( Stat.S_IFDIR | 0777 );
        stat.setATime( time );
        stat.setCTime( time );
        stat.setMTime( time );
        stat.setSize( 0 );	
        stat.setGeneration( time );
        stat.setGid( 0 );
        stat.setUid( 0 );
        stat.setNlink( 1 );
        stat.setDev( 17 );
        stat.setIno( (int) inodeNumber );
        stat.setRdev( 17 );
        stat.setFileid( (int) inodeNumber );
        
        return new DirectoryEntry( imageName, toFh( ino ), stat, n ) ;
	}
	
	private DirectoryEntry imagePathToDirEntry( String path, long inodeNumber, long n ) throws IOException {
		long ino = resolvePath( path );
        
        String[] segs = path.split( "/" );
        String name = segs[ segs.length - 1 ];
        
        Stat pStat = statPath( path, ino );

        System.out.println( "Adding list path " + path );
        System.out.println( "   Inode     " + String.format( "%16x", ino ) );
        System.out.println( "   Name      " + name );
        System.out.println( "   StatInode " + pStat.getFileId() );
        
        return new DirectoryEntry( name, toFh(ino), pStat, n );
	}
    
	private Inode toFh( long inodeNumber ) {
        return Inode.forFile( Longs.toByteArray( inodeNumber ) );
    }
	
    private long getInodeNumber( Inode inode ) {
        return Longs.fromByteArray( inode.getFileId() );
    }
}
