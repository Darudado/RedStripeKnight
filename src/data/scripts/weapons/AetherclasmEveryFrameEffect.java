package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import java.util.Random;
import data.scripts.utils.MathPersonal;
import java.awt.Color;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

public class AetherclasmEveryFrameEffect implements EveryFrameWeaponEffectPlugin {
    private static final int NUM_ARCS = 5;
    private static final Color ARC_COLOR = new Color(255, 255, 255, 225);

    // 记录上一帧的充能等级，用于检测充能进度变化（chargeLevel > lastChargeLevel时判定为充能中）。
    private static final Color FLARECOLOR1 = new Color(255, 255, 255, 255);
    private static final Color FLARECOLOR2 = new Color(247, 246, 246, 180);
    private static final Random random = new Random();

    float lastChargeLevel = 0.0F;

    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

        ShipAPI ship = weapon.getShip();
        float chargeLevel = weapon.getChargeLevel();
        Vector2f RandonLocation = weapon.getFirePoint(0);

        float range = weapon.getRange();
        if (chargeLevel > this.lastChargeLevel && weapon.isFiring()) {
            float duration = 0.8F * (1.0F - chargeLevel);
            float fadeIn = duration * 0.6F;
            float fadeOut = duration * 0.4F;


            for (int i = 0; (float) i < 200.0F * amount; ++i) {
                Vector2f point = MathUtils.getPointOnCircumference(RandonLocation, MathUtils.getRandomNumberInRange(80.0F, 100.0F) * (2.0F - chargeLevel), MathUtils.getRandomNumberInRange(0f, 360f));
                Vector2f vel = Vector2f.sub(RandonLocation, point, null);
                vel.scale(1.0F / duration);
                Vector2f size = new Vector2f(MathUtils.getRandomNumberInRange(50.0F, 75.0F), MathUtils.getRandomNumberInRange(10.0F, 15.0F));
                Vector2f growth = new Vector2f(MathUtils.getRandomNumberInRange(-40.0F, -45.0F), MathUtils.getRandomNumberInRange(-2.0F, 0.0F));
                float particleFacing = VectorUtils.getFacing(vel);

                engine.addSmoothParticle(
                        RandonLocation, new Vector2f(), 200.0F * chargeLevel, 1.0F, 0.5F, amount * 3.0F, FLARECOLOR2);
            }

            Vector2f point = MathUtils.getPointOnCircumference(RandonLocation, MathUtils.getRandomNumberInRange(80.0F, 100.0F) * (2.0F - chargeLevel), MathUtils.getRandomNumberInRange(0f, 360f));
            Vector2f vel = Vector2f.sub(RandonLocation, point, null);
            engine.addHitParticle(
                    point,
                    new Vector2f(vel.x * 0.1f, vel.y * 0.1f),
                    MathUtils.getRandomNumberInRange(50.0F, 60.0F),
                    0.5f,
                    fadeIn + fadeOut,
                    FLARECOLOR2
            );

            if (MathPersonal.rollChance(7.0F * amount)) {
                for (int i = 0; i < MathPersonal.RANDOM.nextInt(2) + 1; ++i) {
                    float radius = 50.0F + MathUtils.getRandomNumberInRange(150.0F, 200.0F) * (1.0F - chargeLevel);
                    Vector2f spawnVector = MathUtils.getRandomPointInCircle(RandonLocation, radius);
                    float width = MathUtils.getRandomNumberInRange(4.0F, 10.0F);
                    engine.spawnEmpArcVisual(RandonLocation, weapon.getShip(), spawnVector, (CombatEntityAPI) null, width, ARC_COLOR, Color.white);
                    engine.spawnEmpArc(weapon.getShip(), spawnVector, weapon.getShip(), weapon.getShip(), DamageType.ENERGY, 0, 0, 150.0F, (String)null, MathUtils.getRandomNumberInRange(10.0F, 20.0F), ARC_COLOR, Color.white);
                }


                if (weapon.isFiring()) {
                    ShipAPI source = weapon.getShip();
                    if (ship.getOwner() == 0 &&
                            MathUtils.getRandomNumberInRange(0f, 1f) <= 0.05f * amount &&
                            !source.isStationModule() &&
                            !source.isStation()) {
                        for (WeaponAPI other : source.getAllWeapons()) {
                            if (other != weapon && other.isFiring() && other.getId().contentEquals(weapon.getId())) {

                                break;
                            }
                        }
                    }
                }


            }
        }
    }
}


