package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.Launchai;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.fs.starfarer.api.combat.CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;

public class Tr_Fiver_launch_assault implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {
    private final IntervalUtil intervalUtil = new IntervalUtil(1f, 1f);
    public static final Color color = Color.RED;
    private final Map<DamagingProjectileAPI, ProjectileState> delayedProjectiles =
            new LinkedHashMap<>(32, 0.75f, false);

    private final float rotationSpeed = 5f;
    private final float maxFrontArc = 35f;
    private final float maxBacktArc = 0f;
    private WingSide wingSide = WingSide.NONE; // 使用枚举表示机翼位置

    private ShipAPI wingModule = null; // 新增：对应的机翼模块

    // 机翼位置枚举
    private enum WingSide {
        LEFT, RIGHT, NONE
    }

    // 添加无参构造函数
    public Tr_Fiver_launch_assault() {
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused()) {
            return;
        }

        ShipAPI ship = weapon.getShip();
        if (ship == null || !ship.isAlive()) {
            return;
        }

        // 初始化判断机翼位置
        String slotId = weapon.getSlot().getId();
        if (slotId.startsWith("LEFT")) {
            wingSide = WingSide.LEFT;
        } else if (slotId.startsWith("RIGHT")) {
            wingSide = WingSide.RIGHT;
        } else {
            wingSide = WingSide.NONE;
            return; // 非机翼武器不处理
        }

        String wingId;
        if (wingSide == WingSide.LEFT) {
            wingId = "LWING_" + "L";
        } else {
            wingId = "RWING_" + "R";
        }

        for (ShipAPI module : ship.getChildModulesCopy()) {
            if (module.getStationSlot() != null && module.getStationSlot().getId().equals(wingId)) {
                wingModule = module;
                break;
            }
        }


        if (wingSide == WingSide.NONE || wingModule == null || !wingModule.isAlive()) {
            weapon.disable();
            hideWeapon(weapon);
        }

        // 获取舰船当前朝向
        float shipFacing = ship.getFacing();

        // 计算当前武器相对于舰船的偏移角度
        float currentOffset = weapon.getCurrAngle() - shipFacing;
        while (currentOffset > 180) currentOffset -= 360;
        while (currentOffset < -180) currentOffset += 360;

        // 获取舰船当前速度和最大速度
        float currentSpeed = ship.getVelocity().length();
        float maxSpeed = ship.getMutableStats().getMaxSpeed().getBaseValue(); // 获取包含所有加成的最大速度
        float speedThreshold = maxSpeed * 2.5f; // 最大速度的175%

        // 根据速度比例计算目标偏移角度
        float targetOffset;
        if (currentSpeed > speedThreshold) {
            // 速度小于最大速度的75%：机翼朝减速位置旋转
            targetOffset = wingSide == WingSide.LEFT ? maxBacktArc : -maxBacktArc;
        } else {
            // 速度大于等于最大速度的75%：机翼朝加速位置旋转
            targetOffset = wingSide == WingSide.LEFT ? -maxFrontArc : maxFrontArc;
        }

        // 仅对目标偏移角度应用平滑过渡
        if (currentOffset != targetOffset) {
            if (currentOffset < targetOffset) {
                currentOffset = Math.min(currentOffset + rotationSpeed * amount, targetOffset);
            } else {
                currentOffset = Math.max(currentOffset - rotationSpeed * amount, targetOffset);
            }
        }

        // 计算最终角度(舰船朝向+偏移角度)
        float finalAngle = shipFacing + currentOffset;
        weapon.setCurrAngle(finalAngle);

        Iterator<Map.Entry<DamagingProjectileAPI, ProjectileState>> iter =
                delayedProjectiles.entrySet().iterator();

        while (iter.hasNext()) {
            Map.Entry<DamagingProjectileAPI, ProjectileState> entry = iter.next();
            DamagingProjectileAPI proj = entry.getKey();
            ProjectileState state = entry.getValue();

            // 检查抛射体是否仍然有效
            if (!engine.isEntityInPlay(proj) || proj.isExpired() || proj.didDamage()) {
                iter.remove();
                continue;
            }

            // 绘制效果
            Vector2f nv1 = new Vector2f(25, 25);
            SpriteAPI l = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
            MagicRender.singleframe(l, proj.getLocation(), nv1, 0, color, true, ABOVE_SHIPS_AND_MISSILES_LAYER);

            intervalUtil.advance(amount);
            if (intervalUtil.intervalElapsed()) {
                spawnShip(weapon.getShip(), proj, state);
                iter.remove(); // 移除已处理的抛射体
            }
        }
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        delayedProjectiles.put(projectile, new ProjectileState(
                engine.getTotalElapsedTime(true),
                new Vector2f(weapon.getShip().getVelocity()),
                projectile.getOwner()
        ));
    }

    private static class ProjectileState {
        final float fireTime;
        final Vector2f parentVelocity;
        final int owner;

        ProjectileState(float fireTime, Vector2f parentVelocity, int owner) {
            this.fireTime = fireTime;
            this.parentVelocity = parentVelocity;
            this.owner = owner;
        }
    }

    private static void spawnShip(ShipAPI source, DamagingProjectileAPI projectile, ProjectileState state) {
        CombatFleetManagerAPI manager = Global.getCombatEngine().getFleetManager(source.getOwner());
        boolean orig = manager.isSuppressDeploymentMessages();
        manager.setSuppressDeploymentMessages(false);

        Global.getCombatEngine().removeEntity(projectile);
        manager.setSuppressDeploymentMessages(orig);
        Global.getCombatEngine().spawnExplosion(projectile.getLocation(), new Vector2f(0, 0), Color.darkGray, 200f, 2f);

        Vector2f combinedVel = Vector2f.add(projectile.getVelocity(), state.parentVelocity, null);

        ShipAPI ship = manager.spawnShipOrWing("rs_Tr_3_Assault", projectile.getLocation(), projectile.getFacing(), 3);

        Vector2f nv1 = new Vector2f(150, 150);
        SpriteAPI l = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        MagicRender.singleframe(l, ship.getLocation(), nv1, 0, color, true, ABOVE_SHIPS_AND_MISSILES_LAYER);
        ship.getVelocity().set(combinedVel);

        // 应用AI
        ship.setShipAI(new Launchai(ship));
    }


    private void hideWeapon(WeaponAPI weapon) {
        if (weapon == null) return;

        // 透明颜色，参考骤雨mod的实现
        Color transparent = new Color(0f, 0f, 0f, 0f);

        // 设置所有贴图组件为透明，包括发光贴图
        if (weapon.getSprite() != null) {
            weapon.getSprite().setColor(transparent);
        }
        if (weapon.getBarrelSpriteAPI() != null) {
            weapon.getBarrelSpriteAPI().setColor(transparent);
        }
        if (weapon.getUnderSpriteAPI() != null) {
            weapon.getUnderSpriteAPI().setColor(transparent);
        }
        if (weapon.getGlowSpriteAPI() != null) {
            weapon.getGlowSpriteAPI().setColor(transparent);
        }

        // 隐藏导弹渲染数据
        if (weapon.getMissileRenderData() != null) {
            for (MissileRenderDataAPI render : weapon.getMissileRenderData()) {
                render.getSprite().setWidth(0);
                render.getSprite().setHeight(0);
            }
        }
    }

}