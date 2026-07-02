package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import data.scripts.utils.MathPersonal;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicIncompatibleHullmods;

import java.awt.*;

public class ImpregnableRampart extends BaseHullMod {
    public static final float HEALTH_BONUS = 100f;
    public static final float SHIELD_BONUS = 10f;
    public static final float PIERCE_MULT = 0.5f;
    public static final float DAMAGE_REDUCTION = 0.8f;
    private float baseShield = 0.0F;
    private boolean loaded = false;
    public static final String MOD_NAME = getString(1);



    public boolean isApplicableToShip(ShipAPI ship) {
        return false;
    }

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getEngineHealthBonus().modifyPercent(id, HEALTH_BONUS);
        stats.getWeaponHealthBonus().modifyPercent(id, HEALTH_BONUS);
        stats.getShieldDamageTakenMult().modifyMult(id, 1f - SHIELD_BONUS * 0.01f);
        stats.getDynamic().getStat(Stats.SHIELD_PIERCED_MULT).modifyMult(id, PIERCE_MULT);
        stats.getHighExplosiveDamageTakenMult().modifyMult(id, DAMAGE_REDUCTION);
        stats.getEnergyDamageTakenMult().modifyMult(id, DAMAGE_REDUCTION);

        if (stats.getVariant().hasHullMod("missleracks")) {
            MagicIncompatibleHullmods.removeHullmodWithWarning(stats.getVariant(), "missleracks", spec.getId());
        }

    }

    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        MutableShipStatsAPI stats = fighter.getMutableStats();

        stats.getArmorDamageTakenMult().modifyMult(id, DAMAGE_REDUCTION);
        stats.getHullDamageTakenMult().modifyMult(id, DAMAGE_REDUCTION);
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (ship.getShield() != null) {
            this.baseShield = ship.getShield().getArc();
        }

        if (ship.getHullSize() != ShipAPI.HullSize.FIGHTER && !ship.hasListenerOfClass(ImpregnableRampartScript.class)) {
            ship.addListener(new ImpregnableRampartScript(ship));
        }
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!Global.getCombatEngine().isPaused() && ship.isAlive()) {
            if (ship.getShield() != null) {
                if (!this.loaded) {
                    this.loaded = true;
                }
                float fluxRatio = ship.getFluxTracker().getFluxLevel();
                float fluxRatioRes;
                if (ship.getVariant().hasHullMod("stabilizedshieldemitter")) {
                    float flux = Math.min(ship.getFluxTracker().getFluxLevel(), 0.85F);
                    fluxRatioRes = MathPersonal.normalize(flux, 0.0F, 0.85F);
                } else {
                    fluxRatioRes = ship.getFluxTracker().getFluxLevel();
                }
                float resBonus = 100.0F * -(0.5F * fluxRatioRes); // 公式：-50% * fluxRatioRes
                ship.getMutableStats().getShieldDamageTakenMult().modifyPercent("ImpregnableRampart2", resBonus);

// 展开/折叠速度加成（通量越高，速度越快）
                float foldBonus = 100.0F * 1.0F * fluxRatioRes;   // 公式：+100% * fluxRatioRes
                ship.getMutableStats().getShieldTurnRateMult().modifyPercent("ImpregnableRampart2", foldBonus);
                ship.getMutableStats().getShieldUnfoldRateMult().modifyPercent("ImpregnableRampart2", foldBonus);
                float resistanceBonusTt = ship.getShield().getFluxPerPointOfDamage() * ship.getMutableStats().getShieldDamageTakenMult().getModifiedValue();
                resistanceBonusTt *= 100.0F;
                resistanceBonusTt = (float) Math.round(resistanceBonusTt);
                resistanceBonusTt /= 100.0F;
                float sizeBonusTt = ship.getShield().getArc();
                if (ship == Global.getCombatEngine().getPlayerShip() && fluxRatio > 0.0F) {
                    Global.getCombatEngine().maintainStatusForPlayerShip("ImpregnableRampart", "graphics/icons/hullsys/fortress_shield.png", MOD_NAME, getString(2).replace("$resistanceBonusTtNum", String.valueOf(resistanceBonusTt)).replace("$sizeBonusTtNum", String.valueOf(Math.round(sizeBonusTt))), false);
                }


            }
        }
    }

    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) {
            return "360";
        } else if (index == 1) {
            return "" + (int) this.baseShield;
        } else if (index == 2) {
            return Math.round(50.0F) + "%";
        } else if (index == 3) {
            return Math.round(100.0F) + "%";
        } else {
            return index == 4 ? "0" : null;
        }
    }

    public Color getNameColor() {
        return new Color(231, 124, 138, 255);
    }

    private static String getString(int ID) {
        return Global.getSettings().getString("cr_hullmods", String.format("%s_%d", "ImpregnableRampart", ID));
    }

    public static class ImpregnableRampartScript implements DamageTakenModifier {

        public ShipAPI ship;

        public ImpregnableRampartScript(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
            return "";
        }
    }
}