package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.hullmods.BaseLogisticsHullMod;
import com.fs.starfarer.api.impl.hullmods.CompromisedStructure;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.Moci_RS_CollisionStateManager;
import data.scripts.utils.Moci_TextLoader;
import data.subsystems.Moci_MaMobilitySubsystem;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.subsystems.MagicSubsystemsManager;
import org.magiclib.util.MagicIncompatibleHullmods;

import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.fs.starfarer.api.impl.campaign.ids.HullMods.SAFETYOVERRIDES;

public class SpecializedMobileArmor extends BaseLogisticsHullMod {
    public static final float SENSOR_PROFILE_MULT = 0.5f; // 传感器信号乘数
    public static final float HARD_FLUX_DISSIPATION = 5f; // 耗散硬幅能能力5%
    public static final float WEAPON_ENGINE_DAMAGE_REDUCTION = 0.25f; // 武器引擎伤害减免25%
    public static final float EMP_DAMAGE_REDUCTION = 0.25f; // EMP伤害减免25%
    public static final float SIGHT_RANGE_BONUS = 50f; // 战场视野提高100%
    public static final float NO_OFFICER_CR_PENALTY = 0.15f; // 无军官CR惩罚
    public static float CR_DECAY_MULT = 2.5f; // 战备衰减速率乘数
    public static final float FLUX_SHARE_RATE = 0.005f;

    private static final float SPEED_BONUS = 50f; // 主力舰速度加成
    private static final float TURN_BONUS = 50f; // 主力舰转向加成


    private static final Color ENGINE_COLOR = new Color(80, 255, 0, 255); // 主力舰引擎颜色
    private static final Color JITTER_COLOR = new Color(0, 255, 0, 100); // 绿色抖动效果颜色
    private static final Color PARTICLE_EDGE_COLOR = new Color(80, 255, 0, 197); // 粒子边缘颜色
    private static final float PARTICLE_INTERVAL = 0.1f; // 粒子生成间隔时间（秒）
    private static final String PARTICLE_TIMER_KEY = "moci_psycommu_particle_timer"; // 粒子计时器存储键

    private static final String SYSTEM_ID = "Moci_SpecializedMobileArmorSystem";
    private static final String NO_OFFICER_KEY = "moci_specialize_mobile_armor_no_officer";
    private static final String TEXT_ID = "Moci_LandingSequence";
    private final Moci_RS_CollisionStateManager collisionManager = Moci_RS_CollisionStateManager.getInstance();

    private static final String ENGINE_COLOR_KEY = "moci_ms_idcard_engine_color";
    private static final String FRIGATE_RULES_KEY = "moci_frigate_rules_active";
    private static final String COLLISION_ONLY_KEY = "moci_collision_only_active";
    private static final String COLLISION_APPLIED_KEY = "moci_collision_applied";

    private static final String COLLISION_MODIFIER_ID = "Moci_MobileArmorsIDcard";
    private static final int COLLISION_PRIORITY = 150;

