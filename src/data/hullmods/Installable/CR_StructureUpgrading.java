package data.hullmods.Installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.BitSet;

public class CR_StructureUpgrading extends BaseHullMod {

    // ======================== 基础效果参数 ========================
    public static final float ARMOR_MULT_BONUS = 0.25f;      // 装甲减伤比例
    public static final float SMOD_BONUS = 0.15f;            // 内置加成
    public static final float SHIELD_DEC = 0.6f;             // 护盾性能下降比例

    // ======================== 动态机制参数 ========================
    private static final float DAMAGE_ACTIVATION_THRESHOLD = 150f;   // 单次装甲损失触发阈值
    private static final float FLICKER_DURATION = 2f;                // 闪烁持续时间（秒）
    private static final float COOLDOWN = 5f;                        // 触发后冷却时间（秒）
    private static final float TIME_MULT = 2f;                       // 闪烁期间时间流速倍数
    private static final float DAMAGE_MULT = 0.25f;                  // 闪烁期间伤害倍率

    // ======================== 视觉特效参数 ========================
    private static final Color FLICKER_COLOR = new Color(227, 35, 15, 131);
    private static final Color SHIMMER_COLOR = new Color(215, 25, 30, 57);

    // 闪烁抖动参数
    private static final float JITTER_UNDER_INTENSITY = 0.5f;
    private static final int JITTER_UNDER_RANGE = 20;
    private static final float JITTER_UNDER_DURATION = 1f;
    private static final float JITTER_UNDER_MAX_DIST = 5f;

    private static final float JITTER_INTENSITY = 0.7f;
    private static final int JITTER_RANGE = 10;
    private static final float JITTER_DURATION = 25f;
    private static final float JITTER_MAX_DIST = 50f;

    private static final float EXTRA_ALPHA_FLICKER = 0.5f;    // 闪烁时的半透明值
    private static final float EXTRA_ALPHA_NORMAL = 1f;       // 正常状态透明度

    // 装甲恢复参数
    private static final float MIN_ARMOR_FRACTION = 0.4f;      // 装甲恢复时每格不低于最大单格装甲的百分比

    // ======================== 其他常量 ========================
    private static final String EFFECT_ID = "combat_armor_refit";      // 用于修改统计的唯一标识

    // ======================== 自定义数据类 ========================
    private static class ShipData {
        float cooldown = 0f;
        float flickerRemaining = 0f;
        float lastArmor = 0f;
        BitSet destroyedGridPieces = new BitSet();   // 使用 BitSet 提高性能
        boolean newGridDestroyed = false;            // 临时标志，用于记录本帧是否有新格子被摧毁
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (Global.getCombatEngine().isPaused() || !ship.isAlive()) {
            return;
        }

        String key = "CR_STRUCTURE_UPGRADING_DATA_" + ship.getId();
        ShipData data = (ShipData) Global.getCombatEngine().getCustomData().get(key);
        if (data == null) {
            data = new ShipData();
            Global.getCombatEngine().getCustomData().put(key, data);
        }

        MutableShipStatsAPI stats = ship.getMutableStats();

        // 冷却递减
        data.cooldown -= amount;

        // 未在冷却中且未在闪烁中时，检测是否触发
        if (data.cooldown <= 0f && data.flickerRemaining <= 0f) {
            // 一次遍历：计算当前总装甲值，同时检测新摧毁的装甲格
            float currentArmor = updateArmorAndDestroyed(ship, data);
            float armorLoss = data.lastArmor - currentArmor;

            // 触发条件：装甲损失超过阈值 或 有新格子被摧毁
            if (armorLoss > DAMAGE_ACTIVATION_THRESHOLD || data.newGridDestroyed) {
                // 激活闪烁
                data.flickerRemaining = FLICKER_DURATION;
                data.cooldown = COOLDOWN;

                // 视觉特效：抖动和音效
                ship.setJitterUnder(ship, SHIMMER_COLOR, JITTER_UNDER_INTENSITY, JITTER_UNDER_RANGE,
                        JITTER_UNDER_DURATION, JITTER_UNDER_MAX_DIST);
                Global.getSoundPlayer().playSound("system_damper_omega_loop", 1f, 1f,
                        ship.getLocation(), ship.getLocation());
            }

            // 更新上一帧装甲值
            data.lastArmor = currentArmor;
        } else {
            // 未触发时也需要更新装甲值（用于后续帧计算损失）
            data.lastArmor = updateArmorAndDestroyed(ship, data);
        }

