package data.scripts.weapons;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

public class Moci_MechLegAnimationController_TR implements EveryFrameWeaponEffectPlugin {
    private static final Map<String, int[]> SEQUENCE = new HashMap<>();
    private boolean initialized = false;

    /**
     * 腿部动画要点：
     * 第4帧作为默认帧，表现标准状态
     * 前进时向前播放到第0帧（收腿状态）
     * 后退时向后播放到最后一帧（展开状态）
     * 固定帧切换间隔为0.12秒
     */
    private static int maxFrames = 1;
    private static int defaultFrame = 4; // 默认帧为第4帧

    // 新增：固定帧切换间隔（0.12秒）
    private static final float FRAME_INTERVAL = 0.12f;
    private float frameTimer = 0;

    static {
        SEQUENCE.put("LEFT", new int[]{0, maxFrames});
        SEQUENCE.put("RIGHT", new int[]{0, maxFrames});
        SEQUENCE.put("FRONT", new int[]{0, defaultFrame}); // 前进时从第4帧播放到第0帧
        SEQUENCE.put("BACK", new int[]{defaultFrame, maxFrames}); // 后退时从第4帧播放到最后一帧
    }

    String currentSequence = null;
    int current = 4; // 默认从第4帧开始

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused() || weapon.getAnimation()==null) {
            return;
        }

        ShipAPI ship = weapon.getShip();
        if (!ship.isAlive() || ship.isHulk()) {
            weapon.getAnimation().pause(); // 确保在死亡时暂停动画
            return;
        }

        // 初始化动画序列(第一次运行时)
        if (!initialized) {
            if (weapon.getAnimation() != null) {
                maxFrames = weapon.getAnimation().getNumFrames() - 1;
                // 确保默认帧不超过总帧数
                defaultFrame = Math.min(4, maxFrames);
            }
            SEQUENCE.put("FRONT", new int[]{0, defaultFrame}); // 前进时从默认帧播放到第0帧
            SEQUENCE.put("BACK", new int[]{defaultFrame, maxFrames}); // 后退时从默认帧播放到最后一帧
            initialized = true;
        }

        // 替换原有的暂停逻辑
        if(currentSequence == null) {
            weapon.getAnimation().pause();
        }

        // 固定时间间隔更新帧
        frameTimer += amount;
        while (frameTimer >= FRAME_INTERVAL) {
            frameTimer -= FRAME_INTERVAL;

            if (currentSequence != null) {
                int[] sq = SEQUENCE.get(currentSequence);

                // 检测引擎状态
                boolean isAccelerating = ship.getEngineController().isAccelerating();
                boolean isDecelerating = ship.getEngineController().isDecelerating();
                boolean isBackwards = ship.getEngineController().isAcceleratingBackwards();

                if ("FRONT".equals(currentSequence)) {
                    // 前进序列：加速时向0帧播放（帧数减少）
                    if (isAccelerating) {
                        current = Math.max(current - 1, sq[0]); // 向0帧播放
                    } else {
                        // 不加速时逐帧回到默认帧，实现平滑过渡
                        if (current < defaultFrame) {
                            current = Math.min(current + 1, defaultFrame);
                        }
                    }
                } else if ("BACK".equals(currentSequence)) {
                    // 后退序列：减速/后退时向最大帧播放（帧数增加）
                    if (isDecelerating || isBackwards) {
                        current = Math.min(current + 1, sq[1]); // 向最大帧播放
                    } else {
                        // 不减速时逐帧回到默认帧，实现平滑过渡
                        if (current > defaultFrame) {
                            current = Math.max(current - 1, defaultFrame);
                        }
                    }
                }

                // 重置条件 - 只有当回到默认帧且不再有相应操作时才重置
                boolean shouldReset = false;
                if ("FRONT".equals(currentSequence)) {
                    // 前进序列：已回到默认帧且不再加速时重置
                    shouldReset = (current == defaultFrame && !isAccelerating);
                } else if ("BACK".equals(currentSequence)) {
                    // 后退序列：已回到默认帧且不再减速时重置
                    shouldReset = (current == defaultFrame && !isDecelerating && !isBackwards);
                }

                if (shouldReset) {
                    currentSequence = null;
                }
            }
            else {
                // 检测前进和后退
                boolean isAccelerating = ship.getEngineController().isAccelerating();
                boolean isDecelerating = ship.getEngineController().isDecelerating();
                boolean isBackwards = ship.getEngineController().isAcceleratingBackwards();

                if (isAccelerating) {
                    currentSequence = "FRONT"; // 前进时收腿
                }
                if (isDecelerating || isBackwards) {
                    currentSequence = "BACK"; // 后退时展开腿
                }

                if(currentSequence != null){
                    weapon.getAnimation().play(); // 开始播放动画
                }
            }
        }

        // 如果没有动画序列，确保在默认帧
        if (currentSequence == null) {
            current = defaultFrame;
        }

        weapon.getAnimation().setFrame(current);
    }
}