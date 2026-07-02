package data.hullmods.Installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.ProjectileSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.combat.entities.BallisticProjectile;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CR_ChargingRing extends BaseHullMod {
    private static final String BASIC_BOUN_ID = "cr_chargingring_base";
    private static final float BASIC_BOUN = 35f;
    private static final float SMOD_BONUS = 5f;
    private static final float FLUX_THRESHOLD = 0.65f; // 65%辐能触发
    private static final float FLUX_REDUCTION = 0.25f; // 减少25%辐能
    private static final float COOLDOWN_DURATION = 30f; // 30秒冷却

    // 颜色定义
    private static final Color SHIELD_COLOR = new Color(173,14,14, 200);
    private static final Color EMP_COLOR = new Color(194,49,49, 255);
    private static final Color PARTICLE_COLOR = new Color(242,110,100, 150);

    // 内部数据类
    public static class ChargingRingData {
        public enum State {
            READY("Ready", "System is ready", 0f),
            CHARGING("Charging", "Shield recharging... %s%%", 1f),
            ACTIVE("activation", "The shield explodes! %s seconds", 0.1f),
            COOLDOWN("cool down", "System cooling... %s seconds", COOLDOWN_DURATION);

            public final String statusTitle;
            public final String statusDesc;
            public final float timeNeededToNextState;

            State(String title, String desc, float time) {
                this.statusTitle = title;
                this.statusDesc = desc;
                this.timeNeededToNextState = time;
            }
        }

        private State state;
        private float elapsed;
        private float damageStorage; // 存储的伤害量
        private float fluxCheckTimer; // 辐能检查计时器
        private boolean cooldownStarted; // 标记冷却是否已经开始

        // 临时数据
        private ShieldAPI lastShield;
        private float lastActiveArc;

        public ChargingRingData() {
            this.state = State.READY;
            this.elapsed = 0f;
            this.damageStorage = 0f;
            this.fluxCheckTimer = 0f;
            this.cooldownStarted = false;
        }

        public void advance(float amount, ShipAPI ship) {
            if (amount == 0f || ship == null || !ship.isAlive()) return;

            CombatEngineAPI engine = Global.getCombatEngine();
            ShieldAPI shield = ship.getShield();

            // 如果没有护盾，直接返回（不重置状态）
            if (shield == null) {
                return;
            }

            // 检查护盾状态变化
            if (shield != lastShield || Math.abs(shield.getActiveArc() - lastActiveArc) > 1f) {
                lastShield = shield;
                lastActiveArc = shield.getActiveArc();
            }

            // 状态机逻辑
            switch (state) {
                case READY:
                    // 检查是否在冷却中
                    if (cooldownStarted) {
                        // 如果还在冷却中，不能进入充能状态
                        break;
                    }

                    // 护盾关闭时，不检查辐能
                    if (!shield.isOn()) {
                        break;
                    }

                    // 每0.5秒检查一次辐能水平
                    fluxCheckTimer += amount;
                    if (fluxCheckTimer >= 0.5f) {
                        fluxCheckTimer = 0f;

                        // 检查辐能是否超过65%
                        float fluxLevel = ship.getFluxTracker().getFluxLevel();
                        if (fluxLevel >= FLUX_THRESHOLD) {
                            // 开始充能
                            state = State.CHARGING;
                            elapsed = 0f;
                            damageStorage = 0f; // 重置伤害存储
                            cooldownStarted = false; // 重置冷却标记

                            // 添加充能音效
                            //Global.getSoundPlayer().playSound("system_shield_raise", 1f, 1f, ship.getLocation(), ship.getVelocity());
                        }
                    }

                    // 准备特效（只在护盾开启时显示）
                    if (shield.isOn() && engine.getTotalElapsedTime(false) % 2f < amount) {
                        Vector2f loc = MathUtils.getRandomPointInCircle(
                                ship.getLocation(),
                                shield.getRadius() * 0.8f
                        );
                        Vector2f vel = new Vector2f(ship.getVelocity());
                        engine.addHitParticle(loc, vel, 3f, 0.5f, 0.3f,
                                new Color(100, 150, 255, 100));
                    }
                    break;

                case CHARGING:
                    // 护盾关闭时中断充能
                    if (!shield.isOn()) {
                        state = State.READY;
                        elapsed = 0f;
                        fluxCheckTimer = 0f;
                        damageStorage = 0f;

                        // 移除临时加成
                        ship.getMutableStats().getShieldDamageTakenMult().unmodify("cr_chargingring");
                        ship.getMutableStats().getShieldUnfoldRateMult().unmodify("cr_chargingring");
                        break;
                    }

                    elapsed += amount;

                    // 收集护盾受到的伤害
                    collectShieldDamage(ship, amount);

                    // 充能特效
                    float chargeProgress = Math.min(1f, elapsed / state.timeNeededToNextState);

                    // 护盾强化效果
                    MutableShipStatsAPI stats = ship.getMutableStats();
                    stats.getShieldDamageTakenMult().modifyMult("cr_chargingring", 1f - 0.9f * chargeProgress);
                    stats.getShieldUnfoldRateMult().modifyMult("cr_chargingring", 1f - 0.9f * chargeProgress);

                    // 视觉特效
                    ship.setJitter("cr_chargingring", SHIELD_COLOR, chargeProgress, 10,
                            (60f - 30f * chargeProgress) * getStrengthFactor());

                    // 粒子效果
                    if (engine.getTotalElapsedTime(false) % 0.05f < amount) {
                        spawnChargingParticles(shield, chargeProgress);
                    }

                    if (elapsed >= state.timeNeededToNextState) {
                        // 充能完成，准备爆发
                        state = State.ACTIVE;
                        elapsed = 0f;
                    }
                    break;

                case ACTIVE:
                    elapsed += amount;

                    if (elapsed >= state.timeNeededToNextState) {
                        // 执行护盾爆发
                        executeShieldBurst(ship, shield);

                        // 减少25%辐能
                        float currentFlux = ship.getCurrFlux();
                        float fluxToReduce = currentFlux * FLUX_REDUCTION;
                        ship.getFluxTracker().decreaseFlux(fluxToReduce);

                        // 进入冷却
                        state = State.COOLDOWN;
                        elapsed = 0f;
                        cooldownStarted = true; // 标记冷却已开始

                        // 移除临时加成
                        ship.getMutableStats().getShieldDamageTakenMult().unmodify("cr_chargingring");
                        ship.getMutableStats().getShieldUnfoldRateMult().unmodify("cr_chargingring");
                    }
                    break;

                case COOLDOWN:
                    elapsed += amount;

                    // 冷却特效（无论护盾是否开启都显示）
                    if (engine.getTotalElapsedTime(false) % 1f < amount) {
                        float radius = shield.isOn() ? shield.getRadius() * 0.6f : ship.getCollisionRadius() * 0.8f;
                        Vector2f loc = MathUtils.getRandomPointInCircle(
                                ship.getLocation(),
                                radius
                        );
                        Vector2f vel = new Vector2f(ship.getVelocity());
                        engine.addHitParticle(loc, vel, 4f, 0.4f, 0.4f,
                                new Color(100, 100, 200, 80));
                    }

                    if (elapsed >= state.timeNeededToNextState) {
                        state = State.READY;
                        elapsed = 0f;
                        fluxCheckTimer = 0f;
                        cooldownStarted = false; // 冷却完成
                    }
                    break;
            }

            // 更新玩家状态显示
            if (engine.getPlayerShip() == ship) {
                updatePlayerStatus();
            }
        }

        private void collectShieldDamage(ShipAPI ship, float amount) {
            // 简化版的伤害收集：基于时间模拟伤害累积
            if (ship.getShield() != null && ship.getShield().isOn()) {
                // 根据护盾承受的攻击强度来增加伤害存储
                float baseDamageRate = 100f; // 每秒基础伤害率
                damageStorage += baseDamageRate * amount;

                // 限制最大存储量
                damageStorage = Math.min(damageStorage, 3000f);
            }
        }

        private float getStrengthFactor() {
            if (damageStorage < 500f) {
                return 0.5f * (1f + damageStorage / 500f);
            } else {
                float factor = 1f + 0.5f * (damageStorage / 3000f);
                return Math.min(2.0f, factor); // 限制最大强度
            }
        }

        private void spawnChargingParticles(ShieldAPI shield, float effectLevel) {
            CombatEngineAPI engine = Global.getCombatEngine();
            Vector2f center = shield.getLocation();
            float radius = shield.getRadius();
            float activeArc;
            if(shield.getActiveArc() == 0f){
                activeArc = 360f;
            }
            else {
                activeArc = shield.getActiveArc();
            }
            float facing = shield.getFacing();

            // 简化版粒子生成
            for (int i = 0; i < 3; i++) {
                float particleDistance = MathUtils.getRandomNumberInRange(
                        radius * 0.8f, radius * 1.2f);
                float particleAngle = MathUtils.getRandomNumberInRange(
                        facing - activeArc / 2f, facing + activeArc / 2f);

                Vector2f loc = MathUtils.getPointOnCircumference(center, particleDistance, particleAngle);
                Vector2f vel = VectorUtils.getDirectionalVector(loc, center);
                vel.scale(MathUtils.getRandomNumberInRange(50f, 150f));

                float size = MathUtils.getRandomNumberInRange(3f, 6f) * (1f + effectLevel);
                float duration = MathUtils.getRandomNumberInRange(0.3f, 0.5f);

                engine.addSmoothParticle(loc, vel, size, 1f, 0.5f, duration, PARTICLE_COLOR);
            }
        }

        private void executeShieldBurst(ShipAPI ship, ShieldAPI shield) {
            CombatEngineAPI engine = Global.getCombatEngine();
            Vector2f center = shield.getLocation();
            float radius = shield.getRadius();

            // 修复弧角计算
            float activeArc = shield.getActiveArc() == 0f ? 360f : shield.getActiveArc();
            float arcMax = shield.getArc();

            // 计算扩散因子
            float spreadFactor;
            if (arcMax > 0) {
                float arcRatio = Math.min(activeArc / arcMax, 1.5f);
                spreadFactor = Math.max(0.1f, 1f - arcRatio * 0.8f);
            } else {
                spreadFactor = 0.2f;
            }

            float strengthFactor = Math.min(3.0f, Math.max(0.5f, getStrengthFactor()));

            // 视觉电弧生成（仅视觉效果）
            float arcInterval = Math.max(7f, activeArc / 50f);
            List<Vector2f> arcPoints = new ArrayList<>();

            for (float angleOffset = 0; angleOffset < activeArc / 2f; angleOffset += arcInterval) {
                for (int direction = -1; direction <= 1; direction += 2) {
                    float angle;
                    if(!(shield ==null)){
                        angle = shield.getFacing() + direction * angleOffset;
                    }else{
                        angle = ship.getFacing() + direction * angleOffset;
                    }
                    Vector2f startPoint = MathUtils.getPointOnCircumference(center, radius, angle);
                    Vector2f endPoint = MathUtils.getPointOnCircumference(startPoint, 800f, angle);

                    engine.spawnEmpArcVisual(
                            startPoint, ship, endPoint, null,
                            MathUtils.getRandomNumberInRange(20f, 40f) * spreadFactor,
                            EMP_COLOR, SHIELD_COLOR
                    );
                    arcPoints.add(startPoint);

                    // 添加粒子效果
                    for (int i = 0; i < 3; i++) {
                        Vector2f particleLoc = MathUtils.getRandomPointInCircle(startPoint, 20f);
                        Vector2f particleVel = MathUtils.getPointOnCircumference(null,
                                MathUtils.getRandomNumberInRange(100f, 200f),
                                angle + MathUtils.getRandomNumberInRange(-30f, 30f));
                        engine.addNebulaParticle(particleLoc, particleVel,
                                MathUtils.getRandomNumberInRange(30f, 60f) * spreadFactor,
                                1.5f, 0.3f, 0f, 0.5f, EMP_COLOR);
                    }
                }
            }

            // 对范围内的所有敌人造成伤害（排除弹丸）
            float baseDamage = 7500f;
            List<CombatEntityAPI> entities = CombatUtils.getEntitiesWithinRange(center, 900f);
            for (CombatEntityAPI entity : entities) {
                // 排除自身、友军、无效实体及弹丸
                if (entity == null || entity == ship || entity.getOwner() == ship.getOwner()) continue;
                if (!(entity instanceof ShipAPI) && !(entity instanceof MissileAPI)) continue;
                if (entity instanceof ProjectileSpecAPI) continue; // 关键：过滤弹丸
                if (entity instanceof BallisticProjectile) continue;

                boolean isValid = true;
                if (entity instanceof ShipAPI) {
                    if (!((ShipAPI) entity).isAlive()) isValid = false;
                } else {
                    MissileAPI missile = (MissileAPI) entity;
                    if (missile.isFading() || missile.getOwner() < 0) isValid = false;
                }
                if (!isValid || entity.getLocation() == null) continue;

                float distance = MathUtils.getDistance(center, entity.getLocation());
                float distanceFactor = Math.max(0.1f, 1f - (distance / 900f));

                boolean inShieldArc = shield.isWithinArc(entity.getLocation());
                float arcDamageMultiplier;
                if (inShieldArc) {
                    float arcDamageScale = Math.max(0.3f, 1f - (activeArc / 720f));
                    arcDamageMultiplier = strengthFactor * arcDamageScale;
                } else {
                    arcDamageMultiplier = strengthFactor * 0.15f;
                }

                float damage = baseDamage * distanceFactor * arcDamageMultiplier;
                if (entity instanceof ShipAPI targetShip) {
                    if (targetShip.isFighter()) damage *= 0.5f;
                    if (targetShip.isPhased()) damage *= 0.25f;
                } else {
                    damage *= 0.75f;
                }
                damage = Math.min(damage, 15000f);


                Vector2f hitPoint = new Vector2f(entity.getLocation());
                if (distance > 0) {
                    Vector2f direction = Vector2f.sub(entity.getLocation(), ship.getLocation(), null);
                    direction.normalise();
                    // 将命中点从船舰中心向爆炸中心方向移动碰撞半径的距离
                    hitPoint.x -= direction.x * entity.getCollisionRadius();
                    hitPoint.y -= direction.y * entity.getCollisionRadius();
                }

                // 直接应用伤害，不使用 spawnEmpArc 避免额外弹丸
                engine.applyDamage(entity, hitPoint, damage, DamageType.ENERGY, 0f,
                        false, false, ship);

                // 施加击退效果
                float force = 3500f * distanceFactor * arcDamageMultiplier;
                force = Math.min(force, 5000f);
                CombatUtils.applyForce(entity,
                        VectorUtils.getAngle(center, entity.getLocation()),
                        force);
            }

            // 添加视觉冲击效果
            for (Vector2f point : arcPoints) {
                engine.spawnExplosion(point, ship.getVelocity(),
                        new Color(150, 200, 255, 200),
                        MathUtils.getRandomNumberInRange(50f, 100f) * strengthFactor,
                        0.5f);
            }
        }

        private void updatePlayerStatus() {
            CombatEngineAPI engine = Global.getCombatEngine();
            String description;

            switch (state) {
                case READY:
                    if (cooldownStarted) {
                        description = "Cooling down...";
                    } else {
                        description = "Wait for radiation to reach 65%";
                    }
                    break;
                case CHARGING:
                    float chargePercent = (elapsed / state.timeNeededToNextState) * 100f;
                    description = String.format(state.statusDesc, String.format("%.0f%%", chargePercent));
                    break;
                case ACTIVE:
                    description = "The shield is exploding!";
                    break;
                case COOLDOWN:
                    float remaining = state.timeNeededToNextState - elapsed;
                    description = String.format(state.statusDesc, String.format("%.1f", remaining));
                    break;
                default:
                    description = "";
            }

            engine.maintainStatusForPlayerShip(
                    "cr_chargingring_status",
                    "graphics/icons/hullsys/fortress_shield.png",
                    state.statusTitle,
                    description,
                    state == State.COOLDOWN || (state == State.READY && cooldownStarted)
            );
        }

        public State getState() {
            return state;
        }

    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // 基础加成
        stats.getShieldUpkeepMult().modifyPercent(BASIC_BOUN_ID, -BASIC_BOUN);
        stats.getShieldTurnRateMult().modifyPercent(BASIC_BOUN_ID, BASIC_BOUN);
        stats.getShieldUnfoldRateMult().modifyPercent(BASIC_BOUN_ID, BASIC_BOUN);

        boolean sMod = isSMod(stats);
        if (sMod) {
            stats.getShieldSoftFluxConversion().modifyPercent(id, SMOD_BONUS);
            stats.getShieldDamageTakenMult().modifyPercent(id, -SMOD_BONUS);
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        // 确保有护盾
        if (ship.getShield() == null) return;

        // 获取或创建数据
        ChargingRingData data = getOrCreateData(ship);

        // 更新数据
        data.advance(amount, ship);
    }

    private ChargingRingData getOrCreateData(ShipAPI ship) {
        String key = "cr_chargingring_data_" + ship.getId();
        CombatEngineAPI engine = Global.getCombatEngine();

        ChargingRingData data = (ChargingRingData) engine.getCustomData().get(key);
        if (data == null) {
            data = new ChargingRingData();
            engine.getCustomData().put(key, data);
        }

        return data;
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return String.format("%.0f", BASIC_BOUN) + "%";
        if (index == 1) return String.format("%.0f", BASIC_BOUN) + "%";
        return null;
    }

    public String getSModDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return String.format("%.0f", SMOD_BONUS) + "%";
        if (index == 1) return String.format("%.0f", SMOD_BONUS) + "%";
        return null;
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float pads = 10f;
        float pad = 2f;
        tooltip.addPara("Specially tuned energy systems automatically and explosively dissipate grid waste heat", Misc.getHighlightColor(), pads);

        tooltip.addSectionHeading("energy burst", Alignment.MID, pads);
        tooltip.addPara("Automatically triggered when radiation level is greater than %s and shield is on", pad, Misc.getHighlightColor(), "65%");
        tooltip.addPara("Releases an energy blast in the direction of the shield, causing damage and knocking back enemy units.", new Color(150, 200, 255, 255), pads);
        tooltip.addPara("And dissipate %s of the ship's radiation energy", pad, Misc.getHighlightColor(), "25%");
        tooltip.addPara("The system has a 30-second cooldown. Turning off the shield during the cooldown period will not reset the timer.", Misc.getHighlightColor(), pad);
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        // 只适用于有护盾的舰船
        if (ship == null) return false;
        if (ship.getShield() == null) return false;
        if(ship.getVariant().hasHullMod("CR_EngineRegularBoost")) return false;
        if(ship.getVariant().hasHullMod("CR_StructureUpgrading")) return false;
        if(ship.getVariant().hasHullMod("CR_ShieldOscillating")) return false;
        return ship.getVariant().hasHullMod("CrusadersCore");
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) return "ship does not exist";
        if (ship.getShield() == null) return "The ship has no shields";
        if (!ship.getVariant().hasHullMod("CrusadersCore")) return "Requires Crusader Core";
        return null;
    }
}