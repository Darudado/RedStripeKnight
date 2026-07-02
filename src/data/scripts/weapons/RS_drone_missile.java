package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.ai.Launchai;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.fs.starfarer.api.combat.CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;

public class RS_drone_missile implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {
    private final IntervalUtil intervalUtil = new IntervalUtil(1f, 1f);
    public static final Color color = Color.RED;
    private final Map<DamagingProjectileAPI, ProjectileState> delayedProjectiles =
            new LinkedHashMap<>(32, 0.75f, false);

    // 添加无参构造函数
    public RS_drone_missile() {
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused()) {
            return;
        }

        ShipAPI ship = weapon.getShip();
        if (ship == null || !ship.isAlive()) {
            return;
        }

        Iterator<Map.Entry<DamagingProjectileAPI, ProjectileState>> iter =
                delayedProjectiles.entrySet().iterator();

        while (iter.hasNext()) {
            Map.Entry<DamagingProjectileAPI, ProjectileState> entry = iter.next();
            DamagingProjectileAPI proj = entry.getKey();
            ProjectileState state = entry.getValue();

            // 检查抛射体是否仍然有效
            if (!engine.isEntityInPlay(proj) || proj.isExpired() || proj.didDamage()) {
                iter.remove();
                continue;
            }

            // 绘制效果
            Vector2f nv1 = new Vector2f(150, 20);
            SpriteAPI l = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
            MagicRender.singleframe(l, proj.getLocation(), nv1, 0, color, true, ABOVE_SHIPS_AND_MISSILES_LAYER);

            intervalUtil.advance(amount);
            if (intervalUtil.intervalElapsed()) {
                spawnShip(weapon.getShip(), proj, state);
                iter.remove(); // 移除已处理的抛射体
            }
        }
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        delayedProjectiles.put(projectile, new ProjectileState(
                engine.getTotalElapsedTime(true),
                new Vector2f(weapon.getShip().getVelocity()),
                projectile.getOwner()
        ));
    }

    private static class ProjectileState {
        final float fireTime;
        final Vector2f parentVelocity;
        final int owner;

        ProjectileState(float fireTime, Vector2f parentVelocity, int owner) {
            this.fireTime = fireTime;
            this.parentVelocity = parentVelocity;
            this.owner = owner;
        }
    }

    private static void spawnShip(ShipAPI source, DamagingProjectileAPI projectile, ProjectileState state) {
        CombatFleetManagerAPI manager = Global.getCombatEngine().getFleetManager(source.getOwner());
        boolean orig = manager.isSuppressDeploymentMessages();
        manager.setSuppressDeploymentMessages(false);

        Global.getCombatEngine().removeEntity(projectile);
        manager.setSuppressDeploymentMessages(orig);
        Global.getCombatEngine().spawnExplosion(projectile.getLocation(), new Vector2f(0, 0), Color.darkGray, 200f, 2f);

        Vector2f combinedVel = Vector2f.add(projectile.getVelocity(), state.parentVelocity, null);

        ShipAPI ship = manager.spawnShipOrWing("rs_drone_fighter_wing", projectile.getLocation(), projectile.getFacing(), 3);
        ship.getVelocity().set(combinedVel);

        // 应用AI
        ship.setShipAI(new Launchai(ship));
    }
}