package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;

public class Tank extends BaseHullMod {

    public boolean showInRefitScreenModPickerFor(ShipAPI ship) {
        return TankSwitcher.BASIC_HULL_ID.equals(ship.getHullSpec().getHullId());
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        for (String hullMod : TankSwitcher.HULL_IDS.keySet()) {
            if (!spec.getId().equals(hullMod)) {
                if (ship.getVariant().hasHullMod(hullMod)) {
                    return false;
                }
            }
        }
        return true;
    }
}