    public static final Map<String, Float> GROUND_BONUS = new HashMap<>();
    static {
        GROUND_BONUS.put("rs_Tr_1_owsla", 35f);
        GROUND_BONUS.put("rs_Tr_1_owsla_assault", 25f);
        GROUND_BONUS.put("rs_Tr_1_owsla_defense", 20f);
        GROUND_BONUS.put("rs_Tr_1_owsla_firesupport", 15f);
        GROUND_BONUS.put("rs_Tr_6_haznthley", 20f);
        GROUND_BONUS.put("rs_Tr_6_haznthley_assault", 20f);
        GROUND_BONUS.put("vow_VMS_12", 15f);
        GROUND_BONUS.put("vow_VMS_12_heavyweapon", 10f);
        GROUND_BONUS.put("vow_VMS_14", 40f);
        GROUND_BONUS.put("vow_VMS_14_assault", 30f);
        GROUND_BONUS.put("vow_VMS_14_assault_d", 30f);
        GROUND_BONUS.put("vow_VMS_14_firesupport", 30f);
        GROUND_BONUS.put("vow_VMS_14_firesupport_d", 30f);
        GROUND_BONUS.put("vow_VMS_15",50f);
        GROUND_BONUS.put("vow_VMS_15_assault",40f);
        GROUND_BONUS.put("vow_VMS_15_assault_d",40f);
        GROUND_BONUS.put("vow_VMS_15_firesupport",45f);
        GROUND_BONUS.put("vow_VMS_15_firesupport_d",45f);
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // 传感器信号减半
        stats.getSensorProfile().modifyMult(id, SENSOR_PROFILE_MULT);
        // 25%的耗散硬幅能能力
        stats.getHardFluxDissipationFraction().modifyFlat(id, HARD_FLUX_DISSIPATION / 100f);

        stats.getDynamic().getMod("ground_support").modifyFlat(id, 75.0F);

        // 降低武器和引擎受到的伤害
        stats.getWeaponDamageTakenMult().modifyMult(id, 1f - WEAPON_ENGINE_DAMAGE_REDUCTION);
        stats.getEngineDamageTakenMult().modifyMult(id, 1f - WEAPON_ENGINE_DAMAGE_REDUCTION);

        // 降低EMP伤害
        stats.getEmpDamageTakenMult().modifyMult(id, 1f - EMP_DAMAGE_REDUCTION);
        if (stats.getVariant().hasHullMod(SAFETYOVERRIDES)) {
            MagicIncompatibleHullmods.removeHullmodWithWarning(stats.getVariant(), SAFETYOVERRIDES, spec.getId());
        }
        // 检查军官状态，直接应用CR惩罚和船员需求惩罚
        if (stats.getEntity() instanceof ShipAPI ship) {
            if (ship.getCaptain() == null || ship.getCaptain().isDefault()) {
                stats.getMaxCombatReadiness().modifyFlat(id + "_no_officer", -NO_OFFICER_CR_PENALTY);
                stats.getCRLossPerSecondPercent().modifyMult(id + "_no_officer" , CR_DECAY_MULT);
            }else{
                // 战场视野提高50%
                stats.getSightRadiusMod().modifyPercent(id, SIGHT_RANGE_BONUS);
            }
        } else if (stats.getFleetMember() != null) {
            // 战役模式下检查舰队成员的军官
            if (stats.getFleetMember().getCaptain() == null || stats.getFleetMember().getCaptain().isDefault()) {
                stats.getMaxCombatReadiness().modifyFlat(id + "_no_officer", -NO_OFFICER_CR_PENALTY);
                stats.getCRLossPerSecondPercent().modifyMult(id + "_no_officer" , CR_DECAY_MULT);
            }else{
                // 战场视野提高50%
                stats.getSightRadiusMod().modifyPercent(id, SIGHT_RANGE_BONUS);
            }
        }
    }

