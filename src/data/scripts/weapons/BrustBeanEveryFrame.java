package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class BrustBeanEveryFrame implements EveryFrameWeaponEffectPlugin {
    private final IntervalUtil interval = new IntervalUtil(0.015F, 0.015F);
    private float lastChargeLevel = 0.0F;
    private static final Color CHARGEUP_PARTICLE_COLOR = new Color(25, 100, 255, 175);
    private static final Color ARC_COLOR1 = new Color(20, 102, 255, 255);
    private static final Color ARC_COLOR2 = new Color(115, 115, 255, 255);


    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (!engine.isPaused()) {
            float chargeLevel = weapon.getChargeLevel();
            int weaponAmmo = weapon.getAmmo();
            int lastWeaponAmmo = 0;
            if (!(chargeLevel > this.lastChargeLevel) && weaponAmmo >= lastWeaponAmmo) {
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

            }
            this.lastChargeLevel = chargeLevel;
        }

    }
}