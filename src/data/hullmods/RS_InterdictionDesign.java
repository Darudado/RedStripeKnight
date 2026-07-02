package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.awt.*;

public class RS_InterdictionDesign extends BaseHullMod {
    private static final int CR_LOSS = 50;

    public static final float SMALL_COST_REDUCTION = 2F;
    public static final float MEDIUM_COST_REDUCTION = 5F;
    public static final float LARGE_COST_REDUCTION = 8F;

    public static final float MIN_WEAPON_RANGE = 800f;

    // 性能提升配置
    public static final float MAX_FIRERATE_MULT = 1.5f;        // 最高射速倍率
    public static final float MIN_FLUX_COST_MULT = 0.45f;       // 最低辐能消耗倍率
    public static final float MAX_DAMAGE_REDUCTION = 0.75f;    // 最高伤害减免（承受75%伤害）

    // 护盾变形配置 - 主力舰级别
    public static final float CAPITAL_SHIELD_DEVIATION = 72f;  // 主力舰护盾变形强度

    // 状态ID
    private static final String STATS_ID = "RS_InterdictionDesign";
    private static final String SHIELD_STATS_ID = "RS_InterdictionDesign_shield";

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getCRLossPerSecondPercent().modifyPercent(id, CR_LOSS);

        // 武器OP减免 - 使用Stats常量
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.SMALL_BALLISTIC_MOD).modifyFlat(id, -SMALL_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.SMALL_ENERGY_MOD).modifyFlat(id, -SMALL_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.MEDIUM_BALLISTIC_MOD).modifyFlat(id, -MEDIUM_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.MEDIUM_ENERGY_MOD).modifyFlat(id, -MEDIUM_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.LARGE_BALLISTIC_MOD).modifyFlat(id, -LARGE_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.LARGE_ENERGY_MOD).modifyFlat(id, -LARGE_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.FIGHTER_COST_MOD).modifyFlat(id, -10F);

        stats.getBallisticWeaponFluxCostMod().modifyMult(STATS_ID, 0.75f);
        stats.getEnergyWeaponFluxCostMod().modifyMult(STATS_ID, 0.75f);

        stats.getSensorProfile().modifyMult(id, 0.5F);


    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!Global.getCombatEngine().isPaused() && ship.isAlive()) {
            if (ship.getShield() != null) {
                ship.getShield().setRadius(ship.getShield().getRadius(),
                        Global.getSettings().getSpriteName("fx", "CR_shields_OUT"),
                        Global.getSettings().getSpriteName("fx", "CR_shields_IN")

                );
            }
            // 计算血量比率 (0 = 空血, 1 = 满血)
            float currentHP = ship.getHitpoints();
            float maxHP = ship.getMaxHitpoints();
            float hpRatio = currentHP / maxHP;

            // 计算性能提升系数 (0 = 无提升, 1 = 最大提升)
            // 血量越低，提升系数越高
            float performanceFactor = 1f - hpRatio;

            // 应用性能提升
            applyPerformanceBoost(ship, performanceFactor);


            // 可选：在玩家舰船上显示状态信息
            if (ship == Global.getCombatEngine().getPlayerShip()) {
                displayStatusInfo(ship, performanceFactor, hpRatio);
            }
        }
    }

    private void applyPerformanceBoost(ShipAPI ship, float performanceFactor) {
        MutableShipStatsAPI stats = ship.getMutableStats();

        // 武器射速提升：线性从1倍到2.5倍
        float fireRateMultiplier = 1f + MAX_FIRERATE_MULT * performanceFactor;
        stats.getBallisticRoFMult().modifyMult(STATS_ID, fireRateMultiplier);
        stats.getEnergyRoFMult().modifyMult(STATS_ID, fireRateMultiplier);

        // 辐能消耗降低：线性从1倍到0.4倍
        float fluxCostMultiplier = 0.75f - (0.75f - MIN_FLUX_COST_MULT) * performanceFactor;
        stats.getBallisticWeaponFluxCostMod().modifyMult(STATS_ID, fluxCostMultiplier);
        stats.getEnergyWeaponFluxCostMod().modifyMult(STATS_ID, fluxCostMultiplier);
        stats.getMissileWeaponFluxCostMod().modifyMult(STATS_ID, fluxCostMultiplier);

        // 伤害减免：线性从0%到25%减免（承受100%到75%伤害）
        float damageTakenMultiplier = 1f - (1f - MAX_DAMAGE_REDUCTION) * performanceFactor;
        stats.getArmorDamageTakenMult().modifyMult(STATS_ID, damageTakenMultiplier);
        stats.getHullDamageTakenMult().modifyMult(STATS_ID, damageTakenMultiplier);
        stats.getShieldDamageTakenMult().modifyMult(STATS_ID, damageTakenMultiplier);
    }

    private void displayStatusInfo(ShipAPI ship, float performanceFactor, float hpRatio) {
        // 计算当前加成的具体数值
        float currentFireRate = 1f + (MAX_FIRERATE_MULT - 1f) * performanceFactor;
        float currentFluxCost = 1f - (1f - MIN_FLUX_COST_MULT) * performanceFactor;
        float currentDamageReduction = (1f - (1f - (1f - MAX_DAMAGE_REDUCTION) * performanceFactor)) * 100f;

        String fireRateStr = String.format("%.1f", currentFireRate);
        String fluxCostStr = String.format("%.1f", currentFluxCost);
        String damageRedStr = String.format("%.0f", currentDamageReduction);

        // 添加护盾变形状态信息（只对主力舰显示）
        String shieldInfo = "";
        if (ship.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP && ship.getShield() != null && !ship.getShield().isOff()) {
            shieldInfo = " + Dynamic Shield";
        }

        Global.getCombatEngine().maintainStatusForPlayerShip(
                STATS_ID,
                "graphics/icons/hullsys/phase_anchor.png", // 可以使用合适的图标
                "VIII Interdiction Design" + shieldInfo,
                "HP: " + (int)(hpRatio * 100) + "% - Performance Boost: " + (int)(performanceFactor * 100) + "%",
                false
        );
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 添加射程修正监听器
        if (!ship.hasListener(new InterdictionRangeModifier())) {
            ship.addListener(new InterdictionRangeModifier());
        }
    }

    @Override
    public boolean affectsOPCosts() {
        return true;
    }

    // 清理状态，避免在战斗外保留效果


    public static class InterdictionRangeModifier implements WeaponBaseRangeModifier {
        @Override
        public float getWeaponBaseRangePercentMod(ShipAPI ship, WeaponAPI weapon) {
            return 0f; // 不使用百分比修正
        }

        @Override
        public float getWeaponBaseRangeMultMod(ShipAPI ship, WeaponAPI weapon) {
            return 1f; // 不使用乘数修正
        }

        @Override
        public float getWeaponBaseRangeFlatMod(ShipAPI ship, WeaponAPI weapon) {
            // 获取武器基础射程
            float baseRange = weapon.getSpec().getMaxRange();

            // 如果基础射程低于1000，返回差值
            if (baseRange < MIN_WEAPON_RANGE) {
                return MIN_WEAPON_RANGE - baseRange;
            }

            // 否则不修改射程
            return 0f;
        }
    }

    // 可选：添加描述参数
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "1000";
        if (index == 1) return "50%";  // 射速提升
        if (index == 2) return "60%";  // 辐能消耗降低
        if (index == 3) return "25%";  // 伤害减免
        if (index == 4 && hullSize == ShipAPI.HullSize.CAPITAL_SHIP) return "动态护盾变形"; // 护盾变形效果
        return null;
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        float pad = 2f;
        Color highlight = Misc.getHighlightColor();
        Color h = Misc.getPositiveHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        tooltip.addPara("特殊设计的舰船框架，专注机动性与进攻能力的提升",  opad,h);
        tooltip.addSectionHeading("效果", Alignment.MID, opad);
        tooltip.addPara("非导弹武器基础射程提升至 %s 以上 ", pad, h, "" +800);
        tooltip.addPara("传感器截面 %s", pad, h, "降低50%");
        tooltip.addPara("小型武器装配点 %s，中型 %s，大型 %s，战机 %s", pad, h, "-2", "-5", "-8", "-10");
        tooltip.addPara("在舰船结构受到伤害时激发额外效果，按住 %s 以查看详细机制", opad, highlight,  "F3" );
        if (Keyboard.isKeyDown(Keyboard.KEY_F3)) {
            tooltip.addPara("实弹与能量武器射速随受损程度提升，最多 +%s", pad, h, "50%");
            tooltip.addPara("武器辐能消耗随受损程度进一步降低，最多降至 %s", pad, h, "45%");
            tooltip.addPara("舰体伤害减免随受损程度增加，最多减免 %s", pad, h, "25%");
        }
    }

    private static String getString(int ID) {
        return Global.getSettings().getString("cr_hullmods", String.format("%s_%d", "ImpregnableRampart", ID));
    }
}