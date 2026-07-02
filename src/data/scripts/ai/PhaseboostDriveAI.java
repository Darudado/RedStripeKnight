package data.scripts.ai;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.shipsystems.PhaseboostDrive;
import org.lazywizard.lazylib.CollectionUtils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class PhaseboostDriveAI implements ShipSystemAIScript {
    private static CombatEngineAPI engine;          // 战斗引擎实例
    private ShipwideAIFlags flags;           // 舰船AI标志
    private ShipAPI ship;                    // 装配此系统的舰船
    private ShipSystemAPI system;            // 推进器系统实例
    private final IntervalUtil tracker = new IntervalUtil(0.05F, 0.1F); // 定期检查的计时器
    private final Object STATUSKEY1 = new Object();  // 状态键（未实际使用）
    private final Object STATUSKEY2 = new Object();  // 状态键（未实际使用）

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.system = system;
        PhaseboostDriveAI.engine = engine;
    }

    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine != null) {
            if (!engine.isPaused()) {
                this.tracker.advance(amount);
                if (this.tracker.intervalElapsed()) {
                    if (this.ship.getFluxTracker().isOverloadedOrVenting() || this.system.getAmmo() == 0 || !AIUtils.canUseSystemThisFrame(this.ship) || this.ship.isLanding()) {
                        return;
                    }

                    float desire = 0.0F;
                    if (this.ship.getVariant().hasHullMod("PolariphaseDrive")) {
                        if (this.ship.getEngineController().isFlamedOut()) {
                            desire += 3.0F;
                        }
                    } else if (this.ship.getEngineController().isFlamedOut()) {
                        return;
                    }



                    boolean returning = this.ship.getWing() != null && this.ship.getWing().isReturning(this.ship);

                    float engageRange;
                    if (this.ship.getWing() != null) {
                        if (returning) {
                            engageRange = 650.0F;
                        } else {
                            engageRange = this.ship.getWing().getSpec().getAttackRunRange();
                        }
                    } else {
                        engageRange = 1200.0F;

                        for (WeaponAPI weapon : this.ship.getUsableWeapons()) {
                            if (weapon.getType() != WeaponAPI.WeaponType.MISSILE && weapon.getRange() > engageRange) {
                                engageRange = weapon.getRange();
                            }
                        }
                    }

                    CombatEntityAPI immediateTarget;
                    ShipAPI carrier = null;
                    List<CombatEntityAPI> targetPriority = new ArrayList<>();

// 1. 母舰指定的目标
                    if (this.ship.getWing() != null) {
                        carrier = this.ship.getWing().getSourceShip();
                        if (carrier != null) {
                            Object customTarget = carrier.getAIFlags().getCustom(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET);
                            if (customTarget instanceof CombatEntityAPI) {
                                targetPriority.add((CombatEntityAPI) customTarget);
                            }
                        }
                    }

// 2. 自身标记的目标
                    Object customTarget = this.flags.getCustom(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET);
                    if (customTarget instanceof CombatEntityAPI) {
                        targetPriority.add((CombatEntityAPI) customTarget);
                    }

// 3. 机动目标
                    customTarget = this.flags.getCustom(ShipwideAIFlags.AIFlags.MANEUVER_TARGET);
                    if (customTarget instanceof CombatEntityAPI) {
                        targetPriority.add((CombatEntityAPI) customTarget);
                    }

// 4. 最后选择舰船自身目标
                    if (this.ship.getShipTarget() != null) {
                        targetPriority.add(this.ship.getShipTarget());
                    }

// 选择第一个有效目标
                    immediateTarget = targetPriority.isEmpty() ? null : targetPriority.get(0);

                    if (immediateTarget == null && this.ship.isFighter() && this.flags.getCustom(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET) instanceof CombatEntityAPI) {
                        immediateTarget = (CombatEntityAPI) this.flags.getCustom(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET);
                    }

                    if (immediateTarget == null && this.flags.getCustom(ShipwideAIFlags.AIFlags.MANEUVER_TARGET) instanceof CombatEntityAPI) {
                        immediateTarget = (CombatEntityAPI) this.flags.getCustom(ShipwideAIFlags.AIFlags.MANEUVER_TARGET);
                    }

                    if (immediateTarget == null) {
                        immediateTarget = this.ship.getShipTarget();
                    }

                    CombatFleetManagerAPI.AssignmentInfo assignment = engine.getFleetManager(this.ship.getOwner()).getTaskManager(this.ship.isAlly()).getAssignmentFor(this.ship);
                    Vector2f targetSpot;
                    if (this.ship.isFighter()) {
                        assignment = null;
                        targetSpot = null;
                    } else if (assignment != null && assignment.getTarget() != null && assignment.getType() != CombatAssignmentType.AVOID) {
                        targetSpot = assignment.getTarget().getLocation();
                    } else {
                        targetSpot = null;
                    }

                    boolean isEliteDrive = this.ship.getVariant().hasHullMod("PolariphaseDrive");

                     // 在时间关键操作中增加权重
                    if (isEliteDrive) {
                        if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.IN_CRITICAL_DPS_DANGER)) {
                            desire += 1.2f; // 精英插件在危险时更倾向使用系统
                        }

                        // 提高撤退时的欲望值
                        if (assignment != null && assignment.getType() == CombatAssignmentType.RETREAT) {
                            desire += 0.8f;
                        }
                    }

                    Vector2f direction;
                    PhaseboostDrive.BASELINE_MULT.get(this.ship.getHullSize());
                    float boostScale;
                    BoostDirectionResult result = calculateBoostDirection();
                    direction = result.direction;
                    boostScale = result.boostScale;

                    if (direction.length() <= 0.0F) {
                        direction.y = PhaseboostDrive.BASELINE_MULT.get(this.ship.getHullSize()) - PhaseboostDrive.FORWARD_PENALTY.get(this.ship.getHullSize());
                        boostScale -= PhaseboostDrive.FORWARD_PENALTY.get(this.ship.getHullSize());
                    }

                    Misc.normalise(direction);
                    VectorUtils.rotate(direction, this.ship.getFacing() - 90.0F, direction);
                    float angleToTargetSpot = 0.0F;
                    if (targetSpot != null) {
                        float targetSpotDir = VectorUtils.getAngleStrict(this.ship.getLocation(), targetSpot);
                        angleToTargetSpot = MathUtils.getShortestRotation(VectorUtils.getFacing(direction), targetSpotDir);
                    }

                    float angleToImmediateTarget = 0.0F;
                    if (immediateTarget != null) {
                        float immediateTargetDir = VectorUtils.getAngleStrict(this.ship.getLocation(), immediateTarget.getLocation());
                        angleToImmediateTarget = MathUtils.getShortestRotation(VectorUtils.getFacing(direction), immediateTargetDir);
                    }

                    float onTargetThreshold;
                    if (this.ship.isFighter()) {
                        onTargetThreshold = 45.0F;
                    } else {
                        onTargetThreshold = 60.0F;
                    }

                    if (!this.ship.isFighter()) {
                        if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.RUN_QUICKLY)) {
                            if (immediateTarget != null) {
                                if (Math.abs(angleToImmediateTarget) >= 90.0F) {
                                    ++desire;
                                }
                            } else {
                                desire += 0.75F;
                            }
                        }

                        if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.PURSUING)) {
                            if (immediateTarget != null) {
                                if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                                    desire += 0.75F;
                                }
                            } else if (targetSpot != null) {
                                if (Math.abs(angleToTargetSpot) <= onTargetThreshold) {
                                    desire += 0.5F;
                                }
                            } else {
                                desire += 0.25F;
                            }
                        }

                        if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.HARASS_MOVE_IN)) {
                            if (immediateTarget != null) {
                                if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                                    ++desire;
                                }
                            } else if (targetSpot != null) {
                                if (Math.abs(angleToTargetSpot) <= onTargetThreshold) {
                                    desire += 0.75F;
                                }
                            } else {
                                desire += 0.5F;
                            }
                        }
                    } else if (!returning && this.flags.hasFlag(ShipwideAIFlags.AIFlags.IN_ATTACK_RUN)) {
                        if (immediateTarget != null) {
                            if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                                if (MathUtils.getDistance(immediateTarget, this.ship) < 500.0F - this.ship.getCollisionRadius()) {
                                    --desire;
                                } else if (!(MathUtils.getDistance(immediateTarget, this.ship) < 1200.0F - this.ship.getCollisionRadius())) {
                                    ++desire;
                                }
                            } else if (Math.abs(angleToImmediateTarget) >= 180.0F - onTargetThreshold) {
                                if (MathUtils.getDistance(immediateTarget, this.ship) < 500.0F - this.ship.getCollisionRadius()) {
                                    ++desire;
                                } else if (MathUtils.getDistance(immediateTarget, this.ship) < 1200.0F - this.ship.getCollisionRadius()) {
                                    if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.POST_ATTACK_RUN)) {
                                        ++desire;
                                    }
                                } else {
                                    --desire;
                                }
                            } else if (MathUtils.getDistance(immediateTarget, this.ship) < 500.0F - this.ship.getCollisionRadius()) {
                                ++desire;
                            } else if (MathUtils.getDistance(immediateTarget, this.ship) < 1200.0F - this.ship.getCollisionRadius()) {
                                if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.POST_ATTACK_RUN)) {
                                    ++desire;
                                } else {
                                    desire += 0.75F;
                                }
                            }
                        } else if (targetSpot != null) {
                            if (Math.abs(angleToTargetSpot) <= onTargetThreshold) {
                                ++desire;
                            }
                        } else {
                            desire += 0.5F;
                        }
                    }

                    boolean immediateTargetInRange = immediateTarget != null && MathUtils.getDistance(immediateTarget, this.ship) < engageRange - this.ship.getCollisionRadius();

                    if (immediateTarget != null && !immediateTargetInRange && (carrier == null || !carrier.isPullBackFighters() || immediateTarget == carrier) && Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                        desire += 0.5F;
                    }

                    if (this.ship.isFighter()) {
                        if (returning && !immediateTargetInRange) {
                            if (immediateTarget != null) {
                                if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                                    desire += 2.0F;
                                }
                            } else if (targetSpot != null) {
                                if (Math.abs(angleToTargetSpot) <= onTargetThreshold) {
                                    desire += 2.0F;
                                }
                            } else {
                                ++desire;
                            }
                        }
                    } else {
                        float desiredRange = 500.0F;
                        if (assignment != null && (assignment.getType() == CombatAssignmentType.ENGAGE || assignment.getType() == CombatAssignmentType.HARASS || assignment.getType() == CombatAssignmentType.INTERCEPT || assignment.getType() == CombatAssignmentType.LIGHT_ESCORT || assignment.getType() == CombatAssignmentType.MEDIUM_ESCORT || assignment.getType() == CombatAssignmentType.HEAVY_ESCORT || assignment.getType() == CombatAssignmentType.STRIKE)) {
                            desiredRange = engageRange;
                        }

                        if (targetSpot != null && MathUtils.getDistance(targetSpot, this.ship.getLocation()) >= desiredRange && !immediateTargetInRange) {
                            if (immediateTarget != null && MathUtils.getDistance(immediateTarget, targetSpot) <= engageRange) {
                                if (Math.abs(angleToTargetSpot) <= onTargetThreshold) {
                                    desire += 0.5F;
                                }
                            } else if (immediateTarget != null) {
                                if (Math.abs(angleToTargetSpot) <= onTargetThreshold) {
                                    desire += 0.25F;
                                }
                            } else if (Math.abs(angleToTargetSpot) <= onTargetThreshold) {
                                desire += 0.75F;
                            }
                        }
                    }

                    if (this.ship.isFighter()) {
                        if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.WANTED_TO_SLOW_DOWN)) {
                            desire -= 0.5F;
                        }
                    } else {
                        if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.TURN_QUICKLY)) {
                            desire += 0.35F;
                        }

                        if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.BACKING_OFF)) {
                            if (immediateTarget != null) {
                                if (Math.abs(angleToImmediateTarget) >= 90.0F) {
                                    desire += 0.75F;
                                }
                            } else {
                                desire += 0.5F;
                            }
                        }

                        if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.DO_NOT_PURSUE)) {
                            if (immediateTarget != null) {
                                if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                                    --desire;
                                }
                            } else {
                                desire -= 0.5F;
                            }
                        }

                        if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.DO_NOT_USE_FLUX)) {
                            desire += 0.35F;
                        }

                        if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP) || this.flags.hasFlag(ShipwideAIFlags.AIFlags.IN_CRITICAL_DPS_DANGER)) {
                            if (immediateTarget != null) {
                                if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                                    --desire;
                                } else if (Math.abs(angleToImmediateTarget) >= 180.0F - onTargetThreshold) {
                                    ++desire;
                                } else {
                                    ++desire;
                                }
                            } else {
                                ++desire;
                            }
                        }

                        if (this.flags.hasFlag(ShipwideAIFlags.AIFlags.HAS_INCOMING_DAMAGE)) {
                            if (immediateTarget != null) {
                                if (Math.abs(angleToImmediateTarget) <= onTargetThreshold) {
                                    desire -= 0.5F;
                                } else if (Math.abs(angleToImmediateTarget) >= 180.0F - onTargetThreshold) {
                                    desire += 0.5F;
                                } else {
                                    desire += 0.75F;
                                }
                            } else {
                                desire += 0.75F;
                            }
                        }

                        if (assignment != null && assignment.getType() == CombatAssignmentType.RETREAT) {
                            float retreatDirection = this.ship.getOwner() == 0 ? 270.0F : 90.0F;
                            if (Math.abs(MathUtils.getShortestRotation(VectorUtils.getFacing(direction), retreatDirection)) <= onTargetThreshold) {
                                ++desire;
                            } else if (Math.abs(MathUtils.getShortestRotation(VectorUtils.getFacing(direction), retreatDirection)) >= 90.0F) {
                                --desire;
                            }
                        }
                    }

                    float range = 600.0F * boostScale;
                    List<ShipAPI> directTargets = getShipsWithinRange(this.ship.getLocation(), range);
                    if (!directTargets.isEmpty() && !this.ship.isFighter()) {
                        Vector2f endpoint = new Vector2f(direction);
                        endpoint.scale(range);
                        Vector2f.add(endpoint, this.ship.getLocation(), endpoint);
                        directTargets.sort(new CollectionUtils.SortEntitiesByDistance(this.ship.getLocation()));

                        for (ShipAPI tmp : directTargets) {
                            if (tmp != this.ship && this.ship.getCollisionClass() != CollisionClass.NONE && !tmp.isFighter() && !tmp.isDrone()) {
                                Vector2f loc = tmp.getLocation();
                                float areaChange = 1.0F;
                                if (tmp.getOwner() == this.ship.getOwner()) {
                                    areaChange *= 1.5F;
                                }

                                if (CollisionUtils.getCollides(this.ship.getLocation(), endpoint, loc, tmp.getCollisionRadius() * 0.5F + this.ship.getCollisionRadius() * 0.75F * areaChange)) {
                                    if (this.ship.isFrigate()) {
                                        if (tmp.isFrigate()) {
                                            --desire;
                                        } else if (tmp.isDestroyer()) {
                                            desire -= 2.0F;
                                        } else if (tmp.isCruiser()) {
                                            desire -= 4.0F;
                                        } else {
                                            desire -= 8.0F;
                                        }
                                    } else if (this.ship.isDestroyer()) {
                                        if (tmp.isFrigate() && !tmp.isHulk()) {
                                            desire -= 2.0F;
                                        } else if (tmp.isDestroyer()) {
                                            --desire;
                                        } else if (tmp.isCruiser()) {
                                            desire -= 2.0F;
                                        } else {
                                            desire -= 4.0F;
                                        }
                                    } else if (this.ship.isCruiser()) {
                                        if (tmp.isFrigate() && !tmp.isHulk()) {
                                            desire -= 4.0F;
                                        } else if (tmp.isDestroyer() && !tmp.isHulk()) {
                                            desire -= 2.0F;
                                        } else if (tmp.isCruiser()) {
                                            --desire;
                                        } else {
                                            desire -= 2.0F;
                                        }
                                    } else if (tmp.isFrigate() && !tmp.isHulk()) {
                                        desire -= 8.0F;
                                    } else if (tmp.isDestroyer() && !tmp.isHulk()) {
                                        desire -= 4.0F;
                                    } else if (tmp.isCruiser() && !tmp.isHulk()) {
                                        desire -= 2.0F;
                                    } else {
                                        --desire;
                                    }
                                }
                            }
                        }
                    }

                    float targetDesire;
                    if (this.system.getMaxAmmo() <= 2) {
                        if (this.system.getAmmo() <= 1) {
                            targetDesire = 1.0F;
                        } else {
                            targetDesire = 0.5F;
                        }
                    } else if (this.system.getMaxAmmo() == 3) {
                        if (this.system.getAmmo() <= 1) {
                            targetDesire = 1.1F;
                        } else if (this.system.getAmmo() == 2) {
                            targetDesire = 0.667F;
                        } else {
                            targetDesire = 0.45F;
                        }
                    } else if (this.system.getMaxAmmo() == 4) {
                        if (this.system.getAmmo() <= 1) {
                            targetDesire = 1.2F;
                        } else if (this.system.getAmmo() == 2) {
                            targetDesire = 0.8F;
                        } else if (this.system.getAmmo() == 3) {
                            targetDesire = 0.533F;
                        } else {
                            targetDesire = 0.4F;
                        }
                    } else if (this.system.getAmmo() <= 1) {
                        targetDesire = 1.4F;
                    } else if (this.system.getAmmo() == 2) {
                        targetDesire = 1.033F;
                    } else if (this.system.getAmmo() == 3) {
                        targetDesire = 0.74F;
                    } else if (this.system.getAmmo() == 4) {
                        targetDesire = 0.52F;
                    } else if (this.system.getAmmo() == 5) {
                        targetDesire = 0.373F;
                    } else {
                        targetDesire = 0.3F;
                    }

                    boolean shouldActivate = desire >= targetDesire;

