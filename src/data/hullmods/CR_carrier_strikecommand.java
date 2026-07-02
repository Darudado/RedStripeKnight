package data.hullmods;


import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

import static com.fs.starfarer.api.impl.campaign.ids.Stats.REPLACEMENT_RATE_DECREASE_MULT;

public class CR_carrier_strikecommand extends BaseHullMod {
    public static final float MISSILE_SPEED_BONUS = 25.0F;
    public static final float MISSILE_RANGE_MULT = 0.8F;
    public static final float MISSILE_HITPOINTS_BONUS = 50.0F;
    public static final float STRIKE_DAMAGE_BONUS = 10.0F;
    public static final float NON_MISSILE_SPEED_BONUS = 15.0F;
    public static final float NON_MISSILE_RANGE_MULT = 0.8F;
    public static final float NON_MISSILE_HITPOINTS_BONUS = 15.0F;
    public static final float NON_STRIKE_DAMAGE_BONUS = 5.0F;
    public static final float FIGHTER_CREW_LOSS_REDUCTION = 20.0F;  // 舰载机船员损失减少20%
    public static final float FIGHTER_REPLACEMENT_RATE_BONUS = 20.0F; // 舰载机补充速率提升10%
    public static final float RANGE_BONUS=0.3F;


    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getDynamic().getStat(REPLACEMENT_RATE_DECREASE_MULT).modifyMult(id, 0.9F);
        stats.getDynamic().getStat("fighter_crew_loss_mult").modifyMult(id, 1.3F);
        stats.getFighterWingRange().modifyMult(this.getClass().getName(), 1+RANGE_BONUS);
    }

    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        fighter.getMutableStats().getMissileMaxSpeedBonus().modifyPercent(id, 20.0F);
        fighter.getMutableStats().getMissileTurnAccelerationBonus().modifyPercent(id, 20.0F);
        fighter.getMutableStats().getMissileAccelerationBonus().modifyPercent(id, 20.0F);
        fighter.getMutableStats().getMissileMaxTurnRateBonus().modifyPercent(id, 30.0F);
        fighter.getMutableStats().getMissileWeaponRangeBonus().modifyMult(id, 0.8F);
        fighter.getMutableStats().getBallisticWeaponRangeBonus().modifyPercent(id, 0.85F);
        fighter.getMutableStats().getEnergyWeaponRangeBonus().modifyPercent(id, 0.85F);
        fighter.getMutableStats().getMissileHealthBonus().modifyPercent(id, 0.3F);
        fighter.getMutableStats().getDamageToDestroyers().modifyPercent(id, 0.1F);
        fighter.getMutableStats().getDamageToCruisers().modifyPercent(id, 0.1F);
        fighter.getMutableStats().getDamageToCapital().modifyPercent(id, 0.1F);

        fighter.getMutableStats().getHullDamageTakenMult().modifyMult(id, 1.1F);
        fighter.getMutableStats().getArmorDamageTakenMult().modifyMult(id, 1.1F);
        fighter.getMutableStats().getShieldDamageTakenMult().modifyMult(id, 1.1F);
    }


    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        Color green = new Color(55, 245, 65, 255);
        Color flavor = new Color(110, 110, 110, 255);
        Color bad = Misc.getNegativeHighlightColor();
        Color badbg = new Color(128, 38, 0, 175);
        tooltip.addPara("Increase carrier-based aircraft replenishment rate: %s", 10.0F, green, Misc.getRoundedValue(20.0F) + "%");
        tooltip.addPara("Increase crew losses: %s", 30.0F, green, Misc.getRoundedValue(10.0F) + "%");
        tooltip.addPara("Increase carrier-based aircraft combat radius: %s", 10.0F, green, Misc.getRoundedValue(30.0F) + "%");
        tooltip.addPara("Increase carrier-based aircraft mobility", 10.0F);
        tooltip.addPara("Increase carrier-based aircraft weapon damage", 10.0F);
        tooltip.addPara("Reduce damage received by carrier-based aircraft: %s", 10.0F, green, Misc.getRoundedValue(20.0F) + "%");


    }
}