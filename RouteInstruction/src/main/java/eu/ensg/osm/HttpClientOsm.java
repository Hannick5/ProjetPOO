package eu.ensg.osm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;


public class HttpClientOsm {
	
	/** OverPass API URL. */
	// private static final String URL_OVERPASS_API = "http://overpass-api.de/api/interpreter";
	private static final String URL_OVERPASS_API = "https://lz4.overpass-api.de/api/interpreter";
	// private static final String URL_OVERPASS_API = "https://z.overpass-api.de/api/interpreter";
	
	/**
	 * @see http://wiki.openstreetmap.org/wiki/Overpass_API/Language_Guide
	 * Example : way(50.746,7.154,50.748,7.157);out body;
	 * 
	 * @param data : 
	 *    param request in UTF-8 to send.
	 * @return 
	 */
	public static String getOsmXML(String data) {

		StringBuffer response = new StringBuffer();
		try {
			
			String urlTxt = URL_OVERPASS_API + "?data=" + URLEncoder.encode(data, "UTF-8");
			
			
			URL url = new URL(urlTxt);
		    URLConnection urlConn = url.openConnection();
		    // urlConn.setRequestProperty("Accept-Charset", "UTF-8");

		    
		    // Get connection inputstream
		    InputStream is = urlConn.getInputStream();

		    BufferedReader s = new BufferedReader(new InputStreamReader(is));
		    String line = s.readLine();
		    while (line != null) {
		    	response.append(line);
		    	line = s.readLine();
		    }
		    s.close();
	      
		} catch (IOException e) {
	      e.printStackTrace();
	    }
	    
		return response.toString();

	}
}

