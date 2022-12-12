/*******************************************************************************
 * 
 * @copyright IGN - 2018
 * 
 * This software is released under the licence CeCILL
 * see <a href="https://fr.wikisource.org/wiki/Licence_CeCILL_version_2">
 * https://fr.wikisource.org/wiki/Licence_CeCILL_version_2</a>
 *
 ******************************************************************************/
package eu.ensg.portail;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 * WMTS Capabilities to get some metadata.
 * 
 * @author M.-D. Van Damme
 */
public class PyramideFondOrtho {
    
    private HashMap<String, int[]> tileMatrixMap = new HashMap<String, int[]>();
    
    /**
     * 
     * @param args
     */
    public void getTileMatrix() {
        
        try {
            // ClassLoader classLoader = getClass().getClassLoader();
            // File wmtsFile = new File(classLoader.getResource("wmts.xml").getFile());
            
            InputStream is = new FileInputStream(new File("./data/wmts.xml")); // getClass().getResourceAsStream("wmts.xml");
            
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setNamespaceAware(true);
            builderFactory.setValidating(false);
            DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();

            // Document rawDocument = documentBuilder.parse(wmtsFile);
            Document rawDocument = documentBuilder.parse(is);
            
            Element capabilities = rawDocument.getDocumentElement();
            NodeList tileMS = capabilities.getElementsByTagName("TileMatrixSet");
            for (int i = 0; i < tileMS.getLength(); i++) {
                Node n1 = tileMS.item(i);
                if (n1 instanceof Element) {
                    Element e = (Element) n1;
                    if (e.getElementsByTagName("ows:Identifier").getLength() > 0) {
                        if (e.getElementsByTagName("ows:Identifier").item(0).getTextContent().equals("PM")) {
                            // TileMatrix
                            for (int j = 0; j < e.getElementsByTagName("TileMatrix").getLength(); j++) {
                                Node tileNoeud = e.getElementsByTagName("TileMatrix").item(j);
                                if (tileNoeud instanceof Element) {
                                    Element tileElt = (Element) tileNoeud;
                                    
                                    
                                    String zoom = tileElt.getElementsByTagName("ows:Identifier").item(0).getTextContent();
                                    
                                    // TopLeftCorner à récupérer
                                    String topLeftCorner = tileElt.getElementsByTagName("TopLeftCorner").item(0).getTextContent();
                                    String[] parts = topLeftCorner.split(" ");
                                    int x0 = Double.valueOf(parts[0]).intValue();
                                    int y0 = Double.valueOf(parts[1]).intValue();
                                        
                                    int[] coord = new int[2];
                                    coord[0] = x0;
                                    coord[1] = y0;
                                    
                                    tileMatrixMap.put(zoom, coord);    
                                }
                            }
                        }
                    }
                    
                }
                
            }

        } catch(Exception e) {
            e.printStackTrace();
        }

    }
    
    
    /**
     * @param zoom
     * @return Return top left coordonate
     */
    public int[] getTopLeftCorner(String zoom) {
        return tileMatrixMap.get(zoom);
    }

}
