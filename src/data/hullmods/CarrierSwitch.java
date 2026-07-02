package data.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import data.scripts.utils.RSUtil;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CarrierSwitch extends BaseHullMod {
    private static final String SUPPORT_MOD = "ControllingSystem_support";
    private static final String STRIKE_MOD = "ControllingSystem_strike";
    private static final String SUPPORT_PERMA = "CR_carrier_supportcommand";
    private static final String STRIKE_PERMA = "CR_carrier_strikecommand";

    // 使用WeakReference避免内存泄漏
    private static final Map<WeakReference<ShipVariantAPI>, Set<String>> modRecorder = new HashMap<>();

    // 清理计数器，避免频繁清理影响性能
    private static int cleanupCounter = 0;
    private static final int CLEANUP_INTERVAL = 60;

    // 模式枚举
    private enum CarrierMode {
        SUPPORT,
        STRIKE,
        NONE
    }


    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 放宽条件：允许在战役（包括改装界面）和战斗模拟中执行
        if (!shouldProcess(ship)) {
            return;
        }

        ShipVariantAPI variant = ship.getVariant();
        if (variant == null) return;

        // 确保克隆变体，以便保存修改
        if (RSUtil.isInPlayerFleet(ship)) {
            RSUtil.ensureCloneVariant(ship.getFleetMember(), false);
        }

        // 清理过期弱引用
        cleanupStaleReferences();

        // 获取上一次记录的状态
        Set<String> previousMods = getRecordedMods(variant);
        Set<String> currentMods = new HashSet<>(variant.getHullMods());

        // 检测手动移除并执行轮替
        if (previousMods != null) {
            boolean strikeRemoved = previousMods.contains(STRIKE_MOD) && !currentMods.contains(STRIKE_MOD);
            boolean supportRemoved = previousMods.contains(SUPPORT_MOD) && !currentMods.contains(SUPPORT_MOD);

            if (strikeRemoved && !currentMods.contains(SUPPORT_MOD)) {
                applyCarrierMode(variant, CarrierMode.SUPPORT);
                recordCurrentMods(variant);
                RSUtil.refreshRefitUI();
                return;
            } else if (supportRemoved && !currentMods.contains(STRIKE_MOD)) {
                applyCarrierMode(variant, CarrierMode.STRIKE);
                recordCurrentMods(variant);
                RSUtil.refreshRefitUI();
                return;
            }
        }

        // 正常状态维护：确保 perma mod 与主 mod 匹配
        boolean hasSupport = variant.hasHullMod(SUPPORT_MOD);
        boolean hasStrike = variant.hasHullMod(STRIKE_MOD);
        boolean hasSupportPerma = variant.hasHullMod(SUPPORT_PERMA);
        boolean hasStrikePerma = variant.hasHullMod(STRIKE_PERMA);

        if (!hasSupport && !hasStrike) {
            // 未安装任何模式 → 默认打击模式
            applyCarrierMode(variant, CarrierMode.STRIKE);
        } else if (hasStrike) {
            // 当前为打击模式
            if (!hasStrikePerma) {
                safeAddPermaMod(variant, STRIKE_PERMA);
            }
            if (hasSupportPerma) {
                safeRemovePermaMod(variant, SUPPORT_PERMA);
            }
        } else if (hasSupport) {
            // 当前为支援模式
            if (!hasSupportPerma) {
                safeAddPermaMod(variant, SUPPORT_PERMA);
            }
            if (hasStrikePerma) {
                safeRemovePermaMod(variant, STRIKE_PERMA);
            }
        }
        // 更新记录
        recordCurrentMods(variant);
    }

    /**
     * 判断是否应该处理（战役中或战斗模拟）
     */
    private boolean shouldProcess(ShipAPI ship) {
        if (ship == null) return false;
        GameState state = Global.getCurrentState();

        // 生涯模式（包括改装界面）始终处理
        if (state == GameState.CAMPAIGN && Global.getSector() != null) {
            return true;
        }

        // 战斗模拟中处理
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != null && engine.isSimulation()) {
            return true;
        }


        return true;
    }

    /**
     * 获取已记录的上次 mod 集合
     */
    private Set<String> getRecordedMods(ShipVariantAPI variant) {
        WeakReference<ShipVariantAPI> ref = findReference(variant);
        return ref != null ? modRecorder.get(ref) : null;
    }


    /**
     * 记录当前模块状态
     */
    private void recordCurrentMods(ShipVariantAPI variant) {
        WeakReference<ShipVariantAPI> ref = findReference(variant);
        if (ref == null) {
            ref = new WeakReference<>(variant);
            modRecorder.put(ref, new HashSet<>(variant.getHullMods()));
        } else {
            modRecorder.put(ref, new HashSet<>(variant.getHullMods()));
        }
    }

    /**
     * 查找现有的WeakReference
     */
    private WeakReference<ShipVariantAPI> findReference(ShipVariantAPI variant) {
        for (WeakReference<ShipVariantAPI> ref : modRecorder.keySet()) {
            ShipVariantAPI existing = ref.get();
            if (existing != null && existing == variant) {
                return ref;
            }
        }
        return null;
    }

    /**
     * 清理无效的WeakReference
     */
    private void cleanupStaleReferences() {
        cleanupCounter++;
        if (cleanupCounter >= CLEANUP_INTERVAL) {
            cleanupCounter = 0;

            Set<WeakReference<ShipVariantAPI>> toRemove = new HashSet<>();
            for (WeakReference<ShipVariantAPI> ref : modRecorder.keySet()) {
                if (ref.get() == null) {
                    toRemove.add(ref);
                }
            }
            for (WeakReference<ShipVariantAPI> ref : toRemove) {
                modRecorder.remove(ref);
            }

            if (!toRemove.isEmpty()) {
                Global.getLogger(this.getClass()).info("CarrierSwitch: 清理了 " + toRemove.size() + " 个无效引用");
            }
        }
    }

    private void applyCarrierMode(ShipVariantAPI variant, CarrierMode mode) {
        Global.getLogger(this.getClass()).info("CarrierSwitch: 开始应用模式 " + mode);

        // 清理现有模式
        cleanupCurrentMode(variant);

        // 应用新模式
        switch (mode) {
            case SUPPORT:
                safeAddMod(variant, SUPPORT_MOD);
                safeAddPermaMod(variant, SUPPORT_PERMA);
                break;
            case STRIKE:
                safeAddMod(variant, STRIKE_MOD);
                safeAddPermaMod(variant, STRIKE_PERMA);
                break;
            case NONE:
                Global.getLogger(this.getClass()).warn("CarrierSwitch: 尝试应用NONE模式");
                break;
        }

        // 刷新UI
        try {
            RSUtil.refreshRefitUI();
            Global.getLogger(this.getClass()).info("CarrierSwitch: UI刷新完成");
        } catch (Exception e) {
            logError(e);
        }
    }

    /**
     * 清理当前模式
     */
    private void cleanupCurrentMode(ShipVariantAPI variant) {
        Global.getLogger(this.getClass()).info("CarrierSwitch: 开始清理当前模式");

        int removedCount = 0;
        if (variant.hasHullMod(SUPPORT_MOD)) {
            safeRemoveMod(variant, SUPPORT_MOD);
            removedCount++;
        }
        if (variant.hasHullMod(STRIKE_MOD)) {
            safeRemoveMod(variant, STRIKE_MOD);
            removedCount++;
        }
        if (variant.hasHullMod(SUPPORT_PERMA)) {
            safeRemovePermaMod(variant, SUPPORT_PERMA);
            removedCount++;
        }
        if (variant.hasHullMod(STRIKE_PERMA)) {
            safeRemovePermaMod(variant, STRIKE_PERMA);
            removedCount++;
        }

        Global.getLogger(this.getClass()).info("CarrierSwitch: 清理了 " + removedCount + " 个模块");
    }

    // 安全操作方法
    private void safeAddMod(ShipVariantAPI variant, String modId) {
        if (!variant.hasHullMod(modId)) {
            variant.addMod(modId);
            Global.getLogger(this.getClass()).info("CarrierSwitch: 添加模块 " + modId);
        }
    }

    private void safeRemoveMod(ShipVariantAPI variant, String modId) {
        if (variant.hasHullMod(modId)) {
            variant.removeMod(modId);
            Global.getLogger(this.getClass()).info("CarrierSwitch: 移除模块 " + modId);
        }
    }

    private void safeAddPermaMod(ShipVariantAPI variant, String modId) {
        if (!variant.hasHullMod(modId)) {
            variant.addPermaMod(modId, false);
            Global.getLogger(this.getClass()).info("CarrierSwitch: 添加永久模块 " + modId);
        }
    }

    private void safeRemovePermaMod(ShipVariantAPI variant, String modId) {
        if (variant.hasHullMod(modId)) {
            variant.removePermaMod(modId);
            Global.getLogger(this.getClass()).info("CarrierSwitch: 移除永久模块 " + modId);
        }
    }


    private void logError(Exception e) {
        Global.getLogger(this.getClass()).error("CarrierSwitch: UI刷新失败", e);
    }
}