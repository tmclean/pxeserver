package net.tmclean.pxeserver.image;

public class ImageFileEntry {
	private String name;
	private String path;
	private long length;
	private long lastModified;
	private boolean directory;
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	
	public long getLength() {
		return length;
	}
	public void setLength(long length) {
		this.length = length;
	}
	
	public long getLastModified() {
		return lastModified;
	}
	public void setLastModified(long lastModified) {
		this.lastModified = lastModified;
	}
	
	public boolean isDirectory() {
		return directory;
	}
	public void setDirectory(boolean directory) {
		this.directory = directory;
	}
}
