package data.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.hullmods.BaseLogisticsHullMod;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;

public class SpecializedMobileArmor_module extends BaseLogisticsHullMod {
    public static final float WEAPON_ENGINE_DAMAGE_REDUCTION = 0.25f; // 武器引擎伤害减免25%
    public static final float EMP_DAMAGE_REDUCTION = 0.25f; // EMP伤害减免25%

    public static float PROFILE_MULT = 0.1f; // 传感器信号乘数
    public static final float DAMAGE_MULT = 0f; // 无殉爆伤害

    // 护卫舰特殊规则相关常量（继承自机动兵器ID卡）
    public static float FRIGATE_PD_DAMAGE_MULT = 1.5f; // 点防伤害乘数

    // 数据键定义
    private static final String FRIGATE_RULES_KEY = "moci_ma_module_frigate_rules_active";

    // 模块隐藏相关常量
    public static final String MODULE_HIDE_TIMER_KEY = "moci_ma_module_hide_timer";
    public static final float MODULE_HIDE_DURATION = 1f; // 隐藏持续时间（秒）

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // 降低武器和引擎受到的伤害
        stats.getWeaponDamageTakenMult().modifyMult(id, 1f - WEAPON_ENGINE_DAMAGE_REDUCTION);
        stats.getEngineDamageTakenMult().modifyMult(id, 1f - WEAPON_ENGINE_DAMAGE_REDUCTION);

        // 降低EMP伤害
        stats.getEmpDamageTakenMult().modifyMult(id, 1f - EMP_DAMAGE_REDUCTION);

        // 模块脱离几率增加100%
        stats.getDynamic().getStat(Stats.MODULE_DETACH_CHANCE_MULT).modifyFlat(id, 100f);
        // 殉爆伤害减免
        stats.getDynamic().getStat(Stats.EXPLOSION_DAMAGE_MULT).modifyMult(id, DAMAGE_MULT);
        // EMP伤害免疫
        stats.getEmpDamageTakenMult().modifyMult(id, 0f);
        // 传感器信号降低
        stats.getSensorProfile().modifyMult(id, PROFILE_MULT);
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 添加防殉爆和碰撞伤害监听器
        if (!ship.hasListenerOfClass(SpecializedMobileArmor.MobileArmorDamageListener.class)) {
            ship.addListener(new SpecializedMobileArmor.MobileArmorDamageListener());
        }

        // 移除模块幅能共享监听器
        if (!ship.hasListenerOfClass(SpecializedMobileArmor.ModuleFluxSharingListener.class)) {
            ship.addListener(new SpecializedMobileArmor.ModuleFluxSharingListener(ship));
        }

        ShipAPI parentShip = ship.getParentStation();
        if (parentShip != null && parentShip.getHullSize() == ShipAPI.HullSize.FRIGATE &&
                parentShip.getVariant().hasHullMod("SpecializedMobileArmor")) {

            // 检查母舰是否应该应用护卫舰特殊规则
            if (shouldParentApplyFrigateRules(parentShip)) {
                // 标记模块需要应用护卫舰规则
                ship.setCustomData(FRIGATE_RULES_KEY, true);
            }
        }

