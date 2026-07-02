package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class RS_MaECMpulseStats extends BaseShipSystemScript {

    private final IntervalUtil timer = new IntervalUtil(0.1f, 0.1f);
    private final IntervalUtil sparkle = new IntervalUtil(0.1f, 0.1f);
    private final IntervalUtil waveTimer = new IntervalUtil(2.5f, 2.5f); // 波纹生成间隔
    private boolean runOnce = false;
    private ShipAPI theShip;
    private ShipSystemAPI theSystem;
    private float distubringRange;
    private float ecmRate;
    private float ecmBounsRange;
    private List<MissileAPI> locked = new ArrayList<>(), vulnerable = new ArrayList<>();

    // 波纹特效相关常量
    private final Color WAVE_COLOR = new Color(100, 150, 255, 100); // 淡蓝色，半透明

    @Override
    public void apply(MutableShipStatsAPI stats, String id, ShipSystemStatsScript.State state, float effectLevel) {

        CombatEngineAPI engine = Global.getCombatEngine();

        if (!runOnce) {
            runOnce = true;
            if (stats.getEntity() instanceof ShipAPI) {
                theShip = (ShipAPI) stats.getEntity();
            }
            theSystem = theShip.getSystem();
            locked.clear();
            vulnerable.clear();
        }

        if (engine.isPaused()) {
            return;
        }

        float amount = 0.0F;
        if (!engine.isPaused()) {
            amount = engine.getElapsedInLastFrame();
        }

        if (theSystem.isOn()) {
            timer.advance(amount);
            waveTimer.advance(amount);

            ecmRate=stats.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).flatBonus;
            ecmBounsRange=ecmRate*50.0f;  // every 1% ecm strength gain 50su and 1% disturbing strength
            if(ecmBounsRange >= 1500)       //max ecmrange bonus 1500su
                ecmBounsRange=1500.0F;

            float RANGE = 2000;
            distubringRange= RANGE * theShip.getMutableStats().getSystemRangeBonus().getBonusMult() * theSystem.getEffectLevel()+ecmBounsRange;

            // 生成范围指示波纹
            if (waveTimer.intervalElapsed() && theSystem.isOn()) {
                createRangeWaveEffect();
            }

            if (timer.intervalElapsed()) {
                List<MissileAPI> missiles = AIUtils.getNearbyEnemyMissiles(theShip,distubringRange);
                for (MissileAPI m : missiles) {
                    //leave missiles imune to flares alone
                    if (m.getWeaponSpec() != null && m.getWeaponSpec().getAIHints().contains("IGNORES_FLARES"))
                        continue;

                    // 移除对DAModPlugin的依赖，直接检查导弹属性
                    if (!locked.contains(m) && !isMissileImmune(m)) {
                        locked.add(m);
                        if (!isMissileResistant(m)) {
                            vulnerable.add(m);
                        }
                    }
                }
            }
            lockMissiles(engine, amount);
        }

    }

    /**
     * 检查导弹是否免疫干扰（替代DAModPlugin.DERECHO_IMMUNE）
     */
    private boolean isMissileImmune(MissileAPI missile) {
        // 这里可以根据导弹的特定属性来判断是否免疫
        // 例如：检查导弹的specId或其它特征
        String specId = missile.getProjectileSpecId();
        if (specId == null) return false;

        // 示例：某些特定导弹免疫干扰
        return specId.contains("immune") || specId.contains("torpedo") || specId.contains("rocket");
    }

    /**
     * 检查导弹是否抵抗干扰（替代DAModPlugin.DERECHO_RESIST）
     */
    private boolean isMissileResistant(MissileAPI missile) {
        // 这里可以根据导弹的特定属性来判断是否抵抗
        String specId = missile.getProjectileSpecId();
        if (specId == null) return false;

        // 示例：某些特定导弹有抵抗效果
        return specId.contains("resistant") || specId.contains("armored");
    }

    /**
     * 创建范围指示波纹特效
     */
    private void createRangeWaveEffect() {
        Vector2f shipLocation = theShip.getLocation();
        if (!MagicRender.screenCheck(0.1f, shipLocation)) return;

        // 创建波纹特效 - 使用与BansheeEccm相同的sprite
        // 波纹持续时间
        float WAVE_DURATION = 2.5f;
        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "banshee_wave"),
                shipLocation,
                new Vector2f(0, 0),
                new Vector2f(10, 10), // 初始大小
                new Vector2f(distubringRange * 2, distubringRange * 2), // 最终大小
                theShip.getFacing(), // 角度
                0, // 角速度
                WAVE_COLOR,
                true,
                0.1f, // 淡入时间
                0.3f, // 主要显示时间
                WAVE_DURATION // 总持续时间
        );

        // 可选：添加第二个波纹，稍微延迟一点
        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "banshee_wave"),
                shipLocation,
                new Vector2f(0, 0),
                new Vector2f(5, 5), // 初始大小
                new Vector2f(distubringRange * 1.8f, distubringRange * 1.8f), // 最终大小
                theShip.getFacing(),
                0,
                new Color(WAVE_COLOR.getRed(), WAVE_COLOR.getGreen(), WAVE_COLOR.getBlue(), 70), // 更透明
                true,
                0.2f, // 更长的淡入时间
                0.2f, // 更短的主要显示时间
                WAVE_DURATION * 0.8f // 更短的持续时间
        );
    }

    private void lockMissiles(com.fs.starfarer.api.combat.CombatEngineAPI engine, float amount) {

        boolean sparkling = false;
        sparkle.advance(amount);
        if (sparkle.intervalElapsed()) {
            sparkling = true;
        }

        if (!locked.isEmpty()) {
            int missileOrder = 0;
            for (Iterator<MissileAPI> iter = locked.iterator(); iter.hasNext(); ) {
                MissileAPI m = iter.next();
                missileOrder+=1;
                if (m.isFading() || m.didDamage() || !engine.isEntityInPlay(m)) {
                    iter.remove();
                    vulnerable.remove(m);
                } else {
                    if(m.isGuided()&&!m.isFizzling()) //the "torpedo" or "rocket" should be immune from ecm right?
                    {
                        m.giveCommand(ShipCommand.ACCELERATE_BACKWARDS);

                        if(missileOrder%2==0)
                            m.giveCommand(ShipCommand.TURN_RIGHT);
                        else
                            m.giveCommand(ShipCommand.TURN_LEFT);
                    }
                    //tart`s original derecho effect ↓
                    //  m.setAngularVelocity(0);
                    if (sparkling) {
                        //flameout
                        if (Math.random() > 0.95-ecmRate*0.01f && vulnerable.contains(m)) {
                            if (m.getEngineController().isFlamedOut()) {
                                m.setArmingTime(m.getFlightTime() + 0.2f);
                            } else {
                                m.flameOut();
                            }
                        }

                        //zaps - 使用与BansheeEccm相同的闪电特效
                        if (Math.random() > 0.75 && MagicRender.screenCheck(0.1f, m.getLocation())) {
                            int zapFrames = 8;
                            int chooser = new Random().nextInt(zapFrames - 1) + 1;
                            float rand = 0.5f * (float) Math.random() + 0.5f;

                            //    private final float rangeMult=0;
                            String zapSprite = "zap_0";
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
                        }
                    }
                }
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
    }

    @Override
    public ShipSystemStatsScript.StatusData getStatusData(int index, ShipSystemStatsScript.State state, float effectLevel) {
        if (index == 0) {
            return new ShipSystemStatsScript.StatusData("conduct electronic countermeasures"+(int)ecmBounsRange+"/"+(int)ecmRate+"%" , false);
        }
        return null;
    }
}