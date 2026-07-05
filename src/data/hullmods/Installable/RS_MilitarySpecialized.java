package data.hullmods.Installable;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.hullmods.BaseLogisticsHullMod;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.awt.Color;

public class RS_MilitarySpecialized extends BaseLogisticsHullMod {

    // 定义要检测的军事类 hullmod ID 列表
    private static final String[] COMBAT_MOD_IDS = {
            "heavyarmor",               // 重型装甲
            "reinforcedhull",           // 强化船体
            "fluxcoil",                 // 通量线圈
            "fluxdistributor",          // 通量分发器
            "advancedoptics",           // 先进光学
            "targetingunit",            // 目标定位单元
            "stabilizedshieldemitter",  // 稳定护盾发生器
            "armoredweapons",           // 装甲武器
            "turretgyros",
            "hardened_subsystems",       // 硬化子系统
            "magazines",       // 扩展弹匣
            "missileracks",             // 导弹架
            "ecm",                      // 电子对抗
            "auxiliarythrusters",       // 辅助推进器
            "missleracks",
            "eccm",
            "assault_package",
            "escort_package",
            "expanded_deck_crew",
            "EnergyTargetCognetrix",
            "BallisticTargetCognetrix",
            "UniversalRangeFinder",
            "PolariphaseDrive",
            "PhaseDefenseUnit",
            "WeaponOverLoad",
            "defensive_targeting_array",
            "CR_ImprovedWeaponControlling",
            "CR_FighterProducingOverloading",
            "CR_EngineRegularBoost",
            "autorepair",
            "RS_FluxExtendTendency",
            "CR_StructureUpgrading",
            "CR_Retinere",
            "CR_Votum",
            "CR_Circumvenire",

    };

    // 加成配置
    private static final float FLUX_BONUS_PER_MOD = 0.05f;    // 每个 hullmod 提供 5% 伤害加成
    private static final float ARMOR_BONUS_PER_MOD = 0.03f;   // 每个 hullmod 提供 3% 装甲加成
    private static final float SPEED_BONUS_PER_MOD = 0.025f;  // 每个 hullmod 提供 2.5% 速度加成
    private static final float Logistics_Dec_PER_MOD = 0.075f;

    // 加成上限配置
    private static final float MAX_FLUX_BONUS = 0.20f;        // 最大伤害加成 20%
    private static final float MAX_ARMOR_BONUS = 0.12f;       // 最大装甲加成 12%
    private static final float MAX_SPEED_BONUS = 0.10f;       // 最大速度加成 10%
    private static final float MAX_Logistics_Dec = 0.35f;

    // 统计安装的指定 hullmod 数量
    private static int countMods(MutableShipStatsAPI stats) {
        int count = 0;
        for (String id : RS_MilitarySpecialized.COMBAT_MOD_IDS) {
            if (stats.getVariant().hasHullMod(id)) count++;
        }
        return count;
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // 计算安装的军事类 hullmod 数量
        int installedMods = countMods(stats);

        if (installedMods > 0) {
            // 计算实际加成（考虑上限）
            float fluxBonus = Math.min(installedMods * FLUX_BONUS_PER_MOD, MAX_FLUX_BONUS);
            float armorBonus = Math.min(installedMods * ARMOR_BONUS_PER_MOD, MAX_ARMOR_BONUS);
            float speedBonus = Math.min(installedMods * SPEED_BONUS_PER_MOD, MAX_SPEED_BONUS);
            float logisticsDec = Math.min(installedMods * Logistics_Dec_PER_MOD, MAX_Logistics_Dec);
            // 应用加成到舰船属性
            stats.getFluxCapacity().modifyPercent(id, fluxBonus * 100f);
            stats.getFluxDissipation().modifyPercent(id, fluxBonus * 100f);
            stats.getWeaponTurnRateBonus().modifyPercent(id, fluxBonus * 100f);
            stats.getArmorBonus().modifyPercent(id, armorBonus * 100f);
            stats.getMaxSpeed().modifyPercent(id, speedBonus * 100f);
            stats.getSuppliesPerMonth().modifyPercent(id ,logisticsDec * 100f);
        }
    }


    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        float pad = 2f;
        Color highlight = Misc.getHighlightColor();
        Color positive = Misc.getPositiveHighlightColor();
        Color gray = Misc.getGrayColor();
        Color textColor = Misc.getTextColor();


        // 获取当前安装的军事类船插数量
        int installedMods = 0;
        if (ship != null) {
            installedMods = countMods(ship.getMutableStats());
        }

        // 计算当前加成效果
        float currentFluxBonus = Math.min(installedMods * FLUX_BONUS_PER_MOD, MAX_FLUX_BONUS) * 100f;
        float currentArmorBonus = Math.min(installedMods * ARMOR_BONUS_PER_MOD, MAX_ARMOR_BONUS) * 100f;
        float currentSpeedBonus = Math.min(installedMods * SPEED_BONUS_PER_MOD, MAX_SPEED_BONUS) * 100f;
        float currentLogisticsDec = Math.min(installedMods * Logistics_Dec_PER_MOD, MAX_Logistics_Dec) * 100f;

        // 添加效果说明
        tooltip.addSectionHeading("Effect description", Alignment.MID, opad);

