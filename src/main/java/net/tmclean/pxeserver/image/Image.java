package net.tmclean.pxeserver.image;

public class Image {

	private long        id;
	private String      name;
	private String      description;
	private ImageFormat format;
	private String      location; 
	
	public long getId() {
		return id;
	}
	public void setId(long id) {
		this.id = id;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	
	public ImageFormat getFormat() {
		return format;
	}
	public void setFormat(ImageFormat format) {
		this.format = format;
	}
	
	public String getLocation() {
		return location;
	}
	public void setLocation(String location) {
		this.location = location;
	}
}
