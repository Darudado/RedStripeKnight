package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.combat.entities.Missile;
import com.fs.starfarer.combat.entities.terrain.Asteroid;
import data.scripts.RSModPlugin;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.Map;
import java.util.Objects;

import data.scripts.utils.MathPersonal;
import data.scripts.utils.RSUtil;

import static com.fs.starfarer.api.combat.DamageType.ENERGY;

public class PiercingEffect implements OnHitEffectPlugin ,OnFireEffectPlugin{
    float ex_dam = MathPersonal.randBetween(500,1500);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        Object source = projectile.getSource();
        engine.applyDamage(target ,point , ex_dam , DamageType.KINETIC , 50 , false ,false ,source);

        String picked_weapon_id = "rs_judexterminatus";
        projectile.getWeapon().getId();
        String curr_proj_id = projectile.getProjectileSpecId();
        if("judexterminatus_shot".equals(curr_proj_id)) {
            picked_weapon_id = "rs_judexterminatus";
        }
        boolean doPen = false;
        WeaponAPI weapon = projectile.getWeapon();
        ShipAPI weapon_ship = weapon.getShip();
        if (!shieldHit) {
            doPen = true;
        }

        if (doPen) {
            // 处理飞船目标（非残骸）
            if (target instanceof ShipAPI && !((ShipAPI) target).isHulk()) {
                ShipAPI ship = (ShipAPI) target;
                float projectile_angle = projectile.getFacing();
                Vector2f exit_location = MathPersonal.getVector(point, projectile_angle, target.getCollisionRadius() * 2.0F);
                Vector2f exitLocation = CollisionUtils.getCollisionPoint(exit_location, point, target);
                float travel_distance;

                try {
                    travel_distance = MathUtils.getDistance(point, Objects.requireNonNull(exitLocation));
                } catch (Exception e) {
                    return;
                }

                Vector2f spawn_location = MathPersonal.getVector(point, projectile_angle, travel_distance - 50.0F);
                if (spawn_location != null) {
                    DamagingProjectileAPI new_proj = (DamagingProjectileAPI) engine.spawnProjectile(weapon_ship, weapon, picked_weapon_id, spawn_location, projectile_angle, null);
                    DamagingExplosionSpec exitPlosion = new DamagingExplosionSpec(0.5F, 50.0F, 300.0F, 300.0F, 400.0F, CollisionClass.PROJECTILE_FF, CollisionClass.PROJECTILE_FIGHTER, 10.0F, 5.0F, 6.5F, 5, new Color(255, 155, 155, 255), new Color(100, 100, 255, 255));
                    exitPlosion.setShowGraphic(true);
                    engine.spawnDamagingExplosion(exitPlosion, projectile.getWeapon().getShip(), spawn_location, false);
                    Map<String, Object> customData = projectile.getCustomData();
                    new_proj.setCustomData("target", target);
                    new_proj.setCustomData("exitLocation", spawn_location);
                    if (customData.containsKey("originalSpawnLocation")) {
                        new_proj.setCustomData("originalSpawnLocation", customData.get("originalSpawnLocation"));
                    } else {
                        new_proj.setCustomData("originalSpawnLocation", projectile.getSpawnLocation());
                    }

                    if (customData.containsKey("originalRange")) {
                        new_proj.setCustomData("originalRange", customData.get("originalRange"));
                    } else {
                        new_proj.setCustomData("originalRange", projectile.getWeapon().getRange());
                    }

                    RSUtil.addPenetratingProjectile(new_proj, ship, ENERGY);
                }
            }
            // 处理飞船残骸
            else if (target instanceof ShipAPI && ((ShipAPI) target).isHulk()) {
                ShipAPI hulk = (ShipAPI) target;
                float projectile_angle = projectile.getFacing();
                Vector2f exit_location = MathPersonal.getVector(point, projectile_angle, target.getCollisionRadius() * 2.0F);
                Vector2f exitLocation = CollisionUtils.getCollisionPoint(exit_location, point, target);
                float travel_distance;

                try {
                    travel_distance = MathUtils.getDistance(point, Objects.requireNonNull(exitLocation));
                } catch (Exception e) {
                    return;
                }

                Vector2f spawn_location = MathPersonal.getVector(point, projectile_angle, travel_distance + 55.0F);
                DamagingProjectileAPI new_proj = (DamagingProjectileAPI) engine.spawnProjectile(weapon_ship, weapon, picked_weapon_id, spawn_location, projectile_angle, null);
                Map<String, Object> customData = projectile.getCustomData();
                new_proj.setCustomData("target", target);
                new_proj.setCustomData("exitLocation", point);
                if (customData.containsKey("originalSpawnLocation")) {
                    new_proj.setCustomData("originalSpawnLocation", customData.get("originalSpawnLocation"));
                } else {
                    new_proj.setCustomData("originalSpawnLocation", projectile.getSpawnLocation());
                }

                if (customData.containsKey("originalRange")) {
                    new_proj.setCustomData("originalRange", customData.get("originalRange"));
                } else {
                    new_proj.setCustomData("originalRange", projectile.getWeapon().getRange());
                }

                RSUtil.addPenetratingProjectile(new_proj, hulk, ENERGY);
            }
            // 处理导弹
            else if (target instanceof Missile) {
                float projectile_angle = projectile.getFacing();
                DamagingProjectileAPI new_proj = (DamagingProjectileAPI) engine.spawnProjectile(weapon_ship, weapon, weapon.getId(), point, projectile_angle, null);
                Map<String, Object> customData = projectile.getCustomData();
                new_proj.setCustomData("target", target);
                new_proj.setCustomData("exitLocation", point);
                if (customData.containsKey("originalSpawnLocation")) {
                    new_proj.setCustomData("originalSpawnLocation", customData.get("originalSpawnLocation"));
                } else {
                    new_proj.setCustomData("originalSpawnLocation", projectile.getSpawnLocation());
                }

                if (customData.containsKey("originalRange")) {
                    new_proj.setCustomData("originalRange", customData.get("originalRange"));
                } else {
                    new_proj.setCustomData("originalRange", projectile.getWeapon().getRange());
                }

                // 对于导弹，传递 weapon_ship 而不是 target
                RSUtil.addPenetratingProjectile(new_proj, weapon_ship, ENERGY);
            }
            // 处理小行星
            else if (target instanceof Asteroid) {
                float projectile_angle = projectile.getFacing();
                DamagingProjectileAPI new_proj = (DamagingProjectileAPI) engine.spawnProjectile(weapon_ship, weapon, weapon.getId(), point, projectile_angle, null);
                Map<String, Object> customData = projectile.getCustomData();
                new_proj.setCustomData("target", target);
                new_proj.setCustomData("exitLocation", point);
                if (customData.containsKey("originalSpawnLocation")) {
                    new_proj.setCustomData("originalSpawnLocation", customData.get("originalSpawnLocation"));
                } else {
                    new_proj.setCustomData("originalSpawnLocation", projectile.getSpawnLocation());
                }

                if (customData.containsKey("originalRange")) {
                    new_proj.setCustomData("originalRange", customData.get("originalRange"));
                } else {
                    new_proj.setCustomData("originalRange", projectile.getWeapon().getRange());
                }

                // 对于小行星，传递 weapon_ship 而不是 target
                RSUtil.addPenetratingProjectile(new_proj, weapon_ship, ENERGY);
            }
        }
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (RSModPlugin.particleEngineEnabled) {
            if (projectile != null) {
                RSUtil.SparkTrail.makeTrail(projectile);
            }
        }
    }
}