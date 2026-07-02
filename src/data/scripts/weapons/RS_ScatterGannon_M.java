package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;

import java.awt.Color;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class RS_ScatterGannon_M implements OnFireEffectPlugin, EveryFrameWeaponEffectPlugin {

    private static final Color FLASH_COLOR = new Color(3,205,255,215); //Color of muzzle flash explosion
    private static final float FLASH_SIZE = 75f; //explosion size
    private static final float FLASH_DUR = 0.2f;

    @Override
    public void onFire(DamagingProjectileAPI proj, WeaponAPI weapon, CombatEngineAPI engine)
    {
        Vector2f loc = proj.getLocation();
        Vector2f vel = proj.getVelocity();
        int shotCount1 = (5); //Scatter projectiles
        for (int j = 0; j < shotCount1; j++) {
            Vector2f randomVel = MathUtils.getRandomPointOnCircumference(null, MathUtils.getRandomNumberInRange(
                    45f, 90f));
            randomVel.x += vel.x;
            randomVel.y += vel.y;
            //spec + "_clone" means this will call the weapon (not projectile! you need a separate weapon) with the id "($projectilename)_clone".
            engine.spawnProjectile(proj.getSource(), proj.getWeapon(), "rs_scattercannon_sub_M", loc, proj.getFacing(),
                    randomVel);
        }
        int shotCount2 = (1); //Core projectile
        for (int j = 0; j < shotCount2; j++) {
            engine.spawnProjectile(proj.getSource(), proj.getWeapon(), "rs_scattercannon_core_M", loc, proj.getFacing(),
                    null);
        }
        int shotCount3 = (15); //Micro projectiles
        for (int j = 0; j < shotCount3; j++) {
            Vector2f randomVel = MathUtils.getRandomPointOnCircumference(null, MathUtils.getRandomNumberInRange(
                    75f, 150f));
            randomVel.x += vel.x;
            randomVel.y += vel.y;
            //spec + "_clone" means this will call the weapon (not projectile! you need a separate weapon) with the id "($projectilename)_clone".
            engine.spawnProjectile(proj.getSource(), proj.getWeapon(), "rs_scattercannon_submicro_M", loc, proj.getFacing(),
                    randomVel);
        }
        engine.removeEntity(proj);
        // set up for explosions
        ShipAPI ship = weapon.getShip();
        Vector2f ship_velocity = ship.getVelocity();
        Vector2f proj_location = proj.getLocation();
        // do visual fx
        engine.spawnExplosion(proj_location, ship_velocity, FLASH_COLOR, FLASH_SIZE, FLASH_DUR);
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {

    }
}