/*******************************************************************************
 * Copyright (c) 2008, 2012 Stepan Rutz.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stepan Rutz - initial implementation
 *******************************************************************************/

package eu.ensg.portail;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;



/**
 * 
 * @see https://github.com/srutz/mappanel
 * 
 * MapPanel display tiles from ign as is. This simple minimal viewer supports zoom around mouse-click center and has a simple api.
 * A number of tiles are cached. See {@link #CACHE_SIZE} constant. If you use this it will create traffic on the tileserver you are
 * using. Please be conscious about this.
 *
 * This class is a JPanel which can be integrated into any swing app just by creating an instance and adding like a JLabel.
 *
 * The map has the size <code>256*1<<zoomlevel</code>. This measure is referred to as map-coordinates. Geometric locations
 * like longitude and latitude can be obtained by helper methods. Note that a point in map-coordinates corresponds to a given
 * geometric position but also depending on the current zoom level.
 *
 * You can zoomIn around current mouse position by left double click. Left right click zooms out.
 *
 * <p>
 * Methods of interest are
 * <ul>
 * <li>{@link #setZoom(int)} which sets the map's zoom level. Values between 1 and 18 are allowed.</li>
 * <li>{@link #setMapPosition(Point)} which sets the map's top left corner. (In map coordinates)</li>
 * <li>{@link #setCenterPosition(Point)} which sets the map's center position. (In map coordinates)</li>
 * <li>{@link #computePosition(java.awt.geom.Point2D.Double)} returns the position in the map panels coordinate system
 * for the given longitude and latitude. If you want to center the map around this geometric location you need
 * to pass the result to the method</li>
 * </ul>
 * </p>
 *
 * <p>As mentioned above Longitude/Latitude functionality is available via the method {@link #computePosition(java.awt.geom.Point2D.Double)}.
 * If you have a GIS database you can get this info out of it for a given town/location, invoke {@link #computePosition(java.awt.geom.Point2D.Double)} to
 * translate to a position for the given zoom level and center the view around this position using {@link #setCenterPosition(Point)}.
 * </p>
 *
 * <p>The properties <code>zoom</code> and <code>mapPosition</code> are bound and can be tracked via
 * regular {@link PropertyChangeListener}s.</p>
 *
 * <p>License is EPL (Eclipse Public License).  Contact at stepan.rutz@gmx.de</p>
 *
 * @author stepan.rutz
 * @version $Revision$
 */
@SuppressWarnings("serial")
public class MapPanel extends JPanel {

	private static final Logger log = Logger.getLogger(MapPanel.class.getName());

	public int xrect = 0;
	public int yrect = 0;
	public int wrect = 0;
	public int hrect = 0;
	public boolean selectRectangle = false;

	public static double[] RES = new double[22];
	
	private PyramideFondOrtho pyramideFondOrtho;
	
	/* constants ... */
	private static TileServer[] TILESERVERS = null;

	private static final int PREFERRED_WIDTH = 320;
	private static final int PREFERRED_HEIGHT = 200;

	private static final int ANIMATION_FPS = 15, ANIMATION_DURARTION_MS = 500;

	/* basically not be changed */
	private static final int TILE_SIZE = 256;
	private static final int CACHE_SIZE = 256;

	private static final int MAGNIFIER_SIZE = 100;

