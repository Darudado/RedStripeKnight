package data.hullmods.Installable;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;

public class VOW_AdditionalFluxDissipation extends BaseHullMod {
    public float SMOD_BONUS = 15f;
    public float BONUS = 15f;
    public float BASE_BONUS = 30f;
    String ID = "VOW_AdditionalFluxDissipation_id";

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {

        stats.getFluxDissipation().modifyPercent(id, BONUS);
        stats.getHullBonus().modifyPercent(id, BONUS);

        stats.getArmorBonus().modifyPercent(id, -BONUS);

        if(stats.getVariant().hasHullMod("VOW_DesignSystem")){
            stats.getFluxCapacity().modifyPercent(id, 10f);
            stats.getHullBonus().modifyPercent(id ,BONUS+5f);
        }

        boolean sMod = isSMod(stats);
        if (sMod) {
            stats.getFluxDissipation().modifyPercent(id, SMOD_BONUS);
            stats.getHullBonus().modifyPercent(id, SMOD_BONUS-10f);
            stats.getHardFluxDissipationFraction().modifyPercent(id, SMOD_BONUS-5f);
        }

    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        float HULL_RATE =ship.getHitpoints()/ship.getMaxHitpoints();
        float DIS_RATE = BASE_BONUS * HULL_RATE;
        ship.getMutableStats().getFluxDissipation().modifyPercent(ID , DIS_RATE );

        float DES_RATE = BASE_BONUS * (1- HULL_RATE);
        ship.getMutableStats().getArmorDamageTakenMult().modifyPercent(ID ,-DES_RATE);
        ship.getMutableStats().getHullDamageTakenMult().modifyPercent(ID ,-DES_RATE);
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return (int) BONUS + "%";
        if (index == 1) return (int) BONUS + "%";
        if (index == 2) return (int) BONUS + "%";
        if (index == 3) return (int) BASE_BONUS + "%";
        if (index == 4) return (int) BASE_BONUS + "%";
        return null;
    }

    public String getSModDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return  SMOD_BONUS + "%";
        if (index == 1) return  SMOD_BONUS-10f + "%";
        if (index == 2) return  SMOD_BONUS-5f + "%";
        return null;
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null) return false;
        return ship.getVariant().hasHullMod("CrusadersCore");
    }

    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) return "ship does not exist";
        if (!ship.getVariant().hasHullMod("CrusadersCore")) return "Requires Crusader Core";
        return null;
    }
}