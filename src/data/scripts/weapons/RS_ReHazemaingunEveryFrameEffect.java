package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicFakeBeam;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import static data.scripts.weapons.Moci_VOW_Magnum_EveryFrame.getBarrelCount;

public class RS_ReHazemaingunEveryFrameEffect implements EveryFrameWeaponEffectPlugin {

	public static final String ID = "RS_ReHazemaingunEveryFrameEffect";
	private static final String FIRE_SOUND_ID = "Moci_vow_Magnum_01";

	public static final float TARGET_RANGE = 10f;
	public static final float RIFT_RANGE = 5f;

	private int currentBarrel = 0;

	private final IntervalUtil fireInterval = new IntervalUtil(0.05f, 0.1f);
	private int fire = 0;
	protected IntervalUtil interval = new IntervalUtil(0.05f, 0.1f);
	IntervalUtil sparkInterval = new IntervalUtil(0.05f, 0.1f);
	public static final float maxSpread = 15;

	// ==================== 新增：散热烟雾参数 ====================
	private static final boolean ENABLE_HEAT_SMOKE = true;
	private static final float SMOKE_INTERVAL = 0.05f;
	private static final float SMOKE_SPEED = 25f;
	private static final float SMOKE_SIZE = 15f;
	private static final float SMOKE_SIZE_MULT = 2.5f;
	private static final float SMOKE_DURATION = 3f;
	private static final Color SMOKE_COLOR = new Color(120, 120, 120, 100);
	private float smokeTimer = 0f;
	private float cooldownSmokeIntensity = 0f;
	private boolean wasFiringAtPeak = false;

	// ==================== 新增：光束环绕电弧参数 ====================
	private static final boolean ENABLE_SURROUND_ARCS = true;
	private static final float SURROUND_ARC_FRAME_SPAWN_CHANCE = 0.15f;
	private static final int SURROUND_ARCS_PER_FRAME = 1;
	private static final float SURROUND_ARC_SECTOR_HALF_ANGLE = 15f;
	private static final float SURROUND_ARC_LENGTH_MIN = 30f;
	private static final float SURROUND_ARC_LENGTH_MAX = 45f;
	private static final float SURROUND_ARC_TANGENT_ANGLE_JITTER = 8f;
	private static final float SURROUND_ARC_ENDPOINT_SPEED_MIN = 300f;
	private static final float SURROUND_ARC_ENDPOINT_SPEED_MAX = 500f;
	private static final float SURROUND_ARC_RADIUS_MIN = 30f;
	private static final float SURROUND_ARC_RADIUS_END_MARGIN = 10f;
	private static final float SURROUND_ARC_RADIUS_JITTER = 4f;
	private static final float SURROUND_ARC_LIFETIME_MIN = 0.2f;
	private static final float SURROUND_ARC_LIFETIME_MAX = 0.35f;
	private static final float SURROUND_ARC_THICKNESS_MIN = 1f;
	private static final float SURROUND_ARC_THICKNESS_MAX = 3f;
	private static final Color SURROUND_ARC_FRINGE_COLOR = new Color(169, 25, 95, 175);
	private static final Color SURROUND_ARC_CORE_COLOR = new Color(195, 35, 75, 125);

	private final List<MovingArcData> activeSurroundArcs = new ArrayList<>();

	private boolean hasFiredThisCharge = false;

	public RS_ReHazemaingunEveryFrameEffect() {
		interval.setElapsed((float) Math.random() * interval.getIntervalDuration());
	}

	@Override
	public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
		if (engine.isPaused()) return;
		if (weapon.getShip() == null) return;

		float chargeLevel = weapon.getChargeLevel();
		boolean isFiring = weapon.isFiring();

		if (chargeLevel >= 1f && isFiring && !hasFiredThisCharge) {
			// 每轮蓄力只在真正开火瞬间播放一次音效
			Global.getSoundPlayer().playSound(
					FIRE_SOUND_ID,
					1f,
					1f,
					weapon.getLocation(),
					weapon.getShip().getVelocity());
			hasFiredThisCharge = true;
		}

