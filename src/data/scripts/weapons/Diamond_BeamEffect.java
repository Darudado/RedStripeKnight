package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.util.Iterator;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

public class Diamond_BeamEffect implements EveryFrameWeaponEffectPlugin {
    public static float TARGET_RANGE = 75.0F;
    public static float RIFT_RANGE = 10.0F;
    protected IntervalUtil interval = new IntervalUtil(0.8F, 1.2F);

    public Diamond_BeamEffect() {
        this.interval.setElapsed((float)Math.random() * this.interval.getIntervalDuration());
    }

    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        List<BeamAPI> beams = weapon.getBeams();
        if (!beams.isEmpty()) {
            BeamAPI beam = beams.get(0);
            if (!(beam.getBrightness() < 1.0F)) {
                this.interval.advance(amount * 2.0F);
                if (this.interval.intervalElapsed()) {
                    if (beam.getLengthPrevFrame() < 10.0F) {
                        return;
                    }

                    CombatEntityAPI target = this.findTarget(beam, beam.getWeapon(), engine);
                    Vector2f loc;
                    if (target == null) {
                        loc = this.pickNoTargetDest(beam, beam.getWeapon(), engine);
                    } else {
                        loc = target.getLocation();
                    }

                    Vector2f from = Misc.closestPointOnSegmentToPoint(beam.getFrom(), beam.getRayEndPrevFrame(), loc);
                    Vector2f to = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(from, loc));
                    to.scale(Math.min(Misc.getDistance(from, loc), RIFT_RANGE));
                    Vector2f.add(from, to, to);
                    this.spawnMine(beam.getSource(), to);
                }

            }
        }
    }

    public void spawnMine(ShipAPI source, Vector2f mineLoc) {
        CombatEngineAPI engine = Global.getCombatEngine();
        MissileAPI mine = (MissileAPI)engine.spawnProjectile(source, null, "riftcascade_minelayer", mineLoc, (float)Math.random() * 360.0F, null);
        if (source != null) {
            Global.getCombatEngine().applyDamageModifiersToSpawnedProjectileWithNullWeapon(source, WeaponType.MISSILE, false, mine.getDamage());
        }

        float fadeInTime = 0.05F;
        mine.getVelocity().scale(0.0F);
        mine.fadeOutThenIn(fadeInTime);
        float liveTime = 0.0F;
        mine.setFlightTime(mine.getMaxFlightTime() - liveTime);
        mine.addDamagedAlready(source);
        mine.setNoMineFFConcerns(true);
    }

    public Vector2f pickNoTargetDest(BeamAPI beam, WeaponAPI weapon, CombatEngineAPI engine) {
        Vector2f from = beam.getFrom();
        Vector2f to = beam.getRayEndPrevFrame();
        float length = beam.getLengthPrevFrame();
        float f = 0.25F + (float)Math.random() * 0.75F;
        Vector2f loc = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(from, to));
        loc.scale(length * f);
        Vector2f.add(from, loc, loc);
        return Misc.getPointWithinRadius(loc, RIFT_RANGE);
    }

    public CombatEntityAPI findTarget(BeamAPI beam, WeaponAPI weapon, CombatEngineAPI engine) {
        Vector2f to = beam.getRayEndPrevFrame();
        Iterator<Object> iter = Global.getCombatEngine().getAllObjectGrid().getCheckIterator(to, RIFT_RANGE * 2.0F, RIFT_RANGE * 2.0F);
        int owner = weapon.getShip().getOwner();
        WeightedRandomPicker<CombatEntityAPI> picker = new WeightedRandomPicker<>();

        while(true) {
            CombatEntityAPI other;
            ShipAPI ship;
            do {
                do {
                    Object o;
                    do {
                        if (!iter.hasNext()) {
                            return picker.pick();
                        }

                        o = iter.next();
                    } while(!(o instanceof MissileAPI) && !(o instanceof ShipAPI));

                    other = (CombatEntityAPI)o;
                } while(other.getOwner() == owner);

                if (!(other instanceof ShipAPI)) {
                    break;
                }

                ship = (ShipAPI)other;
            } while(!ship.isFighter() && !ship.isDrone());

            float radius = Misc.getTargetingRadius(to, other, false);
            Vector2f p = Misc.closestPointOnSegmentToPoint(beam.getFrom(), beam.getRayEndPrevFrame(), other.getLocation());
            float dist = Misc.getDistance(p, other.getLocation()) - radius;
            if (!(dist > TARGET_RANGE)) {
                picker.add(other);
            }
        }
    }
}
