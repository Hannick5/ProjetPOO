package eu.ensg.ign;

import java.util.List;

public class Resultat {
	
	private String profile;
	public void setProfile(String profile) { this.profile = profile; }
	public String getProfile() { return this.profile; }
	
	// ....
	
	private List<Portion> portions;
	public List<Portion> getPortions() { return this.portions; }
	public void setPortions(List<Portion> portions) { this.portions = portions; }
}
