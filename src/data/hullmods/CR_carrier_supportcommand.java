package data.hullmods;


import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

import static com.fs.starfarer.api.impl.campaign.ids.Stats.REPLACEMENT_RATE_DECREASE_MULT;

public class CR_carrier_supportcommand extends BaseHullMod {
    public static final float FIGHTER_CREW_LOSS_REDUCTION = 20.0F;  // 舰载机船员损失减少20%
    public static final float FIGHTER_REPLACEMENT_RATE_BONUS = 10.0F; // 舰载机补充速率提升10%
    public static final float REDUCED_RANGE = 0.1F;
    public static final float RANGE_BONUS=0.75F;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getDynamic().getStat(REPLACEMENT_RATE_DECREASE_MULT).modifyMult(id, 0.8F);
        stats.getDynamic().getStat("fighter_crew_loss_mult").modifyMult(id, 0.8F);
        stats.getFighterWingRange().modifyMult(this.getClass().getName(), REDUCED_RANGE);

    }

    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        fighter.getMutableStats().getHullDamageTakenMult().modifyMult(id, 0.9F);
        fighter.getMutableStats().getArmorDamageTakenMult().modifyMult(id, 0.9F);
        fighter.getMutableStats().getShieldDamageTakenMult().modifyMult(id, 0.9F);
        fighter.getMutableStats().getBallisticWeaponRangeBonus().modifyMult(id, 1+RANGE_BONUS);
        fighter.getMutableStats().getEnergyWeaponRangeBonus().modifyMult(id, 1+RANGE_BONUS);

    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        Color green = new Color(55, 245, 65, 255);
        Color badbg = new Color(128, 38, 0, 175);
        tooltip.addPara("Increase carrier-based aircraft replenishment rate: %s", 10.0F, green, Misc.getRoundedValue(10.0F) + "%");
        tooltip.addPara("Reduce crew losses: %s", 10.0F, green, Misc.getRoundedValue(20.0F) + "%");
        tooltip.addPara("Reduce carrier-based aircraft combat radius: %s", 10.0F, green, Misc.getRoundedValue(90.0F) + "%");
        tooltip.addPara("Increase carrier aircraft weapon range: %s", 10.0F, green, Misc.getRoundedValue(90.0F) + "%");
        tooltip.addPara("Reduce damage received by carrier-based aircraft: %s", 10.0F, green, Misc.getRoundedValue(10.0F) + "%");


    }
}