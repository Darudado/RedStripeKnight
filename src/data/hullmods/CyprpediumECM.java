package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.*;

public class CyprpediumECM extends BaseHullMod {

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getEccmChance().modifyMult(id, 1.5f);
        stats.getSensorStrength().modifyMult(id, 2.5f);
        stats.getBallisticWeaponRangeBonus().modifyPercent(id, 20F);
        stats.getEnergyWeaponRangeBonus().modifyPercent(id, 20F);
        stats.getDamageToFighters().modifyPercent(id, 50F);
        stats.getDamageToMissiles().modifyPercent(id, 50F);
        stats.getAutofireAimAccuracy().modifyMult(id, 2f);
        stats.getRecoilPerShotMultSmallWeaponsOnly().modifyMult(id, 2f);
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara("Zhuolan has undergone extreme electronic warfare specialization design, which greatly improves the electronic warfare performance.", opad, h);
        tooltip.addSectionHeading("Details", Alignment.MID, opad);
        tooltip.addPara("Increase the electronic warfare intensity of ship %s", opad, h, "" + 50 + "%");
        tooltip.addPara("Improve sensor performance of ship %s", opad, h, "" + 150 + "%");
        tooltip.addPara("Additional increase in non-missile weapon range of ship %s", opad, h, "" + 20 + "%");

        tooltip.addPara("Equipped with a cash fire control system to increase the damage of ship %s against aircraft and missiles", opad, h, "" + 50 + "%");
        tooltip.addPara("Also improves the firing accuracy of ship %s", opad, h, "" + 100 + "%" );



    }
}