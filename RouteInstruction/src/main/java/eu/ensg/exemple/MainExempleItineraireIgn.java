package eu.ensg.exemple;

import java.util.Arrays;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import eu.ensg.ign.HttpClientIgn;
import eu.ensg.ign.Portion;
import eu.ensg.ign.Resultat;


public class MainExempleItineraireIgn {

	public static void main(String[] args) {

		String url = "https://wxs.ign.fr/calcul/geoportail/itineraire/rest/1.0.0/route?resource=bdtopo-pgr"
				+ "&profile=pedestrian&optimization=fastest"
				+ "&start=2.3547965564258226,48.84615526548436&end=2.3698762993655165,48.84911418041841"
				+ "&intermediates=&constraints={\"constraintType\":\"banned\",\"key\":\"wayType\",\"operator\":\"=\",\"value\":\"tunnel\"}"
				+ "&geometryFormat=geojson&crs=EPSG:4326&getSteps=true&getBbox=true&waysAttributes=nature";
		String txtJson = HttpClientIgn.request(url);
		// System.out.println(txtJson);
		
		Gson gson = new GsonBuilder().create();
		Resultat itineraire = gson.fromJson(txtJson, Resultat.class);
		for (Portion portion : itineraire.getPortions()) {
			
			String[] start = portion.getStart().split(",");
			double lon = Double.parseDouble(start[0]);
			double lat = Double.parseDouble(start[1]);
			System.out.println(lon + "," + lat + "--" + Arrays.toString(start));
		}
	}
	
}
