package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import data.scripts.utils.RSUtil;

import java.util.*;

import static com.fs.starfarer.api.impl.campaign.CoreScript.log;

public class TankSwitcher extends BaseHullMod {

    // 配置参数
    public static final String BASIC_HULL_ID = "rs_cherubicoblatus_A";
    public static final Map<String, String> HULL_IDS = new HashMap<>();

    static {
        HULL_IDS.put("rs_cherubicoblatus_AHullMod", "rs_cherubicoblatus_A");
        HULL_IDS.put("rs_cherubicoblatus_BHullMod", "rs_cherubicoblatus_B");
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        String hullId = stats.getVariant().getHullSpec().getHullId();
        boolean hasHullMod = false;
        log.info("READY");
        for (String hullMod : HULL_IDS.keySet()) {
            log.info("HullModId: " + hullMod);
            if (stats.getVariant().hasHullMod(hullMod)) {
                log.info("hasHullMod: " + hullMod);
                if (BASIC_HULL_ID.equals(hullId)) {
                    log.info("setHullId: " + HULL_IDS.get(hullMod));
                    ShipVariantAPI variant = Global.getSettings().getVariant(HULL_IDS.get(hullMod) + "_Hull");
                    variant.addMod(hullMod); // 正确添加对应HullMod
                    if (stats.getFleetMember() != null) {
                        stats.getFleetMember().setVariant(variant, false, false);
                    }
                    return;
                } else {
                    hasHullMod = true;
                }
                break;
            }
        }
        if (!hasHullMod && !BASIC_HULL_ID.equals(hullId)) {
            ShipVariantAPI variant = Global.getSettings().getVariant(BASIC_HULL_ID + "_Hull");
            variant.addMod("rs_cherubicoblatus_AHullMod");
            if (stats.getFleetMember() != null) {
                stats.getFleetMember().setVariant(variant, false, false);
            }
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (RSUtil.isInPlayerFleet(ship)) {
            RSUtil.ensureCloneVariant(ship.getFleetMember(), false);
        }

        String hullId = ship.getVariant().getHullSpec().getHullId();
        boolean hasHullMod = false;

        for (String hullMod : HULL_IDS.keySet()) {
            if (ship.getVariant().hasHullMod(hullMod)) {
                if (BASIC_HULL_ID.equals(hullId)) {
                    ShipHullSpecAPI hullSpec = Global.getSettings().getHullSpec(HULL_IDS.get(hullMod));
                    ship.getVariant().setHullSpecAPI(hullSpec);

                    // 仅移除变体相关的HullMod
                    List<String> modsToRemove = new ArrayList<>();
                    for (String mod : ship.getVariant().getPermaMods()) {
                        if (HULL_IDS.containsKey(mod)) {
                            modsToRemove.add(mod);
                        }
                    }
                    for (String mod : modsToRemove) {
                        ship.getVariant().removePermaMod(mod);
                    }

                    // 添加新船体的内置插件
                    for (String builtInMod : hullSpec.getBuiltInMods()) {
                        ship.getVariant().addPermaMod(builtInMod);
                    }
                    ship.getVariant().addMod(hullMod);

                    if (RSUtil.isInPlayerFleet(ship)) {
                        RSUtil.refreshRefitUI();
                    }
                    return;
                } else {
                    hasHullMod = true;
                }
                break;
            }
        }

        if (!hasHullMod && !BASIC_HULL_ID.equals(hullId)) {
            ShipHullSpecAPI hullSpec = Global.getSettings().getHullSpec(BASIC_HULL_ID);
            ship.getVariant().setHullSpecAPI(hullSpec);

            // 同步基础船体的内置插件
            for (String builtInMod : hullSpec.getBuiltInMods()) {
                ship.getVariant().addPermaMod(builtInMod);
            }
        }
    }
}