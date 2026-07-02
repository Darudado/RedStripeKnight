package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.plugins.MagicTrailPlugin;

import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicUI;

import java.awt.*;
import java.util.*;
import java.util.List;

import static data.scripts.shipsystems.PhaseboostDrive.getSystemEngineScale;
import static data.scripts.utils.MathPersonal.estimateIncomingDamage;
import static data.scripts.utils.MathPersonal.getHitChance;
import static org.magiclib.util.MagicSettings.clamp255;

public class PolariphaseDrive extends BaseHullMod {
    public static final float MOBILITY_MULT = 1.2f;
    public static final float SPEED_BONUS_MULT = 2f;
    public static final Map<HullSize, Float> TURN_REMOVAL_MULT_MAP = new HashMap<>();
    public static final Map<HullSize, Float> TIME_TO_MAX_SPEED_MAP = new HashMap<>();
    private final Map<ShipAPI, Map<WeaponSlotAPI, Float>> associatedIDs = new HashMap<>();
    public static final float MAX_SPEED_BONUS_PERCENTAGE = 20f;

    private static final float AFTERIMAGE_THRESHOLD = 0.4f;
    private static final float SHADOW_DISTANCE_DIFFERENCE = 25f;
    private static final float SHADOW_FLICKER_DIFFERENCE = 10f;
    private static final int SHADOW_FLICKER_CLONES = 3;

    private static final Set<String> BLOCKED_OTHER = new HashSet<>();
    private static final Set<String> BLOCKED_OMNI = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER_PLAYER_ONLY = new HashSet<>();
    private static final String ERROR = "IncompatibleHullmodWarning";
    private float check = 0.0F;

    private static final String DATA_KEY = "CR_booster_data";
    public static final float BOOST_COST = 5f;
    private static final Vector2f ZERO = new Vector2f();
    private static final Map<HullSize, Float> SIZE_MULT = new HashMap<>();
    private static final float SUBSYSTEMCD = 20f; // cooldown after use
    public static final float OVERLOAD_ENEMY_DURATION = 5f; //overload duration

    // 新增常量 - 粒子效果参数
    private static final float A_2 = (float) 45 / 2;
    private static final float VEL_MIN = 0.5f;
    private static final float VEL_MAX = 1f;


    private static final Color AFTERIMAGE_COLOR = new Color(255, 255, 255, 50);

    static {
        SIZE_MULT.put(HullSize.FIGHTER, 1f);
        SIZE_MULT.put(HullSize.FRIGATE, 1.2f);
        SIZE_MULT.put(HullSize.DESTROYER, 0.85f);
        SIZE_MULT.put(HullSize.CRUISER, 0.50f);
        SIZE_MULT.put(HullSize.CAPITAL_SHIP, 0.50f);
    }

    static {
        TURN_REMOVAL_MULT_MAP.put(HullSize.FRIGATE, 0.8f);
        TURN_REMOVAL_MULT_MAP.put(HullSize.DESTROYER, 0.6f);
        TURN_REMOVAL_MULT_MAP.put(HullSize.CRUISER, 0.5f);
        TURN_REMOVAL_MULT_MAP.put(HullSize.CAPITAL_SHIP, 0.4f);

        TIME_TO_MAX_SPEED_MAP.put(HullSize.FRIGATE, 2.0f);
        TIME_TO_MAX_SPEED_MAP.put(HullSize.DESTROYER, 3.0f);
        TIME_TO_MAX_SPEED_MAP.put(HullSize.CRUISER, 3.5f);
        TIME_TO_MAX_SPEED_MAP.put(HullSize.CAPITAL_SHIP, 4.0f);
    }

    private float CRBOOST_subsys() {
        return SUBSYSTEMCD;
    }

