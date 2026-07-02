package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

public class Moci_MechProjDecoWeaponEffect_Haznthley implements EveryFrameWeaponEffectPlugin {
    boolean init = false;
    WeaponAPI source;

    public Moci_MechProjDecoWeaponEffect_Haznthley() {
    }

    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused()) {
            return;
        }

        if (!this.init) {
            this.init = true;
            ShipAPI ship = weapon.getShip();
            String slotId = weapon.getSlot().getId();
            String sourceId = slotId + "_SOURCE";

            // 查找源武器
            for (WeaponAPI w : ship.getAllWeapons()) {
                if (sourceId.equals(w.getSlot().getId())) {
                    this.source = w;
                    break;
                }
            }

            if (this.source != null) {
                // 计算槽位位置差
                Vector2f diff = new Vector2f();
                Vector2f.sub(weapon.getSlot().getLocation(), this.source.getSlot().getLocation(), diff);

                // 使用Moci_HiddenWeapon的同步开火点逻辑
                syncFirePoint(this.source, diff, weapon);

                // 隐藏源武器
                this.hideSourceWeapon();

                // 初始化动画状态（保持原有逻辑）
                if (weapon.getAnimation() != null) {
                    weapon.getAnimation().pause();
                }
            }
        }

        if (this.source != null && weapon.getAnimation() != null) {
            int maxFrames = weapon.getAnimation().getNumFrames();
            if (this.source.getType() != WeaponAPI.WeaponType.MISSILE &&
                    this.source.getType() != WeaponAPI.WeaponType.COMPOSITE &&
                    this.source.getType() != WeaponAPI.WeaponType.SYNERGY) {
                weapon.getAnimation().setFrame(0);
            } else if (maxFrames > 1) {
                weapon.getAnimation().setFrame(1);
            } else {
                weapon.getAnimation().setFrame(0);
            }
        }

        if (this.source != null) {
            // 同步角度
            weapon.setCurrAngle(this.source.getCurrAngle());

            // 持续隐藏源武器
            this.hideSourceWeapon();

        }
    }

    private void hideSourceWeapon() {
        if (this.source != null) {
            Color transparent = new Color(0.0F, 0.0F, 0.0F, 0.0F);

            if (this.source.getSprite() != null) {
                this.source.getSprite().setColor(transparent);
            }

            if (this.source.getBarrelSpriteAPI() != null) {
                this.source.getBarrelSpriteAPI().setColor(transparent);
            }

            if (this.source.getUnderSpriteAPI() != null) {
                this.source.getUnderSpriteAPI().setColor(transparent);
            }

            if (this.source.getGlowSpriteAPI() != null) {
                this.source.getGlowSpriteAPI().setColor(transparent);
            }

            if (this.source.getMissileRenderData() != null) {
                for (MissileRenderDataAPI render : this.source.getMissileRenderData()) {
                    render.getSprite().setWidth(0.0F);
                    render.getSprite().setHeight(0.0F);
                }
            }
        }
    }

    /**
     * 同步开火点 - 从Moci_HiddenWeapon复制过来的逻辑
     * @param sync   要同步的源武器
     * @param diff   武器槽之间的坐标差值
     * @param weapon 装饰武器（作为开火点参考）
     */
    public static void syncFirePoint(WeaponAPI sync, Vector2f diff, WeaponAPI weapon) {
        int i = Math.max(sync.getSpec().getTurretFireOffsets().size(), weapon.getSpec().getTurretFireOffsets().size());
        int i2 = Math.max(sync.getSpec().getHardpointFireOffsets().size(),
                weapon.getSpec().getHardpointFireOffsets().size());

        // 渲染导弹和炮管的武器使用原本武器的开火点数量避免报错，否则以装饰武器的开火点数量为准
        while (sync.getMissileRenderData() != null || sync.getBarrelSpriteAPI() != null) {
            i = sync.getSpec().getTurretFireOffsets().size();
            i2 = sync.getSpec().getHardpointFireOffsets().size();
            break;
        }

        // 确保武器的spec经过clone，不会直接修改所有同类武器
        sync.ensureClonedSpec();

        // 清空发射点
        sync.getSpec().getTurretFireOffsets().clear();
        sync.getSpec().getTurretAngleOffsets().clear();
        sync.getSpec().getHardpointFireOffsets().clear();
        sync.getSpec().getHardpointAngleOffsets().clear();

        // 装饰武器的开火点数量
        int decoPointNum = weapon.getSpec().getTurretFireOffsets().size();

        // 遍历，同时输入坐标和角度确保数量匹配
        for (int a = 0; a < i; a++) {
            int n = a;
            while (n >= decoPointNum) {
                n -= decoPointNum;
            }
            Vector2f p = weapon.getSpec().getTurretFireOffsets().get(n);
            Vector2f point = Vector2f.add(p, diff, null);
            float ang = weapon.getSpec().getTurretAngleOffsets().get(n);
            sync.getSpec().getTurretFireOffsets().add(point);
            sync.getSpec().getTurretAngleOffsets().add(ang);
        }

        decoPointNum = weapon.getSpec().getHardpointFireOffsets().size();
        for (int a = 0; a < i2; a++) {
            int n = a;
            while (n >= decoPointNum) {
                n -= decoPointNum;
            }
            Vector2f p = weapon.getSpec().getHardpointFireOffsets().get(n);
            Vector2f point = Vector2f.add(p, diff, null);
            float ang = weapon.getSpec().getHardpointAngleOffsets().get(n);
            sync.getSpec().getHardpointFireOffsets().add(point);
            sync.getSpec().getHardpointAngleOffsets().add(ang);
        }

        while (sync.getMissileRenderData() != null) {
            sync.getMissileRenderData().clear();
            for (MissileRenderDataAPI render : sync.getMissileRenderData()) {
                render.getSprite().setWidth(0);
                render.getSprite().setHeight(0);
                render.getMissileCenterLocation().set(0, 0);
            }
            break;
        }
    }
}