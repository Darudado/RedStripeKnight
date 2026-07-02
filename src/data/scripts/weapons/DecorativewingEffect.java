package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;

public class DecorativewingEffect implements EveryFrameWeaponEffectPlugin {
    private static final float ROTATION_SPEED = 12.0f;
    private static final float MAX_TURN_ARC = 13.0f;
    private static final float TURN_ARC1 = 6.0f;
    private static final float SMOOTHING_FACTOR = 0.15f; // 新增平滑因子


    private static final String LEFT_WING_ID = "decleftwing";
    private static final String RIGHT_WING_ID = "decrightwing";

    private enum WingSide { LEFT, RIGHT, NONE }

    private WingSide wingSide = WingSide.NONE;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused()) return;

        ShipAPI ship = weapon.getShip();
        if (ship == null || !ship.isAlive()) return;

        String slotId = weapon.getSlot().getId();
        // 延迟初始化机翼位置（只执行一次）
        if (wingSide == WingSide.NONE) {
            if (slotId.contains(LEFT_WING_ID)) {
                wingSide = WingSide.LEFT;
            } else if (slotId.contains(RIGHT_WING_ID)) {
                wingSide = WingSide.RIGHT;
            } else {
                wingSide = WingSide.NONE;
                return;
            }
        }

        // 获取舰船当前朝向
        float shipFacing = ship.getFacing();
        float targetOffset = getTargetOffset(ship);
        float currentOffset = weapon.getCurrAngle() - shipFacing;
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
        

        // 平滑过渡到目标偏移角度
        if (currentOffset < targetOffset) {
            currentOffset = Math.min(currentOffset + ROTATION_SPEED * amount, targetOffset);
        } else if (currentOffset > targetOffset) {
            currentOffset = Math.max(currentOffset - ROTATION_SPEED * amount, targetOffset);
        }

        // 应用最终角度（舰船朝向 + 偏移量）
        float finalAngle = shipFacing + currentOffset;

        // 规范化角度到[0,360)区间
        finalAngle = normalizeAngle(finalAngle);
        weapon.setCurrAngle(finalAngle);
    }

    private float getTargetOffset(ShipAPI ship) {
        float targetOffset = 0f;

        // 根据舰船运动状态确定目标偏移量

        if (ship.getEngineController().isTurningRight() || ship.getEngineController().isStrafingLeft()) {
            // 右转/左平移：左翼前倾，右翼后摆
            targetOffset = (wingSide == WingSide.LEFT) ? MAX_TURN_ARC : TURN_ARC1;
        } else if (ship.getEngineController().isTurningLeft() || ship.getEngineController().isStrafingRight()) {
            // 左转/右平移：左翼后摆，右翼前倾
            targetOffset = (wingSide == WingSide.LEFT) ? -TURN_ARC1 : -MAX_TURN_ARC;
        } else if (ship.getEngineController().isDecelerating() || ship.getEngineController().isAcceleratingBackwards()) {
            // 减速/倒车：左翼上抬，右翼下压
            targetOffset = (wingSide == WingSide.LEFT) ? TURN_ARC1 : -TURN_ARC1;
        } else if (ship.getEngineController().isAccelerating()) {
            // 加速：左翼下压，右翼上抬
            targetOffset = (wingSide == WingSide.LEFT) ? -MAX_TURN_ARC : MAX_TURN_ARC;
        }
        return targetOffset;
    }

    // 辅助方法：规范化角度值
    private float normalizeAngle(float angle) {
        angle %= 360f;
        if (angle < 0) angle += 360f;
        return angle;
    }
}