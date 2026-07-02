package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import data.scripts.utils.MathPersonal;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class SEHPEffect implements OnHitEffectPlugin {
    float ex_dam = MathPersonal.randBetween(15,45);
    private static final Color NEBULA_COLOR = new Color(175,90,90,75);
    private static final float NEBULA_SIZE = 10.0F * (0.75F + (float)Math.random() * 0.5F);
    private static final Color EXPLOSION_COLOR = new Color(175,45,45,125);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        Vector2f v_target = new Vector2f(target.getVelocity());
        engine.addNebulaParticle(point, v_target, NEBULA_SIZE, 20.0F, 0.15F, 0.3F, 0.8F, NEBULA_COLOR, true);
        engine.spawnExplosion(point, v_target, EXPLOSION_COLOR, NEBULA_SIZE * 3.0F, 0.6F);
        Object source = projectile.getSource();
        engine.applyDamage(target ,point , ex_dam , DamageType.FRAGMENTATION , 50 , false ,false ,source);

    }
}