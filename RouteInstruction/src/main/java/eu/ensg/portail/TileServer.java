package eu.ensg.portail;

public class TileServer {
	private final String url;
	private final String name;
	private final int maxZoom;
	private boolean broken;

	public TileServer(String name, String url, int maxZoom) {
	    this.name = name;
		this.url = url;
		this.maxZoom = maxZoom;
	}

	public String toString() {
		return url;
	}

	public int getMaxZoom() {
		return maxZoom;
	}
	public String getURL() {
		return url;
	}
	public String getName() {
	    return this.name;
	}

	public boolean isBroken() {
		return broken;
	}

	public void setBroken(boolean broken) {
		this.broken = broken;
	}
}
