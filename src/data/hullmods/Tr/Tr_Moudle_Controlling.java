package data.hullmods.Tr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Tr_Moudle_Controlling extends BaseHullMod {
    private float check = 0.0F;
    private static final String ERROR = "IncompatibleHullmodWarning";
    private static final Set<String> BLOCKED_OMNI = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER_PLAYER_ONLY = new HashSet<>();

    // 存储舰船ID对应的原始质量和引擎数量
    private static final Map<String, ShipData> SHIP_DATA = new HashMap<>();

    static {
        // 初始化舰船数据
        SHIP_DATA.put("rs_Tr_6_Inle", new ShipData(7550f, 19));
        SHIP_DATA.put("rs_Tr_5_fiver", new ShipData(5750f, 7));
        SHIP_DATA.put("rs_Tr_4_rozet", new ShipData(1750f, 19));

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
        BLOCKED_OTHER_PLAYER_ONLY.add("CR_Votum");
        BLOCKED_OTHER_PLAYER_ONLY.add("CR_Circumvenire");
        BLOCKED_OTHER_PLAYER_ONLY.add("CR_Retinere");
    }

    // 舰船数据内部类
    private static class ShipData {
        final float originalMass;
        final int originalEngines;

        ShipData(float mass, int engines) {
            this.originalMass = mass;
            this.originalEngines = engines;
        }
    }

    private static void advanceChild(ShipAPI child, ShipAPI parent) {
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

        // 时流同步优化 - 修复时间乘数同步
        MutableShipStatsAPI parentStats = parent.getMutableStats();
        MutableShipStatsAPI childStats = child.getMutableStats();

        if (parentStats != null && childStats != null) {
            float parentTimeMult = parentStats.getTimeMult().getModifiedValue();
            if (parent.isAlive()) {
                // 使用更直接的同步方式，参考RS_LinkedHull
                childStats.getTimeMult().modifyMult("Tr_LinkedHull_TimeSync", parentTimeMult);
            }
        }

        // 目标同步优化 - 避免不必要的设置
        if (parent.getShipTarget() != null) {
            ShipAPI currentTarget = child.getShipTarget();
            if (currentTarget == null || !currentTarget.equals(parent.getShipTarget())) {
                child.setShipTarget(parent.getShipTarget());
            }
        }

        // 舰载机命令同步
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
        String hullId = parent.getHullSpec().getBaseHullId();

        // 检查是否为需要计算模块引擎的舰船
        if (SHIP_DATA.containsKey(hullId)) {
            calculateEnginePerformance(parent, children, hullId);
        }
    }

    private static void calculateEnginePerformance(ShipAPI parent, List<ShipAPI> children, String hullId) {
        ShipEngineControllerAPI ec = parent.getEngineController();
        if (ec == null) return;

        ShipData data = SHIP_DATA.get(hullId);
        if (data == null) return;

        // 计算每个引擎的基础推力
        float thrustPerEngine = data.originalMass / data.originalEngines;

        // 计算工作引擎总数（包括模块的贡献）
        float totalWorkingEngines = 0f;

        // 计算父舰自身的引擎贡献 - 参考RS_LinkedHull的简化方式
        totalWorkingEngines += ec.getShipEngines().size();

        // 计算子模块的引擎贡献 - 参考RS_LinkedHull的贡献计算方式
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

        // 计算总推力
        float totalThrust = totalWorkingEngines * thrustPerEngine;

        // 计算引擎性能（推力/质量比）
        float enginePerformance = totalThrust / Math.max(1f, parent.getMassWithModules());

        // 限制性能范围，避免极端值
        enginePerformance = Math.max(0.3f, Math.min(enginePerformance, 2.0f));

        // 应用性能修正到机动性 - 参考RS_LinkedHull的直接应用方式
        String modId = "Tr_ModuleEngine";
        MutableShipStatsAPI stats = parent.getMutableStats();

        if (stats != null) {
            stats.getAcceleration().modifyMult(modId, enginePerformance);
            stats.getDeceleration().modifyMult(modId, enginePerformance);
            stats.getTurnAcceleration().modifyMult(modId, enginePerformance);
            stats.getMaxTurnRate().modifyMult(modId, enginePerformance);
            stats.getMaxSpeed().modifyMult(modId, enginePerformance);
            stats.getZeroFluxSpeedBoost().modifyMult(modId, enginePerformance);
        }

        // 调试信息（可选）
        if (parent == Global.getCombatEngine().getPlayerShip()) {
            Global.getCombatEngine().maintainStatusForPlayerShip(
                    "TR_ENGINE_PERF",
                    "graphics/icons/hullsys/high_energy_focus.png",
                    "引擎性能",
                    String.format("性能系数: %.2f", enginePerformance),
                    false
            );
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        // 处理子模块逻辑
        ShipAPI parent = ship.getParentStation();
        if (parent != null) {
            advanceChild(ship, parent);

            // 旅行驱动器同步
            if (parent.getTravelDrive().isActive()) {
                ship.toggleTravelDrive();
            } else {
                ship.getTravelDrive().deactivate();
            }
        }

        // 处理父舰逻辑
        List<ShipAPI> children = ship.getChildModulesCopy();
        if (children != null && !children.isEmpty()) {
            advanceParent(ship, children);
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 清理不兼容的插件
        String hullId = ship.getHullSpec().getBaseHullId();
        if (isModule(hullId)) {
            checkAndRemoveBlockedMods(ship, BLOCKED_OMNI);
            checkAndRemoveBlockedMods(ship, BLOCKED_OTHER);
            checkAndRemoveBlockedMods(ship, BLOCKED_OTHER_PLAYER_ONLY);
        }

        // 清除错误提示（如果存在）
        if (check > 0.0F && --check < 1.0F) {
            ship.getVariant().removeMod(ERROR);
        }
    }

    private boolean isModule(String hullId) {
        return hullId.equals("rs_Tr_5_mainwing_left") ||
                hullId.equals("rs_Tr_5_mainwing_right") ||
                hullId.equals("rs_fiver_subwing_right") ||
                hullId.equals("rs_fiver_subwing_left") ||
                hullId.equals("rs_fiver_armowing_right") ||
                hullId.equals("rs_fiver_armowing_left") ||
                hullId.equals("rs_fiver_wingengineLeft") ||
                hullId.equals("rs_fiver_wingengineRight")||
                hullId.equals("rs_Tr_1_left_shield")||
                hullId.equals("rs_Tr_1_right_shield")||
                hullId.equals("rs_lordanes_ring")||
                hullId.equals("rs_tr_4_engine");
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
}