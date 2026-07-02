//
// (powered by FernFlower decompiler)
//已经实现舰船主体与模块的时流同步，但似乎可以优化

package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class XIV_LinkedHull extends BaseHullMod {
    private float check = 0.0F;
    private static final String ERROR = "IncompatibleHullmodWarning";
    private static final Set<String> BLOCKED_OMNI = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER_PLAYER_ONLY = new HashSet<>();
    private static final float DEFAULT_ORIGINAL_MASS = 8500.0F;
    private static final int DEFAULT_ORIGINAL_ENGINES = 28;



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

        if (child.hasLaunchBays()) {
            if (child.isPullBackFighters() ^ parent.isPullBackFighters()) {
                child.giveCommand(ShipCommand.PULL_BACK_FIGHTERS, null, 0);
            }

            if (child.getAIFlags() != null) {
                Object target = (Global.getCombatEngine().getPlayerShip() == parent || parent.getAIFlags() == null) ?
                        parent.getShipTarget() : parent.getAIFlags().getCustom(AIFlags.CARRIER_FIGHTER_TARGET);
                if (target != null) {
                    child.getAIFlags().setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 1.0F, target);
                }
            }
        }

        MutableShipStatsAPI parentStats = parent.getMutableStats();
        MutableShipStatsAPI childStats = child.getMutableStats();

        if (parentStats != null && childStats != null) {
            float parentTimeMult = parentStats.getTimeMult().getModifiedValue();
            parentStats.getTimeMult().getModifiedValue();

            if (parent.isAlive()) {
                childStats.getTimeMult().modifyMult("XIV_LinkedHull_TimeSync", parentTimeMult);
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
                    child.getAIFlags().setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 1f, parent.getShipTarget());
                } else if ((parent.getAIFlags() != null)
                        && parent.getAIFlags().hasFlag(AIFlags.CARRIER_FIGHTER_TARGET)
                        && (parent.getAIFlags().getCustom(AIFlags.CARRIER_FIGHTER_TARGET) != null)) {
                    child.getAIFlags().setFlag(AIFlags.CARRIER_FIGHTER_TARGET, 1f, parent.getAIFlags().getCustom(AIFlags.CARRIER_FIGHTER_TARGET));
                }
            }
        }
    }



    private static void advanceParent(ShipAPI parent, List<ShipAPI> children) {
        ShipEngineControllerAPI ec = parent.getEngineController();
        if (ec == null) return;

        String hullId = parent.getHullSpec().getBaseHullId();
        float thrustPerEngine = calculateThrustPerEngine(hullId);
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

        float thrust = totalWorkingEngines * thrustPerEngine;
        float enginePerformance = thrust / Math.max(1.0F, parent.getMassWithModules());
        MutableShipStatsAPI stats = parent.getMutableStats();
        if (stats != null) {
            stats.getAcceleration().modifyMult("XIV_LinkedHull", enginePerformance);
            stats.getDeceleration().modifyMult("XIV_LinkedHull", enginePerformance);
            stats.getTurnAcceleration().modifyMult("XIV_LinkedHull", enginePerformance);
            stats.getMaxTurnRate().modifyMult("XIV_LinkedHull", enginePerformance);
            stats.getMaxSpeed().modifyMult("XIV_LinkedHull", enginePerformance);
            stats.getZeroFluxSpeedBoost().modifyMult("XIV_LinkedHull", enginePerformance);
        }
    }

    private static float calculateThrustPerEngine(String hullId) {
        if (hullId.equals("rs_XIV_Exousia")) {
            return DEFAULT_ORIGINAL_MASS / DEFAULT_ORIGINAL_ENGINES;
        }
        return 9700.0F; // 默认值示例，应根据实际值调整
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        ShipAPI parent = ship.getParentStation();
        if (parent != null) advanceChild(ship, parent);

        if (parent != null) {
            if (parent.getTravelDrive().isActive()) {
                ship.toggleTravelDrive();
            } else {
                ship.getTravelDrive().deactivate();
            }
        }

        List<ShipAPI> children = ship.getChildModulesCopy();
        if (children != null && !children.isEmpty()) {
            advanceParent(ship, children);
        }
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.getMutableStats();
        if (check > 0.0F && --check < 1.0F) {
            ship.getVariant().removeMod(ERROR);
        }

        String hullId = ship.getHullSpec().getBaseHullId();
        if (isModule(hullId)) {
            checkAndRemoveBlockedMods(ship, BLOCKED_OMNI);
            checkAndRemoveBlockedMods(ship, BLOCKED_OTHER);
            checkAndRemoveBlockedMods(ship, BLOCKED_OTHER_PLAYER_ONLY);
        }
    }

    private boolean isModule(String hullId) {
        return hullId.equals("rs_XIV_Exousia_Left_Deck") ||
                hullId.equals("rs_XIV_Exousia_Right_Deck") ||
                hullId.equals("rs_XIV_Exousia_Left_Engine") ||
                hullId.equals("rs_XIV_Exousia_Right_Engine");
    }

    private void checkAndRemoveBlockedMods(ShipAPI ship, @NotNull Set<String> blockedMods) {
        for (String mod : blockedMods) {
            if (ship.getVariant().hasHullMod(mod)) {
                ship.getVariant().removeMod(mod);
                ship.getVariant().addMod(ERROR);
                check = 3.0F;
            }
        }
    }

    static {
        BLOCKED_OMNI.add("high_scatter_amp");

        BLOCKED_OTHER.add("shield_shunt");
        BLOCKED_OTHER.add("unstable_injector");
        BLOCKED_OTHER.add("safetyoverrides");
        BLOCKED_OTHER.add("recovery_shuttles");
        BLOCKED_OTHER.add("additional_berthing");
        BLOCKED_OTHER.add("augmentedengines");
        BLOCKED_OTHER.add("auxiliary_fuel_tanks");
        BLOCKED_OTHER.add("efficiency_overhaul");
        BLOCKED_OTHER.add("expanded_cargo_holds");
        BLOCKED_OTHER.add("hiressensors");
        BLOCKED_OTHER.add("militarized_subsystems");

        BLOCKED_OTHER_PLAYER_ONLY.add("converted_hangar");
        BLOCKED_OTHER_PLAYER_ONLY.add("TSC_converted_hangar");
    }
}