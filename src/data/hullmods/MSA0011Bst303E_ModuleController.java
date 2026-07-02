package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import java.util.HashMap;
import java.util.Map;

public class MSA0011Bst303E_ModuleController extends BaseHullMod {

    // 动画参数
    private static final float ROTATION_SPEED = 12.0f;
    private static final float MAX_TURN_ARC = 13.0f;
    private static final float TURN_ARC1 = 6.0f;
    private static final float SMOOTHING_FACTOR = 0.15f; // 新增平滑因子


    // 存储每艘船的机翼状态
    private final Map<ShipAPI, WingState> shipStates = new HashMap<>();

    // 机翼舰船ID配置
    private static final String LEFT_WING_SHIP_ID = "_subEngineLeft";
    private static final String RIGHT_WING_SHIP_ID = "_subEngineRight";

    // 机翼状态类
    private static class WingState {
        float leftOffset = 0f;
        float rightOffset = 0f;
        ShipAPI leftWingShip = null;
        ShipAPI rightWingShip = null;
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused() || !ship.isAlive()) {
            return;
        }

        // 初始化并获取船体状态
        WingState state = shipStates.get(ship);
        if (state == null) {
            state = new WingState();
            shipStates.put(ship, state);
            findWingShips(ship, state);
        }

        // 如果机翼舰船丢失，尝试重新查找
        if (state.leftWingShip == null || state.rightWingShip == null) {
            findWingShips(ship, state);
        }

        // 计算目标偏移
        float leftTargetOffset = 0f;
        float rightTargetOffset = 0f;

        // 1. 优先检测转向/侧移组合动作
        ShipEngineControllerAPI engines = ship.getEngineController();
        if (engines.isTurningRight() || engines.isStrafingLeft()) {
            // 右转/右移：左翼向前(+)，右翼向后(-)
            leftTargetOffset = MAX_TURN_ARC;
            rightTargetOffset = TURN_ARC1;
        } else if (engines.isTurningLeft() || engines.isStrafingRight()) {
            // 左转/左移：左翼向后(-)，右翼向前(+)
            leftTargetOffset = -TURN_ARC1;
            rightTargetOffset = -MAX_TURN_ARC;
        }
        // 2. 检测加速/减速动作
        else {
            if (engines.isAccelerating() && !engines.isAcceleratingBackwards()) {
                // 加速：左翼向前5°，右翼向后5°
                leftTargetOffset = TURN_ARC1;
                rightTargetOffset = -TURN_ARC1;
            } else if (engines.isDecelerating() || engines.isAcceleratingBackwards()) {
                // 减速：左翼向后10°，右翼向前10°
                leftTargetOffset = -MAX_TURN_ARC;
                rightTargetOffset = MAX_TURN_ARC;
            }
        }

        // 更新左右机翼
        if (state.leftWingShip != null && state.leftWingShip.isAlive()) {
            state.leftOffset = updateWingOffset(state.leftOffset, leftTargetOffset, amount);
            applyWingRotation(state.leftWingShip, ship.getFacing() + state.leftOffset);
        }
        if (state.rightWingShip != null && state.rightWingShip.isAlive()) {
            state.rightOffset = updateWingOffset(state.rightOffset, rightTargetOffset, amount);
            applyWingRotation(state.rightWingShip, ship.getFacing() + state.rightOffset);
        }
    }

    // 平滑更新机翼偏移量
    private float updateWingOffset(float currentOffset, float targetOffset, float amount) {
        // 规范化偏移角度到[-180, 180]范围
        while (currentOffset > 180f) currentOffset -= 360f;
        while (currentOffset < -180f) currentOffset += 360f;

        // 计算当前差值
        float diff = targetOffset - currentOffset;
        // 规范化差值到[-180,180]范围，确保走最短路径
        while (diff > 180f) diff -= 360f;
        while (diff < -180f) diff += 360f;

        // 平方缓动函数：在接近目标时减速
        float step = diff * SMOOTHING_FACTOR;

        // 确保步长不超过最大旋转速度
        float maxStep = ROTATION_SPEED * amount;
        if (step > maxStep) step = maxStep;
        else if (step < -maxStep) step = -maxStep;

        return currentOffset + step;
    }

    // 查找关联的机翼舰船
    private void findWingShips(ShipAPI mothership, WingState state) {
        for (ShipAPI ship : Global.getCombatEngine().getShips()) {
            if (!ship.isAlive()) continue;

            if (ship.getParentStation() == mothership) {
                String hullId = ship.getHullSpec().getHullId();

                if (hullId.contains(LEFT_WING_SHIP_ID)) {
                    state.leftWingShip = ship;
                } else if (hullId.contains(RIGHT_WING_SHIP_ID)) {
                    state.rightWingShip = ship;
                }
            }
        }
    }

    private void applyWingRotation(ShipAPI wingShip, float targetAngle) {
        wingShip.setFacing(targetAngle);
        wingShip.setAngularVelocity(0f); // 重置角速度确保平滑旋转
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (!shipStates.containsKey(ship)) {
            WingState state = new WingState();
            shipStates.put(ship, state);
            findWingShips(ship, state);
        }
    }

    public void onShipDeath(ShipAPI ship, DamageAPI damage) {
        shipStates.remove(ship);
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "wing module";
        if (index == 1) return "Dynamic adjustment";
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship.getHullSpec().getHullId().contains("vower");
    }
}