package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * Moci_RepairBayHullModEffect - 修理舱船体模组效果
 * 
 * 继承BaseHullMod，为舰船提供修理舱功能的船体模组
 * 
 * 核心功能：
 * - 在舰船创建后自动初始化修理舱系统
 * - 为装备该模组的舰船添加Moci_RepairBayScript监听器
 * - 使舰船具备为其他舰船提供修理服务的能力
 * 
 * 安装要求：
 * - 舰船必须拥有非内置的飞行甲板
 * - 舰体级别必须大于等于驱逐舰（不能是护卫舰）
 * - 需要配合带有"Moci_RepairBay"标签的武器使用
 * 
 * 技术特点：
 * - 简单而高效的模组结构
 * - 自动化的系统初始化流程
 * - 与修理舱核心系统无缝集成
 */
public class Moci_MaRepairBayHullModEffect extends BaseHullMod {

    /**
     * 在舰船创建后应用效果
     * 为舰船添加修理舱管理脚本监听器
     * 
     * @param ship 要应用效果的舰船
     * @param id 船体模组的唯一标识符
     */
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 为舰船添加修理舱脚本监听器
        // 该监听器将自动扫描舰船的修理舱武器并初始化修理系统
        //ship.addListener(new Moci_MaRepairBayScript(ship));
    }

    /**
     * 检查该船体模组是否适用于指定舰船
     * 判断标准：
     * - 舰体级别必须大于等于驱逐舰
     * - 飞行甲板数量 > 内置战机数量，表示有非内置的飞行甲板
     */
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null || ship.getHullSpec() == null) {
            return false;
        }
        
        // 检查舰体级别：必须是巡洋舰及以上
        ShipAPI.HullSize hullSize = ship.getHullSpec().getHullSize();
        if (hullSize == ShipAPI.HullSize.FRIGATE || hullSize == ShipAPI.HullSize.FIGHTER || hullSize == ShipAPI.HullSize.DESTROYER) {
            return false;
        }
        
        // 检查是否有飞行甲板
        if (ship.getHullSpec().getFighterBays() <= 0) {
            return false;
        }
        
        // 关键逻辑：比较飞行甲板数量和内置战机数量
        int totalFighterBays = ship.getHullSpec().getFighterBays();  // 总飞行甲板数
        int builtInWings = ship.getHullSpec().getBuiltInWings().size();  // 内置战机中队数
        
        // 如果飞行甲板数量大于内置战机数量，说明有非内置的飞行甲板
        return totalFighterBays > builtInWings;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) {
            return "舰船为空";
        }
        
        if (ship.getHullSpec() == null) {
            return "舰船规格为空";
        }
        
        // 检查舰体级别
        ShipAPI.HullSize hullSize = ship.getHullSpec().getHullSize();
        if (hullSize == ShipAPI.HullSize.FRIGATE || hullSize == ShipAPI.HullSize.FIGHTER) {
            return "舰体级别必须为巡洋舰及以上";
        }
        
        // 检查飞行甲板
        if (ship.getHullSpec().getFighterBays() <= 0) {
            return "舰船必须拥有飞行甲板";
        }
        
        // 检查非内置甲板
        int totalFighterBays = ship.getHullSpec().getFighterBays();
        int builtInWings = ship.getHullSpec().getBuiltInWings().size();
        if (totalFighterBays <= builtInWings) {
            return "舰船必须拥有非内置的飞行甲板";
        }
        
        return null;
    }

    @Override
    public boolean showInRefitScreenModPickerFor(ShipAPI ship) {
        return isApplicableToShip(ship);
    }
}
