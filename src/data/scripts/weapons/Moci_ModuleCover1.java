package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * 模块护甲动画控制器 版本1
 * 根据关联装甲模块的存活状态切换武器动画帧
 * 查找规则：当前槽位ID + "_ARMOR" 后缀
 * 例如：WS0001 -> WS0001_ARMOR
 */
public class Moci_ModuleCover1 implements EveryFrameWeaponEffectPlugin {
    private boolean init = false;
    private ShipAPI armorModule = null;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused()) {
            return;
        }

        // 初始化：查找关联的装甲模块
        if (!init) {
            init = true;
            ShipAPI ship = weapon.getShip();
            
            // 获取当前武器的槽位ID
            String currentSlotId = weapon.getSlot() != null ? weapon.getSlot().getId() : null;
            if (currentSlotId == null) {
                return;
            }
            
            // 构造对应的装甲模块槽位ID（添加_ARMOR后缀）
            String armorSlotId = currentSlotId + "_ARMOR";
            
            // 查找对应的装甲模块
            for (ShipAPI module : ship.getChildModulesCopy()) {
                if (module.getStationSlot() != null && 
                    module.getStationSlot().getId().equals(armorSlotId)) {
                    armorModule = module;
                    break;
                }
            }
        }

        // 控制动画帧
        if (weapon.getAnimation() != null) {
            if (armorModule != null && armorModule.isAlive()) {
                // 装甲模块存活：显示完整状态（第0帧）
                weapon.getAnimation().setFrame(0);
            } else {
                // 装甲模块损坏或不存在：显示损坏状态（第1帧）
                weapon.getAnimation().setFrame(1);
            }
        }
    }
}
