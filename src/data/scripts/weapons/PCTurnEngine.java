/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * @author 49747
 */
public class PCTurnEngine implements EveryFrameWeaponEffectPlugin, EngineController {

    // 常量定义
    private static final float FLAME_MIN = 0.2f;     // 最小火焰长度
    private static final float FLAME_IDLE = 0.4f;    // 怠速火焰长度
    private static final float FLAME_TURN = 0.8f;    // 转向时火焰长度
    private static final float FLAME_MAX = 1.0f;     // 最大火焰长度
    private static final float FLAME_CHANGE_RATE = 2.0f; // 火焰变化速率

    ShipEngineAPI CombinedEngine = null;
    private float enginenowLenght = 0f;
    private float target = 180;
    private float engineDefAngle = 180f;
    private boolean noEngine = false;
    private Side side = Side.MIDDLE;
    private Acc AccelType = Acc.None;
    private float turnrate = 0;

    @Override
    public float getTarget() {
        return target;
    }

    @Override
    public float getDefault() {
        return engineDefAngle;
    }

    @Override
    public Acc getAcc(ShipAPI ship) {
        return AccelType;
    }

    private enum Side {
        LEFT,
        MIDDLE,
        RIGHT
    }

    @Override
    public float getTurnRate() {
        return turnrate;
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused()) {
            return;
        }
        ShipAPI ship = weapon.getShip();
        if (ship == null || !ship.isAlive()) {
            return;
        }

        // 初始化：找到对应的引擎
        if (CombinedEngine == null && !noEngine) {
            for (ShipEngineAPI eng : weapon.getShip().getEngineController().getShipEngines()) {
                if (Misc.getDistance(weapon.getLocation(), eng.getLocation()) < 1f) {
                    CombinedEngine = eng;
                    ship.setCustomData(weapon.getSlot().getId().substring(0, 2) + "Engine", this);
                    engineDefAngle = eng.getEngineSlot().getAngle();

                    // 根据武器槽ID判断引擎位置
                    if (weapon.getSlot().getId().contains("Left")) {
                        side = Side.LEFT;
                    } else if (weapon.getSlot().getId().contains("Right")) {
                        side = Side.RIGHT;
                    }

                    enginenowLenght = FLAME_IDLE;
                    target = weapon.getSlot().getAngle();
                    turnrate = weapon.getTurnRate();
                    break;
                }
            }
            if (CombinedEngine == null) {
                noEngine = true;
            }
        }

        if (CombinedEngine == null || CombinedEngine.isDisabled()) {
            return;
        }

        // 检测角速度变化（用于判断转向启动/停止）

        ShipEngineControllerAPI Controller = ship.getEngineController();
        float needLength = FLAME_IDLE;  // 默认怠速状态

        // 重置加速类型
        AccelType = Acc.None;

        // 判断是否正在进行转向操作
        boolean isTurningLeft = Controller.isTurningLeft();
        boolean isTurningRight = Controller.isTurningRight();

        // 判断是否正在进行横移操作
        boolean isStrafingLeft = Controller.isStrafingLeft();
        boolean isStrafingRight = Controller.isStrafingRight();

        // 仅在转向或横移时增加引擎火焰
        if (isTurningLeft || isTurningRight || isStrafingLeft || isStrafingRight) {

            // 转向逻辑：根据转向方向调整不同位置引擎的火焰
            if (isTurningLeft) {
                if (side == Side.LEFT) {
                    // 左侧引擎：减弱火焰（提供转向阻力）
                    needLength = FLAME_MIN;
                    AccelType = Acc.DeAcc;
                } else if (side == Side.RIGHT) {
                    // 右侧引擎：增强火焰（提供转向动力）
                    needLength = FLAME_MAX;
                    AccelType = Acc.Acc;
                } else {
                    // 中间引擎：中等火焰
                    needLength = FLAME_TURN;
                    AccelType = Acc.Acc;
                }
            }
            else if (isTurningRight) {
                if (side == Side.LEFT) {
                    // 左侧引擎：增强火焰
                    needLength = FLAME_MAX;
                    AccelType = Acc.Acc;
                } else if (side == Side.RIGHT) {
                    // 右侧引擎：减弱火焰
                    needLength = FLAME_MIN;
                    AccelType = Acc.DeAcc;
                } else {
                    // 中间引擎：中等火焰
                    needLength = FLAME_TURN;
                    AccelType = Acc.Acc;
                }
            }

            // 横移逻辑：根据横移方向调整引擎火焰
            if (isStrafingRight) {
                if (side == Side.LEFT) {
                    // 左横移：左侧引擎增强火焰
                    needLength = FLAME_MAX;
                    AccelType = Acc.Acc;
                } else if (side == Side.RIGHT) {
                    // 左横移：右侧引擎减弱火焰
                    needLength = FLAME_MIN;
                    AccelType = Acc.DeAcc;
                } else {
                    // 中间引擎：中等火焰
                    needLength = FLAME_TURN;
                    AccelType = Acc.Acc;
                }
            }
            else if (isStrafingLeft) {
                if (side == Side.LEFT) {
                    // 右横移：左侧引擎减弱火焰
                    needLength = FLAME_MIN;
                    AccelType = Acc.DeAcc;
                } else if (side == Side.RIGHT) {
                    // 右横移：右侧引擎增强火焰
                    needLength = FLAME_MAX;
                    AccelType = Acc.Acc;
                } else {
                    // 中间引擎：中等火焰
                    needLength = FLAME_TURN;
                    AccelType = Acc.Acc;
                }
            }

            // 如果同时进行转向和横移，取火焰长度较大值
        }

        // 线性加速/减速时保持怠速火焰
        else if (Controller.isAccelerating() || Controller.isAcceleratingBackwards() ||
                Controller.isDecelerating()) {
            // 保持默认的怠速火焰
            AccelType = Acc.None;
        }

        // 平滑调整火焰长度
        smoothFlameAdjustment(needLength, amount);

        // 应用火焰长度到引擎
        ship.getEngineController().setFlameLevel(CombinedEngine.getEngineSlot(), enginenowLenght);
    }

    /**
     * 平滑调整火焰长度
     * @param targetLength 目标火焰长度
     * @param amount 帧时间
     */
    private void smoothFlameAdjustment(float targetLength, float amount) {
        float changeThreshold = amount * FLAME_CHANGE_RATE;

        if (Math.abs(enginenowLenght - targetLength) < changeThreshold) {
            enginenowLenght = targetLength;
        } else {
            if (targetLength > enginenowLenght) {
                enginenowLenght += amount;
            } else {
                enginenowLenght -= amount;
            }
        }

        // 确保火焰长度在有效范围内
        enginenowLenght = Math.max(FLAME_MIN, Math.min(FLAME_MAX, enginenowLenght));
    }
}