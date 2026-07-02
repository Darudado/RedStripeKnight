package data.scripts.weapons;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class AetherclasmBeanEffect implements BeamEffectPlugin {
    private final IntervalUtil fireInterval = new IntervalUtil(0.2F, 0.3F);
    private boolean wasZero = true;
    IntervalUtil sparkInterval = new IntervalUtil(0.05f, 0.1f);   //电弧生成逻辑初始化
    public static final float maxSpread = 20;                      //电弧扩散逻辑

    public AetherclasmBeanEffect() {
    }

    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
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
            engine.spawnEmpArcVisual(closer, beam.getSource(), farer, beam.getSource(), 5, Color.WHITE, Color.WHITE);



        }

    }
}