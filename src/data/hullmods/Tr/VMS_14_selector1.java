package data.hullmods.Tr;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;

import static data.hullmods.Tr.VMS_14_system.BASIC_HULL_ID;

public class VMS_14_selector1 extends BaseHullMod {
	@Override
	public boolean showInRefitScreenModPickerFor(ShipAPI ship) {
		return BASIC_HULL_ID.contains(ship.getHullSpec().getHullId());
		//    || UUN_ZongShipSwithHullSpecHullMod.HULL_IDS.get(spec.getId()).equals(ship.getHullSpec().getHullId());
	}

	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
		if(ship.getVariant().hasHullMod("vow_VMS_14_assault_selector")) return false;
		return ship.getVariant().hasHullMod("vow_VMS_14_system");
	}
}
