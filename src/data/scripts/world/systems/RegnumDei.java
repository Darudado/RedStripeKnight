package data.scripts.world.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.util.Misc;
import data.scripts.utils.String_RS;


import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

import static com.fs.starfarer.api.impl.campaign.ids.Conditions.VOLATILES_PLENTIFUL;
import static data.scripts.RSModPlugin.*;
import static data.scripts.utils.String_RS.*;
import static data.scripts.world.RSNormalGenerate.addMarketplace;

// 修改文件名和下方为 你的星系名
// 在教程范例中为 Nipher
public class RegnumDei implements SectorGeneratorPlugin {

	public void generate(SectorAPI sector) {
        
        StarSystemAPI system = sector.createStarSystem("RegnumDei");

		system.getLocation().set(350F, -20500F);

		system.setBackgroundTextureFilename("graphics/backgrounds/rs_background1.jpg");

        system.setLightColor(new Color(255, 210, 159));

        PlanetAPI star = system.initStar(
                "Constantinople",
                "star_red_supergiant",
                1200f,
                600f,
                3.0f,
                1.5f,
                1.0f
        );

        star.setCustomDescriptionId("Constantinople");

        // --- 1. 恒星吸积盘 ---
        system.addRingBand(star, "misc", "rings_dust0", 256f, 0, Color.red, 2048f, 2000f, 40f, Terrain.ASTEROID_BELT, "accretion disk α");
        system.addRingBand(star, "misc", "rings_ice0", 256f, 0, new Color(245,110,100, 175), 2048f, 2500f, 60f);

        system.addAsteroidBelt(
                star,
                140,
                5500f,
                700f,
                180,
                360,
                Terrain.ASTEROID_BELT,
                ""
        );
        
        // 稍后将在此加入星球生成代码
        SectorEntityToken A = system.addCustomEntity("RS_A", "Regnum Dei Comm Relay", "comm_relay", "red_stripe");
        A.setCircularOrbit(star, 180f, 2900f, 365f);
        SectorEntityToken B = system.addCustomEntity("RS_B", "Regnum Dei Nav Buoy", "nav_buoy", "red_stripe");
        B.setCircularOrbit(star, 220f, 2500f, 365f);
        SectorEntityToken C = system.addCustomEntity("RS_C", "Regnum Dei Sensor Array", "sensor_array", "red_stripe");
        C.setCircularOrbit(star, 240f, 2900f, 365f);
        
		
		/*
         * [
         * 
         * 
         */
            SectorEntityToken gate = system.addCustomEntity("RS_gate", // unique id 设置星门id
                    "Gate of the Holy Abyss", // name - if null, defaultName from custom_entities.json will be used 设置你星门的名字
                    "inactive_gate", // type of object, defined in custom_entities.json 设置标签（让系统识别这是个星门）根据custom_entities.json设置
                    null);
        // 
        gate.setCircularOrbit(star, 250, 5750, 687);

            PlanetAPI planet1 = system.addPlanet(
                    "RS_planet1", //行星ID
                    star, //恒星ID
                    "Terra Sancta", //星球名字
                    "terran", //类型
                    215,
                    180f,
                    4000f,
                    365f
            );
            system.addAsteroidBelt(star,
                    150, 4000f, 180f, 180, 360, Terrain.RING, ""
            );
            planet1.setFaction("red_stripe"); //行星所属势力
            planet1.getSpec().setGlowColor(new Color(255, 210, 159));   //行星散射光照(可以理解为大气层发光)
            planet1.getSpec().setUseReverseLightForGlow(true);
            planet1.getSpec().setCloudColor(new Color(255, 222, 198, 150));    //行星de云
            planet1.applySpecChanges(); //行星应用特殊设置
            planet1.getSpec().setAtmosphereThicknessMin(25);
            planet1.getSpec().setAtmosphereThickness(0.2f);
            planet1.getSpec().setAtmosphereColor( new Color(80, 92, 100,120) );
            applyVisuals_earth(planet1);
            Misc.initConditionMarket(planet1);

            MarketAPI planet1Market = addMarketplace(
                    "red_stripe",
                    planet1,
                    null,
                    planet1.getName(),
                    7,
                    new ArrayList<>(
                            Arrays.asList(
                                    Conditions.POPULATION_7, // 设置殖民地规模
                                    //这几块都在设置环境
                                    Conditions.MILD_CLIMATE, //芜热
                                    Conditions.ORE_RICH, //恶劣天气
                                    Conditions.RUINS_VAST,
                                    Conditions.RARE_ORE_MODERATE,
                                    Conditions.LOW_GRAVITY,
                                    Conditions.FARMLAND_BOUNTIFUL,
                                    Conditions.ORGANICS_PLENTIFUL)), //
                    new ArrayList<>(
                            Arrays.asList(
                                    //这几块都在设置市场类型
                                    Submarkets.SUBMARKET_OPEN,
                                    Submarkets.GENERIC_MILITARY,
                                    Submarkets.SUBMARKET_STORAGE,
                                    "RS_OrdoPraetorianorumMarket"
                            )),
                    new ArrayList<>(
                            Arrays.asList(
                                    Industries.POPULATION, //这几块都在设置工业区划建设
                                    Industries.MEGAPORT,
                                    Industries.STARFORTRESS_HIGH,
                                    Industries.HEAVYBATTERIES,
                                    Industries.REFINING,
                                    Industries.ORBITALWORKS,
                                    Industries.WAYSTATION,
                                    Industries.HIGHCOMMAND,
                                    Industries.FARMING,
                                    String_RS.Guard1
                            )
                    ),
            0.3f,
            false,
            true
                    );

            planet1.setCustomDescriptionId("RS_planet1_description");

            Industry red_stripeMegaport = planet1Market.getIndustry("megaport");
            red_stripeMegaport.setAICoreId("gamma_core");

            Industry red_stripePopulation = planet1Market.getIndustry("population");
            red_stripePopulation.setAICoreId("gamma_core");

            Industry red_stripeHighCommand = planet1Market.getIndustry("highcommand");
            red_stripeHighCommand.setAICoreId("alpha_core");



            PlanetAPI planet2 = system.addPlanet(
                    "RS_planet2", //行星ID
                    star, //恒星ID
                    "Vallis Lacrimarum", //星球名字
                    "barren", //类型
                    320,
                    180f,
                    6000f,
                    650f
            );
            planet2.setFaction("red_stripe"); //行星所属势力
            planet2.getSpec().setGlowColor(new Color(255, 200, 200));   //行星散射光照(可以理解为大气层发光)
            planet2.getSpec().setUseReverseLightForGlow(true);
            planet2.applySpecChanges(); //行星应用特殊设置
            planet2.getSpec().setAtmosphereColor( new Color(83, 95, 106,120) );
            applyVisuals_barren(planet2);
            Misc.initConditionMarket(planet2);
        // 
            MarketAPI planet2Market = addMarketplace(
                    "red_stripe",
                    planet2,
                    null,
                    planet2.getName(),
                    6,
                    new ArrayList<>(
                            Arrays.asList(
                                    Conditions.POPULATION_6, // 设置殖民地规模
                                    //这几块都在设置环境
                                    Guard2_condition,
                                    Conditions.VERY_HOT, //
                                    Conditions.RUINS_VAST,
                                    Conditions.RARE_ORE_MODERATE,
                                    Conditions.LOW_GRAVITY,
                                    Conditions.NO_ATMOSPHERE,
                                    Conditions.ORE_RICH)), //
                    new ArrayList<>(
                            Arrays.asList(
                                    //这几块都在设置市场类型
                                    Submarkets.SUBMARKET_OPEN,
                                    Submarkets.GENERIC_MILITARY,
                                    Submarkets.SUBMARKET_STORAGE)),
                    new ArrayList<>(
                            Arrays.asList(
                                    Industries.POPULATION, //这几块都在设置工业区划建设
                                    Industries.MEGAPORT,
                                    Industries.STARFORTRESS_HIGH,
                                    Industries.HEAVYBATTERIES,
                                    Industries.REFINING,
                                    Industries.ORBITALWORKS,
                                    Industries.WAYSTATION,
                                    Industries.HIGHCOMMAND,
                                    Industries.FUELPROD,
                                    Guard2
                            )
                    ),
                    0.3f,
                    false,
                    true
            );


        planet2.setCustomDescriptionId("RS_planet2_description");

            Industry red_stripeOrbitalWorks = planet2Market.getIndustry("orbitalworks");
            red_stripeOrbitalWorks.setAICoreId("alpha_core");

        Industry red_stripeHighCommand2 = planet2Market.getIndustry(Industries.HIGHCOMMAND);
        red_stripeHighCommand2.setAICoreId("alpha_core");

            planet2Market.getIndustry(Industries.ORBITALWORKS).setSpecialItem(new SpecialItemData(Items.PRISTINE_NANOFORGE, null));
        planet2Market.getIndustry(Industries.HIGHCOMMAND).setSpecialItem(new SpecialItemData(Items.CRYOARITHMETIC_ENGINE, null));
        

        system.autogenerateHyperspaceJumpPoints(true, true);


        PlanetAPI planet3 = system.addPlanet(
                "RS_planet3", //行星ID
                star, //恒星ID
                "Ardor Ignis", //星球名字
                "gas_giant", //类型
                350,
                650f,
                9500f,
                775f
        );
        planet3.setFaction("red_stripe"); //行星所属势力
        planet3.getSpec().setGlowColor(new Color(175, 15, 15));   //行星散射光照(可以理解为大气层发光)
        planet3.getSpec().setUseReverseLightForGlow(true);
        planet3.applySpecChanges(); //行星应用特殊设置
        planet3.getSpec().setAtmosphereColor( new Color(216, 25, 25,75) );
        applyVisuals_giant(planet3);
        Misc.initConditionMarket(planet3);

        MarketAPI planet3Market = addMarketplace(
                "red_stripe",
                planet3,
                null,
                planet3.getName(),
                6,
                new ArrayList<>(
                        Arrays.asList(
                                Conditions.POPULATION_6, // 设置殖民地规模
                                //这几块都在设置环境
                                Conditions.VERY_COLD, //
                                VOLATILES_PLENTIFUL,
                                Conditions.RUINS_VAST,
                                Conditions.LOW_GRAVITY)), //
                new ArrayList<>(
                        Arrays.asList(
                                //这几块都在设置市场类型
                                Submarkets.SUBMARKET_OPEN,
                                Submarkets.GENERIC_MILITARY,
                                Submarkets.SUBMARKET_BLACK,
                                Submarkets.SUBMARKET_STORAGE)),
                new ArrayList<>(
                        Arrays.asList(
                                Industries.POPULATION, //这几块都在设置工业区划建设
                                Industries.MEGAPORT,
                                Industries.STARFORTRESS_HIGH,
                                Industries.HEAVYBATTERIES,
                                Industries.MINING,
                                Industries.ORBITALWORKS,
                                Industries.WAYSTATION,
                                Industries.HIGHCOMMAND,
                                Industries.FUELPROD
                        )
                ),
                0.3f,
                true,
                true
        );


        planet3.setCustomDescriptionId("RS_planet3_description");

        Industry red_fule = planet3Market.getIndustry(Industries.FUELPROD);
        red_fule.setAICoreId("alpha_core");

        planet3Market.getIndustry(Industries.FUELPROD).setSpecialItem(new SpecialItemData(Items.SYNCHROTRON, null));

        SectorEntityToken RS1 = system.addCustomEntity("rs_oath_battlestation",
                "Oath Keep",
                "rs_oath_station",
                null);
        RS1.setCircularOrbitPointingDown(star, 0, 4500, 90);

        Misc.setAbandonedStationMarket("corvus_abandoned_station_market", RS1);
        RS1.setCustomDescriptionId("rs_oath_station"); // 设置自定义描述ID
        RS1.setInteractionImage("illustrations", "abandoned_station2"); // 设置交互图像
        RS1.getMarket().getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().addMothballedShip(FleetMemberType.SHIP, "rs_typhaon_variant", null);


        SectorEntityToken RS2 = system.addCustomEntity("rs_arxcaelestis_battlestation",
                "Arx Caelestis",
                "rs_arxcaelestis",
                "red_stripe");
        RS2.setCircularOrbitPointingDown(planet3, 0, 900, 45);

        MarketAPI M02 = addMarketplace(
                "red_stripe",
                RS2,
                null,
                RS2.getName(),
                6,
                new ArrayList<>(
                        Arrays.asList(
                                Conditions.POPULATION_6, // 设置殖民地规模
                                //这几块都在设置环境
                                VOLATILES_PLENTIFUL,
                                Guard2_condition,
                                Conditions.LOW_GRAVITY)), //
                new ArrayList<>(
                        Arrays.asList(
                                //这几块都在设置市场类型
                                "RS_OrdoPraetorianorumMarket",
                                Submarkets.GENERIC_MILITARY,
                                Submarkets.SUBMARKET_BLACK,
                                Submarkets.SUBMARKET_STORAGE)),
                new ArrayList<>(
                        Arrays.asList(
                                Industries.POPULATION, //这几块都在设置工业区划建设
                                Industries.MEGAPORT,
                                Industries.STARFORTRESS_HIGH,
                                Industries.HEAVYBATTERIES,
                                Industries.MINING,
                                Industries.ORBITALWORKS,
                                Industries.WAYSTATION,
                                Industries.HIGHCOMMAND,
                                Guard3,
                                Industries.FUELPROD
                        )
                ),
                0.3f,
                true,
                true
        );

        M02.getTariff().modifyFlat("default_tariff", RS2.getFaction().getTariffFraction());

        Global.getSector().getEconomy().addMarket(M02, true);
        Industry HighCommand = M02.getIndustry(Industries.HIGHCOMMAND);
        HighCommand.setAICoreId("alpha_core");
        Industry ObitalWorks = M02.getIndustry(Industries.ORBITALWORKS);
        ObitalWorks.setAICoreId("alpha_core");
        ObitalWorks.setSpecialItem(new SpecialItemData(Items.CORRUPTED_NANOFORGE, null));
        Industry FueProd = M02.getIndustry(Industries.FUELPROD);
        FueProd.setAICoreId("alpha_core");



        if(HAVE_INDEVO){
            if(INDEVO_ARTY){
                if(!planet1Market.hasCondition("IndEvo_ArtilleryStationCondition")){
                    planet1Market.addCondition("IndEvo_ArtilleryStationCondition");
                    planet1Market.addIndustry("IndEvo_Artillery_missile");

                }
                if(!M02.hasCondition("IndEvo_ArtilleryStationCondition")){
                    M02.addCondition("IndEvo_ArtilleryStationCondition");
                    M02.addIndustry("IndEvo_Artillery_mortar");
                }
                if(!planet3Market.hasCondition("IndEvo_ArtilleryStationCondition")){
                    planet3Market.addCondition("IndEvo_ArtilleryStationCondition");
                    planet3Market.addIndustry("IndEvo_Artillery_railgun");
                }

                Industry Artillery1 = planet1Market.getIndustry("IndEvo_Artillery_missile");
                Artillery1.setImproved(true);
                Artillery1.setAICoreId("alpha_core");
                Industry Artillery2 = M02.getIndustry("IndEvo_Artillery_mortar");
                Artillery2.setAICoreId("alpha_core");
                Artillery2.setImproved(true);
                Industry Artillery3 = planet3Market.getIndustry("IndEvo_Artillery_railgun");
                Artillery3.setAICoreId("alpha_core");
                Artillery3.setImproved(true);
            }
            if(INDEVO_MINES) {
               // if (!sector.getMemoryWithoutUpdate().getBoolean("$tahlan_minesDeployed")) {
                    //sector.getMemoryWithoutUpdate().set("$tahlan_minesDeployed", true);
                   // for (SectorEntityToken point : sector.getStarSystem("Rubicon").getJumpPoints()) {
                        //MineBeltTerrainPlugin belt = (MineBeltTerrainPlugin) MineBeltTerrainPlugin.addMineBelt(point, 300f, 150f, 30f, 40f, point.getName() + " Minefield");
                        //belt.getMemoryWithoutUpdate().set("$IndEvo_PlanetMinefieldKey", planet2Market.getPrimaryEntity());
                        //belt.setEntity(planet2);
                   // }
               // }
                if (!planet2Market.hasCondition("IndEvo_mineFieldCondition")) {
                    planet2Market.addCondition("IndEvo_mineFieldCondition");
                }

            }
        }


    }
    public static void applyVisuals_earth(PlanetAPI planet) {
        if (planet != null) {
            planet.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "rs_earth"));
            planet.getSpec().setShieldThickness(0F);
            planet.getSpec().setShieldColor(new Color(255, 255, 255, 175));
            planet.applySpecChanges();
        }
    }

    public static void applyVisuals_barren(PlanetAPI planet) {
        if (planet != null) {
            planet.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "rs_dark"));
            planet.getSpec().setShieldThickness(0F);
            planet.getSpec().setShieldColor(new Color(255, 255, 255, 175));
            planet.applySpecChanges();
        }
    }

    public static void applyVisuals_giant(PlanetAPI planet) {
        if (planet != null) {
            planet.getSpec().setTexture(Global.getSettings().getSpriteName("planets", "rs_hot_jupiter"));
            planet.getSpec().setShieldThickness(0F);
            planet.getSpec().setShieldColor(new Color(255, 255, 255, 175));
            planet.applySpecChanges();
        }
    }


}