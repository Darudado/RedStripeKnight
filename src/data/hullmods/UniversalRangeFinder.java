package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier;
import com.fs.starfarer.api.combat.listeners.WeaponOPCostModifier;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;

public class UniversalRangeFinder extends BaseHullMod {
    // 更新为百分比加成
    public static float SMALL_SLOT_BONUS_PERCENT = 25f; // 小槽位40%加成
    public static float MEDIUM_SLOT_BONUS_PERCENT = 15f; // 中槽位25%加成
    public static float LARGE_SLOT_BONUS_FLAT = 75f; // 大槽位150点固定加成
    public static int SMALL_SLOT_OP_MODIFIER = -2;
    public static int MEDIUM_SLOT_OP_MODIFIER = -3;
    public static int LARGE_SLOT_OP_MODIFIER = 4;

    // 获取舰船最大武器槽（无论实弹或能量）
    public static WeaponSize getLargestSlot(ShipAPI ship) {
        if (ship == null) {
            return null;
        } else {
            WeaponSize largest = null;

            for(WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
                if (!slot.isDecorative() &&
                        (slot.getWeaponType() == WeaponType.BALLISTIC ||
                                slot.getWeaponType() == WeaponType.ENERGY||
                                slot.getWeaponType() == WeaponType.MISSILE) &&
                        (largest == null || largest.ordinal() < slot.getSlotSize().ordinal())) {
                    largest = slot.getSlotSize();
                }
            }

            return largest;
        }
    }

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // 添加 OP 修改监听器
        stats.addListener(new UniversalOPModifier());
        boolean sMod = isSMod(stats);
        if (sMod) {
            stats.getEnergyWeaponFluxCostMod().modifyMult(id , 0.5f);
            stats.getBallisticWeaponFluxCostMod().modifyMult(id , 0.5f);
            stats.getEnergyRoFMult().modifyMult(id , 1.8f);
            stats.getBallisticRoFMult().modifyMult(id , 1.8f);
            stats.getEnergyWeaponDamageMult().modifyMult(id, 0.6f);
            stats.getBallisticWeaponDamageMult().modifyMult(id, 0.6f);
        }

    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        WeaponSize largest = getLargestSlot(ship);
        if (largest != null) {
            // 只保留射程修改器
            ship.addListener(new UniversalRangeModifier());
        }
    }

    public String getSModDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + 50 + "%";
        if (index == 1) return "" + 80 + "%";
        if (index == 2) return "" + 40 + "%";
        return null;
    }

    private static class UniversalOPModifier implements WeaponOPCostModifier {

        public UniversalOPModifier() {
        }

        @Override
        public int getWeaponOPCost(MutableShipStatsAPI stats, WeaponSpecAPI weapon, int currCost) {
            // 排除导弹武器
            if (weapon.getType() == WeaponType.MISSILE) {
                return currCost;
            }

            // 根据武器尺寸应用 OP 修改
            switch (weapon.getSize()) {
                case SMALL:
                    return currCost + SMALL_SLOT_OP_MODIFIER;
                case MEDIUM:
                    return currCost + MEDIUM_SLOT_OP_MODIFIER;
                case LARGE:
                    return currCost + LARGE_SLOT_OP_MODIFIER;
                default:
                    return currCost;
            }
        }
    }

    // 确保声明影响 OP
    public boolean affectsOPCosts() {
        return true;
    }



    public boolean isApplicableToShip(ShipAPI ship) {
        WeaponSize largest = getLargestSlot(ship);
        if (ship != null && largest == null) {
            return false;
        } else {
            return this.getUnapplicableReason(ship) == null;
        }
    }

    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) return null;
        // 条件1: 必须有实弹或能量武器槽
        if (getLargestSlot(ship) == null)
            return "The ship does not have slots for ballistic or energy weapons";
        // 条件2: 仅限驱逐舰/巡洋舰/主力舰
        if (ship.getHullSize() != HullSize.CAPITAL_SHIP &&
                ship.getHullSize() != HullSize.DESTROYER &&
                ship.getHullSize() != HullSize.CRUISER)
            return "Can only be installed on destroyers or larger ships";
        return null; // 可安装
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 3.0F;
        float opad = 10.0F;
        Color h = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        Color good = Misc.getPositiveHighlightColor();
        Color red = Misc.getNegativeHighlightColor();
        Color yellow = new Color(255, 255, 150);

        // 原有射程描述
        tooltip.addPara("Unified Weapon Range System: Optimizes ship weapon range distribution and applies hard caps.", opad);

        // OP调整表格
        tooltip.addSectionHeading("Ordnance Point (OP) adjustment", Alignment.MID, opad);
        tooltip.addPara("Adjusts Ordnance Point costs based on weapon size:", pad);

        float col1W = 120.0F;
        float colW = 80.0F;

        tooltip.beginTable(
                Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(),
                Misc.getBrightPlayerColor(),
                20.0F,
                true,
                true,
                new Object[]{"Weapon slot", col1W, "OP adjustment", colW}
        );

        String smallOP = (SMALL_SLOT_OP_MODIFIER < 0 ? "-" : "+") + Math.abs(SMALL_SLOT_OP_MODIFIER);
        String mediumOP = (MEDIUM_SLOT_OP_MODIFIER < 0 ? "-" : "+") + Math.abs(MEDIUM_SLOT_OP_MODIFIER);
        String largeOP = (LARGE_SLOT_OP_MODIFIER < 0 ? "-" : "+") + Math.abs(LARGE_SLOT_OP_MODIFIER);

        tooltip.addRow(SMALL_SLOT_OP_MODIFIER < 0 ? good : bad, "Small slot", smallOP);
        tooltip.addRow(MEDIUM_SLOT_OP_MODIFIER < 0 ? good : bad, "medium slot", mediumOP);
        tooltip.addRow(LARGE_SLOT_OP_MODIFIER < 0 ? good : bad, "Large slot", largeOP);
        tooltip.addTable("", 0, pad);

        tooltip.addPara("NOTE: The Ordnance Point cost of %s is unaffected.",
                opad, bad, "missile weapons");

        // 射程加成表格（包含上限说明）
        tooltip.addSectionHeading("range bonus system", Alignment.MID, opad);
        tooltip.addPara("Layered range bonus system:", pad);

        // 加成表格
        tooltip.beginTable(
                Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(),
                Misc.getBrightPlayerColor(),
                20.0F,
                true,
                true,
                new Object[]{"Slot type", col1W, "Bonus value", colW}
        );

        tooltip.addRow(h, "Small slot", "+" + (int)SMALL_SLOT_BONUS_PERCENT + "%");
        tooltip.addRow(h, "medium slot", "+" + (int)MEDIUM_SLOT_BONUS_PERCENT + "%");
        tooltip.addRow(h, "Large slot", "+" + (int)LARGE_SLOT_BONUS_FLAT);
        tooltip.addTable("", 0, pad);

        // 全局加成说明
        tooltip.addPara("▸ All weapons gain %s base range bonus",
                pad, h, "+300");

        // 射程上限说明（重点更新部分）
        tooltip.addPara("▸ Forced range cap:",
                pad, red);
        tooltip.addPara("• Small weapons: %s",
                pad, bad, "≤ 800");
        tooltip.addPara("• Medium/Large Weapons: %s",
                pad, bad, "≤ 850");

        // 计算示例
        tooltip.addSectionHeading("Calculation example", Alignment.MID, opad);
        tooltip.addPara("Small weapons (base 400):", pad);
        tooltip.addPara("　(400 + 300) × 1.25 = 875 → %s",
                pad, red, "Forced down to 800");

        tooltip.addPara("Large weapons (base 700):", pad);
        tooltip.addPara("　700 + 300 + 150 = 1150 → %s",
                pad, red, "Forced down to 850");

        // 排除规则
        tooltip.addPara("Note: %s is not affected by bonuses and restrictions",
                opad, bad, "Point defense and missile weapons");
    }

    public float getTooltipWidth() {
        return 500.0F;
    }


    // 射程修改器实现（保持不变）
    private static class UniversalRangeModifier implements WeaponBaseRangeModifier {
        @Override
        public float getWeaponBaseRangePercentMod(ShipAPI ship, WeaponAPI weapon) {
            // 保持不变，返回百分比加成
            if (weapon.getType() == WeaponType.MISSILE ||
                    weapon.hasAIHint(WeaponAPI.AIHints.PD)) {
                return 0;
            }
            if (weapon.getType() != WeaponType.BALLISTIC &&
                    weapon.getType() != WeaponType.ENERGY) {
                return 0;
            }
            return switch (weapon.getSlot().getSlotSize()) {
                case SMALL -> SMALL_SLOT_BONUS_PERCENT;
                case MEDIUM -> MEDIUM_SLOT_BONUS_PERCENT;
                default -> 0;
            };
        }

        @Override
        public float getWeaponBaseRangeMultMod(ShipAPI ship, WeaponAPI weapon) {
            return 1;
        }

        @Override
        public float getWeaponBaseRangeFlatMod(ShipAPI ship, WeaponAPI weapon) {
            // === 基础射程全局加成 ===
            float totalFlatBonus = 300f;  // 所有武器+300

            // === 大槽额外加成 ===
            if (weapon.getSlot().getSlotSize() == WeaponSize.LARGE) {
                totalFlatBonus += LARGE_SLOT_BONUS_FLAT;
            }

            // === 获取基础射程和百分比加成 ===
            float baseRange = weapon.getSpec().getMaxRange();
            float percentMod = getWeaponBaseRangePercentMod(ship, weapon) / 100f;
            float currentRange = (baseRange + totalFlatBonus) * (1 + percentMod);

            // === 射程上限限制 ===
            float maxAllowed;
            switch (weapon.getSlot().getSlotSize()) {
                case SMALL:
                    maxAllowed = 800f;   // 小槽硬上限800
                    break;
                case MEDIUM:
                    maxAllowed = 850f;  // 中/大槽硬上限850
                    break;
                default:
                    maxAllowed = Float.MAX_VALUE;
            }

            // === 应用射程上限 ===
            float finalRange = Math.min(currentRange, maxAllowed);

            // === 返回需要添加的固定值 ===
            return finalRange - baseRange;
        }
    }
}