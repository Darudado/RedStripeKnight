package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.dweller.RiftLightningEffect;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;

import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class SanctaferitBeamEffect implements BeamEffectPlugin {
    private boolean done = false;
    private boolean runOnce;

    public SanctaferitBeamEffect() {
    }

    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        if (!this.done) {
            CombatEntityAPI target = beam.getDamageTarget();
            boolean first = beam.getWeapon().getBeams().indexOf(beam) == 0;
            if (target != null && beam.getBrightness() >= 1.0F && first) {
                Vector2f point = beam.getTo();
                float maxDist = 0.0F;

                for(BeamAPI curr : beam.getWeapon().getBeams()) {
                    maxDist = Math.max(maxDist, Misc.getDistance(point, curr.getTo()));
                }

                if (maxDist < 15.0F) {
                    DamagingProjectileAPI e = engine.spawnDamagingExplosion(this.createExplosionSpec(), beam.getSource(), point);
                    e.addDamagedAlready(target);
                    this.done = true;
                }
            }

        }

        WeaponAPI weapon = beam.getWeapon();
        CombatEntityAPI target = beam.getDamageTarget();
        ShipAPI ship = weapon.getShip();
        float width = beam.getWidth();
        float i;

        Vector2f point = beam.getRayEndPrevFrame();
        Vector2f weaponLocation = weapon.getLocation();

        engine.addHitParticle(point, MathUtils.getPointOnCircumference(null, MathUtils.getRandomNumberInRange(100f, 200f), MathUtils.getRandomNumberInRange(0f, 360f)), 7f, 5f, MathUtils.getRandomNumberInRange(0.5f, 1f), beam.getFringeColor());

        Color beamcolor = beam.getCoreColor();

        //运行一次后方代码
        if (weapon.getChargeLevel() >= 1f && !this.runOnce) {
            this.runOnce = true;
            if (weapon.getChargeLevel() > 0) {
                engine.spawnExplosion(point, new Vector2f(), Color.magenta, 25f, 0.3f);
            }
        }

        //运行
        if (this.runOnce) {
            //获取光束的距离
            i = width * 0.1f * MathUtils.getDistance(beam.getTo(), beam.getFrom()) * amount * 0.15f * weapon.getChargeLevel();

            for (int a = 0; a < i; ++a) {
                Vector2f loc = MathUtils.getRandomPointInCircle(MathUtils.getRandomPointOnLine(beam.getFrom(), beam.getTo()), width * 0.1f);
                if (Global.getCombatEngine().getViewport().isNearViewport(loc, 30f)) {
                    Vector2f vel = MathUtils.getRandomPointInCircle(new Vector2f(ship.getVelocity().x * 0.5f, ship.getVelocity().y * 0.5f), 50f);
                    //engine.addSmoothParticle(loc, vel, MathUtils.getRandomNumberInRange(5f, 10f), weapon.getChargeLevel(), MathUtils.getRandomNumberInRange(0.4f, 0.9f), beamcolor);
                    //生成和设定颜色相反的粒子
                    engine.addNegativeParticle(loc, vel, MathUtils.getRandomNumberInRange(5f, 10f), weapon.getChargeLevel(), MathUtils.getRandomNumberInRange(0.3f, 0.6f), beam.getFringeColor());
                    //生成螺旋状大片星云粒子
                    //engine.addSwirlyNebulaParticle(loc, var27, 40f * (0.75f + (float)Math.random() * 0.5f), MathUtils.getRandomNumberInRange(1f, 3f), 0f, 0f, 1f, new Color(beam.getFringeColor().getRed(), beam.getFringeColor().getGreen(), beam.getFringeColor().getBlue(), 100),true);
                }
            }
        }
        //if (weapon.getShip().getFluxTracker().getFluxLevel() > 0.5f) {

                if (beam.didDamageThisFrame()) {
                    float damage = 25f;
                    engine.applyDamage(target, point, damage, DamageType.KINETIC, 0, false, false, 1);
                    engine.applyDamage(target, point, damage*2, DamageType.FRAGMENTATION, 0, false, false, 1);
                    for (int ii = 1; ii < 2; ii++) {
                        //engine.spawnEmpArcVisual(beam.getFrom(), weapon.getShip(), point, weapon.getShip(), 5f,  beam.getFringeColor(), beam.getCoreColor());
                        //电弧特效前置设置
                        float angle;
                        float radiusMult;
                        angle = 360f * (float) Math.random();
                        radiusMult = MathUtils.getRandomNumberInRange(1f, 2f);
                        Vector2f point1 = MathUtils.getPointOnCircumference(weaponLocation, 80f * radiusMult * 0.5f, angle);
                        //生成电弧
                        //engine.spawnEmpArcVisual(beam.getFrom(), weapon.getShip(), point1, weapon.getShip(), 10f, beam.getFringeColor(), beam.getCoreColor());

                        float dist = Misc.getDistance(beam.getFrom(), beam.getTo());
                        if (dist > 100f) {
                            EmpArcEntityAPI.EmpArcParams params = new EmpArcEntityAPI.EmpArcParams();
                            //长度
                            params.segmentLengthMult = 8f;
                            params.zigZagReductionFactor = 0.15f;
                            params.fadeOutDist = 50f;
                            params.minFadeOutMult = 10f;
                            params.flickerRateMult = 0.3f;
                            float fraction = Math.min(0.33f, 300f / dist);
                            params.brightSpotFullFraction = fraction;
                            params.brightSpotFadeFraction = fraction;

                            float arcSpeed = RiftLightningEffect.RIFT_LIGHTNING_SPEED;
                            params.movementDurOverride = Math.max(0.05f, dist / arcSpeed);
                            EmpArcEntityAPI arc = engine.spawnEmpArcVisual(beam.getFrom(), ship, beam.getTo(), ship,
                                    20f, // thickness
                                    beamcolor,
                                    Color.white,
                                    params
                            );
                            arc.setCoreWidthOverride(20f);

                            arc.setRenderGlowAtStart(false);
                            arc.setFadedOutAtStart(true);
                            arc.setSingleFlickerMode(true);

                            Vector2f pt = Vector2f.add(beam.getFrom(), beam.getTo(), new Vector2f());
                            pt.scale(0.5f);
                        }
                    }
                    for (int ii = 1; ii < 2; ii++) {
                        engine.spawnEmpArc(
                                weapon.getShip(),
                                point,
                                null,
                                beam.getDamageTarget(),
                                beam.getWeapon().getDamageType(),
                                0,
                                0,
                                1000000,
                                null,
                                5f,
                                beam.getFringeColor(),
                                beam.getCoreColor());
                    }
            }


    }

    public DamagingExplosionSpec createExplosionSpec() {
        float damage = 100.0F;
        DamagingExplosionSpec spec = new DamagingExplosionSpec(0.1F, 100.0F, 50.0F, damage, damage / 2.0F, CollisionClass.PROJECTILE_FF, CollisionClass.PROJECTILE_FIGHTER, 3.0F, 3.0F, 0.5F, 150, new Color(255, 255, 255, 255), new Color(255, 100, 100, 75));
        spec.setDamageType(DamageType.HIGH_EXPLOSIVE);
        spec.setUseDetailedExplosion(false);
        spec.setSoundSetId("explosion_guardian");
        return spec;
    }
}
