package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;

import java.util.List;

public class CR_ModuleContorlling extends BaseHullMod{
    private static void advanceChild(ShipAPI child, ShipAPI parent ) {
        ShipEngineControllerAPI ec = parent.getEngineController();
        if (ec == null) return;

        if (parent.isAlive()) {
            if (ec.isAccelerating()) child.giveCommand(ShipCommand.ACCELERATE, null, 0);
            if (ec.isAcceleratingBackwards()) child.giveCommand(ShipCommand.ACCELERATE_BACKWARDS, null, 0);
            if (ec.isDecelerating()) child.giveCommand(ShipCommand.DECELERATE, null, 0);
            if (ec.isStrafingLeft()) child.giveCommand(ShipCommand.STRAFE_LEFT, null, 0);
            if (ec.isStrafingRight()) child.giveCommand(ShipCommand.STRAFE_RIGHT, null, 0);
            if (ec.isTurningLeft()) child.giveCommand(ShipCommand.TURN_LEFT, null, 0);
            if (ec.isTurningRight()) child.giveCommand(ShipCommand.TURN_RIGHT, null, 0);
        }

        ShipEngineControllerAPI cec = child.getEngineController();
        if (cec != null && (ec.isFlamingOut() || ec.isFlamedOut()) && !cec.isFlamingOut() && !cec.isFlamedOut()) {
            cec.forceFlameout(true);
        }

        MutableShipStatsAPI parentStats = parent.getMutableStats();
        MutableShipStatsAPI childStats = child.getMutableStats();

        if (parentStats != null && childStats != null) {
            float parentTimeMult = parentStats.getTimeMult().getModifiedValue();
            parentStats.getTimeMult().getModifiedValue();

            if (parent.isAlive()) {
                childStats.getTimeMult().modifyMult("RS_LinkedHull_TimeSync", parentTimeMult);
            }
        }

        if (parent.getShipTarget() != null) {  //  检测母舰是否有锁定目标
            child.setShipTarget(parent.getShipTarget()); //  强制同步目标
        }

        if (child.hasLaunchBays()) {
            if (child.isPullBackFighters() ^ parent.isPullBackFighters()) {
                child.giveCommand(ShipCommand.PULL_BACK_FIGHTERS, null, 0);
            }
            if (child.getAIFlags() != null) {
                if (((Global.getCombatEngine().getPlayerShip() == parent) || (parent.getAIFlags() == null))
                        && (parent.getShipTarget() != null)) {
                    child.getAIFlags().setFlag(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET, 1f, parent.getShipTarget());
                } else if ((parent.getAIFlags() != null)
                        && parent.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET)
                        && (parent.getAIFlags().getCustom(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET) != null)) {
                    child.getAIFlags().setFlag(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET, 1f, parent.getAIFlags().getCustom(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET));
                }
            }
        }
    }

    private static void advanceParent(ShipAPI parent, List<ShipAPI> children) {
        ShipEngineControllerAPI ec = parent.getEngineController();
        if (ec == null) return;

        //String hullId = parent.getHullSpec().getBaseHullId();
        //float thrustPerEngine = calculateThrustPerEngine(hullId);
        float totalWorkingEngines = ec.getShipEngines().size();

        for (ShipAPI child : children) {
            if (child.getParentStation() == parent && child.getStationSlot() != null && child.isAlive()) {
                ShipEngineControllerAPI cec = child.getEngineController();
                if (cec == null) continue;

                float contribution = 0.0F;
                for (ShipEngineControllerAPI.ShipEngineAPI ce : cec.getShipEngines()) {
                    if (ce.isActive() && !ce.isDisabled() && !ce.isPermanentlyDisabled() && !ce.isSystemActivated()) {
                        contribution += ce.getContribution();
                    }
                }
                totalWorkingEngines += cec.getShipEngines().size() * contribution;
            }
        }

        //float thrust = totalWorkingEngines * thrustPerEngine;
        //float enginePerformance = thrust / Math.max(1.0F, parent.getMassWithModules());
//        MutableShipStatsAPI stats = parent.getMutableStats();
//        if (stats != null) {
//            stats.getAcceleration().modifyMult("cr_LinkedHull", enginePerformance);
//            stats.getDeceleration().modifyMult("cr_LinkedHull", enginePerformance);
//            stats.getTurnAcceleration().modifyMult("cr_LinkedHull", enginePerformance);
//            stats.getMaxTurnRate().modifyMult("cr_LinkedHull", enginePerformance);
//            stats.getMaxSpeed().modifyMult("cr_LinkedHull", enginePerformance);
//            stats.getZeroFluxSpeedBoost().modifyMult("cr_LinkedHull", enginePerformance);
//        }
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        ShipAPI parent = ship.getParentStation();
        if (parent != null) advanceChild(ship, parent);

        List<ShipAPI> children = ship.getChildModulesCopy();
        if (children != null && !children.isEmpty()) {
            advanceParent(ship, children);
        }

        if (parent != null) {
            if (parent.getTravelDrive().isActive()) {
                ship.toggleTravelDrive();
            } else {
                ship.getTravelDrive().deactivate();
            }
        }

    }


}