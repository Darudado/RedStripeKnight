package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lwjgl.util.vector.Vector2f;
import java.util.HashMap;
import java.util.Map;

public class ExRangeRocket implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin, OnHitEffectPlugin {

    // 存储弹丸数据的映射
    private Map<DamagingProjectileAPI, ProjectileData> projectileDataMap = new HashMap<>();

    // 配置常量
    private static final float ACCELERATION_FORCE = 500f; // 加速力大小
    private static final float DAMAGE_BOOST_MULTIPLIER = 1.25f; // 伤害提升倍数
    private static final float ACCELERATION_RANGE_RATIO = 0.75f; // 加速触发时的射程比例

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        Object source = projectile.getSource();
        engine.applyDamage(target ,point , 300 , DamageType.HIGH_EXPLOSIVE , 50 , false ,false ,source);
        engine.applyDamage(target ,point , 600 , DamageType.FRAGMENTATION , 50 , false ,false ,source);
    }

    // 弹丸数据内部类
    private static class ProjectileData {
        float maxRange; // 最大射程
        float originalDamage; // 原始伤害
        boolean hasAccelerated = false; // 是否已经加速过
        float distanceTraveled = 0f; // 已飞行距离
        Vector2f lastPosition; // 上一帧位置

        ProjectileData(float range, float damage, Vector2f startPos) {
            this.maxRange = range;
            this.originalDamage = damage;
            this.lastPosition = new Vector2f(startPos);
        }
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (projectile == null || weapon == null) return;

        // 获取武器射程（如果没有武器则使用默认值）
        float weaponRange = weapon.getRange();

        // 记录原始伤害和射程
        float originalDamage = projectile.getDamage().getDamage();

        // 创建并存储弹丸数据
        ProjectileData data = new ProjectileData(weaponRange, originalDamage, projectile.getLocation());
        projectileDataMap.put(projectile, data);

        // 可选：在发射时添加一些视觉效果
        engine.addHitParticle(
                projectile.getLocation(),
                new Vector2f(),
                10f, // 粒子大小
                1f, // 亮度
                0.5f, // 持续时间
                new java.awt.Color(255, 200, 100, 255) // 橙黄色
        );
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused()) return;

        // 遍历所有跟踪的弹丸
        Map<DamagingProjectileAPI, ProjectileData> newMap = new HashMap<>();

        for (Map.Entry<DamagingProjectileAPI, ProjectileData> entry : projectileDataMap.entrySet()) {
            DamagingProjectileAPI projectile = entry.getKey();
            ProjectileData data = entry.getValue();

            // 检查弹丸是否仍然有效
            if (projectile.isExpired() || !engine.isEntityInPlay(projectile)) {
                continue;
            }

            // 更新飞行距离
            if (data.lastPosition != null) {
                float distanceThisFrame = Vector2f.sub(
                        projectile.getLocation(), data.lastPosition, new Vector2f()
                ).length();
                data.distanceTraveled += distanceThisFrame;
            }
            data.lastPosition = new Vector2f(projectile.getLocation());

            // 检查是否达到加速条件（射程的1/2且尚未加速）
            float accelerationTriggerDistance = data.maxRange * ACCELERATION_RANGE_RATIO;

            if (!data.hasAccelerated && data.distanceTraveled >= accelerationTriggerDistance) {
                applyAcceleration(projectile, data, engine);
                data.hasAccelerated = true;
            }

            // 保留有效的弹丸数据
            newMap.put(projectile, data);
        }

        // 更新映射
        projectileDataMap = newMap;
    }

    /**
     * 对弹丸施加加速并提升伤害
     */
    private void applyAcceleration(DamagingProjectileAPI projectile, ProjectileData data, CombatEngineAPI engine) {
        // 1. 施加加速力（沿当前方向）
        Vector2f acceleration = new Vector2f(projectile.getFacing(), ACCELERATION_FORCE);
        Vector2f.add(projectile.getVelocity(), acceleration, projectile.getVelocity());

        // 2. 提升伤害
        float newDamage = data.originalDamage * DAMAGE_BOOST_MULTIPLIER;
        projectile.getDamage().setDamage(newDamage);

        // 3. 创建加速视觉效果
        createAccelerationEffects(projectile, engine);

        // 4. 播放音效（可选）
        // Global.getSoundPlayer().playSound("system_emp_emitter_impact", 1f, 1f, projectile.getLocation(), new Vector2f());
    }

    /**
     * 创建加速时的视觉效果
     */
    private void createAccelerationEffects(DamagingProjectileAPI projectile, CombatEngineAPI engine) {
        Vector2f location = projectile.getLocation();
        Vector2f velocity = projectile.getVelocity();

        // 主加速闪光
        engine.spawnExplosion(
                location,
                velocity,
                new java.awt.Color(255, 150, 50, 255), // 橙红色
                80f, // 大小
                0.8f // 持续时间
        );

        // 粒子效果
        for (int i = 0; i < 15; i++) {
            Vector2f randomOffset = new Vector2f(
                    (float) (Math.random() - 0.5) * 40f,
                    (float) (Math.random() - 0.5) * 40f
            );
            Vector2f particlePos = Vector2f.add(location, randomOffset, new Vector2f());

            Vector2f particleVel = new Vector2f(
                    velocity.x * 0.5f + (float) (Math.random() - 0.5) * 100f,
                    velocity.y * 0.5f + (float) (Math.random() - 0.5) * 100f
            );

            engine.addHitParticle(
                    particlePos,
                    particleVel,
                    (float) (Math.random() * 3f + 2f), // 大小
                    1f, // 亮度
                    (float) (Math.random() * 0.5f + 0.3f), // 持续时间
                    new java.awt.Color(255, 200, 100, 200) // 颜色
            );
        }

        // 尾迹增强
        for (int i = 0; i < 8; i++) {
            Vector2f trailPos = new Vector2f(
                    location.x - velocity.x * 0.1f * i,
                    location.y - velocity.y * 0.1f * i
            );

            engine.addSmokeParticle(
                    trailPos,
                    new Vector2f(velocity.x * 0.2f, velocity.y * 0.2f),
                    15f, // 大小
                    0.8f, // 亮度递减
                    1.5f, // 持续时间
                    new java.awt.Color(200, 150, 80, 150) // 烟雾颜色
            );
        }
    }
}