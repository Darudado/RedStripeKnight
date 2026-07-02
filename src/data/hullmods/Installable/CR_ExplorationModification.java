package data.hullmods.Installable;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.hullmods.BaseLogisticsHullMod;

public class CR_ExplorationModification extends BaseLogisticsHullMod {
    public static float MAINTENANCE_MULT = 0.8F;
    public static float REPAIR_RATE_BONUS = 50.0F;
    public static float CR_RECOVERY_BONUS = 50.0F;
    public static float SMOD_MODIFIER = 0.15F;

    public static float CARGO_BONUS = 100F;
    public static float DECK_CARGO_BONUS = 10F;

    public CR_ExplorationModification() {
    }

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        boolean sMod = this.isSMod(stats);
        stats.getMinCrewMod().modifyMult(id, MAINTENANCE_MULT - (sMod ? SMOD_MODIFIER : 0.0F));
        stats.getSuppliesPerMonth().modifyMult(id, MAINTENANCE_MULT - (sMod ? SMOD_MODIFIER : 0.0F));
        stats.getFuelUseMod().modifyMult(id, MAINTENANCE_MULT - (sMod ? SMOD_MODIFIER : 0.0F));
        stats.getBaseCRRecoveryRatePercentPerDay().modifyPercent(id, CR_RECOVERY_BONUS);
        stats.getRepairRatePercentPerDay().modifyPercent(id, REPAIR_RATE_BONUS);

        if(stats.getNumFighterBays().getModifiedValue() > 0){
            stats.getCargoMod().modifyPercent(id, CARGO_BONUS+stats.getNumFighterBays().getModifiedValue()*DECK_CARGO_BONUS);
            stats.getFuelMod().modifyPercent(id, CARGO_BONUS+stats.getNumFighterBays().getModifiedValue()*DECK_CARGO_BONUS);
        }else {
            stats.getCargoMod().modifyPercent(id, CARGO_BONUS);
            stats.getFuelMod().modifyPercent(id, CARGO_BONUS);
        }
        
        stats.getSensorStrength().modifyMult(id , 1.5f);
        stats.getCrewLossMult().modifyMult(id , 0.5f);

    }

    public String getSModDescriptionParam(int index, ShipAPI.HullSize hullSize, ShipAPI ship) {
        return index == 0 ? Math.round(SMOD_MODIFIER * 100.0F) + "%" : null;
    }

    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize, ShipAPI ship) {
        if (index == 0) {
            return Math.round((1.0F - MAINTENANCE_MULT) * 100.0F) + "%";
        } else if (index == 1){
            return Math.round(CR_RECOVERY_BONUS) + "%";
        }else if (index == 2){
            return Math.round(CARGO_BONUS) + "%";
        }else{
            return Math.round(DECK_CARGO_BONUS) + "%";
        }
    }
}
