package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * 模块护甲动画控制器 版本2
 * 根据关联装甲模块的存活状态切换武器动画帧
 * 查找规则：当前槽位ID 删除 "_COVER" 后缀
 * 例如：BACKPACK_R_COVER -> BACKPACK_R
 *       BACKPACK_L_COVER -> BACKPACK_L
 *       WS0001_COVER -> WS0001
 */
public class Moci_ModuleCover2 implements EveryFrameWeaponEffectPlugin {
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
            
            // 删除 _COVER 后缀，得到对应的装甲模块槽位ID
            String armorSlotId = currentSlotId;
            if (currentSlotId.endsWith("_COVER")) {
                armorSlotId = currentSlotId.substring(0, currentSlotId.length() - "_COVER".length());
            }
            
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