        // 处理闪烁状态
        if (data.flickerRemaining > 0f) {
            // 应用时流加速和减伤
            stats.getTimeMult().modifyMult(EFFECT_ID, TIME_MULT);
            stats.getArmorDamageTakenMult().modifyMult(EFFECT_ID, DAMAGE_MULT);
            stats.getHullDamageTakenMult().modifyMult(EFFECT_ID, DAMAGE_MULT);

            // 视觉效果：半透明和抖动
            ship.setExtraAlphaMult(EXTRA_ALPHA_FLICKER);
            ship.setApplyExtraAlphaToEngines(true);
            ship.setJitter(ship, FLICKER_COLOR, JITTER_INTENSITY, JITTER_RANGE,
                    JITTER_DURATION, JITTER_MAX_DIST);

            data.flickerRemaining -= amount;
            if (data.flickerRemaining <= 0f) {
                // 闪烁结束，清除效果并恢复装甲
                stats.getTimeMult().unmodify(EFFECT_ID);
                stats.getArmorDamageTakenMult().unmodify(EFFECT_ID);
                stats.getHullDamageTakenMult().unmodify(EFFECT_ID);
                ship.setExtraAlphaMult(EXTRA_ALPHA_NORMAL);
                ship.setApplyExtraAlphaToEngines(false);

                // 恢复装甲，并更新 lastArmor 为恢复后的总装甲值
                data.lastArmor = regenArmor(ship, data);
            }
        } else {
            // 确保非闪烁状态效果已清除
            stats.getTimeMult().unmodify(EFFECT_ID);
            stats.getArmorDamageTakenMult().unmodify(EFFECT_ID);
            stats.getHullDamageTakenMult().unmodify(EFFECT_ID);
            ship.setExtraAlphaMult(EXTRA_ALPHA_NORMAL);
            ship.setApplyExtraAlphaToEngines(false);
        }
    }

    /**
     * 遍历所有装甲格，计算总装甲值并更新被摧毁格子的记录。
     * @return 当前总装甲值
     */
    private float updateArmorAndDestroyed(ShipAPI ship, ShipData data) {
        int maxX = ship.getArmorGrid().getLeftOf() + ship.getArmorGrid().getRightOf();
        int maxY = ship.getArmorGrid().getAbove() + ship.getArmorGrid().getBelow();
        float total = 0f;
        data.newGridDestroyed = false;

        for (int ix = 0; ix < maxX; ix++) {
            for (int iy = 0; iy < maxY; iy++) {
                int index = ix + iy * maxX;
                float armorValue = ship.getArmorGrid().getArmorValue(ix, iy);
                total += armorValue;

                boolean isDestroyed = ship.getArmorGrid().getArmorFraction(ix, iy) <= 0f;
                if (isDestroyed && !data.destroyedGridPieces.get(index)) {
                    data.newGridDestroyed = true;
                    data.destroyedGridPieces.set(index);
                } else if (!isDestroyed) {
                    data.destroyedGridPieces.clear(index);
                }
            }
        }
        return total;
    }

