package data.missions.revenge;

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

		api.setFleetTagline(FleetSide.PLAYER, "ship of revenge");
		api.setFleetTagline(FleetSide.ENEMY, "Akira's left path plundering fleet is returning");

		api.addBriefingItem("All criminals deserve the punishment they deserve - their own destruction");
		api.addBriefingItem("You should know that revenge is to move towards the future, not to abandon the future");
		api.addBriefingItem("Your crew has a bright future, and it's up to you to take them home - the road can be paved with blood and honor.");
		api.addBriefingItem("Use your mobility and firepower to gradually weaken the enemy. Remember that a swarm of ants can kill an elephant. Please be lenient to the enemy.");


		api.addToFleet(FleetSide.PLAYER, "rs_nazaret_Energy", FleetMemberType.SHIP, "RS Abaddōn", true);

		api.addToFleet(FleetSide.ENEMY, "prometheus2_Standard", FleetMemberType.SHIP, true);
		api.addToFleet(FleetSide.ENEMY, "prometheus2_Standard", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "prometheus2_Standard", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "prometheus2_Standard", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "prometheus2_Standard", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "prometheus2_Standard", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "venture_pather_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "venture_pather_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "venture_pather_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "venture_pather_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "venture_pather_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "brawler_pather_Raider", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "brawler_pather_Raider", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "brawler_pather_Raider", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "brawler_pather_Raider", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "brawler_pather_Raider", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "brawler_pather_Raider", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "cerberus_luddic_path_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "cerberus_luddic_path_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "cerberus_luddic_path_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "cerberus_luddic_path_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "cerberus_luddic_path_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "cerberus_luddic_path_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "cerberus_luddic_path_Attack", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "lasher_luddic_path_Raider", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "lasher_luddic_path_Raider", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "lasher_luddic_path_Raider", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "lasher_luddic_path_Raider", FleetMemberType.SHIP, false);
		api.addToFleet(FleetSide.ENEMY, "lasher_luddic_path_Raider", FleetMemberType.SHIP, false);

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