		if (hasFiredThisCharge && (chargeLevel <= 0f || !isFiring)) {
			hasFiredThisCharge = false;
			currentBarrel++;

			int barrelCount = getBarrelCount(weapon);
			if (currentBarrel >= barrelCount) {
				currentBarrel = 0;
			}
		}

		// 屏幕外跳过，并清理动态电弧
		if (!engine.getViewport().isNearViewport(weapon.getLocation(), 400f)) {
			activeSurroundArcs.clear();
			return;
		}

		// ---- 新增：光束环绕电弧更新 ----
		if (ENABLE_SURROUND_ARCS) {
			updateSurroundArcs(engine, weapon, amount);
		}

		// ---- 开火/冷却状态管理 ----
		boolean atPeak = (chargeLevel >= 1f && isFiring);
		boolean justFinishedFiring = wasFiringAtPeak && chargeLevel < 1f;
		if (justFinishedFiring) {
			cooldownSmokeIntensity = 1f;
		}
		wasFiringAtPeak = atPeak;

		// ---- 冷却烟雾衰减 ----
		if (!isFiring && cooldownSmokeIntensity > 0f) {
			cooldownSmokeIntensity -= amount * 1.5f;
			if (cooldownSmokeIntensity < 0f) cooldownSmokeIntensity = 0f;
		}

		// ---- 充能阶段粒子（保留原有） ----
		if (chargeLevel >= 0.09f && chargeLevel <= 0.11f && weapon.getCooldownRemaining() <= 0f) {
			Vector2f firePoint = weapon.getFirePoint(0);
			for (int i = 0; i < 6; i++) {
				engine.addNebulaParticle(firePoint,
						MathUtils.getRandomPointInCircle(new Vector2f(), MathUtils.getRandomNumberInRange(55f, 65f)),
						MathUtils.getRandomNumberInRange(60f, 80f), 0.6f, 2.2f, 0.6f, 0.3f,
						new Color(169, 25, 95, 175));
			}
		}

		if (chargeLevel >= 1.0f) {
			Vector2f firePoint = weapon.getFirePoint(0);
			for (int i = 0; i < 1; i++) {
				engine.addNegativeSwirlyNebulaParticle(firePoint,
						MathUtils.getRandomPointInCircle(new Vector2f(), MathUtils.getRandomNumberInRange(15f, 158f)),
						MathUtils.getRandomNumberInRange(0f, 43f), 1.6f, 0.2f, 0.1f, 1.0f,
						new Color(255, 55, 25, 225));
			}
		}

		// ---- 开火主阶段粒子 + 烟雾 + MagicFakeBeam（保留原有） ----
		if (isFiring && weapon.getCooldownRemaining() <= 0f) {
			Vector2f firePoint = weapon.getFirePoint(0);

			// 原有粒子
			for (int i = 0; i < 1; i++) {
				engine.addNebulaParticle(firePoint,
						MathUtils.getRandomPointInCircle(new Vector2f(), MathUtils.getRandomNumberInRange(55f, 65f)),
						MathUtils.getRandomNumberInRange(60f, 80f), 0.6f, 1.2f, 0.6f, 0.4f,
						new Color(169, 23, 23, 155));
				engine.addHitParticle(firePoint,
						MathUtils.getRandomPointInCircle(new Vector2f(), MathUtils.getRandomNumberInRange(155f, 165f)),
						MathUtils.getRandomNumberInRange(60f, 80f), 0.2f, 0.7f,
						new Color(169, 57, 23, 23));
			}

			// 原有 MagicFakeBeam
			for (int i = 0; i < 4; i++) {
				if (fire <= i && weapon.getChargeLevel() >= 0.2f * (i + 1f)) {
					fire += 1;
					MagicFakeBeam.spawnAdvancedFakeBeam(engine, weapon.getFirePoint(0), weapon.getRange(),
							weapon.getCurrAngle(), 5f, 5f, -5f, "rs_3_weave_core", "rs_3_weave_fringe",
							32f, 128f, 0f, 0f, 0.1f, 0.4f, 5f,
							new Color(162, 22, 22, 255), new Color(221, 75, 30, 255),
							50f, weapon.getDamage().getType(), 0f, weapon.getShip());
					break;
				}
			}

			// 新增：开火主阶段枪口散热烟雾（强度 1.0）
			if (ENABLE_HEAT_SMOKE) {
				smokeTimer += amount;
				if (smokeTimer >= SMOKE_INTERVAL) {
					smokeTimer -= SMOKE_INTERVAL;
					handleMuzzleSmokeEffects(engine, weapon, firePoint, 1f);
				}
			}
		} else {
			// 非开火主阶段时，处理冷却烟雾
			if (ENABLE_HEAT_SMOKE && cooldownSmokeIntensity > 0f) {
				smokeTimer += amount;
				if (smokeTimer >= SMOKE_INTERVAL) {
					smokeTimer -= SMOKE_INTERVAL;
					handleMuzzleSmokeEffects(engine, weapon, weapon.getFirePoint(0), cooldownSmokeIntensity);
				}
			} else {
				smokeTimer = 0f;
			}

			// 重置 fire 计数
			if (!isFiring && weapon.getCooldownRemaining() <= 0f && fire != 0) {
				fire = 0;
			}
		}

