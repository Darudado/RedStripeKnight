package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lazywizard.lazylib.combat.entities.AnchoredEntity;
import org.magiclib.util.MagicAnim;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

public class PhaseboostDrive extends BaseShipSystemScript {
    // 常量定义
    public static final float INSTANT_BOOST_FLAT = 350.0F;  // 基础速度提升值
    // 根据不同船体尺寸配置的映射表
    public static final Map<HullSize, Float> SPEED_FALLOFF_PER_SEC = new HashMap<>();  // 速度衰减率
    public static final Map<HullSize, Float> BASELINE_MULT = new HashMap<>();          // 基础推进倍率
    public static final Map<HullSize, Float> FORWARD_PENALTY = new HashMap<>();        // 前进方向惩罚
    public static final Map<HullSize, Float> REVERSE_PENALTY = new HashMap<>();        // 后退方向惩罚
    // 系统阶段时间覆盖
    public static final Map<HullSize, Float> IN_OVERRIDE = new HashMap<>();            // 启动阶段时间
    public static final Map<HullSize, Float> ACTIVE_OVERRIDE = new HashMap<>();        // 激活阶段时间
    public static final Map<HullSize, Float> OUT_OVERRIDE = new HashMap<>();           // 结束阶段时间
    // 系统使用限制
    public static final Map<HullSize, Integer> USES_OVERRIDE = new HashMap<>();        // 使用次数
    public static final Map<HullSize, Float> REGEN_OVERRIDE = new HashMap<>();         // 系统恢复速率
    public static final Map<HullSize, Float> ELITE_REGEN_MULT = new HashMap<>();       // 精英船体恢复倍率
    // 内部使用的映射表
    private static final Map<HullSize, Float> EXTEND_TIME = new HashMap<>();           // 引擎动画扩展时间
    private static final Map<HullSize, Float> MAX_FRAC_OUT = new HashMap<>();          // 结束阶段最大推进比例
    private static final Map<HullSize, Float> BOOST_MULT = new HashMap<>();            // 推进倍率乘数

    // 颜色常量 (标准模式)
    private static final Color ENGINE_COLOR_STANDARD;
    private static final Color CONTRAIL_COLOR_STANDARD;
    private static final Color BOOST_COLOR_STANDARD;
    // 颜色常量 (目标锁定模式 - 当装备特定船体插件时使用)
    private static final Color ENGINE_COLOR_TARGETING;
    private static final Color CONTRAIL_COLOR_TARGETING;
    private static final Color BOOST_COLOR_TARGETING;

    private static final Vector2f ZERO = new Vector2f();  // (0,0) 向量常量

    // 状态标识键
    private final Object STATUSKEY1 = new Object();  // 状态显示标识
    private final Object ENGINEKEY2 = new Object();  // 引擎特效标识
    private final Object WEAPONKEY3 = new Object();
    private final Object REPAIRKEY4 = new Object();


    private final Map<Integer, Float> engState = new HashMap<>();  // 存储每个引擎的状态值
    private boolean started = false;  // 系统启动标记
    private boolean ended = false;    // 系统结束标记
    private final IntervalUtil interval = new IntervalUtil(0.015F, 0.015F);  // 定时器，用于控制特效生成频率
    private float boostScale = 0.75F;       // 推进缩放比例
    private float boostVisualDir = 0.0F;    // 推进视觉方向
    private boolean boostForward = false;   // 是否向前推进

    private boolean weaponOverloadActive = false;
    private float weaponOverloadTimer = 0f;
    private static final float WEAPON_OVERLOAD_DAMAGE_BONUS = 50f; // 伤害加成百分比
    private static final float WEAPON_OVERLOAD_DURATION = 2.0f;    // 持续时间（秒）

    private static final float FLUX_REDUCTION = 0.15f; // 减少15%辐能



