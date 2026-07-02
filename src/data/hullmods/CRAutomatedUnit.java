package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;


public class CRAutomatedUnit extends BaseHullMod {


    public CRAutomatedUnit() {
    }

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getMaxCrewMod().unmodify("automated");
        float cargo = 0.0F;
        float fuel = 0.0F;
        if (stats.getVariant() != null) {
            cargo = stats.getVariant().getHullSpec().getCargo();
            fuel = stats.getVariant().getHullSpec().getFuel();
        }

        float crew = (Math.max(cargo, fuel) + Math.min(cargo, fuel) * 3.0F) / 4.0F;
        stats.getMaxCrewMod().modifyFlat(id, crew);
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        return ship.getVariant().hasHullMod("automated") && !ship.getVariant().hasHullMod("jc_auto_scholar") && !ship.getVariant().hasHullMod("jc_sf_base") && super.isApplicableToShip(ship);
    }

    public String getUnapplicableReason(ShipAPI ship) {
        if (!ship.getVariant().hasHullMod("automated")) {
            return "Can only be installed on automated ships";
        }
        return "";
    }
}