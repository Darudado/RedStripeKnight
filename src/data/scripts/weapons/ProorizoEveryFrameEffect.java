package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.util.MagicRender;
import java.awt.Color;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;

public class ProorizoEveryFrameEffect implements EveryFrameWeaponEffectPlugin {
    private static final Color CHARGEUP_PARTICLE_COLOR = new Color(200, 35, 25, 100);
    private static final Color MUZZLE_FLASH_COLOR = new Color(200, 0, 10, 200);
    // 新增光晕相关参数
    private static final Vector2f FLARE_A = new Vector2f(500.0F, 200.0F);
    private static final Vector2f FLARE_B = new Vector2f(500.0F, 200.0F); // 初始尺寸与A相同
    private static final Vector2f FLARE_C = new Vector2f(500.0F, 200.0F); // 初始尺寸与A相同

    private final IntervalUtil interval = new IntervalUtil(0.015F, 0.015F);
    private float lastChargeLevel = 0.0F;
    private int lastWeaponAmmo = 0;
    private boolean shot = false;

    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        if (weapon.getCooldownRemaining() <= 0.0F && weapon.isFiring()) {
            Global.getSoundPlayer().playLoop("Proorizo_Charging_Loop", ship, 2.0F * weapon.getChargeLevel(), 1.0F, weapon.getLocation(), ship.getVelocity());
        }
        if (!engine.isPaused()) {
            float chargeLevel = weapon.getChargeLevel();
            int weaponAmmo = weapon.getAmmo();
            if (!(chargeLevel > this.lastChargeLevel) && weaponAmmo >= this.lastWeaponAmmo) {
                this.shot = false;
            } else {
                Vector2f weaponLocation = weapon.getLocation();
                float shipFacing = weapon.getCurrAngle();
                Vector2f shipVelocity = ship.getVelocity();
                Vector2f muzzleLocation = MathUtils.getPointOnCircumference(weaponLocation, weapon.getSlot().isHardpoint() ? 28.5F : 30.0F, shipFacing);
                this.interval.advance(amount);

                // 添加充电时的光晕效果
                if (this.interval.intervalElapsed() && weapon.isFiring()) {
                    int particleCount = (int)(20.0F * chargeLevel);

                    for(int i = 0; i < particleCount; ++i) {
                        float distance = MathUtils.getRandomNumberInRange(20.0F, 125.0F);
                        float size = MathUtils.getRandomNumberInRange(5.0F, 10.0F);
                        float angle = MathUtils.getRandomNumberInRange(-180.0F, 180.0F);
                        Vector2f spawnLocation = MathUtils.getPointOnCircumference(muzzleLocation, distance, angle + shipFacing);
                        float speed = distance / 0.75F;
                        Vector2f particleVelocity = MathUtils.getPointOnCircumference(shipVelocity, speed, 180.0F + angle + shipFacing);
                        engine.addHitParticle(spawnLocation, particleVelocity, size, weapon.getChargeLevel(), 0.75F, CHARGEUP_PARTICLE_COLOR);
                    }

                    Vector2f point1 = MathUtils.getRandomPointInCircle(muzzleLocation, (float)Math.random() * weapon.getChargeLevel() * 125.0F + 50.0F);
                    engine.spawnEmpArc(ship, muzzleLocation, new SimpleEntity(muzzleLocation), new SimpleEntity(point1), DamageType.ENERGY, 0.0F, 0.0F, 1000.0F, null, weapon.getChargeLevel() * 5.0F + 5.0F, CHARGEUP_PARTICLE_COLOR, CHARGEUP_PARTICLE_COLOR);

                    // 充电时随机触发光晕效果
                    if (Math.random() < (double)(amount * 30.0F)) {
                        Vector2f offset = new Vector2f(muzzleLocation);
                        Vector2f.sub(offset, ship.getLocation(), offset);

                        // 根据chargeLevel计算光晕尺寸和位置变化
                        float sizeScaleB = 1.0F - chargeLevel * 0.5F; // B光晕逐渐变小
                        float sizeScaleC = 1.0F - chargeLevel * 0.7F; // C光晕变得最小

                        // 计算光晕前移距离
                        float forwardOffsetB = chargeLevel * 200.0F; // B光晕前移距离
                        float forwardOffsetC = chargeLevel * 350.0F; // C光晕前移最远

                        // 计算前移后的位置
                        Vector2f offsetB = MathUtils.getPointOnCircumference(offset, forwardOffsetB, shipFacing);
                        Vector2f offsetC = MathUtils.getPointOnCircumference(offset, forwardOffsetC, shipFacing);

                        // A光晕 - 尺寸和位置不变
                        MagicRender.objectspace(
                                Global.getSettings().getSprite("fx", "RS_flare1"),
                                ship,
                                offset,
                                new Vector2f(),
                                (Vector2f)(new Vector2f(FLARE_A)).scale(MathUtils.getRandomNumberInRange(0.95F, 1.05F)),
                                new Vector2f(),
                                ship.getFacing(),
                                0.0F,
                                false,
                                CHARGEUP_PARTICLE_COLOR,
                                true,
                                0.05F,
                                0.05F,
                                (float)Math.random() / 10.0F,
                                false
                        );

                        // B光晕 - 尺寸逐渐变小，位置前移
                        MagicRender.objectspace(
                                Global.getSettings().getSprite("fx", "RS_flare1"),
                                ship,
                                offsetB,
                                new Vector2f(),
                                (Vector2f)(new Vector2f(FLARE_B)).scale(sizeScaleB * MathUtils.getRandomNumberInRange(0.95F, 1.05F)),
                                new Vector2f(),
                                ship.getFacing(),
                                0.0F,
                                false,
                                CHARGEUP_PARTICLE_COLOR,
                                true,
                                0.05F,
                                0.05F,
                                (float)Math.random() / 10.0F,
                                false
                        );

                        // C光晕 - 尺寸变得最小，位置前移最远
                        MagicRender.objectspace(
                                Global.getSettings().getSprite("fx", "RS_flare1"),
                                ship,
                                offsetC,
                                new Vector2f(),
                                (Vector2f)(new Vector2f(FLARE_C)).scale(sizeScaleC * MathUtils.getRandomNumberInRange(0.95F, 1.05F)),
                                new Vector2f(),
                                ship.getFacing(),
                                0.0F,
                                false,
                                CHARGEUP_PARTICLE_COLOR,
                                true,
                                0.05F,
                                0.1F,
                                (float)Math.random() / 5.0F,
                                false
                        );
                    }
                }

                if (!this.shot && weaponAmmo < this.lastWeaponAmmo) {
                    // 射击时的爆炸和光晕效果
                    engine.spawnExplosion(muzzleLocation, shipVelocity, MUZZLE_FLASH_COLOR, 250.0F, 0.25F);
                    engine.addSmoothParticle(muzzleLocation, shipVelocity, 750.0F, 1.0F, 0.5F, MUZZLE_FLASH_COLOR);

                    // 射击时触发更强烈的光晕效果
                    Vector2f offset = new Vector2f(muzzleLocation);
                    Vector2f.sub(offset, ship.getLocation(), offset);

                    // 射击时B和C光晕前移到最大距离
                    Vector2f offsetB = MathUtils.getPointOnCircumference(offset, 100.0F, shipFacing);
                    Vector2f offsetC = MathUtils.getPointOnCircumference(offset, 200.0F, shipFacing);

                    // 三个不同参数的光晕，增强视觉效果
                    MagicRender.objectspace(
                            Global.getSettings().getSprite("fx", "RS_flare1"),
                            ship,
                            offset,
                            new Vector2f(),
                            (Vector2f)(new Vector2f(FLARE_A)).scale(2.0F), // 射击时尺寸更大
                            new Vector2f(),
                            ship.getFacing(),
                            2.0F,
                            false,
                            MUZZLE_FLASH_COLOR, // 使用枪口闪光颜色
                            true,
                            1F,
                            8F,
                            1F,
                            false
                    );

                    MagicRender.objectspace(
                            Global.getSettings().getSprite("fx", "RS_flare1"),
                            ship,
                            offsetB,
                            new Vector2f(),
                            (Vector2f)(new Vector2f(FLARE_B)).scale(1.0F), // 射击时B光晕恢复正常尺寸但位置前移
                            new Vector2f(),
                            ship.getFacing(), // 使用武器当前角度
                            2.0F,
                            false,
                            MUZZLE_FLASH_COLOR, // 使用金色
                            true,
                            1F,
                            8F,
                            1F,
                            false
                    );

                    MagicRender.objectspace(
                            Global.getSettings().getSprite("fx", "RS_flare1"),
                            ship,
                            offsetC,
                            new Vector2f(),
                            (Vector2f)(new Vector2f(FLARE_C)).scale(0.8F), // 射击时C光晕保持较小尺寸但位置前移最远
                            new Vector2f(),
                            ship.getFacing(),
                            2.0F,
                            false,
                            MUZZLE_FLASH_COLOR,
                            true,
                            1F,
                            8F,
                            1F,
                            false
                    );

                    for(int i = 0; i < 5; ++i) {
                        Vector2f point1 = MathUtils.getRandomPointInCircle(muzzleLocation, (float)Math.random() * 150.0F + 150.0F);
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
}