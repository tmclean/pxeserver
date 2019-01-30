package net.tmclean.pxeserver.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import net.tmclean.pxeserver.image.Image;
import net.tmclean.pxeserver.image.ImageRepository;

import java.util.List;

@RestController
@RequestMapping( "/images" )
public class ImagesRestController {

	private final ImageRepository imageRepo;
	
	public ImagesRestController( ImageRepository imageRepo ) {
		this.imageRepo = imageRepo;
	}
	
	@GetMapping
	public List<Image> images(){
		return imageRepo.getAllImages();
	}
}
