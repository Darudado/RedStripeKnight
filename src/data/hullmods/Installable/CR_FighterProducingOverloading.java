package data.hullmods.Installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicIncompatibleHullmods;

import java.awt.Color;

public class CR_FighterProducingOverloading extends BaseHullMod {

    // ==================== 静态常量定义 ====================

    /** 战机作战范围增加百分比（当前为0） */
    private static final float RANGE_INCREASE_PERCENT = 0f;

    private static final float SMOD_BONUS = 0.25f;

    /** 战机获得速度增益的有效范围 */
    private static final float SPEED_BONUS_RANGE = 1500f;

    /** 战机速度阈值，低于此值才获得增益 */
    private static final float SPEED_THRESHOLD = 215f;

    /** 速度增益系数：低于阈值的每单位速度获得多少增益 */
    private static final float SPEED_GAP_BONUS = 0.7f;

    /** 战机转向速率阈值 */
    private static final float TURNRATE_THRESHOLD = 90f;

    /** 转向增益系数 */
    private static final float TURNRATE_GAP_BONUS = 0.7f;

    /** 锻炉激活时的音效ID */
    private static final String FORGE_ACTIVE_SOUND_ID = "system_ammo_feeder";

    /** 锻炉激活期间每秒产生的幅能百分比（基于最大幅能） */
    private static final float FLUX_PERCENT_PER_SECOND = 0.1f; // 10% 最大幅能/秒

    /** 锻炉产生的幅能不会使舰船超过该幅能水平 */
    private static final float MAX_INCREASE_FLUX_LEVEL = 0.9f;

    /** 幅能水平低于此值时才能启用锻炉 */
    private static final float MAX_ACTIVE_FLUX_LEVEL = 0.5f;

    /** 锻炉激活持续时间（秒） */
    private static final float FORGE_ACTIVE_TIME = 15f;

    /** 锻炉总冷却时间（秒） */
    private static final float FORGE_TOTAL_TIME = 30f;

    /** 战机存活比例阈值，低于此值触发锻炉 */
    private static final float FORGE_THRESHOLD = 0.2f;

    /** 惩罚计算：战机装配点超过此值开始计算惩罚 */
    private static final float PUNISH_START_OP = 20f;

    /** 每超出1点OP增加的惩罚时间 */
    private static final float PUNISH_PER_OP = 0.4f;

    /** 模块ID */
    private static final String ID = "cr_ProducingOverloading";

    /** 锻炉状态ID */
    private static final String ID_FORGE = "cr_Forge";

    /** 锻炉计时器ID */
    private static final String ID_FORGE_TIMER = "cr_ForgeTimer";

    private static final String ID_PUNISH_TIMER = "cr_PunishTimer";

    // ==================== 构造函数 ====================

    public CR_FighterProducingOverloading() {
        // 构造函数
    }

    // ==================== 静态方法 ====================

    /**
     * 计算重型战机的惩罚时间
     * @param ship 舰船对象
     * @return 惩罚时间（秒）
     */
    public static float computePunish(ShipAPI ship) {
        if (ship == null) return 0f;

        float punish = 0f;

        // 遍历所有已安装的舰载机联队
        for (String wingId : ship.getVariant().getFittedWings()) {
            // 获取战机联队的规格
            FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(wingId);
            if (spec == null) continue;

            // 获取战机的装配点消耗
            float op = spec.getOpCost(null);

            // 如果装配点超过阈值，计算惩罚
            if (op > PUNISH_START_OP) {
                punish += (op - PUNISH_START_OP) * PUNISH_PER_OP;
            }
        }

        return punish;
    }

    /**
     * 判断战机联队是否有效（非轰炸机且有一定范围）
     * 参考代码中的简化版本
     */
    public static boolean isValidWing(FighterWingAPI wing) {
        if (wing == null) return false;
        if (wing.getSpec() == null) return false;
        if (wing.getSpec().isBomber()) return false;
        return wing.getSpec().getRange() < 2000f;
    }

    // ==================== 覆盖的方法 ====================

