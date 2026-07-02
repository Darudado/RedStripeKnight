package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.ProjectileSpecAPI;
import data.scripts.utils.MathPersonal;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import java.util.*;

public class QubitMirage extends BaseHullMod {

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        int crafts = 0, extraCrafts = 0;
        for (String w : ship.getVariant().getFittedWings()) {
            FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(w);
            if (spec != null) {
                crafts += spec.getNumFighters();
                extraCrafts++; // 每个机翼累加一次
            }
        }
        if (extraCrafts > 0) {
            float penalty = (float) (crafts + extraCrafts) / crafts;
            ship.getMutableStats().getDynamic().getMod(Stats.REPLACEMENT_RATE_DECREASE_MULT)
                    .modifyMult(id, penalty);
        }

        Set<WeaponAPI> registered = new HashSet<>();
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (isWeaponValid(weapon) && !registered.contains(weapon)) {
                ship.addListener(new QubitMirageListener(ship, weapon));
                registered.add(weapon);
            }
        }
    }

    private boolean isWeaponValid(WeaponAPI weapon) {
        return !weapon.isDecorative()
                && !weapon.isBeam()
                && !weapon.isBurstBeam()
                && !weapon.hasAIHint(WeaponAPI.AIHints.PD)
                && !weapon.hasAIHint(WeaponAPI.AIHints.PD_ONLY)
                && weapon.getType() != WeaponAPI.WeaponType.MISSILE
                && weapon.getSpec().getProjectileSpec() != null;
    }

    public static class QubitMirageListener implements AdvanceableListener {
        private final ShipAPI ship;
        private final WeaponAPI weapon;
        private float cooldownTimer = 0f;
        private static final float COOLDOWN = 0.3f;

        public QubitMirageListener(ShipAPI ship, WeaponAPI weapon) {
            this.ship = ship;
            this.weapon = weapon;
        }

        @Override
        public void advance(float amount) {
            if (Global.getCombatEngine().isPaused()
                    || ship == null
                    || !ship.isAlive()
                    || weapon == null
                    || !weapon.isFiring()) {
                return;
            }

            cooldownTimer += amount;
            if (cooldownTimer < COOLDOWN) return;
            cooldownTimer = 0f;

            if (MathPersonal.randBetween(0f, 1f) >= 0.1f) return;

            Vector2f spawnLoc = MathPersonal.pickLocation(ship);
            Vector2f target = getTargetPosition();
            float angle = calculateAngle(spawnLoc, target);

            spawnProjectile(angle, spawnLoc);
        }

        private Vector2f getTargetPosition() {
            if (ship.getShipTarget() != null) {
                return ship.getShipTarget().getLocation();
            }
            return ship.getMouseTarget();
        }

        private float calculateAngle(Vector2f spawnLoc, Vector2f target) {
            if (target == null) return ship.getFacing();

            float mistakeRange = MathUtils.getRandomNumberInRange(40f, 160f);
            Vector2f adjustedTarget = MathUtils.getRandomPointInCircle(target, mistakeRange);
            return VectorUtils.getAngle(spawnLoc, adjustedTarget);
        }

        private void spawnProjectile(float angle, Vector2f spawnLoc) {
            CombatEngineAPI engine = Global.getCombatEngine();
            CombatEntityAPI entity = engine.spawnProjectile(
                    ship, weapon, weapon.getId(),
                    spawnLoc, angle, ship.getVelocity()
            );

            if (!(entity instanceof DamagingProjectileAPI proj)||entity instanceof BeamAPI ||entity instanceof MissileAPI) {
                engine.removeEntity(entity);
                return;
            }

            ProjectileSpecAPI spec = proj.getProjectileSpec();
            if (spec != null && spec.getOnFireEffect() != null) {
                spec.getOnFireEffect().onFire(proj, weapon, engine);
            }
        }
    }
}