        tooltip.addPara("Combat bonuses are provided based on the number of military-type ship plugs installed:", opad, textColor);
        tooltip.addPara("- Each military class ship insert provides:", pad, textColor);
        tooltip.addPara("• %s grid gain", pad, highlight, String.format("+%.1f%%", FLUX_BONUS_PER_MOD * 100f));
        tooltip.addPara("• %s armor bonus", pad, highlight, String.format("+%.1f%%", ARMOR_BONUS_PER_MOD * 100f));
        tooltip.addPara("• %s maximum speed", pad, highlight, String.format("+%.1f%%", SPEED_BONUS_PER_MOD * 100f));
        tooltip.addPara("• %s supply consumption", pad, highlight, String.format("+%.1f%%", Logistics_Dec_PER_MOD * 100f));

        tooltip.addPara("- Bonus limit:", opad, textColor);
        tooltip.addPara("• Maximum grid bonus %s", pad, highlight, String.format("%.0f%%", MAX_FLUX_BONUS * 100f));
        tooltip.addPara("• Maximum armor bonus %s", pad, highlight, String.format("%.0f%%", MAX_ARMOR_BONUS * 100f));
        tooltip.addPara("• Maximum speed bonus %s", pad, highlight, String.format("%.0f%%", MAX_SPEED_BONUS * 100f));
        tooltip.addPara("• Maximum supply consumption %s", pad, highlight, String.format("%.0f%%", MAX_Logistics_Dec * 100f));

        // 显示当前状态（仅在查看具体舰船时显示）
        if (ship != null && !isForModSpec) {
            tooltip.addSectionHeading("Current status", new Color(245, 230, 185, 255), new Color(220, 199, 152, 155), Alignment.MID, opad);

            tooltip.addPara("Installed military ship plug-ins: %s", pad,
                    installedMods > 0 ? positive : gray,
                    String.valueOf(installedMods));

            if (installedMods > 0) {
                tooltip.addPara("Current bonus effects:", pad, textColor);
                tooltip.addPara("• Grid strength increased: %s", 3f, positive, String.format("+%.1f%%", currentFluxBonus));
                tooltip.addPara("• Armor strength: %s", 3f, positive, String.format("+%.1f%%", currentArmorBonus));
                tooltip.addPara("• Maximum speed: %s", 3f, positive, String.format("+%.1f%%", currentSpeedBonus));
                tooltip.addPara("• Supply consumption increased: %s", 3f, positive, String.format("+%.1f%%", currentLogisticsDec));

                // 显示可检测的船插列表（折叠显示）
                if (Keyboard.isKeyDown(Keyboard.KEY_F3)) {
                    tooltip.addPara("Hold down F3 to view detected military-type ships:", opad, textColor);
                    for (String modId : COMBAT_MOD_IDS) {
                        if (ship.getVariant().hasHullMod(modId)) {
                            tooltip.addPara("  • " + getHullModName(modId), pad, highlight);
                        }
                    }
                }
            }
        }

        // 兼容性说明
        tooltip.addSectionHeading("Compatibility Notes", Alignment.MID, pad);
        tooltip.addPara("Number of detectable military ship plugs: %s types", pad, highlight, String.valueOf(COMBAT_MOD_IDS.length));
        tooltip.addPara("Compatible with most ship plug-ins, bonus effects are automatically calculated.", pad, textColor);
    }

    // 辅助方法：获取船插的显示名称
    private String getHullModName(String modId) {
        // 这里可以添加更完整的名称映射
        return switch (modId) {
            case "heavyarmor" -> "heavy armor";
            case "reinforcedhull" -> "Reinforced hull";
            case "fluxcoil" -> "flux coil";
            case "fluxdistributor" -> "flux distributor";
            case "advancedoptics" -> "advanced optics";
            case "targetingunit" -> "targeting unit";
            case "stabilizedshieldemitter" -> "Stable shield generator";
            case "armoredweapons" -> "armored weapons";
            case "turretgyros" -> "Reinforced turret";
            case "hardened_subsystems" -> "hardened subsystem";
            case "magazines" -> "extended magazine";
            case "missileracks" -> "missile rack";
            case "ecm" -> "electronic countermeasures system";
            case "eccm" -> "Electronic countermeasure components";
            case "auxiliarythrusters" -> "Auxiliary thruster";
            case "assault_package" -> "assault kit";
            case "escort_package" -> "escort kit";
            case "expanded_deck_crew" -> "Expanded deck crew";
            case "missleracks" -> "Expanded Missile Racks";
            case "PolariphaseDrive" -> "Bi-phase drive";
            case "PhaseDefenseUnit" -> "Partial Phase Dimension Difference Defense";
            case "WeaponOverLoad" -> "Overload of partial phase weapon system";
            case "EnergyTargetCognetrix" -> "Energy Projection Computing Array";
            case "BallisticTargetCognetrix" -> "mass projection calculation array";
            case "UniversalRangeFinder" -> "universal rangefinder";
            case "defensive_targeting_array" -> "Carrier-based aircraft defense positioning network";
            case "CR_ImprovedWeaponControlling" -> "Fire control upgrade";
            case "CR_FighterProducingOverloading" -> "Fighter Forge Overrelaxation";
            case "CR_EngineRegularBoost" -> "Tidal engine modification";
            case "autorepair" -> "Automatic repair components";

            default -> modId;
        };
    }


    @Override
    public String getSModDescriptionParam(int index, HullSize hullSize) {
        return getDescriptionParam(index, hullSize);
    }

   // public boolean isApplicableToShip(ShipAPI ship) {
        //return ship.getVariant().getHullMods().contains("CrusadersCore");
                //&& !ship.getVariant().getHullMods().contains("PolariphaseDrive");
    //}

    //public String getUnapplicableReason(ShipAPI ship) {
        //return "仅适用于已安装十字军能量核心的舰船";
    //}
}