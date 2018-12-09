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

import net.tmclean.pxeserver.iso.ImageRepository;

public class IsoVfs implements VirtualFileSystem {
	
    private final NfsIdMapping _idMapper = new SimpleIdMap();

    private final ImageRepository imageRepository;
    
    public IsoVfs( ImageRepository imageRepository ) {
    	this.imageRepository = imageRepository;
	}

	@Override
	public FsStat getFsStat() throws IOException {
		int fileCount = -1;
		int isoFileSize = -1;
		return new FsStat( isoFileSize, fileCount, isoFileSize, fileCount );
	}

	@Override
	public Inode lookup( Inode parent, String path ) throws IOException {
		System.out.println( "Lookking up path " + path );
        long parentInodeNumber = getInodeNumber( parent );
        String parentName = resolveInode( parentInodeNumber ); 
        String child =  parentName.isEmpty() ? path : parentName + "/" + path;
        long childInodeNumber = resolvePath( child );
        return toFh( childInodeNumber );
	}
	
	private DirectoryEntry imageNameToDirEntry( String imageName, long inodeNumber, long n ) throws IOException {
		long ino = this.imageRepository.imageNameToId( imageName );
		
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
        
        return new DirectoryEntry( imageName, toFh(ino), stat, n ) ;
	}
	
	@Override
	public DirectoryStream list( Inode inode, byte[] verifier, long l ) throws IOException {

        long inodeNumber = getInodeNumber( inode );
        
        long cookie = 2;
        final List<DirectoryEntry> list = new ArrayList<>();
        
        System.out.println( "Listing dir children for inode " + String.format( "%016x", inodeNumber ) );
        
        if( inodeNumber == 1L ) {    
        	for( String imageName : this.imageRepository.getImageNames() ) {
    			cookie++;
    			if( cookie > l ) {
    		        list.add( imageNameToDirEntry( imageName, inodeNumber, cookie ) );
    			}
        	}
        }
    	else if( isImageRootInode( inodeNumber ) ) {
            String imageName = resolveInode( inodeNumber );
            System.out.println( "Getting root for image named " + imageName );
        	
            for( String p : this.imageRepository.listImagePath( imageName, "" ) ) {
        		if( !p.contains( "/" ) && !p.isEmpty() ) {
        			cookie++;
        			if( cookie > l ) {
        				String fullPath = imageName + "/" + p;
    					long ino = resolvePath( fullPath );
    	            
    		            String[] segs = p.split( "/" );
    		            String name = segs[ segs.length - 1 ];
    		            
    		            Stat pStat = statPath( fullPath, ino );
    		            
    		            System.out.println( "Adding list path " + fullPath );
    		            System.out.println( "   Inode     " + String.format( "%16x", ino ) );
    		            System.out.println( "   Name      " + name );
    		            System.out.println( "   StatInode " + pStat.getFileId() );
    		            
    		            list.add( new DirectoryEntry( name, toFh(ino), pStat, cookie ) );
        			}
        		}
            }
    	}
        else {
            String path = resolveInode( inodeNumber );

    		String imageName = path.substring( 0, path.indexOf( '/' ) );
    		String filePath  = path.substring( path.indexOf( "/" ) + 1 );

            System.out.println( "Getting path " + filePath + " for image named " + imageName );
            
            for( String p : this.imageRepository.listImagePath( imageName, filePath ) ) {
    			cookie++;
    			if( cookie > l ) {
    				String fullPath = imageName + "/" + p;
					long ino = resolvePath( fullPath );
	            
		            String[] segs = p.split( "/" );
		            String name = segs[ segs.length - 1 ];
		            
		            Stat pStat = statPath( fullPath, ino );

		            System.out.println( "Adding list path " + fullPath );
		            System.out.println( "   Inode     " + String.format( "%16x", ino ) );
		            System.out.println( "   Name      " + name );
		            System.out.println( "   StatInode " + pStat.getFileId() );
		            
		            list.add( new DirectoryEntry( name, toFh(ino), pStat, cookie ) );
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
        
        String path = resolveInode( inodeNumber );
        String parentPath = getParent( path );
        long parentInodeNumber = resolvePath( parentPath );
        return toFh( parentInodeNumber );
	}
	
	private String getParent( String path ) {
		if( path.isEmpty() || "/".equals( path ) ) {
			return "";
		}
		
		return path.substring( 0, path.lastIndexOf( "/" )-1 );
	}

	@Override
	public int read( Inode inode, byte[] data, long offset, int count ) throws IOException {
        long inodeNumber = getInodeNumber( inode );
        String path = resolveInode( inodeNumber );

		String imageName   = path.substring( 0, path.indexOf( '/' ) );
		String filePath    = path.substring( path.indexOf( "/" )+1 );
    	
        return this.imageRepository.readImageFile( imageName, filePath, data, offset, count );
	}

	@Override
	public Stat getattr( Inode inode ) throws IOException {
		long inodeNumber = getInodeNumber( inode );
		String path = resolveInode( inodeNumber );
		return statPath( path, inodeNumber );
	}

	@Override
	public Inode getRootInode() throws IOException {
		return toFh( 1L );
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
        
    	if( inodeNumber == 1L || 
    		path == null || path.trim().isEmpty() || 
    		"/".equals( path.trim() ) ||  
    		!path.contains( "/" ) ) {
    		
            stat.setMode( Stat.S_IFDIR | 0777 );
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
            
            stat.setMode( type | 0777 );
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
    	
    	if( inodeNumber == 1L ) {
    		return "";
    	}
    	else if( isImageRootInode( inodeNumber ) ) {
    		return this.imageRepository.idToImageName( inodeNumber );
    	}
    	else {
    		long imageId = inodeToImageId( inodeNumber );
    		
	    	String imageName = this.imageRepository.idToImageName( imageId );
	    	String filePath = this.imageRepository.idToFilePath( inodeNumber );
    	
	    	return imageName + "/" + filePath;
    	}
    }
    
    private boolean isImageRootInode( long inodeNumber ) {
    	return (inodeNumber & 0x00ffffffL) == 0;
    }

    private long inodeToImageId( long inode ) {
    	return inode & 0xff000000L;
    }
    
    private long resolvePath( String path ) throws IOException {

    	System.out.println( path );

		try {	
	    	if( "".equals( path ) || "/".equals( path ) ) {
	    		return 1L;
	    	}
	    	else if( !path.contains( "/" ) ) {
	    		return imageRepository.imageNameToId( path );
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
	
	private Inode toFh( long inodeNumber ) {
        return Inode.forFile( Longs.toByteArray( inodeNumber ) );
    }
	
    private long getInodeNumber( Inode inode ) {
        return Longs.fromByteArray( inode.getFileId() );
    }
}