        // 移除无军官CR惩罚检查监听器（改为直接在applyEffectsBeforeShipCreation中处理）
        // if (!ship.hasListenerOfClass(NoOfficerCRPenaltyListener.class)) {
        //     ship.addListener(new NoOfficerCRPenaltyListener(ship, id));
        // }
    }

    private void hideWeaponCovers(ShipAPI ship) {
        if (ship.getLargeHardpointCover() != null) {
            ship.getLargeHardpointCover().setSize(0.0f, 0.0f);
        }
        if (ship.getMediumHardpointCover() != null) {
            ship.getMediumHardpointCover().setSize(0.0f, 0.0f);
        }
        if (ship.getSmallHardpointCover() != null) {
            ship.getSmallHardpointCover().setSize(0.0f, 0.0f);
        }
        if (ship.getLargeTurretCover() != null) {
            ship.getLargeTurretCover().setSize(0.0f, 0.0f);
        }
        if (ship.getMediumTurretCover() != null) {
            ship.getMediumTurretCover().setSize(0.0f, 0.0f);
        }
        if (ship.getSmallTurretCover() != null) {
            ship.getSmallTurretCover().setSize(0.0f, 0.0f);
        }

        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon != null && (weapon.getSlot().isTurret() || weapon.getSlot().isHardpoint())) {
                weapon.ensureClonedSpec();
            }
        }
    }

    private boolean shouldParentApplyFrigateRules(ShipAPI parentShip) {
        // 检查船体尺寸是否为护卫舰
        if (parentShip.getHullSize() != ShipAPI.HullSize.FRIGATE) {
            return false;
        }

        // 检查是否安装了禁用特殊规则的船插
        String[] disableHullmods = { "Moci_EinstWeapon", "Moci_SuperAlloyArmor", "Moci_PsycommuSystem" };
        for (String hullMod : disableHullmods) {
            if (parentShip.getVariant().hasHullMod(hullMod)) {
                return false;
            }
        }

        // 检查是否安装了完全禁用的船插
        String[] disableAllHullmods = { "strikeCraft", "armaa_strikeCraftFrig" };
        for (String hullMod : disableAllHullmods) {
            if (parentShip.getVariant().hasHullMod(hullMod)) {
                return false;
            }
        }

        return true;
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        // 基础安全检查
        if (ship == null || !ship.isAlive()) {
            return;
        }

        // 检查是否为模块舰船
        if (!ship.isStationModule()) {
            return; // 不是模块，无需同步
        }

        // 获取父舰船（母舰）
        ShipAPI parentShip = ship.getParentStation();

        // 安全检查：确保母舰存在且有效
        if (parentShip == null || !parentShip.isAlive()) {
            return;
        }

        // 每帧直接同步碰撞类型：将模块的碰撞类型设置为与母舰相同
        if (ship.getCollisionClass() != parentShip.getCollisionClass()) {
            ship.setCollisionClass(parentShip.getCollisionClass());
        }

        // 处理模块隐藏逻辑
        handleModuleHiding(ship, amount);
        hideWeaponCovers(ship);

    }

    private void handleModuleHiding(ShipAPI ship, float amount) {
        // 检查是否有隐藏计时器
        Object timerObj = ship.getCustomData().get(MODULE_HIDE_TIMER_KEY);

        if (timerObj instanceof Float) {
            float timer = (Float) timerObj;

            if (timer > 0f) {
                // 在整个持续时间内保持完全隐藏
                ship.setAlphaMult(0f);

                // 更新计时器
                timer -= amount;
                ship.setCustomData(MODULE_HIDE_TIMER_KEY, timer);

                // 如果计时器结束，直接恢复完全可见
                if (timer <= 0f) {
                    ship.removeCustomData(MODULE_HIDE_TIMER_KEY);
                    ship.setAlphaMult(1f);
                }
            }
        }
    }

    public static void startModuleHiding(ShipAPI ship) {
        if (ship != null && ship.isStationModule()) {
            ship.setCustomData(MODULE_HIDE_TIMER_KEY, MODULE_HIDE_DURATION);
        }
    }

    public boolean shouldAddDescriptionToTooltip(ShipAPI.HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        // 只对模块舰船生效
        return ship != null && ship.isStationModule();
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        float pad = 3f;
        Color h = Misc.getHighlightColor();
        Color good = Misc.getPositiveHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        tooltip.addPara("属于特殊设计的 %s ，其战斗表现有别于正常舰船模块。", opad, h, "特装机动装甲模块(SMAM)");
        tooltip.addPara("为追求极致的机动火力投送，SMAM在设计上就具有极强的机动能力，不会受到碰撞与殉爆伤害。", pad, h);

        tooltip.addSectionHeading("重点防护", com.fs.starfarer.api.ui.Alignment.MID, opad);
        tooltip.addPara("武器和引擎受到的伤害减少 %s 。", pad, good, (int)(WEAPON_ENGINE_DAMAGE_REDUCTION * 100f) + "%");
        tooltip.addPara("受到的EMP伤害减少 %s 。", pad, good, (int)(EMP_DAMAGE_REDUCTION * 100f) + "%");

        tooltip.addSectionHeading("幅能系统", com.fs.starfarer.api.ui.Alignment.MID, opad);
        tooltip.addPara("舰船的 %s 会在本体与各个模块之间 %s ，自动平衡各部分的幅能等级。", pad, h, "幅能", "实时共享");
    }

}
