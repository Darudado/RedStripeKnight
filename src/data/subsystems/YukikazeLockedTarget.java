package data.subsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.subsystems.MagicSubsystem;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class YukikazeLockedTarget extends MagicSubsystem implements DamageDealtModifier {
    private ShipAPI ship;
    private ShipSystemAPI system;

    private boolean runOnce = false, activated = false, playerShip = false;

    private final List<ShipAPI> TARGETS = new ArrayList<>(); // 改为实例变量
    private int select = -1;
    private final int MAX_RANGE = 2500;
    private final int MAX_TARGETS = 5; // 新增：最大目标数量
    private float beep = 0;

    // 伤害增益相关变量
    private final float DAMAGE_BONUS = 0.25f; // 25%额外伤害

    // 用于跟踪已应用伤害增益的目标
    private final List<ShipAPI> targetsWithDamageBonus = new ArrayList<>();

    // 伤害监听器注册状态
    private boolean listenerRegistered = false;

    public YukikazeLockedTarget(ShipAPI ship, ShipSystemAPI system) {
        super(ship);
        this.ship = ship;
        this.system = system;
        this.playerShip = ship == Global.getCombatEngine().getPlayerShip();

        // 注册伤害监听器
        registerDamageListener();
    }

    public void advance(float amount, boolean isPaused) {
        if (isPaused) return;

        CombatEngineAPI engine = Global.getCombatEngine();

        // 确保伤害监听器已注册
        if (!listenerRegistered) {
            registerDamageListener();
        }

        // 系统激活时选择目标
        if (system.isActive()) {
            if (!activated) {
                activated = true;
                TARGETS.clear(); // 激活时清空目标列表
                select = -1;
                targetsWithDamageBonus.clear();
            }
            pickTargets(engine, system.getEffectLevel(), amount);

            // 应用伤害增益效果
            applyDamageBonus(engine, amount);
        } else {
            // 系统未激活时移除伤害增益
            if (activated) {
                removeDamageBonus();
                activated = false;
            }
        }

        // 清理无效目标（被摧毁或超出范围）
        cleanupTargets(engine);
    }

    // 注册伤害监听器
    private void registerDamageListener() {
        if (ship != null && !listenerRegistered) {
            if (!ship.hasListenerOfClass(YukikazeLockedTarget.class)) {
                ship.addListener(this);
                listenerRegistered = true;
            }
        }
    }

    // 注销伤害监听器
    private void unregisterDamageListener() {
        if (ship != null && listenerRegistered) {
            ship.removeListener(this);
            listenerRegistered = false;
        }
    }

    // 应用伤害增益效果
    private void applyDamageBonus(CombatEngineAPI engine, float amount) {
        // 为每个锁定目标应用伤害增益
        for (ShipAPI target : TARGETS) {
            if (!target.isAlive()) continue;

            // 如果目标还没有伤害增益，添加它
            if (!targetsWithDamageBonus.contains(target)) {
                targetsWithDamageBonus.add(target);
            }

            // 显示伤害增益视觉特效
            if (engine.getTotalElapsedTime(false) % 0.5f < amount) {
                showDamageBonusEffect(target);
            }
        }

        // 清理已不在锁定列表但仍有效果的目标
        List<ShipAPI> toRemove = new ArrayList<>();
        for (ShipAPI target : targetsWithDamageBonus) {
            if (!TARGETS.contains(target) || !target.isAlive()) {
                toRemove.add(target);
            }
        }
        targetsWithDamageBonus.removeAll(toRemove);
    }

    // 显示伤害增益视觉特效
    private void showDamageBonusEffect(ShipAPI target) {
        // 在目标周围显示伤害增益特效
        float size = target.getCollisionRadius() * 2f;
        Color effectColor = new Color(1f, 0.5f, 0f, 0.3f); // 橙色半透明

        MagicRender.objectspace(
                Global.getSettings().getSprite("fx", "RING"),
                target,
                new Vector2f(),
                new Vector2f(),
                new Vector2f(size, size),
                new Vector2f(0, 0),
                0,
                0,
                true,
                effectColor,
                true,
                0, 0,
                0, 0, 0,
                0.1f, 0.1f, 0.5f,
                false,
                CombatEngineLayers.ABOVE_SHIPS_LAYER
        );
    }

    // 移除所有伤害增益
    private void removeDamageBonus() {
        targetsWithDamageBonus.clear();
    }

    // 实现 DamageDealtModifier 接口
    @Override
    public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
        // 检查伤害是否来自本舰并且目标是锁定的
        if (target instanceof ShipAPI && TARGETS.contains(target) && system.isActive()) {
            // 增加伤害
            damage.setDamage(damage.getDamage() * (1f + DAMAGE_BONUS));

            // 显示伤害增益特效
            showDamageEffect(target, damage.getDamage() * DAMAGE_BONUS);

            return "yukikaze_damage_bonus";
        }

        return null;
    }

    // 显示伤害增益特效
    private void showDamageEffect(CombatEntityAPI target, float bonusDamage) {
        // 在目标位置显示伤害数字
        Vector2f loc = new Vector2f(target.getLocation());
        loc.y += target.getCollisionRadius();

        CombatEngineAPI engine = Global.getCombatEngine();
        engine.addFloatingText(loc, "+" + (int)bonusDamage, 20f,
                new Color(1f, 0.5f, 0f, 1f), target, 1f, 1f);

        // 添加伤害特效
        engine.spawnExplosion(loc, new Vector2f(), new Color(1f, 0.5f, 0f, 0.7f), 50f, 0.2f);
    }

    public ShipAPI getTarget() {
        if (TARGETS.isEmpty()) {
            return null;
        }

        // 循环选择目标
        select = (select + 1) % TARGETS.size();
        return TARGETS.get(select);
    }

    private void pickTargets(CombatEngineAPI engine, float level, float amount) {
        beep -= amount;

        // 如果已达最大目标数，不再添加新目标
        if (TARGETS.size() >= MAX_TARGETS) {
            return;
        }

        // 获取附近敌人
        List<ShipAPI> nearby = AIUtils.getNearbyEnemies(ship, MAX_RANGE * level);

        // 优先选择符合条件的敌人
        List<ShipAPI> prioritizedTargets = prioritizeTargets(nearby);

        for (ShipAPI target : prioritizedTargets) {
            // 如果已达最大目标数，停止添加
            if (TARGETS.size() >= MAX_TARGETS) {
                break;
            }

            // 只添加不在列表中的有效目标
            if (!TARGETS.contains(target) && target.isAlive()) {
                TARGETS.add(target);

                // 播放音效
                if (playerShip && beep <= 0) {
                    beep = 0.075f;
                    Global.getSoundPlayer().playSound("diableavionics_virtuousTarget_beep", 1, 1,
                            ship.getLocation(), ship.getVelocity());
                }

                // 显示目标指示器
                if (engine.isUIShowingHUD()) {
                    renderTargetIndicator(target);
                }
            }
        }
    }

    // 优先选择符合条件的敌人
    private List<ShipAPI> prioritizeTargets(List<ShipAPI> nearbyEnemies) {
        List<ShipAPI> prioritized = new ArrayList<>(nearbyEnemies);

        // 使用自定义比较器对目标进行排序
        Collections.sort(prioritized, new Comparator<ShipAPI>() {
            @Override
            public int compare(ShipAPI ship1, ShipAPI ship2) {
                // 计算每个目标的优先级分数
                float score1 = calculateTargetPriority(ship1);
                float score2 = calculateTargetPriority(ship2);

                // 分数高的排在前面（优先级更高）
                return Float.compare(score2, score1);
            }
        });

        return prioritized;
    }

    // 计算目标优先级分数
    private float calculateTargetPriority(ShipAPI target) {
        float priority = 0f;

        // 1. 血量百分比低的目标优先级更高（最高+50分）
        float hullPercent = target.getHullLevel();
        priority += (1f - hullPercent) * 50f;

        // 2. 辐能百分比高的目标优先级更高（最高+30分）
        float fluxPercent = target.getFluxLevel();
        priority += fluxPercent * 30f;

        // 3. 移动速度慢的目标优先级更高（最高+20分）
        float speedFactor = 1f - Math.min(1f, target.getVelocity().length() / target.getMaxSpeed());
        priority += speedFactor * 20f;

        return priority;
    }

    private void renderTargetIndicator(ShipAPI target) {
        // 添加目标指示菱形
        MagicRender.objectspace(
                Global.getSettings().getSprite("fx", "DIAMOND"),
                target,
                new Vector2f(),
                new Vector2f(),
                new Vector2f(64, 64),
                new Vector2f(0, 0),
                45,
                0,
                false,
                Color.orange,
                false,
                0, 0,
                2, 1, 0.2f,
                0.5f, 4 - system.getChargeUpDur() * system.getEffectLevel(), 0.5f,
                true,
                CombatEngineLayers.BELOW_INDICATORS_LAYER
        );

        // 如果目标在屏幕内，添加旋转指示器
        if (MagicRender.screenCheck(0.2f, target.getLocation())) {
            MagicRender.objectspace(
                    Global.getSettings().getSprite("fx", "DIAMOND"),
                    target,
                    new Vector2f(),
                    new Vector2f(),
                    new Vector2f(192, 192),
                    new Vector2f(-256, -256),
                    45,
                    360,
                    false,
                    Color.orange,
                    false,
                    0, 0,
                    0, 0, 0,
                    0.35f, 0.05f, 0.1f,
                    true,
                    CombatEngineLayers.BELOW_INDICATORS_LAYER
            );
        }
    }

    private void cleanupTargets(CombatEngineAPI engine) {
        // 移除已摧毁或超出范围的目标
        TARGETS.removeIf(target ->
                !target.isAlive() ||
                        Vector2f.sub(ship.getLocation(), target.getLocation(), null).length() > MAX_RANGE
        );

        // 确保选择索引有效
        if (select >= TARGETS.size()) {
            select = TARGETS.isEmpty() ? -1 : 0;
        }
    }

    public int getLockedTargetCount() {
        return TARGETS.size();
    }

    public boolean isTargetLocked(ShipAPI target) {
        return TARGETS.contains(target);
    }

    public float getDamageBonus() {
        return DAMAGE_BONUS;
    }

    @Override
    public float getBaseActiveDuration() {
        return 5f; // 5秒激活时间
    }

    @Override
    public float getBaseCooldownDuration() {
        return 10f; // 10秒冷却时间
    }

    @Override
    public boolean shouldActivateAI(float amount) {
        // 简单AI逻辑：当有敌人在范围内时激活
        return !AIUtils.getNearbyEnemies(ship, MAX_RANGE).isEmpty();
    }

    @Override
    public String getDisplayText() {
        return "Target:" + TARGETS.size() + "/" + MAX_TARGETS + " (+" + (int)(DAMAGE_BONUS * 100) + "%harm)";
    }

}