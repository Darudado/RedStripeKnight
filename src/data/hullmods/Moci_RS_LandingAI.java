package data.hullmods;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.subsystems.drones.PIDController;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.Misc;

import data.scripts.Moci_RS_CollisionStateManager;
import data.scripts.util.Moci_AIRestoreUtil;

/**
 * Moci_LandingAI - 精密着陆AI控制器
 * 
 * 实现ShipAIPlugin接口，完全替代舰船的默认AI行为
 * 提供高精度的自动着陆和起飞控制
 * 
 * 核心特性：
 * - 三阶段状态机：MOVING（移动）→ LANDING（着陆）→ FINISHED_LANDING（完成着陆）
 * - PID控制器驱动的精确导航系统
 * - 动态速度和角度控制
 * - 双向操作支持（着陆/起飞）
 * - 实时安全检查和异常处理
 * - 使用碰撞状态管理器避免与其他功能的碰撞冲突
 * 
 * 技术特点：
 * - 使用MagicLib的PID控制器实现平滑控制
 * - 分层距离控制：600距离外自由移动，400-600距离精确制导，400距离内速度衰减
 * - 统一碰撞管理：通过Moci_CollisionStateManager避免碰撞状态冲突
 * - 支持动画序列和视觉效果
 */
public class Moci_RS_LandingAI implements ShipAIPlugin {
    private static final boolean ENABLE_DEBUG_LOGS = false;
    private static final String LOG_PREFIX = "[MOCI_LANDING] ";
    // ========== 动画模式开关 ==========
    /**
     * 动画模式开关
     * false: 使用自定义渐隐/渐显动画
     * true: 使用游戏原生着陆动画API
     */
    private static final boolean USE_NATIVE_ANIMATION = true;
    
    // ========== 着陆参数配置 ==========
    /**
     * 着陆触发距离（单位）
     */
    private static final float LANDING_TRIGGER_DISTANCE = 200f;
    
    /**
     * 着陆完成距离阈值（单位）
     * 放宽到30单位，降低精度要求
     */
    private static final float LANDING_COMPLETE_DISTANCE = 30f;
    private static final float NATIVE_LANDING_FALLBACK_TIME = 3.0f;
    
    /**
     * 着陆超时时间（秒）
     */
    private static final float LANDING_TIMEOUT = 50f;
    
    private ShipwideAIFlags flag; // AI标志
    private ShipAIConfig config; // AI配置
    private Moci_RS_RepairBayScript.Moci_RepairBay bay; // 目标修理舱
    private ShipAPI ship; // 控制的舰船
    private float delay = 0; // 起飞延迟计时器
    private boolean takeoffAnimationPlayed = false; // 是否已播放起飞动画
    private STATE state = STATE.MOVING; // 当前状态
    private PIDController controller = new PIDController(2f, 2f, 6f, 0.5f); // PID控制器（比例、积分、微分、最大输出）
    private boolean finished = false; // 是否完成着陆
    private float stateTimer = 0f; // 状态计时器，用于检测状态卡住
    private float positionCorrectionFactor = 0f; // 位置修正因子
    private float landingTimer = 0f; // 着陆延迟计时器
    private boolean landingTriggered = false; // 是否已触发着陆
    private float totalTimer = 0f; // 总计时器，用于超时检测

    // 轨迹平滑参数
    private Vector2f targetVelocity = new Vector2f(0f, 0f); // 目标速度向量
    private Vector2f lastTargetPosition = null; // 上一帧的目标位置
    private float approachFactor = 0f; // 接近因子，随距离变化

    private boolean shouldResetAI = true;

    // 碰撞状态管理
    private static final String COLLISION_MODIFIER_ID = "Moci_LandingAI";
    private static final int COLLISION_PRIORITY = 300; // 着陆AI的碰撞优先级
    private Moci_RS_CollisionStateManager collisionManager;

