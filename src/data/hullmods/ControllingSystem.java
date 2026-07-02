package data.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;

public class ControllingSystem extends BaseHullMod {

    public ControllingSystem (){

    }


    // 获取无法安装时的提示信息
    public String getCanNotBeInstalledNowReason(ShipAPI ship, MarketAPI marketOrNull, CampaignUIAPI.CoreUITradeMode mode) {
        if (ship != null
                && ship.getCaptain() != null
                && !ship.getCaptain().isDefault()
                && !ship.getCaptain().getMemoryWithoutUpdate().getBoolean("$rs_regardAsAI")
                && !ship.getCaptain().getMemoryWithoutUpdate().getBoolean("$rs_aiCompatibleForOfficer")) {
            return "The officer or AI core must first be unassigned"; // 军官相关的提示
        } else {
            // 舰队组成不满足要求的提示
            return !hasEnoughNoAIShip(ship) ? "At least one manned ship is required in the fleet" : super.getCanNotBeInstalledNowReason(ship, marketOrNull, mode);
        }
    }

    // 控制模组在列表中的显示顺序（较大数值靠后）
    public int getDisplaySortOrder() {
        return 2500;
    }

    // 指定模组显示在"自动化"分类中（分类索引对应游戏内预设）
    public int getDisplayCategoryIndex() {
        return 3; // 通常3代表自动化分类
    }

    // 静态方法：检查舰队是否满足最低载人舰船要求
    public static boolean hasEnoughNoAIShip(ShipAPI ship) {
        // 在标题界面或未进入战役时直接返回true
        if (Global.getCurrentState() != GameState.TITLE && Global.getSector() != null) {
            // 如果当前舰船本身就是自动化的，直接通过检查
            if (Misc.isAutomated(ship)) {
                return true;
            } else if (Global.getSector().getPlayerFleet() != null
                    && Global.getSector().getPlayerFleet().getFleetData() != null) {
                int num = 0; // 统计有效舰船计数器

                // 遍历舰队所有成员
                for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                    // 排除战斗机中队/封存舰船/自动化舰船
                    if (!member.isFighterWing()
                            && !member.isMothballed()
                            && !Misc.isAutomated(member)) {
                        ++num;
                        if (num >= 2) { // 发现至少2艘有效舰船立即返回
                            return true;
                        }
                    }
                }
                return false; // 循环结束仍未达标
            } else {
                return false; // 无有效舰队数据
            }
        } else {
            return true; // 非游戏进行状态直接通过
        }
    }
}