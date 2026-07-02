package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.utils.MathPersonal;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class CrDisruptEffect implements OnHitEffectPlugin {
    float ex_dam = MathPersonal.randBetween(350,750);
    private final IntervalUtil fireInterval = new IntervalUtil(0.2F, 0.3F);
    private static final float BASE_EMP_CHANCE = 0.1f;
    private static final Color NEBULA_COLOR = new Color(175,90,90,225);
    private static final float NEBULA_SIZE = 10.0F * (0.75F + (float)Math.random() * 0.5F);
    private static final Color EXPLOSION_COLOR = new Color(175,45,45,225);




    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (target == null) return;
        Vector2f v_target = new Vector2f(target.getVelocity());
        engine.addNebulaParticle(point, v_target, NEBULA_SIZE, 20.0F, 0.15F, 0.3F, 0.8F, NEBULA_COLOR, true);
        engine.spawnExplosion(point, v_target, EXPLOSION_COLOR, NEBULA_SIZE * 3.0F, 0.6F);
        Object source = projectile.getSource();
        engine.applyDamage(target ,point , ex_dam , DamageType.FRAGMENTATION , 50 , false ,false ,source);
        if(point != null) {
            if (Math.random() < BASE_EMP_CHANCE) {
                engine.spawnEmpArcPierceShields(projectile.getSource(), point, null, target, DamageType.ENERGY, 0.0F, 1500.0F, 100000.0F, "tachyon_lance_emp_impact", 20.0F, projectile.getProjectileSpec().getFringeColor(), projectile.getProjectileSpec().getCoreColor());
            }
        }
        if (point != null) {
            if (!(target instanceof MissileAPI)) {
                if (shieldHit && target instanceof ShipAPI Ship) {
                    Ship.getMutableStats().getDynamic().getStat("CR_DISRUPT_SHIELD").modifyFlat("CR_DISRUPT2", 0.2F);
                } else if (!shieldHit && target instanceof ShipAPI Ship) {
                    Ship.getMutableStats().getDynamic().getStat("CR_DISRUPT_E").modifyFlat("CR_DISRUPT2", 0.2F);
                }
            }

            if (this.fireInterval.intervalElapsed()) {
                ShipAPI ship = null;
                if (target instanceof ShipAPI) {
                    ship = (ShipAPI) target;
                }
                boolean hitShield = false;
                if (target != null) {
                    hitShield = target.getShield() != null && target.getShield().isWithinArc(projectile.getSpawnLocation());
                }
                float pierceChance = 0;
                if (target != null) {
                    if (target instanceof ShipAPI) {
                        pierceChance = ((ShipAPI) target).getHardFluxLevel() - 0.1F;
                    }
                }
                if (ship != null) {
                    pierceChance *= ship.getMutableStats().getDynamic().getValue("shield_pierced_mult");
                }
                boolean piercedShield = hitShield && (float) Math.random() < pierceChance;


                if (!hitShield || piercedShield) {
                    Vector2f loc = (Vector2f) projectile.getDamageTarget();
                    float emp = projectile.getDamage().getFluxComponent() * 0.5F;
                    float dam = projectile.getDamage().getDamage() * 0.25F;
                    engine.spawnEmpArcPierceShields(projectile.getSource(), loc, projectile.getDamageTarget(), projectile.getDamageTarget(), DamageType.ENERGY, dam, emp, 100000.0F, "tachyon_lance_emp_impact", 15.0F, projectile.getProjectileSpec().getFringeColor(), projectile.getProjectileSpec().getCoreColor());
                }
            }
        }

    }

    public void advance(float amount, CombatEngineAPI engine, DamagingProjectileAPI pro) {
        CombatEntityAPI target = pro.getDamageTarget();

    }
}