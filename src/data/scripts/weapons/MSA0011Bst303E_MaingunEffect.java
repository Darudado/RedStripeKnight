package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MSA0011Bst303E_MaingunEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    // 电弧生成间隔(秒)
    private static final float ARC_INTERVAL = 0.15f;
    // 每次生成的电弧数量
    private static final int ARC_COUNT = 8;
    // 电弧随机偏移范围
    private static final float ARC_RANDOM_OFFSET = 50f;
    // 开火特效范围
    private static final float FIRE_EFFECT_RANGE = 50f;

    private final IntervalUtil arcTimer = new IntervalUtil(ARC_INTERVAL, ARC_INTERVAL);
    // 存储弹体和它们的历史位置
    private final Map<DamagingProjectileAPI, Vector2f> projectileHistory = new HashMap<>();
    // 充能阶段参数
    private static final float CHARGE_ARC_INTERVAL = 0.1f; // 充能电弧间隔
    private static final int CHARGE_ARC_COUNT = 4; // 充能阶段电弧生成次数
    private float effectLevel = 0f; // 充能进度
    private final IntervalUtil chargeArcTimer = new IntervalUtil(CHARGE_ARC_INTERVAL, CHARGE_ARC_INTERVAL);


    private final IntervalUtil interval = new IntervalUtil(0.015F, 0.015F);
    private float lastChargeLevel = 0.0F;
    private int lastWeaponAmmo = 0;
    private boolean shot = false;

    private static final Color MUZZLE_FLASH_COLOR = new Color(200, 0, 10, 200);
    private static final Color CHARGEUP_PARTICLE_COLOR = new Color(200, 35, 25, 100);


    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused() || weapon == null || weapon.getShip() == null) return;
        // 处理充能阶段的电弧
        if (weapon.isFiring() && weapon.getCooldownRemaining() == 0 && effectLevel < 1f) {
            effectLevel += amount * 2f; // 调整这个值可以控制效果速度
            chargeArcTimer.advance(amount);
            if (chargeArcTimer.intervalElapsed()) {
                // 获取武器朝向向量
                Vector2f weaponDir = VectorUtils.rotate(new Vector2f(1, 0), weapon.getCurrAngle());

                // 计算基础位置（使用x,y偏移坐标）
                float offsetX = -20 + 20f * (effectLevel * CHARGE_ARC_COUNT); // x轴偏移
                float offsetY = 7f; // y轴偏移
                Vector2f firePoint = new Vector2f(weapon.getLocation());
                Vector2f basePos = Vector2f.add(firePoint,
                        new Vector2f(weaponDir.x * offsetX + weaponDir.y * offsetY,
                                weaponDir.y * offsetX - weaponDir.x * offsetY), null);



                // 在左右两侧生成电弧
                for (int i = 0; i < 2; i++) {
                    Vector2f leftPos = MathUtils.getPointOnCircumference(
                            basePos,
                            MathUtils.getRandomNumberInRange(5f, 10f),
                            weapon.getCurrAngle() - 90f);

                    Vector2f rightPos = MathUtils.getPointOnCircumference(
                            basePos,
                            MathUtils.getRandomNumberInRange(5f, 10f),
                            weapon.getCurrAngle() + 90f);

                    engine.spawnEmpArcVisual(
                            leftPos, weapon.getShip(), rightPos, weapon.getShip(),
                            1f,
                            new Color(255, 100, 100, 125),
                            new Color(255, 255, 255, 180)
                    );
                }
            }
        }
        else {
            effectLevel = 0f;
            chargeArcTimer.setElapsed(0f);
        }
        // 更新电弧计时器
        arcTimer.advance(amount);

        // 获取所有属于该武器的弹体
        List<DamagingProjectileAPI> projectiles = new ArrayList<>();
        for (DamagingProjectileAPI proj : CombatUtils.getProjectilesWithinRange(weapon.getLocation(), 1000f)) {
            if (weapon == proj.getWeapon() && engine.isEntityInPlay(proj) && !proj.isExpired()) {
                projectiles.add(proj);
            }
        }

        // 生成电弧效果
        if (arcTimer.intervalElapsed()) {
            for (DamagingProjectileAPI proj : projectiles) {
                Vector2f currentPos = new Vector2f(proj.getLocation());
                Vector2f lastPos = projectileHistory.get(proj);

                if (lastPos == null) {
                    projectileHistory.put(proj, currentPos);
                    continue;
                }

                for (int i = 0; i < ARC_COUNT; i++) {
                    // 使用线性插值计算路径上的点
                    float lerp = MathUtils.getRandomNumberInRange(0f, 1f);
                    Vector2f arcPos = new Vector2f(
                            lastPos.x + (currentPos.x - lastPos.x) * lerp,
                            lastPos.y + (currentPos.y - lastPos.y) * lerp
                    );

                    // 添加随机偏移，使用世界坐标而非相对坐标
                    Vector2f randomOffset = MathUtils.getRandomPointInCircle(
                            new Vector2f(0, 0),
                            MathUtils.getRandomNumberInRange(10f, ARC_RANDOM_OFFSET)
                    );
                    arcPos = Vector2f.add(arcPos, randomOffset, null);

                    // 生成电弧视觉效果
                    engine.spawnEmpArcVisual(
                            lastPos, null, // 不再绑定到舰船
                            arcPos, null,  // 不再绑定到舰船
                            MathUtils.getRandomNumberInRange(5f, 15f),
                            new Color(255, 100, 100, 100),
                            new Color(255, 55, 55, 150)
                    );
                }

                projectileHistory.put(proj, currentPos);
            }
        }

        // 清理已销毁的弹体记录
        List<DamagingProjectileAPI> toRemove = new ArrayList<>();
        for (DamagingProjectileAPI proj : projectileHistory.keySet()) {
            if (!engine.isEntityInPlay(proj) || proj.isExpired()) {
                toRemove.add(proj);
            }
        }
        for (DamagingProjectileAPI proj : toRemove) {
            projectileHistory.remove(proj);
        }

        if (!engine.isPaused()) {
            float chargeLevel = weapon.getChargeLevel();
            int weaponAmmo = weapon.getAmmo();
            if (!(chargeLevel > this.lastChargeLevel) && weaponAmmo >= this.lastWeaponAmmo) {
                this.shot = false;
            } else {
                Vector2f weaponLocation = weapon.getLocation();
                ShipAPI ship = weapon.getShip();
                float shipFacing = weapon.getCurrAngle();
                Vector2f shipVelocity = ship.getVelocity();
                Vector2f muzzleLocation = MathUtils.getPointOnCircumference(weaponLocation, weapon.getSlot().isHardpoint() ? 28.5F : 30.0F, shipFacing);
                this.interval.advance(amount);
                if (this.interval.intervalElapsed() && weapon.isFiring()) {
                    int particleCount = (int) (20.0F * chargeLevel);

                    for (int i = 0; i < particleCount; ++i) {
                        float distance = MathUtils.getRandomNumberInRange(20.0F, 125.0F);
                        float size = MathUtils.getRandomNumberInRange(5.0F, 10.0F);
                        float angle = MathUtils.getRandomNumberInRange(-180.0F, 180.0F);
                        Vector2f spawnLocation = MathUtils.getPointOnCircumference(muzzleLocation, distance, angle + shipFacing);
                        float speed = distance / 0.75F;
                        Vector2f particleVelocity = MathUtils.getPointOnCircumference(shipVelocity, speed, 180.0F + angle + shipFacing);
                        engine.addHitParticle(spawnLocation, particleVelocity, size, weapon.getChargeLevel(), 0.75F, CHARGEUP_PARTICLE_COLOR);
                    }

                    Vector2f point1 = MathUtils.getRandomPointInCircle(muzzleLocation, (float) Math.random() * weapon.getChargeLevel() * 125.0F + 50.0F);
                    engine.spawnEmpArc(ship, muzzleLocation, new SimpleEntity(muzzleLocation), new SimpleEntity(point1), DamageType.ENERGY, 0.0F, 0.0F, 1000.0F, null, weapon.getChargeLevel() * 5.0F + 5.0F, CHARGEUP_PARTICLE_COLOR, CHARGEUP_PARTICLE_COLOR);
                }

                if (!this.shot && weaponAmmo < this.lastWeaponAmmo) {
                    engine.spawnExplosion(muzzleLocation, shipVelocity, MUZZLE_FLASH_COLOR, 250.0F, 0.25F);
                    engine.addSmoothParticle(muzzleLocation, shipVelocity, 750.0F, 1.0F, 0.5F, MUZZLE_FLASH_COLOR);

                    for (int i = 0; i < 5; ++i) {
                        Vector2f point1 = MathUtils.getRandomPointInCircle(muzzleLocation, (float) Math.random() * 150.0F + 150.0F);
                        engine.spawnEmpArc(ship, muzzleLocation, new SimpleEntity(muzzleLocation), new SimpleEntity(point1), DamageType.ENERGY, 0.0F, 0.0F, 1000.0F, null, weapon.getChargeLevel() * 15.0F + 15.0F, CHARGEUP_PARTICLE_COLOR, MUZZLE_FLASH_COLOR);
                    }

                    RippleDistortion ripple = new RippleDistortion(muzzleLocation, ship.getVelocity());
                    ripple.setSize(250.0F);
                    ripple.setIntensity(25.0F);
                    ripple.setFrameRate(120.0F);
                    ripple.fadeInSize(0.5F);
                    ripple.fadeOutIntensity(0.5F);
                    DistortionShader.addDistortion(ripple);
                    float oppositeAngle = weapon.getCurrAngle() - 180.0F;
                    if (oppositeAngle < 0.0F) {
                        oppositeAngle += 360.0F;
                    }

                    CombatUtils.applyForce(ship, oppositeAngle, 500.0F);
                } else {
                    this.shot = false;
                }
            }

            this.lastChargeLevel = chargeLevel;
            this.lastWeaponAmmo = weaponAmmo;
        }
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {

        // 初始化弹体历史位置
        projectileHistory.put(projectile, new Vector2f(projectile.getLocation()));

        // 在武器发射点周围生成电弧特效
        Vector2f firePoint = new Vector2f(projectile.getLocation());
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
                    new Color(255, 52, 52, 150),
                    new Color(175,25,40, 200)
            );
            Vector2f drift = MathUtils.getRandomPointInCone(weapon.getShip().getVelocity(), MathUtils.getRandomNumberInRange(25, 100), weapon.getCurrAngle()-5, weapon.getCurrAngle()+5);
            Vector2f.add(weapon.getShip().getVelocity(), MathUtils.getPoint(new Vector2f(), 35, weapon.getCurrAngle()+180), weapon.getShip().getVelocity());
            engine.addNebulaParticle(
                    projectile.getLocation(),
                    drift,
                    MathUtils.getRandomNumberInRange(20, 40),
                    2,
                    0.1f,
                    0.3f,
                    1f,
                    new Color(175,25,45,100)
            );
        }
    }
}