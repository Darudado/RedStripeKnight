package data.scripts.weapons;


import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lwjgl.util.vector.Vector2f;

import data.scripts.utils.MathPersonal;

import java.awt.*;

public class JusticeConfessorEffect implements OnHitEffectPlugin, EveryFrameWeaponEffectPlugin{
    private static final Color FLARECOLOR = new Color(247, 246, 246, 180);


    float ex_dam = MathPersonal.randBetween(50,250);
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        Object source = projectile.getSource();
        engine.applyDamage(target ,point , ex_dam , DamageType.HIGH_EXPLOSIVE , 50 , false ,false ,source);

    }


    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        Vector2f RandonLocation = weapon.getFirePoint(0);
        if (weapon.isFiring()) {
            engine.addSmoothParticle(
                    RandonLocation, new Vector2f(), 5f, 0.5F, 0.5F, 0.1f, FLARECOLOR);
        }
    }
}