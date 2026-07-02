package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;

public class MLMROnHit implements OnHitEffectPlugin {
    private static final Color COLOR1 = new Color(25, 125, 150);
    private static final Color COLOR2 = new Color(255, 255, 255);
    private static final Vector2f ZERO = new Vector2f();

    List<ShipAPI> targets = new ArrayList<>();


    public MLMROnHit() {
    }

    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        for (ShipAPI ship : engine.getShips()) {
            if (MathUtils.getDistance(ship.getLocation(), point) <= 250.0F) {
                targets.add(ship);
            }
        }


        if (target != null && point != null) {
            float emp = projectile.getEmpAmount();
            float dam = projectile.getDamageAmount();
            List<ShipAPI> targets = new ArrayList<>();
            // 排除目标自身（如果目标是舰船）
            for (ShipAPI ship : targets)
                if (ship == target)
                    return;
            if (target instanceof ShipAPI ship) {
                boolean remove = targets.remove(ship);
                engine.spawnEmpArc(projectile.getSource(), point, null, target, DamageType.ENERGY, dam, emp, 100000.0F, "tachyon_lance_emp_impact", 40.0F, COLOR1, COLOR2);
            }

            for (ShipAPI ship : targets) {
                float distance = MathUtils.getDistance(ship.getLocation(), point);
                float reduction = 1.0F;
                if (distance > 125.0F + ship.getCollisionRadius()) {
                    reduction = (250.0F - distance / 2.0F) / 125.0F;
                }

                if (ship.getOwner() == projectile.getOwner()) {
                    reduction *= 0.25F;
                }

                engine.spawnEmpArc(projectile.getSource(), point, null, ship, DamageType.ENERGY, dam * reduction, emp * reduction, 500.0F, "tachyon_lance_emp_impact", 40.0F, COLOR1, COLOR2);
            }

            for (int i = 0; i < 3; ++i) {
                Vector2f location = new Vector2f(projectile.getLocation().x + (float) Math.random() * 200.0F + 100.0F, projectile.getLocation().y);
                location = VectorUtils.rotateAroundPivot(location, projectile.getLocation(), (float) Math.random() * 360.0F, location);
                engine.spawnEmpArcPierceShields(projectile.getSource(), point, null, new SimpleEntity(location), DamageType.ENERGY, 0.0F, 1500.0F, 100000.0F, "tachyon_lance_emp_impact", 20.0F, COLOR1, COLOR2);
                engine.applyDamage(target , point , 150 , DamageType.HIGH_EXPLOSIVE , 50 ,true ,false ,projectile.getSource());
                Global.getSoundPlayer().playSound("disabled_large", 0.75F, 0.8F, point, ZERO);
            }
        }
    }



}