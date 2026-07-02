package data.hullmods.Installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static data.hullmods.Installable.CR_ShieldOscillating.CR_ShieldOscillatingListener.ARC_THRESHOLD;

public class CR_ShieldOscillating extends BaseHullMod {

	public static final float BEAM_DPS_DURATION = 0.1f;
	public static final float BEAM_DPS_DURATION_CONPUTE_MULT = 10f;
	public static final float BEAM_REDUCE_PERCENT = 170f;
	public static final float SHIELD_UPKEEP = 1f;

	public static final float E_DAMAGE = 0.05f;
	public static final float EMP_DAMAGE = 0.95f;

	public static final float Kinetic_BOUNS = 0.2f;
	public static final float Energy_BOUNS = 0.1f;
	public static final float SoftFlux_BOUNS = 0.15f;

	public static final float MAX_DAMAGE_REDUCE_PERCENT = 60f;
	public static final float DAMAGE_REDUCE_TEST1 = 75f;
	public static final float DAMAGE_REDUCE_TEST2 = 250f;
	public static final float DAMAGE_REDUCE_TEST3 = 200f;
	public static final float DAMAGE_REDUCE_TEST4 = 500f;
	public static final float DAMAGE_THRESHOLD_TO_SPAWN_EFFECT = 40f;

	public static float SMOD_EFFECT_PERCENT = 25f;


	@Override
	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
		//stats.getBeamDamageTakenMult().modifyMult(id, 1f - BEAM_REDUCE_PERCENT * 0.01f);

		//stats.getShieldUpkeepMult().modifyMult(id, 1 + SHIELD_UPKEEP);

		stats.getKineticShieldDamageTakenMult().modifyPercent(id , Kinetic_BOUNS);
		stats.getEnergyShieldDamageTakenMult().modifyPercent(id , Energy_BOUNS);
		stats.getShieldSoftFluxConversion().modifyPercent(id ,SoftFlux_BOUNS);


