package data.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static java.awt.Color.green;
import static java.awt.Color.red;

public class CelestiLockSystem extends BaseHullMod {

    public static float REPLACEMENT_RATE_THRESHOLD = 0.3f; // 触发补充速率重置的阈值（30%）
    public static float REPLACEMENT_RATE_RESET = 0.7f;     // 重置后的补充速率（70%）

    private final Color HL = Global.getSettings().getColor("hColor"); // 高亮颜色（UI用）
    private final String ID = "CelestiLockSystem", TAG1 = "fighter", TAG2 = "interceptor"; // 当前的舰载机标签

    public String getSModDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) {
            return Math.round(REPLACEMENT_RATE_THRESHOLD * 100f) + "%";
        } else if (index == 1) {
            return Math.round(REPLACEMENT_RATE_RESET * 100f) + "%";
        }
        return null;
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        float pad = 2f;

        //title
        tooltip.addSectionHeading("Effect", Alignment.MID, opad);
        if(ship != null && ship.getVariant().hasHullMod("CR_carrier_strikecommand")){
            tooltip.addPara("Airspace control system adjusted to assault mode", opad, green);
            tooltip.addPara("Increase carrier-based aircraft replenishment rate: %s", pad, green, Misc.getRoundedValue(10.0F) + "%");
            tooltip.addPara("Increase carrier-based aircraft combat radius: %s", pad, green, Misc.getRoundedValue(90.0F) + "%");
            tooltip.addPara("Increase carrier-based aircraft mobility: %s", pad, green, Misc.getRoundedValue(10.0F) + "%");
            tooltip.addPara("Increase carrier-based aircraft weapon damage: %s", pad, green, Misc.getRoundedValue(10.0F) + "%");
            tooltip.addPara("Increase pilot losses: %s", pad, red, Misc.getRoundedValue(10.0F) + "%");
            tooltip.addPara("Increase the damage taken by fighter planes: %s", pad, red, Misc.getRoundedValue(10.0F) + "%");
            tooltip.addPara("Reduce carrier aircraft weapon range: %s", pad, red, Misc.getRoundedValue(10.0F) + "%");

        }
        if(ship != null && ship.getVariant().hasHullMod("CR_carrier_supportcommand")){
            tooltip.addPara("Airspace control system adjusted to support mode", opad, green);
            tooltip.addPara("Increase carrier-based aircraft replenishment rate: %s", pad, green, Misc.getRoundedValue(20.0F) + "%");
            tooltip.addPara("Reduce pilot losses: %s", pad, green, Misc.getRoundedValue(10.0F) + "%");
            tooltip.addPara("Increase carrier aircraft weapon range: %s", pad, green, Misc.getRoundedValue(75.0F) + "%");
            tooltip.addPara("Reduce damage received by carrier-based aircraft: %s", pad, green, Misc.getRoundedValue(20.0F) + "%");
            tooltip.addPara("Reduce carrier-based aircraft combat radius: %s", pad, red, Misc.getRoundedValue(90.0F) + "%");
        }

        if (ship != null && ship.getVariant() != null) {
            if (ship.getVariant().getFittedWings().isEmpty()) {
                //no wing fitted
                tooltip.addPara(
                        "No fighter installed"
                        , 10
                        , HL
                );
            } else if (noFightersAndIntercptors(ship.getVariant())) {
                //no wanzer wings installed
                tooltip.addPara(
                        "Not adapted to install fighter aircraft"
                        , 10
                        , HL
                );
            } else {
                //effect applied
                java.util.List<String> FightersAndIntercptors = allFightersAndIntercptors(ship.getVariant());

                if (!FightersAndIntercptors.isEmpty()) {
                    tooltip.addPara(
                            "The following fighter wings receive an additional carrier aircraft:"
                            , 10
                            , HL
                            , "One additional carrier-based aircraft"
                    );

                    tooltip.setBulletedListMode("    - ");

                    for (String w : FightersAndIntercptors) {
                        tooltip.addPara(
                                w
                                , 3
                        );
                    }

                    tooltip.setBulletedListMode(null);

                    // 添加debuff说明
                    tooltip.addPara("Maintenance rate adjustment:", 10, HL, "Maintenance rate adjustment");
                    tooltip.setBulletedListMode("    - ");

                    // 计算各类型联队的debuff
                    for (String wingId : ship.getVariant().getFittedWings()) {
                        FighterWingSpecAPI wingSpec = Global.getSettings().getFighterWingSpec(wingId);
                        if (wingSpec.hasTag(TAG1) || wingSpec.hasTag(TAG2)) {
                            int numFighters = wingSpec.getNumFighters();
                            String debuffText = getDebuffTextForWingSize(numFighters);
                            tooltip.addPara(wingSpec.getWingName() + ": " + debuffText, 3);
                        }
                    }

                    tooltip.setBulletedListMode(null);
                }
            }

            tooltip.addSectionHeading("rear deck", Alignment.MID, opad);
            tooltip.addPara("After the ship's readiness rate falls below %s, the shipboard nanofactory will perform an overload and immediately restore the formation and adjust the ship's readiness rate to %s." , pad, red, Misc.getRoundedValue(30.0F) + "%" ,Misc.getRoundedValue(70.0F) + "%");

        }
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        ship.addListener(new BackupListener(ship));

        if (ship.getOriginalOwner() == -1) {
            return; //suppress in refit
        }

        boolean allDeployed = true, ranOnce;

        for (FighterLaunchBayAPI bay : ship.getLaunchBaysCopy()) {
            if (bay.getWing() != null) {
                ranOnce = true;
                if (bay.getWing().getSpec().hasTag(TAG1) || bay.getWing().getSpec().hasTag(TAG2)) {

                    FighterWingSpecAPI wingSpec = bay.getWing().getSpec();
                    int deployed = bay.getWing().getWingMembers().size();
                    int baseNum = wingSpec.getNumFighters();

                    // 根据联队飞机数量决定是否增加额外舰载机和debuff
                    int maxTotal = baseNum;
                    if (baseNum != 6) { // 6机联队不额外增加舰载机
                        maxTotal = baseNum + 1;
                    }

                    int actualAdd = maxTotal - deployed;

                    if (actualAdd > 0) {
                        bay.setExtraDeployments(actualAdd);
                        bay.setExtraDeploymentLimit(maxTotal);
                        bay.setExtraDuration(9999999);
                        allDeployed = false;
                    } else {
                        bay.setExtraDeployments(0);
                        bay.setExtraDeploymentLimit(0);
                        bay.setFastReplacements(0);
                    }

                    if (ship.getMutableStats().getFighterRefitTimeMult().getPercentStatMod(ID) == null && actualAdd != 0) {
                        //instantly add all the required fighters upon deployment
                        bay.setFastReplacements(actualAdd);
                    }

                    // 当所有舰载机部署完毕时标记状态
                    if (allDeployed && ranOnce) {
                        ship.getMutableStats().getFighterRefitTimeMult().modifyPercent(ID, 20f);
                    }
                }
            }
        }
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        //reset the "check" mutable stat so that it is applied next deployment
        stats.getFighterRefitTimeMult().unmodify(ID);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        //the extra crafts do not deplete the fighter replacement gauge when destroyed, this makes the rate deplete faster from those that do to compensate.
        int crafts = 0, extraCrafts = 0;
        float debuffMultiplier = 1.0f;

        for (String w : ship.getVariant().getFittedWings()) {
            FighterWingSpecAPI wingSpec = Global.getSettings().getFighterWingSpec(w);
            if (wingSpec.hasTag(TAG1) || wingSpec.hasTag(TAG2)) {
                int numFighters = wingSpec.getNumFighters();
                crafts += numFighters;

                // 应用基于联队规模的debuff
                float wingDebuff = getDebuffMultiplierForWingSize(numFighters);
                if (wingDebuff > 1.0f) {
                    debuffMultiplier *= wingDebuff;
                }

                // 只有非6机联队才增加额外舰载机
                if (numFighters != 6) {
                    extraCrafts++;
                }
            }
        }

        if (extraCrafts > 0) {
            ship.getMutableStats().getDynamic().getMod(Stats.REPLACEMENT_RATE_DECREASE_MULT).modifyMult(id, (float) (crafts + extraCrafts) / crafts);
        }

        // 应用整备下降速率debuff
        if (debuffMultiplier > 1.0f) {
            for (String w : ship.getVariant().getFittedWings()) {

                ship.getMutableStats().getFighterRefitTimeMult().modifyMult(id, debuffMultiplier);
            }
        }
    }

    // 根据联队飞机数量获取debuff乘数
    private float getDebuffMultiplierForWingSize(int numFighters) {
        return switch (numFighters) {
            case 1 -> 1.20f; // 1机联队：整备下降速率提升25%
            case 2 -> 1.1f; // 2机联队：整备下降速率提升10%
            default -> 1.0f;  // 3机及以上：无debuff
        };
    }

    // 根据联队飞机数量获取debuff描述文本
    private String getDebuffTextForWingSize(int numFighters) {
        return switch (numFighters) {
            case 1 -> "Maintenance descent rate increased by 20%";
            case 2 -> "Maintenance descent rate increased by 10%";
            case 6 -> "No additional carrier-based aircraft, no debuffs";
            default -> "No debuff";
        };
    }

    // 判断舰船是否无适配舰载机
    private boolean noFightersAndIntercptors(ShipVariantAPI variant) {
        for (String w : variant.getFittedWings()) {
            if (Global.getSettings().getFighterWingSpec(w).hasTag(TAG1) || Global.getSettings().getFighterWingSpec(w).hasTag(TAG2))
                return false;
        }
        return true;
    }

    // 获取所有适配舰载机名称
    private List<String> allFightersAndIntercptors(ShipVariantAPI variant) {
        List<String> allFightersAndIntercptors = new ArrayList<>();
        for (String w : variant.getFittedWings()) {
            FighterWingSpecAPI f = Global.getSettings().getFighterWingSpec(w);
            if (f.hasTag(TAG1) || f.hasTag(TAG2)) {
                allFightersAndIntercptors.add(f.getWingName() + " " + f.getRoleDesc());
            }
        }
        return allFightersAndIntercptors;
    }

    public static class BackupListener implements AdvanceableListener {
        protected ShipAPI ship;
        protected boolean fired = false;

        public BackupListener(ShipAPI ship) {
            this.ship = ship;
        }

        public void advance(float amount) {
            float cr = ship.getCurrentCR();

            if (!fired && cr >= 0) {
                if (ship.getSharedFighterReplacementRate() <= REPLACEMENT_RATE_THRESHOLD) {
                    fired = true;

                    for (FighterLaunchBayAPI bay : ship.getLaunchBaysCopy()) {
                        if (bay.getWing() == null) continue;

                        float rate = REPLACEMENT_RATE_RESET;
                        bay.setCurrRate(rate);

                        bay.makeCurrentIntervalFast();
                        FighterWingSpecAPI spec = bay.getWing().getSpec();

                        int baseNum = spec.getNumFighters();
                        int maxTotal = baseNum;
                        // 6机联队不额外增加舰载机
                        if (baseNum != 6) {
                            maxTotal = baseNum + 1;
                        }
                        int actualAdd = maxTotal - bay.getWing().getWingMembers().size();
                        if (actualAdd > 0) {
                            bay.setFastReplacements(bay.getFastReplacements() + actualAdd);
                        }
                    }
                }
            }

            if (Global.getCurrentState() == GameState.COMBAT &&
                    Global.getCombatEngine() != null && Global.getCombatEngine().getPlayerShip() == ship) {

                String status = "BackUpDeck";
                boolean penalty = false;
                if (fired) status = "ready";
                Global.getCombatEngine().maintainStatusForPlayerShip("cr_bdeck",
                        Global.getSettings().getSpriteName("ui", "backup_deck"),
                        "rear deck", status, penalty);
            }
        }
    }
}