package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.MarkovNames;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.AddedEntity;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.EntityLocation;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.LocationType;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.LinkedHashMap;
import java.util.Random;

/**
 * 十字军殖民地工厂类，负责创建殖民地市场、空间站实体、名称等。
 */
public class CrusadersBaseConstruction {

    public static final String CRUSADERS_FACTION = "cinis_of_crusaders";

    /**
     * 创建一个十字军殖民地，返回对应的 CrusadersBaseGen 实例。
     */
    public static CrusadersBaseGen createColony(StarSystemAPI system, CrusadersBaseGen.ColonyTier tier, Random random) {
        // 1. 创建市场
        int size = getMarketSize(tier);
        MarketAPI market = Global.getFactory().createMarket(Misc.genUID(), "Crusaders Colony", size);
        market.setFactionId(CRUSADERS_FACTION);
        market.setSurveyLevel(SurveyLevel.FULL);
        market.setHidden(true);
        market.getMemoryWithoutUpdate().set("$crusaders_colony", true);
        market.getMemoryWithoutUpdate().set(MemFlags.HIDDEN_BASE_MEM_FLAG, true);

        // 添加基础产业
        market.addCondition(Conditions.POPULATION_3);
        market.addIndustry(Industries.POPULATION);
        market.addIndustry(Industries.SPACEPORT);
        market.addIndustry(Industries.MILITARYBASE);
        market.addSubmarket("open_market");
        market.addSubmarket("black_market");
        market.getTariff().modifyFlat("default_tariff", market.getFaction().getTariffFraction());

        // 2. 选择位置
        EntityLocation location = pickLocation(system, random);
        if (location == null) return null;

        // 3. 创建空间站实体
        AddedEntity added = BaseThemeGenerator.addNonSalvageEntity(system, location, "station_side00", CRUSADERS_FACTION);
        if (added.entity == null) return null;

        SectorEntityToken entity = added.entity;
        String name = generateName(random);
        market.setName(name);
        entity.setName(name);
        BaseThemeGenerator.convertOrbitWithSpin(entity, -5f);
        market.setPrimaryEntity(entity);
        entity.setMarket(market);
        entity.setSensorProfile(1f);
        entity.setDiscoverable(true);
        entity.getDetectedRangeMod().modifyFlat("gen", 5000f);

        // 4. 配置市场
        market.setEconGroup(market.getId());
        market.getMemoryWithoutUpdate().set("$core_noDeciv", true);
        market.reapplyIndustries();
        Global.getSector().getEconomy().addMarket(market, false);
        market.reapplyIndustries();

        // 5. 添加十字军特色产业
        addSpecialIndustries(market, tier);

        // 6. 创建并返回殖民地实例
        return new CrusadersBaseGen(system, market, entity, tier);
    }

    private static int getMarketSize(CrusadersBaseGen.ColonyTier tier) {
        return switch (tier) {
            case TIER_1 -> 3;
            case TIER_2 -> 4;
            case TIER_3 -> 5;
        };
    }

    private static EntityLocation pickLocation(StarSystemAPI system, Random random) {
        LinkedHashMap<LocationType, Float> weights = new LinkedHashMap<>();
        weights.put(LocationType.IN_SMALL_NEBULA, 15f);
        weights.put(LocationType.IN_ASTEROID_BELT, 12f);
        weights.put(LocationType.IN_ASTEROID_FIELD, 12f);
        weights.put(LocationType.IN_RING, 10f);
        weights.put(LocationType.GAS_GIANT_ORBIT, 8f);
        weights.put(LocationType.PLANET_ORBIT, 8f);
        weights.put(LocationType.STAR_ORBIT, 5f);

        WeightedRandomPicker<EntityLocation> locs = BaseThemeGenerator.getLocations(random, system, null, 100f, weights);
        return locs.pick();
    }

    private static String generateName(Random random) {
        MarkovNames.loadIfNeeded();
        for (int i = 0; i < 10; i++) {
            MarkovNames.MarkovNameResult gen = MarkovNames.generate(random);
            if (gen != null) {
                String name = gen.name;
                if (name.toLowerCase().startsWith("the ")) continue;
                String postfix = pickPostfix(random);
                if (postfix != null && !postfix.isEmpty()) {
                    name += " " + postfix;
                }
                String prefix = pickPrefix(random);
                if (prefix != null && !prefix.isEmpty()) {
                    name = prefix + " " + name;
                }
                if (name.length() <= 28) return name;
            }
        }
        return "Crusaders Outpost";
    }

    private static String pickPrefix(Random random) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
        picker.add("Holy", 12f);
        picker.add("Sacred", 10f);
        picker.add("Sanctified", 10f);
        picker.add("Crusader", 15f);
        picker.add("Templar", 10f);
        picker.add("Paladin", 8f);
        return picker.pick();
    }

    private static String pickPostfix(Random random) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
        picker.add("Sanctuary", 15f);
        picker.add("Citadel", 12f);
        picker.add("Bastion", 10f);
        picker.add("Fortress", 12f);
        picker.add("Redoubt", 8f);
        picker.add("Keep", 10f);
        picker.add("Stronghold", 10f);
        picker.add("Outpost", 15f);
        return picker.pick();
    }

    private static void addSpecialIndustries(MarketAPI market, CrusadersBaseGen.ColonyTier tier) {
        // 所有等级都添加防御设施
        market.addIndustry("CR_Defense");
        // 可根据需要添加更多产业
        switch (tier) {
            case TIER_2:
                //market.addIndustry("crusaders_outpost");
                break;
            case TIER_3:
                //market.addIndustry("crusaders_fortress");
                //market.addIndustry("crusaders_workshop");
                break;
        }
        market.reapplyIndustries();
    }
}