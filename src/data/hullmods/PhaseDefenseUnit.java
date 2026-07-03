package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import java.util.*;

public class PhaseDefenseUnit extends BaseHullMod {
    // 防御场常量参数
    private static final float MIN_ABSORB_CHANCE = 0.15f;
    private static final float MAX_ABSORB_CHANCE = 0.6f;
    private static final float FIELD_RADIUS_FACTOR = 1.5f;
    private static final float FLUX_PER_DAMAGE = 1.25f;
    private static final float PARTICLE_SIZE = 4.0f;
    private static final float PARTICLE_DURATION = 0.4f;
    private static final Color PARTICLE_COLOR = new Color(175, 15, 15, 175);

    private static final Set<String> BLOCKED_OTHER = new HashSet<>();
    private static final Set<String> BLOCKED_OMNI = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER_PLAYER_ONLY = new HashSet<>();
    private static final String ERROR = "IncompatibleHullmodWarning";
    private float check = 0.0F;

    // 静态存储：舰船ID -> 最后吸收数据
    private static final Map<String, AbsorptionData> lastAbsorptionData = new HashMap<>();

    // 静态内部类存储吸收数据
    public static class AbsorptionData {
        public final float damage;
        public static float fluxToAdd; // 注意：这里应该是非静态的

        public AbsorptionData(float damage, float fluxToAdd) {
            this.damage = damage;
            AbsorptionData.fluxToAdd = fluxToAdd;
        }
    }

    // 提供给其他类访问的静态方法
    public static AbsorptionData getLastAbsorptionData(String shipId) {
        return lastAbsorptionData.get(shipId);
    }