		boolean sMod = isSMod(stats);
		if (sMod) {
			stats.getShieldTurnRateMult().modifyMult(id, 1f + 0.01f * SMOD_EFFECT_PERCENT);
			stats.getShieldUnfoldRateMult().modifyMult(id, 1f + 0.01f * SMOD_EFFECT_PERCENT);
		}

	}


	@Override
	public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
		return !isForModSpec;
	}
	@Override
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) return (int)Kinetic_BOUNS*100 + "%";
		if (index == 1) return (int)Energy_BOUNS*100 + "%";
		if (index == 2) return (int)SoftFlux_BOUNS*100 + "%";
		if (index == 3) return (int)CR_ShieldOscillatingListener.getReduceInPercent(DAMAGE_REDUCE_TEST2) + "%";
		if (index == 4) return (int)DAMAGE_REDUCE_TEST4 + "";
		if (index == 5) return (int)CR_ShieldOscillatingListener.getReduceInPercent(DAMAGE_REDUCE_TEST4) + "%";
		if (index == 7) return (int)(E_DAMAGE * 100) + "%";
		if (index == 8) return (int)(EMP_DAMAGE * 100) + "%";

		return null;
	}
	public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
		float pad = 10f;
		float padS = 2f;

		// 计算示例减伤值用于提示
		int lowDmgReduce = (int) CR_ShieldOscillatingListener.getReduceInPercent(DAMAGE_REDUCE_TEST1);
		int midDmgReduce = (int) CR_ShieldOscillatingListener.getReduceInPercent(DAMAGE_REDUCE_TEST2);
		int highDmgReduce = (int) CR_ShieldOscillatingListener.getReduceInPercent(DAMAGE_REDUCE_TEST4);
		int arcEnergy = (int)(E_DAMAGE * 100);
		int arcEMP = (int)(EMP_DAMAGE * 100);

		tooltip.addPara("The shield system energy register can buffer the shield from ground impact, but the register needs to release energy from time to time, and this part of the energy can only be borne by the ship body.", Misc.getHighlightColor(), pad);

		tooltip.addSectionHeading("dynamic damage response", Alignment.MID, pad);
		tooltip.addPara("When the shield is attacked, the damage is dynamically reduced based on the size of the single damage:" +
						"The lower the damage, the higher the damage reduction ratio, up to %s; the higher the damage, the lower the damage reduction, and it will no longer take effect when the converted damage reduction is less than 1%%." +
						"For example, the single-shot damage reduction is about %s%% when %s, about %s%% when %s, and about %s%% when %s.",
				padS, Misc.getHighlightColor(),
				(int)MAX_DAMAGE_REDUCE_PERCENT + "%",
				(int)DAMAGE_REDUCE_TEST1 + "", lowDmgReduce + "%",
				(int)DAMAGE_REDUCE_TEST2 + "", midDmgReduce + "%",
				(int)DAMAGE_REDUCE_TEST4 + "", highDmgReduce + "%");

		tooltip.addSectionHeading("resonant arc", Alignment.MID, pad);
		tooltip.addPara("Each successfully reduced damage will be absorbed by the shield capacitor after accumulating %s points." +
						"The arc released on the ship causes %s energy damage and %s EMP damage.",
				padS, Misc.getHighlightColor(),
				(int) ARC_THRESHOLD + "",
				arcEnergy + "%", arcEMP + "%");
	}

	public String getSModDescriptionParam(int index, ShipAPI.HullSize hullSize) {
		if (index == 0) return (int)SMOD_EFFECT_PERCENT + "%";
		return null;
	}

	@Override
	public void addSModEffectSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec, boolean isForBuildInList) {
		float pad = 10f;
		float padS = 2f;
		// 可在此添加内建效果说明
	}

	@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
		ship.addListener(new CR_ShieldOscillatingListener());
	}

	public static class CR_ShieldOscillatingListener implements DamageTakenModifier {

		public static final String id = "CR_ShieldOscillating1";
		public static final float ARC_THRESHOLD = 300f;

		public static float getReduceInPercent(float damageAmount) {
			return (float)Math.exp(-0.003f * damageAmount + 4.4f);
		}

		private float storagedDamage = 0f;

		@Override
		public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {

			if (!shieldHit) return null;

			float damageAmount = 0f;
			float damageForReduce = 0f;
			if (param instanceof BeamAPI) {
				damageAmount = damage.computeDamageDealt(BEAM_DPS_DURATION);
				damageForReduce = damageAmount * 10f;
			} else if (param != null) {
				damageAmount = damage.getDamage();
				damageForReduce = damageAmount;
			}

			float y = getReduceInPercent(damageForReduce);
			if (y > MAX_DAMAGE_REDUCE_PERCENT) y = MAX_DAMAGE_REDUCE_PERCENT;
			if (y < 1f) y = 0f;

			if (y > 0f) {

				ShipAPI ship = (ShipAPI)target;
				if (y > DAMAGE_THRESHOLD_TO_SPAWN_EFFECT) {
					new CR_ShieldOscillatingVisual(ship, VectorUtils.getAngle(ship.getShieldCenterEvenIfNoShield(), point));
				}

				float reduceMult = 1f - y * 0.01f;
				damage.getModifier().modifyMult(id, reduceMult);

				float reduced = damageAmount * (1f - reduceMult);
				storagedDamage += reduced;

				if (storagedDamage >= ARC_THRESHOLD) {
					// 随机瘫痪一门可用武器
					List<WeaponAPI> weapons = ship.getAllWeapons();
					List<WeaponAPI> eligible = new ArrayList<>();
					for (WeaponAPI w : weapons) {
						if (!w.isDecorative() && !w.isDisabled() && !w.isPermanentlyDisabled()) {
							eligible.add(w);
						}
					}
					if (!eligible.isEmpty()) {
						WeaponAPI toDisable = eligible.get((int) (Math.random() * eligible.size()));
						toDisable.disable(); // 暂时禁用，模拟 EMP 瘫痪
					}
					if (storagedDamage >= ARC_THRESHOLD){
						float facing = ship.getShield().getFacing();
					facing += MathUtils.getRandomNumberInRange(ship.getShield().getActiveArc() * -0.5f, ship.getShield().getActiveArc() * 0.5f);

					float range = ship.getShield().getRadius() * MathUtils.getRandomNumberInRange(0.5f, 0.9f);
					Vector2f location = MathUtils.getPoint(ship.getShieldCenterEvenIfNoShield(), range, facing);
					Global.getCombatEngine().spawnEmpArcPierceShields(ship, location, null, ship, DamageType.ENERGY, (E_DAMAGE * storagedDamage), (EMP_DAMAGE * storagedDamage) , 999999999f, null, 3f, ship.getShield().getInnerColor(), ship.getShield().getRingColor());

				}
					storagedDamage = 0f;
				}

				return id;
			}
			return null;
		}
	}

	public boolean isApplicableToShip(ShipAPI ship) {
		if (ship == null) return false;
		if (ship.getShield() == null) return false;
		if(ship.getVariant().hasHullMod("CR_EngineRegularBoost")) return false;
		if(ship.getVariant().hasHullMod("CR_StructureUpgrading")) return false;
		if(ship.getVariant().hasHullMod("CR_ChargingRing")) return false;
		return ship.getVariant().hasHullMod("CrusadersCore");
	}

	@Override
	public String getUnapplicableReason(ShipAPI ship) {
		if (ship == null) return "ship does not exist";
		if (ship.getShield() == null) return "The ship has no shields";
		if (!ship.getVariant().hasHullMod("CrusadersCore")) return "Requires Crusader Core";
		return null;
	}

	public static class CR_ShieldOscillatingVisual extends BaseCombatLayeredRenderingPlugin {

		public static final float MAX_EFFECT_TIME = 1.6f;
		public static final float EFFECT_TIME = 0.02f;
		public static final float SIZE_FACTOR = 0.004f;

		private CombatEngineAPI engine;
		private final ShipAPI ship;
		private final float angle;
		private final SpriteAPI sprite;

		private float timer = MAX_EFFECT_TIME;
		private boolean done;

		public CR_ShieldOscillatingVisual(ShipAPI ship, float angle) {
			this.ship = ship;
			this.angle = angle;
			this.sprite = Global.getSettings().getSprite("fx", "BLADE_WAVE");
			this.done = false;

			sprite.setAngle(angle - MathUtils.getRandomNumberInRange(87f, 93f));
			sprite.setSize(0.50f, 0.4f);
			sprite.setAlphaMult(0.2f);
			sprite.setColor(ship.getShield().getRingColor());

			Global.getCombatEngine().addLayeredRenderingPlugin(this);
		}

		@Override
		public void init(CombatEntityAPI entity) {
			super.init(entity);
			engine = Global.getCombatEngine();
		}

		@Override
		public void advance(float amount) {
			if (engine == null || engine.isPaused()) return;

			entity.getLocation().set(ship.getShieldCenterEvenIfNoShield());

			timer -= amount;
			if (timer <= 0f) {
				done = true;
				return;
			}

			float size = timer * ship.getShieldRadiusEvenIfNoShield() * SIZE_FACTOR;
			sprite.setWidth(size * 92f);
			sprite.setHeight(size * 10f);
		}

		@Override
		public void render(CombatEngineLayers layer, ViewportAPI viewport) {

			if (layer == CombatEngineLayers.ABOVE_PARTICLES_LOWER) {

				float alphaMult = viewport.getAlphaMult();
				alphaMult *= Math.max((timer / MAX_EFFECT_TIME) * 3f - 2f, 0f);
				sprite.setAlphaMult(alphaMult);

				float radius = ship.getShieldRadiusEvenIfNoShield();
				radius *= timer / MAX_EFFECT_TIME;
				Vector2f point = MathUtils.getPoint(entity.getLocation(), radius, angle);
				sprite.renderAtCenter(point.getX(), point.getY());
			}
		}

		@Override
		public EnumSet<CombatEngineLayers> getActiveLayers() {
			return EnumSet.of(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
		}

		@Override
		public float getRenderRadius() {
			return ship.getShieldRadiusEvenIfNoShield() + 1000f;
		}

		@Override
		public boolean isExpired() {
			return done;
		}
	}
}