    // 应用船体修改效果
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getTimeMult().modifyPercent(id, 15f);
        stats.getEngineDamageTakenMult().modifyMult(id, 1f + 0.75f);
        stats.getCombatEngineRepairTimeMult().modifyMult(id, 1f + 0.25f);
        stats.getPeakCRDuration().modifyMult(id, 0.5f);
        stats.getCRLossPerSecondPercent().modifyMult(id, 1.25f);
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        final CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || !engine.isEntityInPlay(ship) || !ship.isAlive()) {
            return;
        }
        String key = DATA_KEY + "_" + ship.getId();
        CRboost_data data = (CRboost_data) engine.getCustomData().get(key);
        if (data == null) {
            data = new CRboost_data();
            engine.getCustomData().put(key, data);
        }
        if (!data.runOnce) {
            data.runOnce = true;
            data.subsysID = this.getClass().getName() + "_" + ship.getId();
            data.maxcooldown = CRBOOST_subsys();
            data.maxActiveTime = OVERLOAD_ENEMY_DURATION;
        }

        // 能量恢复逻辑
        if (data.cooldown < data.maxcooldown && data.activeTime <= 0f && ship.getCurrentCR() > 0f && !ship.getFluxTracker().isOverloadedOrVenting()) {
            float bonus = 1f;
            if (data.burnedOut) {
                bonus = 0.5f;
            }
            data.cooldown += amount * bonus;
        }

        // 玩家按键检测
        if (!data.burnedOut && !ship.getFluxTracker().isOverloaded() && !ship.getEngineController().isFlamedOut()) {
            if (Global.getCombatEngine().getPlayerShip() == ship) {
                isKeyPressed(ship);
            }
        }

        // 过热恢复逻辑
        if (data.burnedOut) {
            data.tracker.advance(amount);
            data.smokeTracker.advance(amount);

            if (data.tracker.intervalElapsed()) {
                data.burnedOut = false;
            }
        }

        // 基础机动性系统
        float actualAmount = amount;
        if (ship.getSystem().getEffectLevel() > 0f || ship.isHulk()) {
            actualAmount = 0f;
        }

        ship.getMutableStats().getTurnAcceleration().modifyMult("PolariphaseDriveID", PolariphaseDrive.MOBILITY_MULT);
        ship.getMutableStats().getMaxTurnRate().modifyMult("PolariphaseDriveID", PolariphaseDrive.MOBILITY_MULT);

        // 根据舰船行为更新计数器
        float gainMult = 2f;
        Float turnMultObj = TURN_REMOVAL_MULT_MAP.get(ship.getHullSize());
        float turnMult = (turnMultObj != null) ? turnMultObj : 1.0f;

        boolean isTurning = ship.getEngineController().isTurningLeft() || ship.getEngineController().isTurningRight();
        boolean isAccelerating = ship.getEngineController().isAccelerating();
        MutableShipStatsAPI stats = ship.getMutableStats();
        float currentCounter = stats.getDynamic().getStat("PolariphaseDriveTurnCounter").getModifiedValue();

        // 更新计数器逻辑
        if (isTurning) {
            stats.getDynamic().getStat("PolariphaseDriveTurnCounter")
                    .modifyFlat("ID", currentCounter - (actualAmount * turnMult));
        } else if (isAccelerating) {
            stats.getDynamic().getStat("PolariphaseDriveTurnCounter")
                    .modifyFlat("ID", currentCounter + (actualAmount * gainMult));
        }

        // 限制计数器范围
        Float accelDurationObj = TIME_TO_MAX_SPEED_MAP.get(ship.getHullSize());
        float accelDuration = (accelDurationObj != null) ? accelDurationObj : 1.0f;
        float updatedCounter = MathUtils.clamp(currentCounter, 0f, accelDuration);
        stats.getDynamic().getStat("PolariphaseDriveTurnCounter").modifyFlat("ID", updatedCounter);

        // 应用速度加成
        ship.getMutableStats().getMaxSpeed().modifyPercent("ID",
                PolariphaseDrive.MAX_SPEED_BONUS_PERCENTAGE * SPEED_BONUS_MULT * (updatedCounter / accelDuration));

        // 残影生成逻辑
        ship.getMutableStats().getDynamic().getStat("PolariphaseDriveAfterimageTrackerID")
                .modifyFlat("PolariphaseDriveAfterimageTrackerNullerID", -1);

        ship.getMutableStats().getDynamic().getStat("PolariphaseDriveAfterimageTrackerID")
                .modifyFlat("PolariphaseDriveAfterimageTrackerID",
                        ship.getMutableStats().getDynamic().getStat("PolariphaseDriveAfterimageTrackerID").getModifiedValue() + amount);

        if (ship.getMutableStats().getDynamic().getStat("PolariphaseDriveAfterimageTrackerID").getModifiedValue() > AFTERIMAGE_THRESHOLD) {
            Vector2f initialOffset = MathUtils.getRandomPointInCircle(null, SHADOW_DISTANCE_DIFFERENCE);

            for (int i = 0; i < SHADOW_FLICKER_CLONES; i++) {
                MathUtils.getRandomPointInCircle(initialOffset, SHADOW_FLICKER_DIFFERENCE);

                float currentSpeed = ship.getVelocity().length();
                float maxSpeed = ship.getMutableStats().getMaxSpeed().getBaseValue(); // 获取包含所有加成的最大速度
                float speedThreshold = maxSpeed * 1.5f; // 最大速度的75%

                if(currentSpeed > speedThreshold) {
                    createAfterimage(ship);
                }
            }

            stats.getDynamic().getStat("PolariphaseDriveAfterimageTrackerID")
                    .modifyFlat("PolariphaseDriveAfterimageTrackerID",
                            stats.getDynamic().getStat("PolariphaseDriveAfterimageTrackerID").getModifiedValue() - AFTERIMAGE_THRESHOLD);
        } else {
            // 切断自定义拖尾效果
            for (WeaponSlotAPI testSlot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
                if (!testSlot.isSystemSlot()) continue;

                if (!associatedIDs.containsKey(ship)) {
                    associatedIDs.put(ship, new HashMap<>());
                }

                associatedIDs.get(ship).put(testSlot, MagicTrailPlugin.getUniqueID());
            }
        }

        // AI行为
        if (ship.getAI() != null) {
            data.aiTracker.advance(amount);
            if (data.aiTracker.intervalElapsed() && data.cooldown >= 5f && !ship.getFluxTracker().isOverloaded()) {
                boolean player = ship == Global.getCombatEngine().getPlayerShip();
                final ShipwideAIFlags flags = ship.getAIFlags();
                if (data.aiTracker.intervalElapsed() && data.cooldown >= 5f && !ship.getFluxTracker().isOverloaded()) {
                    if (!player && ship.getHullLevel() >= 0.5f) {
                        if ((flags.hasFlag(ShipwideAIFlags.AIFlags.MOVEMENT_DEST) && !ship.areAnyEnemiesInRange() || flags.hasFlag(ShipwideAIFlags.AIFlags.PURSUING))  && data.cooldown >= 5f) {
                            boost(ship.getFacing(), ship);
                        }
                        List<DamagingProjectileAPI> possibleTargets = new ArrayList<>(100);
                        possibleTargets.addAll(CombatUtils.getMissilesWithinRange(ship.getLocation(), 500f));
                        possibleTargets.addAll(CombatUtils.getProjectilesWithinRange(ship.getLocation(), 500f));

                        for (DamagingProjectileAPI proj : possibleTargets) {
                            if (proj == null) {
                                continue;
                            }
                            // 简化版的命中率检测
                            if (estimateIncomingDamage(ship) < 100) {
                                if (getHitChance(proj, ship) > 0.8f) {
                                    if (proj.getLocation().getX() < ship.getLocation().getX()) {
                                        boost(ship.getFacing() + 90f, ship);
                                        break;
                                    } else if (proj.getLocation().getX() > ship.getLocation().getX()) {
                                        boost(ship.getFacing() - 90f, ship);
                                        break;
                                    } else {
                                        boost(ship.getFacing() - 180f, ship);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // UI显示
        if (engine.getPlayerShip() == ship) {
            if (data.burnedOut || data.cooldown <= 10f) {
                MagicUI.drawHUDStatusBar(
                        ship,
                        data.cooldown / data.maxcooldown,
                        Misc.getNegativeHighlightColor(),
                        Misc.getNegativeHighlightColor(),
                        0,
                        "Charge",
                        "",
                        false
                );
            } else {
                MagicUI.drawHUDStatusBar(
                        ship,
                        data.cooldown / data.maxcooldown,
                        Misc.getPositiveHighlightColor(),
                        Misc.getPositiveHighlightColor(),
                        0,
                        "Charge",
                        "",
                        false
                );
            }
        }

        Global.getCombatEngine().getCustomData().put(key, data);
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        return ship.getVariant().getHullMods().contains("CrusadersCore") &&
                !ship.getVariant().getHullMods().contains("PhaseDefenseUnit") &&
                !ship.getVariant().getHullMods().contains("WeaponOverLoad");
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
                ship.getVariant().removeMod(mod);
                if (!ship.getVariant().hasHullMod(ERROR)) {
                    ship.getVariant().addMod(ERROR);
                }
                Global.getLogger(this.getClass()).info(
                        "Removed conflicting hullmod [" + mod + "] from " + ship.getName()
                );
            }
        }
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        float pad = 2f;
        Color highlight = Misc.getHighlightColor();
        Color h = Misc.getPositiveHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        tooltip.addPara("Enhancement kits that provide specific upgrades to ships. Each ship can be equipped with a special enhancement kit.", opad, h);
        tooltip.addPara("Redirects most of the ship's energy to the power system.", pad, h);
        tooltip.addSectionHeading("mechanism", Alignment.MID, opad);
        tooltip.addPara("Improve the maneuverability of ship %s", pad, h, "" + 20 + "%");
        tooltip.addPara("Increase the normal time flow rate of ship %s", pad, h, "" + 15 + "%");
        tooltip.addPara("Increase the maximum speed of ship %s during combat", pad, h, "" + MAX_SPEED_BONUS_PERCENTAGE * SPEED_BONUS_MULT + "%");
        tooltip.addPara("Restructured engine pipeline improves engine repair speed for %s", pad, h, "" + 25 + "%");
        tooltip.addPara("Extremely improved ship engines are more susceptible to damage, increasing damage taken by %s", pad, bad , "" + 50 + "%");
        tooltip.addPara("The high-intensity operation of the power system reduces the ship's peak active time by %s and increases the combat readiness decay rate by %s.", pad, bad  ,"" + 50 + "%","" + 25 + "%");
        tooltip.addPara("Optimization of the power take-off pipeline allows the ship to make small maneuvers in %s", pad, h,"Unbelievable" );
        tooltip.addPara("Press and hold %s to view detailed mechanics", opad, highlight,  "F3" );
        if (Keyboard.isKeyDown(Keyboard.KEY_F3)) {
            // 新增推进系统说明
            tooltip.addSectionHeading("phase propulsion system", Alignment.MID, pad);
            tooltip.addPara("Double-click the direction keys for rapid phase advancement:", pad);
            tooltip.addPara("Double click on %s - move forward", pad, h, "W");
            tooltip.addPara("Double click on %s - push left", pad, h, "A");
            tooltip.addPara("Double click on %s - advance backward", pad, h, "S");
            tooltip.addPara("Double click on %s - push to the right", pad, h, "D");
            tooltip.addPara("%s " + "Propulsion consumes system energy, and exhaustion of energy will cause the engine to stall.", pad, Misc.getNegativeHighlightColor(), "⚠");
        }

        tooltip.addSectionHeading("The benefits of ship plugs to the tactical system", Alignment.MID, opad);
        if((ship == null)){
            tooltip.addPara("Compliant ships and tactical systems not detected", pad);
        }
        if(!(ship == null)) {
            if (!(ship.getSystem() == null)) {
                tooltip.addPara("Press and hold %s to view details", opad, h, "F4");
                if (Keyboard.isKeyDown(Keyboard.KEY_F4)) {
                    if (ship.getSystem().getId().equals("CR_PhaseboostDrive")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/plasma_jets.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("Increases the time flow rate of the ship to 6 times while the tactical system is in effect", pad);
                        tooltip.addImageWithText(15f);
                    }


                    if (ship.getSystem().getId().equals("CR_PhaseVerbJet")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/plasma_jets.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("The time flow rate of the ship while the tactical system is in effect is increased to 1.5 times.", pad);
                        tooltip.addImageWithText(15f);
                    }


                    if (ship.getSystem().getId().equals("CR_PhaseDrift")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/plasma_jets.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("Improves the time flow during the duration of the tactical system and causes continuous damage to surrounding units", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("RS_WeaponOverloading")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/plasma_jets.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("During the duration of the tactical system, the speed is increased and the time flow is accelerated by 1.5 times.", pad);
                        tooltip.addImageWithText(15f);
                    }
                    if (ship.getSystem().getId().equals("CR_TargetingLink")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/plasma_jets.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("Greatly accelerates the time flow of battleships and launched carrier-based aircraft", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("RS_FortressShieldStats")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/plasma_jets.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("Slightly pressurizes the shield and accelerates the ship's time flow", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("CR_PhaseCrossing")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/plasma_jets.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("After jumping, energy erupts, emitting a large amount of arcs.", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("CR_DamperBurn")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/plasma_jets.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("Accelerate the ship's time flow during the system duration", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("RS_MABurn")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/plasma_jets.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("Accelerate the ship's time flow during the system duration", pad);
                        tooltip.addImageWithText(15f);
                    }

                }


            }
        }

    }

    static {
        BLOCKED_OMNI.add("PhaseDefenseUnit");
        BLOCKED_OMNI.add("WeaponOverLoad");

        BLOCKED_OTHER.add("PhaseDefenseUnit");
        BLOCKED_OTHER.add("WeaponOverLoad");

        BLOCKED_OTHER_PLAYER_ONLY.add("PhaseDefenseUnit");
        BLOCKED_OTHER_PLAYER_ONLY.add("WeaponOverLoad");
    }

    // 简化的数据类，移除了assaultBoost相关字段
    private static class CRboost_data {
        String subsysID = "";
        boolean runOnce = false;
        float activeTime = 0f;
        float maxActiveTime = 0f;
        float cooldown = 25f;
        float maxcooldown = 0f;
        boolean keyPressed = false;
        boolean moveKeyPressed = false;
        public String lastKeyPressed = null;
        public long lastKeyTime = 0;
        boolean boostEnabled = false;
        boolean burnedOut = false;
        IntervalUtil tracker = new IntervalUtil(5f, 5f);
        IntervalUtil aiTracker = new IntervalUtil(1f, 1f);
        IntervalUtil smokeTracker = new IntervalUtil(0.25f, 0.25f);
    }

    // 移除assaultBoost参数的推进粒子效果
    public void createBoostParticles(ShipAPI ship) {
        List<ShipEngineControllerAPI.ShipEngineAPI> engines = ship.getEngineController().getShipEngines();
        if (engines == null || engines.isEmpty()) {
            return;  // 无引擎，不生成粒子效果
        }
        float INSTANT_BOOST_FLAT = 300f;
        float INSTANT_BOOST_MULT = 5f;
        float boostVisualDir;
        float shipRadius = ship.getCollisionRadius();
        float duration = (float) Math.sqrt(shipRadius) / 25f;
        Vector2f direction = new Vector2f();
        float boostScale = 0.75f;

        if (ship.getEngineController().isAccelerating()) {
            direction.y += 0.75f - 0.35f;
            boostScale -= 0.35f;
        } else if (ship.getEngineController().isAcceleratingBackwards() || ship.getEngineController().isDecelerating()) {
            direction.y -= 0.75f - 0.45f;
            boostScale -= 0.45f;
        }
        if (ship.getEngineController().isStrafingLeft()) {
            direction.x -= 1f;
            boostScale += 1f - 0.75f;
        } else if (ship.getEngineController().isStrafingRight()) {
            direction.x += 1f;
            boostScale += 1f - 0.75f;
        }
        if (direction.length() <= 0f) {
            direction.y = 0.75f - 0.35f;
            boostScale -= 0.35f;
        }
        Misc.normalise(direction);
        VectorUtils.rotate(direction, ship.getFacing() - 90f, direction);
        direction.scale(((ship.getMaxSpeedWithoutBoost() * INSTANT_BOOST_MULT) + INSTANT_BOOST_FLAT) * boostScale);

        boostVisualDir = MathUtils.clampAngle(VectorUtils.getFacing(direction) - 90f);
        Color ENGINE_COLOR;
        if (ship.getEngineController().getFlameColorShifter() != null
                && ship.getEngineController().getFlameColorShifter().getCurr().getAlpha() != 0) {
            ENGINE_COLOR = ship.getEngineController().getFlameColorShifter().getCurr();
        } else {
            ENGINE_COLOR = engines.get(0).getEngineColor();  // 已确保非空
        }
        Color BOOST_COLOR = new Color(255, 175, 175, 200);

        for (ShipEngineControllerAPI.ShipEngineAPI eng : ship.getEngineController().getShipEngines()) {
            float level = 1f;
            if (eng.isSystemActivated()) {
                level = getSystemEngineScale(eng, boostVisualDir);
            }
            if ((eng.isActive() || eng.isSystemActivated()) && (level > 0f)) {
                Color bigBoostColor = new Color(
                        clamp255(Math.round(0.1f * ENGINE_COLOR.getRed())),
                        clamp255(Math.round(0.1f * ENGINE_COLOR.getGreen())),
                        clamp255(Math.round(0.1f * ENGINE_COLOR.getBlue())),
                        clamp255(Math.round(0.3f * ENGINE_COLOR.getAlpha() * level)));
                Color boostColor = new Color(BOOST_COLOR.getRed(), BOOST_COLOR.getGreen(), BOOST_COLOR.getBlue(),
                        clamp255(Math.round(BOOST_COLOR.getAlpha() * level)));
                Global.getCombatEngine().spawnExplosion(eng.getLocation(), ZERO, bigBoostColor,
                        2f * 4f * boostScale * eng.getEngineSlot().getWidth(), duration * 1.5f);
                Global.getCombatEngine().spawnExplosion(eng.getLocation(), ZERO, boostColor,
                        1f * 2f * boostScale * eng.getEngineSlot().getWidth(), 0.15f);

                float speed = 450f;
                float facing = ship.getFacing();
                float angle = MathUtils.getRandomNumberInRange(facing - A_2, facing + A_2);
                float vel = MathUtils.getRandomNumberInRange(speed * -VEL_MIN, speed * -VEL_MAX);
                Vector2f vector = MathUtils.getPointOnCircumference(null, vel, angle);
                Vector2f origin = new Vector2f(eng.getLocation());
                Vector2f offset = new Vector2f(-5f, -0f);
                VectorUtils.rotate(offset, ship.getFacing(), offset);
                Vector2f.add(offset, origin, origin);

                Global.getCombatEngine().addHitParticle(
                        origin,
                        vector,
                        MathUtils.getRandomNumberInRange(2, 5),
                        1f,
                        MathUtils.getRandomNumberInRange(0.4f, 0.8f),
                        BOOST_COLOR);
            }
        }

        float soundScale = (float) Math.sqrt(boostScale);
        switch (ship.getHullSize()) {
            case FIGHTER:
                Global.getSoundPlayer().playSound("cr_booster_activate", 1.1f, 0.6f * soundScale, ship.getLocation(), ZERO);
                break;
            case FRIGATE:
                Global.getSoundPlayer().playSound("cr_booster_activate", .8f, 1.1f * soundScale, ship.getLocation(), ZERO);
                break;
            case DESTROYER:
                Global.getSoundPlayer().playSound("cr_booster_activate", 0.9f, 1.1f * soundScale, ship.getLocation(), ZERO);
                break;
            case CRUISER:
                Global.getSoundPlayer().playSound("cr_booster_activate", 0.8f, 1.2f * soundScale, ship.getLocation(), ZERO);
                break;
            case CAPITAL_SHIP:
                Global.getSoundPlayer().playSound("cr_booster_activate", 0.7f, 1.3f * soundScale, ship.getLocation(), ZERO);
                break;
        }
    }

    // 简化的boost方法，移除assaultBoost相关逻辑
    public void boost(float angleDegrees, ShipAPI ship) {
        String key = DATA_KEY + "_" + ship.getId();
        CRboost_data data = (CRboost_data) Global.getCombatEngine().getCustomData().get(key);

        // 消耗能量
        data.cooldown -= BOOST_COST;

        if (MagicRender.screenCheck(0.1f, ship.getLocation())) {
            createBoostParticles(ship);
        }
        data.boostEnabled = false;

        // 过热检测
        if (data.cooldown < 0) {
            ship.getEngineController().forceFlameout();
            data.burnedOut = true;
            Global.getSoundPlayer().playSound("disabled_small_crit", 1f, 1f, ship.getLocation(), ship.getVelocity());
            data.cooldown = 0;
            return;
        }

        // 应用推进力
        CombatUtils.applyForce(ship, angleDegrees, (ship.getMaxSpeed() + (ship.getMass() * SIZE_MULT.get(ship.getHullSize())) * 2f));
    }

    // 简化的按键检测，移除assaultBoost相关逻辑
    public void isKeyPressed(ShipAPI ship) {
        boolean wPressed = Keyboard.isKeyDown(Keyboard.getKeyIndex(Global.getSettings().getControlStringForEnumName("SHIP_ACCELERATE")));
        boolean aPressed = Keyboard.isKeyDown(Keyboard.getKeyIndex(Global.getSettings().getControlStringForEnumName("SHIP_TURN_LEFT")));
        boolean sPressed = Keyboard.isKeyDown(Keyboard.getKeyIndex(Global.getSettings().getControlStringForEnumName("SHIP_ACCELERATE_BACKWARDS")));
        boolean dPressed = Keyboard.isKeyDown(Keyboard.getKeyIndex(Global.getSettings().getControlStringForEnumName("SHIP_TURN_RIGHT")));

        String key = DATA_KEY + "_" + ship.getId();
        CRboost_data data = (CRboost_data) Global.getCombatEngine().getCustomData().get(key);

        // 检查是否有移动键按下
        data.keyPressed = wPressed || aPressed || sPressed || dPressed;

        if (!data.boostEnabled) {
            // 如果移动键刚刚按下
            if (!data.moveKeyPressed && data.keyPressed) {
                long now = System.currentTimeMillis();
                int inputTime = 300;

                //if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
                    //inputTime = LunaSettings.getInt("armaa", "armaa_inputTime");
                //}

                String currentKey = null;
                if (wPressed) {
                    currentKey = "W";
                } else if (aPressed) {
                    currentKey = "A";
                } else if (sPressed) {
                    currentKey = "S";
                } else if (dPressed) {
                    currentKey = "D";
                }

                if (currentKey != null) {
                    // 双击检测：在时间内再次按下相同键
                    if (currentKey.equals(data.lastKeyPressed) && now - data.lastKeyTime < inputTime) {
                        switch (currentKey) {
                            case "W" -> boost(ship.getFacing(), ship);
                            case "A" -> boost(ship.getFacing() + 90f, ship);
                            case "S" -> boost(ship.getFacing() - 180f, ship);
                            case "D" -> boost(ship.getFacing() - 90f, ship);
                        }
                        // 重置按键记录
                        data.lastKeyPressed = null;
                    } else {
                        // 记录新按键和时间
                        data.lastKeyPressed = currentKey;
                        data.lastKeyTime = now;
                    }
                }
                data.moveKeyPressed = true;
            }

            if (!data.keyPressed) {
                data.moveKeyPressed = false;
            }
        }

        Global.getCombatEngine().getCustomData().put(key, data);
    }

    private void createAfterimage(ShipAPI ship) {
        // 获取船体贴图偏移量
        SpriteAPI sprite = ship.getSpriteAPI();
        float offsetX = sprite.getWidth()/2 - sprite.getCenterX();
        float offsetY = sprite.getHeight()/2 - sprite.getCenterY();

        // 计算旋转后的偏移量
        float trueOffsetX = (float)Math.cos(Math.toRadians(ship.getFacing()-90f))*offsetX -
                (float)Math.sin(Math.toRadians(ship.getFacing()-90f))*offsetY;
        float trueOffsetY = (float)Math.sin(Math.toRadians(ship.getFacing()-90f))*offsetX +
                (float)Math.cos(Math.toRadians(ship.getFacing()-90f))*offsetY;

        // 为船体创建残影
        MagicRender.battlespace(
                Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
                new Vector2f(ship.getLocation().x + trueOffsetX, ship.getLocation().y + trueOffsetY),
                new Vector2f(0, 0),
                new Vector2f(sprite.getWidth(), sprite.getHeight()),
                new Vector2f(0, 0),
                ship.getFacing()-90f,
                0f,
                AFTERIMAGE_COLOR,
                true,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                0.65f,
                CombatEngineLayers.BELOW_SHIPS_LAYER
        );

        // 为所有武器创建残影
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.getSprite() == null) continue;

            // 检查武器是否被设置为完全透明（隐藏武器）
            boolean isHidden = false;
            if (weapon.getSprite() != null && weapon.getSprite().getColor().getAlpha() == 0) {
                isHidden = true;
            }
            if (weapon.getBarrelSpriteAPI() != null && weapon.getBarrelSpriteAPI().getColor().getAlpha() == 0) {
                isHidden = true;
            }
            if (weapon.getUnderSpriteAPI() != null && weapon.getUnderSpriteAPI().getColor().getAlpha() == 0) {
                isHidden = true;
            }
            if (weapon.getGlowSpriteAPI() != null && weapon.getGlowSpriteAPI().getColor().getAlpha() == 0) {
                isHidden = true;
            }

            if (isHidden) continue;

            // 获取武器贴图路径
            String spritePath;
            if (weapon.getAnimation() != null && weapon.getAnimation().getNumFrames() > 1) {
                // 对于动画武器，获取当前帧的贴图路径
                int frame = weapon.getAnimation().getFrame();
                String basePath = weapon.getSlot().isTurret() ?
                        weapon.getSpec().getTurretSpriteName() :
                        weapon.getSpec().getHardpointSpriteName();
                // 移除.png后缀（4个字符）
                if (basePath.endsWith(".png")) {
                    basePath = basePath.substring(0, basePath.length() - 4);
                }
                // 添加帧号和后缀
                spritePath = String.format("%s_%02d.png", basePath, frame);
            } else {
                // 无动画的武器使用默认贴图
                spritePath = weapon.getSlot().isTurret() ?
                        weapon.getSpec().getTurretSpriteName() :
                        weapon.getSpec().getHardpointSpriteName();
            }

            // 获取新的Sprite实例
            SpriteAPI weaponSprite = Global.getSettings().getSprite(spritePath);
            weaponSprite.setSize(weapon.getSprite().getWidth(), weapon.getSprite().getHeight());

            // 创建武器残影
            MagicRender.battlespace(
                    weaponSprite,
                    new Vector2f(weapon.getLocation().x + trueOffsetX, weapon.getLocation().y + trueOffsetY),
                    new Vector2f(0, 0),
                    new Vector2f(weapon.getSprite().getWidth(), weapon.getSprite().getHeight()),
                    new Vector2f(0, 0),
                    weapon.getCurrAngle()-90f,
                    0f,
                    AFTERIMAGE_COLOR,
                    true,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0f,
                    0.65f,
                    CombatEngineLayers.BELOW_SHIPS_LAYER
            );
        }
    }

}