package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.List;

public class CR_QuantumDisruptor extends BaseShipSystemScript {
    public static float ENERGY_DAM_PENALTY_MULT = 1.0F;
    public static float DISRUPTION_DUR = 1.0F;
    protected static float MIN_DISRUPTION_RANGE = 750.0F;
    public static final Color OVERLOAD_COLOR = new Color(255, 155, 255, 255);
    public static final Color JITTER_COLOR = new Color(255, 155, 255, 75);
    public static final Color JITTER_UNDER_COLOR = new Color(255, 155, 255, 155);

    public void apply(MutableShipStatsAPI stats, String id, ShipSystemStatsScript.State state, float effectLevel) {
        ShipAPI ship;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            stats.getEnergyWeaponDamageMult().modifyMult(id, ENERGY_DAM_PENALTY_MULT);
            float jitterLevel = effectLevel;
            if (state == State.OUT) {
                jitterLevel = effectLevel * effectLevel;
            }

            float maxRangeBonus = 50.0F;
            float jitterRangeBonus = jitterLevel * maxRangeBonus;
            ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, 21, 0.0F, 3.0F + jitterRangeBonus);
            ship.setJitter(this, JITTER_COLOR, jitterLevel, 4, 0.0F, 0.0F + jitterRangeBonus);

            String targetKey = ship.getId() + "_acausal_target";
            Object foundTarget = Global.getCombatEngine().getCustomData().get(targetKey);
            if (state == State.IN) {
                if (foundTarget == null) {
                    ShipAPI target = this.findTarget(ship);
                    if (target != null) {
                        Global.getCombatEngine().getCustomData().put(targetKey, target);
                    }
                }
            } else if (effectLevel >= 1.0F) {
                if (foundTarget instanceof ShipAPI target) {
                    if (target.getFluxTracker().isOverloadedOrVenting()) {
                        target = ship;
                    }
                    this.applyEffectToTarget(ship, target);
                }
            } else if (state == State.OUT && foundTarget != null) {
                Global.getCombatEngine().getCustomData().remove(targetKey);
            }
        }
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getEnergyWeaponDamageMult().unmodify(id);
    }

    protected ShipAPI findTarget(ShipAPI ship) {
        float range = getMaxRange(ship);
        boolean player = ship == Global.getCombatEngine().getPlayerShip();
        ShipAPI target = ship.getShipTarget();
        if (ship.getShipAI() != null && ship.getAIFlags().hasFlag(AIFlags.TARGET_FOR_SHIP_SYSTEM)) {
            target = (ShipAPI) ship.getAIFlags().getCustom(AIFlags.TARGET_FOR_SHIP_SYSTEM);
            if (target != null && target.getOriginalOwner() == ship.getOriginalOwner()) {
                target = null;
            }
        }

        if (target != null) {
            float dist = Misc.getDistance(ship.getLocation(), target.getLocation());
            float radSum = ship.getCollisionRadius() + target.getCollisionRadius();
            if (dist > range + radSum) {
                target = null;
            }
        }

        // 修复：原 else if 导致距离超出后无法进入后备寻敌
        if (target == null || target.getOwner() == ship.getOwner()) {
            if (player) {
                target = Misc.findClosestShipEnemyOf(ship, ship.getMouseTarget(), HullSize.FRIGATE, range, true);
            } else {
                Object test = ship.getAIFlags().getCustom(AIFlags.MANEUVER_TARGET);
                if (test instanceof ShipAPI) {
                    target = (ShipAPI) test;
                    float dist = Misc.getDistance(ship.getLocation(), target.getLocation());
                    float radSum = ship.getCollisionRadius() + target.getCollisionRadius();
                    if (dist > range + radSum || target.isFighter()) {
                        target = null;
                    }

                    if (target != null && target.getOriginalOwner() == ship.getOriginalOwner()) {
                        target = null;
                    }
                }
            }
        }

        if (target != null && target.isFighter()) {
            target = null;
        }

        if (target == null) {
            target = Misc.findClosestShipEnemyOf(ship, ship.getLocation(), HullSize.FRIGATE, range, true);
        }

        if (target == null || target.getFluxTracker().isOverloadedOrVenting()) {
            target = ship;
        }

        return target;
    }

    public static float getMaxRange(ShipAPI ship) {
        return ship.getMutableStats().getSystemRangeBonus().computeEffective(MIN_DISRUPTION_RANGE);
    }

    protected void applyEffectToTarget(ShipAPI ship, ShipAPI target) {
        if (!target.getFluxTracker().isOverloadedOrVenting()) {
            if (target != ship) {
                target.setOverloadColor(OVERLOAD_COLOR);
                target.getFluxTracker().beginOverloadWithTotalBaseDuration(DISRUPTION_DUR);
                if (target.getFluxTracker().showFloaty()
                        || ship == Global.getCombatEngine().getPlayerShip()
                        || target == Global.getCombatEngine().getPlayerShip()) {
                    target.getFluxTracker().playOverloadSound();
                    target.getFluxTracker().showOverloadFloatyIfNeeded("System Disruption!", OVERLOAD_COLOR, 4.0F, true);
                }

                Global.getCombatEngine().addPlugin(new CR_QuantumDisruptorListener(target));
            }
        }
    }

    public ShipSystemStatsScript.StatusData getStatusData(int index, ShipSystemStatsScript.State state, float effectLevel) {
        float percent = (1.0F - ENERGY_DAM_PENALTY_MULT) * 100.0F;
        return index == 0 && percent > 0.0F ? new ShipSystemStatsScript.StatusData((int) percent + "% less energy damage", false) : null;
    }

    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (system.isOutOfAmmo()) {
            return null;
        } else if (system.getState() != SystemState.IDLE) {
            return null;
        } else {
            ShipAPI target = this.findTarget(ship);
            if (target != null && target != ship) {
                return "READY";
            } else {
                return ship.getShipTarget() != null ? "OUT OF RANGE" : "NO TARGET";
            }
        }
    }

    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        ShipAPI target = this.findTarget(ship);
        return target != null && target != ship;
    }

    // 修正后的内部类
    protected static class CR_QuantumDisruptorListener extends BaseEveryFrameCombatPlugin {
        private final ShipAPI target;

        public CR_QuantumDisruptorListener(ShipAPI target) {
            this.target = target;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (!target.getFluxTracker().isOverloadedOrVenting()) {
                target.resetOverloadColor();
                Global.getCombatEngine().removePlugin(this);
            }
        }
    }
}