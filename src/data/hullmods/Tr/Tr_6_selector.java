package data.hullmods.Tr;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;

import static data.hullmods.Tr.Tr6_Haznthley_system.BASIC_HULL_ID;

public class Tr_6_selector extends BaseHullMod {
	@Override
	public boolean showInRefitScreenModPickerFor(ShipAPI ship) {
		return BASIC_HULL_ID.contains(ship.getHullSpec().getHullId());
		//    || UUN_ZongShipSwithHullSpecHullMod.HULL_IDS.get(spec.getId()).equals(ship.getHullSpec().getHullId());
	}

	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
		return ship.getVariant().hasHullMod("Tr6_Haznthley_system");
	}
}
