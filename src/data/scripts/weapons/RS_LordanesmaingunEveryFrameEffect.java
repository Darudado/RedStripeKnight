package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicFakeBeam;

import java.awt.Color;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class RS_LordanesmaingunEveryFrameEffect implements EveryFrameWeaponEffectPlugin {

	public static final String ID = "rs_LordanesmaingunEveryFrameEffect";

	public static final float TARGET_RANGE = 10f;
	public static final float RIFT_RANGE = 5f;

	private final IntervalUtil fireInterval = new IntervalUtil(0.2f, 0.25f);

    private int fire = 0;
	protected IntervalUtil interval = new IntervalUtil(0.6f, 1.2f);

	IntervalUtil sparkInterval = new IntervalUtil(0.1f, 0.2f);   //电弧生成逻辑初始化
	public static final float maxSpread = 25;

	public RS_LordanesmaingunEveryFrameEffect() {
		interval.setElapsed((float)Math.random() * interval.getIntervalDuration());
	}

	@Override
	public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
		if (engine.isPaused())
			return;

		if (weapon.getShip() == null) return;

		if (weapon.getChargeLevel() >= 0.09f && weapon.getChargeLevel() <= 0.11f && weapon.getCooldownRemaining() <= 0f) {

			Vector2f firePoint = weapon.getFirePoint(0);
			//engine.spawnEmpArcVisual(firePoint,weapon.getShip(),MathUtils.getRandomPointInCircle(firePoint,110f),weapon.getShip(),12f,new Color(210, 99, 52, 245),new Color(183, 59, 35, 221);

			for (int i = 0; i < 6; i = i + 1) {
				engine.addNebulaParticle(firePoint, MathUtils.getRandomPointInCircle(new Vector2f(), MathUtils.getRandomNumberInRange(55f, 65f)), MathUtils.getRandomNumberInRange(60f, 80f), 0.6f, 2.2f, 0.6f, 0.3f, new Color(169, 25, 95, 175)

				);
			}

			//Global.getSoundPlayer().playSound("all_goat_punish_charge", 0.6f, 0.8f, weapon.getLocation(), new Vector2f());
        }

		if (weapon.getChargeLevel() >= 1.0f) {
			Vector2f firePoint = weapon.getFirePoint(0);
			for (int i = 0; i < 1; i = i + 1) {
				engine.addNegativeSwirlyNebulaParticle(firePoint, MathUtils.getRandomPointInCircle(new Vector2f(), MathUtils.getRandomNumberInRange(15f, 158f)), MathUtils.getRandomNumberInRange(0f, 43f), 1.6f, 0.2f, 0.1f, 1.0f, new Color(255, 55, 25, 225)

				);
			}

		}

		if (weapon.isFiring()) {
			if (weapon.getCooldownRemaining() <= 0f) {

				Vector2f firePoint = weapon.getFirePoint(0);

				for (int i = 0; i < 1; i = i + 1) {
					engine.addNebulaParticle(firePoint, MathUtils.getRandomPointInCircle(new Vector2f(), MathUtils.getRandomNumberInRange(55f, 65f)), MathUtils.getRandomNumberInRange(60f, 80f), 0.6f, 1.2f, 0.6f, 0.4f, new Color(169, 23, 23, 155)

					);
					engine.addHitParticle(firePoint, MathUtils.getRandomPointInCircle(new Vector2f(), MathUtils.getRandomNumberInRange(155f, 165f)), MathUtils.getRandomNumberInRange(60f, 80f), 0.2f, 0.7f, new Color(169, 57, 23, 23)

					);
				}

				for (int i = 0; i < 4; i++) {
					if (fire <= i && weapon.getChargeLevel() >= 0.2f * (i + 1f)) {
						fire += 1;
						MagicFakeBeam.spawnAdvancedFakeBeam(engine, weapon.getFirePoint(0), weapon.getRange(), weapon.getCurrAngle(), 5f, 5f, -5f, "rs_3_weave_core", "rs_3_weave_fringe", 32f, 128f, 0f, 0f, 0.1f, 0.4f, 5f, new Color(162, 22, 22, 255), new Color(221, 75, 30, 255), 50f, weapon.getDamage().getType(), 0f, weapon.getShip());
						break;
					}
				}
			} else if (!weapon.isFiring() && weapon.getCooldownRemaining() <= 0f && fire != 0) {
				fire = 0;
			}
		} else if (!weapon.isFiring() && weapon.getCooldownRemaining() <= 0f && fire != 0) {
			fire = 0;
		}

		List<BeamAPI> beams = weapon.getBeams();
		if (beams.isEmpty()) return;
		BeamAPI beam = beams.get(0);
		if (beam.getBrightness() < 1f) return;

		interval.advance(amount * 25f);
		if (interval.intervalElapsed()) {
			if (beam.getLengthPrevFrame() < 10) return;

			Vector2f loc;
			CombatEntityAPI target = findTarget(beam, beam.getWeapon());
			if (target == null) {
				loc = pickNoTargetDest(beam, beam.getWeapon());
			} else {
				loc = target.getLocation();
			}

			Vector2f from = Misc.closestPointOnSegmentToPoint(beam.getFrom(), beam.getRayEndPrevFrame(), loc);
			Vector2f to = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(from, loc));
			//to.scale(Math.max(RIFT_RANGE * 0.5f, Math.min(Misc.getDistance(from, loc), RIFT_RANGE)));
			to.scale(Math.min(Misc.getDistance(from, loc), RIFT_RANGE));
			Vector2f.add(from, to, to);

			spawnMine(beam.getSource(), to);
			//			float thickness = beam.getWidth();
			//			EmpArcEntityAPI arc = engine.spawnEmpArcVisual(from, null, to, null, thickness, beam.getFringeColor(), Color.white);
			//			arc.setCoreWidthOverride(Math.max(20f, thickness * 0.67f));
			//Global.getSoundPlayer().playSound("tachyon_lance_emp_impact", 1f, 1f, arc.getLocation(), arc.getVelocity());
		}

		if (beam.getDamageTarget() instanceof ShipAPI target) {
            boolean hitShield = target.getShield() != null && target.getShield().isWithinArc(beam.getTo());
			if (hitShield) {
				float shieldEfficiency = Math.min(1,
						target.getShield().getFluxPerPointOfDamage()
								* target.getMutableStats().getShieldDamageTakenMult().getModifiedValue()
								* target.getMutableStats().getShieldAbsorptionMult().getModifiedValue());
				float reduction = beam.getDamage().computeDamageDealt(amount) * (1 - shieldEfficiency);
                // private boolean wasZero = true;
                float ignore = 0.15f;
                float pierce = reduction * ignore;
				target.getFluxTracker().increaseFlux(pierce,true);
			}
		}
		if (beam.getBrightness() >= 0.5) {
			fireInterval.advance(amount);
			if (fireInterval.intervalElapsed())
				createVisualArc(beam, beam.getWidth());
		}

		sparkInterval.advance(amount);
		if (sparkInterval.intervalElapsed()) {   //准备生成电弧
			Vector2f start = beam.getFrom();
			Vector2f end = beam.getTo();
			//获取光束起始位置
			Vector2f p1 = MathUtils.getRandomPointOnLine(start, end);
			Vector2f p2 = MathUtils.getRandomPointOnLine(start, end);
			float dist1 = Misc.getDistance(p1, start);
			float dist2 = Misc.getDistance(p2, start);
			Vector2f closer = p1;
			Vector2f farer = p2;
			float farerDist = dist1;
			if (dist1 > dist2) {
				closer = p2;
				farer = p1;
				farerDist = dist2;               //确定光束起始位置
			}
			closer = MathUtils.getRandomPointOnCircumference(closer, beam.getWidth());
			farer = MathUtils.getPointOnCircumference(farer, beam.getWidth() * maxSpread * (farerDist / beam.getWeapon().getRange()), (float) ((Math.random() - 0.5f) * (Math.random() * 40 + 160) + beam.getWeapon().getCurrAngle()));
			//     farer = MathUtils.getRandomPointOnCircumference(farer, beam.getWidth() * maxSpread * (farerDist / beam.getWeapon().getRange()));
			engine.spawnEmpArcVisual(closer, beam.getSource(), farer, beam.getSource(), 5, new Color(169, 25, 95, 175), Color.magenta);



		}

	}

	public void createVisualArc(BeamAPI beam, float r) {
		float dist = Misc.getDistance(beam.getFrom(), beam.getRayEndPrevFrame());
		Vector2f direction = VectorUtils.getDirectionalVector(beam.getFrom(), beam.getRayEndPrevFrame());
		float gap = 100f;
		float gap_random = 50f;
		float next = gap + (1 - 0.5f) * gap_random;
		float currentDist = 0;
		Vector2f last = new Vector2f(beam.getFrom());
		while ((dist - currentDist) > next) {
			currentDist += next;
			Vector2f n = new Vector2f(direction);
			n.scale(currentDist);
			n = Vector2f.add(beam.getFrom(), n, n);
			n = Misc.getPointWithinRadiusUniform(n, r, r * 1.5f, new Random());
			EmpArcEntityAPI arc = Global.getCombatEngine().spawnEmpArcVisual(last, beam.getSource(), n,
					beam.getSource(), beam.getWidth() / 3f + beam.getWidth() / 8f * (float) Math.random(),
					beam.getFringeColor(), beam.getCoreColor());
			arc.setSingleFlickerMode();
			last = new Vector2f(n);
			next = gap + (1 - 0.5f) * gap_random;
		}
		EmpArcEntityAPI arc = Global.getCombatEngine().spawnEmpArcVisual(last, beam.getSource(),
				beam.getRayEndPrevFrame(), beam.getSource(),
				beam.getWidth() / 3f + beam.getWidth() / 8f * (float) Math.random(), beam.getFringeColor(),
				beam.getCoreColor());
		arc.setSingleFlickerMode();
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

	public Vector2f pickNoTargetDest(BeamAPI beam, WeaponAPI weapon) {
		Vector2f from = beam.getFrom();
		Vector2f to = beam.getRayEndPrevFrame();
		float length = beam.getLengthPrevFrame();

		float f = 0.65f + (float)Math.random() * 0.35f;
		Vector2f loc = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(from, to));
		loc.scale(length * f);
		Vector2f.add(from, loc, loc);

		return Misc.getPointWithinRadius(loc, RIFT_RANGE);
	}

	public CombatEntityAPI findTarget(BeamAPI beam, WeaponAPI weapon) {
		Vector2f to = beam.getRayEndPrevFrame();

		Iterator<Object> iter = Global.getCombatEngine().getAllObjectGrid().getCheckIterator(to, RIFT_RANGE * 3f, RIFT_RANGE * 8f);
		int owner = weapon.getShip().getOwner();
		WeightedRandomPicker<CombatEntityAPI> picker = new WeightedRandomPicker<>();
		while (iter.hasNext()) {
			Object o = iter.next();
			if (!(o instanceof MissileAPI) && !(o instanceof ShipAPI)) continue;
			CombatEntityAPI other = (CombatEntityAPI)o;
			if (other.getOwner() == owner) continue;
			if (other instanceof ShipAPI ship) {
                if (!ship.isFighter() && !ship.isDrone()) continue;
			}

			float radius = Misc.getTargetingRadius(to, other, false);
			Vector2f p = Misc.closestPointOnSegmentToPoint(beam.getFrom(), beam.getRayEndPrevFrame(), other.getLocation());
			float dist = Misc.getDistance(p, other.getLocation()) - radius;
			if (dist > TARGET_RANGE) continue;

			picker.add(other);

		}
		return picker.pick();
	}

}