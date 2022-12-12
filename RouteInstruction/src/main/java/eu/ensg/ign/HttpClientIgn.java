package eu.ensg.ign;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

public class HttpClientIgn {
	
	public static String request(String urlTxt) {
		System.setProperty("jdk.tls.maxHandshakeMessageSize", "99999");
		StringBuffer response = new StringBuffer();
		try {
			
			URL url = new URL(urlTxt);
		    URLConnection urlConn = url.openConnection();
		    urlConn.setRequestProperty("Accept-Charset", "UTF-8");
		    
		    InputStream is = urlConn.getInputStream(); 
		    BufferedReader s = new BufferedReader(new InputStreamReader(is));
		    String line = s.readLine();
		    String txtJson = line;
		    while (line != null) {
		    	response.append(line);
		    	line = s.readLine();
		    	if (line != null) {
		    		txtJson = txtJson + line;
		    	}
		    }
		    s.close();
		    
		    if (txtJson == null || txtJson == "") {
		    	System.exit(1);
	        }
		    // System.out.println(txtJson);
		    return txtJson;
		
		} catch (Exception e) {
			System.out.println("Problem with API");
			e.printStackTrace();
		}
		return null;
	}

}
