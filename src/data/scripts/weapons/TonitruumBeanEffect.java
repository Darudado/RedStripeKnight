//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

public class TonitruumBeanEffect implements BeamEffectPlugin {
    protected boolean wasZero = true;
    private final IntervalUtil fireInterval = new IntervalUtil(0.05F, 0.1F);

    public TonitruumBeanEffect() {
    }

    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        CombatEntityAPI target = beam.getDamageTarget();
        if (target instanceof ShipAPI && beam.getBrightness() >= 1.0F && beam.getWeapon() != null) {
            float dur = beam.getDamage().getDpsDuration();
            if (!this.wasZero) {
                dur = 0.0F;
            }

            this.wasZero = beam.getDamage().getDpsDuration() <= 0.0F;
            if (dur > 0.0F) {
                boolean hitShield = target.getShield() != null && target.getShield().isWithinArc(beam.getTo());
                if (hitShield) {
                    ShipAPI ship = (ShipAPI)target;
                    if (!ship.hasListenerOfClass(com.fs.starfarer.api.impl.combat.GravitonBeamEffect.GravitonBeamDamageTakenMod.class)) {
                        ship.addListener(new com.fs.starfarer.api.impl.combat.GravitonBeamEffect.GravitonBeamDamageTakenMod(ship));
                    }

                    List<com.fs.starfarer.api.impl.combat.GravitonBeamEffect.GravitonBeamDamageTakenMod> listeners = ship.getListeners(com.fs.starfarer.api.impl.combat.GravitonBeamEffect.GravitonBeamDamageTakenMod.class);
                    if (listeners.isEmpty()) {
                        return;
                    }

                    com.fs.starfarer.api.impl.combat.GravitonBeamEffect.GravitonBeamDamageTakenMod listener = listeners.get(0);
                    listener.notifyHit(beam.getWeapon());
                }
            }
        }

        if (target instanceof ShipAPI) { //&& beam.getBrightness() >= 1.0F) {
            float dur = beam.getDamage().getDpsDuration();
            if (!this.wasZero) {
                dur = 0.0F;
            }

            this.wasZero = beam.getDamage().getDpsDuration() <= 0.0F;
            this.fireInterval.advance(dur);
            if (this.fireInterval.intervalElapsed()) {
                ShipAPI ship = (ShipAPI)target;
                    Vector2f point = beam.getRayEndPrevFrame();
                    float emp = beam.getDamage().getFluxComponent() * 1.5F;
                    float dam = beam.getDamage().getDamage() * 0.01F;
                    engine.spawnEmpArcPierceShields(beam.getSource(), point, beam.getDamageTarget(), beam.getDamageTarget(), DamageType.ENERGY, dam, emp, 100000.0F, "tachyon_lance_emp_impact", beam.getWidth() + 9.0F, beam.getFringeColor(), beam.getCoreColor());

            }
        }

    }
}
