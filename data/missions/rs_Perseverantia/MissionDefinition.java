package data.missions.rs_Perseverantia;

// shared by Nihilism, modified from AnyIDElse under license

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MissionDefinition implements MissionDefinitionPlugin {


	@Override
	public void defineMission(MissionDefinitionAPI api) {
		api.initFleet(FleetSide.PLAYER, "RS", FleetGoal.ATTACK, false, 5);
		api.initFleet(FleetSide.ENEMY, "LP", FleetGoal.ATTACK, true, 5);

		api.setFleetTagline(FleetSide.PLAYER, "一支小舰队");
		api.setFleetTagline(FleetSide.ENEMY, "碰运气的左径掠夺舰队先锋");

		api.addBriefingItem("虽然措手不及，但是你们有过预案");
		api.addBriefingItem("你很清楚，你们的机动性是短板");
		api.addBriefingItem("善用你手下舰船的充足韧性");
		api.addBriefingItem("尽量减少损失");


		api.addToFleet(FleetSide.PLAYER, "rs_leviathan_variant", FleetMemberType.SHIP, "Pax", true);
		api.addToFleet(FleetSide.PLAYER, "rs_xun_standard", FleetMemberType.SHIP, "Patientia", false);
		api.addToFleet(FleetSide.PLAYER, "rs_avaritia_Strike", FleetMemberType.SHIP, "Gaudium", false);
		api.addToFleet(FleetSide.PLAYER, "rs_avaritia_variant", FleetMemberType.SHIP, "Caritas", false);

		api.addToFleet(FleetSide.ENEMY, "prometheus2_Standard", FleetMemberType.SHIP, true);
		api.addToFleet(FleetSide.ENEMY, "venture_pather_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "venture_pather_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "brawler_pather_Raider", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "brawler_pather_Raider", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "cerberus_luddic_path_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "cerberus_luddic_path_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "cerberus_luddic_path_Attack", FleetMemberType.SHIP, false);

		float width = 10000f;
		float height = 10000f;
		api.initMap(-width / 2f, width / 2f, -height / 2f, height / 2f);

		float minX = -width / 2;
		float minY = -height / 2;

		api.addNebula(minX + width * 0.66f, minY + height * 0.5f, 2000);
		api.addNebula(minX + width * 0.25f, minY + height * 0.6f, 1000);
		api.addNebula(minX + width * 0.25f, minY + height * 0.4f, 1000);

		for (int i = 0; i < 5; i++) {
			float x = (float) Math.random() * width - width / 2;
			float y = (float) Math.random() * height - height / 2;
			float radius = 100f + (float) Math.random() * 400f;
			api.addNebula(x, y, radius);
		}

		api.addObjective(minX + width * 0.25f + 2000f, minY + height * 0.5f, "sensor_array");
		api.addObjective(minX + width * 0.75f - 2000f, minY + height * 0.5f, "comm_relay");
		api.addObjective(minX + width * 0.33f + 2000f, minY + height * 0.4f, "nav_buoy");
		api.addObjective(minX + width * 0.66f - 2000f, minY + height * 0.6f, "nav_buoy");

		api.addAsteroidField(-(minY + height), minY + height, -45, 2000f, 20f, 70f, 100);
	}
}