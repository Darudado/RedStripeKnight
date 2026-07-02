package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.util.IntervalUtil;
import org.magiclib.util.MagicRender;

import java.awt.*;

public class LudiciumBeanEffect implements BeamEffectPlugin {

    private final IntervalUtil fireInterval = new IntervalUtil(0.25f, 1.75f);
    private boolean wasZero = true;
    boolean runOnce = false;
    IntervalUtil sparkInterval = new IntervalUtil(0.1f, 0.15f);
    public static final float maxSpread = 15;

    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        //CombatEntityAPI target = beam.getDamageTarget();
        WeaponAPI weapon = beam.getWeapon();
        float range = beam.getBrightness() * 800f;
        int alpha_value1 = Math.round(beam.getBrightness() * 50f);

        if (alpha_value1 > 255); {
            alpha_value1 = 255;
        }

        MagicRender.battlespace(Global.getSettings().getSprite("graphics/starscape/star2.png"), beam.getFrom(), new Vector2f(),
                new Vector2f(50f, 25f),
                new Vector2f(range * 2f, range),
                weapon.getCurrAngle() - 90f,
                0f,
                new Color(175,90,90, alpha_value1),
                true,
                0.07f,
                0f,
                0.14f);

        if (beam.getBrightness() >= 1f) {
            float dur = beam.getDamage().getDpsDuration();
            // needed because when the ship is in fast-time, dpsDuration will not be reset every frame as it should be
            if (!wasZero) dur = 0;
            wasZero = beam.getDamage().getDpsDuration() <= 0;
            fireInterval.advance(dur);

            //boolean hitShield = target.getShield() != null && target.getShield().isWithinArc(beam.getTo());
            Vector2f point = beam.getRayEndPrevFrame();

            if (!runOnce) {
                runOnce = true;
                Global.getSoundPlayer().playSound("LEA_fire", 1.1f, 1.5f, beam.getFrom(), new Vector2f());
                engine.spawnProjectile(weapon.getShip(), weapon, "rs_Ludiciumaccelerator_main", beam.getFrom(), weapon.getCurrAngle(), new Vector2f());
                if (weapon.getChargeLevel() >= 0.5f) {
                    beam.setWidth(beam.getWidth() * (-1f + (weapon.getChargeLevel() * 2.5f)));
                }
            }
        } else {
            runOnce = false;
        }

        CombatEntityAPI target = beam.getDamageTarget();


        if (target instanceof ShipAPI && beam.getBrightness() >= 1.0F) {
            float dur = beam.getDamage().getDpsDuration();
            if (!this.wasZero) {
                dur = 0.0F;
            }

            this.wasZero = beam.getDamage().getDpsDuration() <= 0.0F;
            this.fireInterval.advance(dur);
            if (this.fireInterval.intervalElapsed()) {
                ShipAPI ship = (ShipAPI)target;
                boolean hitShield = target.getShield() != null && target.getShield().isWithinArc(beam.getTo());
                float pierceChance = ((ShipAPI)target).getHardFluxLevel() - 0.1F;
                pierceChance *= ship.getMutableStats().getDynamic().getValue("shield_pierced_mult");
                boolean piercedShield = hitShield && (float)Math.random() < pierceChance;
                if (!hitShield || piercedShield) {
                    Vector2f point = beam.getRayEndPrevFrame();
                    float emp = beam.getDamage().getFluxComponent() * 0.5F;
                    float dam = beam.getDamage().getDamage() * 0.25F;
                    engine.spawnEmpArcPierceShields(beam.getSource(), point, beam.getDamageTarget(), beam.getDamageTarget(), DamageType.ENERGY, dam, emp, 100000.0F, "tachyon_lance_emp_impact", beam.getWidth() + 5.0F, beam.getFringeColor(), beam.getCoreColor());
                }
            }
        }





        sparkInterval.advance(amount);
        if (sparkInterval.intervalElapsed()) {   //准备生成电弧
            Vector2f start = beam.getFrom();
            Vector2f end = beam.getTo();
            //获取光束起始位置
            Vector2f p1 = MathUtils.getRandomPointOnLine(start, end);
            Vector2f p2 = MathUtils.getRandomPointOnLine(start, end);
            float dist1 = Misc.getDistance(p1, start);
            float dist2 = Misc.getDistance(p2, start);
            Vector2f closer = p1;
            Vector2f farer = p2;
            float farerDist = dist1;
            if (dist1 > dist2) {
                closer = p2;
                farer = p1;
                farerDist = dist2;               //确定光束起始位置
            }
            closer = MathUtils.getRandomPointOnCircumference(closer, beam.getWidth());
            farer = MathUtils.getPointOnCircumference(farer, beam.getWidth() * maxSpread * (farerDist / beam.getWeapon().getRange()), (float) ((Math.random() - 0.5f) * (Math.random() * 40 + 160) + beam.getWeapon().getCurrAngle()));
            //     farer = MathUtils.getRandomPointOnCircumference(farer, beam.getWidth() * maxSpread * (farerDist / beam.getWeapon().getRange()));
            engine.spawnEmpArcVisual(closer, beam.getSource(), farer, beam.getSource(), 5, beam.getFringeColor(), beam.getCoreColor());



        }

    }
}