    /**
     * 着陆状态枚举
     * MOVING: 移动阶段 - 向目标修理舱移动
     * LANDING: 着陆阶段 - 精确控制进入修理舱
     * FINISHED_LANDING: 完成着陆 - 启动修理序列
     */
    public enum STATE {
        MOVING, LANDING, FINISHED_LANDING;
    }

    /**
     * 构造函数
     *
     * @param ship   要控制的舰船
     * @param flag   AI行为标志
     * @param config AI配置
     * @param bay    目标修理舱武器槽
     */
    public Moci_RS_LandingAI(ShipAPI ship, ShipwideAIFlags flag, ShipAIConfig config,
                             Moci_RS_RepairBayScript.Moci_RepairBay bay) {
        this.flag = flag;
        this.config = config;
        this.bay = bay;
        this.ship = ship;
        this.collisionManager = Moci_RS_CollisionStateManager.getInstance();

        // 初始化碰撞状态管理
        collisionManager.initializeDefaultCollision(ship);
        RS_Moci_MobileSuitRepairTracker.getOrCreate(ship).beginApproach(bay.getShip(), bay);
    }

    public Moci_RS_LandingAI(ShipAPI ship, ShipwideAIFlags flag, ShipAIConfig config,
                             Moci_RS_RepairBayScript.Moci_RepairBay bay, boolean shouldResetAI) {
        this.flag = flag;
        this.config = config;
        this.bay = bay;
        this.ship = ship;
        this.collisionManager = Moci_RS_CollisionStateManager.getInstance();
        this.shouldResetAI = shouldResetAI;

        // 初始化碰撞状态管理
        collisionManager.initializeDefaultCollision(ship);
        RS_Moci_MobileSuitRepairTracker.getOrCreate(ship).beginApproach(bay.getShip(), bay);
    }

    /**
     * 设置不开火延迟
     * 当delay>0时，舰船处于起飞模式
     */
    @Override
    public void setDoNotFireDelay(float amount) {
        this.delay = amount;
        this.takeoffAnimationPlayed = false; // 重置动画播放标记
    }

    @Override
    public void forceCircumstanceEvaluation() {

    }