	//-------------------------------------------------------------------------
	// tile url construction.
	// change here to support some other tile
	public static String getTileString(MapPanel mapPanel, TileServer tileServer, int xtile, int ytile, int zoom, Point.Double position) {
	    if (position != null) {
	        try {
    	        double lon = position.getX();
    	        double lat = position.getY();
    	        
    	        // Change to Web Mercator
    	        CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:4326");
    			CoordinateReferenceSystem targetCRS = CRS.decode("EPSG:3857");
    			MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, false);
    			
    	        GeometryFactory geometryFactory = new GeometryFactory();
    	        org.locationtech.jts.geom.Point sourceGeometry = geometryFactory.createPoint(new Coordinate(lat, lon));
    	        org.locationtech.jts.geom.Point targetGeometry =  (org.locationtech.jts.geom.Point)JTS.transform( sourceGeometry, transform);
    	         
    	        int[] coord = mapPanel.pyramideFondOrtho.getTopLeftCorner(Integer.toString(zoom));
    	        double X0 = -20037508;
                double Y0 = 20037508;
    	        if (coord != null) {
    	            X0 = coord[0];
                    Y0 = coord[1];
    	        } 
    	        
    	        double X = targetGeometry.getX();
    	        double Y = targetGeometry.getY();
    	        
    	        double d = 256 * MapPanel.RES[zoom];
    	        double dx = X - X0;
    	        double dy = Y0 - Y;
    	        
    	        int TILECOL = (int)(dx / d);
    	        int TILEROW = (int)(dy / d);
    	        
    	        String url = tileServer.getURL() + "TILEMATRIX=" + zoom + "&TILEROW=" + TILEROW + "&TILECOL=" + TILECOL;
    	        return url;
    	        
	        } catch (Exception e) {
	            e.printStackTrace();
	            return null;
	        }
	        
	    } else {
	        zoom = 18; 
	        xtile = 90241;
	        String url = tileServer.getURL() + "TILEMATRIX=" + zoom + "&TILEROW=" + xtile + "&TILECOL=132877";
	        return url;
	    }
	}

	//-------------------------------------------------------------------------
	// map impl.

	private Dimension mapSize = new Dimension(0, 0);
	private Point mapPosition = new Point(0, 0);
	private int zoom;

	private TileServer tileServer = null;

	private DragListener mouseListener = new DragListener();
	private TileCache cache = new TileCache();
	private Stats stats = new Stats();
	private OverlayPanel overlayPanel = new OverlayPanel();
	private ControlPanel controlPanel = new ControlPanel();

	private boolean useAnimations = true;
	private Animation animation;

	protected double smoothScale = 1.0D;
	private int smoothOffset = 0;
	private Point smoothPosition, smoothPivot;
	private Rectangle magnifyRegion;
	
	
	public MapPanel() {
		
		// System.setProperty("http.proxyHost", "10.0.4.2");
        // System.setProperty("http.proxyPort", "3128");
		
		String url = "http://wxs.ign.fr/pratique/wmts";
		              
		TILESERVERS = new TileServer[3];
        TILESERVERS[0] = new TileServer("Photographies aériennes", url + "?LAYER=ORTHOIMAGERY.ORTHOPHOTOS&"
                + "EXCEPTIONS=text/xml&FORMAT=image/jpeg&SERVICE=WMTS&VERSION=1.0.0&REQUEST=GetTile&STYLE=normal&TILEMATRIXSET=PM&", 21);
        TILESERVERS[1] = new TileServer("Plan IGN", url + "?LAYER=GEOGRAPHICALGRIDSYSTEMS.PLANIGN&"
                + "EXCEPTIONS=text/xml&FORMAT=image/jpeg&SERVICE=WMTS&VERSION=1.0.0&REQUEST=GetTile&STYLE=normal&TILEMATRIXSET=PM&", 21);
        
        tileServer = TILESERVERS[0];
		
	    pyramideFondOrtho = new PyramideFondOrtho();
	    this.pyramideFondOrtho.getTileMatrix();
	    
	    Point mapPosition = new Point(8282, 5179); 
	    int zoom = 6;
	    
	    try {
            // disable animation on windows7 for now
            useAnimations = !("Windows Vista".equals(System.getProperty("os.name")) && "6.1".equals(System.getProperty("os.version")));
        } catch (Exception e) {
            // be defensive here
            log.log(Level.INFO, "failed to check for win7", e);
        }

	    setLayout(new MapLayout());
	    setOpaque(true);
	    setBackground(new Color(0xc0, 0xc0, 0xc0));
	    add(overlayPanel);
	    add(controlPanel);
	    addMouseListener(mouseListener);
	    addMouseMotionListener(mouseListener);
	    addMouseWheelListener(mouseListener);
	    // add(slider);
	    setZoom(zoom);
	    setMapPosition(mapPosition);
      
		RES[0] = 156543.0339280410;
		RES[1] = 78271.5169640205;
	    RES[2] =  39135.7584820102;
	    RES[3] =  19567.8792410051;
	    RES[4] =  9783.9396205026;
	    RES[5] =  4891.9698102513;
	    RES[6] =  2445.9849051256;
	    RES[7] =  1222.9924525628;
	    RES[8] =  611.4962262814;
	    RES[9] =  305.7481131407;
	    RES[10] = 152.8740565704;
	    RES[11] = 76.4370282852;
	    RES[12] = 38.2185141426;
	    RES[13] = 19.1092570713;
	    RES[14] = 9.5546285356;
	    RES[15] = 4.7773142678;
	    RES[16] = 2.3886571339;
	    RES[17] = 1.1943285670;
	    RES[18] = 0.5971642835;
	    RES[19] = 0.2985821417;
	    RES[20] = 0.1492910709;
	    RES[21] = 0.0746455354;
	    
	}

	

	

	public TileServer getTileServer() {
		return tileServer;
	}
	
	

	public void setTileServer(TileServer tileServer) {
		if(this.tileServer == tileServer)
			return;
		this.tileServer = tileServer;
		while (getZoom() > tileServer.getMaxZoom())
			zoomOut(new Point(getWidth() / 2, getHeight() / 2));
	}

	public OverlayPanel getOverlayPanel() {
		return overlayPanel;
	}

	public ControlPanel getControlPanel() {
		return controlPanel;
	}

	public TileCache getCache() {
		return cache;
	}

	public Stats getStats() {
		return stats;
	}

	public Point getMapPosition() {
		return new Point(mapPosition.x, mapPosition.y);
	}

	public void setMapPosition(Point mapPosition) {
		setMapPosition(mapPosition.x, mapPosition.y);
	}

	public void setMapPosition(int x, int y) {
		if (mapPosition.x == x && mapPosition.y == y)
			return;
		Point oldMapPosition = getMapPosition();
		mapPosition.x = x;
		mapPosition.y = y;
		firePropertyChange("mapPosition", oldMapPosition, getMapPosition());
	}

	public void translateMapPosition(int tx, int ty) {
		setMapPosition(mapPosition.x + tx, mapPosition.y + ty);
	}

	public int getZoom() {
		return zoom;
	}

	public void setZoom(int zoom) {
		if (zoom == this.zoom)
			return;
		int oldZoom = this.zoom;
		this.zoom = Math.min(getTileServer().getMaxZoom(), zoom);
		mapSize.width = getXMax();
		mapSize.height = getYMax();
		firePropertyChange("zoom", oldZoom, zoom);
	}


	public void zoomInAnimated(Point pivot) {
		if (!useAnimations) {
			zoomIn(pivot);
			return;
		}
		if (animation != null)
			return;
		mouseListener.downCoords = null;
		animation = new Animation(AnimationType.ZOOM_IN, ANIMATION_FPS, ANIMATION_DURARTION_MS) {
			protected void onComplete() {
				smoothScale = 1.0d;
				smoothPosition = smoothPivot = null;
				smoothOffset = 0;
				animation = null;
				repaint();
			}
			protected void onFrame() {
				smoothScale = 1.0 + getFactor();
				repaint();
			}

		};
		smoothPosition = new Point(mapPosition.x, mapPosition.y);
		smoothPivot = new Point(pivot.x, pivot.y);
		smoothOffset = -1;
		zoomIn(pivot);
		animation.run();
	}

	public void zoomOutAnimated(Point pivot) {
		if (!useAnimations) {
			zoomOut(pivot);
			return;
		}
		if (animation != null)
			return;
		mouseListener.downCoords = null;
		animation = new Animation(AnimationType.ZOOM_OUT, ANIMATION_FPS, ANIMATION_DURARTION_MS) {
			protected void onComplete() {
				smoothScale = 1.0d;
				smoothPosition = smoothPivot = null;
				smoothOffset = 0;
				animation = null;
				repaint();
			}
			protected void onFrame() {
				smoothScale = 1 - .5 * getFactor();
				repaint();
			}

		};
		smoothPosition = new Point(mapPosition.x, mapPosition.y);
		smoothPivot = new Point(pivot.x, pivot.y);
		smoothOffset = 1;
		zoomOut(pivot);
		animation.run();
	}

	public void zoomIn(Point pivot) {
		if (getZoom() >= getTileServer().getMaxZoom())
			return;
		Point mapPosition = getMapPosition();
		int dx = pivot.x;
		int dy = pivot.y;
		setZoom(getZoom() + 1);
		setMapPosition(mapPosition.x * 2 + dx, mapPosition.y * 2 + dy);
		repaint();
	}

	public void zoomOut(Point pivot) {
		if (getZoom() <= 1)
			return;
		Point mapPosition = getMapPosition();
		int dx = pivot.x;
		int dy = pivot.y;
		setZoom(getZoom() - 1);
		setMapPosition((mapPosition.x - dx) / 2, (mapPosition.y - dy) / 2);
		repaint();
	}

	public int getXTileCount() {
		return (1 << zoom);
	}

	public int getYTileCount() {
		return (1 << zoom);
	}

	public int getXMax() {
		return TILE_SIZE * getXTileCount();
	}

	public int getYMax() {
		return TILE_SIZE * getYTileCount();
	}

	public Point getCursorPosition() {
		return new Point(mapPosition.x + mouseListener.mouseCoords.x, mapPosition.y + mouseListener.mouseCoords.y);
	}

	public Point getTile(Point position) {
		return new Point((int) Math.floor(((double) position.x) / TILE_SIZE),(int) Math.floor(((double) position.y) / TILE_SIZE));
	}

	public Point getCenterPosition() {
		return new Point(mapPosition.x + getWidth() / 2, mapPosition.y + getHeight() / 2);
	}

	public void setCenterPosition(Point p) {
		setMapPosition(p.x - getWidth() / 2, p.y - getHeight() / 2);
	}

	public Point.Double getLongitudeLatitude(Point position) {
		return new Point.Double(
				position2lon(position.x, getZoom()),
				position2lat(position.y, getZoom()));
	}

	public Point computePosition(Point.Double coords) {
		int x = lon2position(coords.x, getZoom());
		int y = lat2position(coords.y, getZoom());
		return new Point(x, y);
	}

	protected void paintComponent(Graphics gOrig) {
		super.paintComponent(gOrig);
		Graphics2D g = (Graphics2D) gOrig.create();
		try {
			paintInternal(g);
			// paintAppliInstruction(g);
		} finally {
			g.dispose();
		}
	}
	
	// private void paintAppliInstruction(Graphics2D g2d) {
		// Si besoin !
		// Ecrire le code de l'appli qui dessine sur la carte
	// }
	
	
	private static final class Painter {
		private final int zoom;
		private float transparency = 1F;
		private double scale = 1d;
		private final MapPanel mapPanel;

		private Painter(MapPanel mapPanel, int zoom) {
			this.mapPanel = mapPanel;
			this.zoom = zoom;
		}

		public float getTransparency() {
			return transparency;
		}

		public void setTransparency(float transparency) {
			this.transparency = transparency;
		}

		public double getScale() {
			return scale;
		}

		public void setScale(double scale) {
			this.scale = scale;
		}

		private void paint(Graphics2D gOrig, Point mapPosition, Point scalePosition) {
			Graphics2D g = (Graphics2D) gOrig.create();
			try {
				if (getTransparency() < 1f && getTransparency() >= 0f) {
					g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, transparency));
				}

				if (getScale() != 1d) {
					//Point scalePosition = new Point(component.getWidth()/ 2, component.getHeight() / 2);
					AffineTransform xform = new AffineTransform();
					xform.translate(scalePosition.x, scalePosition.y);
					xform.scale(scale, scale);
					xform.translate(-scalePosition.x, -scalePosition.y);
					g.transform(xform);
					g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
				}
				int width = mapPanel.getWidth();
				int height = mapPanel.getHeight();
				int x0 = (int) Math.floor(((double) mapPosition.x) / TILE_SIZE);
				int y0 = (int) Math.floor(((double) mapPosition.y) / TILE_SIZE);
				int x1 = (int) Math.ceil(((double) mapPosition.x + width) / TILE_SIZE);
				int y1 = (int) Math.ceil(((double) mapPosition.y + height) / TILE_SIZE);

				int dy = y0 * TILE_SIZE - mapPosition.y;
				for (int y = y0; y < y1; ++y) {
					int dx = x0 * TILE_SIZE - mapPosition.x;
					for (int x = x0; x < x1; ++x) {
						paintTile(g, dx, dy, x, y);
						dx += TILE_SIZE;
						++mapPanel.getStats().tileCount;
					}
					dy += TILE_SIZE;
				}

				if (getScale() == 1d && mapPanel.magnifyRegion != null) {
					Rectangle magnifyRegion = new Rectangle(mapPanel.magnifyRegion);
					magnifyRegion.translate(-mapPosition.x, -mapPosition.y);
					g.setColor(Color.yellow);
				}
			} finally {
				g.dispose();
			}
		}

		private void paintTile(Graphics2D g, int dx, int dy, int x, int y) {
			// System.out.println("PaintTile " + x + ", " + y);
			
			boolean DEBUG = false;
			boolean DRAW_IMAGES = true;
			boolean DRAW_OUT_OF_BOUNDS = false;

			boolean imageDrawn = false;
			int xTileCount = 1 << zoom;
			int yTileCount = 1 << zoom;
			boolean tileInBounds = x >= 0 && x < xTileCount && y >= 0 && y < yTileCount;
			boolean drawImage = DRAW_IMAGES && tileInBounds;
			if (drawImage) {
				TileCache cache = mapPanel.getCache();
				TileServer tileServer = mapPanel.getTileServer();
				Image image = cache.get(tileServer, x, y, zoom);
				if (image == null) {
				    Point.Double d = mapPanel.getLongitudeLatitude(new Point((x*TILE_SIZE),(y*TILE_SIZE)));
				    final String urlAddress = getTileString(mapPanel, mapPanel.tileServer, x, y, zoom, d);
					try {
						URL url = new URL(urlAddress);
						// System.out.println(url);
					    image = Toolkit.getDefaultToolkit().getImage(url);
					} catch (Exception e) {
						log.log(Level.SEVERE, "failed to load url \"" + urlAddress + "\"", e);
					}
					if (image != null)
						cache.put(tileServer, x, y, zoom, image);
				}
				if (image != null) {
					g.drawImage(image, dx, dy, mapPanel);
					imageDrawn = true;
				}
			}
			if (DEBUG && (!imageDrawn && (tileInBounds || DRAW_OUT_OF_BOUNDS))) {
				g.setColor(Color.blue);
				g.fillRect(dx + 4, dy + 4, TILE_SIZE - 8, TILE_SIZE - 8);
				g.setColor(Color.gray);
				String s = "T " + x + ", " + y + (!tileInBounds ? " #" : "");
				g.drawString(s, dx + 4+ 8, dy + 4 + 12);
			}
		}


	}

	private void paintInternal(Graphics2D g) {
		stats.reset();
		long t0 = System.currentTimeMillis();

		if (smoothPosition != null) {
			{
				Point position = getMapPosition();
				Painter painter = new Painter(this, getZoom());
				painter.paint(g, position, null);
			}
			Point position = new Point(smoothPosition.x, smoothPosition.y);
			Painter painter = new Painter(this, getZoom() + smoothOffset);
			painter.setScale(smoothScale);

			float t = (float) (animation == null ? 1f : 1 - animation.getFactor());
			painter.setTransparency(t);
			painter.paint(g, position, smoothPivot);
			if (animation != null && animation.getType() == AnimationType.ZOOM_IN) {
				int cx = smoothPivot.x, cy = smoothPivot.y;
				drawScaledRect(g, cx, cy, animation.getFactor(), 1 + animation.getFactor());
			} else if (animation != null && animation.getType() == AnimationType.ZOOM_OUT) {
				int cx = smoothPivot.x, cy = smoothPivot.y;
				drawScaledRect(g, cx, cy, animation.getFactor(), 2 - animation.getFactor());
			}
			//System.err.println("smoothScale" + smoothScale);
		}

		if (smoothPosition == null) {
			Point position = getMapPosition();
			Painter painter = new Painter(this, getZoom());
			painter.paint(g, position, null);
		}

		long t1 = System.currentTimeMillis();
		stats.dt = t1 - t0;
	}


	private void drawScaledRect(Graphics2D g, int cx, int cy, double f, double scale) {
		AffineTransform oldTransform = g.getTransform();
		g.translate(cx, cy);
		g.scale(scale, scale);
		g.translate(-cx, -cy);
		int c = 0x80 + (int) Math.floor(f * 0x60);
		if (c < 0) c = 0;
		else if (c > 255) c = 255;
		Color color = new Color(c, c, c);
		g.setColor(color);
		g.drawRect(cx - 40, cy - 30, 80, 60);
		g.setTransform(oldTransform);
	}

	//-------------------------------------------------------------------------
	// utils
	public static String format(double d) {
		return String.format("%.5f", d);
	}

	public static double getN(int y, int z) {
		double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
		return n;
	}

	public static double position2lon(int x, int z) {
		double xmax = TILE_SIZE * (1 << z);
		return x / xmax * 360.0 - 180;
	}

	public static double position2lat(int y, int z) {
		double ymax = TILE_SIZE * (1 << z);
		return Math.toDegrees(Math.atan(Math.sinh(Math.PI - (2.0 * Math.PI * y) / ymax)));
	}

	public static double tile2lon(int x, int z) {
		return x / Math.pow(2.0, z) * 360.0 - 180;
	}

	public static double tile2lat(int y, int z) {
		return Math.toDegrees(Math.atan(Math.sinh(Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z))));
	}

	public static int lon2position(double lon, int z) {
		double xmax = TILE_SIZE * (1 << z);
		return (int) Math.floor((lon + 180) / 360 * xmax);
	}

	public static int lat2position(double lat, int z) {
		double ymax = TILE_SIZE * (1 << z);
		return (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * ymax);
	}

