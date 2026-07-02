package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.ui.Alignment;

public class OpenPort extends BaseHullMod {
    public static final int CAPACITY_PER_FIGHTER = 300; // 每架战机增加300点容量
    public static final int DISSIPATION_PER_FIGHTER = 15; // 每架战机增加15点耗散能力

    private static final int RATE_LOSS = 30;
    private static final int RATE_REVOCER = 50;

    public static final float SMALL_COST_REDUCTION = 3F;
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

    private int countTotalFighters(MutableShipStatsAPI stats) {
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

        tooltip.addSectionHeading("Detailed parameters", Alignment.MID, pads);

        tooltip.addPara("Assembly optimization:", pads);
        tooltip.addPara("-Small non-missile weapon assembly point requirements -%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "3");
        tooltip.addPara("-Fighter/interceptor/support fighters assembly point requirements -%s, bomber -%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "5", "3");

        tooltip.addPara("Aviation maintenance adjustments:", pads);
        tooltip.addPara("- Carrier-based aircraft loss rate increased by %s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "30%");
        tooltip.addPara("- The replenishment rate of carrier-based aircraft is increased by %s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "50%");

        tooltip.addPara("Air traffic control optimization:", pads);
        tooltip.addPara("Carrier-based aircraft deployed from this ship receive enhancements:", pad);
        tooltip.addPara("Maximum speed +%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "25%");
        tooltip.addPara("Automatic shooting accuracy +%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "45%");

        // 新增：战机容量和耗散加成的描述
        tooltip.addPara("Carrier-based aircraft maintenance space:", pads);
        tooltip.addPara("- Each loaded fighter increases grid capacity by %s points", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "300");
        tooltip.addPara("- Each loaded fighter increases %s points of dissipation ability", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "15");


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

                tooltip.addPara("Currently loading %s fighters:", pads,
                        com.fs.starfarer.api.util.Misc.getHighlightColor(), String.valueOf(totalFighters));
                tooltip.addPara("Grid capacity capacity increase: %s", pad,
                        com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), capacityBonus + "");
                tooltip.addPara("Increase in dissipation capacity: %s", pad,
                        com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), dissipationBonus + "");
            }
        }
    }
}