package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import org.lwjgl.util.vector.Vector2f;

public class AccAdvancedScripts implements OnFireEffectPlugin, EveryFrameWeaponEffectPlugin {

    // 状态变量
    private float currentSpread = -1f; // 当前散布值，初始化为-1表示使用最大散布
    private boolean wasReloading = false;
    private int shotsInBurst = 0;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused()) return;

        advanceConverging(amount, weapon);
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        // 应用当前的散布到发射的抛射体
        applyConvergingSpread(projectile, weapon);

        // 连发计数
        WeaponSpecAPI spec = weapon.getSpec();
        int burstSize = spec.getBurstSize();
        shotsInBurst++;

        // 如果完成一次连发，减少散布，并重置计数
        if (shotsInBurst >= burstSize) {
            float convergenceStep = spec.getSpreadBuildup();
            currentSpread = Math.max(spec.getMinSpread(), currentSpread - convergenceStep);
            shotsInBurst = 0;
        }
    }

    private void advanceConverging(float amount, WeaponAPI weapon) {
        WeaponSpecAPI spec = weapon.getSpec();
        float maxSpread = spec.getMaxSpread();
        float minSpread = spec.getMinSpread();
        float decayRate = spec.getSpreadDecayRate();

        if (currentSpread < 0f) {
            currentSpread = maxSpread;
        }

        boolean isReloading = weapon.getCooldownRemaining() > 0;
        boolean isFiring = weapon.isFiring();
        float chargeLevel = weapon.getChargeLevel();

        // 原有的装填逻辑：装填开始时减少散布（如果设计需要，否则移除）
        if (isReloading && !wasReloading) {
            // 根据设计意图决定是否在装填时减少散布
            currentSpread = Math.max(minSpread, currentSpread - spec.getSpreadBuildup());
        }

        // 当武器不处于任何活动状态时，散布逐渐恢复
        if (!isReloading && !isFiring && chargeLevel <= 0) {
            currentSpread = Math.min(maxSpread, currentSpread + decayRate * amount);
        }

        wasReloading = isReloading;

        // 确保散布在最小和最大范围内
        currentSpread = Math.max(minSpread, Math.min(maxSpread, currentSpread));
    }

    private void applyConvergingSpread(DamagingProjectileAPI projectile, WeaponAPI weapon) {
        float spreadToUse = (currentSpread < 0f) ? weapon.getSpec().getMaxSpread() : currentSpread;
        float angleOffset = (float) ((Math.random() * 2f - 1f) * spreadToUse);
        applyAngleToProjectile(projectile, angleOffset);
    }

    private void applyAngleToProjectile(DamagingProjectileAPI projectile, float angleOffset) {
        float facing = projectile.getFacing();
        float newFacing = facing + angleOffset;
        projectile.setFacing(newFacing);

        float speed = projectile.getVelocity().length();
        float angleRad = (float) Math.toRadians(newFacing);

        Vector2f newVelocity = new Vector2f(
                (float) Math.cos(angleRad) * speed,
                (float) Math.sin(angleRad) * speed
        );

        projectile.getVelocity().set(newVelocity);
    }


    // 可选方法：播放开火特效
    private void playFireEffect(WeaponAPI weapon, DamagingProjectileAPI projectile) {
        // 根据当前散布调整特效强度
        float effectIntensity = 1.0f - (currentSpread / weapon.getSpec().getMaxSpread()) * 0.5f;

        // 可以在这里添加自定义粒子效果、声音等
    }

}