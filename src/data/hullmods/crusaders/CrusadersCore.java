/**
 * 感谢猫猫、狐条与寒流。
 * 舰船ai的部分基本参考于天苑四，在修改后以期能较符合骑士团舰船作战预期。
 * 骑士团舰船身份证插件。
 */
package data.hullmods.crusaders;

// 导入依赖类（省略部分标准库导入）
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import org.boxutil.util.ShaderUtil;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicFakeBeam;

import static data.hullmods.crusaders.CrusadersPlating.*;
import static data.scripts.utils.RSUtil.getEnemyProjectilesAndMissilesWithinRange;
import static org.lazywizard.lazylib.combat.WeaponUtils.getTimeToAim;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 类声明：继承自BaseHullMod，表示这是一个船体插件（HullMod）
public class CrusadersCore extends BaseHullMod {

    // 常量定义（未声明final，存在风险）
    private static final float TIME_ACCELERATION_BONUS = 5f;  // 时间加速增益百分比
    private static final float SHIELD_PENALTY = 15f;           // 护盾承受伤害惩罚百分比
    private static final Color JITTER_UNDER_COLOR = new Color(225,225,225,50); // 舰船抖动效果颜色
    public static float VENT_RATE_BONUS = 10f;

    private static final String THIS_MOD = "CrusadersCore";
    
    static int programID;

    private static final float DAMAGE_FACTOR = 0.01f;
    private static final float DAMAGE_POWER = 1.5f;
    private static final float EMP_FACTOR = 0.25f;
    
    public static void initShader() {
    	programID = ShaderUtil.createShaderVF(THIS_MOD, CrusaderShaders.VERT, CrusaderShaders.FRAG);
    }

