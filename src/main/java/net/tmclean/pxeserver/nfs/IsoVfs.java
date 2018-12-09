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
import org.dcache.nfs.vfs.Stat.Type;

import com.github.stephenc.javaisotools.loopfs.iso9660.Iso9660FileEntry;
import com.google.common.primitives.Longs;

import net.tmclean.pxeserver.iso.Image;
import net.tmclean.pxeserver.iso.ImageRepository;

public class IsoVfs implements VirtualFileSystem {

	private static final long ROOT_INODE = 1L;
	
    private final NfsIdMapping _idMapper = new SimpleIdMap();

    private final ImageRepository imageRepository;
    
    public IsoVfs( ImageRepository imageRepository ) {
    	this.imageRepository = imageRepository;
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
    			if( cookie++ > l ) {
    		        list.add( imageNameToDirEntry( image.getName(), inodeNumber, cookie ) );
    			}
        	}
        }
        else {
        	String imageName = "";
        	String filePath  = "";
        	String resolvedPath = resolveInode( inodeNumber );
        	
        	if( this.imageRepository.isImageRootId( inodeNumber ) ) {
        		imageName = resolvedPath;
                System.out.println( "Getting root for image named " + imageName );
        	}
        	else {
        		imageName = resolvedPath.substring( 0, resolvedPath.indexOf( '/' ) );
        		filePath  = resolvedPath.substring( resolvedPath.indexOf( "/" ) + 1 );
        	}

            System.out.println( "Getting path " + filePath + " for image named " + imageName );
            
            for( String p : this.imageRepository.listImagePath( imageName, filePath ) ) {
    			cookie++;
    			if( cookie > l ) {
    		        list.add( imagePathToDirEntry( imageName + "/" + p, inodeNumber, cookie ) );
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
    	
        return this.imageRepository.readImageFile( imageName, filePath, data, offset, count );
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
    		"/".equals( path.trim() ) ||  				// Is explicitly the root path
    		!path.contains( "/" ) 						// The path contains no separators, implying the root of an images
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
    	else {
    		String imageName = path.substring( 0, path.indexOf( '/' ) );
    		String filePath  = path.substring( path.indexOf( "/" )+1 );

            Iso9660FileEntry isoEntry = this.imageRepository.getFileEntry( imageName, filePath );	
            
            int type = isoEntry.isDirectory() ? Stat.S_IFDIR : Stat.S_IFREG;
            
            stat.setMode( type | 0707 );
            stat.setATime( isoEntry.getLastModifiedTime() );
            stat.setCTime( isoEntry.getLastModifiedTime() );
            stat.setMTime( isoEntry.getLastModifiedTime() );
            stat.setSize( isoEntry.getSize() );
            stat.setGeneration( isoEntry.getLastModifiedTime() );
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
    	else if( this.imageRepository.isImageRootId( inodeNumber ) ) {
    		return getImage( inodeNumber ).getName();
    	}
    	else {
    		long imageId = this.imageRepository.imageFileIdToImageId( inodeNumber );
    		Image image = getImage( imageId );
    		
	    	String imageName = image.getName();
	    	String filePath  = this.imageRepository.idToFilePath( inodeNumber );
    	
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

		throw new NoEntException( "Failed to locate image with name " + name );
    }
    
    private long resolvePath( String path ) throws IOException {

    	System.out.println( path );

		try {	
	    	if( "".equals( path ) || "/".equals( path ) ) {
	    		return ROOT_INODE;
	    	}
	    	else if( !path.contains( "/" ) ) {
	    		return getImage( path ).getId();
	    	}
	    	else {
				String imageName = path.substring( 0, path.indexOf( '/' ) );
				String filePath  = path.substring( path.indexOf( "/" )+1 );
				
				return this.imageRepository.filePathToId( imageName, filePath );
	    	}
    	}
		catch( IOException e ) {
			throw new NoEntException( "Failed to resolve path " + path );
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