// 添加冷却时间检查
                    float systemCooldown = system.getCooldownRemaining();
                    if (systemCooldown > 0.2f) {
                        shouldActivate = false;
                    }

// 在关键状态避免使用
                    if (ship.getFluxTracker().isOverloadedOrVenting()) {
                        shouldActivate = false;
                    }

                    if (shouldActivate) {
                        this.ship.useSystem();
                    }
                }

            }
        }
    }

    public static List<ShipAPI> getShipsWithinRange(Vector2f center, float range) {
        List<ShipAPI> ships = new ArrayList<>();
        for (ShipAPI ship : engine.getShips()) {
            if (MathUtils.getDistance(center, ship.getLocation()) <= range) {
                ships.add(ship);
            }
        }
        return ships;
    }

    public Object getSTATUSKEY1() {
        return STATUSKEY1;
    }

    public Object getSTATUSKEY2() {
        return STATUSKEY2;
    }

    private static class BoostDirectionResult {
        Vector2f direction;
        float boostScale;
    }

    private BoostDirectionResult calculateBoostDirection() {
        BoostDirectionResult result = new BoostDirectionResult();
        result.direction = new Vector2f();
        result.boostScale = PhaseboostDrive.BASELINE_MULT.get(ship.getHullSize());

        // 统一处理所有移动方向
        if (ship.getEngineController().isAccelerating()) {
            result.direction.y += 1;
            result.boostScale -= PhaseboostDrive.FORWARD_PENALTY.get(ship.getHullSize());
        } else if (ship.getEngineController().isAcceleratingBackwards() || ship.getEngineController().isDecelerating()) {
            result.direction.y -= 1;
            result.boostScale -= PhaseboostDrive.REVERSE_PENALTY.get(ship.getHullSize());
        }

        if (ship.getEngineController().isStrafingLeft()) {
            result.direction.x -= 1;
            result.boostScale += 1.0F - PhaseboostDrive.BASELINE_MULT.get(ship.getHullSize());
        } else if (ship.getEngineController().isStrafingRight()) {
            result.direction.x += 1;
            result.boostScale += 1.0F - PhaseboostDrive.BASELINE_MULT.get(ship.getHullSize());
        }

        // 默认向前推进
        if (result.direction.lengthSquared() <= 0.001F) {
            result.direction.y = 1;
            result.boostScale -= PhaseboostDrive.FORWARD_PENALTY.get(ship.getHullSize());
        }

        return result;
    }
}