package data.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.utils.RSUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SPAutomated extends BaseHullMod {

    public static final String MEMKEY_ASAI = "$rs_regardAsAI";
    public static final String MEMKEY_ASAICUSTOM = "$rs_aiCompatibleForOfficer";
    private static final float SUPPLY_USE_MULT = 1.5f;

    public SPAutomated() {
    }


    // 在舰船创建前应用的修改效果
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getSuppliesPerMonth().modifyMult(id, SUPPLY_USE_MULT);


        if (stats.getVariant().hasHullMod("ControllingSystem_auto")) {
            // 协同插件存在时的属性调整
            stats.getFluxCapacity().modifyMult(id, 1.1F);
            stats.getEnergyWeaponRangeBonus().modifyMult(id, 1.1F);
            stats.getBallisticWeaponRangeBonus().modifyMult(id, 1.1F);
            stats.getPeakCRDuration().modifyMult(id, 1.2F);
            stats.getMaxSpeed().modifyMult(id , 1.1F);
        } else {
        }
    }


    // 在舰船创建后应用的修改效果
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 确保克隆舰船变体（用于保存原始配置）
        if (RSUtil.isInPlayerFleet(ship)) {
            RSUtil.ensureCloneVariant(ship.getFleetMember(), false);
        }

        // 自动/手动模式切换逻辑
        boolean force_auto = false;
        boolean force_manned = false;

        // 根据舰长类型判断模式
        if (ship.getCaptain() != null &&
                !ship.getCaptain().getMemoryWithoutUpdate().getBoolean(MEMKEY_ASAI) &&
                !ship.getCaptain().getMemoryWithoutUpdate().getBoolean(MEMKEY_ASAICUSTOM)) {

            if (ship.getCaptain().isAICore()) {  // 如果是AI核心舰长
                force_auto = true;
            } else if (!ship.getCaptain().isDefault()) {  // 如果是自定义舰长
                force_manned = true;
            }
        }

        // 强制切换模式处理
        if ((force_auto || force_manned) &&
                Global.getCurrentState() == GameState.CAMPAIGN &&
                Global.getSector() != null &&
                !Global.getSector().isPaused()) {

            if (force_auto) {  // 强制自动模式
                ship.getVariant().addMod("ControllingSystem_auto");
                ship.getVariant().removeMod("ControllingSystem_manned");
                ship.getVariant().addPermaMod("automated", false);  // 添加永久自动化插件
            } else {  // 强制手动模式
                ship.getVariant().addMod("ControllingSystem_manned");
                ship.getVariant().removeMod("ControllingSystem_auto");
                ship.getVariant().removePermaMod("automated");  // 移除自动化插件
            }
        }
        // 默认模式处理
        else if (!ship.getVariant().hasHullMod("ControllingSystem_auto") &&
                !ship.getVariant().hasHullMod("ControllingSystem_manned")) {

            if (ship.getVariant().hasHullMod("automated")) {  // 已有自动化插件
                ship.getVariant().addMod("ControllingSystem_manned");
                ship.getVariant().removePermaMod("automated");
            } else {  // 无自动化插件
                ship.getVariant().addMod("ControllingSystem_auto");
                ship.getVariant().addPermaMod("automated", false);
            }
            RSUtil.refreshRefitUI();  // 刷新改装界面
        }
        // 同步自动化状态
        else if (ship.getVariant().hasHullMod("ControllingSystem_auto")) {
            if (!ship.getVariant().hasHullMod("automated")) {
                ship.getVariant().addPermaMod("automated", false);
                RSUtil.refreshRefitUI();
            }
        } else if (ship.getVariant().hasHullMod("automated")) {
            ship.getVariant().removePermaMod("automated");
            RSUtil.refreshRefitUI();
        }

        // 自动模式下的舰载机处理
        if (RSUtil.isInPlayerFleet(ship) &&
                ship.getVariant().hasHullMod("automated")) {

            List<String> needRemove = new ArrayList<>();
            // 检查所有舰载机
            for(int i = 0; i < ship.getVariant().getWings().size(); ++i) {
                if (ship.getVariant().getWing(i) != null &&
                        !ship.getVariant().getWing(i).hasTag("auto_fighter")) {
                    needRemove.add(ship.getVariant().getWingId(i));
                }
            }
            // 移除非自动舰载机并退回给玩家
            for(String wid : needRemove) {
                boolean done = ship.getVariant().getWings().remove(wid);
                if (done && Global.getSector() != null &&
                        Global.getSector().getPlayerFleet() != null &&
                        Global.getSector().getPlayerFleet().getCargo() != null) {
                    Global.getSector().getPlayerFleet().getCargo().addFighters(wid, 1);
                }
            }
        }
    }




    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "" + 50 + "%";
        return null;
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara("曾经有探险家在英仙座边缘星区发现过特殊的、遭受过严重损害的自动舰船工厂，其生产的大型舰船都带有这一特殊设计系统。令人惊奇的是，控制该工厂的ai核心已经严重损坏，大半的储存机构已然不翼而飞，但他仍在一丝不苟地按照已经遵守了几个世纪的计划进行着舰船建造。就像他依旧在静静地为了那个宏伟的计划而奉献一样。", opad, h);
    }

}