    /**
     * 计算平滑的导航路径
     * 根据当前位置、目标位置和速度生成平滑的导航路径
     *
     * @param currentPos 当前位置
     * @param targetPos  目标位置
     * @param currentVel 当前速度
     * @param maxSpeed   最大速度
     * @param distance   与目标的距离
     * @param amount     时间步长
     * @return 计算得到的新速度向量
     */
    private Vector2f calculateSmoothPath(Vector2f currentPos, Vector2f targetPos, Vector2f currentVel,
            float maxSpeed, float distance, float amount) {
        // 初始化目标速度
        if (targetVelocity.length() < 0.1f) {
            targetVelocity = new Vector2f(currentVel);
        }

        // 初始化上一帧目标位置
        if (lastTargetPosition == null) {
            lastTargetPosition = new Vector2f(targetPos);
        }

        // 计算目标位置的移动速度（考虑母舰运动）
        Vector2f targetVel = new Vector2f(0f, 0f);
        if (bay != null && bay.getShip() != null) {
            targetVel = new Vector2f(bay.getShip().getVelocity());
        }

        // 计算理想方向向量
        Vector2f idealDir = new Vector2f(
                targetPos.x - currentPos.x,
                targetPos.y - currentPos.y);

        if (idealDir.length() < 0.1f) {
            return new Vector2f(0f, 0f); // 已经非常接近目标
        }

        // 标准化理想方向
        idealDir.normalise();

        // 计算当前速度的方向和大小
        float currentSpeed = currentVel.length();
        Vector2f currentDir = new Vector2f(currentVel);
        if (currentSpeed > 0.1f) {
            currentDir.normalise();
        } else {
            currentDir = new Vector2f(idealDir);
        }

        // 根据距离计算接近因子 - 提高接近速度
        float targetApproachFactor;
        if (distance > 600f) {
            targetApproachFactor = 0.1f; // 远距离时更强的修正
        } else if (distance > 300f) {
            targetApproachFactor = 0.15f + (600f - distance) / 300f * 0.15f; // 中距离渐进增强
        } else if (distance > 100f) {
            targetApproachFactor = 0.3f + (300f - distance) / 200f * 0.3f; // 近距离明显修正
        } else {
            targetApproachFactor = 0.6f + (100f - distance) / 100f * 0.4f; // 极近距离强力修正
        }

        // 平滑过渡接近因子
        approachFactor += (targetApproachFactor - approachFactor) * Math.min(amount * 5f, 0.2f);

        // 计算目标速度大小 - 优化减速曲线，保持更高速度
        float targetSpeed;
        if (distance > 500f) {
            targetSpeed = maxSpeed; // 远距离全速
        } else if (distance > 200f) {
            targetSpeed = maxSpeed * (0.85f + distance / 500f * 0.15f); // 中距离轻微减速
        } else if (distance > 100f) {
            targetSpeed = maxSpeed * 0.85f * (distance / 200f); // 近距离保持较高速度
        } else if (distance > 50f) {
            targetSpeed = maxSpeed * 0.7f * (distance / 100f); // 接近时适度减速
        } else if (distance > 20f) {
            targetSpeed = maxSpeed * 0.5f * (distance / 50f); // 最后阶段减速
        } else {
            targetSpeed = maxSpeed * 0.3f * (distance / 20f); // 极近距离减速
        }

        // 确保最低速度 - 提高到50单位，避免过慢
        targetSpeed = Math.max(targetSpeed, 50f);

        // 计算理想速度向量（考虑目标运动）
        Vector2f idealVelocity = new Vector2f(
                idealDir.x * targetSpeed + targetVel.x,
                idealDir.y * targetSpeed + targetVel.y);

        // 平滑插值到理想速度 - 提高响应速度
        targetVelocity.x += (idealVelocity.x - targetVelocity.x) * approachFactor * amount * 80f;
        targetVelocity.y += (idealVelocity.y - targetVelocity.y) * approachFactor * amount * 80f;

        // 限制最大速度
        float resultSpeed = targetVelocity.length();
        if (resultSpeed > maxSpeed * 1.5f) { // 允许更高的最大速度
            targetVelocity.normalise();
            targetVelocity.scale(maxSpeed * 1.5f);
        }

        // 更新上一帧目标位置
        lastTargetPosition = new Vector2f(targetPos);

        return targetVelocity;
    }

