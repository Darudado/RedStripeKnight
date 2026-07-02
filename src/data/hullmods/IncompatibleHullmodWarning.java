package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;

public class IncompatibleHullmodWarning extends BaseHullMod {
    public IncompatibleHullmodWarning() {
    }

    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        return index == 0 ? Global.getSettings().getString("hullmods", "IncompatibleHullmodWarning_r") : null;
    }
}