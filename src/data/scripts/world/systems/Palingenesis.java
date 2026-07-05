package data.scripts.world.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;

import java.awt.*;

import static com.fs.starfarer.api.impl.campaign.ids.Conditions.*;
import static com.fs.starfarer.api.impl.campaign.ids.Industries.*;
import static com.fs.starfarer.api.impl.campaign.ids.Submarkets.GENERIC_MILITARY;

public class Palingenesis implements SectorGeneratorPlugin{

    @Override
    public void generate(SectorAPI sector) {
        StarSystemAPI system = sector.createStarSystem("Palingenesis");

        // 使用 Random 类生成随机坐标
        system.getLocation().set(-5750, -50000);
        system.setLightColor(new Color(255, 210, 159));

        system.setBackgroundTextureFilename("graphics/backgrounds/rs_background2.jpg");

        system.getTags().add(Tags.THEME_HIDDEN);

        PlanetAPI star = system.initStar(
                "Eremia",
                "star_blue_supergiant",
                1500f,
                750f,
                3.0f,
                1.5f,
                1.0f
        );

        system.addAsteroidBelt(
                star,
                175,
                5500f,
                700f,
                180,
                360,
                Terrain.ASTEROID_BELT,
                ""
        );


        system.addRingBand(star, "misc", "rings_asteroids0", 256f, 3, Color.white, 256f, 2200, 345f, null, null);


        SectorEntityToken A = system.addCustomEntity("CR1", "Palingenesis Comm Relay", "comm_relay", "red_stripe_vower");
        A.setCircularOrbit(star, 180f, 2900f, 365f);
        SectorEntityToken B = system.addCustomEntity("CR2", "Palingenesis Nav Buoy", "nav_buoy", "red_stripe_vower");
        B.setCircularOrbit(star, 220f, 2500f, 365f);

        system.autogenerateHyperspaceJumpPoints(true, true);

        PlanetAPI planet1 = system.addPlanet(
                "Planet1", //行星ID
                star, //恒星ID
                "Alpha_1", //星球名字
                "barren", //类型
                215,
                180f,
                4000f,
                365f
        );
        planet1.hasCondition(HOT);
        planet1.hasCondition(NO_ATMOSPHERE);
        planet1.hasCondition(LOW_GRAVITY);
        planet1.hasCondition(VOLATILES_PLENTIFUL);
        planet1.hasCondition(ORE_ULTRARICH);
        planet1.hasCondition(RARE_ORE_ABUNDANT);

        PlanetAPI planet2 = system.addPlanet(
                "Planet2", star, "Alpha_2", "lava_minor", 30, 110, 2850, 90
        );
        planet2.hasCondition(HOT);
        planet2.hasCondition(NO_ATMOSPHERE);
        planet2.hasCondition(ORE_ABUNDANT);
        planet2.hasCondition(RARE_ORE_ABUNDANT);

        PlanetAPI planet3 = system.addPlanet(
                "Planet3", star, "Alpha_3", "barren-bombarded", 80, 130, 6800, 225
        );
        planet3.hasCondition(VERY_HOT);
        planet3.hasCondition(TOXIC_ATMOSPHERE);
        planet3.hasCondition(EXTREME_TECTONIC_ACTIVITY);
        planet3.hasCondition(LOW_GRAVITY);
        planet3.hasCondition(VOLATILES_PLENTIFUL);
        planet3.hasCondition(ORE_ULTRARICH);
        planet3.hasCondition(RARE_ORE_ULTRARICH);

        PlanetAPI planet4 = system.addPlanet(
                "Planet4", star, "Alpha_4", "gas_giant", 230, 275, 9500, 450
        );
        planet4.getSpec().setPlanetColor(new Color(150,245,255,255));
        planet4.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "banded"));
        planet4.getSpec().setGlowColor(new Color(250,225,55,64));
        planet4.getSpec().setUseReverseLightForGlow(true);
        planet4.applySpecChanges();
        planet4.hasCondition(VERY_HOT);
        planet4.hasCondition(DENSE_ATMOSPHERE);
        planet4.hasCondition(VOLATILES_PLENTIFUL);
        planet4.hasCondition(RUINS_EXTENSIVE);

        PlanetAPI planet5 = system.addPlanet(
                "Planet5", star, "Alpha_5", "gas_giant", 250, 280, 12050, 650
        );
        planet5.getSpec().setPlanetColor(new Color(170,190,255,255));
        planet5.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "banded"));
        planet5.getSpec().setGlowColor(new Color(250,225,155,32));
        planet5.applySpecChanges();
        planet5.hasCondition(VERY_HOT);
        planet5.hasCondition(DENSE_ATMOSPHERE);
        planet5.hasCondition(VOLATILES_PLENTIFUL);
        planet5.hasCondition(RUINS_EXTENSIVE);

        SectorEntityToken Cusader_GATEHAULER = system.addCustomEntity("VOW_Entity1",
                "Cinis Alpha",
                "cr_gatehauler",
                "red_stripe_vower");
        Cusader_GATEHAULER.setCircularOrbitPointingDown(star, 0, 3000, 45);

// 创建市场
        MarketAPI M01 = Global.getFactory().createMarket("VOW_Entity1", Cusader_GATEHAULER.getName(), 6);

// 先设置市场到实体并确保位置正确
        Cusader_GATEHAULER.setMarket(M01);
        Cusader_GATEHAULER.setContainingLocation(system); // 确保实体在系统中

// 设置市场属性
        M01.setPrimaryEntity(Cusader_GATEHAULER);
        M01.setSurveyLevel(MarketAPI.SurveyLevel.NONE);
        M01.setFactionId("red_stripe_vower");
        M01.addCondition("population_6");
        M01.addCondition("CR_Defense");
        M01.addIndustry("population");
        M01.addIndustry(MEGAPORT);
        M01.addIndustry(HIGHCOMMAND);
        M01.addIndustry(ORBITALWORKS);
        M01.addIndustry(REFINING);
        M01.addIndustry(FUELPROD);
        M01.addIndustry("CR_Defense");
        M01.addIndustry("CR_DeepSpaceMiningIndustry");
        M01.addIndustry(STARFORTRESS_HIGH);
        M01.addSubmarket("open_market");
        M01.addSubmarket(GENERIC_MILITARY);
        M01.addSubmarket("black_market");
        M01.addSubmarket("storage");
        M01.getTariff().modifyFlat("default_tariff", Cusader_GATEHAULER.getFaction().getTariffFraction());
        M01.setHidden(true);

// 最后添加到经济系统
        Global.getSector().getEconomy().addMarket(M01, true);

// 设置AI核心和特殊物品
        Industry HighCommand = M01.getIndustry(Industries.HIGHCOMMAND);
        HighCommand.setAICoreId("alpha_core");
        HighCommand.setSpecialItem(new SpecialItemData(Items.CRYOARITHMETIC_ENGINE, null));

        Industry ObitalWorks = M01.getIndustry(Industries.ORBITALWORKS);
        ObitalWorks.setAICoreId("alpha_core");
        ObitalWorks.setSpecialItem(new SpecialItemData(Items.PRISTINE_NANOFORGE, null));

        Industry Refining = M01.getIndustry(Industries.REFINING);
        Refining.setAICoreId("alpha_core");
        Refining.setSpecialItem(new SpecialItemData(Items.CATALYTIC_CORE, null));

        Industry FueProd = M01.getIndustry(Industries.FUELPROD);
        FueProd.setAICoreId("alpha_core");
    }
}