package data.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.hullmods.BaseLogisticsHullMod;

public class SystemUpgrading extends BaseLogisticsHullMod {
    public static float UPGRADE_BONUS_Energy = 15f;
    public static float SPUPGRADE_BONUS_Energy = 20f;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if(stats.getVariant().hasHullMod("CrusadersCore")){
            stats.getMaxSpeed().modifyPercent(id ,SPUPGRADE_BONUS_Energy);
            stats.getAcceleration().modifyPercent(id ,SPUPGRADE_BONUS_Energy);
            stats.getDeceleration().modifyPercent(id ,SPUPGRADE_BONUS_Energy);
            stats.getMaxTurnRate().modifyPercent(id ,SPUPGRADE_BONUS_Energy);
            stats.getTurnAcceleration().modifyPercent(id ,SPUPGRADE_BONUS_Energy);
            stats.getFluxCapacity().modifyPercent(id ,SPUPGRADE_BONUS_Energy);
            stats.getFluxDissipation().modifyPercent(id ,SPUPGRADE_BONUS_Energy);
            stats.getArmorBonus().modifyPercent(id ,SPUPGRADE_BONUS_Energy);
            stats.getSuppliesPerMonth().modifyPercent(id ,UPGRADE_BONUS_Energy);
        }else{
            stats.getMaxSpeed().modifyPercent(id ,UPGRADE_BONUS_Energy);
            stats.getAcceleration().modifyPercent(id ,UPGRADE_BONUS_Energy);
            stats.getDeceleration().modifyPercent(id ,UPGRADE_BONUS_Energy);
            stats.getMaxTurnRate().modifyPercent(id ,UPGRADE_BONUS_Energy);
            stats.getTurnAcceleration().modifyPercent(id ,UPGRADE_BONUS_Energy);
            stats.getFluxCapacity().modifyPercent(id ,UPGRADE_BONUS_Energy);
            stats.getFluxDissipation().modifyPercent(id ,UPGRADE_BONUS_Energy);
            stats.getArmorBonus().modifyPercent(id ,UPGRADE_BONUS_Energy);
            stats.getSuppliesPerMonth().modifyPercent(id ,UPGRADE_BONUS_Energy);
        }
    }

    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "" + (int) UPGRADE_BONUS_Energy + "%";
        if (index == 1) return "" + (int) SPUPGRADE_BONUS_Energy + "%";
        return null;
    }
}