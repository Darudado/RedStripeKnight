package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.util.*;

import org.jetbrains.annotations.NotNull;

public class BallisticTargetCognetrix extends BaseHullMod {

    public static float RANGE_BONUS_Energy = 25f;
    public static float SmodDamagemult = 5f;

    private float check = 0.0F;
    private static final Set<String> BLOCKED_OTHER = new HashSet<>();
    private static final Set<String> BLOCKED_OMNI = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER_PLAYER_ONLY = new HashSet<>();
    private static final String ERROR = "IncompatibleHullmodWarning";

    public static float PD_MINUS = 30f;

    public static Map mag = new HashMap();
    static {
        mag.put(HullSize.FIGHTER, 15f);
        mag.put(HullSize.FRIGATE, 35f);
        mag.put(HullSize.DESTROYER, 45f);
        mag.put(HullSize.CRUISER, 55f);
        mag.put(HullSize.CAPITAL_SHIP, 70f);
    }

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        Map map = mag;

        boolean sMod = isSMod(stats);

        stats.getEnergyWeaponRangeBonus().modifyPercent(id, RANGE_BONUS_Energy);
        stats.getBallisticWeaponRangeBonus().modifyPercent(id, (Float) map.get(hullSize));

        stats.getBeamPDWeaponRangeBonus().modifyPercent(id, RANGE_BONUS_Energy);
        stats.getNonBeamPDWeaponRangeBonus().modifyPercent(id, RANGE_BONUS_Energy);

        stats.getNonBeamPDWeaponRangeBonus().modifyPercent(id, -PD_MINUS);
        stats.getBeamPDWeaponRangeBonus().modifyPercent(id, -PD_MINUS);


        if (!sMod) {
            stats.getBallisticWeaponDamageMult().modifyPercent(id, SmodDamagemult);
        }
    }


    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + (int) RANGE_BONUS_Energy + "%";
        if (index == 1) return "" + ((Float) mag.get(HullSize.FIGHTER)).intValue() + "%";
        if (index == 2) return "" + ((Float) mag.get(HullSize.FRIGATE)).intValue() + "%";
        if (index == 3) return "" + ((Float) mag.get(HullSize.DESTROYER)).intValue() + "%";
        if (index == 4) return "" + ((Float) mag.get(HullSize.CRUISER)).intValue() + "%";
        if (index == 5) return "" + ((Float) mag.get(HullSize.CAPITAL_SHIP)).intValue() + "%";
        return null;
    }
    public String getSModDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + (int) SmodDamagemult + "%";
        return null;
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        return !ship.getVariant().getHullMods().contains("targetingunit") &&
                !ship.getVariant().getHullMods().contains("advancedcore") &&
                !ship.getVariant().getHullMods().contains("dedicated_targeting_core")&&
                !ship.getVariant().getHullMods().contains("distributed_fire_control")&&
        !ship.getVariant().getHullMods().contains("EnergyTargetCognetrix");
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.getMutableStats();
        if (check > 0.0F && --check < 1.0F) {
            ship.getVariant().removeMod(ERROR);
        }

        for (Set<String> strings : Arrays.asList(BLOCKED_OMNI, BLOCKED_OTHER, BLOCKED_OTHER_PLAYER_ONLY)) {
            checkAndRemoveBlockedMods(ship, strings);
        }

    }

    private void checkAndRemoveBlockedMods(ShipAPI ship, @NotNull Set<String> blockedMods) {
        List<String> shipMods = new ArrayList<>(ship.getVariant().getHullMods());
        for (String mod : shipMods) {
            if (blockedMods.contains(mod)) {
                // 移除冲突船插
                ship.getVariant().removeMod(mod);
                if (!ship.getVariant().hasHullMod(ERROR)) {
                    ship.getVariant().addMod(ERROR);
                }
                // 记录日志
                Global.getLogger(this.getClass()).info(
                        "Removed conflicting hullmod [" + mod + "] from " + ship.getName()
                );
            }
        }
    }

    static {
        BLOCKED_OMNI.add("dedicated_targeting_core");
        BLOCKED_OMNI.add("advancedcore");
        BLOCKED_OMNI.add("advancedoptics");
        BLOCKED_OMNI.add("targetingunit");


        BLOCKED_OTHER.add("dedicated_targeting_core");
        BLOCKED_OTHER.add("advancedcore");
        BLOCKED_OTHER.add("advancedoptics");
        BLOCKED_OTHER.add("targetingunit");

        BLOCKED_OTHER_PLAYER_ONLY.add("dedicated_targeting_core");
        BLOCKED_OTHER_PLAYER_ONLY.add("advancedcore");
        BLOCKED_OTHER_PLAYER_ONLY.add("advancedoptics");
        BLOCKED_OTHER_PLAYER_ONLY.add("targetingunit");
    }
}
