package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class Tr_FiverWingController_R implements EveryFrameWeaponEffectPlugin {
    ShipAPI ship;
    private ShipAPI wing;
    private ShipAPI subwing;
    private ShipAPI armorwing;
    protected WeaponAPI source; // 新增：关联的_SOURCE武器= null;
    WeaponAPI sourceFollow; // 新增：FOLLOW武器引用
    private int shieldAnimationFrame = 0; // 护盾动画帧（0:无盾牌, 1:普通护盾, 2:例外护盾）

    boolean init = false;

    public static final Color color = Color.RED;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused()) {
            return;
        }
        if (!init){
            init = true;
            ship = weapon.getShip();
            String slotId = weapon.getSlot().getId();
            String wingId = "RWING_" + "R";
            String subwingId = "RSWING_" + "R";
            String armorwingId = "RAWING_" + "R";

            for (ShipAPI module_a : ship.getChildModulesCopy()) {
                if (module_a.getStationSlot() != null && module_a.getStationSlot().getId().equals(wingId)) {
                    wing = module_a;
                    // 根据船体列表确定动画帧
                    shieldAnimationFrame =  1; // 例外用第2帧，普通用第1帧
                    break;
                }
            }

            for (ShipAPI module_b : ship.getChildModulesCopy()) {
                if (module_b.getStationSlot() != null && module_b.getStationSlot().getId().equals(subwingId)) {
                    subwing = module_b;
                    break;
                }
            }

            for (ShipAPI module_c : ship.getChildModulesCopy()) {
                if (module_c.getStationSlot() != null && module_c.getStationSlot().getId().equals(armorwingId)) {
                    armorwing = module_c;
                    break;
                }
            }

            String sourceId = slotId + "_SOURCE";
            String followId = slotId + "_FOLLOW"; // 新增：FOLLOW槽位ID
            for (WeaponAPI w : ship.getAllWeapons()) {
                if (sourceId.equals(w.getSlot().getId())) {
                    source = w;
                }
                // 新增：查找FOLLOW武器
                if (followId.equals(w.getSlot().getId())) {
                    sourceFollow = w;
                }
            }

            if (source != null && sourceFollow != null) {
                initializeFollowWeapon(weapon);
            }
        }


        if (weapon.getAnimation() != null) {
            if (wing != null && wing.isAlive()) {
                weapon.getAnimation().setFrame(shieldAnimationFrame);
            } else {
                weapon.getAnimation().setFrame(0);
            }
        }

        if (sourceFollow != null && source != null) {
            sourceFollow.setCurrAngle(source.getCurrAngle());
            hideFollowWeapon();
        }

        if (source != null) {
            // 计算武器相对于飞船的角度
            float shipFacing = weapon.getShip().getFacing();
            float sourceAngle = source.getCurrAngle() - shipFacing;

            // 规范化角度到[-180,180]范围
            while (sourceAngle > 180) sourceAngle -= 360;
            while (sourceAngle < -180) sourceAngle += 360;

            // 应用跟随比例并加回飞船朝向
            float targetAngle = shipFacing + sourceAngle;
            weapon.setCurrAngle(targetAngle);
        }

        if (wing != null && wing.isAlive() && source != null) {
            wing.setFacing(source.getCurrAngle());
        }

        if (subwing != null && subwing.isAlive() && source != null) {
            subwing.setFacing(source.getCurrAngle());
        }

        if (armorwing != null && armorwing.isAlive() && armorwing != null) {
            armorwing.setFacing(source.getCurrAngle());
        }


    }


    private void initializeFollowWeapon(WeaponAPI weapon) {
        if (source == null || sourceFollow == null) return;

        // FOLLOW武器同步到装饰武器的位置（与SOURCE武器保持一致）
        Vector2f diffFollow = new Vector2f();
        Vector2f.sub(weapon.getSlot().getLocation(), sourceFollow.getSlot().getLocation(), diffFollow);

        // 获取装饰武器的开火点作为FOLLOW武器的目标开火点
        Vector2f decorativeFirePoint = weapon.getSpec().getTurretFireOffsets().get(0);
        syncFirePoint(sourceFollow, diffFollow, decorativeFirePoint);

        // 初始隐藏FOLLOW武器
        hideFollowWeapon();
    }

    private void hideFollowWeapon() {
        if (sourceFollow == null) return;

        // 透明颜色，参考骤雨mod的实现
        Color transparent = new Color(0f, 0f, 0f, 0f);

        // 设置所有贴图组件为透明，包括发光贴图
        if (sourceFollow.getSprite() != null) {
            sourceFollow.getSprite().setColor(transparent);
        }
        if (sourceFollow.getBarrelSpriteAPI() != null) {
            sourceFollow.getBarrelSpriteAPI().setColor(transparent);
        }
        if (sourceFollow.getUnderSpriteAPI() != null) {
            sourceFollow.getUnderSpriteAPI().setColor(transparent);
        }
        if (sourceFollow.getGlowSpriteAPI() != null) {
            sourceFollow.getGlowSpriteAPI().setColor(transparent);
        }

        // 隐藏导弹渲染数据
        if (sourceFollow.getMissileRenderData() != null) {
            for (MissileRenderDataAPI render : sourceFollow.getMissileRenderData()) {
                render.getSprite().setWidth(0);
                render.getSprite().setHeight(0);
            }
        }
    }

    public static void syncFirePoint(WeaponAPI sync, Vector2f diff, Vector2f override) {
        if (override == null) {
            override = new Vector2f();
        }

        Vector2f.add(override, diff, diff);
        sync.ensureClonedSpec();
        int t = sync.getSpec().getTurretFireOffsets().size();
        int h = sync.getSpec().getHardpointFireOffsets().size();
        int hd = sync.getSpec().getHiddenFireOffsets().size();

        sync.getSpec().getTurretFireOffsets().clear();
        sync.getSpec().getHardpointFireOffsets().clear();
        sync.getSpec().getHiddenFireOffsets().clear();
        for (int i = 0; i < t; i++) {
            sync.getSpec().getTurretFireOffsets().add(new Vector2f(diff));
        }
        for (int i = 0; i < h; i++) {
            sync.getSpec().getHardpointFireOffsets().add(new Vector2f(diff));
        }
        for (int i = 0; i < hd; i++) {
            sync.getSpec().getHiddenFireOffsets().add(new Vector2f(diff));
        }

        if (sync.getMissileRenderData() != null) {
            for (MissileRenderDataAPI render : sync.getMissileRenderData()) {
                render.getSprite().setWidth(0);
                render.getSprite().setHeight(0);
            }
        }
    }

}