//	public static String getTileNumber(TileServer tileServer, double lat, double lon, int zoom) {
//		int xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
//		int ytile = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));
//		return getTileString(tileServer, xtile, ytile, zoom, null);
//	}



	private static void drawBackground(Graphics2D g, int width, int height) {
		Color color1 = Color.black;
		Color color2 = new Color(0x30, 0x30, 0x30);
		color1 = new Color(0xc0, 0xc0, 0xc0);
		color2 = new Color(0xe0, 0xe0, 0xe0);
		Composite oldComposite = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.75f));
		g.setPaint(new GradientPaint(0, 0, color1, 0, height, color2));
		g.fillRoundRect(0, 0, width, height, 4, 4);
		g.setComposite(oldComposite);
	}

	private static void drawRollover(Graphics2D g, int width, int height) {
		Color color1 = Color.white;
		Color color2 = new Color(0xc0, 0xc0, 0xc0);
		Composite oldComposite = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.25f));
		g.setPaint(new GradientPaint(0, 0, color1, width, height, color2));
		g.fillRoundRect(0, 0, width, height, 4, 4);
		g.setComposite(oldComposite);
	}

	private static BufferedImage flip(BufferedImage image, boolean horizontal, boolean vertical) {
		int width = image.getWidth(), height = image.getHeight();
		if (horizontal) {
			for (int y = 0; y < height; ++y) {
				for (int x = 0; x < width / 2; ++x) {
					int tmp = image.getRGB(x, y);
					image.setRGB(x, y, image.getRGB(width - 1 - x, y));
					image.setRGB(width - 1 - x, y, tmp);
				}
			}
		}
		if (vertical) {
			for (int x = 0; x < width; ++x) {
				for (int y = 0; y < height / 2; ++y) {
					int tmp = image.getRGB(x, y);
					image.setRGB(x, y, image.getRGB(x, height - 1 - y));
					image.setRGB(x, height - 1 - y, tmp);
				}
			}
		}
		return image;
	}

	private static BufferedImage makeIcon(Color background) {
		final int WIDTH = 16, HEIGHT = 16;
		BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < HEIGHT; ++y)
			for (int x = 0; x < WIDTH; ++x)
				image.setRGB(x, y, 0);
		Graphics2D g2d = (Graphics2D) image.getGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setColor(background);
		g2d.fillOval(0, 0, WIDTH - 1, HEIGHT - 1);

		double hx = 4;
		double hy = 4;
		for (int y = 0; y < HEIGHT; ++y) {
			for (int x = 0; x < WIDTH; ++x) {
				double dx = x - hx;
				double dy = y - hy;
				double dist = Math.sqrt(dx * dx + dy * dy);
				if (dist > WIDTH) {
					dist = WIDTH;
				}
				int color = image.getRGB(x, y);
				int a = (color >>> 24) & 0xff;
				int r = (color >>> 16) & 0xff;
				int g = (color >>> 8) & 0xff;
				int b = (color >>> 0) & 0xff;
				double coef = 0.7 - 0.7 * dist / WIDTH;
				image.setRGB(x, y, (a << 24) | ((int) (r + coef * (255 - r)) << 16) | ((int) (g + coef * (255 - g)) << 8) | (int) (b + coef * (255 - b)));
			}
		}
		g2d.setColor(Color.gray);
		g2d.drawOval(0, 0, WIDTH - 1, HEIGHT - 1);
		return image;
	}

	private static BufferedImage makeXArrow(Color background) {
		BufferedImage image = makeIcon(background);
		Graphics2D g = (Graphics2D) image.getGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.fillPolygon(new int[] { 10, 4, 10} , new int[] { 5, 8, 11 }, 3);
		image.flush();
		return image;

	}
	private static BufferedImage makeYArrow(Color background) {
		BufferedImage image = makeIcon(background);
		Graphics2D g = (Graphics2D) image.getGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.fillPolygon(new int[] { 5, 8, 11} , new int[] { 10, 4, 10 }, 3);
		image.flush();
		return image;
	}
	private static BufferedImage makePlus(Color background) {
		BufferedImage image = makeIcon(background);
		Graphics2D g = (Graphics2D) image.getGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.fillRect(4, 7, 8, 2);
		g.fillRect(7, 4, 2, 8);
		image.flush();
		return image;
	}
	private static BufferedImage makeMinus(Color background) {
		BufferedImage image = makeIcon(background);
		Graphics2D g = (Graphics2D) image.getGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.fillRect(4, 7, 8, 2);
		image.flush();
		return image;
	}


	//-------------------------------------------------------------------------
	// helpers
	private enum AnimationType {
		ZOOM_IN, ZOOM_OUT
	}

	private static abstract class Animation implements ActionListener {

		private final AnimationType type;
		private final Timer timer;
		private long t0 = -1L;
		private long dt;
		private final long duration;

		public Animation(AnimationType type, int fps, long duration) {
			this.type = type;
			this.duration = duration;
			int delay = 1000 / fps;
			timer = new Timer(delay, this);
			timer.setCoalesce(true);
			timer.setInitialDelay(0);
		}

		public AnimationType getType() {
			return type;
		}

		protected abstract void onComplete();

		protected abstract void onFrame();

		public double getFactor() {
			return (double) getDt() / getDuration();
		}

		public void actionPerformed(ActionEvent e) {
			if (getDt() >= duration) {
				kill();
				onComplete();
				return;
			}
			onFrame();
		}

		public long getDuration() {
			return duration;
		}

		public long getDt() {
			if (!timer.isRunning())
				return dt;
			long now = System.currentTimeMillis();
			if (t0 < 0)
				t0 = now;
			return now - t0 + dt;
		}

		public void run() {
			if (timer.isRunning())
				return;
			timer.start();
		}

		public void kill() {
			if (!timer.isRunning())
				return;
			dt = getDt();
			timer.stop();
		}
	}

	private static class Tile {
		private final String key;
		public final int x, y, z;
		public Tile(String tileServer, int x, int y, int z) {
			this.key = tileServer;
			this.x = x;
			this.y = y;
			this.z = z;
		}
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((key == null) ? 0 : key.hashCode());
			result = prime * result + x;
			result = prime * result + y;
			result = prime * result + z;
			return result;
		}
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Tile other = (Tile) obj;
			if (key == null) {
				if (other.key != null)
					return false;
			} else if (!key.equals(other.key))
				return false;
			if (x != other.x)
				return false;
			if (y != other.y)
				return false;
			if (z != other.z)
				return false;
			return true;
		}

	}

	private static class TileCache {
		private LinkedHashMap<Tile,Image> map = new LinkedHashMap<Tile,Image>(CACHE_SIZE, 0.75f, true) {
			protected boolean removeEldestEntry(java.util.Map.Entry<Tile,Image> eldest) {
				boolean remove = size() > CACHE_SIZE;
				return remove;
			}
		};
		public void put(TileServer tileServer, int x, int y, int z, Image image) {
			map.put(new Tile(tileServer.getURL(), x, y, z), image);
		}
		public Image get(TileServer tileServer, int x, int y, int z) {
			//return map.get(new Tile(x, y, z));
			Image image = map.get(new Tile(tileServer.getURL(), x, y, z));
			return image;
		}
		@SuppressWarnings("unused")
		public int getSize() {
			return map.size();
		}
	}

	private static class Stats {
		@SuppressWarnings("unused")
		private int tileCount;
		@SuppressWarnings("unused")
		private long dt;
		private Stats() {
			reset();
		}
		private void reset() {
			tileCount = 0;
			dt = 0;
		}
	}

	public static class CustomSplitPane extends JComponent  {
		private static final int SPACER_SIZE = 4;
		private final boolean horizonal;
		private final JComponent spacer;

		private double split = 0.5;
		private int dx, dy;
		private Component componentOne, componentTwo;

		public CustomSplitPane(boolean horizonal) {
			this.spacer = new JPanel();
			this.spacer.setOpaque(false);
			this.spacer.setCursor(horizonal ? Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR) : Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
			this.dx = this.dy = -1;
			this.horizonal = horizonal;

			/* because of jdk1.5, javafx */
			class SpacerMouseAdapter extends MouseAdapter implements MouseMotionListener {
				public void mouseReleased(MouseEvent e) {
					Insets insets = getInsets();
					int width = getWidth();
					int height = getHeight();
					int availw = width - insets.left - insets.right;
					int availh = height - insets.top - insets.bottom;
					if (CustomSplitPane.this.horizonal && dy != -1) {
						setSplit((double) dx / availw);
					} else if (dx != -1) {
						setSplit((double) dy / availh);
					}
					dx = dy = -1;
					spacer.setOpaque(false);
					repaint();
				}

				public void mouseDragged(MouseEvent e) {
					dx = e.getX() + spacer.getX(); 
					dy = e.getY() + spacer.getY();
					spacer.setOpaque(true);
					if (dx != -1 && CustomSplitPane.this.horizonal) {
						spacer.setBounds(dx, 0, SPACER_SIZE, getHeight());
					} else if (dy != -1 && !CustomSplitPane.this.horizonal) {
						spacer.setBounds(0, dy, getWidth(), SPACER_SIZE);
					}
					repaint();
				}

				public void mouseMoved(MouseEvent e) {
				}
			};
			SpacerMouseAdapter mouseAdapter = new SpacerMouseAdapter();
			spacer.addMouseListener(mouseAdapter);
			spacer.addMouseMotionListener(mouseAdapter);

			setLayout(new LayoutManager() {
				public void addLayoutComponent(String name, Component comp) {
				}

				public void removeLayoutComponent(Component comp) {
				}

				public Dimension minimumLayoutSize(Container parent) {
					return new Dimension(1, 1);
				}

				public Dimension preferredLayoutSize(Container parent) {
					return new Dimension(128, 128);
				}

				public void layoutContainer(Container parent) {
					Insets insets = parent.getInsets();
					int width = parent.getWidth();
					int height = parent.getHeight();
					int availw = width - insets.left - insets.right;
					int availh = height - insets.top - insets.bottom;

					if (CustomSplitPane.this.horizonal) {
						availw -= SPACER_SIZE;
						int width1 = Math.max(0, (int) Math.floor(split * availw));
						int width2 = Math.max(0, availw - width1);
						if (componentOne.isVisible() && !componentTwo.isVisible()) {
							spacer.setBounds(0, 0, 0, 0);
							componentOne.setBounds(insets.left, insets.top, availw, availh);
						} else if (!componentOne.isVisible() && componentTwo.isVisible()) {
							spacer.setBounds(0, 0, 0, 0);
							componentTwo.setBounds(insets.left, insets.top, availw, availh);
						} else {
							spacer.setBounds(insets.left + width1, insets.top, SPACER_SIZE, availh);
							componentOne.setBounds(insets.left, insets.top, width1, availh);
							componentTwo.setBounds(insets.left + width1 + SPACER_SIZE, insets.top, width2, availh);
						}
					} else {
						availh -= SPACER_SIZE;
						int height1 = Math.max(0, (int) Math.floor(split * availh));
						int height2 = Math.max(0, availh - height1);
						if (componentOne.isVisible() && !componentTwo.isVisible()) {
							spacer.setBounds(0, 0, 0, 0);
							componentOne.setBounds(insets.left, insets.top, availw, availh);
						} else if (!componentOne.isVisible() && componentTwo.isVisible()) {
							spacer.setBounds(0, 0, 0, 0);
							componentTwo.setBounds(insets.left, insets.top, availw, availh);
						} else {
							spacer.setBounds(insets.left, insets.top + height1, availw, SPACER_SIZE);
							componentOne.setBounds(insets.left, insets.top, availw, height1);
							componentTwo.setBounds(insets.left, insets.top + height1 + SPACER_SIZE, availw, height2);
						}
					}
				}
			});
			add(spacer);
		}

		public double getSplit() {
			return split;
		}

		public void setSplit(double split) {
			if (split < 0)
				split = 0;
			else if (split > 1)
				split = 1;
			this.split = split;
			invalidate();
			validate();
		}

		public void setComponentOne(Component component) {
			this.componentOne = component;
			if (componentOne != null)
				add(componentOne);
		}

		public void setComponentTwo(Component component) {
			this.componentTwo = component;
			if (componentTwo != null)
				add(componentTwo);
		}
	}

	private class DragListener extends MouseAdapter implements MouseMotionListener, MouseWheelListener {
		private Point mouseCoords;
		private Point downCoords;
		private Point downPosition;

		public DragListener() {
			mouseCoords = new Point();
		}

		public void mouseClicked(MouseEvent e) {
			if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() >= 2) {
				zoomInAnimated(new Point(mouseCoords.x, mouseCoords.y));
			} else if (e.getButton() == MouseEvent.BUTTON3 && e.getClickCount() >= 2) {
				zoomOutAnimated(new Point(mouseCoords.x, mouseCoords.y));
			} else if (e.getButton() == MouseEvent.BUTTON2) {
				setCenterPosition(getCursorPosition());
				repaint();
			}
		}

		public void mousePressed(MouseEvent e) {
			if (e.getButton() == MouseEvent.BUTTON1) {
				downCoords = e.getPoint();
				downPosition = getMapPosition();
			} else if (e.getButton() == MouseEvent.BUTTON3) {
				int cx = getCursorPosition().x;
				int cy = getCursorPosition().y;
				magnifyRegion = new Rectangle(cx - MAGNIFIER_SIZE / 2, cy - MAGNIFIER_SIZE / 2, MAGNIFIER_SIZE, MAGNIFIER_SIZE);
				repaint();
			}
		}

		public void mouseReleased(MouseEvent e) {
			//setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
			handleDrag(e);
			downCoords = null;
			downPosition = null;
			magnifyRegion = null;
		}

		public void mouseMoved(MouseEvent e) {
			handlePosition(e);
		}

		public void mouseDragged(MouseEvent e) {
			//setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			handlePosition(e);
			handleDrag(e);
		}

		public void mouseExited(MouseEvent e) {
			//setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		}

		public void mouseEntered(MouseEvent me) {
			super.mouseEntered(me);
		}

		private void handlePosition(MouseEvent e) {
			// System.out.println("----- +++ ------");
			mouseCoords = e.getPoint();
			if (overlayPanel.isVisible())
				MapPanel.this.repaint();
		}

		private void handleDrag(MouseEvent e) {
			if (downCoords != null) {
				int tx = downCoords.x - e.getX();
				int ty = downCoords.y - e.getY();
				setMapPosition(downPosition.x + tx, downPosition.y + ty);
				repaint();
			} else if (magnifyRegion != null) {
				int cx = getCursorPosition().x;
				int cy = getCursorPosition().y;
				magnifyRegion = new Rectangle(cx - MAGNIFIER_SIZE / 2, cy - MAGNIFIER_SIZE / 2, MAGNIFIER_SIZE, MAGNIFIER_SIZE);
				repaint();
			}
		}

		public void mouseWheelMoved(MouseWheelEvent e) {
			int rotation = e.getWheelRotation();
			if (rotation < 0)
				zoomInAnimated(new Point(mouseCoords.x, mouseCoords.y));
			else
				zoomOutAnimated(new Point(mouseCoords.x, mouseCoords.y));
		}
	}

	public final class OverlayPanel extends JPanel {

		private OverlayPanel() {
			setOpaque(false);
			setPreferredSize(new Dimension(570, 570));
		}

		protected void paintComponent(Graphics gOrig) {
			super.paintComponent(gOrig);
			Graphics2D g = (Graphics2D) gOrig.create();
			try {
				paintOverlay(g);
			} finally {
				g.dispose();
			}
		}

		private void paintOverlay(Graphics2D g) {


			if (selectRectangle){

				Stroke dashed2 = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5,5}, 0);
				g.setStroke(dashed2);
				g.setColor(Color.RED.darker());

				int x0 = xrect;
				int y0 = yrect;

				if (wrect < 0){x0 = x0 + wrect; wrect = -wrect;}
				if (hrect < 0){y0 = y0 + hrect; hrect = -hrect;}

				g.drawRect(x0, y0-20, wrect, hrect);
				
				g.setColor(new Color(1.f, 0, 0, 0.1f));
				g.fillRect(x0, y0-20, wrect, hrect);

			}

		}

		@SuppressWarnings("unused")
		private void drawString(Graphics2D g, int row, String key, String value) {
			int y = 16 + row * 16;
			g.drawString(key, 20, y);
			g.drawString(value, 150, y);
		}
	}

	public final class ControlPanel extends JPanel {

		protected static final int MOVE_STEP = 32;



		private JButton makeButton(Action action) {
			JButton b = new JButton(action);
			b.setFocusable(false);
			b.setText(null);
			b.setContentAreaFilled(false);
			b.setBorder(BorderFactory.createEmptyBorder());
			BufferedImage image = (BufferedImage) ((ImageIcon)b.getIcon()).getImage();
			BufferedImage hl = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = (Graphics2D) hl.getGraphics();
			g.drawImage(image, 0, 0, null);
			drawRollover(g, hl.getWidth(), hl.getHeight());
			hl.flush();
			b.setRolloverIcon(new ImageIcon(hl));
			return b;
		}

		public ControlPanel() {
			setOpaque(false);
			setForeground(Color.white);
			setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
			setLayout(new BorderLayout());

			Action zoomInAction = new AbstractAction() {
				{
					String text = "Zoom In";
					putValue(Action.NAME, text);
					putValue(Action.SHORT_DESCRIPTION, text);
					putValue(Action.SMALL_ICON, new ImageIcon(flip(makePlus(new Color(0xc0, 0xc0, 0xc0)), false, false)));
				}

				public void actionPerformed(ActionEvent e) {
					zoomInAnimated(new Point(MapPanel.this.getWidth() / 2, MapPanel.this.getHeight() / 2));
				}
			};
			Action zoomOutAction = new AbstractAction() {
				{
					String text = "Zoom Out";
					putValue(Action.NAME, text);
					putValue(Action.SHORT_DESCRIPTION, text);
					putValue(Action.SMALL_ICON, new ImageIcon(flip(makeMinus(new Color(0xc0, 0xc0, 0xc0)), false, false)));
				}

				public void actionPerformed(ActionEvent e) {
					zoomOutAnimated(new Point(MapPanel.this.getWidth() / 2, MapPanel.this.getHeight() / 2));
				}
			};

			Action upAction = new AbstractAction() {
				{
					String text = "Up";
					putValue(Action.NAME, text);
					putValue(Action.SHORT_DESCRIPTION, text);
					putValue(Action.SMALL_ICON, new ImageIcon(flip(makeYArrow(new Color(0xc0, 0xc0, 0xc0)), false, false)));
				}

				public void actionPerformed(ActionEvent e) {
					translateMapPosition(0, -MOVE_STEP);
					MapPanel.this.repaint();
				}
			};
			Action downAction = new AbstractAction() {
				{
					String text = "Down";
					putValue(Action.NAME, text);
					putValue(Action.SHORT_DESCRIPTION, text);
					putValue(Action.SMALL_ICON, new ImageIcon(flip(makeYArrow(new Color(0xc0, 0xc0, 0xc0)), false, true)));
				}

				public void actionPerformed(ActionEvent e) {
					translateMapPosition(0, +MOVE_STEP);
					MapPanel.this.repaint();
				}
			};
			Action leftAction = new AbstractAction() {
				{
					String text = "Left";
					putValue(Action.NAME, text);
					putValue(Action.SHORT_DESCRIPTION, text);
					putValue(Action.SMALL_ICON, new ImageIcon(flip(makeXArrow(new Color(0xc0, 0xc0, 0xc0)), false, false)));
				}

				public void actionPerformed(ActionEvent e) {
					translateMapPosition(-MOVE_STEP, 0);
					MapPanel.this.repaint();
				}
			};
			Action rightAction = new AbstractAction() {
				{
					String text = "Right";
					putValue(Action.NAME, text);
					putValue(Action.SHORT_DESCRIPTION, text);
					putValue(Action.SMALL_ICON, new ImageIcon(flip(makeXArrow(new Color(0xc0, 0xc0, 0xc0)), true, false)));
				}

				public void actionPerformed(ActionEvent e) {
					translateMapPosition(+MOVE_STEP, 0);
					MapPanel.this.repaint();
				}
			};
			JPanel moves = new JPanel(new BorderLayout());
			moves.setOpaque(false);
			JPanel zooms = new JPanel(new BorderLayout(0, 0));
			zooms.setOpaque(false);
			zooms.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
			moves.add(makeButton(upAction), BorderLayout.NORTH);
			moves.add(makeButton(leftAction), BorderLayout.WEST);
			moves.add(makeButton(downAction), BorderLayout.SOUTH);
			moves.add(makeButton(rightAction), BorderLayout.EAST);
			zooms.add(makeButton(zoomInAction), BorderLayout.NORTH);
			zooms.add(makeButton(zoomOutAction), BorderLayout.SOUTH);
			add(moves, BorderLayout.NORTH);
			add(zooms, BorderLayout.SOUTH);
		}

		public void paint(Graphics gOrig) {
			Graphics2D g = (Graphics2D) gOrig.create();

			try {
				int w = getWidth(), h = getHeight();
				drawBackground(g, w, h);
			} finally {
				g.dispose();
			}
			super.paint(gOrig);
		}
	}


	private final class MapLayout implements LayoutManager {

		public void addLayoutComponent(String name, Component comp) {
		}
		public void removeLayoutComponent(Component comp) {
		}
		public Dimension minimumLayoutSize(Container parent) {
			return new Dimension(1, 1);
		}
		public Dimension preferredLayoutSize(Container parent) {
			return new Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT);
		}
		public void layoutContainer(Container parent) {
			int width = parent.getWidth();
			{
				Dimension psize = overlayPanel.getPreferredSize();
				overlayPanel.setBounds(width - psize.width - 20, 20, psize.width, psize.height);
			}
			{
				Dimension psize = controlPanel.getPreferredSize();
				controlPanel.setBounds(20, 20, psize.width, psize.height);
			}
		}
	}

}


