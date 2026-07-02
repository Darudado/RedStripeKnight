package data.hullmods.VOW;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.magiclib.util.MagicIncompatibleHullmods;

import static data.hullmods.VOW.VOW_AdmonitioSystem.BASIC_HULL_ID;

public class VOW_Admonitio_selector extends BaseHullMod {

	public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
		if(stats.getVariant().hasHullMod("vow_admonitioA_selector")) {
			if(stats.getVariant().hasHullMod("vow_admonitioB_selector")) {
				MagicIncompatibleHullmods.removeHullmodWithWarning(stats.getVariant(), "vow_admonitioB_selector", spec.getId());
			}
		}

		if(stats.getVariant().hasHullMod("vow_admonitioB_selector")) {
			if(stats.getVariant().hasHullMod("vow_admonitioA_selector")) {
				MagicIncompatibleHullmods.removeHullmodWithWarning(stats.getVariant(), "vow_admonitioA_selector", spec.getId());
			}
		}

		if(stats.getVariant().hasHullMod("vow_admonitioA_selector") && stats.getVariant().hasHullMod("vow_admonitioB_selector")) {
			if(stats.getVariant().hasHullMod("vow_admonitioB_selector")) {
				MagicIncompatibleHullmods.removeHullmodWithWarning(stats.getVariant(), "vow_admonitioB_selector", spec.getId());
			}
		}
	}

	@Override
	public boolean showInRefitScreenModPickerFor(ShipAPI ship) {
		return BASIC_HULL_ID.contains(ship.getHullSpec().getHullId());
		//    || UUN_ZongShipSwithHullSpecHullMod.HULL_IDS.get(spec.getId()).equals(ship.getHullSpec().getHullId());
	}

	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
		return ship.getVariant().hasHullMod("VOW_AdmonitioSystem");
	}
}
