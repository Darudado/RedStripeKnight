package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.RSModPlugin;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class BansheeEccmEffect implements EveryFrameWeaponEffectPlugin {
    private final float RANGE = 2000;
    private final IntervalUtil timer = new IntervalUtil(0.1f, 0.1f);
    private final IntervalUtil sparkle = new IntervalUtil(0.1f, 0.1f);
    private final String zapSprite = "zap_0";
    private final int zapFrames = 8;

    private boolean runOnce = false;
    private ShipAPI theShip;
    private float disturbingRange;
    private float ecmRate;
    private float ecmBonusRange;
    private List<MissileAPI> locked = new ArrayList<>(), vulnerable = new ArrayList<>();

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused()) {
            return;
        }

        // 初始化
        if (!runOnce) {
            runOnce = true;
            theShip = weapon.getShip();
            if (theShip == null) return;
            locked.clear();
            vulnerable.clear();
        }

        // 检查舰船是否有效
        if (theShip == null || !theShip.isAlive() || theShip.getOriginalOwner() == -1) {
            return;
        }

        // 只有武器处于激活状态时才处理
        if (weapon.isDisabled()) {
            return;
        }

        try {
            // 计算ECM加成和干扰范围
            ecmRate = theShip.getMutableStats().getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).flatBonus;
            ecmBonusRange = ecmRate * 50.0f;  // 每1% ECM强度增加50单位范围
            if (ecmBonusRange >= 1500) {
                ecmBonusRange = 1500.0F;  // 最大ECM范围加成1500单位
            }

            disturbingRange = RANGE + ecmBonusRange;

            // 定时检测导弹
            timer.advance(amount);
            if (timer.intervalElapsed()) {
                List<MissileAPI> missiles = AIUtils.getNearbyEnemyMissiles(theShip, disturbingRange);
                if (missiles != null) {
                    for (MissileAPI m : missiles) {
                        if (m == null) continue;

                        // 忽略对干扰免疫的导弹
                        if (m.getWeaponSpec() != null && m.getWeaponSpec().getAIHints().contains("IGNORES_FLARES")) {
                            continue;
                        }

                        if (!locked.contains(m) && !RSModPlugin.DERECHO_IMMUNE.contains(m.getProjectileSpecId())) {
                            locked.add(m);
                            if (!RSModPlugin.DERECHO_RESIST.contains(m.getProjectileSpecId())) {
                                vulnerable.add(m);
                            }
                        }
                    }
                }
            }

            // 处理导弹干扰
            lockMissiles(engine, amount);
        } catch (Exception e) {
            // 捕获任何异常，防止游戏崩溃
            Global.getLogger(this.getClass()).error("Error in BansheeEccmEffect: " + e.getMessage(), e);
        }
    }

    private void lockMissiles(CombatEngineAPI engine, float amount) {
        if (engine == null || engine.isPaused()) return;

        boolean sparkling = false;
        sparkle.advance(amount);
        if (sparkle.intervalElapsed()) {
            sparkling = true;
        }

        if (!locked.isEmpty()) {
            int missileOrder = 0;
            for (Iterator<MissileAPI> iter = locked.iterator(); iter.hasNext(); ) {
                MissileAPI m = iter.next();
                if (m == null) {
                    iter.remove();
                    continue;
                }

                missileOrder += 1;

                if (m.isFading() || m.didDamage() || !engine.isEntityInPlay(m)) {
                    iter.remove();
                    if (vulnerable.contains(m)) {
                        vulnerable.remove(m);
                    }
                } else {
                    // 对制导导弹进行干扰
                    if (m.isGuided() && !m.isFizzling()) {
                        try {
                            m.giveCommand(ShipCommand.ACCELERATE_BACKWARDS);

                            if (missileOrder % 2 == 0) {
                                m.giveCommand(ShipCommand.TURN_RIGHT);
                            } else {
                                m.giveCommand(ShipCommand.TURN_LEFT);
                            }
                        } catch (Exception e) {
                            // 忽略命令发送错误
                        }
                    }

                    // 视觉效果和熄火效果
                    if (sparkling) {
                        // 随机使导弹熄火
                        if (Math.random() > 0.95 - ecmRate * 0.01f && vulnerable.contains(m)) {
                            try {
                                if (m.getEngineController().isFlamedOut()) {
                                    m.setArmingTime(m.getFlightTime() + 0.2f);
                                } else {
                                    m.flameOut();
                                }
                            } catch (Exception e) {
                                // 忽略导弹操作错误
                            }
                        }

                        // 生成闪电效果
                        if (Math.random() > 0.75 && MagicRender.screenCheck(0.1f, m.getLocation())) {
                            try {
                                int chooser = new Random().nextInt(zapFrames - 1) + 1;
                                float rand = 0.5f * (float) Math.random() + 0.5f;

                                MagicRender.objectspace(
                                        Global.getSettings().getSprite("fx", zapSprite + chooser),
                                        m,
                                        new Vector2f(),
                                        new Vector2f(),
                                        new Vector2f(48 * rand, 48 * rand),
                                        new Vector2f((float) Math.random() * 20, (float) Math.random() * 20),
                                        (float) Math.random() * 360,
                                        (float) (Math.random() - 0.5f) * 10,
                                        false,
                                        new Color(255, 175, 255),
                                        true,
                                        0,
                                        0.1f + (float) Math.random() * 0.1f,
                                        0.1f,
                                        false
                                );
                            } catch (Exception e) {
                                // 忽略渲染错误
                            }
                        }
                    }
                }
            }
        }
    }
}