    /**
     * AI主循环推进方法
     * 处理起飞和着陆的完整逻辑
     */
    @Override
    public void advance(float amount) {
        // 更新状态计时器
        stateTimer += amount;
        totalTimer += amount;

        // 超时保护：50秒超时机制
        if (totalTimer > LANDING_TIMEOUT && state != STATE.FINISHED_LANDING) {
            logEvent("着陆超时，中止。state=" + state
                    + ", isLanding=" + ship.isLanding()
                    + ", isFinishedLanding=" + ship.isFinishedLanding()
                    + ", totalTimer=" + totalTimer);
            abort();
            return;
        }

        if (delay > 0) {
            // === 起飞模式 ===
            delay -= amount;

            if (USE_NATIVE_ANIMATION) {
                // ========== 使用原生起飞动画 ==========

                if (!takeoffAnimationPlayed) {
                    logEvent("开始播放原生起飞动画。");
                    Global.getSoundPlayer().playSound("fighter_takeoff", 1f, 1f, ship.getLocation(), new Vector2f());

                    ship.setInvalidTransferCommandTarget(false);

                    ship.setAnimatedLaunch();

                    // 恢复所有模块显示
                    for (ShipAPI module : ship.getChildModulesCopy()) {
                        if (module == null) continue;
                        module.setAnimatedLaunch();

                        // 结束整备期间的模块隐藏
                        if (module.getVariant().hasHullMod("Moci_module_collision_controller")) {
                            RS_Moci_ModuleCollisionSync.endRepairingHiding(module);
                        }
                    }

                    ship.setControlsLocked(false);
                    ship.setShipSystemDisabled(false);

                    takeoffAnimationPlayed = true;
                }

                if (delay <= 0) {
                    delay = 0;
                    logEvent("起飞延迟结束，执行起飞收尾。");
                    abort();
                    if (ship.getShipAI() != null) {
                        ship.getShipAI().setDoNotFireDelay(0.1f);
                    }

                    collisionManager.removeCollisionModifier(ship, COLLISION_MODIFIER_ID);
                    cleanupCustomData();

                    landingTriggered = false;
                    landingTimer = 0f;
                    positionCorrectionFactor = 0f;
                    takeoffAnimationPlayed = false;
                    totalTimer = 0f;
                }
            } else {
                boolean shouldPlayAnimation = Moci_SMALandingSequence.bayShouldHidden(bay);
                if (shouldPlayAnimation && !ship.hasListenerOfClass(Moci_RS_RepairBayScript.Moci_AnimationLaunch.class)) {
                    ship.addListener(new Moci_RS_RepairBayScript.Moci_AnimationLaunch(ship));
                }
                ship.turnOnTravelDrive(1f);
                ship.giveCommand(ShipCommand.ACCELERATE, null, 0);

                if (delay <= 0) {
                    delay = 0;
                    logEvent("自定义起飞模式延迟结束，执行起飞收尾。");
                    abort();
                    if (ship.getShipAI() != null) {
                        ship.getShipAI().setDoNotFireDelay(0.1f);
                    }
                    ship.turnOffTravelDrive();

                    collisionManager.removeCollisionModifier(ship, COLLISION_MODIFIER_ID);
                    cleanupCustomData();

                    landingTriggered = false;
                    landingTimer = 0f;
                    positionCorrectionFactor = 0f;
                    takeoffAnimationPlayed = false;
                    totalTimer = 0f;
                } else {
                    float continuousPushForce = 50f;
                    Vector2f pushDirection = new Vector2f(
                            (float) Math.cos(Math.toRadians(ship.getFacing())),
                            (float) Math.sin(Math.toRadians(ship.getFacing())));
                    Vector2f additionalVelocity = new Vector2f(
                            pushDirection.x * continuousPushForce * amount,
                            pushDirection.y * continuousPushForce * amount);
                    ship.getVelocity().translate(additionalVelocity.x, additionalVelocity.y);

                    float currentSpeed = ship.getVelocity().length();
                    float maxSpeed = ship.getMaxSpeedWithoutBoost() * 1.5f;
                    if (currentSpeed > maxSpeed) {
                        ship.getVelocity().scale(maxSpeed / currentSpeed);
                    }

                    ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
                }
            }
        } else {
            // 安全检查：确保目标修理舱仍然可用
            if (finished)
                return;
            if (bay.getShip() == null || !bay.getShip().isAlive()
                    || Moci_RS_RepairBayScript.getInstance(bay.getShip()) == null ||
                    (Moci_RS_RepairBayScript.getInstance(bay.getShip()) != null
                            && Moci_RS_RepairBayScript.getInstance(bay.getShip()).getBay(bay) == null)
                    || (Moci_RS_RepairBayScript.getInstance(bay.getShip()) != null
                            && Moci_RS_RepairBayScript.getInstance(bay.getShip()).getBay(bay) != null)
                            && Moci_RS_RepairBayScript.getInstance(bay.getShip()).getBay(bay).isOccupied()) {
                logEvent("目标整备湾失效，中止着陆。"
                        + " hostAlive=" + (bay.getShip() != null && bay.getShip().isAlive())
                        + ", bayOccupied=" + (Moci_RS_RepairBayScript.getInstance(bay.getShip()) != null
                        && Moci_RS_RepairBayScript.getInstance(bay.getShip()).getBay(bay) != null
                        && Moci_RS_RepairBayScript.getInstance(bay.getShip()).getBay(bay).isOccupied()));
                abort(); // 异常情况，中止着陆
                return;
            }

            Vector2f targetLoc = bay.getLocation(); // 目标位置
            float targetFacing = VectorUtils.getAngle(ship.getLocation(), targetLoc); // 目标朝向
            float d = Misc.getDistance(ship.getLocation(), targetLoc); // 距离

            // 设置为战机碰撞类别（使用碰撞管理器）
            collisionManager.setCollisionModifier(ship, COLLISION_MODIFIER_ID, COLLISION_PRIORITY,
                    CollisionClass.FIGHTER);

            switch (state) {
                case MOVING:
                    // === 移动阶段：向目标修理舱移动 ===
                    float dist = Misc.getDistance(ship.getLocation(), targetLoc);

                    // 使用平滑路径计算
                    Vector2f newVelocity = calculateSmoothPath(
                            ship.getLocation(),
                            targetLoc,
                            ship.getVelocity(),
                            ship.getMaxSpeedWithoutBoost(),
                            dist,
                            amount);

                    // 设置新的速度
                    ship.getVelocity().set(newVelocity);

                    // 平滑转向目标方向
                    controller.rotate(VectorUtils.getAngle(ship.getLocation(), targetLoc), ship);

                    // 检查是否进入着陆阶段
                    if (dist < 600f) {
                        state = STATE.LANDING;
                    }
                    break;

                case LANDING:
                    // === 着陆阶段：精确控制进入修理舱 ===
                    boolean quickLanding = Moci_SMALandingSequence.bayShouldHidden(bay);
                    float angleDiff;

                    if (USE_NATIVE_ANIMATION) {
                        // ========== 使用原生着陆动画 ==========
                        targetFacing = Misc.getAngleInDegrees(ship.getLocation(), bay.getLocation());

                        // 在触发距离时启动着陆动画，但继续执行移动和角度调整
                        if (d <= LANDING_TRIGGER_DISTANCE && !landingTriggered) {
                            if (!ship.isLanding()) {
                                logEvent("触发 beginLandingAnimation。distance=" + d
                                        + ", target=" + bay.getShip().getName());
                                ship.beginLandingAnimation(bay.getShip());
                                
                                // 隐藏所有模块
                                for (ShipAPI module : ship.getChildModulesCopy()) {
                                    if (module == null) continue;
                                    module.beginLandingAnimation(bay.getShip());
                                    
                                    // 启动整备期间的模块隐藏
                                    if (module.getVariant().hasHullMod("Moci_module_collision_controller")) {
                                        RS_Moci_ModuleCollisionSync.startRepairingHiding(module);
                                    }
                                }
                            }
                            
                            landingTriggered = true;
                            landingTimer = 0f;
                            logEvent("进入原生着陆触发态。isLanding=" + ship.isLanding()
                                    + ", isFinishedLanding=" + ship.isFinishedLanding());
                        }

                        // 继续执行速度控制和角度调整
                        Vector2f landingVelocity = calculateSmoothPath(
                                ship.getLocation(),
                                targetLoc,
                                ship.getVelocity(),
                                ship.getMaxSpeedWithoutBoost() * 1.2f,
                                d,
                                amount);

                        // 在距离较远时使用计算的速度
                        if (d > 50f) {
                            ship.getVelocity().set(landingVelocity);
                        }

                        // 平滑角度对齐
                        angleDiff = MathUtils.getShortestRotation(ship.getFacing(), targetFacing);
                        float alignFactor = Math.min(1.0f, (600f - d) / 600f * 3f);
                        float angleCorrection = angleDiff * alignFactor * 0.08f * amount * 60f;
                        ship.setFacing(ship.getFacing() + angleCorrection);

                        // 检查完成条件
                        if (landingTriggered) {
                            landingTimer += amount;

                            if (ship.isFinishedLanding()) {
                                logEvent("原生着陆完成标记生效，切换到 FINISHED_LANDING。distance=" + d);
                                state = STATE.FINISHED_LANDING;
                                stateTimer = 0f;
                            }
                            else if (landingTimer >= NATIVE_LANDING_FALLBACK_TIME && d <= LANDING_COMPLETE_DISTANCE) {
                                logEvent("原生着陆未给出完成标记，使用距离/时间兜底切换 FINISHED_LANDING。distance="
                                        + d + ", landingTimer=" + landingTimer);
                                state = STATE.FINISHED_LANDING;
                                stateTimer = 0f;
                            }
                        }

                        // 着陆过程中使用位置插值（在接近目标时）
                        if (ship.isLanding() && d > 5f && d <= 50f) {
                            float distToCarrierNormalized = d / bay.getShip().getCollisionRadius();
                            float f = 2.0f - Math.min(1.0f, distToCarrierNormalized);
                            
                            Vector2f currentLoc = ship.getLocation();
                            currentLoc.x = (targetLoc.x * (f * 0.1f) + currentLoc.x * (2 - f * 0.1f)) / 2;
                            currentLoc.y = (targetLoc.y * (f * 0.1f) + currentLoc.y * (2 - f * 0.1f)) / 2;
                            
                            Vector2f carrierVel = bay.getShip().getVelocity();
                            Vector2f currentVel = ship.getVelocity();
                            currentVel.x = (carrierVel.x * f + currentVel.x * (2 - f)) / 2;
                            currentVel.y = (carrierVel.y * f + currentVel.y * (2 - f)) / 2;
                        }

                        // 锁定控制
                        if (ship.isLanding()) {
                            ship.setShipSystemDisabled(true);
                            ship.setControlsLocked(true);
                            ship.setInvalidTransferCommandTarget(true);
                        }

                    } else {
                        if (!quickLanding) {
                            targetFacing = bay.getFacing();

                            Vector2f landingVelocity = calculateSmoothPath(
                                    ship.getLocation(),
                                    targetLoc,
                                    ship.getVelocity(),
                                    ship.getMaxSpeedWithoutBoost() * 1.0f,
                                    d,
                                    amount);

                            ship.getVelocity().set(landingVelocity);

                            angleDiff = MathUtils.getShortestRotation(ship.getFacing(), targetFacing);
                            float alignFactor = Math.min(1.0f, (600f - d) / 600f * 3f);
                            float angleCorrection = angleDiff * alignFactor * 0.08f * amount * 60f;
                            ship.setFacing(ship.getFacing() + angleCorrection);

                            if (d < 40f) {
                                positionCorrectionFactor += amount * (0.15f + (40f - d) / 40f * 0.25f);
                                positionCorrectionFactor = Math.min(positionCorrectionFactor, 0.3f);

                                Vector2f diff = Vector2f.sub(targetLoc, ship.getLocation(), null);
                                float posCorrectFactor = positionCorrectionFactor * (1.0f - d / 40f);
                                Vector2f correction = new Vector2f(diff.x * posCorrectFactor, diff.y * posCorrectFactor);
                                Vector2f newPosition = Vector2f.add(ship.getLocation(), correction, null);
                                ship.getLocation().set(newPosition);

                                if (d < 10f) {
                                    float speedFactor = Math.max(0.2f, d / 10f);
                                    ship.getVelocity().scale(speedFactor);
                                }
                            }
                        } else {
                            targetFacing = Misc.getAngleInDegrees(ship.getLocation(), bay.getLocation());

                            Vector2f landingVelocity = calculateSmoothPath(
                                    ship.getLocation(),
                                    targetLoc,
                                    ship.getVelocity(),
                                    ship.getMaxSpeedWithoutBoost() * 1.2f,
                                    d,
                                    amount);

                            ship.getVelocity().set(landingVelocity);

                            angleDiff = MathUtils.getShortestRotation(ship.getFacing(), targetFacing);
                            float alignFactor = Math.min(1.0f, (600f - d) / 600f * 3f);
                            float angleCorrection = angleDiff * alignFactor * 0.08f * amount * 60f;
                            ship.setFacing(ship.getFacing() + angleCorrection);

                            if (d < 60f) {
                                positionCorrectionFactor += amount * (0.1f + (60f - d) / 60f * 0.2f);
                                positionCorrectionFactor = Math.min(positionCorrectionFactor, 0.25f);

                                Vector2f diff = Vector2f.sub(targetLoc, ship.getLocation(), null);
                                float posCorrectFactor = positionCorrectionFactor * (1.0f - d / 60f);
                                Vector2f correction = new Vector2f(diff.x * posCorrectFactor, diff.y * posCorrectFactor);
                                Vector2f newPosition = Vector2f.add(ship.getLocation(), correction, null);
                                ship.getLocation().set(newPosition);

                                if (d < 15f) {
                                    float speedFactor = Math.max(0.15f, d / 15f);
                                    ship.getVelocity().scale(speedFactor);
                                }
                            }
                        }

                        // 检查着陆精度
                        angleDiff = MathUtils.getShortestRotation(ship.getFacing(), targetFacing);

                        // 触发条件：距离足够近即可触发
                        if (d <= LANDING_COMPLETE_DISTANCE && !landingTriggered) {
                            landingTriggered = true;
                            landingTimer = 0f;
                            logEvent("触发自定义着陆态。distance=" + d);
                            
                            ship.setShipSystemDisabled(true);
                            ship.setControlsLocked(true);
                            ship.setInvalidTransferCommandTarget(true);

                            if (quickLanding) {
                                if (!ship.hasListenerOfClass(Moci_RS_RepairBayScript.Moci_AnimationLanding.class)
                                        && ship.getExtraAlphaMult2() > 0f) {
                                    ship.addListener(new Moci_RS_RepairBayScript.Moci_AnimationLanding(ship));
                                }
                            }
                        }

                        if (landingTriggered) {
                            landingTimer += amount;
                            
                            ship.setShipSystemDisabled(true);
                            ship.setControlsLocked(true);
                            ship.setInvalidTransferCommandTarget(true);

                            if (landingTimer >= 0.5f) {
                                if (d <= 20f || ship.isFinishedLanding()) {
                                    logEvent("自定义着陆完成，切换到 FINISHED_LANDING。distance=" + d
                                            + ", isFinishedLanding=" + ship.isFinishedLanding());
                                    state = STATE.FINISHED_LANDING;
                                    stateTimer = 0f;
                                }
                                else if (landingTimer >= 3.0f && d <= LANDING_COMPLETE_DISTANCE) {
                                    logEvent("自定义着陆使用兜底切换 FINISHED_LANDING。distance=" + d
                                            + ", landingTimer=" + landingTimer);
                                    state = STATE.FINISHED_LANDING;
                                    stateTimer = 0f;
                                }
                            }
                        }
                    }

                    // 角度修正缓动（两种模式通用）
                    if (!USE_NATIVE_ANIMATION || !ship.isLanding()) {
                        if (Math.abs(angleDiff) <= 20f && Math.abs(ship.getAngularVelocity()) >= 15f) {
                            ship.setAngularVelocity(ship.getAngularVelocity() * 0.999f);
                        }
                        if (Math.abs(angleDiff) <= 10f && Math.abs(ship.getAngularVelocity()) >= 7f) {
                            ship.setAngularVelocity(ship.getAngularVelocity() * 0.9f);
                        }
                        if (Math.abs(angleDiff) <= 3f && Math.abs(ship.getAngularVelocity()) >= 4f) {
                            ship.setAngularVelocity(ship.getAngularVelocity() * 0.1f);
                        }
                    }

                    break;

                case FINISHED_LANDING:
                    if (!finished) {
                        logEvent("开始调用 startRepair。isLanding=" + ship.isLanding()
                                + ", isFinishedLanding=" + ship.isFinishedLanding());
                        Moci_RS_RepairBayScript.getInstance(bay.getShip()).getBay(bay).startRepair(ship);
                        finished = true;
                    }
                    
                    ship.setShipSystemDisabled(true);
                    ship.setControlsLocked(true);
                    ship.setInvalidTransferCommandTarget(true);
                    break;
            }

            // 状态变化时重置计时器
            STATE oldState = state;
            if (oldState != state) {
                logEvent("状态切换: " + oldState + " -> " + state
                        + ", distance=" + d
                        + ", isLanding=" + ship.isLanding()
                        + ", isFinishedLanding=" + ship.isFinishedLanding());
                stateTimer = 0f;
                if (state == STATE.LANDING) {
                    landingTriggered = false;
                    landingTimer = 0f;
                    positionCorrectionFactor = 0f;
                }
            }

            if (ship.equals(Global.getCombatEngine().getPlayerShip())) {
                String statusText = "";
                switch (state) {
                    case MOVING:
                        statusText = "撤退中";
                        break;
                    case LANDING:
                        statusText = "正在着陆";
                        break;
                    case FINISHED_LANDING:
                        statusText = "正在整备";
                        break;
                }
                Global.getCombatEngine().maintainStatusForPlayerShip("Moci_LANDINGAI",
                        Global.getSettings().getSpriteName("ui", "icon_tactical_bdeck"), "整备程序", statusText, true);
            }

            if (delay > 0 && ship.equals(Global.getCombatEngine().getPlayerShip())) {
                Global.getCombatEngine().maintainStatusForPlayerShip("Moci_TAKEOFF",
                        Global.getSettings().getSpriteName("ui", "icon_tactical_bdeck"), "整备程序", "准备起飞", true);
            }
        }
    }