    // 方法：在舰船创建前应用效果（如修改护盾伤害、绑定工具类逻辑）
    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (!stats.getVariant().hasHullMod(id)) {
            stats.getVariant().addMod(id); // 确保 HullMod 被正确注册到舰船
        }
        stats.getVentRateMult().modifyPercent(id, VENT_RATE_BONUS );
        stats.getVariant().addTag(THIS_MOD + "_module_handler");                            // 添加模块处理器
        stats.getShieldDamageTakenMult().modifyMult(id, 1f + SHIELD_PENALTY * 0.01f); // 护盾伤害惩罚（魔数0.01f）
    }

    // 方法：在舰船创建后应用效果（如时间加速、自动移除非法HullMod）
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {

        if (ship.getShield() != null) {
            ship.getShield().setRadius(ship.getShield().getRadius(),
                    Global.getSettings().getSpriteName("fx", "CR_shields_OUT"),
                    Global.getSettings().getSpriteName("fx", "CR_shields_IN")

            );
        }

        boolean isModule = ship.isStationModule() || ship.getParentStation() != null;
        // 若当前舰船不满足条件且是玩家舰船，自动移除本HullMod
        if (!isApplicableToShip(ship) && ship.getOwner() == 0 && !isModule) {
            ship.getVariant().getHullMods().remove(id);
        } else {
            final float TIME_MULT = 1f + TIME_ACCELERATION_BONUS * 0.01f;
            ship.getMutableStats().getTimeMult().modifyMult(id, TIME_MULT);

            // 同步到子模块（替代util的模块处理）
            if (ship.getVariant().hasTag(THIS_MOD + "_module_handler")) {
                for (ShipAPI module : ship.getChildModulesCopy()) {
                    module.getMutableStats().getTimeMult().modifyMult(id, TIME_MULT);
                }
            }
        }
    }

    // 方法：在战斗中持续生效（如舰船抖动效果）
    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive() || ship.isPhased()) return;

        // 时间加速效果需每帧同步到模块
        final float TIME_MULT = 1f + TIME_ACCELERATION_BONUS * 0.01f;
        ship.getMutableStats().getTimeMult().modifyMult(THIS_MOD, TIME_MULT);

        // 同步到子模块
        for (ShipAPI module : ship.getChildModulesCopy()) {
            module.getMutableStats().getTimeMult().modifyMult(THIS_MOD, TIME_MULT);
        }

        ship.setJitterUnder(ship, JITTER_UNDER_COLOR, 0.3f, 10, 12f);

        boolean isPlayer = Global.getCombatEngine().getPlayerShip() == ship;
        ship.getMutableStats().getTimeMult().modifyMult(THIS_MOD, TIME_MULT);
        if (isPlayer) {
            Global.getCombatEngine().getTimeMult().modifyMult(THIS_MOD, 1 / TIME_MULT);
        } else {
            Global.getCombatEngine().getTimeMult().unmodify(THIS_MOD);

        }


        if(ship.getHullSize()==HullSize.FIGHTER) return;
        
        FluxTrackerAPI flux = ship.getFluxTracker();
        if (!flux.isOverloaded()) {
            if (!flux.isVenting()) {
                if (ship.getShipAI() != null && timesPerSec(2, amount)) {
                    if (Global.getCombatEngine().getPlayerShip() == ship) {
//                        Global.getCombatEngine().maintainStatusForPlayerShip(
//                                "AI",
//                                "graphics/icons/hullsys/targeting_feed.png",
//                                "AI",
//                                "作战型智能周期性启用中",
//                                false
//                        );
                    }
                    ventAIPlus(ship);
                }
            }
        }

    }

    public static boolean timesPerSec(float times, float amount) {
        return Math.random() < amount * times;
    }

    // 方法：返回HullMod在界面中的参数描述（如显示数值）
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "" + (int) TIME_ACCELERATION_BONUS + "%"; // 时间加速百分比
        if (index == 1) return "" + (int) VENT_RATE_BONUS + "%";
        if (index == 2) return "" + (int) SHIELD_PENALTY + "%";          // 护盾惩罚百分比
        return null;
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        float pad = 2f;
        Color highlight = Misc.getHighlightColor();
        if(!(ship == null)){
            if (ship.getVariant().hasHullMod("CrusadersPlating")) {
                Color h = Misc.getPositiveHighlightColor();
                tooltip.addPara("The ship has a defensive plating covering the surface of the ship based on radiation capacity.", opad, h);
                tooltip.addPara("Provides a total of %s of %s plating based on hull size and radiation capacity", pad, h, "" + Math.round(ship.getMutableStats().getFluxCapacity().getModifiedValue() * mag.get(hullSize)), "Armor-like");
                tooltip.addPara("When the plating is online, it can reduce the damage taken by the ship and has EMP damage reduction.", pad, h);
                tooltip.addPara("Due to plating support, hull armor increased by %s, but damage reduction decreased by %s", pad, h, 40 +"%",(1-ARMOR_MULT)*100+"%");
                tooltip.addSectionHeading("Hold F3 to view detailed mechanics", Alignment.MID, opad);
                if (Keyboard.isKeyDown(Keyboard.KEY_F3)) {
                    tooltip.addPara("After %s seconds without being hit, the plating will regenerate at a rate of %s units per second", pad, h, "2", "10");
                    tooltip.addPara("Once the plating collapses, restart when the plating recharges to %s", pad, h, String.valueOf(Math.round(ship.getMutableStats().getFluxCapacity().getModifiedValue() * mag.get(hullSize) * THRESHOLD_RECOVERY)));
                    tooltip.addPara("The coating effect is halved when overloaded or actively dissipated.", pad, h);
                    tooltip.addPara("Reduces %s beam damage and %s energy damage taken by the hull", pad, h,"25"+ "%","15"+ "%");
                    tooltip.addPara("When a single damage exceeds %s, the plating will be reduced by %s", pad, h,"750", "50"+ "%");
                    tooltip.addPara("When actively dissipating, %s of coating capacity can be restored for each %s unit of radiant energy dissipated.", pad, Misc.getHighlightColor(), "25", "1 unit");
                    tooltip.addPara("When the ship is overloaded, the plating will be destroyed at a rate of %s units per second", pad, h, "" + Math.round(ship.getMutableStats().getFluxCapacity().getModifiedValue() * mag.get(hullSize) * OVERLOAD_DEGRADE));
                    tooltip.addPara("Below %s readiness value, the plating will be partially deactivated", pad, h, Math.round(40.0F) + "%");
                    tooltip.addPara("Coating avoids damage caused by corona", pad, h);
                    tooltip.addPara("And the plating generator fixes the ship's shield generator so that it cannot be modified in any way.", pad, h);
                }

                tooltip.addSectionHeading("Incompatibility", Alignment.MID, opad);
                tooltip.addPara("Hold down F4 to view the ship's incompatible ship plugs", pad, h);
                if (Keyboard.isKeyDown(Keyboard.KEY_F4)) {
                    for (String modId : UNAPPLIED_MOD_IDS) {
                        tooltip.addPara(getHullModName(modId), 2f, highlight);
                    }
                }
            }

            if (ship.getVariant().hasHullMod("Moci_RS_GNShield")) {
                TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/hullmods/CrusadersPlating.png", 35f);
                imageText.addPara("The ship's shield system has been significantly modified:", pad);
                imageText.addPara("Replace the ship's shields with ones that closely fit the ship's surface", pad, highlight);
                imageText.addPara("Greatly reduces the probability of the ship being hit when the shield is deployed.", pad, highlight);
                //imageText.addPara("但使得一些舰体升级难以进行，不兼容 %s ", pad, Color.red ,"扩展发射架");
                tooltip.addImageWithText(15f);
            }
        }

        if(!(ship == null)) {
            Color h = Misc.getHighlightColor();
            //tooltip.addSectionHeading("舰船核心架构", Alignment.MID, opad);
            if (ship.getVariant().hasHullMod("ImpregnableRampart")) {
                tooltip.addSectionHeading("Hard wall adaptive defense architecture", Alignment.MID, opad);
                TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/fortress_shield.png", 35f);
                imageText.addPara("The ship has a unique frame system:", pad);
                imageText.addPara("As the ship's radiation level increases, the power grid system can pressurize the shield to increase shield effectiveness.", pad, h);
                imageText.addPara("However, it makes it difficult to upgrade some hulls and is not compatible with %s", pad, Color.red ,"Extended launcher");
                tooltip.addImageWithText(15f);
            }
            if (ship.getVariant().hasHullMod("PennaVelox")) {
                tooltip.addSectionHeading("Xunyu adaptive air traffic control architecture", Alignment.MID, opad);
                TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/reserve_deployment.png", 35f);
                imageText.addPara("The ship has a unique frame system:", pad);
                imageText.addPara("As the radiation level of the ship increases, the energy system will increase the energy consumption of the maintenance system to improve efficiency.", pad, h);
                imageText.addPara("However, this is at the expense of reducing the hull strength of %s carrier-based aircraft and increasing the damage suffered by %s carrier-based aircraft.", pad, Color.red ,"50%","25%");
                tooltip.addImageWithText(15f);
            }
            if (ship.getVariant().hasHullMod("Rhongomyniad")) {
                tooltip.addSectionHeading("Terminal Adaptive Weapon Correction Architecture", Alignment.MID, opad);
                TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/ammo_feeder.png", 35f);
                imageText.addPara("The ship has a unique frame system:", pad);
                imageText.addPara("As the ship's radiation level increases, the energy system will pressurize the ship's weapon systems to enhance weapon damage performance.", pad, Color.red);
                imageText.addPara("But increases %s shield maintenance cost", pad, Color.red ,"50%");
                tooltip.addImageWithText(15f);
            }
            if (ship.getVariant().hasHullMod("Excalibur")) {
                tooltip.addSectionHeading("Victory Adaptive Energy Distribution Architecture", Alignment.MID, opad);
                TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/plasma_jets.png", 35f);
                imageText.addPara("The ship has a unique frame system:", pad);
                imageText.addPara("As the ship's radiation level increases, the energy system will pressurize the ship's power system to accelerate the ship's time and maneuverability", pad, Color.red);
                imageText.addPara("However, the ship will receive %s more damage, and the shield will receive an additional %s damage.", pad, Color.red ,"25%","35%");
                tooltip.addImageWithText(15f);
            }
        }


    }

    private static final String[] UNAPPLIED_MOD_IDS = {
            "high_scatter_amp",
            "shield_shunt",
            "unstable_injector",
            "augmentedengines",
            "frontshield",
            "frontemitter",
            "extendedshieldemitter",
            "adaptiveshields",
            "additional_berthing",
            "safetyoverrides",
            "recovery_shuttles",

    };

    private String getHullModName(String modId) {
        // 这里可以添加更完整的名称映射
        return switch (modId) {
            case "high_scatter_amp" -> "Highly efficient diffuser";
            case "shield_shunt" -> "Shield diversion";
            case "frontshield" -> "solidified shield";
            case "frontemitter" -> "Directional shield generator";
            case "adaptiveshields" -> "Full-width shield";
            case "extendedshieldemitter" -> "Expand shield";
            case "unstable_injector" -> "unstable thruster";
            case "additional_berthing" -> "Expand dormitory";
            case "safetyoverrides" -> "Security protocol override";
            default -> modId;
        };
    }

    private static final Map<HullSize, Float> RESERVED_FLUX_DEFAULTS = new HashMap<>(4);
    static {
        RESERVED_FLUX_DEFAULTS.put(ShipAPI.HullSize.FRIGATE, 0.8f);
        RESERVED_FLUX_DEFAULTS.put(ShipAPI.HullSize.DESTROYER, 0.8f);
        RESERVED_FLUX_DEFAULTS.put(ShipAPI.HullSize.CRUISER, 0.6f);
        RESERVED_FLUX_DEFAULTS.put(ShipAPI.HullSize.CAPITAL_SHIP, 0.75f);
    }

    private static float getArmorLevel(ShipAPI ship) {
        if (ship == null || !Global.getCombatEngine().isEntityInPlay(ship)) {
            return 0f;
        }

        float current = 0f;
        float total = 0f;
        float worst = 1f;
        ArmorGridAPI armorGrid = ship.getArmorGrid();
        for (int x = 0; x < armorGrid.getGrid().length; x++) {
            for (int y = 0; y < armorGrid.getGrid()[x].length; y++) {
                float fraction = armorGrid.getArmorFraction(x, y);
                current += fraction;
                total += 1f;
                if (fraction < worst) {
                    worst = fraction;
                }
            }
        }

        return (current / total) * (float) Math.sqrt(worst * 0.75f + 0.25f);
    }

    public static void ventAIPlus(ShipAPI ship) {

        FluxTrackerAPI shipFlux = ship.getFluxTracker();
        MutableShipStatsAPI stats = ship.getMutableStats();

        if (ship.getSystem() != null && ship.getSystem().isActive()) return;
        if (ship.getPhaseCloak() != null && ship.getPhaseCloak().isActive()) return;
        if (ship.getTravelDrive() != null && ship.getTravelDrive().isActive()) return;

        float ventRate = stats.getFluxDissipation().getModifiedValue() * 2f * stats.getVentRateMult().getModifiedValue();
        if (ventRate <= 0f) return;

        float maxVentTime = shipFlux.getMaxFlux() / ventRate;

        float armorLevel = getArmorLevel(ship);
        float threatLevel = getThreatLevel(ship);
        float decisionLevel = 4f * (float) Math.sqrt(ship.getHitpoints() * 0.01f) + 0.5f
                * (float) Math.sqrt(ship.getHitpoints() * 0.01f)
                * (float) Math.sqrt(armorLevel * ship.getArmorGrid().getArmorRating() * 0.1f)
                / (maxVentTime * 0.125f);

        float reserved = RESERVED_FLUX_DEFAULTS.get(ship.getHullSize());
        decisionLevel *= (shipFlux.getCurrFlux() + 0.5f * shipFlux.getHardFlux()
                - reserved * shipFlux.getMaxFlux()) / shipFlux.getMaxFlux();

        FleetMemberAPI member = ship.getFleetMember();
        float shipStrength = member != null ? 0.1f + member.getFleetPointCost() : 1f;
        if (threatLevel > shipStrength) {
            decisionLevel *= shipStrength / threatLevel;
        }

        //Global.getCombatEngine().maintainStatusForPlayerShip("14223", "", "des orig", decisionLevel+"", false);


        decisionLevel -= threatLevel;

        if (isInBurst(ship)) {
            decisionLevel *= 0.25f;
        }

        ShipwideAIFlags flags = ship.getAIFlags();
        if (flags.hasFlag(ShipwideAIFlags.AIFlags.DO_NOT_VENT)) {
            decisionLevel *= 0.25f;
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.KEEP_SHIELDS_ON)) {
            decisionLevel *= 0.5f;
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.SAFE_VENT)) {
            decisionLevel *= 1.5f;
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.PURSUING)) {
            decisionLevel *= 0.75f;
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.IN_ATTACK_RUN)) {
            decisionLevel *= 0.75f;
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.POST_ATTACK_RUN)) {
            decisionLevel *= 1.2f;
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.BACK_OFF)) {
            decisionLevel *= 1.2f;
        }

        if (flags.hasFlag(ShipwideAIFlags.AIFlags.RUN_QUICKLY)) {
            decisionLevel *= 1.2f;
        }

        if (shipFlux.getFluxLevel() <= 0.25f) {
            decisionLevel *= shipFlux.getFluxLevel() * 4f;
        } else if (shipFlux.getFluxLevel() >= 0.95f) {
            decisionLevel *= shipFlux.getFluxLevel() * 10f - 8.5f;
        }

        float threshold = ((0.65f * (float) Math.sqrt(ship.getMaxHitpoints() * 0.02f) + 0.05f * (float) Math.sqrt(
                ship.getMaxHitpoints() * 0.02f)
                * (float) Math.sqrt(ship.getArmorGrid().getArmorRating() * 0.25f))
                * maxVentTime * 0.125f) * (1.5f - reserved);


        if (decisionLevel > threshold) {
            ship.giveCommand(ShipCommand.VENT_FLUX, null, 0);
            ship.getAIFlags().removeFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_SHIELDS);
        } else if (decisionLevel > threshold * 0.8f) {
            ship.getAIFlags().setFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_SHIELDS, 2f);
        }
    }

    public static Vector2f getShipCollisionPoint(Vector2f segStart, Vector2f segEnd, ShipAPI ship) {
        if (ship.getCollisionClass() == CollisionClass.NONE) {
            return null;
        } else {
            Vector2f result;

            ShieldAPI shield = ship.getShield();
            if (shield != null && !shield.isOff()) {
                Vector2f circleCenter = shield.getLocation();
                float circleRadius = shield.getRadius();
                if (MathUtils.isPointWithinCircle(segStart, circleCenter, circleRadius)) {
                    result = shield.isWithinArc(segStart) ? segStart : CollisionUtils.getCollisionPoint(segStart, segEnd, ship);
                } else {
                    Vector2f tmp1 = MagicFakeBeam.getCollisionPointOnCircumference(segStart, segEnd, circleCenter, circleRadius);
                    result = tmp1 != null && shield.isWithinArc(tmp1) ? tmp1 : CollisionUtils.getCollisionPoint(segStart, segEnd, ship);
                }
            } else {
                result = CollisionUtils.getCollisionPoint(segStart, segEnd, ship);
            }

            if (result != null && !MathUtils.isWithinRange(result, ship.getLocation(), ship.getCollisionRadius())) {
                result = null;
            }
            return result;
        }
    }

    public static boolean willProjectileHitShipWithInSec(DamagingProjectileAPI proj, ShipAPI ship, float sec) {

        if (sec <= 0f) return false;

        if (proj instanceof MissileAPI missile) {
            if (missile.isMine() && missile.getUntilMineExplosion() < sec) {
                return MathUtils.isWithinRange(ship, missile, missile.getMineExplosionRange());
            }
            if (missile.isGuided() && !missile.isFizzling()) {
                Vector2f projVel = proj.getVelocity();
                float speed = projVel.length();

                float range = speed * sec;
                return MathUtils.isWithinRange(ship, missile, range);
            }
        }

        Vector2f projVel = proj.getVelocity();
        float speed = projVel.length();
        if (speed == 0f) return false;
        Vector2f shipVel = ship.getVelocity();

        // check every 0.1s
        for (float i = 0f; i < sec; i += 0.1f) {
            float projMove = speed * sec * i;
            float shipSpeed = shipVel.length() * sec * i;
            Vector2f relativeLocAfterProjVel = MathUtils.getPointOnCircumference(proj.getLocation(), proj.getCollisionRadius() + projMove, VectorUtils.getFacing(projVel));
            Vector2f relativeLocAfterShipVel = MathUtils.getPointOnCircumference(relativeLocAfterProjVel, shipSpeed, VectorUtils.getFacing(shipVel) + 180f);
            if (getShipCollisionPoint(proj.getLocation(), relativeLocAfterShipVel, ship) != null) return true;
        }
        return false;
    }

    private static float getThreatLevel(ShipAPI ship) {
        FluxTrackerAPI shipFlux = ship.getFluxTracker();
        MutableShipStatsAPI stats = ship.getMutableStats();
        // do not consider self's time mult

        float shipVentRate = stats.getFluxDissipation().getModifiedValue() * 2f * stats.getVentRateMult().getModifiedValue();
        float shipTTV = 20f;
        if (shipVentRate > 0f) {
            shipTTV = shipFlux.getCurrFlux() / shipVentRate;
        }

        float range = ship.getCollisionRadius() * 15f;

        List<DamagingProjectileAPI> nearbyThreats = new ArrayList<>();
        for (DamagingProjectileAPI tmp : getEnemyProjectilesAndMissilesWithinRange(ship.getLocation(), range, ship.getOwner())) {
            if (tmp instanceof MissileAPI missile) {
                if (missile.isFizzling() || missile.isFlare()) continue;
                if (missile.getMissileAI() != null &&
                        missile.getMissileAI().getClass().getSimpleName().contentEquals("RocketAI") &&
                        !willProjectileHitShipWithInSec(tmp, ship, (int)shipTTV + 2)) continue;
                nearbyThreats.add(missile);
            } else {
                if (willProjectileHitShipWithInSec(tmp, ship, (int)shipTTV + 2)) {
                    nearbyThreats.add(tmp);
                }
            }
        }

        FleetMemberAPI member = ship.getFleetMember();
        float shipStrength = member != null ? 0.1f + member.getFleetPointCost() : 1f;
        float armorLevel = getArmorLevel(ship);
        float empCap = EMP_FACTOR * stats.getEmpDamageTakenMult().modified;
        float damageCap = DAMAGE_FACTOR
                * (armorLevel * stats.getArmorDamageTakenMult().modified
                + (1f - armorLevel) * stats.getHullDamageTakenMult().modified);

        float threatLevel = 0f;
        for (DamagingProjectileAPI threat : nearbyThreats) {
            float damage = threat.getDamageAmount() + threat.getEmpAmount() * empCap;
            damage /= (float) Math.sqrt(Math.max((ship.getHitpoints() / 25000f) * armorLevel
                    * ship.getArmorGrid().getArmorRating(), 0.1f));

            threatLevel += (float) Math.pow(damage * (1f + (threat.getDamage().getType().getArmorMult() - 1f) * armorLevel * damageCap * 1.25f), DAMAGE_POWER);

            //Global.getCombatEngine().addFloatingText(threat.getLocation(), "" + damage, 15f, Color.white, ship, 0f, 0f);
        }

        for (BeamAPI beam : Global.getCombatEngine().getBeams()) {
            if (beam.getDamageTarget() != ship) continue;

            float damage;
            if (beam.getWeapon().isBurstBeam()) {
                damage = beam.getWeapon().getDerivedStats().getBurstDamage() * 1.2f
                        + beam.getWeapon().getDerivedStats().getEmpPerSecond() * shipTTV * empCap;
            } else {
                damage = beam.getWeapon().getDerivedStats().getDps() * shipTTV
                        + beam.getWeapon().getDerivedStats().getEmpPerSecond() * shipTTV * empCap;
            }
            damage /= (float) Math.sqrt(Math.max((ship.getHitpoints() / 25000f) * armorLevel
                    * ship.getArmorGrid().getArmorRating(), 0.1f));
            threatLevel += (float) Math.pow(damage * (1f + (beam.getDamage().getType().getArmorMult() - 1f) * armorLevel * damageCap * 0.75f), DAMAGE_POWER);
        }

        float opportunityLevel = 0f;
        for (ShipAPI enemy : AIUtils.getNearbyEnemies(ship, range)) {
            float falloff = 1f;
            float distance = MathUtils.getDistance(ship, enemy);
            if (distance >= range * 0.5f) {
                falloff = (1f - distance / range) * 2f;
            }

            FluxTrackerAPI enemyFT = enemy.getFluxTracker();
            MutableShipStatsAPI enemyMS = enemy.getMutableStats();
            float fluxDifference = ((enemyFT.getMaxFlux() - enemyFT.getCurrFlux()) - (shipFlux.getMaxFlux()
                    - shipFlux.getCurrFlux()))
                    / (shipFlux.getMaxFlux() + 1f);
            if ((enemyFT.isOverloadedOrVenting() || fluxDifference <= -0.5f)
                    && (member == null || !member.isCivilian())) {
                FleetMemberAPI enemyMember = enemy.getFleetMember();
                if (enemyMember != null) {
                    if (ship.getShipTarget() == enemy) {
                        opportunityLevel += 100f * Math.max(-fluxDifference, 0.5f) * (float) Math.sqrt(
                                enemyMember.getFleetPointCost()) / shipStrength;
                    } else {
                        opportunityLevel += 30f * Math.max(-fluxDifference, 0.5f) * (float) Math.sqrt(
                                enemyMember.getFleetPointCost()) / shipStrength;
                    }
                }
            }

            float enemyTime = enemyMS.getTimeMult().getModifiedValue();

            float enemyVentRate = enemyMS.getFluxDissipation().getModifiedValue() * 2f * enemyMS.getVentRateMult().getModifiedValue() * enemyTime;
            float enemyTTV = 0f;
            if (enemyVentRate > 0f) {
                enemyTTV = enemyFT.getCurrFlux() / enemyVentRate;
            }

            if (enemyFT.isOverloaded() && enemyFT.getOverloadTimeRemaining() * enemyTime > shipTTV + 2.5f) {
                continue;
            }
            if (enemyFT.isVenting() && enemyTTV > shipTTV + 2.5f) {
                continue;
            }

            float speedFactor = (float) Math.sqrt(enemy.getMaxSpeed() * enemyTime
                    / (ship.getMaxSpeed() + 20f));

            if (distance <= range * 0.5f) {
                FleetMemberAPI enemyMember = enemy.getFleetMember();
                if (enemyMember != null) {
                    float fall = (range * 0.5f - distance) / (range * 0.5f);
                    if (ship.getShipTarget() == enemy) {
                        threatLevel += speedFactor * 50f * fall
                                * (float) Math.sqrt(enemyMember.getFleetPointCost()) / shipStrength;
                    } else {
                        threatLevel += speedFactor * 20f * fall * (float) Math.sqrt(
                                (enemyMember.getFleetPointCost())) / shipStrength;
                    }
                }
            }

            for (WeaponAPI weapon : enemy.getUsableWeapons()) {
                float speedDiversion = Math.max(0f,
                        enemy.getMaxSpeed() - ship.getMaxSpeed() * 0.5f);
                float rangeSlip = speedDiversion * Math.max(weapon.getCooldownRemaining(), shipTTV);
                float weaponDist = MathUtils.getDistance(ship, weapon.getLocation());
                float weaponRange = weapon.getRange() + rangeSlip;
                float availableFlux = Math.min(enemyFT.getMaxFlux() - enemyFT.getCurrFlux()
                        + speedDiversion * Math.max(weapon.getCooldownRemaining(), shipTTV), enemyFT.getMaxFlux());
                if ((!weapon.isFiring() && weapon.getCooldownRemaining() <= shipTTV)
                        && (weapon.getAmmo() > 0 || !weapon.usesAmmo())
                        && weapon.getFluxCostToFire() <= availableFlux
                        && ((getTimeToAim(weapon, ship.getLocation()) <= shipTTV
                        || weapon.getSpec().getAIHints().contains(WeaponAPI.AIHints.DO_NOT_AIM)) && weaponRange >= weaponDist)) {

                    WeaponSpecAPI spec = weapon.getSpec();
                    WeaponAPI.DerivedWeaponStatsAPI weaponStats = weapon.getDerivedStats();

                    float damage;
                    if (weapon.isBeam()) {
                        if (weapon.isBurstBeam()) {
                            float burstTime = Math.min(spec.getBurstDuration(), shipTTV);
                            damage = 2.5f * weaponStats.getBurstDamage() * burstTime / spec.getBurstDuration()
                                    + weaponStats.getEmpPerSecond() * empCap;
                        } else {
                            damage = (weaponStats.getDps() + weaponStats.getEmpPerSecond() * empCap) * shipTTV;
                        }
                    } else {
                        if (weapon.usesAmmo()) {
                            int burstSize = Math.min((int)(spec.getBurstSize() * (1 + shipTTV / weapon.getCooldown())),
                                    weapon.getAmmo());
                            damage = (weaponStats.getDamagePerShot() + weaponStats.getEmpPerShot() * empCap) * burstSize;

                            float timeLeft = shipTTV - weapon.getCooldown() * burstSize;
                            if (timeLeft > 0f) {
                                int sustainedSize = (int)(spec.getAmmoPerSecond() * timeLeft);
                                damage += (weaponStats.getDamagePerShot() + weaponStats.getEmpPerShot() * empCap) * sustainedSize;
                            }
                        } else {
                            int burstSize = (int)(spec.getBurstSize() * (1 + shipTTV / weapon.getCooldown()));
                            damage = (weaponStats.getDamagePerShot() + weaponStats.getEmpPerShot() * empCap) * burstSize;
                        }

                        if (ship.getMaxSpeed() > 0f && weapon.getProjectileSpeed() > 0f) {
                            damage *= weapon.getProjectileSpeed() / (ship.getMaxSpeed() + weapon.getProjectileSpeed());
                        }
                    }

                    damage = falloff * 1.1f // we have a 1.1 now
                            * (float) Math.pow(damage * (1f + (weapon.getDamageType().getArmorMult() - 1f) * armorLevel)
                            * damageCap, DAMAGE_POWER);

                    //Global.getCombatEngine().addFloatingText(weapon.getLocation(), "" + damage, 15f, Color.white, enemy, 0f, 0f);
                    threatLevel += speedFactor * damage * enemyTime;
                }
            }
        }

        float allyLevel = 0f;
        for (ShipAPI ally : AIUtils.getNearbyAllies(ship, range * 0.5f)) {
            if (ally == ship || ally.isDrone() || ally.isFighter()) continue;
            if (ally.getHullSpec().isCivilianNonCarrier() || ally.isStationModule()) continue;
            if (ally.getFluxTracker().isOverloadedOrVenting()) continue;

            FleetMemberAPI allyMember = ally.getFleetMember();
            if (allyMember != null) {
                allyLevel += allyMember.getFleetPointCost();
            } else {
                if (ally.isFrigate()) {
                    allyLevel += 4f;
                } else if (ally.isDestroyer()) {
                    allyLevel += 8f;
                } else if (ally.isCruiser()) {
                    allyLevel += 14f;
                } else if (ally.isCapital()) {
                    allyLevel += 28f;
                }
            }
        }

        threatLevel = (float) Math.pow(threatLevel, 0.75f) / (float) Math.sqrt(shipStrength);
        threatLevel -= (float) Math.sqrt(allyLevel * 0.3f) * 5f;
        threatLevel += opportunityLevel;//move to here
        return Math.max(threatLevel, 0f);
    }

    private static boolean isInBurst(ShipAPI ship) {
        int burstLevel = 0;
        for (WeaponAPI weapon : ship.getUsableWeapons()) {
            if (((!weapon.isBeam() && weapon.getDerivedStats().getBurstFireDuration() > 0f && weapon.getDerivedStats().getBurstFireDuration() <= 15f)
                    || weapon.isBurstBeam()) && (weapon.getChargeLevel() >= 0.75f || weapon.isFiring()) && weapon.getCooldownRemaining() <= 0.25f) {
                switch (weapon.getSize()) {
                    case LARGE:
                        return true;
                    case MEDIUM:
                        burstLevel += 3;
                        break;
                    case SMALL:
                        burstLevel ++;
                        break;
                    default:
                        break;
                }
            }
        }

        return switch (ship.getHullSize()) {
            case CAPITAL_SHIP -> burstLevel >= 9;
            case CRUISER -> burstLevel >= 5;
            case DESTROYER -> burstLevel >= 3;
            case FRIGATE -> burstLevel > 0;
            default -> false;
        };
    }

}