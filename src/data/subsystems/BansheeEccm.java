package data.subsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.RSModPlugin;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.subsystems.MagicSubsystem;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicUI;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class BansheeEccm extends MagicSubsystem {

    // 常量定义
    private final float BASE_RANGE = 2000f;

    // 波纹特效相关常量
    private final Color WAVE_COLOR = new Color(100, 150, 255, 100); // 淡蓝色，半透明
    private final float WAVE_DURATION = 2.5f; // 波纹持续时间
    private final float WAVE_INTERVAL = 2.5f; // 波纹生成间隔

    // 系统特定变量
    private final IntervalUtil timer = new IntervalUtil(0.1f, 0.1f);
    private final IntervalUtil sparkle = new IntervalUtil(0.1f, 0.1f);
    private final IntervalUtil waveTimer = new IntervalUtil(WAVE_INTERVAL, WAVE_INTERVAL);
    private float distubringRange;
    private float ecmRate;
    private float ecmBounsRange;
    private final List<MissileAPI> locked = new ArrayList<>();
    private final List<MissileAPI> vulnerable = new ArrayList<>();
    private Vector2f systemSlotLocation = null;

    public BansheeEccm(ShipAPI ship) {
        super(ship);

    }

    @Override
    public float getBaseActiveDuration() {
        return 10f; // 系统激活持续10秒
    }

    @Override
    public float getBaseCooldownDuration() {
        return 5f; // 系统冷却1秒
    }

    @Override
    public boolean shouldActivateAI(float amount) {
        if (!canActivate()) {
            return false;
        }
        // AI逻辑：当附近有敌方导弹时激活系统
        if (ship.getFluxTracker().getFluxLevel() > 0.7f) return false; // 通量过高时不激活

        List<MissileAPI> nearbyMissiles = AIUtils.getNearbyEnemyMissiles(ship, BASE_RANGE);
        return !nearbyMissiles.isEmpty();
    }

    @Override
    public String getDisplayText() {
        return "量子脉冲";
    }

    @Override
    public String getBriefText() {
        return "干扰敌方导弹制导系统";
    }

    @Override
    public float getRange() {
        return BASE_RANGE;
    }

    @Override
    public float getFluxCostFlatOnActivation() {
        return 100f; // 激活时消耗100点通量
    }

    @Override
    public float getFluxCostPercentPerSecondWhileActive() {
        return 0.05f; // 激活期间每秒消耗5%基础通量容量
    }

    @Override
    public void advance(float amount, boolean isPaused) {
        if (isPaused || !isActive()) return;

        // 初始化时找到系统武器槽位
        for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
            if (slot.getWeaponType() == WeaponAPI.WeaponType.SYSTEM) continue;
                // 将槽位位置转换为世界坐标
                systemSlotLocation = new Vector2f(slot.getLocation());
                // 考虑舰船旋转和位置
                Vector2f.add(systemSlotLocation, ship.getLocation(), systemSlotLocation);
                break;

        }

        // 更新系统槽位位置（跟随舰船移动）
        updateSystemSlotLocation();

        // 计算ECM加成
        ecmRate = ship.getMutableStats().getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).flatBonus;
        ecmBounsRange = ecmRate * 50.0f;
        if (ecmBounsRange >= 1500) ecmBounsRange = 1500.0F;

        // 计算实际干扰范围
        distubringRange = BASE_RANGE * ship.getMutableStats().getSystemRangeBonus().getBonusMult() * getEffectLevel() + ecmBounsRange;



        // 定时检测导弹
        timer.advance(amount);
        if (timer.intervalElapsed()) {
            List<MissileAPI> missiles = AIUtils.getNearbyEnemyMissiles(ship, distubringRange);
            for (MissileAPI m : missiles) {
                // 忽略对诱饵免疫的导弹
                if (m.getWeaponSpec() != null && m.getWeaponSpec().getAIHints().contains("IGNORES_FLARES"))
                    continue;

                if (!locked.contains(m) && !RSModPlugin.DERECHO_IMMUNE.contains(m.getProjectileSpecId())) {
                    locked.add(m);
                    if (!RSModPlugin.DERECHO_RESIST.contains(m.getProjectileSpecId())) {
                        vulnerable.add(m);
                    }
                }
            }
        }

        if (isActive()) {
            // 生成范围指示波纹
            waveTimer.advance(amount);
            if (waveTimer.intervalElapsed() && isActive()) {
                createRangeWaveEffect();
            }
            // 处理导弹干扰
            lockMissiles(amount);
        }
    }

    /**
     * 更新系统武器槽位的位置（跟随舰船移动和旋转）
     */
    private void updateSystemSlotLocation() {
        // 重置为舰船中心
        systemSlotLocation = new Vector2f(ship.getLocation());

        // 尝试找到系统武器槽位
        for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
            if (slot.getWeaponType() == WeaponAPI.WeaponType.SYSTEM) continue;
                // 将槽位位置转换为世界坐标
                Vector2f slotLocation = new Vector2f(slot.getLocation());

                // 应用舰船旋转
                float angle = ship.getFacing();
                float s = (float) Math.sin(Math.toRadians(angle));
                float c = (float) Math.cos(Math.toRadians(angle));

                // 旋转槽位位置
                float xNew = slotLocation.x * c - slotLocation.y * s;
                float yNew = slotLocation.x * s + slotLocation.y * c;

                // 添加到舰船位置
                systemSlotLocation.x = ship.getLocation().x + xNew;
                systemSlotLocation.y = ship.getLocation().y + yNew;
                break;

        }
    }

    /**
     * 创建范围指示波纹特效
     */
    private void createRangeWaveEffect() {
        if (!MagicRender.screenCheck(0.1f, systemSlotLocation)) return;

        // 创建波纹特效
        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", "banshee_wave"),
                systemSlotLocation,
                new Vector2f(0, 0),
                new Vector2f(10, 10), // 初始大小
                new Vector2f(distubringRange * 2, distubringRange * 2), // 最终大小
                ship.getFacing(), // 角度
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
                systemSlotLocation,
                new Vector2f(0, 0),
                new Vector2f(5, 5), // 初始大小
                new Vector2f(distubringRange * 1.8f, distubringRange * 1.8f), // 最终大小
                ship.getFacing(),
                0,
                new Color(WAVE_COLOR.getRed(), WAVE_COLOR.getGreen(), WAVE_COLOR.getBlue(), 70), // 更透明
                true,
                0.2f, // 更长的淡入时间
                0.2f, // 更短的主要显示时间
                WAVE_DURATION * 0.8f // 更短的持续时间
        );
    }

    private void lockMissiles(float amount) {
        boolean sparkling = false;
        sparkle.advance(amount);
        if (sparkle.intervalElapsed()) {
            sparkling = true;
        }

        if (!locked.isEmpty()) {
            int missileOrder = 0;
            for (Iterator<MissileAPI> iter = locked.iterator(); iter.hasNext(); ) {
                MissileAPI m = iter.next();
                missileOrder += 1;

                if (m.isFading() || m.didDamage() || !Global.getCombatEngine().isEntityInPlay(m)) {
                    iter.remove();
                    if (vulnerable.contains(m)) {
                        vulnerable.remove(m);
                    }
                } else {
                    // 干扰制导导弹
                    if (m.isGuided() && !m.isFizzling()) {
                        m.giveCommand(ShipCommand.ACCELERATE_BACKWARDS);

                        if (missileOrder % 2 == 0)
                            m.giveCommand(ShipCommand.TURN_RIGHT);
                        else
                            m.giveCommand(ShipCommand.TURN_LEFT);
                    }

                    // 特效与额外效果
                    if (sparkling) {
                        // 熄火效果
                        if (Math.random() > 0.95 - ecmRate * 0.01f && vulnerable.contains(m)) {
                            if (m.getEngineController().isFlamedOut()) {
                                m.setArmingTime(m.getFlightTime() + 0.2f);
                            } else {
                                m.flameOut();
                            }
                        }

                        // 闪电特效
                        if (Math.random() > 0.75 && MagicRender.screenCheck(0.1f, m.getLocation())) {
                            int zapFrames = 8;
                            int chooser = new Random().nextInt(zapFrames - 1) + 1;
                            float rand = 0.5f * (float) Math.random() + 0.5f;

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
    public String getExtraInfoText() {
        // 显示ECM加成信息
        String TXT1 = ("quantum1");
        String TXT2 = ("quantum2");
        return TXT1 + TXT2 + (int) ecmBounsRange + "/" + (int) ecmRate + "%";
    }

    @Override
    public Color getHUDColor() {
        return MagicUI.BLUCOLOR;
    }

    @Override
    public void onActivate() {
        // 系统激活时初始化列表
        locked.clear();
        vulnerable.clear();
    }

    @Override
    public void onFinished() {
        // 系统结束时清理列表
        locked.clear();
        vulnerable.clear();
    }


}