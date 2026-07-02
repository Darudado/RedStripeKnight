package data.scripts.weapons;

import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.MissileRenderDataAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

public class Moci_MechProjDecoWeaponEffect_followControl_RS implements EveryFrameWeaponEffectPlugin {
    boolean init = false;
    WeaponAPI source;
    WeaponAPI sourceMAIN;
    WeaponAPI typeWeapon; // TYPE武器引用
    WeaponAPI sourceFollow; // FOLLOW武器引用
    WeaponAPI rightHandWeapon; // 右手武器引用
    private static final float FOLLOW_RATIO = 0.33f; // 跟随比例参数
    boolean wasFiring = false;
    private boolean isAnimating = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused()) {
            return;
        }

        // 初始化
        if (!init) {
            init = true;
            ShipAPI ship = weapon.getShip();
            String slotId = weapon.getSlot().getId();
            // 通过武器槽添加后缀寻找关联武器
            String sourceId = slotId + "_SOURCE";
            String mainId = slotId + "_MAIN"; // 主炮槽位ID
            String followId = slotId + "_FOLLOW"; // FOLLOW槽位ID
            String rightHandId = slotId + "_RIGHT"; // 右手武器槽位ID
            String typeId = slotId + "_TYPE"; // TYPE槽位ID

            for (WeaponAPI w : ship.getAllWeapons()) {
                if (sourceId.equals(w.getSlot().getId())) {
                    source = w;
                }
                // 如果_SOURCE槽位不存在，检查_MAIN槽位
                if (source == null && mainId.equals(w.getSlot().getId())) {
                    sourceMAIN = w;
                }
                // 查找FOLLOW武器
                if (followId.equals(w.getSlot().getId())) {
                    sourceFollow = w;
                }
                // 查找右手武器
                if (rightHandId.equals(w.getSlot().getId())) {
                    rightHandWeapon = w;
                }
                // 查找TYPE武器
                if (typeId.equals(w.getSlot().getId())) {
                    typeWeapon = w;
                }
            }

            // 关联武器槽安装了武器的情况下
            if (source != null && typeWeapon != null) {
                // 计算TYPE武器和SOURCE武器之间的坐标差
                Vector2f diff = new Vector2f();
                Vector2f.sub(typeWeapon.getSlot().getLocation(), source.getSlot().getLocation(), diff);
                // 取TYPE武器的炮塔模式第一个开火点坐标
                Vector2f firePoint = typeWeapon.getSpec().getTurretFireOffsets().get(0);
                syncFirePoint(source, diff, firePoint);
                // 设置SOURCE武器为透明
                hideSourceWeapon();
            }
        }

        // 持续隐藏SOURCE武器
        hideSourceWeapon();

        // 检查是否选中了FOLLOW武器组
        boolean followWeaponSelected = sourceFollow != null &&
                sourceFollow.getShip().getSelectedGroupAPI() != null &&
                sourceFollow.getShip().getSelectedGroupAPI().getWeaponsCopy().contains(sourceFollow);

        // 检查是否选中了SOURCE武器组
        boolean sourceWeaponSelected = source != null &&
                source.getShip().getSelectedGroupAPI() != null &&
                source.getShip().getSelectedGroupAPI().getWeaponsCopy().contains(source);

        // 如果选中了FOLLOW武器组，让所有武器跟随FOLLOW武器
        if (followWeaponSelected) {
            // 装饰武器跟随FOLLOW武器
            weapon.setCurrAngle(sourceFollow.getCurrAngle());

            // SOURCE武器跟随FOLLOW武器
            if (source != null) {
                source.setCurrAngle(sourceFollow.getCurrAngle());
            }

            // TYPE武器跟随FOLLOW武器
            if (typeWeapon != null) {
                typeWeapon.setCurrAngle(sourceFollow.getCurrAngle());
            }

            // 右手武器跟随FOLLOW武器
            if (rightHandWeapon != null) {
                rightHandWeapon.setCurrAngle(sourceFollow.getCurrAngle());
            }
        }
        // 如果选中了SOURCE武器组，让所有武器跟随SOURCE武器
        else if (sourceWeaponSelected && source != null) {
            // 装饰武器跟随SOURCE武器
            weapon.setCurrAngle(source.getCurrAngle());

            // FOLLOW武器跟随SOURCE武器
            if (sourceFollow != null) {
                sourceFollow.setCurrAngle(source.getCurrAngle());
            }

            // TYPE武器跟随SOURCE武器
            if (typeWeapon != null) {
                typeWeapon.setCurrAngle(source.getCurrAngle());
            }

            // 右手武器跟随SOURCE武器
            if (rightHandWeapon != null) {
                rightHandWeapon.setCurrAngle(source.getCurrAngle());
            }

            // 动画同步控制
            if (weapon.getAnimation() != null) {
                boolean isFiring = source.isFiring() &&
                        (source.getCooldownRemaining() == 0 ||
                                (source.getSpec().getBurstSize() > 1 && source.isInBurst()));

                if (isFiring) {
                    // 重置动画到第一帧
                    if (!wasFiring) {
                        weapon.getAnimation().setFrame(0);
                    }
                    weapon.getAnimation().play();
                    wasFiring = true;
                    isAnimating = true;
                } else if (wasFiring) {
                    // 检查是否到达最后一帧
                    if (weapon.getAnimation().getFrame() >= weapon.getAnimation().getNumFrames() - 1) {
                        weapon.getAnimation().pause();
                        weapon.getAnimation().setFrame(0); // 播放完成后回到第0帧
                        wasFiring = false;
                        isAnimating = false;
                    } else if (isAnimating) {
                        // 继续播放直到完成
                        weapon.getAnimation().play();
                    }
                } else {
                    weapon.getAnimation().pause();
                    isAnimating = false;
                }
            }
        }
        // 如果都没有选中，执行默认的同步逻辑
        else {
            // 如果有SOURCE武器，装饰武器跟随SOURCE武器
            if (source != null) {
                weapon.setCurrAngle(source.getCurrAngle());

                // 同步FOLLOW武器角度
                if (sourceFollow != null) {
                    sourceFollow.setCurrAngle(source.getCurrAngle());
                }

                // 同步TYPE武器角度
                if (typeWeapon != null) {
                    typeWeapon.setCurrAngle(source.getCurrAngle());
                }

                // 同步右手武器角度
                if (rightHandWeapon != null) {
                    rightHandWeapon.setCurrAngle(source.getCurrAngle());
                }

                // 动画同步控制
                if (weapon.getAnimation() != null) {
                    boolean isFiring = source.isFiring() &&
                            (source.getCooldownRemaining() == 0 ||
                                    (source.getSpec().getBurstSize() > 1 && source.isInBurst()));

                    if (isFiring) {
                        // 重置动画到第一帧
                        if (!wasFiring) {
                            weapon.getAnimation().setFrame(0);
                        }
                        weapon.getAnimation().play();
                        wasFiring = true;
                        isAnimating = true;
                    } else if (wasFiring) {
                        // 检查是否到达最后一帧
                        if (weapon.getAnimation().getFrame() >= weapon.getAnimation().getNumFrames() - 1) {
                            weapon.getAnimation().pause();
                            weapon.getAnimation().setFrame(0); // 播放完成后回到第0帧
                            wasFiring = false;
                            isAnimating = false;
                        } else if (isAnimating) {
                            // 继续播放直到完成
                            weapon.getAnimation().play();
                        }
                    } else {
                        weapon.getAnimation().pause();
                        isAnimating = false;
                    }
                }
            }

            // 如果SOURCE不存在但MAIN存在，执行比例跟随
            if (source == null && sourceMAIN != null) {
                // 计算武器相对于飞船的角度
                float shipFacing = weapon.getShip().getFacing();
                float sourceAngle = sourceMAIN.getCurrAngle() - shipFacing;

                // 规范化角度到[-180,180]范围
                while (sourceAngle > 180)
                    sourceAngle -= 360;
                while (sourceAngle < -180)
                    sourceAngle += 360;

                // 应用跟随比例并加回飞船朝向
                float targetAngle = shipFacing + sourceAngle * FOLLOW_RATIO;
                weapon.setCurrAngle(targetAngle);
            }

            // 新增：寻找不到SOURCE武器时，令TYPE武器跟随FOLLOW武器
            if (source == null && typeWeapon != null && sourceFollow != null) {
                typeWeapon.setCurrAngle(sourceFollow.getCurrAngle());
            }
        }
    }

    /**
     * 隐藏SOURCE武器
     */
    private void hideSourceWeapon() {
        if (source == null) return;

        Color transparent = new Color(0f, 0f, 0f, 0f);
        if(source.getSprite()!=null){
            source.getSprite().setColor(transparent);
        }
        if(source.getBarrelSpriteAPI()!=null){
            source.getBarrelSpriteAPI().setColor(transparent);
        }
        if(source.getUnderSpriteAPI()!=null){
            source.getUnderSpriteAPI().setColor(transparent);
        }
        if(source.getGlowSpriteAPI()!=null){
            source.getGlowSpriteAPI().setColor(transparent);
        }

        // 隐藏导弹渲染数据
        if (source.getMissileRenderData() != null) {
            for (MissileRenderDataAPI render : source.getMissileRenderData()) {
                render.getSprite().setWidth(0);
                render.getSprite().setHeight(0);
            }
        }
    }

    /**
     * 批量修改开火点
     *
     * @param sync     关联武器的开火点V2的list
     * @param diff     武器槽之间的坐标差值
     * @param override 装饰武器的开火点
     */
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
            }
        }
    }
}