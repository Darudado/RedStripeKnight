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

        tooltip.addPara("灼兰经过极端的电子战特化设计，极大提升了电子战性能", opad, h);
        tooltip.addSectionHeading("详情", Alignment.MID, opad);
        tooltip.addPara("提升舰船 %s 的电子战强度", opad, h, "" + 50 + "%");
        tooltip.addPara("提升舰船 %s 的传感器性能", opad, h, "" + 150 + "%");
        tooltip.addPara("额外提升舰船 %s 的非导弹武器射程", opad, h, "" + 20 + "%");

        tooltip.addPara("搭载现金火控系统，提升舰船 %s 的对战机与导弹伤害", opad, h, "" + 50 + "%");
        tooltip.addPara("同时提升舰船 %s 的开火精度", opad, h, "" + 100 + "%" );



    }
}