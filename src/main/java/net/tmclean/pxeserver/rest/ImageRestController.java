package net.tmclean.pxeserver.rest;

import net.tmclean.pxeserver.image.Image;
import net.tmclean.pxeserver.image.ImageRepository;
import net.tmclean.pxeserver.image.aggregate.ImageContentDirectory;
import net.tmclean.pxeserver.rest.util.PathUtils;

import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping( "/image" )
public class ImageRestController {

	private final ImageRepository imageRepo;
	private final ImageContentDirectory contentDirectory;
	
	public ImageRestController( 
		ImageRepository imageRepo, 
		ImageContentDirectory contentDirectory 
	) {
		this.imageRepo = imageRepo;
		this.contentDirectory = contentDirectory;
	}
	
	@GetMapping( path="/id/{id}" )
	public Image imageById( @PathVariable( "id" ) long id ) {
		return imageRepo.getImage( id );
	}
	
	@GetMapping( value="/name/{name}" )
	public Image imageByName( @PathVariable( "name" ) String name ) {
		return imageRepo.getImage( name );
	}
	
	@GetMapping( "/id/{id}/content/**" )
	public List<String> imageContent(
		@PathVariable( "id" ) long id,
		HttpServletRequest request
	) throws IOException {
	    String path = PathUtils.extractRestOfWildcardPath( request );
	    
	    Image image = this.imageById( id );
	    
	    return this.contentDirectory.listImagePath( image, path );
	}
}
