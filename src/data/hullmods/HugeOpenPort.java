package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.Alignment;

public class HugeOpenPort extends BaseHullMod {
    public static final float REPAIR_BONUS = 0.20f;
    public static final float CR_RECOVERY_BONUS = 0.15f;
    public static final float SUPPLY_REDUCTION = 0.1f;
    public static final int MAX_FLEET_GANTRY = 5; // 最大叠加数量

    // 新增：每架战机的容量和耗散加成
    public static final int CAPACITY_PER_FIGHTER = 300; // 每架战机增加300点容量
    public static final int DISSIPATION_PER_FIGHTER = 15; // 每架战机增加15点耗散能力

    private static final int RATE_LOSS = 30;
    private static final int RATE_REVOCER = 50;

    public static final float SMALL_COST_REDUCTION = 2F;
    public static final float FIGHTER_COST_REDUCTION = 5F;
    public static final float BOMBER_COST_REDUCTION = 3F;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getDynamic().getStat(Stats.REPLACEMENT_RATE_DECREASE_MULT).modifyMult(id, 1+RATE_LOSS * 0.01f);
        stats.getDynamic().getStat(Stats.REPLACEMENT_RATE_INCREASE_MULT).modifyPercent(id, RATE_REVOCER);

        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.SMALL_BALLISTIC_MOD).modifyFlat(id, -SMALL_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.SMALL_ENERGY_MOD).modifyFlat(id, -SMALL_COST_REDUCTION);

        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.FIGHTER_COST_MOD).modifyFlat(id, -FIGHTER_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.INTERCEPTOR_COST_MOD).modifyFlat(id, -FIGHTER_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.SUPPORT_COST_MOD).modifyFlat(id, -FIGHTER_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.BOMBER_COST_MOD).modifyFlat(id, -BOMBER_COST_REDUCTION);

        // 新增：计算并应用战机容量和耗散加成
        int totalFighters = countTotalFighters(stats);
        if (totalFighters > 0) {
            int capacityBonus = totalFighters * CAPACITY_PER_FIGHTER;
            int dissipationBonus = totalFighters * DISSIPATION_PER_FIGHTER;

            stats.getFluxCapacity().modifyFlat(id, capacityBonus);
            stats.getFluxDissipation().modifyFlat(id, dissipationBonus);
        }

        FleetMemberAPI ship = stats.getFleetMember();
        if (ship != null && ship.getFleetData() != null && ship.getFleetData().getFleet() != null) {
            // 检查是否为玩家舰队
            if (!isPlayerFleet(ship.getFleetData().getFleet())) {
                return;
            }

            // 计算舰队中有效的维修龙门架船插数量
            int gantryCount = countActiveGantries(ship.getFleetData().getFleet());

            // 限制最大效果，防止过度叠加
            gantryCount = Math.min(gantryCount, MAX_FLEET_GANTRY);

            // 计算舰队效果强度
            float fleetRepairBonus = REPAIR_BONUS * gantryCount;
            float fleetCRBonus = CR_RECOVERY_BONUS * gantryCount;
            float fleetSupplyBonus = SUPPLY_REDUCTION * gantryCount;

            // 限制最大效果，防止过度叠加
            fleetRepairBonus = Math.min(fleetRepairBonus, 0.45f); // 最大45%维修加成
            fleetCRBonus = Math.min(fleetCRBonus, 0.3f); // 最大30%CR恢复加成
            fleetSupplyBonus = Math.min(fleetSupplyBonus, 0.3f); // 最大30%补给节省

            // 应用舰队效果
            ship.getStats().getCombatEngineRepairTimeMult().modifyMult(id, 1f - fleetRepairBonus);
            ship.getStats().getCombatWeaponRepairTimeMult().modifyMult(id, 1f - fleetRepairBonus);
            ship.getStats().getBaseCRRecoveryRatePercentPerDay().modifyPercent(id, fleetCRBonus * 100f);
            ship.getStats().getSuppliesPerMonth().modifyMult(id, 1f - fleetSupplyBonus);
        }
    }

    public boolean affectsOPCosts() {
        return true;
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 为舰船添加修理舱脚本监听器
        // 该监听器将自动扫描舰船的修理舱武器并初始化修理系统
        ship.addListener(new Moci_RS_RepairBayScript(ship));
    }

    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        MutableShipStatsAPI stats = fighter.getMutableStats();
        stats.getMaxSpeed().modifyPercent(id, 25F);
        stats.getAutofireAimAccuracy().modifyPercent(id, 45F);
    }

    private boolean isPlayerFleet(CampaignFleetAPI fleet) {
        return fleet != null && fleet.equals(Global.getSector().getPlayerFleet());
    }

    private int countActiveGantries(CampaignFleetAPI fleet) {
        if (fleet == null)
            return 0;

        int count = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (!member.isMothballed() && member.getVariant() != null &&
                    member.getVariant().hasHullMod("HugeOpenPort")) {
                count++;
            }
        }
        return count;
    }

    // 新增：计算舰船装载的战机总数
    public static int countTotalFighters(MutableShipStatsAPI stats) {
        int totalFighters = 0;

        if (stats.getVariant() != null) {
            for (String wingId : stats.getVariant().getFittedWings()) {
                FighterWingSpecAPI wingSpec = Global.getSettings().getFighterWingSpec(wingId);
                if (wingSpec != null) {
                    totalFighters += wingSpec.getNumFighters();
                }
            }
        }

        return totalFighters;
    }

    public void addPostDescriptionSection(com.fs.starfarer.api.ui.TooltipMakerAPI tooltip,
                                          ShipAPI.HullSize hullSize, ShipAPI ship,
                                          float width, boolean isForModSpec) {
        float pads = 10f;
        float pad = 2f;
        com.fs.starfarer.api.util.Misc.getHighlightColor();
        com.fs.starfarer.api.util.Misc.getPositiveHighlightColor();

        tooltip.addSectionHeading("详细参数", Alignment.MID, pads);

        tooltip.addPara("装配优化:", pads);
        tooltip.addPara("-小型非导弹武器装配点需求 -%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "2");
        tooltip.addPara("-战斗机/拦截机/支援机装配点需求 -%s，轰炸机 -%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "5", "3");

        tooltip.addPara("航空整备调整:", pads);
        tooltip.addPara("- 舰载机损失率增加%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "30%");
        tooltip.addPara("- 舰载机补充速率增加%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "50%");

        tooltip.addPara("航空管制优化:", pads);
        tooltip.addPara("从此舰部署的舰载机获得增强：", pad);
        tooltip.addPara("最大速度 +%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "25%");
        tooltip.addPara("自动射击精度 +%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "45%");

        // 新增：战机容量和耗散加成的描述
        tooltip.addPara("舰载机维护空间:", pads);
        tooltip.addPara("- 每架装载的战机增加 %s 点电网容量", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "300");
        tooltip.addPara("- 每架装载的战机增加 %s 点耗散能力", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "15");

        tooltip.addPara("舰队后勤支援（效果最多叠加5次）:", pads);
        tooltip.addPara("每艘该型舰船为整个舰队提供以下效果：", pad);
        tooltip.addPara("维修速率 +%s（最高 +%s）", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "20%", "45%");
        tooltip.addPara("每日CR恢复速率 +%s（最高 +%s）", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "15%", "30%");
        tooltip.addPara("每月补给消耗 -%s（最高 -%s）", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "10%", "30%");

        // 新增：如果当前有舰船引用，显示实际加成数值
        if (ship != null) {
            int totalFighters = 0;
            if (ship.getVariant() != null) {
                for (String wingId : ship.getVariant().getFittedWings()) {
                    FighterWingSpecAPI wingSpec = Global.getSettings().getFighterWingSpec(wingId);
                    if (wingSpec != null) {
                        totalFighters += wingSpec.getNumFighters();
                    }
                }
            }

            if (totalFighters > 0) {
                int capacityBonus = totalFighters * CAPACITY_PER_FIGHTER;
                int dissipationBonus = totalFighters * DISSIPATION_PER_FIGHTER;

                tooltip.addPara("当前装载 %s 架战机：", pads,
                        com.fs.starfarer.api.util.Misc.getHighlightColor(), String.valueOf(totalFighters));
                tooltip.addPara("电网容量容量增加：%s", pad,
                        com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), capacityBonus + "");
                tooltip.addPara("耗散能力增加：%s", pad,
                        com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), dissipationBonus + "");
            }
        }

        tooltip.addPara("该舰船作为舰队的移动后勤枢纽，通过整合维修设施、补给仓库和指挥中心，为长期深空作战提供了坚实保障。",
                com.fs.starfarer.api.util.Misc.getGrayColor(), pads);
    }


}