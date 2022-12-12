package eu.ensg.exemple;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import eu.ensg.osm.HttpClientOsm;

public class MainExampleOpenStreetMap {
	
	private static double E = 2.59042;
	private static double O = 2.58640;
	private static double S = 48.84051;
	private static double N = 48.84202;

	public static void main(String[] args) throws ParserConfigurationException {
		
		String dataRequest = "<osm-script>"
				+ "<union>"
				+ "<query type=\"node\">"
				+ "<bbox-query e=\"" + E + "\" n=\"" + N + "\" s=\"" + S + "\" w=\"" + O + "\" />"
				+ "</query>"
				+ "</union>"
				+ "<print mode=\"meta\"/>"
				+ "</osm-script>";
		String xmldata = HttpClientOsm.getOsmXML(dataRequest);
		// System.out.println(xml);
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		try {
			Document doc = builder.parse(new ByteArrayInputStream(xmldata.getBytes()));
			doc.getDocumentElement().normalize();
		    Element root = (Element) doc.getElementsByTagName("osm").item(0);
		
		    int nbNoeuds = root.getElementsByTagName("node").getLength();
		    for (int i = 0; i < nbNoeuds; i++) {

		    	Element elem = (Element) root.getElementsByTagName("node").item(i);

		    	// On récupère son ID
		    	long id = Long.valueOf(elem.getAttribute("id"));

		    	// on récupère sa géométrie
		    	double lat = Double.valueOf(elem.getAttribute("lat"));
		    	double lon = Double.valueOf(elem.getAttribute("lon"));
		    	
		    	
		    	for (int j = 0; j < elem.getElementsByTagName("tag").getLength(); j++) {
					Element tagElem = (Element) elem.getElementsByTagName("tag").item(j);
					String cle = tagElem.getAttribute("k");
					String val = tagElem.getAttribute("v");
					
					if (cle.equals("name")) {
						System.out.println(cle + "--" + val);
						System.out.println(lon + "," + lat);
					}
						
		    	}
		    	
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