    private static class DefenseData {
        Map<DamagingProjectileAPI, Boolean> processedProjectiles = new HashMap<>();
        float currentChance = MIN_ABSORB_CHANCE;
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        return ship.getVariant().getHullMods().contains("CrusadersCore")&&
        !ship.getVariant().getHullMods().contains("WeaponOverLoad") &&
                !ship.getVariant().getHullMods().contains("PolariphaseDrive");
    }

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getFluxDissipation().modifyMult(id, 0.75f);
        stats.getFluxCapacity().modifyMult(id, 1.15f);
        stats.getShieldDamageTakenMult().modifyMult(id, 0.9f);
        stats.getHullDamageTakenMult().modifyMult(id, 0.9f);
        stats.getDynamic().getStat(Stats.SHIELD_PIERCED_MULT).modifyMult(id, 0.75f);
        stats.getEmpDamageTakenMult().modifyMult(id, 0.8f);
        stats.getVentRateMult().modifyMult(id, 1.25f);
        stats.getMaxSpeed().modifyMult(id, 0.6f);
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused() || !ship.isAlive()) return;
        if(ship.getFluxTracker().isVenting()) return;
        if(ship.getFluxTracker().getFluxLevel() >= 1f) return;

        DefenseData data = (DefenseData) ship.getCustomData().get("PhaseDefenseData");
        if (data == null) {
            data = new DefenseData();
            ship.getCustomData().put("PhaseDefenseData", data);
        }
        // 移除了清空processedProjectiles的逻辑

        float fluxLevel = ship.getFluxTracker().getFluxLevel();
        data.currentChance = MAX_ABSORB_CHANCE - (MAX_ABSORB_CHANCE - MIN_ABSORB_CHANCE) * fluxLevel;

        if (engine.getPlayerShip() == ship) {
            engine.maintainStatusForPlayerShip(
                    "PHASE_DEFENSE_STATUS",
                    "graphics/icons/hullsys/fortress_shield.png",
                    "phase defense unit",
                    "Absorption probability:" + (int)(data.currentChance * 100) + "%",
                    false
            );
        }

        float shipRadius = ship.getCollisionRadius();
        float fieldRadius = shipRadius * FIELD_RADIUS_FACTOR;
        Vector2f shipLocation = ship.getLocation();

        Iterator<Object> iter = engine.getAllObjectGrid().getCheckIterator(
                shipLocation, fieldRadius * 2f, fieldRadius * 2f);

        while (iter.hasNext()) {
            Object obj = iter.next();
            if (!(obj instanceof DamagingProjectileAPI proj)) continue;

            // 修复1：添加射弹有效性检查
            if (!engine.isEntityInPlay(proj)) continue;

            // 修复2：优化过滤条件
            if (proj.didDamage() ||
                    proj.getOwner() == ship.getOwner() ||
                    data.processedProjectiles.containsKey(proj)) {
                continue;
            }

            if (MathUtils.getDistance(proj, ship) > fieldRadius) continue;

            // 标记为已处理
            data.processedProjectiles.put(proj, true);

            // 修复3：添加射弹存活检查
            if (!engine.isEntityInPlay(proj)) continue;

            if (Math.random() < data.currentChance) {
                absorbProjectile(engine, ship, proj);
            }
        }
    }

    private void absorbProjectile(CombatEngineAPI engine, ShipAPI ship, DamagingProjectileAPI proj) {
        float damage = proj.getDamageAmount();
        float fluxToAdd = damage * FLUX_PER_DAMAGE;

        ship.getFluxTracker().increaseFlux(fluxToAdd, false);
        createAbsorptionEffect(engine, proj.getLocation(), proj.getVelocity());

        // 修复4：添加射弹存在检查
        if (engine.isEntityInPlay(proj)) {
            engine.removeEntity(proj);
        }

        // 存储吸收数据
        lastAbsorptionData.put(ship.getId(), new AbsorptionData(damage, fluxToAdd));
    }

    private void createAbsorptionEffect(CombatEngineAPI engine, Vector2f location, Vector2f velocity) {
        engine.addHitParticle(
                location,
                velocity,
                PARTICLE_SIZE * 3f,
                1.0f,
                PARTICLE_DURATION,
                PARTICLE_COLOR
        );

        for (int i = 0; i < 5; i++) {
            Vector2f particleVel = new Vector2f(
                    velocity.x + (float) Math.random() * 50f - 25f,
                    velocity.y + (float) Math.random() * 50f - 25f
            );
            engine.addHitParticle(
                    location,
                    particleVel,
                    PARTICLE_SIZE * (0.5f + (float) Math.random() * 0.5f),
                    0.8f,
                    PARTICLE_DURATION * 0.7f,
                    PARTICLE_COLOR
            );
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "45%";
        if (index == 1) return "75%";
        if (index == 2) return "25%";
        return null;
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.getMutableStats();
        if (check > 0.0F && --check < 1.0F) {
            ship.getVariant().removeMod(ERROR);
        }

        for (Set<String> strings : Arrays.asList(BLOCKED_OMNI, BLOCKED_OTHER, BLOCKED_OTHER_PLAYER_ONLY)) {
            checkAndRemoveBlockedMods(ship, strings);
        }

    }

    private void checkAndRemoveBlockedMods(ShipAPI ship, @NotNull Set<String> blockedMods) {
        List<String> shipMods = new ArrayList<>(ship.getVariant().getHullMods());
        for (String mod : shipMods) {
            if (blockedMods.contains(mod)) {
                // 移除冲突船插
                ship.getVariant().removeMod(mod);
                if (!ship.getVariant().hasHullMod(ERROR)) {
                    ship.getVariant().addMod(ERROR);
                }
                // 记录日志
                Global.getLogger(this.getClass()).info(
                        "Removed conflicting hullmod [" + mod + "] from " + ship.getName()
                );
            }
        }
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        float pad = 2f;
        Color h = Misc.getPositiveHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        tooltip.addPara("Enhancement kits that provide specific upgrades to ships. Each ship can be equipped with a special enhancement kit.",  opad,h);
        tooltip.addPara("Redirects most of the ship's energy to its protection systems.", pad, h);
        tooltip.addSectionHeading("Effect", Alignment.MID, opad);
        tooltip.addPara("Ship flux dissipation reduced by %s", pad, bad,  "" + 25 + "%");
        tooltip.addPara("Ship flux capacity increased by %s", pad, h,  "" + 15 + "%");
        tooltip.addPara("The damage taken by the hull/shield is reduced by %s, and the chance of the shield being penetrated is reduced by %s.", pad, h, "" + 10 + "%","" + 25 + "%");
        tooltip.addPara("Active venting rate increased by %s", pad, h, "" + 25 + "%");
        tooltip.addPara("Maximum combat speed reduced by %s", pad, bad, "" + 40 + "%");
        tooltip.addPara("Such drastic changes are made to accommodate a new defense mechanism related to plating",  pad, h);
        tooltip.addPara("Press and hold %s to view detailed mechanics", opad, Misc.getHighlightColor(),  "F3" );
        if (Keyboard.isKeyDown(Keyboard.KEY_F3)) {
            tooltip.addPara("Absorb projectiles within %s of shield coverage and convert them into flux", pad, h, "" + (FIELD_RADIUS_FACTOR) + "%");
            tooltip.addPara("Each projectile generates flux equal to %s of its damage", pad, bad, "" + FLUX_PER_DAMAGE + "%");
            tooltip.addPara("The probability of absorption for a given single projectile is between %s - %s, decreasing as flux level increases", pad, h, "" + (int)MIN_ABSORB_CHANCE*100+"%" , "" + (int)MAX_ABSORB_CHANCE *100+"%");
            tooltip.addPara("%s of flux generated this way is used to charge the plating", pad, h, "" + 0.05 + "%");

        }

        tooltip.addSectionHeading("Ship System Upgrades", Alignment.MID, opad);

        if(!(ship == null)) {
            if (!(ship.getSystem() == null)) {
                tooltip.addPara("Press and hold %s to view details", opad, h, "F4");
                if (Keyboard.isKeyDown(Keyboard.KEY_F4)) {
                    if (ship.getSystem().getId().equals("CR_PhaseboostDrive")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/active_flare_launcher.png", 35f);
                        imageText.addPara("Ship system effects:", pad);
                        imageText.addPara("Speeds up ship engine repair and dissipates 15% of the flux based on the current flux level", pad);
                        tooltip.addImageWithText(15f);
                    } else if (ship.getSystem().getId().equals("CR_PhaseVerbJet")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/active_flare_launcher.png", 35f);
                        imageText.addPara("Ship system effects:", pad);
                        imageText.addPara("Pressurizes the shield, providing 25% damage reduction", pad);
                        tooltip.addImageWithText(15f);
                    } else if (ship.getSystem().getId().equals("CR_PhaseDrift")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/active_flare_launcher.png", 35f);
                        imageText.addPara("Ship system effects:", pad);
                        imageText.addPara("Increased soft flux dissipation rate for the duration of the ship system", pad);
                        tooltip.addImageWithText(15f);
                    } else if (ship.getSystem().getId().equals("RS_WeaponOverloading")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/emp_emitter.png", 35f);
                        imageText.addPara("Ship system effects:", pad);
                        imageText.addPara("During the duration of the tactical system, the shield effectiveness is increased and a point defense arc is emitted.", pad);
                        tooltip.addImageWithText(15f);
                    } else if (ship.getSystem().getId().equals("CR_TargetingLink")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/emp_emitter.png", 35f);
                        imageText.addPara("Ship system effects:", pad);
                        imageText.addPara("Improve the damage reduction capabilities of battleships and launched fighters", pad);
                        tooltip.addImageWithText(15f);
                    } else if (ship.getSystem().getId().equals("RS_FortressShieldStats")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/emp_emitter.png", 35f);
                        imageText.addPara("Ship system effects:", pad);
                        imageText.addPara("Further improves shield efficiency and ship flux dissipation, and grants 15% hard flux dissipation.", pad);
                        tooltip.addImageWithText(15f);
                    }else if (ship.getSystem().getId().equals("CR_PhaseCrossing")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/emp_emitter.png", 35f);
                        imageText.addPara("Ship system effects:", pad);
                        imageText.addPara("After jumping, it will provide the ship with a large proportion of damage reduction that lasts for 3 seconds and cannot be superimposed.", pad);
                        tooltip.addImageWithText(15f);
                    }else if (ship.getSystem().getId().equals("CR_DamperBurn")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/emp_emitter.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("During the duration of the system, the damage taken by the ship is greatly reduced.", pad);
                        tooltip.addImageWithText(15f);
                    }else if (ship.getSystem().getId().equals("RS_MABurn")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/emp_emitter.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("During the duration of the system, the damage taken by the ship is greatly reduced.", pad);
                        tooltip.addImageWithText(15f);
                    }
                }
            }
        }
    }

    static {
        BLOCKED_OMNI.add("WeaponOverLoad");
        BLOCKED_OMNI.add("PolariphaseDrive");
        //BLOCKED_OMNI.add("targetingunit");


        BLOCKED_OTHER.add("WeaponOverLoad");
        BLOCKED_OTHER.add("PolariphaseDrive");
        //BLOCKED_OTHER.add("targetingunit");

        BLOCKED_OTHER_PLAYER_ONLY.add("WeaponOverLoad");
        BLOCKED_OTHER_PLAYER_ONLY.add("PolariphaseDrive");
        //BLOCKED_OTHER_PLAYER_ONLY.add("targetingunit");
    }
}