		// ---- 光束效果（保留原有） ----
		List<BeamAPI> beams = weapon.getBeams();
		if (!beams.isEmpty()) {
			BeamAPI beam = beams.get(0);
			if (beam.getBrightness() >= 1f) {
				interval.advance(amount * 25f);
				if (interval.intervalElapsed() && beam.getLengthPrevFrame() >= 10) {
					Vector2f loc;
					CombatEntityAPI target = findTarget(beam, beam.getWeapon());
					if (target == null) {
						loc = pickNoTargetDest(beam, beam.getWeapon());
					} else {
						loc = target.getLocation();
					}
					Vector2f from = Misc.closestPointOnSegmentToPoint(beam.getFrom(), beam.getRayEndPrevFrame(), loc);
					Vector2f to = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(from, loc));
					to.scale(Math.min(Misc.getDistance(from, loc), RIFT_RANGE));
					Vector2f.add(from, to, to);
				}

				if (beam.getDamageTarget() instanceof ShipAPI target) {
					boolean hitShield = target.getShield() != null && target.getShield().isWithinArc(beam.getTo());
					if (hitShield) {
						float shieldEfficiency = Math.min(1,
								target.getShield().getFluxPerPointOfDamage()
										* target.getMutableStats().getShieldDamageTakenMult().getModifiedValue()
										* target.getMutableStats().getShieldAbsorptionMult().getModifiedValue());
						float reduction = beam.getDamage().computeDamageDealt(amount) * (1 - shieldEfficiency);
						float ignore = 0.15f;
						float pierce = reduction * ignore;
						target.getFluxTracker().increaseFlux(pierce, true);
					}
				}
				if (beam.getBrightness() >= 0.5) {
					fireInterval.advance(amount);
					if (fireInterval.intervalElapsed())
						createVisualArc(beam, beam.getWidth());
				}

				sparkInterval.advance(amount);
				if (sparkInterval.intervalElapsed()) {
					Vector2f start = beam.getFrom();
					Vector2f end = beam.getTo();
					Vector2f p1 = MathUtils.getRandomPointOnLine(start, end);
					Vector2f p2 = MathUtils.getRandomPointOnLine(start, end);
					float dist1 = Misc.getDistance(p1, start);
					float dist2 = Misc.getDistance(p2, start);
					Vector2f closer = p1;
					Vector2f farer = p2;
					if (dist1 > dist2) {
						closer = p2;
						farer = p1;
					}
					float farerDist = Math.max(dist1, dist2);
					closer = MathUtils.getRandomPointOnCircumference(closer, beam.getWidth());
					farer = MathUtils.getPointOnCircumference(farer, beam.getWidth() * maxSpread * (farerDist / beam.getWeapon().getRange()),
							(float) ((Math.random() - 0.5f) * (Math.random() * 40 + 160) + beam.getWeapon().getCurrAngle()));
					engine.spawnEmpArcVisual(closer, beam.getSource(), farer, beam.getSource(), 5,
							new Color(169, 25, 95, 175), Color.magenta);
				}
			}
		}
	}

	// ---- 新增：散热烟雾生成 ----
	private void handleMuzzleSmokeEffects(CombatEngineAPI engine, WeaponAPI weapon,
										  Vector2f muzzleLocation, float intensity) {
		float weaponAngle = weapon.getCurrAngle();
		int count = Math.max(1, Math.round(2f * intensity));
		float sizeScale = 0.5f + 0.5f * intensity;

		for (int i = 0; i < count; i++) {
			Vector2f smokeLoc = new Vector2f(muzzleLocation);
			smokeLoc.x += MathUtils.getRandomNumberInRange(-5f, 5f);
			smokeLoc.y += MathUtils.getRandomNumberInRange(-5f, 5f);

			float smokeAngle = weaponAngle + 180f + MathUtils.getRandomNumberInRange(-30f, 30f);
			Vector2f smokeVel = VectorUtils.getDirectionalVector(smokeLoc,
					MathUtils.getPointOnCircumference(smokeLoc, 10f, smokeAngle));
			smokeVel.scale(SMOKE_SPEED);
			Vector2f.add(weapon.getShip().getVelocity(), smokeVel, smokeVel);

			engine.addNebulaSmokeParticle(
					smokeLoc,
					smokeVel,
					SMOKE_SIZE * sizeScale,
					SMOKE_SIZE_MULT,
					0.1f,
					0.1f,
					SMOKE_DURATION,
					SMOKE_COLOR);
		}
	}

	// ---- 新增：环绕电弧系统 ----
	private void updateSurroundArcs(CombatEngineAPI engine, WeaponAPI weapon, float amount) {
		List<BeamAPI> activeBeams = getActiveBeams(weapon);
		if (activeBeams.isEmpty()) {
			activeSurroundArcs.clear();
			return;
		}

		if (Math.random() < SURROUND_ARC_FRAME_SPAWN_CHANCE) {
			for (BeamAPI beam : activeBeams) {
				float beamLength = MathUtils.getDistance(beam.getFrom(), beam.getTo());
				if (beamLength <= 1f) continue;
				for (int i = 0; i < SURROUND_ARCS_PER_FRAME; i++) {
					activeSurroundArcs.add(createSurroundArc(beam, beamLength));
				}
			}
		}

		for (int i = activeSurroundArcs.size() - 1; i >= 0; i--) {
			MovingArcData arcData = activeSurroundArcs.get(i);
			arcData.remainingLifetime -= amount;
			if (arcData.remainingLifetime <= 0f) {
				activeSurroundArcs.remove(i);
				continue;
			}

			arcData.start.x += arcData.startVelocity.x * amount;
			arcData.start.y += arcData.startVelocity.y * amount;
			arcData.end.x += arcData.endVelocity.x * amount;
			arcData.end.y += arcData.endVelocity.y * amount;

			if (!engine.getViewport().isNearViewport(arcData.start, 150f)
					&& !engine.getViewport().isNearViewport(arcData.end, 150f)) {
				continue;
			}

//			engine.spawnEmpArcVisual(
//					arcData.start, null,
//					arcData.end, null,
//					arcData.thickness,
//					SURROUND_ARC_FRINGE_COLOR,
//					SURROUND_ARC_CORE_COLOR);
		}
	}

	private List<BeamAPI> getActiveBeams(WeaponAPI weapon) {
		List<BeamAPI> result = new ArrayList<>();
		for (BeamAPI beam : weapon.getBeams()) {
			if (beam == null || !weapon.isFiring()) continue;
			if (beam.getBrightness() <= 0.1f) continue;
			if (MathUtils.getDistanceSquared(beam.getFrom(), beam.getTo()) <= 1f) continue;
			result.add(beam);
		}
		return result;
	}

	private MovingArcData createSurroundArc(BeamAPI beam, float beamLength) {
		Vector2f beamFrom = beam.getFrom();
		Vector2f beamTo = beam.getTo();
		float beamFacing = (float) Math.toDegrees(Math.atan2(beamTo.y - beamFrom.y, beamTo.x - beamFrom.x));

		float arcLength = MathUtils.getRandomNumberInRange(SURROUND_ARC_LENGTH_MIN, SURROUND_ARC_LENGTH_MAX);
		float maxRadius = Math.max(SURROUND_ARC_RADIUS_MIN + 1f, beamLength - SURROUND_ARC_RADIUS_END_MARGIN);
		float centerRadius = MathUtils.getRandomNumberInRange(SURROUND_ARC_RADIUS_MIN, maxRadius);
		float centerAngle = beamFacing + MathUtils.getRandomNumberInRange(
				-SURROUND_ARC_SECTOR_HALF_ANGLE, SURROUND_ARC_SECTOR_HALF_ANGLE);

		float clampedArcLength = Math.min(arcLength, centerRadius * 1.95f);
		float halfThetaDeg = (float) Math.toDegrees(Math.asin(Math.min(1f, clampedArcLength / (2f * centerRadius))));
		halfThetaDeg += MathUtils.getRandomNumberInRange(-SURROUND_ARC_TANGENT_ANGLE_JITTER, SURROUND_ARC_TANGENT_ANGLE_JITTER);

		float startRadius = centerRadius + MathUtils.getRandomNumberInRange(-SURROUND_ARC_RADIUS_JITTER, SURROUND_ARC_RADIUS_JITTER);
		float endRadius = centerRadius + MathUtils.getRandomNumberInRange(-SURROUND_ARC_RADIUS_JITTER, SURROUND_ARC_RADIUS_JITTER);
		startRadius = Math.max(SURROUND_ARC_RADIUS_MIN, startRadius);
		endRadius = Math.max(SURROUND_ARC_RADIUS_MIN, endRadius);

		Vector2f start = MathUtils.getPointOnCircumference(beamFrom, startRadius, centerAngle - halfThetaDeg);
		Vector2f end = MathUtils.getPointOnCircumference(beamFrom, endRadius, centerAngle + halfThetaDeg);

		float speed = MathUtils.getRandomNumberInRange(SURROUND_ARC_ENDPOINT_SPEED_MIN, SURROUND_ARC_ENDPOINT_SPEED_MAX);
		Vector2f startVelocity = MathUtils.getPointOnCircumference(new Vector2f(), speed, beamFacing);
		Vector2f endVelocity = MathUtils.getPointOnCircumference(new Vector2f(), speed, beamFacing);

		float lifetime = MathUtils.getRandomNumberInRange(SURROUND_ARC_LIFETIME_MIN, SURROUND_ARC_LIFETIME_MAX);
		float thickness = MathUtils.getRandomNumberInRange(SURROUND_ARC_THICKNESS_MIN, SURROUND_ARC_THICKNESS_MAX);
		return new MovingArcData(start, end, startVelocity, endVelocity, lifetime, thickness);
	}

	private static class MovingArcData {
		private final Vector2f start;
		private final Vector2f end;
		private final Vector2f startVelocity;
		private final Vector2f endVelocity;
		private float remainingLifetime;
		private final float thickness;

		private MovingArcData(Vector2f start, Vector2f end,
							  Vector2f startVelocity, Vector2f endVelocity,
							  float remainingLifetime, float thickness) {
			this.start = start;
			this.end = end;
			this.startVelocity = startVelocity;
			this.endVelocity = endVelocity;
			this.remainingLifetime = remainingLifetime;
			this.thickness = thickness;
		}
	}

	// ---- 以下为保留的原有方法，未做修改 ----

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

	public Vector2f pickNoTargetDest(BeamAPI beam, WeaponAPI weapon) {
		Vector2f from = beam.getFrom();
		Vector2f to = beam.getRayEndPrevFrame();
		float length = beam.getLengthPrevFrame();

		float f = 0.65f + (float) Math.random() * 0.35f;
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
			CombatEntityAPI other = (CombatEntityAPI) o;
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