    @Override
    public boolean needsRefit() {
        return false;
    }

    @Override
    public ShipwideAIFlags getAIFlags() {
        return flag;
    }

    @Override
    public void cancelCurrentManeuver() {

    }

    @Override
    public ShipAIConfig getConfig() {
        return config;
    }

    protected boolean shouldRemove = false;

    public boolean shouldRemove() {
        return shouldRemove;
    }

    /**
     * 清理所有与着陆/起飞相关的自定义数据
     * 确保在多次进入自动驾驶时不会有残留数据影响逻辑
     */
    private void cleanupCustomData() {
        // 清除起飞动画标记
        ship.setCustomData("Moci_LaunchAnimStarted", null);
        // 清除着陆AI活跃标记
        ship.setCustomData("Moci_LandingAIActive", false);
        // 清除位置记录和卡住计时器
        ship.setCustomData("Moci_LastLandingPosition", null);
        ship.setCustomData("Moci_StuckTimer", null);

        // 确保巡航加速关闭
        ship.turnOffTravelDrive();

    }

    protected void abort() {
        logEvent("abort() 被调用。shouldResetAI=" + shouldResetAI
                + ", state=" + state
                + ", isLanding=" + ship.isLanding()
                + ", isFinishedLanding=" + ship.isFinishedLanding());
        if (shouldResetAI) {
            Moci_AIRestoreUtil.restoreDefaultAI(ship, config);
        } else {
            shouldRemove = true;
        }
        ship.setCustomData("Moci_LandingAIActive", false);
        collisionManager.removeCollisionModifier(ship, COLLISION_MODIFIER_ID);
        RS_Moci_MobileSuitRepairTracker tracker = RS_Moci_MobileSuitRepairTracker.get(ship);
        if (tracker != null) {
            tracker.markAborted();
        }
    }

    private void logEvent(String message) {
        if (!ENABLE_DEBUG_LOGS) {
            return;
        }
        Global.getLogger(Moci_RS_LandingAI.class).info(LOG_PREFIX + ship.getName() + " - " + message);
    }

    /**
     * 获取当前着陆状态
     * 
     * @return 当前状态
     */
    public STATE getState() {
        return state;
    }

    /**
     * 获取目标修理舱
     * 
     * @return 修理舱对象
     */
    public Moci_RS_RepairBayScript.Moci_RepairBay getBay() {
        return bay;
    }

    /**
     * 获取状态计时器
     * 
     * @return 当前状态持续时间
     */
    public float getStateTimer() {
        return stateTimer;
    }
}