    // =============== 核心系统逻辑 ===============
    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        // 1. 计算平滑的动画效果值 (使用MagicAnim库实现多段平滑)
        float effect = Math.min(1.0F, Math.max(0.0F,
                MagicAnim.smoothReturnNormalizeRange(effectLevel, 0.0F, 1.0F) / 2.0F +
                        MagicAnim.smoothReturnNormalizeRange(effectLevel * 1.5F, 0.0F, 1.0F) / 2.0F +
                        MagicAnim.smoothReturnNormalizeRange(effectLevel * 2.0F, 0.0F, 1.0F) / 2.0F));

        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship != null) {
            float shipRadius = ship.getCollisionRadius();
            // 获取真实时间增量 (考虑游戏时间倍率)
            float amount = Global.getCombatEngine().getElapsedInLastFrame();
            float objectiveAmount = amount * Global.getCombatEngine().getTimeMult().getModifiedValue();

            // 暂停处理
            if (Global.getCombatEngine().isPaused()) {
                amount = 0.0F;
                objectiveAmount = 0.0F;
            }

            // 2. 相位状态切换
            if (!stats.getTimeMult().getPercentMods().containsKey(id)) {
                // 初次激活: 播放音效并进入相位状态
                Global.getSoundPlayer().playSound("cr_booster_activate", 1.0F, 1.66F, ship.getLocation(), ship.getVelocity());
                ship.setPhased(true);
            } else if (ship.isPhased()) {
                // 防止意外退出相位状态
                ship.setPhased(false);
            }

            // 3. 根据船体插件选择颜色模式
            Color ENGINE_COLOR = ENGINE_COLOR_STANDARD;
            Color CONTRAIL_COLOR = CONTRAIL_COLOR_STANDARD;
            Color BOOST_COLOR = BOOST_COLOR_STANDARD;
            float afterimageIntensity = 1.0F;  // 残影强度

            if (ship.getVariant().hasHullMod("PolariphaseDrive")) {
                // 目标锁定模式配置
                ENGINE_COLOR = ENGINE_COLOR_TARGETING;
                CONTRAIL_COLOR = CONTRAIL_COLOR_TARGETING;
                BOOST_COLOR = BOOST_COLOR_TARGETING;
                afterimageIntensity = 1.5F;  // 增强残影效果
            }




            // 5. 引擎火焰效果控制
            ship.getEngineController().extendFlame(this.ENGINEKEY2, 0.0F, effectLevel, 3.0F * effectLevel);

            // 6. 确定推进方向
            if (!this.ended) {
                Vector2f direction = new Vector2f();
                // 根据引擎控制状态确定方向向量
                if (ship.getEngineController().isAccelerating()) {
                    direction.y += 1;  // 前进
                } else if (ship.getEngineController().isAcceleratingBackwards() || ship.getEngineController().isDecelerating()) {
                    direction.y -= 1;  // 后退
                }
                if (ship.getEngineController().isStrafingLeft()) {
                    direction.x -= 1;  // 左平移
                } else if (ship.getEngineController().isStrafingRight()) {
                    direction.x += 1;  // 右平移
                }

                // 默认方向向前
                if (direction.length() <= 0.0F) {
                    direction.y = 1.0F;
                }

                // 计算视觉方向角度
                this.boostVisualDir = MathUtils.clampAngle(VectorUtils.getFacing(direction) - 90.0F);
            }

            // 7. 系统启动阶段 (IN) 处理
            if (state == State.IN) {
                if (!this.started) {
                    // 初次启动音效
                    if (ship.isFighter()) {
                        Global.getSoundPlayer().playSound("cr_booster_activate", 1.1F, 0.6F, ship.getLocation(), ZERO);
                    } else {
                        Global.getSoundPlayer().playSound("cr_booster_activate", 1.0F, 1.0F, ship.getLocation(), ZERO);
                    }

                    // 特殊船体插件效果 (PhaseDefenseUnit)
                    if (ship.getVariant().hasHullMod("PhaseDefenseUnit")) {
                        float numRepaired = 0.0F;
                        float numRepairable = 0.0F;
                        boolean anyUnflamed = false;
                        boolean wasFlamedOut = ship.getEngineController().isFlamedOut();

                        // 修复和恢复引擎
                        for (ShipEngineControllerAPI.ShipEngineAPI eng : ship.getEngineController().getShipEngines()) {
                            if (!eng.isPermanentlyDisabled() && !eng.isSystemActivated()) {
                                numRepairable++;
                                float intensity = 0.0F;
                                float radius = 5.0F * eng.getEngineSlot().getWidth() * (float) Math.sqrt(BOOST_MULT.get(ship.getHullSize()));
                                int numSparks = Math.max(1, Math.round(radius / 30.0F));

                                // 修复损坏的引擎
                                if (eng.isDisabled()) {
                                    intensity = 1.0F;
                                    numRepaired++;
                                    anyUnflamed = true;

                                    // 生成修复特效
                                    for (int i = 0; i < numSparks; ++i) {
                                        Vector2f point = MathUtils.getPointOnCircumference(
                                                eng.getLocation(),
                                                radius,
                                                VectorUtils.getAngleStrict(ship.getLocation(), eng.getLocation())
                                        );
                                        AnchoredEntity anchor = new AnchoredEntity(ship, eng.getLocation());
                                        float thickness = (float) Math.sqrt(radius);
                                        Global.getCombatEngine().spawnEmpArcPierceShields(
                                                ship, point, anchor, anchor,
                                                DamageType.ENERGY, 0.0F, 0.0F, radius,
                                                null, thickness, CONTRAIL_COLOR, ENGINE_COLOR
                                        );
                                    }
                                }
                                // 恢复引擎血量
                                else if (eng.getHitpoints() < eng.getMaxHitpoints()) {
                                    intensity = Math.min(1.0F, (eng.getMaxHitpoints() - eng.getHitpoints()) / eng.getMaxHitpoints());
                                    eng.setHitpoints(eng.getMaxHitpoints());
                                    numRepaired += intensity / 2.0F;
                                }

                                // 添加命中粒子效果
                                if (intensity > 0.0F) {
                                    Color flashColor = new Color(
                                            ENGINE_COLOR.getRed(), ENGINE_COLOR.getGreen(), ENGINE_COLOR.getBlue(),
                                            Math.min(255, Math.max(0, Math.round(intensity * ENGINE_COLOR.getAlpha())))
                                    );
                                    float duration = (float) Math.sqrt(radius) * 0.1F;
                                    Global.getCombatEngine().addHitParticle(
                                            eng.getLocation(), ship.getVelocity(),
                                            radius * 1.5F, 1.0F, duration, flashColor
                                    );
                                }
                            }
                        }

                        // 提高引擎修复速度
                        stats.getCombatEngineRepairTimeMult().modifyMult(id, 0.4F);

                        // 播放修复音效
                        if (anyUnflamed) {
                            float fracRepaired = numRepaired / Math.max(1.0F, numRepairable);
                            if (wasFlamedOut) {
                                Global.getSoundPlayer().playSound("disabled_medium_crit", 1.5F, 1.0F + fracRepaired, ship.getLocation(), ship.getVelocity());
                            } else {
                                Global.getSoundPlayer().playSound("disabled_small_crit", 1.5F, 1.0F + fracRepaired, ship.getLocation(), ship.getVelocity());
                            }
                            ship.getEngineController().computeEffectiveStats(true);
                        }

                        float currentFlux = ship.getCurrFlux();
                        float fluxToReduce = currentFlux * FLUX_REDUCTION;
                        ship.getFluxTracker().decreaseFlux(fluxToReduce);

                    }
                    this.started = true;
                }

                // 引擎动画平滑过渡
                List<ShipEngineControllerAPI.ShipEngineAPI> engList = ship.getEngineController().getShipEngines();
                for (int i = 0; i < engList.size(); ++i) {
                    ShipEngineControllerAPI.ShipEngineAPI eng = engList.get(i);
                    if (eng.isSystemActivated()) {
                        float targetLevel = getSystemEngineScale(eng, this.boostVisualDir) * 0.4F;
                        Float currLevel = this.engState.get(i);
                        if (currLevel == null) currLevel = 0.0F;

                        // 平滑调整引擎状态
                        if (currLevel > targetLevel) {
                            currLevel = Math.max(targetLevel, currLevel - objectiveAmount / EXTEND_TIME.get(ship.getHullSize()));
                        } else {
                            currLevel = Math.min(targetLevel, currLevel + objectiveAmount / EXTEND_TIME.get(ship.getHullSize()));
                        }

                        this.engState.put(i, currLevel);
                        ship.getEngineController().setFlameLevel(eng.getEngineSlot(), currLevel);
                    }
                }
            }

            // 8. 系统结束阶段 (OUT) 处理
            if (state == State.OUT) {
                // 计算减速修正
                float decelMult = Math.max(0.5F, Math.min(2.0F, stats.getDeceleration().getModifiedValue() / stats.getDeceleration().getBaseValue()));
                float adjFalloffPerSec = SPEED_FALLOFF_PER_SEC.get(ship.getHullSize()) * (float) Math.pow(decelMult, 0.5F);
                float maxDecelPenalty = 1.0F / decelMult;

                // 修改机动性状态
                stats.getMaxTurnRate().unmodify(id);
                stats.getDeceleration().modifyMult(id, 1.0F + (maxDecelPenalty - 1.0F) * effectLevel);
                stats.getTurnAcceleration().modifyPercent(id, 50.0F * effectLevel);

                // 控制船只命令
                if (this.boostForward) {
                    ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
                    ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS);
                    ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
                } else {
                    ship.blockCommandForOneFrame(ShipCommand.ACCELERATE);
                }

                stats.getCombatEngineRepairTimeMult().unmodify(id);

                // 速度衰减逻辑 (防止超速)
                if (amount > 0.0F) {
                    Vector2f velocity = ship.getVelocity();
                    float currentSpeed = velocity.length();
                    float maxSpeed = stats.getMaxSpeed().getModifiedValue();

                    if (currentSpeed > maxSpeed) {
                        float overspeedRatio = (currentSpeed - maxSpeed) / maxSpeed;
                        float dynamicFalloff = MathUtils.clamp(adjFalloffPerSec + overspeedRatio * 0.2f, 0.5f, 1.0f);
                        float falloffFactor = (float) Math.pow(dynamicFalloff, amount * stats.getTimeMult().getModifiedValue());

                        // 应用速度衰减
                        Vector2f newVel = new Vector2f(velocity);
                        newVel.scale(falloffFactor);

                        // 速度下限保护
                        float newSpeed = newVel.length();
                        if (newSpeed < maxSpeed) {
                            newVel.normalise().scale(maxSpeed);
                        }
                        velocity.set(newVel);
                    }
                }

                // 生成残影效果
                this.interval.advance(amount);
                if (this.interval.intervalElapsed()) {
                    float randRange = (float) Math.sqrt(shipRadius) * 0.5F * afterimageIntensity * this.boostScale;
                    Vector2f randLoc = MathUtils.getRandomPointInCircle(ZERO, randRange);
                    Color afterimageColor = new Color(
                            CONTRAIL_COLOR.getRed(), CONTRAIL_COLOR.getGreen(), CONTRAIL_COLOR.getBlue(),
                            Math.min(255, Math.max(0, Math.round(0.15F * afterimageIntensity * CONTRAIL_COLOR.getAlpha())))
                    );
                    ship.addAfterimage(
                            afterimageColor, randLoc.x, randLoc.y,
                            -ship.getVelocity().x, -ship.getVelocity().y,
                            randRange, 0.0F, 0.1F, 0.5F, true, false, false
                    );
                }

                // 结束阶段引擎特效
                List<ShipEngineControllerAPI.ShipEngineAPI> engList = ship.getEngineController().getShipEngines();
                for (int i = 0; i < engList.size(); ++i) {
                    ShipEngineControllerAPI.ShipEngineAPI eng = engList.get(i);
                    if (eng.isSystemActivated()) {
                        float targetLevel = getSystemEngineScale(eng, this.boostVisualDir) * effectLevel;
                        // 调整引擎效果强度
                        if (targetLevel >= 1.0F - MAX_FRAC_OUT.get(ship.getHullSize())) {
                            targetLevel = 1.0F;
                        } else {
                            targetLevel /= 1.0F - MAX_FRAC_OUT.get(ship.getHullSize());
                        }
                        this.engState.put(i, targetLevel);
                        ship.getEngineController().setFlameLevel(eng.getEngineSlot(), targetLevel);
                    }
                }
            }
            // 9. 系统激活阶段 (ACTIVE) 处理
            else if (state == State.ACTIVE) {
                // 增强转向能力
                stats.getMaxTurnRate().modifyPercent(id, 50.0F);
                stats.getTurnAcceleration().modifyPercent(id, 50.0F * effectLevel);

                // 扩展引擎视觉效果
                ship.getEngineController().getExtendLengthFraction().advance(objectiveAmount * 2.0F);
                ship.getEngineController().getExtendWidthFraction().advance(objectiveAmount * 2.0F);
                ship.getEngineController().getExtendGlowFraction().advance(objectiveAmount * 2.0F);

                // 更新引擎状态
                List<ShipEngineControllerAPI.ShipEngineAPI> engList = ship.getEngineController().getShipEngines();
                for (int i = 0; i < engList.size(); ++i) {
                    ShipEngineControllerAPI.ShipEngineAPI eng = engList.get(i);
                    if (eng.isSystemActivated()) {
                        float targetLevel = getSystemEngineScale(eng, this.boostVisualDir);
                        Float currLevel = this.engState.get(i);
                        if (currLevel == null) currLevel = 0.0F;

                        // 平滑调整引擎状态
                        if (currLevel > targetLevel) {
                            currLevel = Math.max(targetLevel, currLevel - objectiveAmount / EXTEND_TIME.get(ship.getHullSize()));
                        } else {
                            currLevel = Math.min(targetLevel, currLevel + objectiveAmount / EXTEND_TIME.get(ship.getHullSize()));
                        }

                        this.engState.put(i, currLevel);
                        ship.getEngineController().setFlameLevel(eng.getEngineSlot(), currLevel);
                    }
                }
            }

            // 10. 结束阶段的推进效果
            if (state == State.OUT && !this.ended) {
                Vector2f direction = new Vector2f();
                this.boostForward = false;
                this.boostScale = BASELINE_MULT.get(ship.getHullSize());

                // 根据移动方向计算推进矢量
                if (ship.getEngineController().isAccelerating()) {
                    direction.y += BASELINE_MULT.get(ship.getHullSize()) - FORWARD_PENALTY.get(ship.getHullSize());
                    this.boostScale -= FORWARD_PENALTY.get(ship.getHullSize());
                    this.boostForward = true;
                } else if (ship.getEngineController().isAcceleratingBackwards() || ship.getEngineController().isDecelerating()) {
                    direction.y -= BASELINE_MULT.get(ship.getHullSize()) - REVERSE_PENALTY.get(ship.getHullSize());
                    this.boostScale -= REVERSE_PENALTY.get(ship.getHullSize());
                }

                if (ship.getEngineController().isStrafingLeft()) {
                    direction.x -= 1;
                    this.boostScale += 1.0F - BASELINE_MULT.get(ship.getHullSize());
                    this.boostForward = false;
                } else if (ship.getEngineController().isStrafingRight()) {
                    direction.x += 1;
                    this.boostScale += 1.0F - BASELINE_MULT.get(ship.getHullSize());
                    this.boostForward = false;
                }

                // 默认向前推进
                if (direction.length() <= 0.0F) {
                    direction.y = BASELINE_MULT.get(ship.getHullSize()) - FORWARD_PENALTY.get(ship.getHullSize());
                    this.boostScale -= FORWARD_PENALTY.get(ship.getHullSize());
                }

                // 应用推进力
                Misc.normalise(direction);
                VectorUtils.rotate(direction, ship.getFacing() - 90.0F, direction);
                direction.scale((ship.getMaxSpeedWithoutBoost() * 5.0F + 300.0F) * this.boostScale);
                Vector2f.add(ship.getVelocity(), direction, ship.getVelocity());
                this.ended = true;

                // 引擎爆炸效果
                float duration = (float) Math.sqrt(shipRadius) / 25.0F;
                ship.getEngineController().getExtendLengthFraction().advance(1.0F);
                ship.getEngineController().getExtendWidthFraction().advance(1.0F);
                ship.getEngineController().getExtendGlowFraction().advance(1.0F);

                for (ShipEngineControllerAPI.ShipEngineAPI eng : ship.getEngineController().getShipEngines()) {
                    float level = 1.0F;
                    if (eng.isSystemActivated()) {
                        level = getSystemEngineScale(eng, this.boostVisualDir);
                    }

                    if ((eng.isActive() || eng.isSystemActivated()) && level > 0.0F) {
                        // 创建爆炸效果
                        Color bigBoostColor = new Color(175, 15, 15, 95);
                        Color boostColor = new Color(
                                BOOST_COLOR.getRed(), BOOST_COLOR.getGreen(), BOOST_COLOR.getBlue(),
                                Math.min(255, Math.max(0, Math.round(BOOST_COLOR.getAlpha() * level))
                                ));

                        Global.getCombatEngine().spawnExplosion(
                                eng.getLocation(), ZERO, bigBoostColor,
                                BOOST_MULT.get(ship.getHullSize()) * 4.0F * this.boostScale * eng.getEngineSlot().getWidth(),
                                duration
                        );
                        Global.getCombatEngine().spawnExplosion(
                                eng.getLocation(), ZERO, boostColor,
                                BOOST_MULT.get(ship.getHullSize()) * 2.0F * this.boostScale * eng.getEngineSlot().getWidth(),
                                0.15F
                        );
                    }
                }

                // 播放推进音效 (根据船体尺寸)
                float soundScale = (float) Math.sqrt(this.boostScale);
                HullSize size = ship.getHullSize();
                switch (size) {
                    case FIGHTER:
                        Global.getSoundPlayer().playSound("cr_booster_boom", 1.1F, 0.6F * soundScale, ship.getLocation(), ZERO);
                        break;
                    case FRIGATE:
                        Global.getSoundPlayer().playSound("cr_booster_boom", 1.0F, soundScale, ship.getLocation(), ZERO);
                        break;
                    case DESTROYER:
                        Global.getSoundPlayer().playSound("cr_booster_boom", 0.9F, 1.1F * soundScale, ship.getLocation(), ZERO);
                        break;
                    case CRUISER:
                        Global.getSoundPlayer().playSound("cr_booster_boom", 0.8F, 1.2F * soundScale, ship.getLocation(), ZERO);
                        break;
                    case CAPITAL_SHIP:
                        Global.getSoundPlayer().playSound("cr_booster_boom", 0.7F, 1.3F * soundScale, ship.getLocation(), ZERO);
                }
            }

            // 11. 全局状态修改
            int TIME_BUFF = 1000;  // 时间加速倍率 (1000% = 10倍速)
            stats.getTimeMult().modifyPercent(id, TIME_BUFF * effect);  // 时间流速修改
            stats.getMaxSpeed().modifyPercent(id, INSTANT_BOOST_FLAT * effect);  // 最大速度修改

            if (ship.getVariant().hasHullMod("PolariphaseDrive")) {
                // 创建抖动颜色
                Color jitterColor = new Color(ENGINE_COLOR.getRed(), ENGINE_COLOR.getGreen(), ENGINE_COLOR.getBlue(),
                        Math.min(255, Math.max(0, Math.round(0.2F * effectLevel * ENGINE_COLOR.getAlpha()))));

                Color jitterUnderColor = new Color(ENGINE_COLOR.getRed(), ENGINE_COLOR.getGreen(), ENGINE_COLOR.getBlue(),
                        Math.min(255, Math.max(0, Math.round(0.3F * effectLevel * ENGINE_COLOR.getAlpha()))));

                // 应用抖动效果
                ship.setJitter(this, jitterColor, 1.0F, 5, 0.0F, 3.0F + effectLevel * 10.0F);
                ship.setJitterUnder(this, jitterUnderColor, 1.0F, 25, 0.0F, 7.0F + effectLevel * 10.0F);

                // 时间扭曲效果 (船体局部时间加速)
                float shipTimeMult = 4.0F + 2.0F * effectLevel;
                stats.getTimeMult().modifyMult(id, shipTimeMult);

                // 玩家船只全局时间补偿
                String globalId = id + "_" + ship.getId();
                if (Global.getCombatEngine().getPlayerShip() == ship) {
                    Global.getCombatEngine().getTimeMult().modifyMult(globalId, 1.0F / shipTimeMult);
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            this.STATUSKEY1,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "时间流速改变",
                            false
                    );
                } else {
                    Global.getCombatEngine().getTimeMult().modifyMult(globalId , 2.0F);
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            this.STATUSKEY1,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "时间流速改变",
                            false
                    );
                }
            }

            if (ship.getVariant().hasHullMod("WeaponOverLoad")) {
                // 系统结束，启动武器过载效果
                weaponOverloadActive = true;
                weaponOverloadTimer = WEAPON_OVERLOAD_DURATION;
                if(state == State.ACTIVE) {
                    // 应用武器伤害加成
                    stats.getEnergyWeaponDamageMult().modifyPercent(id, WEAPON_OVERLOAD_DAMAGE_BONUS);
                    stats.getBallisticWeaponDamageMult().modifyPercent(id, WEAPON_OVERLOAD_DAMAGE_BONUS);

                    // 显示状态信息
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            this.WEAPONKEY3,
                            "graphics/icons/hullsys/ammo_feeder.png", // 可以使用合适的图标
                            "武器过载",
                            "能量与实弹武器伤害 +" + (int) WEAPON_OVERLOAD_DAMAGE_BONUS + "%",
                            false
                    );
                }

                if (weaponOverloadActive) {
                    weaponOverloadTimer -= amount;
                    if (weaponOverloadTimer <= 0f) {
                        weaponOverloadActive = false;
                        stats.getEnergyWeaponDamageMult().unmodify(id);
                        stats.getBallisticWeaponDamageMult().unmodify(id);
                    }
                }
            }

            // 更新武器过载计时器
            if (weaponOverloadActive) {
                weaponOverloadTimer -= amount;

                if (weaponOverloadTimer <= 0f) {
                    // 武器过载效果结束
                    weaponOverloadActive = false;
                    stats.getEnergyWeaponDamageMult().unmodify(id);
                    stats.getBallisticWeaponDamageMult().unmodify(id);
                }
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        // 系统卸载时的清理工作
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship != null) {
            // 重置状态变量
            this.started = false;
            this.ended = false;
            this.boostScale = 0.75F;
            this.boostVisualDir = 0.0F;
            this.boostForward = false;
            this.engState.clear();

            // 移除所有状态修改
            stats.getCombatEngineRepairTimeMult().unmodify(id);
            stats.getEngineDamageTakenMult().unmodify(id);
            stats.getTimeMult().unmodify(id);
            String globalId = id + "_" + ship.getId();
            Global.getCombatEngine().getTimeMult().unmodify(globalId);
            stats.getMaxSpeed().unmodify(id);
            stats.getTimeMult().unmodify(id);
            stats.getMaxTurnRate().unmodify(id);
            stats.getDeceleration().unmodify(id);
            stats.getTurnAcceleration().unmodify(id);
            stats.getEnergyWeaponDamageMult().unmodify(id);
            stats.getBallisticWeaponDamageMult().unmodify(id);
            stats.getCombatEngineRepairTimeMult().unmodify(id);
            stats.getEngineDamageTakenMult().unmodify(id);
            stats.getDynamic().getMod("can_repair_modules_under_fire").unmodify(id);

            // 清除抖动效果
            ship.setJitter(this, ENGINE_COLOR_STANDARD, 0.0F, 5, 0.0F, 13.0F);
            ship.setJitterUnder(this, ENGINE_COLOR_STANDARD, 0.0F, 25, 0.0F, 17.0F);
        }
    }

    // =============== UI 和信息方法 ===============
    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (ship != null) {
            if (ship.getEngineController().isFlamedOut()) {
                return "助动启动";  // 引擎熄火时显示
            }
        }
        return null;
    }

    // =============== 系统参数覆盖方法 ===============
    @Override
    public float getInOverride(ShipAPI ship) {
        return ship != null ? IN_OVERRIDE.get(ship.getHullSize()) : -1.0F;
    }

    @Override
    public float getActiveOverride(ShipAPI ship) {
        return ship != null ? ACTIVE_OVERRIDE.get(ship.getHullSize()) : -1.0F;
    }

    @Override
    public float getOutOverride(ShipAPI ship) {
        return ship != null ? OUT_OVERRIDE.get(ship.getHullSize()) : -1.0F;
    }

    @Override
    public int getUsesOverride(ShipAPI ship) {
        if (ship != null) {
            // 精英船体插件提供额外使用次数
            return ship.getVariant().hasHullMod("PolariphaseDrive") && ship.getVariant().hasHullMod("WeaponOverLoad")?
                    USES_OVERRIDE.get(ship.getHullSize()) +1 :
                    USES_OVERRIDE.get(ship.getHullSize());
        }
        return 1;
    }

    @Override
    public float getRegenOverride(ShipAPI ship) {
        if (ship != null) {
            // 精英船体插件提高恢复速率
            return ship.getVariant().hasHullMod("PolariphaseDrive") ?
                    REGEN_OVERRIDE.get(ship.getHullSize()) * ELITE_REGEN_MULT.get(ship.getHullSize()) :
                    REGEN_OVERRIDE.get(ship.getHullSize());
        }
        return -1.0F;
    }

    // =============== 辅助方法 ===============
    /**
     * 计算引擎在给定方向上的缩放比例
     * @param engine 引擎对象
     * @param direction 方向角度 (度)
     * @return 缩放比例 (0.0-1.0)
     */
    public static float getSystemEngineScale(ShipEngineControllerAPI.ShipEngineAPI engine, float direction) {
        float engAngle = engine.getEngineSlot().getAngle();
        // 如果引擎角度与目标方向偏差大于100度，则完全激活，否则不激活
        return Math.abs(MathUtils.getShortestRotation(engAngle, direction)) > 100.0F ? 1.0F : 0.0F;
    }

    static {
        SPEED_FALLOFF_PER_SEC.put(HullSize.FIGHTER, 0.75F);
        SPEED_FALLOFF_PER_SEC.put(HullSize.FRIGATE, 0.55F);
        SPEED_FALLOFF_PER_SEC.put(HullSize.DESTROYER, 0.375F);
        SPEED_FALLOFF_PER_SEC.put(HullSize.CRUISER, 0.25F);
        SPEED_FALLOFF_PER_SEC.put(HullSize.CAPITAL_SHIP, 0.225F);
        BASELINE_MULT.put(HullSize.FIGHTER, 0.975F);
        BASELINE_MULT.put(HullSize.FRIGATE, 0.95F);
        BASELINE_MULT.put(HullSize.DESTROYER, 0.90F);
        BASELINE_MULT.put(HullSize.CRUISER, 0.85F);
        BASELINE_MULT.put(HullSize.CAPITAL_SHIP, 0.80F);
        FORWARD_PENALTY.put(HullSize.FIGHTER, 0.725F);
        FORWARD_PENALTY.put(HullSize.FRIGATE, 0.45F);
        FORWARD_PENALTY.put(HullSize.DESTROYER, 0.35F);
        FORWARD_PENALTY.put(HullSize.CRUISER, 0.30F);
        FORWARD_PENALTY.put(HullSize.CAPITAL_SHIP, 0.25F);
        REVERSE_PENALTY.put(HullSize.FIGHTER, 0.725F);
        REVERSE_PENALTY.put(HullSize.FRIGATE, 0.45F);
        REVERSE_PENALTY.put(HullSize.DESTROYER, 0.4F);
        REVERSE_PENALTY.put(HullSize.CRUISER, 0.35F);
        REVERSE_PENALTY.put(HullSize.CAPITAL_SHIP, 0.4F);
        IN_OVERRIDE.put(HullSize.FIGHTER, 0.2F);
        IN_OVERRIDE.put(HullSize.FRIGATE, 0.2F);
        IN_OVERRIDE.put(HullSize.DESTROYER, 0.2F);
        IN_OVERRIDE.put(HullSize.CRUISER, 0.2F);
        IN_OVERRIDE.put(HullSize.CAPITAL_SHIP, 0.2F);
        ACTIVE_OVERRIDE.put(HullSize.FIGHTER, 0.2F);
        ACTIVE_OVERRIDE.put(HullSize.FRIGATE, 0.2F);
        ACTIVE_OVERRIDE.put(HullSize.DESTROYER, 0.2F);
        ACTIVE_OVERRIDE.put(HullSize.CRUISER, 0.2F);
        ACTIVE_OVERRIDE.put(HullSize.CAPITAL_SHIP, 0.2F);
        OUT_OVERRIDE.put(HullSize.FIGHTER, 0.4F);
        OUT_OVERRIDE.put(HullSize.FRIGATE, 0.6F);
        OUT_OVERRIDE.put(HullSize.DESTROYER, 0.8F);
        OUT_OVERRIDE.put(HullSize.CRUISER, 0.9F);
        OUT_OVERRIDE.put(HullSize.CAPITAL_SHIP, 1.0F);
        USES_OVERRIDE.put(HullSize.FIGHTER, 3);
        USES_OVERRIDE.put(HullSize.FRIGATE, 3);
        USES_OVERRIDE.put(HullSize.DESTROYER, 2);
        USES_OVERRIDE.put(HullSize.CRUISER, 2);
        USES_OVERRIDE.put(HullSize.CAPITAL_SHIP, 2);
        REGEN_OVERRIDE.put(HullSize.FIGHTER, 0.15F);
        REGEN_OVERRIDE.put(HullSize.FRIGATE, 0.15F);
        REGEN_OVERRIDE.put(HullSize.DESTROYER, 0.125F);
        REGEN_OVERRIDE.put(HullSize.CRUISER, 0.1F);
        REGEN_OVERRIDE.put(HullSize.CAPITAL_SHIP, 0.075F);
        ELITE_REGEN_MULT.put(HullSize.FIGHTER, 1.0F);
        ELITE_REGEN_MULT.put(HullSize.FRIGATE, 1.5F);
        ELITE_REGEN_MULT.put(HullSize.DESTROYER, 1.6F);
        ELITE_REGEN_MULT.put(HullSize.CRUISER, 1.75F);
        ELITE_REGEN_MULT.put(HullSize.CAPITAL_SHIP, 2.0F);
        EXTEND_TIME.put(HullSize.FIGHTER, 0.1F);
        EXTEND_TIME.put(HullSize.FRIGATE, 0.1F);
        EXTEND_TIME.put(HullSize.DESTROYER, 0.1F);
        EXTEND_TIME.put(HullSize.CRUISER, 0.1F);
        EXTEND_TIME.put(HullSize.CAPITAL_SHIP, 0.1F);
        MAX_FRAC_OUT.put(HullSize.FIGHTER, 0.15F / OUT_OVERRIDE.get(HullSize.FIGHTER));
        MAX_FRAC_OUT.put(HullSize.FRIGATE, 0.15F / OUT_OVERRIDE.get(HullSize.FRIGATE));
        MAX_FRAC_OUT.put(HullSize.DESTROYER, 0.15F / OUT_OVERRIDE.get(HullSize.DESTROYER));
        MAX_FRAC_OUT.put(HullSize.CRUISER, 0.15F / OUT_OVERRIDE.get(HullSize.CRUISER));
        MAX_FRAC_OUT.put(HullSize.CAPITAL_SHIP, 0.15F / OUT_OVERRIDE.get(HullSize.CAPITAL_SHIP));
        BOOST_MULT.put(HullSize.FIGHTER, 0.5F);
        BOOST_MULT.put(HullSize.FRIGATE, 1.0F);
        BOOST_MULT.put(HullSize.DESTROYER, 2.0F);
        BOOST_MULT.put(HullSize.CRUISER, 3.0F);
        BOOST_MULT.put(HullSize.CAPITAL_SHIP, 4.0F);
        ENGINE_COLOR_STANDARD = new Color(255, 10, 10);
        CONTRAIL_COLOR_STANDARD = new Color(255, 100, 100, 75);
        BOOST_COLOR_STANDARD = new Color(255, 175, 175, 200);
        new Color(255, 150, 10, 225);
        new Color(255, 125, 75, 75);
        new Color(255, 200, 150, 200);
        ENGINE_COLOR_TARGETING = new Color(100, 175, 255, 200);
        CONTRAIL_COLOR_TARGETING = new Color(75, 125, 255, 75);
        BOOST_COLOR_TARGETING = new Color(150, 200, 255, 200);
        new Color(150, 20, 255, 225);
        new Color(125, 75, 255, 75);
        new Color(200, 150, 255, 200);
    }
}