    /**
     * 应用效果到已生成的战机
     * 在原版中保持简单实现，避免复杂的增益逻辑
     */
    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        // 为低速战机提供少量增益
        if (fighter == null || ship == null) return;

        float distance = MathUtils.getDistance(ship.getLocation(), fighter.getLocation());
        if (distance > SPEED_BONUS_RANGE) return;

        // 简单的增益逻辑
        float speed = fighter.getMutableStats().getMaxSpeed().getBaseValue();
        if (speed < SPEED_THRESHOLD) {
            float bonus = (SPEED_THRESHOLD - speed) * SPEED_GAP_BONUS * 0.5f; // 降低效果
            fighter.getMutableStats().getMaxSpeed().modifyFlat(ID, bonus);
        }

        float turnRate = fighter.getMutableStats().getMaxTurnRate().getBaseValue();
        if (turnRate < TURNRATE_THRESHOLD) {
            float bonus = (TURNRATE_THRESHOLD - turnRate) * TURNRATE_GAP_BONUS * 0.5f; // 降低效果
            fighter.getMutableStats().getMaxTurnRate().modifyFlat(ID, bonus);
        }
    }

    /**
     * 舰船创建前应用效果
     */
    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (stats == null) return;

        // 延长战机的作战半径
        stats.getFighterWingRange().modifyPercent(ID, RANGE_INCREASE_PERCENT);
        stats.getPeakCRDuration().modifyMult(id, 0.6f);
        stats.getCRLossPerSecondPercent().modifyMult(id, 1.25f);

        if(stats.getVariant().hasHullMod("CR_ImprovedWeaponControlling")) {
            MagicIncompatibleHullmods.removeHullmodWithWarning(stats.getVariant(), "CR_ImprovedWeaponControlling", spec.getId());
        }

        boolean sMod = isSMod(stats);
        if (sMod) {
            stats.getDynamic().getStat(Stats.FIGHTER_REARM_TIME_MULT).modifyMult(id, 1f - SMOD_BONUS);
        }
    }

    /**
     * 战斗中的每一帧更新
     */
    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        // 如果舰船已死亡或成为残骸，直接返回
        if (!ship.isAlive() || ship.isHulk()) return;

        // 舰船刚部署时，避免立即触发锻炉（防止战机未生成导致的误判）
        if (ship.getFullTimeDeployed() < 5f) {
            return;
        }

        // 检查锻炉计时器
        Float forgeTimer = (Float) ship.getCustomData().get(ID_FORGE_TIMER);
        if (forgeTimer != null) {
            // 更新计时器
            forgeTimer -= amount;
            if (forgeTimer <= 0) {
                // 冷却结束
                ship.removeCustomData(ID_FORGE_TIMER);
                ship.removeCustomData(ID_FORGE);
            } else {
                // 更新计时器
                ship.setCustomData(ID_FORGE_TIMER, forgeTimer);

                // 在激活期间产生幅能
                if (forgeTimer > (FORGE_TOTAL_TIME - FORGE_ACTIVE_TIME)) {
                    // 计算每秒应产生的幅能（最大幅能的10%）
                    float fluxToAdd = ship.getMaxFlux() * FLUX_PERCENT_PER_SECOND * amount;

                    FluxTrackerAPI fluxTracker = ship.getFluxTracker();

                    // 确保不会超过最大允许的幅能水平
                    if (fluxTracker.getFluxLevel() < MAX_INCREASE_FLUX_LEVEL) {
                        float maxAllowedFlux = ship.getMaxFlux() * MAX_INCREASE_FLUX_LEVEL;
                        float currentFlux = fluxTracker.getCurrFlux();

                        // 如果增加后不会超过限制，则增加幅能
                        if (currentFlux + fluxToAdd <= maxAllowedFlux) {
                            fluxTracker.increaseFlux(fluxToAdd, false);
                        } else {
                            // 否则只增加到限制值
                            float fluxToAddLimited = maxAllowedFlux - currentFlux;
                            if (fluxToAddLimited > 0) {
                                fluxTracker.increaseFlux(fluxToAddLimited, false);
                            }
                        }
                    }

                    // 激活期间的视觉效果
                    if (ship == Global.getCombatEngine().getPlayerShip()) {
                        float remainingTime = forgeTimer - (FORGE_TOTAL_TIME - FORGE_ACTIVE_TIME);
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                ID,
                                "graphics/icons/hullsys/ammo_feeder.png", // 使用原版图标
                                "战机锻炉",
                                String.format("激活中: %.1f秒", remainingTime),
                                false
                        );
                    }

                    // 激活期间添加抖动效果
                    Color jitterColor = new Color(255, 100, 100, 50);
                    ship.setJitter(ship, jitterColor, 0.5f, 3, 0, 5f);

                    // 生成粒子效果
                    generateForgeParticles(ship);
                }

                return; // 锻炉在冷却或激活中，不检查触发条件
            }
        }

        // 检测是否需要激活锻炉
        // 如果锻炉不在冷却中
        if (!ship.getCustomData().containsKey(ID_FORGE)) {

            // 计算存活战机的装配点比例
            float fighterOpAround = 0f;   // 存活战机的总装配点（分子）
            float totalFighterOp = 0f;    // 所有战机的总装配点（分母）

            // 遍历所有发射舱
            for (FighterLaunchBayAPI bay : ship.getLaunchBaysCopy()) {
                if (bay == null) continue;

                FighterWingAPI wing = bay.getWing();
                if (wing == null) continue;
                if (isValidWing(wing)) continue;

                // 计算整个联队的装配点
                FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(wing.getWingId());
                if (spec == null) continue;

                float wingOp = spec.getOpCost(null);
                totalFighterOp += wingOp;

                // 计算存活战机的装配点
                int numAlive = 0;
                for (ShipAPI fighter : wing.getWingMembers()) {
                    if (fighter != null && fighter.isAlive() && !fighter.isHulk()) {
                        numAlive++;
                    }
                }

                fighterOpAround += wingOp * (numAlive / (float) spec.getNumFighters());
            }

            // 避免除零错误
            if (totalFighterOp <= 0) return;

            // 计算存活比例
            float survivalRatio = fighterOpAround / totalFighterOp;

            // 触发条件：
            // 1. 存活比例低于阈值
            // 2. 幅能水平低于阈值
            // 3. 锻炉不在冷却中
            if (survivalRatio < FORGE_THRESHOLD
                    && ship.getFluxTracker().getFluxLevel() < MAX_ACTIVE_FLUX_LEVEL) {

                // 标记锻炉为激活状态
                ship.setCustomData(ID_FORGE, 1f);
                ship.setCustomData(ID_FORGE_TIMER, FORGE_TOTAL_TIME);

                // 快速补充所有发射舱的战机
                for (FighterLaunchBayAPI bay : ship.getLaunchBaysCopy()) {
                    if (bay.getWing() == null) continue;
                    if (isValidWing(bay.getWing())) continue;

                    // 计算需要补充的战机数量
                    int numLost = bay.getNumLost();
                    if (numLost > 0) {
                        // 使用原版的快速补充机制
                        bay.setFastReplacements(numLost);

                        // 添加重型战机惩罚
                        float punish = computePunish(ship);
                        if (punish > 0) {
                            // 延长补充时间作为惩罚
                            bay.setFastReplacements(numLost);
                            ship.getMutableStats().getFighterRefitTimeMult().modifyMult(ID_PUNISH_TIMER , 1+punish );
                        }
                    }
                }


                // 播放激活音效
                Global.getSoundPlayer().playSound(
                        FORGE_ACTIVE_SOUND_ID,
                        1.0f, // 音量
                        1.0f, // 音调
                        ship.getLocation(),
                        ship.getVelocity()
                );

                // 添加激活视觉效果
                Color jitterColor = new Color(255, 50, 50, 100);
                ship.setJitter(ship, jitterColor, 1.0f, 10, 0, 15f);

                // 显示状态信息
                if (ship == Global.getCombatEngine().getPlayerShip()) {
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            ID,
                            "graphics/icons/hullsys/ammo_feeder.png",
                            "战机锻炉激活",
                            "快速补充战机中...",
                            false
                    );
                }
            }
        }
    }

    /**
     * 生成锻炉粒子效果
     */
    private void generateForgeParticles(ShipAPI ship) {
        // 简单的粒子效果
        for (int i = 0; i < 3; i++) {
            Vector2f loc = MathUtils.getRandomPointInCircle(ship.getLocation(), ship.getCollisionRadius() * 0.8f);
            Vector2f vel = new Vector2f(
                    (float) Math.random() * 20f - 10f,
                    (float) Math.random() * 20f - 10f
            );

            Color color = new Color(255, 100, 50, 150);
            float size = 5f + (float) Math.random() * 10f;
            float duration = 0.5f + (float) Math.random();

            Global.getCombatEngine().addSmokeParticle(
                    loc,
                    vel,
                    size,
                    1.0f,
                    duration,
                    color
            );
        }
    }

    /**
     * 在船体模块描述后添加额外说明
     */
    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        tooltip.addPara("超弛战舰安全协议以在合适时机对战机进行快速补充。",
                10f, highlight, String.valueOf((int)FORGE_TOTAL_TIME));
        tooltip.addPara("减少战舰 %s 的峰值时间，加速 %s 战备衰减速率。",
                2f, bad, "40%" ,"25%");
        // 添加效果标题
        tooltip.addSectionHeading(
                "效果",
                Alignment.MID,
                10f
        );



        // 添加锻炉效果说明
        String fluxLevelString = String.valueOf((int)(100f * MAX_ACTIVE_FLUX_LEVEL)) + "%";
        tooltip.addPara("当战机存活率低于20%%且幅能水平低于%1$s时，激活战机锻炉系统，在%2$s秒内快速补充所有损失的战机。激活期间每秒产生相当于最大幅能%3$s的幅能。",
                10f, highlight,
                fluxLevelString,
                String.valueOf((int)FORGE_TOTAL_TIME),
                String.format("%.0f%%", FLUX_PERCENT_PER_SECOND * 100f));

        // 添加惩罚说明
        float punish = computePunish(ship);
        tooltip.addPara("装备装配点超过%1$s的重型战机时，补充时间增加：每超出1点装配点增加战机整备时间惩罚%2$s，当前惩罚：%3$s。",
                10f, bad,
                String.format("%.0f", PUNISH_START_OP),
                String.format("%.1f", PUNISH_PER_OP),
                String.format("%.1f", punish));

        // 添加战机增益说明
        tooltip.addPara("在母舰1000距离内的低速战机获得速度和转向增益。",
                10f, highlight);

        // 显示不兼容的模块（如果有的话）
        // showIncompatible(tooltip); // 这个方法在BaseHullMod中没有，需要自定义实现
    }

    public String getSModDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return  SMOD_BONUS*100f + "%";
        return null;
    }
    /**
     * 判断是否可以安装到舰船
     */
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null) return false;
        //int builtIn = ship.getHullSpec().getBuiltInWings().size();
        int bays =Math.round(ship.getMutableStats().getNumFighterBays().getBaseValue());
        // 只适用于有飞行甲板的舰船
        if(ship.getVariant().hasHullMod("CR_ImprovedWeaponControlling")) return false;
        if(!ship.getVariant().hasHullMod("CrusadersCore")) return false;
        if (bays <= 0) return false;
        return true;
    }

    /**
     * 获取不可安装的原因
     */
    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) return "舰船不存在";
        if (!ship.hasLaunchBays()) return "舰船没有飞行甲板";
        if(!ship.getVariant().hasHullMod("CrusadersCore")) return "仅能在拥有能量核心的舰船上安装";
        return null;
    }

    /**
     * 应用效果到舰船创建后
     */
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 清理可能存在的旧数据
        ship.removeCustomData(ID_FORGE);
        ship.removeCustomData(ID_FORGE_TIMER);
    }
}