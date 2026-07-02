package data.hullmods.VOW;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.magiclib.util.MagicIncompatibleHullmods;

import static data.hullmods.VOW.VOW_PiafidesSystem.BASIC_HULL_ID;

public class VOW_Piafides_selector extends BaseHullMod {

	public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
		if(stats.getVariant().hasHullMod("vow_piafidesA_selector")) {
			if(stats.getVariant().hasHullMod("vow_piafidesB_selector")) {
				MagicIncompatibleHullmods.removeHullmodWithWarning(stats.getVariant(), "vow_piafidesB_selector", spec.getId());
			}
		}

		if(stats.getVariant().hasHullMod("vow_piafidesB_selector")) {
			if(stats.getVariant().hasHullMod("vow_piafidesA_selector")) {
				MagicIncompatibleHullmods.removeHullmodWithWarning(stats.getVariant(), "vow_piafidesA_selector", spec.getId());
			}
		}

		if(stats.getVariant().hasHullMod("vow_piafidesA_selector") && stats.getVariant().hasHullMod("vow_piafidesB_selector")) {
			if(stats.getVariant().hasHullMod("vow_piafidesB_selector")) {
				MagicIncompatibleHullmods.removeHullmodWithWarning(stats.getVariant(), "vow_piafidesB_selector", spec.getId());
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
		return ship.getVariant().hasHullMod("VOW_PiafidesSystem");
	}
}
