package data.hullmods.Tr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.magiclib.util.MagicIncompatibleHullmods;

import java.awt.Color;
import java.util.ArrayList;

public class Tr_DesignFrame extends BaseHullMod {

    // 定义允许的战机tag
    private static final String ALLOWED_TAG = "auto_fighter";

    // 错误提示音效
    private static final String REMOVAL_SOUND = "cr_allied_critical";

    // 描述文本
    private static final String RESTRICTION_DESC = "Only automatic control fighters can be installed";

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getVentRateMult().modifyPercent(id, 25f);
        stats.getOverloadTimeMod().modifyMult(id , 0.75f);
        stats.getSuppliesPerMonth().modifyPercent(id, 25f);
        stats.getAllowZeroFluxAtAnyLevel().modifyFlat(id, 1.0F);

        if(stats.getVariant().hasHullMod("converted_hangar")) {
            MagicIncompatibleHullmods.removeHullmodWithWarning(stats.getVariant(), "converted_hangar", spec.getId());
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 检查并移除不合规的战机
        boolean removedAny = false;

        // 遍历所有战机槽位
        int bayCount = (int) ship.getMutableStats().getNumFighterBays().getModifiedValue();
        for (int i = 0; i < bayCount && i < 20; i++) { // 限制最多检查20个槽位
            String wingId = ship.getVariant().getWingId(i);

            // 跳过空槽位
            if (wingId == null || wingId.isEmpty()) {
                continue;
            }

            // 检查战机是否具有"auto_fighter" tag
            try {
                com.fs.starfarer.api.loading.FighterWingSpecAPI wingSpec =
                        Global.getSettings().getFighterWingSpec(wingId);

                if (wingSpec != null && !wingSpec.hasTag(ALLOWED_TAG)) {
                    // 返还战机给玩家（如果玩家存在）
                    if (Global.getSector() != null && Global.getSector().getPlayerFleet() != null) {
                        Global.getSector().getPlayerFleet().getCargo().addFighters(wingId, 1);
                    }

                    // 移除该槽位的战机
                    ship.getVariant().setWingId(i, null);
                    removedAny = true;
                }
            } catch (Exception e) {
                // 如果获取战机规格失败，也移除它
                ship.getVariant().setWingId(i, null);
                removedAny = true;
            }
        }

        // 如果移除了战机，播放错误音效
        if (removedAny && Global.getSoundPlayer() != null) {
            Global.getSoundPlayer().playUISound(REMOVAL_SOUND, 0.7f, 1.0f);
        }
    }


    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize,
                                          ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 10f;
        float smallPad = 2f;

        Color highlight = Misc.getHighlightColor();
        Color badColor = Misc.getNegativeHighlightColor();

        //tooltip.addPara("作为星域中你所能见到最为精密而高效的武器，她需要一些特殊的优渥对待", pad, highlight);
        tooltip.addSectionHeading("Effect", Alignment.MID, pad);
        tooltip.addPara("Overload time reduced by %s", smallPad, highlight, "15%");
        tooltip.addPara("Active venting rate increased by %s", smallPad, highlight, "25%");
        tooltip.addPara("As long as the ship is not overloaded or actively venting, it can maintain its zero-flux boost without generating flux.", smallPad, highlight);
        // 添加限制说明
        tooltip.addPara(RESTRICTION_DESC, pad, badColor);
        tooltip.addPara("Monthly maintenance consumption increased by %s", smallPad, badColor, "15%");

        tooltip.addSectionHeading("Additional configuration", Alignment.MID, pad);
        if(!(ship == null)) {
            if (ship.getVariant().hasHullMod("Tr_SpecializedArmor")){
                TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/damper_field.png", 45f);
                imageText.addPara("The ship integrates a special set of armor:", pad);
                imageText.addPara("When the ship's armor is hit, it will bounce off projectiles that hit it at a larger angle.", pad, highlight);
                imageText.addPara("But the armor's resistance to %s will be reduced", pad, Color.red ,"Kinetic damage");
                tooltip.addImageWithText(15f);
            }
            else if (ship.getVariant().hasHullMod("Moci_IField")){
                TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/temporal_shell.png", 45f);
                imageText.addPara("The ship has Class 1 beam protection:", pad);
                imageText.addPara("The ship is able to block the incidence of frontal beams", pad, highlight);
                imageText.addPara("However, blocking beams generates %s, and the system is disabled when %s.", pad, Color.red ,"soft flux","overloaded");
                tooltip.addImageWithText(15f);
            }
            else if (ship.getVariant().hasHullMod("Tr6_ControllingCore")){
                TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/station_defense_drones_mid.png", 45f);
                imageText.addPara("The ship's control core is a TR6 Woundwort:", pad);
                imageText.addPara("When the ship is destroyed, Woundwort will disengage and start fighting autonomously.", pad, highlight);
                //imageText.addPara("但阻挡光束会产生 %s ，且舰船  %s 时失效", pad, Color.red ,"软辐能","过载");
                tooltip.addImageWithText(15f);
            }
            else if (ship.getVariant().hasHullMod("Tr6_Haznthley_system")){
                TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/station_defense_drones_mid.png", 45f);
                imageText.addPara("This mobile armor is expanded based on the TR6 Woundwort:", pad);
                imageText.addPara("Can cope with different combat environments by changing equipment", pad, highlight);
                //imageText.addPara("但阻挡光束会产生 %s ，且舰船  %s 时失效", pad, Color.red ,"软辐能","过载");
                tooltip.addImageWithText(15f);
            }
            else{
                tooltip.addPara("No special modifications", smallPad, highlight);
            }
        }

        // 如果当前有安装战机，显示合规性检查
        if (ship != null && ship.getVariant() != null) {
            ShipVariantAPI variant = ship.getVariant();

            // 检查现有战机合规性
            ArrayList<String> nonCompliantWings = new ArrayList<>();
            int totalWings = 0;

            for (String wingId : variant.getFittedWings()) {
                totalWings++;
                try {
                    com.fs.starfarer.api.loading.FighterWingSpecAPI wingSpec =
                            Global.getSettings().getFighterWingSpec(wingId);
                    if (wingSpec != null && wingSpec.hasTag(ALLOWED_TAG)) {
                    } else {
                        nonCompliantWings.add(wingSpec != null ? wingSpec.getWingName() : wingId);
                    }
                } catch (Exception e) {
                    nonCompliantWings.add(wingId);
                }
            }

            // 显示合规状态
            if (totalWings > 0) {
                if (nonCompliantWings.isEmpty()) {
                    tooltip.addPara("All currently installed fighters meet the requirements", pad, Misc.getPositiveHighlightColor());
                } else {
                    tooltip.addPara("The following fighters do not meet the requirements and will be removed:", pad, badColor);

                    tooltip.setBulletedListMode("    - ");
                    for (String wingName : nonCompliantWings) {
                        tooltip.addPara(wingName, smallPad);
                    }
                    tooltip.setBulletedListMode(null);
                }
            } else {
                tooltip.addPara("No fighter installed", pad);
            }
        }
    }

    // 可选：如果需要影响OP计算，可以添加此方法
    @Override
    public boolean affectsOPCosts() {
        return false;
    }

    // 可选：添加是否与其他船体插件兼容的检查
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return true; // 可以根据需要添加更多检查
    }
}