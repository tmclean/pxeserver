package net.tmclean.pxeserver.nfs;

import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.Callable;

import org.dcache.nfs.ExportFile;
import org.dcache.nfs.v3.NfsServerV3;
import org.dcache.nfs.v3.xdr.mount_prot;
import org.dcache.nfs.v3.xdr.nfs3_prot;
import org.dcache.nfs.v4.MDSOperationFactory;
import org.dcache.nfs.v4.NFSServerV41;
import org.dcache.nfs.v4.xdr.nfs4_prot;
import org.dcache.oncrpc4j.portmap.OncRpcEmbeddedPortmap;
import org.dcache.oncrpc4j.rpc.OncRpcProgram;
import org.dcache.oncrpc4j.rpc.OncRpcSvc;
import org.dcache.oncrpc4j.rpc.OncRpcSvcBuilder;

import net.tmclean.pxeserver.image.Image;
import net.tmclean.pxeserver.image.ImageContentRepository;
import net.tmclean.pxeserver.image.ImageRepository;

public class NfsService implements Callable<Void> {

    private final ImageRepository imageRepository;
    private final ImageContentRepository imageContentRepository;
    
	public NfsService( ImageRepository imageRepository, ImageContentRepository imageContentRepository ) {
    	this.imageRepository = imageRepository;
    	this.imageContentRepository = imageContentRepository;
	}
	
	@Override
	public Void call() throws IOException {
		IsoVfs vfs = new IsoVfs( this.imageRepository, this.imageContentRepository );

		StringBuffer exportStr = new StringBuffer();
		exportStr.append( "/ 127.0.0.1(ro,no_root_squash,all_squash,all_root,anonuid=0,anongid=0)" ).append( "\n" );
		exportStr.append( "/ 10.4.2.1(ro,no_root_squash,all_squash,all_root,anonuid=0,anongid=0)" ).append( "\n" );
		
		for( Image image : this.imageRepository.getAllImages() ) {
			if( !"/".equals( image.getName() ) ) {
				exportStr.append( "/" + image.getName() + " 127.0.0.1(ro,no_root_squash,all_squash,all_root,anonuid=0,anongid=0)" ).append( "\n" );
				exportStr.append( "/" + image.getName() + " 10.4.2.1(ro,no_root_squash,all_squash,all_root,anonuid=0,anongid=0)" ).append( "\n" );
			}
		}
		
		ExportFile exportFile = new ExportFile( new StringReader( exportStr.toString() ) );
		exportFile.getExports().forEach( System.out::println );
		
		new OncRpcEmbeddedPortmap();
		
		OncRpcSvc nfsSvc = 
			new OncRpcSvcBuilder()
				.withPort( 2049 )
				.withTCP()
				.withAutoPublish()
				.build();
		
		NFSServerV41 nfs4 = 
			new NFSServerV41.Builder()
				.withExportFile( exportFile )
				.withVfs( vfs )
				.withOperationFactory( new MDSOperationFactory() )
				.build();

        NfsServerV3 nfs3 = new NfsServerV3( exportFile, vfs );
        IsoMountServer mountd = new IsoMountServer( exportFile, vfs );

        nfsSvc.register( new OncRpcProgram( mount_prot.MOUNT_PROGRAM, mount_prot.MOUNT_V3 ), mountd );
        nfsSvc.register( new OncRpcProgram( nfs3_prot.NFS_PROGRAM,    nfs3_prot.NFS_V3    ), nfs3   );
        nfsSvc.register( new OncRpcProgram( nfs4_prot.NFS4_PROGRAM,   nfs4_prot.NFS_V4    ), nfs4   );

		nfsSvc.start();
		
		return null;
	}
	
}
