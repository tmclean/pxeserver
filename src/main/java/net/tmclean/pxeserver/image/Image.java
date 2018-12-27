package net.tmclean.pxeserver.image;

public class Image {

	private long        id;
	private String      name;
	private String      description;
	private boolean     root;
	private ImageFormat format;
	private String      location; 
	
	public Image() {
		root = false;
	}
	
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
	
	public boolean isRoot() {
		return root;
	}
	public void setRoot(boolean root) {
		this.root = root;
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