    /**
     * 平均所有装甲格（不低于最大单格装甲的 MIN_ARMOR_FRACTION），清空被摧毁记录。
     * @return 恢复后的总装甲值
     */
    private float regenArmor(ShipAPI ship, ShipData data) {
        int maxX = ship.getArmorGrid().getLeftOf() + ship.getArmorGrid().getRightOf();
        int maxY = ship.getArmorGrid().getAbove() + ship.getArmorGrid().getBelow();
        int totalCells = maxX * maxY;
        float totalArmor = 0f;
        for (int ix = 0; ix < maxX; ix++) {
            for (int iy = 0; iy < maxY; iy++) {
                totalArmor += ship.getArmorGrid().getArmorValue(ix, iy);
            }
        }
        float avgArmor = totalArmor / totalCells;
        float minAllowed = ship.getArmorGrid().getMaxArmorInCell() * MIN_ARMOR_FRACTION;
        if (avgArmor < minAllowed) {
            avgArmor = minAllowed;
        }

        for (int ix = 0; ix < maxX; ix++) {
            for (int iy = 0; iy < maxY; iy++) {
                ship.getArmorGrid().setArmorValue(ix, iy, avgArmor);
            }
        }

        // 清空被摧毁格子记录，因为所有格子都被修复了
        data.destroyedGridPieces.clear();

        // 返回恢复后的总装甲值
        return avgArmor * totalCells;
    }

    // ======================== 原有效果方法 ========================
    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getArmorDamageTakenMult().modifyMult(id, 1 - ARMOR_MULT_BONUS);

        stats.getShieldUnfoldRateMult().modifyMult(id, 1 - SHIELD_DEC);
        stats.getShieldTurnRateMult().modifyMult(id, 1 - SHIELD_DEC);
        stats.getShieldUpkeepMult().modifyMult(id, 1 + SHIELD_DEC);

        boolean sMod = isSMod(stats);
        if (sMod) {
            stats.getArmorBonus().modifyMult(id, 1f + SMOD_BONUS);
        }
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null) return false;
        // 需要同时拥有 CrusadersCore 和 CR_ChargingRing，且不能有 CR_EngineRegularBoost
        if (ship.getVariant().hasHullMod("CR_EngineRegularBoost")) return false;
        if (ship.getVariant().hasHullMod("CR_ChargingRing")) return false;
        if(ship.getVariant().hasHullMod("CR_ShieldOscillating")) return false;
        return ship.getVariant().hasHullMod("CrusadersCore");
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) return "The ship does not exist";
        if (!ship.getVariant().hasHullMod("CrusadersCore"))
            return "Requires installation of energy core (CrusadersCore)";
        return null;
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return String.format("%.0f", ARMOR_MULT_BONUS * 100) + "%";
        if (index == 1) return String.format("%.0f", SHIELD_DEC * 100) + "%";
        return null;
    }

    @Override
    public String getSModDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return String.format("%.0f", SMOD_BONUS * 100) + "%";
        return null;
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float pads = 10f;
        float pad = 5f;
        // 基础效果概述
        tooltip.addPara("Through liquid metal stacking and energy field confinement technology, the ship armor is strengthened but will cause additional burden on the shield system.", pads);
        tooltip.addPara("Armor damaged during battle can be replaced by other intact armor on the hull.", pad);
        // 动态机制标题
        tooltip.addSectionHeading("emergency armor compensation", Alignment.MID, pads);
        // 触发条件
        tooltip.addPara("When a single armor loss exceeds %s or any armor grid is completely destroyed, the emergency regeneration program is triggered.", pad, Misc.getHighlightColor(), "150");
        tooltip.addPara("After the program is started, the ship will enter the \"phase bias compensation\" state of %s. In this state:", pad, Misc.getHighlightColor(), "2 seconds");
        tooltip.addPara("Time flow rate increased to %s.", pad, Misc.getHighlightColor(), "200%");
        tooltip.addPara("Armor and hull damage taken reduced by %s.", pad, Misc.getHighlightColor(), "75%");
        tooltip.addPara("After the program ends, all armor cells will be repaired to an average value no less than the maximum single cell armor %s.", pad, Misc.getHighlightColor(), "40%");
        tooltip.addPara("Emergency Regeneration has a %s cooldown and cannot be triggered again during the cooldown period.", pad, Misc.getHighlightColor(), "5 seconds");
    }

}