    private void hideWeaponCovers(ShipAPI ship) {
        if (ship.getLargeHardpointCover() != null) {
            ship.getLargeHardpointCover().setSize(0.0f, 0.0f);
        }
        if (ship.getMediumHardpointCover() != null) {
            ship.getMediumHardpointCover().setSize(0.0f, 0.0f);
        }
        if (ship.getSmallHardpointCover() != null) {
            ship.getSmallHardpointCover().setSize(0.0f, 0.0f);
        }
        if (ship.getLargeTurretCover() != null) {
            ship.getLargeTurretCover().setSize(0.0f, 0.0f);
        }
        if (ship.getMediumTurretCover() != null) {
            ship.getMediumTurretCover().setSize(0.0f, 0.0f);
        }
        if (ship.getSmallTurretCover() != null) {
            ship.getSmallTurretCover().setSize(0.0f, 0.0f);
        }

        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon != null && (weapon.getSlot().isTurret() || weapon.getSlot().isHardpoint())) {
                weapon.ensureClonedSpec();
            }
        }
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if(!(ship.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP)) {
            try {
                Moci_MaMobilitySubsystem Subsystem = new Moci_MaMobilitySubsystem(ship);
                // 注册到MagicLib子系统管理器
                MagicSubsystemsManager.addSubsystemToShip(ship, Subsystem);
            } catch (Exception e) {
                // 记录错误日志，便于调试
                Global.getLogger(this.getClass()).error("Failed to add subsystem:" + e.getMessage(), e);
            }
        }

        // 添加防殉爆和碰撞伤害监听器
        if (!ship.hasListenerOfClass(MobileArmorDamageListener.class)) {
            ship.addListener(new MobileArmorDamageListener());
        }

        // 移除模块幅能共享监听器
        if (!ship.hasListenerOfClass(ModuleFluxSharingListener.class)) {
             ship.addListener(new ModuleFluxSharingListener(ship));
         }

        // 移除无军官CR惩罚检查监听器（改为直接在applyEffectsBeforeShipCreation中处理）
        // if (!ship.hasListenerOfClass(NoOfficerCRPenaltyListener.class)) {
        //     ship.addListener(new NoOfficerCRPenaltyListener(ship, id));
        // }
    }

    /**
     * 防殉爆和碰撞伤害监听器
     */
    public static class MobileArmorDamageListener implements DamageTakenModifier {
        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
            if (!(target instanceof ShipAPI)) {
                return null;
            }

            // 处理殉爆伤害
            if (param instanceof DamagingProjectileAPI projectile) {
                ShipAPI source = projectile.getSource();
                if (source != null) {
                    float explosionRadius = DamagingExplosionSpec.getShipExplosionRadius(source);
                    if ((projectile.getCollisionRadius() - explosionRadius) == 0) {
                        // 殉爆伤害减免
                        damage.getModifier().modifyMult(this.getClass().getName(), 0f);
                        return "Mobile armor explosion protection";
                    }
                }
            }

            // 处理碰撞伤害 - 动能伤害且param为null才是碰撞伤害
            if (param == null && damage.getType() == DamageType.KINETIC) {
                // 碰撞伤害减免
                damage.getModifier().modifyMult(this.getClass().getName(), 0f);
                return "Mobile armor collision protection";
            }

            return null;
        }
    }

    /**
     * 模块幅能共享监听器
     */

    public static class ModuleFluxSharingListener implements AdvanceableListener {
        private final ShipAPI ship;
        private final IntervalUtil checkInterval = new IntervalUtil(0.1f, 0.1f);

        public ModuleFluxSharingListener(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void advance(float amount) {
            if (!ship.isAlive()) {
                ship.removeListener(this);
                return;
            }

            checkInterval.advance(amount);
            if (!checkInterval.intervalElapsed()) {
                return;
            }

            // 检查所有模块
            for (ShipAPI module : ship.getChildModulesCopy()) {
                if (module != null && module.isAlive()) {
                    shareFluxWithModule(ship, module);
                }
            }

            // 如果是模块，与母舰共享
            if (ship.getParentStation() != null && ship.getParentStation().isAlive()) {
                shareFluxWithModule(ship.getParentStation(), ship);
            }
        }

        private void shareFluxWithModule(ShipAPI mainShip, ShipAPI module) {
            float mainFluxLevel = mainShip.getFluxLevel();
            float moduleFluxLevel = module.getFluxLevel();

            // 计算幅能等级差异
            float levelDifference = Math.abs(mainFluxLevel - moduleFluxLevel);

            // 如果差异很小，就不需要共享
            if (levelDifference < 0.05f) {
                return;
            }

            // 确定转移方向：从高的向低的转移
            boolean transferToModule = mainFluxLevel > moduleFluxLevel;

            // 计算转移量
            float transferAmount;
            if (transferToModule) {
                // 从主舰向模块转移
                float availableFlux = mainShip.getCurrFlux();
                float moduleCapacity = module.getMaxFlux() - module.getCurrFlux();
                float targetTransfer = levelDifference * Math.min(mainShip.getMaxFlux(), module.getMaxFlux()) * FLUX_SHARE_RATE;

                transferAmount = Math.min(targetTransfer, Math.min(availableFlux, moduleCapacity));

                if (transferAmount > 0) {
                    mainShip.getFluxTracker().decreaseFlux(transferAmount);
                    module.getFluxTracker().increaseFlux(transferAmount, false);
                }
            } else {
                // 从模块向主舰转移
                float availableFlux = module.getCurrFlux();
                float mainCapacity = mainShip.getMaxFlux() - mainShip.getCurrFlux();
                float targetTransfer = levelDifference * Math.min(mainShip.getMaxFlux(), module.getMaxFlux()) * FLUX_SHARE_RATE;

                transferAmount = Math.min(targetTransfer, Math.min(availableFlux, mainCapacity));

                if (transferAmount > 0) {
                    module.getFluxTracker().decreaseFlux(transferAmount);
                    mainShip.getFluxTracker().increaseFlux(transferAmount, false);
                }
            }
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null) return;
        ship.setCollisionClass(CollisionClass.FIGHTER);
        hideWeaponCovers(ship);

        if ( ship.getSystem() == null) return;

        if(!(ship.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP) || !(ship.getHullSize() == ShipAPI.HullSize.FIGHTER)) {
            if (ship.getVariant() == null || !ship.getVariant().hasHullMod("Moci_LandingSequence") || !ship.getVariant().hasHullMod("Moci_SMALandingSequence")) {
                Moci_SMALandingSequence.advanceLandingLogic(ship, amount);
            }
        }

        boolean systemActive = ship.getSystem().isActive();
        if(!(ship.getCaptain() ==null)) {
            if (!ship.getCaptain().isDefault()) {
                MutableShipStatsAPI stats = ship.getMutableStats();
                if (systemActive) {
                    stats.getMaxSpeed().modifyFlat("Moci_SpecializedMobileArmorSystem", SPEED_BONUS); // 最大速度加成
                    stats.getAcceleration().modifyPercent("Moci_SpecializedMobileArmorSystem", SPEED_BONUS * 2f); // 加速度加成
                    stats.getDeceleration().modifyPercent("Moci_SpecializedMobileArmorSystem", SPEED_BONUS * 2f); // 减速度加成
                    stats.getTurnAcceleration().modifyFlat("Moci_SpecializedMobileArmorSystem", TURN_BONUS); // 转向加速度加成
                    stats.getTurnAcceleration().modifyPercent("Moci_SpecializedMobileArmorSystem", TURN_BONUS * 3f); // 转向加速度百分比加成
                    stats.getMaxTurnRate().modifyFlat("Moci_SpecializedMobileArmorSystem", 15f); // 最大转向速度加成
                    stats.getMaxTurnRate().modifyPercent("Moci_SpecializedMobileArmorSystem", 50f); // 最大转向速度百分比加成

                    // 主力舰引擎光效调整
                    ship.getEngineController().fadeToOtherColor(this, ENGINE_COLOR, new Color(0, 0, 0, 0), 1f, 0.67f);
                    ship.getEngineController().extendFlame(this, 2f, 1.5f, 1.5f);

                    ship.setJitterUnder(ship, JITTER_COLOR, 0.8f, 30, 0, 10);

                    // 处理粒子效果生成 - 每个舰船独立的计时器
                    IntervalUtil particleInterval = (IntervalUtil) ship.getCustomData().get(PARTICLE_TIMER_KEY);
                    if (particleInterval == null) {
                        particleInterval = new IntervalUtil(PARTICLE_INTERVAL, PARTICLE_INTERVAL);
                        ship.setCustomData(PARTICLE_TIMER_KEY, particleInterval);
                    }
                    particleInterval.advance(amount);
                    if (particleInterval.intervalElapsed()) {
                        spawnParticles(ship);
                    }
                } else {
                    // 系统关闭时移除所有效果

                    // 移除所有可能的效果修改
                    stats.getEnergyWeaponFluxCostMod().unmodify("Moci_SpecializedMobileArmorSystem"); // 移除能量武器赋能消耗修改
                    stats.getBallisticWeaponFluxCostMod().unmodify("Moci_SpecializedMobileArmorSystem"); // 移除实弹武器赋能消耗修改
                    stats.getEnergyRoFMult().unmodify("Moci_SpecializedMobileArmorSystem"); // 移除能量武器射速修改
                    stats.getBallisticRoFMult().unmodify("Moci_SpecializedMobileArmorSystem"); // 移除实弹武器射速修改
                    // 移除主力舰机动性效果
                    stats.getMaxSpeed().unmodify("Moci_SpecializedMobileArmorSystem"); // 移除最大速度修改
                    stats.getAcceleration().unmodify("Moci_SpecializedMobileArmorSystem"); // 移除加速度修改
                    stats.getDeceleration().unmodify("Moci_SpecializedMobileArmorSystem"); // 移除减速度修改
                    stats.getTurnAcceleration().unmodify("Moci_SpecializedMobileArmorSystem"); // 移除转向加速度修改
                    stats.getMaxTurnRate().unmodify("Moci_SpecializedMobileArmorSystem"); // 移除最大转向速度修改
                }
            }
        }


        if (ship.getCustomData().get(FRIGATE_RULES_KEY) != null
                || ship.getCustomData().get(COLLISION_ONLY_KEY) != null) {

            // 检查主舰船的碰撞类型，只有非NONE时才修改
            CollisionClass defaultCollision = collisionManager.getDefaultCollision(ship);
            if (defaultCollision != null && defaultCollision != CollisionClass.NONE) {
                // 检查当前碰撞类型是否正确
                if (ship.getCollisionClass() != CollisionClass.FIGHTER) {
                    // 使用碰撞管理器设置碰撞类型为战机
                    collisionManager.setCollisionModifier(ship, COLLISION_MODIFIER_ID, COLLISION_PRIORITY, CollisionClass.FIGHTER);
                }
            }
        }

        updateLaunchTimer(ship, amount);

        ship.syncWeaponDecalsWithArmorDamage();

        // 隐藏武器覆盖物贴图
        hideWeaponCovers(ship);
//        if (ship.getEngineController() != null) {
//            if (ship.getHullSize() == ShipAPI.HullSize.DESTROYER && ship.getHullSize() == ShipAPI.HullSize.FRIGATE && ship.getHullLevel() > 0.3F && !ship.getFluxTracker().isOverloadedOrVenting()) {
//                CombatEngineAPI engine = Global.getCombatEngine();
//                if (engine != null && !ship.isHulk() && engine.isEntityInPlay(ship)) {
//                    ShipwideAIFlags flags = ship.getAIFlags();
//                    if (flags != null) {
//                        flags.setFlag(ShipwideAIFlags.AIFlags.DO_NOT_BACK_OFF, 0.5F);
//                    }
//                }
//            }
//        }

    }

    private void spawnParticles(ShipAPI ship) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        Vector2f shipLocation = ship.getLocation();
        Vector2f shipVelocity = ship.getVelocity();

        // 每次生成20个粒子
        for (int i = 0; i < 20; i++) {
            // 计算随机角度
            float angle = (float) (Math.random() * 360);
            // 计算起始位置(距离舰船中心100f)
            Vector2f offset = new Vector2f(
                    (float) Math.cos(Math.toRadians(angle)) * 50f,
                    (float) Math.sin(Math.toRadians(angle)) * 50f
            );
            Vector2f loc = Vector2f.add(shipLocation, offset, null);

            // 计算速度(向外扩散并加上舰船速度)
            Vector2f vel = new Vector2f(offset.x, offset.y);
            vel.normalise();
            vel.scale(150f); // 基础扩散速度
            Vector2f.add(vel, shipVelocity, vel); // 叠加舰船速度

            // 生成绿色边缘粒子
            engine.addHitParticle(
                    loc, // 从距离舰船100f的位置开始
                    vel, // 包含舰船速度的粒子速度
                    5, // 粒子大小
                    2f, // 粒子亮度
                    0.75f, // 持续时间
                    PARTICLE_EDGE_COLOR
            );
        }
    }

    @Override
    public boolean shouldAddDescriptionToTooltip(ShipAPI.HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        float pad = 3f;
        Color h = Misc.getHighlightColor();
        Color good = Misc.getPositiveHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        tooltip.addPara("As a unique design type of ship, %s's combat performance is different from ordinary ships.", pad, h, "Specialized Mobile Armor (SMA)");

        tooltip.addPara("%s is built into the body frame, which can greatly improve the performance of the body when driven by %s.", pad, h, "Mental Manipulation Framework", "pilot");

        tooltip.addPara("In order to pursue the ultimate mobile firepower delivery, the SMA is designed to have extremely strong maneuverability and will not be harmed by collisions and explosions.", pad, h);

        tooltip.addPara("Since each subsystem has been specially specialized, security protocol override modification cannot be performed.", pad, h);

        tooltip.addPara("Can provide %s combat support during landing operations.", pad, h, "75");

        // 防护能力
        tooltip.addSectionHeading("Key protection", com.fs.starfarer.api.ui.Alignment.MID, pad);

        tooltip.addPara("Weapons and engines take %s less damage.", pad, good, (int)(WEAPON_ENGINE_DAMAGE_REDUCTION * 100f) + "%");

        tooltip.addPara("EMP damage taken reduced by %s.", pad, good, (int)(EMP_DAMAGE_REDUCTION * 100f) + "%");
        // 幅能系统
        tooltip.addSectionHeading("amplitude energy system", com.fs.starfarer.api.ui.Alignment.MID, pad);

        tooltip.addPara("Gained %s ability to dissipate %s when shield is deployed.", pad, new Color[]{good, h}, (int)HARD_FLUX_DISSIPATION + "%","Hard amplitude");

        tooltip.addPara("The ship's %s will automatically balance the energy level of each part between the main body and each module %s.", pad, h, "Amplitude energy", "Share in real time");


        // 感知系统
        tooltip.addSectionHeading("Perception system", com.fs.starfarer.api.ui.Alignment.MID, pad);
        tooltip.addPara("Sensor signal decreased by %s.", pad, good, (int)((1f - SENSOR_PROFILE_MULT) * 100f) + "%");
        tooltip.addPara("Battlefield vision is increased by %s when piloted by an officer.", pad, good, (int)SIGHT_RANGE_BONUS + "%");

        // 军官要求
        tooltip.addSectionHeading("Ship characteristics", com.fs.starfarer.api.ui.Alignment.MID, pad);
        tooltip.addPara("Maximum combat readiness is reduced by %s when piloting without an officer.", pad, bad, (int)(NO_OFFICER_CR_PENALTY * 100f) + "%");
        tooltip.addPara("The combat readiness value decay rate increases by %s when piloted without an officer.", pad, bad, (int)(CR_DECAY_MULT * 100f) + "%");

        tooltip.addSectionHeading("Mental Manipulation Framework", com.fs.starfarer.api.ui.Alignment.MID, pad);
        tooltip.addSectionHeading("The following effects are activated when the tactical system is activated", new Color(151, 255, 0,255), new Color(160, 255, 0,50), Alignment.MID, opad); // 与其他元素之间的间距
        tooltip.addPara("- The maximum speed of the ship is increased by %s points", pad, good, (int)(SPEED_BONUS) + "%");
        tooltip.addPara("- The ship's %s has been greatly increased", pad, good, "flexibility");

        tooltip.addSectionHeading("Mobile weapons installed with this ship can be landed on conventional ships for maintenance.", Alignment.MID, opad);
        tooltip.addPara("Hold F3 to view detailed mechanics", pad, good);
        if (Keyboard.isKeyDown(Keyboard.KEY_F3)) {
            tooltip.addSectionHeading(Moci_TextLoader.getText(TEXT_ID, "description.ai_heading"), com.fs.starfarer.api.ui.Alignment.MID, pad);

            tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.ai_intro"), 0f, new Color[]{h, h},
                    Moci_TextLoader.getHighlights(TEXT_ID, "description.ai_intro_highlights").toArray(new String[0]));
            tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.ai_cond_hull"), 0f, h,
                    Moci_TextLoader.getHighlights(TEXT_ID, "description.ai_cond_hull_highlights").toArray(new String[0]));
            tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.ai_cond_cr"), 0f, h,
                    Moci_TextLoader.getHighlights(TEXT_ID, "description.ai_cond_cr_highlights").toArray(new String[0]));
            tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.ai_cond_ammo"), 0f, h,
                    Moci_TextLoader.getHighlights(TEXT_ID, "description.ai_cond_ammo_highlights").toArray(new String[0]));

            tooltip.addSectionHeading(Moci_TextLoader.getText(TEXT_ID, "description.player_heading"), com.fs.starfarer.api.ui.Alignment.MID, pad);

            tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.player_intro"), pad);
            tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.player_step_target"), 0f, h,
                    Moci_TextLoader.getHighlights(TEXT_ID, "description.player_step_target_highlights").toArray(new String[0]));
            tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.player_step_auto"), 0f, h,
                    Moci_TextLoader.getHighlights(TEXT_ID, "description.player_step_auto_highlights").toArray(new String[0]));

            tooltip.addSectionHeading(Moci_TextLoader.getText(TEXT_ID, "description.notes_heading"), com.fs.starfarer.api.ui.Alignment.MID, pad);

            tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.notes_carrier"), 0f, new Color[]{h, h},
                    Moci_TextLoader.getHighlights(TEXT_ID, "description.notes_carrier_highlights").toArray(new String[0]));
            tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.notes_cancel"), 0f, h,
                    Moci_TextLoader.getHighlights(TEXT_ID, "description.notes_cancel_highlights").toArray(new String[0]));
        }

        if (!ship.getCaptain().isDefault()) {
            tooltip.addPara("The ship already has an officer and can obtain the above effects.", opad, good);
        } else {
            tooltip.addPara("The ship has no officers and cannot activate the telepathic frame system.", opad, Misc.getNegativeHighlightColor());
        }

    }

    private void updateLaunchTimer(ShipAPI ship, float amount) {
        Object timerObj = ship.getCustomData().get(data.scripts.shipsystems.Moci_SMALaunchTravelDrive.LAUNCH_TIMER_KEY);
        if (timerObj instanceof Float) {
            float timer = (Float) timerObj;

            if (timer > 0f) {
                timer -= amount;

                if (timer <= 0f) {
                    // 时间到了，执行两个操作：

                    // 1. 移除碰撞修改器，恢复默认碰撞状态
                    collisionManager.removeCollisionModifier(ship, "moci_mslaunch_fighter_collision");

                    // 2. 释放航母槽位
                    Object carrierIdObj = ship.getCustomData().get("moci_launch_carrier_id");
                    Object slotIdObj = ship.getCustomData().get("moci_launch_slot_id");

                    // carrier.getId() 返回 String 类型
                    if (carrierIdObj instanceof String carrierId && slotIdObj instanceof String slotId) {

                        // 释放具体槽位标记
                        Global.getCombatEngine().getCustomData().remove("moci_launchSlots" + carrierId + "_" + slotId);

                        // 减少总槽位计数
                        Object takenSlotsObj = Global.getCombatEngine().getCustomData().get("moci_launchSlots" + carrierId);
                        if (takenSlotsObj instanceof Integer) {
                            int takenSlots = (Integer) takenSlotsObj;
                            if (takenSlots > 0) {
                                Global.getCombatEngine().getCustomData().put("moci_launchSlots" + carrierId, takenSlots - 1);
                            }
                        }

                        // 清除舰船上的槽位信息
                        ship.removeCustomData("moci_launch_carrier_id");
                        ship.removeCustomData("moci_launch_slot_id");
                    }

                    // 清除计时器
                    ship.removeCustomData(data.scripts.shipsystems.Moci_SMALaunchTravelDrive.LAUNCH_TIMER_KEY);
                } else {
                    // 更新计时器
                    ship.setCustomData(data.scripts.shipsystems.Moci_SMALaunchTravelDrive.LAUNCH_TIMER_KEY, timer);
                }
            }
        }
    }

    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        // 1. 隐藏尾迹与引擎颜色
        if (member.getFleetData() != null && member.getFleetData().getFleet() != null
                && member.getFleetData().getFleet().getViewForMember(member) != null) {
            member.getFleetData().getFleet().getViewForMember(member).getContrailColor().setBase(new Color(0f,0f,0f,0f));
            member.getFleetData().getFleet().getViewForMember(member).getEngineColor().setBase(new Color(0f,0f,0f,0f));
            member.setSpriteOverride("");
        }
        // 2. 计算军官等级加成
        float level = member.getCaptain().isDefault() ? 0 : member.getCaptain().getStats().getLevel() * 1.5f;
        // 3. 检查 CR 是否满足地面行动要求
        if (member.getRepairTracker().getBaseCR() >= getCRPenalty(member.getVariant())) {
            // 如果船体基础 ID 在 GROUND_BONUS 中存在
            if (GROUND_BONUS.get(member.getHullSpec().getBaseHullId()) != null)
                member.getStats().getDynamic().getMod(Stats.FLEET_GROUND_SUPPORT)
                        .modifyFlat("id", GROUND_BONUS.get(member.getHullSpec().getBaseHullId()) + level);
        } else {
            member.getStats().getDynamic().getMod(Stats.FLEET_GROUND_SUPPORT).unmodify("id");
        }
    }

    private static final float CR_PENALTY = 0.10f;

    public static float getCRPenalty(ShipVariantAPI variant) {
        float scale = 1f;

        Collection<String> hullMods = variant.getHullMods();
        for (String hullMod : hullMods) {
            HullModSpecAPI modSpec = Global.getSettings().getHullModSpec(hullMod);
            if (modSpec.hasTag(Tags.HULLMOD_DMOD)) {
                scale /= CompromisedStructure.DEPLOYMENT_COST_MULT;
            }
        }

        return scale * CR_PENALTY;
    }

}