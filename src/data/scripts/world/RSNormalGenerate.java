package data.scripts.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import data.scripts.world.systems.RegnumDei;

import java.util.ArrayList;

public class RSNormalGenerate implements SectorGeneratorPlugin{

	public void generate(SectorAPI sector) {
		new RegnumDei().generate(sector);
		initFactionRelation(Global.getSector());
		relationAdj(Global.getSector());
	}

	public static void relationAdj(SectorAPI sector)
	{
		FactionAPI RS = sector.getFaction("red_stripe");
		// 设置势力好感度
		RS.setRelationship(Factions.LUDDIC_CHURCH, 0f);
		RS.setRelationship(Factions.LUDDIC_PATH, -0.6f);
		RS.setRelationship(Factions.TRITACHYON, -0.85f);
		RS.setRelationship(Factions.PERSEAN, 0.3f);
		RS.setRelationship(Factions.PIRATES, -1.0f);
		RS.setRelationship(Factions.INDEPENDENT, 0.1f);
		RS.setRelationship(Factions.LIONS_GUARD, 0.0f);
		RS.setRelationship(Factions.HEGEMONY, 0.65f);
		RS.setRelationship(Factions.DIKTAT, 0.0f);
		RS.setRelationship(Factions.REMNANTS, 0.4f);
		RS.setRelationship(Factions.OMEGA, 0.6f);
		SharedData.getData().getPersonBountyEventData().addParticipatingFaction("red_stripe");
	}

	public static void initFactionRelation(SectorAPI sector) {
		FactionAPI CR = sector.getFaction("cinis_of_crusaders");
		SharedData.getData().getPersonBountyEventData().addParticipatingFaction("cinis_of_crusaders");
		for (FactionAPI faction : sector.getAllFactions()) {
			if (faction != CR ) {
				CR.setRelationship(faction.getId(), RepLevel.VENGEFUL);
				CR.setRelationship(Factions.PLAYER, RepLevel.VENGEFUL);
			}
		}
	}

	public static MarketAPI addMarketplace(String factionID, SectorEntityToken primaryEntity, ArrayList<SectorEntityToken> connectedEntities, String name,
										   int size, ArrayList<String> marketConditions, ArrayList<String> submarkets, ArrayList<String> industries, float tarrif,
										   boolean freePort, boolean withJunkAndChatter) {
		EconomyAPI globalEconomy = Global.getSector().getEconomy();
		String planetID = primaryEntity.getId();
		String marketID = planetID + "_market";

		MarketAPI newMarket = Global.getFactory().createMarket(marketID, name, size);
		newMarket.setFactionId(factionID);
		newMarket.setPrimaryEntity(primaryEntity);
		newMarket.getTariff().modifyFlat("generator", tarrif);

		//Adds submarkets
		if (null != submarkets) {
			for (String market : submarkets) {
				newMarket.addSubmarket(market);
			}
		}

		//Adds market conditions
		for (String condition : marketConditions) {
			newMarket.addCondition(condition);
		}

		//Add market industries
		for (String industry : industries) {
			newMarket.addIndustry(industry);
		}

		//Sets us to a free port, if we should
		newMarket.setFreePort(freePort);

		//Adds our connected entities, if any
		if (null != connectedEntities) {
			for (SectorEntityToken entity : connectedEntities) {
				newMarket.getConnectedEntities().add(entity);
			}
		}

		globalEconomy.addMarket(newMarket, withJunkAndChatter);
		primaryEntity.setMarket(newMarket);
		primaryEntity.setFaction(factionID);

		if (null != connectedEntities) {
			for (SectorEntityToken entity : connectedEntities) {
				entity.setMarket(newMarket);
				entity.setFaction(factionID);
			}
		}

		//Finally, return the newly-generated market
		return newMarket;
	}

}
