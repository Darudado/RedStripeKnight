package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class RX78GP03D_MaingunEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    // 电弧生成间隔(秒)
    // 每次生成的电弧数量
    private static final int ARC_COUNT = 8;
    // 开火特效范围
    private static final float FIRE_EFFECT_RANGE = 50f;

    // 存储弹体和它们的历史位置
    private final Map<DamagingProjectileAPI, Vector2f> projectileHistory = new HashMap<>();

    private final IntervalUtil interval = new IntervalUtil(0.015F, 0.015F);
    private float lastChargeLevel = 0.0F;
    private int lastWeaponAmmo = 0;

    private static final Color MUZZLE_FLASH_COLOR = new Color(255,255,255,200);
    private static final Color CHARGEUP_PARTICLE_COLOR = new Color(255,100,200, 100);


    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused() || weapon == null || weapon.getShip() == null) return;
        // 处理充能阶段的电弧
        float chargeLevel = weapon.getChargeLevel();
        int weaponAmmo = weapon.getAmmo();
        if (!(chargeLevel > this.lastChargeLevel) && weaponAmmo >= this.lastWeaponAmmo) {
        } else {
            Vector2f weaponLocation = weapon.getLocation();
            ShipAPI ship = weapon.getShip();
            float shipFacing = weapon.getCurrAngle();
            Vector2f shipVelocity = ship.getVelocity();
            Vector2f muzzleLocation = MathUtils.getPointOnCircumference(weaponLocation, weapon.getSlot().isHardpoint() ? 28.5F : 30.0F, shipFacing);
            this.interval.advance(amount);

            if ( chargeLevel >1) {
                engine.spawnExplosion(muzzleLocation, shipVelocity, MUZZLE_FLASH_COLOR, 250.0F, 0.25F);
                engine.addSmoothParticle(muzzleLocation, shipVelocity, 750.0F, 1.0F, 0.5F, MUZZLE_FLASH_COLOR);


                RippleDistortion ripple = new RippleDistortion(muzzleLocation, ship.getVelocity());
                ripple.setSize(250.0F);
                ripple.setIntensity(25.0F);
                ripple.setFrameRate(120.0F);
                ripple.fadeInSize(0.5F);
                ripple.fadeOutIntensity(0.5F);
                DistortionShader.addDistortion(ripple);
            }
        }

        this.lastChargeLevel = chargeLevel;
        this.lastWeaponAmmo = weaponAmmo;

    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {

        // 初始化弹体历史位置
        projectileHistory.put(projectile, new Vector2f(projectile.getLocation()));


        // 在武器发射点周围生成电弧特效
        Vector2f firePoint = new Vector2f(projectile.getLocation());
        float chargeLevel = weapon.getChargeLevel();
        float shipFacing = weapon.getCurrAngle();

        ShipAPI ship = weapon.getShip();

        if (this.interval.intervalElapsed() && weapon.isFiring()) {
            int particleCount = (int)(20.0F * chargeLevel);

            for(int i = 0; i < particleCount; ++i) {
                float distance = MathUtils.getRandomNumberInRange(20.0F, 125.0F);
                float size = MathUtils.getRandomNumberInRange(5.0F, 10.0F);
                float angle = MathUtils.getRandomNumberInRange(-180.0F, 180.0F);
                Vector2f spawnLocation = MathUtils.getPointOnCircumference(firePoint, distance, angle + shipFacing);
                float speed = distance / 0.75F;
                Vector2f particleVelocity = MathUtils.getPointOnCircumference(firePoint, speed, 180.0F + angle + shipFacing);
                engine.addHitParticle(spawnLocation, particleVelocity, size, weapon.getChargeLevel(), 0.75F, CHARGEUP_PARTICLE_COLOR);
            }

            Vector2f point1 = MathUtils.getRandomPointInCircle(firePoint, (float)Math.random() * weapon.getChargeLevel() * 125.0F + 50.0F);
            engine.spawnEmpArc(ship, firePoint, new SimpleEntity(firePoint), new SimpleEntity(point1), DamageType.ENERGY, 0.0F, 0.0F, 1000.0F, null, weapon.getChargeLevel() * 5.0F + 5.0F, CHARGEUP_PARTICLE_COLOR, CHARGEUP_PARTICLE_COLOR);
        }

        for (int i = 0; i < ARC_COUNT * 2; i++) {
            Vector2f arcStart = MathUtils.getPointOnCircumference(
                    firePoint,
                    MathUtils.getRandomNumberInRange(5f, FIRE_EFFECT_RANGE),
                    MathUtils.getRandomNumberInRange(0f, 360f));

            Vector2f arcEnd = MathUtils.getPointOnCircumference(
                    firePoint,
                    MathUtils.getRandomNumberInRange(5f, FIRE_EFFECT_RANGE),
                    MathUtils.getRandomNumberInRange(0f, 360f));

            engine.spawnEmpArcVisual(
                    arcStart, weapon.getShip(), arcEnd, weapon.getShip(),
                    MathUtils.getRandomNumberInRange(3f, 10f),
                    new Color(255,125,200, 150),
                    new Color(255,110,175, 200)
            );
            Vector2f drift = MathUtils.getRandomPointInCone(weapon.getShip().getVelocity(), MathUtils.getRandomNumberInRange(25, 100), weapon.getCurrAngle()-5, weapon.getCurrAngle()+5);
            engine.addNebulaParticle(
                    projectile.getLocation(),
                    drift,
                    MathUtils.getRandomNumberInRange(20, 40),
                    2,
                    0.1f,
                    0.3f,
                    1f,
                    new Color(255,100,200,100)
            );
        }
    }
}