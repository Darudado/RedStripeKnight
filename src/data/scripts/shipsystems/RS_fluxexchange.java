package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class RS_fluxexchange extends BaseShipSystemScript {
    public static final String TRANSFER_DATA_KEY_SUFFIX = "_fluxtransfer_targetData";
    public static final float TRANSFER_PERCENT_PER_SECONDS = 5F;
    private static final float SYSTEM_RANGE = 2000.0F; // 范围扩大到2000
    private static final Color JITTER_COLOR = new Color(100, 50, 255, 255);
    private static final Color ARC_FRINGE = new Color(150, 100, 255, 200); // 电弧边缘颜色
    private static final Color ARC_CORE = new Color(200, 150, 255, 150);   // 电弧核心颜色

    // 新增内部类用于存储目标数据
    private static class TransferTargetData {
        ShipAPI target;
        ShipAPI ship;

        TransferTargetData(ShipAPI ship) {
            this.ship = ship;
        }
    }

    public static ShipAPI getCurrentTarget(ShipAPI ship) {
        String key = ship.getId() + "_fluxtransfer_targetData";
        if (Global.getCombatEngine().getCustomData().containsKey(key)) {
            TransferTargetData data = (TransferTargetData)Global.getCombatEngine().getCustomData().get(key);
            return data.target != null && data.target.isAlive() ? data.target : null;
        } else {
            return null;
        }
    }

    public void apply(MutableShipStatsAPI stats, String id, ShipSystemStatsScript.State state, float effectLevel) {
        if (stats.getEntity() instanceof ShipAPI ship) {
            String key = ship.getId() + "_fluxtransfer_targetData";
            TransferTargetData data;
            if (!Global.getCombatEngine().getCustomData().containsKey(key)) {
                data = new TransferTargetData(ship);
                Global.getCombatEngine().getCustomData().put(key, data);
            } else {
                data = (TransferTargetData)Global.getCombatEngine().getCustomData().get(key);
            }

            if (state != State.IDLE && state != State.COOLDOWN) {
                // 获取2000范围内的所有友军（不包括自己）
                List<ShipAPI> allies = CombatUtils.getShipsWithinRange(ship.getLocation(), SYSTEM_RANGE);
                allies.removeIf(s -> s.getOwner() != ship.getOwner() || s == ship || s.isHulk() || !s.isAlive() || s.getFluxTracker().isOverloaded());

                // 计算本帧应转移的总量
                float amount = Global.getCombatEngine().getElapsedInLastFrame();
                float transferAmount = ship.getFluxTracker().getMaxFlux() * 0.01F * TRANSFER_PERCENT_PER_SECONDS * amount;

                float totalTransferred = 0f;

                if (!allies.isEmpty()) {
                    // 平均从每个友军转移的量
                    float perAllyTransfer = transferAmount / allies.size();

                    // 从每个友军转移辐能
                    for (ShipAPI ally : allies) {
                        FluxTrackerAPI allyFlux = ally.getFluxTracker();
                        float currFlux = allyFlux.getCurrFlux();
                        float toTransfer = Math.min(perAllyTransfer, currFlux);
                        if (toTransfer > 0) {
                            allyFlux.decreaseFlux(toTransfer);
                            totalTransferred += toTransfer;

                            // 友军到本舰的粒子效果
                            for (int i = 0; i < (int)(3 * effectLevel); ++i) {
                                Vector2f point = MathUtils.getRandomPointInCircle(ally.getLocation(), ally.getCollisionRadius() * 0.6F);
                                Vector2f toShip = VectorUtils.getDirectionalVector(point, ship.getLocation());
                                toShip.normalise();
                                float duration = MathUtils.getRandomNumberInRange(0.25F, 0.5F);
                                float distance = MathUtils.getDistance(point, ship.getLocation());
                                float speed = distance / duration;
                                Vector2f vel = new Vector2f(toShip.x * speed, toShip.y * speed);
                                float size = MathUtils.getRandomNumberInRange(20.0F, 80.0F);
                                Global.getCombatEngine().addSmoothParticle(point, vel, size, 1.0F, duration, JITTER_COLOR);
                            }

                            // 友军到本舰的电弧效果 (每0.2秒生成一次)
                            if (Global.getCombatEngine().getTotalElapsedTime(false) % 0.2f < amount) {
                                Global.getCombatEngine().spawnEmpArcVisual(
                                        MathUtils.getRandomPointInCircle(ally.getLocation(), ally.getCollisionRadius() * 0.5f),
                                        ally,
                                        MathUtils.getRandomPointInCircle(ship.getLocation(), ship.getCollisionRadius() * 0.5f),
                                        ship,
                                        4f, // 电弧厚度
                                        ARC_FRINGE, // 边缘颜色
                                        ARC_CORE  // 核心颜色
                                );
                            }
                        }
                    }
                }

                // 如果有辐能转移
                if (totalTransferred > 0) {
                    // 计算要转移给敌人的量（总量的1/4）
                    float toEnemy = totalTransferred * 0.25f;
                    float toSelf = totalTransferred - toEnemy; // 自身保留3/4

                    // 增加自身辐能（软辐能）
                    ship.getFluxTracker().increaseFlux(toSelf, false);

                    // 获取目标敌人（使用原有的索敌逻辑）
                    ShipAPI enemyTarget = getCurrentTarget(ship);
                    if (enemyTarget == null || !enemyTarget.isAlive() ||
                            Misc.getDistance(ship.getLocation(), enemyTarget.getLocation()) > SYSTEM_RANGE) {
                        // 如果没有有效目标，尝试寻找新目标
                        enemyTarget = findTarget(ship);
                        data.target = enemyTarget; // 更新存储的目标
                    }

                    // 转移给敌人
                    if (enemyTarget != null && enemyTarget.isAlive() &&
                            Misc.getDistance(ship.getLocation(), enemyTarget.getLocation()) <= SYSTEM_RANGE) {
                        enemyTarget.getFluxTracker().increaseFlux(toEnemy, true); // 以硬辐能形式增加

                        // 本舰到敌人的粒子效果
                        for (int i = 0; i < (int)(5 * effectLevel); ++i) {
                            Vector2f point = MathUtils.getRandomPointInCircle(ship.getLocation(), ship.getCollisionRadius() * 0.6F);
                            Vector2f toEnemyVec = VectorUtils.getDirectionalVector(point, enemyTarget.getLocation());
                            toEnemyVec.normalise();
                            float duration = MathUtils.getRandomNumberInRange(0.25F, 0.5F);
                            float distance = MathUtils.getDistance(point, enemyTarget.getLocation());
                            float speed = distance / duration;
                            Vector2f vel = new Vector2f(toEnemyVec.x * speed, toEnemyVec.y * speed);
                            float size = MathUtils.getRandomNumberInRange(20.0F, 80.0F);
                            Global.getCombatEngine().addSmoothParticle(point, vel, size, 1.0F, duration, JITTER_COLOR);
                        }

                        // 本舰到敌人的电弧效果 (每0.2秒生成一次)
                        if (Global.getCombatEngine().getTotalElapsedTime(false) % 0.2f < amount) {
                            Global.getCombatEngine().spawnEmpArcVisual(
                                    MathUtils.getRandomPointInCircle(ship.getLocation(), ship.getCollisionRadius() * 0.5f),
                                    ship,
                                    MathUtils.getRandomPointInCircle(enemyTarget.getLocation(), enemyTarget.getCollisionRadius() * 0.5f),
                                    enemyTarget,
                                    5f, // 电弧厚度
                                    ARC_FRINGE, // 边缘颜色
                                    ARC_CORE  // 核心颜色
                            );
                        }
                    } else {
                        // 没有有效敌人目标，将25%的辐能也加给自身
                        ship.getFluxTracker().increaseFlux(toEnemy, false);
                    }

                    // 本舰的抖动效果
                    ship.setJitterUnder(this, JITTER_COLOR, effectLevel, Math.round(effectLevel * 20.0F), ship.getCollisionRadius() * effectLevel);
                }
            } else {
                // 在非激活状态更新目标
                if (data != null) {
                    data.target = this.findTarget(ship);
                }
            }
        }
    }

    public ShipAPI findTarget(ShipAPI ship) {
        ShipAPI target = null;
        if (ship.getShipTarget() != null && ship.getShipTarget().getHullSize() != HullSize.FIGHTER) {
            target = ship.getShipTarget();
            float dist = Misc.getDistance(ship.getLocation(), target.getLocation());
            if (!(dist > SYSTEM_RANGE)) {
                return target;
            }

            target = null;
        }

        if (ship.getMouseTarget() != null) {
            List<ShipAPI> ships = CombatUtils.getShipsWithinRange(ship.getLocation(), SYSTEM_RANGE);
            float closestDistance = Float.MAX_VALUE;

            for(ShipAPI other : ships) {
                if (other.getHullSize() != HullSize.FIGHTER && other.isAlive() && !other.isShuttlePod() &&
                        !ship.isPhased() && ship.getOwner() != other.getOwner() && other.getOwner() != 100) {

                    float dist = MathUtils.getDistance(ship.getLocation(), other.getLocation());
                    float distSort = MathUtils.getDistance(ship.getMouseTarget(), other.getLocation());
                    if (!(dist > SYSTEM_RANGE) && distSort < closestDistance) {
                        target = other;
                        closestDistance = distSort;
                    }
                }
            }
        }

        return target;
    }

    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (system.isOutOfAmmo()) {
            return null;
        } else if (system.getState() != ShipSystemAPI.SystemState.IDLE) {
            return null;
        } else {
            // 检查2000范围内是否有可用友军
            List<ShipAPI> allies = CombatUtils.getShipsWithinRange(ship.getLocation(), SYSTEM_RANGE);
            allies.removeIf(s -> s.getOwner() != ship.getOwner() || s == ship || s.isHulk() || !s.isAlive() || s.getFluxTracker().isOverloaded());

            if (!allies.isEmpty()) {
                // 直接返回硬编码字符串
                return "READY";
            } else {
                // 直接返回硬编码字符串
                return "NO TARGETS";
            }
        }
    }

    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        if (system.isActive()) {
            return true;
        } else {
            // 检查2000范围内是否有可用友军
            List<ShipAPI> allies = CombatUtils.getShipsWithinRange(ship.getLocation(), SYSTEM_RANGE);
            allies.removeIf(s -> s.getOwner() != ship.getOwner() || s == ship || s.isHulk() || !s.isAlive() || s.getFluxTracker().isOverloaded());
            return !allies.isEmpty();
        }
    }
}