package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;
import java.util.List;

public class DisintegratorBeanEffect implements BeamEffectPlugin {

    // 光束效果的核心数据类
    private static class BeamEffectData {
        // 目标相关
        public ShipAPI target = null;
        public Vector2f offset = null;

        // 计时器和状态
        public int ticks = 0;
        public IntervalUtil interval = new IntervalUtil(0.8f, 1.0f);
        public FaderUtil fader = new FaderUtil(1.0f, 0.5f, 0.5f);

        // 粒子效果
        public List<ParticleData> particles = new ArrayList<>();

        // 光束上次命中的位置（用于检测是否更换目标）
        public Vector2f lastHitPos = null;

        public BeamEffectData() {
            interval.forceIntervalElapsed(); // 立即准备第一次伤害
        }
    }

    // 粒子数据类
    private static class ParticleData {
        public float elapsed = 0.0f;
        public float maxDur;
        public float fadeDur;
        public float angle;
        public float baseSize;
        public float scale;
        public Vector2f offset = new Vector2f();
        public FaderUtil fader;
        public SpriteAPI sprite;
        public Color color = new Color(255, 100, 100, 255);

        public ParticleData(float maxDur, float fadeDur, float baseSize) {
            this.maxDur = maxDur;
            this.fadeDur = fadeDur;
            this.baseSize = baseSize;
            this.fader = new FaderUtil(0.0f, maxDur - fadeDur, fadeDur);
            this.sprite = Global.getSettings().getSprite("misc", "fx_particles1");
            this.angle = (float)Math.random() * 360.0f;
            this.scale = 0.5f + (float)Math.random() * 0.5f;
        }

        public void advance(float amount) {
            elapsed += amount;
            fader.advance(amount);
            angle += amount * 90.0f; // 旋转粒子
        }
    }

    // 每个光束实例的效果数据
    private final Map<BeamAPI, BeamEffectData> beamData = new HashMap<>();

    // 配置参数
    private static final int NUM_TICKS = 11;
    private static final float TOTAL_DAMAGE = 1000.0f;
    private static final int PARTICLES_PER_TICK = 3;

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        // 如果引擎暂停，不处理
        if (engine.isPaused()) {
            return;
        }

        // 获取或创建这个光束的效果数据
        BeamEffectData data = beamData.get(beam);
        if (data == null) {
            data = new BeamEffectData();
            beamData.put(beam, data);
        }

        // 检查光束是否命中目标
        CombatEntityAPI target = beam.getDamageTarget();
        boolean isHitting = beam.getBrightness() >= 1.0f && target != null;

