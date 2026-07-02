package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import java.awt.Color;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;

public class RS_ArondightEveryFrameEffect implements EveryFrameWeaponEffectPlugin {
    private static final float CHARGEUP_PARTICLE_ANGLE_SPREAD = 360.0F;
    private static final Color CHARGEUP_PARTICLE_COLOR = new Color(200, 35, 25, 100);
    private static final float CHARGEUP_PARTICLE_BRIGHTNESS = 1.0F;
    private static final float CHARGEUP_PARTICLE_COUNT_FACTOR = 20.0F;
    private static final float CHARGEUP_PARTICLE_DISTANCE_MAX = 125.0F;
    private static final float CHARGEUP_PARTICLE_DISTANCE_MIN = 20.0F;
    private static final float CHARGEUP_PARTICLE_DURATION = 0.75F;
    private static final float CHARGEUP_PARTICLE_SIZE_MAX = 10.0F;
    private static final float CHARGEUP_PARTICLE_SIZE_MIN = 5.0F;
    private static final Color MUZZLE_FLASH_COLOR = new Color(200, 0, 10, 200);
    private static final float MUZZLE_FLASH_DURATION = 0.25F;
    private static final float MUZZLE_FLASH_SIZE = 250.0F;
    private static final float MUZZLE_OFFSET_HARDPOINT = 28.5F;
    private static final float MUZZLE_OFFSET_TURRET = 30.0F;
    private final IntervalUtil interval = new IntervalUtil(0.015F, 0.015F);
    private float lastChargeLevel = 0.0F;
    private int lastWeaponAmmo = 0;
    private boolean shot = false;

    public RS_ArondightEveryFrameEffect() {
    }

    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
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
                }

                if (!this.shot && weaponAmmo < this.lastWeaponAmmo) {
                    engine.spawnExplosion(muzzleLocation, shipVelocity, MUZZLE_FLASH_COLOR, 250.0F, 0.25F);
                    engine.addSmoothParticle(muzzleLocation, shipVelocity, 750.0F, 1.0F, 0.5F, MUZZLE_FLASH_COLOR);

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