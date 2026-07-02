package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import data.scripts.CombatPlugin;
import data.scripts.RSModPlugin;
import data.scripts.utils.RSUtil;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.CombatEntityAPI;

import static data.scripts.utils.RSUtil.spawnFakeMine;

public class PhaseCruiseMissileEffect implements OnHitEffectPlugin, OnFireEffectPlugin {
    public static final float explosionSpeed = 200.0F;
    public static final float timeBetweenHits = 0.1F;
    public static final float ringDamage = 0.5F;
    private Object spawnInstantaneousExplosion;

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (RSModPlugin.particleEngineEnabled) {
            RSUtil.PhaseTorpedoTrail.makeTrail(projectile);
            ((MissileAPI)projectile).setEmpResistance(10000);
            ((MissileAPI)projectile).setEccmChanceOverride(1.0F);
        }
    }

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        DamagingExplosionSpec spec = ((MissileAPI)projectile).getSpec().getExplosionSpec();
        if (RSModPlugin.particleEngineEnabled) {
            this.addExplosionVisual(point, spec.getRadius());
        }
        Vector2f dummyPos = new Vector2f(point);
        Vector2f scaledVelocity = new Vector2f(projectile.getVelocity());
        scaledVelocity.scale(-0.1F);
        Vector2f.add(dummyPos, scaledVelocity, dummyPos);
        spawnFakeMine(dummyPos, spec.getRadius(), 0.5F, spec.getDamageType(), spec.getRadius() / 200.0F);

        float radius = 20.0F;

        for(float time = 0.1F; radius <= spec.getRadius(); time += 0.1F) {
           CombatPlugin.queueAction(this.addExplosionVisual(point, spec.getRadius()), time);
            radius += 20.0F;
        }

    }
    private CombatPlugin.Action addExplosionVisual(Vector2f loc, float radius) {
        RSUtil.PhaseTorpedoSecondaryExplosion.makeRing(loc, 300);
        RSUtil.Explosion.makeExplosion(loc, 2.0F * radius, 2.4F, 8, 5, 500);
       //spawnInstantaneousExplosion(loc ,2.0F * radius ,2000 ,1500 ,DamageType.ENERGY ,null ,null );
        return null;
    }


    }