        // 处理目标变更
        if (isHitting && target instanceof ShipAPI shipTarget) {
            Vector2f hitPos = beam.getTo();

            // 如果目标改变或首次命中，重新初始化效果
            if (data.target != shipTarget || data.lastHitPos == null ||
                    Vector2f.sub(hitPos, data.lastHitPos, new Vector2f()).length() > 50f) {

                data.target = shipTarget;
                data.lastHitPos = new Vector2f(hitPos);

                // 计算相对于目标的位置偏移
                Vector2f offset = Vector2f.sub(hitPos, shipTarget.getLocation(), new Vector2f());
                offset = Misc.rotateAroundOrigin(offset, -shipTarget.getFacing());
                data.offset = offset;

                // 重置计时器
                data.ticks = 0;
                data.particles.clear();
                data.interval = new IntervalUtil(0.8f, 1.0f);
                data.interval.forceIntervalElapsed();
                data.fader.fadeIn();
            }

            // 更新效果位置（跟随目标）
            if (data.target != null && data.offset != null && data.target.isAlive()) {
                Vector2f loc = new Vector2f(data.offset);
                loc = Misc.rotateAroundOrigin(loc, data.target.getFacing());
                Vector2f.add(data.target.getLocation(), loc, loc);
                data.lastHitPos.set(loc);
            }

            // 处理粒子
            updateParticles(data, amount);

            // 播放音效
            playSound(data);

            // 处理伤害间隔
            data.interval.advance(amount);
            if (data.interval.intervalElapsed() && data.ticks < NUM_TICKS) {
                dealDamage(data, beam, engine);
                data.ticks++;
            }

            // 如果完成所有tick或目标死亡，开始淡出
            if (data.ticks >= NUM_TICKS || (data.target != null && !data.target.isAlive())) {
                data.fader.fadeOut();
            }

            // 更新淡出器
            data.fader.advance(amount);

            // 渲染粒子（在渲染层进行）
            renderParticles(data);

        } else {
            // 光束没有命中目标，开始淡出
            if (data.fader.getBrightness() > 0) {
                data.fader.fadeOut();
                data.fader.advance(amount);

                // 更新和渲染剩余的粒子
                updateParticles(data, amount);
                renderParticles(data);

                // 播放淡出的音效
                playSound(data);
            } else {
                // 完全淡出后清理数据
                beamData.remove(beam);
            }
        }
    }

    private void updateParticles(BeamEffectData data, float amount) {
        if (data.target == null || !data.target.isAlive()) {
            return;
        }

        List<ParticleData> toRemove = new ArrayList<>();

        // 更新现有粒子
        for (ParticleData p : data.particles) {
            p.advance(amount);
            if (p.elapsed >= p.maxDur) {
                toRemove.add(p);
            }
        }

        // 移除过期粒子
        data.particles.removeAll(toRemove);

        // 添加新粒子（在dealDamage中调用）
    }

    private void renderParticles(BeamEffectData data) {
        if (data.particles.isEmpty() || data.lastHitPos == null) {
            return;
        }

        // 渲染每个粒子
        for (ParticleData p : data.particles) {
            if (p.sprite == null) continue;

            // 计算粒子位置
            Vector2f particlePos = new Vector2f(data.lastHitPos);
            Vector2f.add(particlePos, p.offset, particlePos);

            // 设置粒子属性
            float size = p.baseSize * p.scale;
            p.sprite.setAngle(p.angle);
            p.sprite.setSize(size, size);
            p.sprite.setAlphaMult(p.fader.getBrightness());
            p.sprite.setColor(p.color);

            // 使用加色混合 (Additive Blending)
            p.sprite.setBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

            // 渲染粒子
            p.sprite.renderAtCenter(particlePos.x, particlePos.y);
        }
    }

    private void playSound(BeamEffectData data) {
        if (data.target == null || data.lastHitPos == null) {
            return;
        }

        float volume = data.fader.getBrightness();
        if (volume > 0) {
            Global.getSoundPlayer().playLoop(
                    "disintegrator_loop",
                    data.target,
                    1.0f,
                    volume,
                    data.lastHitPos,
                    data.target.getVelocity()
            );
        }
    }

    private void dealDamage(BeamEffectData data, BeamAPI beam, CombatEngineAPI engine) {
        if (data.target == null || !data.target.isAlive() || data.lastHitPos == null) {
            return;
        }

        // 生成粒子
        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            addParticle(data);
        }

        // 获取目标装甲网格
        ArmorGridAPI grid = data.target.getArmorGrid();
        Vector2f point = new Vector2f(data.lastHitPos);
        int[] cell = grid.getCellAtLocation(point);

        if (cell == null) {
            return;
        }

        // 计算伤害
        float[][] gridArray = grid.getGrid();
        int gridWidth = gridArray.length;
        int gridHeight = gridArray[0].length;

        // 获取伤害来源（光束的武器所属飞船）
        ShipAPI source = beam.getSource();
        float damageTypeMult = getDamageTypeMult(source, data.target);
        float damagePerTick = TOTAL_DAMAGE / NUM_TICKS;
        float damageDealt = 0.0f;
        float hullDamage = 0.0f;

        // 菱形伤害区域（5x5减去四个角）
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                // 跳过四个角
                if ((i == 2 || i == -2) && (j == 2 || j == -2)) {
                    continue;
                }

                int cx = cell[0] + i;
                int cy = cell[1] + j;

                // 检查是否在网格内
                if (cx >= 0 && cx < gridWidth && cy >= 0 && cy < gridHeight) {
                    // 伤害倍率
                    float damMult = 0.033333335f;
                    if (i == 0 && j == 0) {
                        damMult = 0.06666667f; // 中心点
                    } else if (i >= -1 && i <= 1 && j >= -1 && j <= 1) {
                        damMult = 0.06666667f; // 3x3内部区域
                    }

                    // 计算伤害
                    float armorInCell = grid.getArmorValue(cx, cy);
                    float damage = damagePerTick * damMult * damageTypeMult;

                    // 检查是否能伤害船体（原代码默认为false）
                    if (damage > armorInCell && canDamageHull()) {
                        hullDamage += damage - armorInCell;
                    }

                    // 应用装甲伤害
                    damage = Math.min(damage, armorInCell);
                    if (damage > 0.0f) {
                        grid.setArmorValue(cx, cy, Math.max(0.0f, armorInCell - damage));
                        damageDealt += damage;
                    }
                }
            }
        }

        // 显示伤害数字
        if (damageDealt > 0.0f) {
            if (Misc.shouldShowDamageFloaty(source, data.target)) {
                engine.addFloatingDamageText(
                        point,
                        damageDealt,
                        0.0f,
                        Misc.FLOATY_ARMOR_DAMAGE_COLOR,
                        data.target,
                        source
                );
            }

            data.target.syncWithArmorGridState();
        }

        // 处理船体伤害
        if (hullDamage > 1.0f) {
            float currentHP = data.target.getHitpoints();
            if (currentHP > 0) {
                data.target.setHitpoints(currentHP - hullDamage);

                // 如果船体生命值归零
                if (data.target.getHitpoints() <= 0.0f && !data.target.isHulk()) {
                    data.target.setSpawnDebris(false);
                    engine.applyDamage(
                            data.target,
                            point,
                            100.0f,
                            DamageType.ENERGY,
                            0.0f,
                            true,
                            false,
                            source,
                            false
                    );
                }

                // 显示船体伤害数字
                if (Misc.shouldShowDamageFloaty(source, data.target)) {
                    Vector2f hullTextPos = new Vector2f(point);
                    hullTextPos.y += 20.0f;
                    engine.addFloatingDamageText(
                            hullTextPos,
                            hullDamage,
                            0.0f,
                            Misc.FLOATY_HULL_DAMAGE_COLOR,
                            data.target,
                            source
                    );
                }
            }
        }
    }

    private void addParticle(BeamEffectData data) {
        ParticleData p = new ParticleData(3.0f + (float)Math.random() * 2.0f, 2.0f, 30.0f);

        // 随机偏移
        float angle = (float)Math.random() * 360.0f;
        float distance = 5.0f + (float)Math.random() * 15.0f;
        p.offset.x = (float)Math.cos(Math.toRadians(angle)) * distance;
        p.offset.y = (float)Math.sin(Math.toRadians(angle)) * distance;

        data.particles.add(p);
    }

    private float getDamageTypeMult(ShipAPI source, ShipAPI target) {
        if (source == null || target == null) {
            return 1.0f;
        }

        float damageTypeMult = target.getMutableStats().getArmorDamageTakenMult().getModifiedValue();

        // 根据目标船型调整伤害
        switch (target.getHullSize()) {
            case FIGHTER:
                damageTypeMult *= source.getMutableStats().getDamageToFighters().getModifiedValue();
                break;
            case FRIGATE:
                damageTypeMult *= source.getMutableStats().getDamageToFrigates().getModifiedValue();
                break;
            case DESTROYER:
                damageTypeMult *= source.getMutableStats().getDamageToDestroyers().getModifiedValue();
                break;
            case CRUISER:
                damageTypeMult *= source.getMutableStats().getDamageToCruisers().getModifiedValue();
                break;
            case CAPITAL_SHIP:
                damageTypeMult *= source.getMutableStats().getDamageToCapital().getModifiedValue();
                break;
        }

        return damageTypeMult;
    }

    private boolean canDamageHull() {
        return false; // 与原版保持一致，默认只伤害装甲
    }

    // 清理资源（可选）
    public void cleanup() {
        beamData.clear();
    }
}