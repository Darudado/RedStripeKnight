package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.scripts.utils.MathPersonal;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.dark.shaders.distortion.WaveDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicLensFlare;
import particleengine.Emitter;
import particleengine.Particles;

import java.awt.Color;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static data.scripts.RSModPlugin.particleEngineEnabled;

public class PhaseCrossing extends BaseShipSystemScript {
    // === 状态变量 ===
    private WaveDistortion wave = null;
    public static Color COLOR_1;
    public static Color COLOR_2 ;
    private static final Color EXPLODE_COLOR = new Color(195, 50, 50, 75);
    public static float EXPLOSION_RADIUS = 450f;

    private Vector2f recordedLocation = new Vector2f();
    private boolean hasJumped = false;
    private boolean Once = false;
    private EveryFrameCombatPlugin defensePlugin = null;

    // 新增：Polariphase 残影控制
    private PolariphaseAfterimage afterimage = null;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI the_ship = (ShipAPI) stats.getEntity();
        Vector2f ship_loc = the_ship.getLocation();
        CombatEngineAPI engine = Global.getCombatEngine();

        // 驱动残影效果
        if (engine != null && afterimage != null) {
            afterimage.advance(engine.getElapsedInLastFrame());
            if (afterimage.shouldBeRemoved()) {
                afterimage = null;
            }
        }

        if(!(the_ship.getOverloadColor() ==null)){
            COLOR_1 = the_ship.getOverloadColor();
        }else{
            COLOR_1 = new Color(194, 49, 49, 175);
        }

        if(!(the_ship.getExplosionFlashColorOverride() ==null)){
            COLOR_2 = the_ship.getExplosionFlashColorOverride();
        }else{
            COLOR_2 = new Color(173, 14, 14, 255);
        }

        if (engine != null) {
            MagicLensFlare.createSharpFlare(
                    engine, the_ship, ship_loc,
                    9.0F,
                    the_ship.getCollisionRadius() * 3.5F,
                    the_ship.getFacing() + 90.0F,
                    COLOR_1, COLOR_2
            );
            the_ship.setExtraAlphaMult(1.0F - effectLevel);

            if (state == State.IN) {
                if (this.wave == null) {
                    this.wave = new WaveDistortion(ship_loc, new Vector2f(0, 0));
                    this.wave.setSize(the_ship.getCollisionRadius() * 2.0F);
                    this.wave.setIntensity(15.0F);
                    this.wave.setArc(0.0F, 360.0F);
                    this.wave.flip(true);
                    DistortionShader.addDistortion(this.wave);
                } else {
                    float intensity = (float) (Math.sqrt(effectLevel) * 60.0F);
                    this.wave.setLocation(ship_loc);
                    this.wave.setSize(the_ship.getCollisionRadius() - effectLevel * 50.0F);
                    this.wave.setIntensity(intensity + 15.0F);
                }

                // Polariphase：开始记录路径
                if (the_ship.getVariant().hasHullMod("PolariphaseDrive") && afterimage == null) {
                    afterimage = new PolariphaseAfterimage(the_ship);
                    afterimage.startRecord();
                }
            }

            if (state == State.OUT && this.wave != null) {
                this.wave.fadeOutSize(0.5F);
                this.wave = null;
            }
        }

        if (state == State.OUT && !hasJumped) {
            hasJumped = true;
            ShipAPI ship = (ShipAPI) stats.getEntity();

            if (ship.getVariant().hasHullMod("WeaponOverLoad")) {
                if (!this.Once) {
                    this.recordedLocation = new Vector2f(stats.getEntity().getLocation());
                    this.Once = true;
                }
                spawnPhaseExitVisuals(ship, engine);
            } else if (ship.getVariant().hasHullMod("PhaseDefenseUnit")) {
                if (!this.Once) {
                    this.recordedLocation = new Vector2f(stats.getEntity().getLocation());
                    this.Once = true;
                }

                if (engine == null) return;
                if (defensePlugin == null) {
                    defensePlugin = new PhaseDefenseBuffPlugin(ship);
                    engine.addPlugin(defensePlugin);
                } else {
                    engine.removePlugin(defensePlugin);
                    MutableShipStatsAPI cleanStats = ship.getMutableStats();
                    cleanStats.getShieldDamageTakenMult().unmodify("phase_defense_buff");
                    cleanStats.getArmorDamageTakenMult().unmodify("phase_defense_buff");
                    cleanStats.getHullDamageTakenMult().unmodify("phase_defense_buff");
                    cleanStats.getEmpDamageTakenMult().unmodify("phase_defense_buff");
                    defensePlugin = new PhaseDefenseBuffPlugin(ship);
                    engine.addPlugin(defensePlugin);
                }

            } else if (ship.getVariant().hasHullMod("PolariphaseDrive")) {
                // 结束记录，开始残影回放
                if (afterimage != null) {
                    afterimage.endRecord();
                }
                // 不再手动设置 Once 和 recordedLocation，爆炸由残影结束时触发
            } else {
                if (!this.Once) {
                    this.recordedLocation = new Vector2f(stats.getEntity().getLocation());
                    this.Once = true;
                }
            }
        }

        if (state == State.IN) {
            hasJumped = false;
        }
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        CombatEngineAPI engine = Global.getCombatEngine();

        // 安全移除防御插件
        if (defensePlugin != null) {
            engine.removePlugin(defensePlugin);
            MutableShipStatsAPI cleanStats = ship.getMutableStats();
            cleanStats.getShieldDamageTakenMult().unmodify("phase_defense_buff");
            cleanStats.getArmorDamageTakenMult().unmodify("phase_defense_buff");
            cleanStats.getHullDamageTakenMult().unmodify("phase_defense_buff");
            cleanStats.getEmpDamageTakenMult().unmodify("phase_defense_buff");
            defensePlugin = null;
        }

        if (ship.getVariant().hasHullMod("PolariphaseDrive")) {
            // 强制结束残影并播放爆炸
            if (afterimage != null) {
                afterimage.forceFinish();
                afterimage = null;
            }
            // 移除原来的爆炸代码，由残影系统负责
            if (this.Once) {
                this.Once = false; // 重置标记
            }
            // 清除可能的副本位置
            ship.setCopyLocation(null, 0f, 0f);
        }
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        return index == 0 ?
                new StatusData(
                        getShipSystemString("FM_CrossBorderInfo"),
                        false
                ) : null;
    }

    public static String getShipSystemString(String id) {
        return Global.getSettings().getString("cr_shipsystems", id);
    }

    private void spawnPhaseExitVisuals(ShipAPI ship, CombatEngineAPI engine) {
        // ... 保持原样不变 ...
        Vector2f location = ship.getLocation();
        float explosionRadius = EXPLOSION_RADIUS * (1f + ship.getFluxLevel() * 0.5f);

        Color baseColor = Misc.scaleColor(ship.getHullSpec().getHyperspaceJitterColor(), 0.8f);
        Color coreColor = colorBlend(
                new Color(175, 15, 15, 215),
                new Color(255, 60, 60, 255),
                Math.min(1f, ship.getFluxLevel() * 1.5f)
        );
        Color waveColor = colorBlend(
                new Color(185, 17, 17, 150),
                new Color(255, 80, 80, 200),
                0.7f
        );
        Color fringeColor = colorBlend(
                new Color(175, 15, 15, 175),
                new Color(255, 100, 100, 255),
                0.5f
        );

        for (int i = 0; i < 3; i++) {
            float sizeMult = 0.5f + i * 0.5f;
            float duration = 0.3f + i * 0.2f;
            engine.addHitParticle(
                    location,
                    new Vector2f(),
                    explosionRadius * 2.5f * sizeMult,
                    2.5f,
                    duration,
                    colorBlend(coreColor, Color.WHITE, i * 0.3f)
            );
        }

        for (int wave = 0; wave < 2; wave++) {
            float waveRadius = explosionRadius * (1.5f + wave * 0.5f);
            engine.spawnExplosion(
                    MathUtils.getRandomPointInCircle(location, ship.getCollisionRadius() * 0.3f),
                    ship.getVelocity(),
                    colorBlend(baseColor, waveColor, wave * 0.5f),
                    waveRadius,
                    0.6f + wave * 0.2f
            );
        }

        if (particleEngineEnabled) {
            spawnEnhancedParticleEffects(location);
        }

        int shockwavePoints = 36;
        for (int i = 0; i < shockwavePoints; i++) {
            float angle = i * (360f / shockwavePoints);
            Vector2f point = MathUtils.getPointOnCircumference(location, explosionRadius * 1.2f, angle);
            Vector2f velocity = Vector2f.sub(point, location, null);
            velocity.normalise();
            velocity.scale(300f + ship.getFluxLevel() * 200f);
            for (int j = 0; j < 2; j++) {
                Vector2f offset = MathUtils.getRandomPointInCircle(new Vector2f(), 20f);
                engine.addHitParticle(
                        Vector2f.add(point, offset, null),
                        velocity,
                        explosionRadius * 0.8f,
                        0.9f,
                        0.8f,
                        colorBlend(waveColor, Color.WHITE, j * 0.3f)
                );
            }
        }

        RippleDistortion ripple = new RippleDistortion(location, new Vector2f());
        ripple.setSize(explosionRadius * 2f);
        ripple.setIntensity(200f + ship.getFluxLevel() * 100f);
        ripple.setFrameRate(180f);
        ripple.fadeInSize(0.3f);
        ripple.fadeInIntensity(0.2f);
        ripple.setLifetime(1.0f);
        ripple.fadeOutIntensity(0.5f);
        DistortionShader.addDistortion(ripple);

        int arcCount = 16 + (int) (ship.getFluxLevel() * 8);
        for (int i = 0; i < arcCount; i++) {
            Vector2f startPoint = MathUtils.getPointOnCircumference(
                    location,
                    MathUtils.getRandomNumberInRange(ship.getCollisionRadius() * 0.5f, explosionRadius * 0.3f),
                    MathUtils.getRandomNumberInRange(0, 360)
            );
            Vector2f endPoint = MathUtils.getPointOnCircumference(
                    location,
                    explosionRadius * 2.5f,
                    MathUtils.getRandomNumberInRange(0, 360)
            );
            for (int branch = 0; branch < 2; branch++) {
                Vector2f midPoint = MathUtils.getRandomPointOnLine(startPoint, endPoint);
                engine.spawnEmpArcVisual(
                        startPoint, null,
                        branch == 0 ? midPoint : endPoint, null,
                        20f + ship.getFluxLevel() * 10f,
                        fringeColor,
                        Color.WHITE
                );
            }
        }

        int particleCount = 80 + (int) (ship.getFluxLevel() * 40);
        for (int i = 0; i < particleCount; i++) {
            float angle = MathUtils.getRandomNumberInRange(0, 360);
            float speed = MathUtils.getRandomNumberInRange(100f, 400f) * (1f + ship.getFluxLevel());
            Vector2f velocity = new Vector2f(
                    (float) Math.cos(Math.toRadians(angle)) * speed,
                    (float) Math.sin(Math.toRadians(angle)) * speed
            );
            engine.addSmoothParticle(
                    location,
                    velocity,
                    MathUtils.getRandomNumberInRange(6f, 18f),
                    1.2f,
                    1.0f,
                    colorBlend(coreColor, Color.WHITE, MathUtils.getRandomNumberInRange(0f, 0.5f))
            );
        }
    }

    private void spawnEnhancedParticleEffects(Vector2f location) {
        // ... 保持原样不变 ...
        Emitter ringEmitter = Particles.initialize(location, "graphics/fx/smoke32.png");
        ringEmitter.setLayer(CombatEngineLayers.ABOVE_PARTICLES);
        ringEmitter.setSyncSize(true);
        ringEmitter.life(0.8f, 1.2f);
        ringEmitter.fadeTime(0f, 0.1f, 0.3f, 0.6f);
        ringEmitter.circleOffset(EXPLOSION_RADIUS * 0.8f, EXPLOSION_RADIUS * 1.2f);

        Pair<Float, Float> radVelAcc = MathPersonal.getRateAndAcceleration(0f, 800f, 600f, 1f);
        ringEmitter.radialVelocity(radVelAcc.one * 0.8f, radVelAcc.one * 1.2f);
        ringEmitter.radialAcceleration(radVelAcc.two * 0.8f, radVelAcc.two * 1.2f);

        ringEmitter.facing(0f, 360f);
        ringEmitter.size(150f, 200f);
        ringEmitter.growthRate(50f, 80f);
        ringEmitter.growthAcceleration(-10f, -15f);
        ringEmitter.turnRate(-60f, 60f);
        ringEmitter.color(COLOR_1);
        ringEmitter.randomHSVA(20f, 1.5f, 0f, 0f);
        Particles.burst(ringEmitter, 60);

        Emitter coreEmitter = Particles.initialize(location, "graphics/fx/explosion_ring0.png");
        coreEmitter.setLayer(CombatEngineLayers.BELOW_SHIPS_LAYER);
        coreEmitter.life(0.4f, 0.7f);
        coreEmitter.fadeTime(0f, 0.05f, 0.2f, 0.4f);
        coreEmitter.radialVelocity(0f, 100f);
        coreEmitter.radialAcceleration(-200f, -300f);
        coreEmitter.size(300f, 400f);
        coreEmitter.growthRate(-400f, -600f);
        coreEmitter.color(COLOR_2);
        Particles.burst(coreEmitter, 3);
    }

    private Color colorBlend(Color a, Color b, float blendLevel) {
        if (blendLevel <= 0f) return a;
        if (blendLevel >= 1f) return b;
        float invBlend = 1f - blendLevel;
        return new Color(
                Math.min(255, Math.max(0, (int)(a.getRed() * invBlend + b.getRed() * blendLevel))),
                Math.min(255, Math.max(0, (int)(a.getGreen() * invBlend + b.getGreen() * blendLevel))),
                Math.min(255, Math.max(0, (int)(a.getBlue() * invBlend + b.getBlue() * blendLevel))),
                Math.min(255, Math.max(0, (int)(a.getAlpha() * invBlend + b.getAlpha() * blendLevel)))
        );
    }

    // ---------- PhaseDefense 插件 ----------
    private static class PhaseDefenseBuffPlugin implements EveryFrameCombatPlugin {
        // ... 保持不变 ...
        private final ShipAPI ship;
        private float elapsed;
        private static final float DURATION = 3f;
        private boolean applied = false;
        private final Object STATUSKEY = new Object();

        public PhaseDefenseBuffPlugin(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (ship == null || !ship.isAlive()) {
                removeSelf();
                return;
            }
            if (!applied) {
                MutableShipStatsAPI stats = ship.getMutableStats();
                stats.getShieldDamageTakenMult().modifyMult("phase_defense_buff", 0.25f);
                stats.getArmorDamageTakenMult().modifyMult("phase_defense_buff", 0.25f);
                stats.getHullDamageTakenMult().modifyMult("phase_defense_buff", 0.25f);
                stats.getEmpDamageTakenMult().modifyMult("phase_defense_buff", 0.25f);
                ship.setJitterUnder("phase_defense_buff", ship.getVentCoreColor(), 1, 15, 0.0F, 15.0F);
                applied = true;
            }
            Global.getCombatEngine().maintainStatusForPlayerShip(
                    this.STATUSKEY,
                    ship.getSystem().getSpecAPI().getIconSpriteName(),
                    ship.getSystem().getDisplayName(),
                    "Greatly reduced damage taken",
                    false
            );
            elapsed += amount;
            if (elapsed >= DURATION) {
                removeSelf();
            }
        }

        private void removeSelf() {
            MutableShipStatsAPI stats = ship.getMutableStats();
            stats.getShieldDamageTakenMult().unmodify("phase_defense_buff");
            stats.getArmorDamageTakenMult().unmodify("phase_defense_buff");
            stats.getHullDamageTakenMult().unmodify("phase_defense_buff");
            stats.getEmpDamageTakenMult().unmodify("phase_defense_buff");
            Global.getCombatEngine().removePlugin(this);
        }

        @Override public void init(CombatEngineAPI engine) {}
        @Override public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {}
        @Override public void renderInWorldCoords(ViewportAPI viewport) {}
        @Override public void renderInUICoords(ViewportAPI viewport) {}
    }

    // ---------- Polariphase 残影系统 ----------
    private class PolariphaseAfterimage {
        private static final float ALPHA = 0.3f;                 // 残影透明度
        private static final float TRAVEL_TIME = 0.4f;           // 残影移动总时长（秒）
        private static Color LIGHTNING_COLOR;
        private static final float DAMAGE = 50f;
        private static final float EMP_DAMAGE = 50f;

        private final ShipAPI ship;


        // 起终点
        private Vector2f startPos, endPos;
        private float startFacing, endFacing;

        private boolean isPlaying = false;   // 是否正在回放
        private float elapsed = 0f;
        private boolean finished = false;    // 是否已完成爆炸

        public PolariphaseAfterimage(ShipAPI ship) {
            this.ship = ship;
        }

        /** 技能开始时调用，记录起点 */
        public void startRecord() {
            startPos = new Vector2f(ship.getLocation());
            startFacing = ship.getFacing();
        }

        /** 技能结束时调用，记录终点并开始回放 */
        public void endRecord() {
            endPos = new Vector2f(ship.getLocation());
            endFacing = ship.getFacing();
            isPlaying = true;
            elapsed = 0f;
        }

        public boolean shouldBeRemoved() {
            return finished;
        }

        public void advance(float amount) {
            if (finished || !isPlaying) return;

            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) return;

            elapsed += amount;

            if(!(ship.getOverloadColor() ==null)){
                LIGHTNING_COLOR = ship.getOverloadColor();
            }else{
                LIGHTNING_COLOR = new Color(175, 15, 15, 215);
            }

            // 移动结束，播放爆炸效果
            if (elapsed >= TRAVEL_TIME) {
                ship.setCopyLocation(null, 0f, 0f);   // 清除残影
                isPlaying = false;
                triggerFinishEffect();
                return;
            }

            // 当前进度
            float t = elapsed / TRAVEL_TIME;
            // 插值位置和朝向
            Vector2f curPos = Misc.interpolateVector(startPos, endPos, t);
            float curFacing = MathUtils.clampAngle(startFacing + MathUtils.getShortestRotation(startFacing, endFacing) * t);

            // 绘制残影
            ship.setCopyLocation(curPos, ALPHA, curFacing);

            // 回放期间的电弧特效（从上一个位置打到当前残影位置）
            // 用简单的起点→当前点生成电弧即可
            if (elapsed > 0.05f) {
                float chance = 60f * amount * (0.5f + 0.5f * ship.getFluxLevel());
                int count = (int) chance;
                if (Math.random() < chance - count) count++;
                for (int i = 0; i < count; i++) {
                    Vector2f src = MathUtils.getRandomPointInCircle(startPos, ship.getCollisionRadius() * 0.5f);
                    Vector2f target = MathUtils.getRandomPointInCircle(curPos, ship.getCollisionRadius() * 0.2f);
                    engine.spawnEmpArcVisual(src, null, target, null,
                            MathUtils.getRandomNumberInRange(4f, 8f),
                            LIGHTNING_COLOR, Color.WHITE);
                    dealDamageAround(new MirrorData(endPos, endFacing), 1f, 1f);
                }
            }
        }

        private void triggerFinishEffect() {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) {
                finished = true;
                return;
            }

            Vector2f loc = ship.getLocation();   // 残影已清除，取本体位置

            // 扭曲冲击波
            RippleDistortion ripple = new RippleDistortion(loc, ship.getVelocity());
            ripple.setSize(ship.getCollisionRadius() * 4f);
            ripple.setIntensity(ship.getCollisionRadius() * 0.2f);
            ripple.fadeInSize(0.4f);
            ripple.fadeOutIntensity(0.8f);
            ripple.setFrameRate(75f);
            DistortionShader.addDistortion(ripple);

            // 粒子爆发
            for (int i = 0; i < 50; i++) {
                Vector2f point = MathUtils.getRandomPointInCircle(loc, ship.getCollisionRadius());
                Vector2f vel = MathUtils.getPointOnCircumference(null,
                        ship.getCollisionRadius() * 4f,
                        VectorUtils.getAngle(loc, point));
                engine.addSmoothParticle(point, vel,
                        MathUtils.getRandomNumberInRange(30f, 45f),
                        -15f, 1f, LIGHTNING_COLOR);
            }

            // 电弧伤害（基于残影终点位置，即技能结束位置）
            for (int i = 0; i < 9; i++) {
                dealDamageAround(new MirrorData(endPos, endFacing), 4f, 4f * (0.5f + 0.5f * ship.getFluxLevel()));
            }
            engine.addPlugin(new AfterimageExpandingArcs(loc, ship.getCollisionRadius() * 4f));

            finished = true;
        }

        public void forceFinish() {
            if (finished) return;
            ship.setCopyLocation(null, 0f, 0f);
            isPlaying = false;
            triggerFinishEffect();
        }

        private void dealDamageAround(MirrorData data, float rangeFactor, float damageFactor) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) return;

            List<CombatEntityAPI> entities = CombatUtils.getEntitiesWithinRange(
                    data.location, ship.getCollisionRadius() * rangeFactor);
            for (CombatEntityAPI entity : entities) {
                if (entity == ship || entity.getOwner() == ship.getOwner())
                    continue;
                Vector2f point = MathUtils.getRandomPointInCircle(
                        data.location, ship.getCollisionRadius() * rangeFactor * 0.5f);
                float dmg = DAMAGE * damageFactor;
                float emp = EMP_DAMAGE * damageFactor;
                if (entity instanceof ShipAPI) {
                    engine.applyDamage(entity, point, dmg, DamageType.ENERGY, emp, false, false, ship, false);
                } else if (entity instanceof MissileAPI) {
                    engine.applyDamage(entity, point, dmg * 0.5f, DamageType.ENERGY, 0, false, false, ship, false);
                }
                engine.spawnEmpArcVisual(ship.getLocation(), null, entity.getLocation(), null,
                        MathUtils.getRandomNumberInRange(4f, 8f), LIGHTNING_COLOR, Color.WHITE);
            }
        }

        // 辅助数据类（仅用于 dealDamageAround 参数）
        private class MirrorData {
            public Vector2f location;
            public float facing;
            public MirrorData(Vector2f location, float facing) {
                this.location = location;
                this.facing = facing;
            }
        }

        // 扩散电弧环插件（保持不变，但简化构造函数）
        private class AfterimageExpandingArcs implements EveryFrameCombatPlugin {
            private final Vector2f origin;
            private final float maxRadius;
            private float curRadius;
            private final IntervalUtil interval = new IntervalUtil(0.05f, 0.1f);

            public AfterimageExpandingArcs(Vector2f origin, float maxRadius) {
                this.origin = origin;
                this.maxRadius = maxRadius;
                this.curRadius = 0f;
            }

            @Override
            public void advance(float amount, List<InputEventAPI> events) {
                CombatEngineAPI engine = Global.getCombatEngine();
                if (engine == null || engine.isPaused()) return;

                curRadius += 1600f * amount;
                if (curRadius > maxRadius) {
                    engine.removePlugin(this);
                    return;
                }
                interval.advance(amount);
                if (interval.intervalElapsed()) {
                    float baseAngle = MathUtils.getRandomNumberInRange(0f, 360f);
                    for (int i = 0; i < 6; i++) {
                        float angle = baseAngle + i * 60f + MathUtils.getRandomNumberInRange(-15f, 15f);
                        float leftAngle = angle + MathUtils.getRandomNumberInRange(30f, 45f);
                        float rightAngle = angle - MathUtils.getRandomNumberInRange(30f, 45f);
                        Vector2f p1 = MathUtils.getPointOnCircumference(origin, curRadius, leftAngle);
                        Vector2f p2 = MathUtils.getPointOnCircumference(origin, curRadius, rightAngle);
                        engine.spawnEmpArcVisual(p1, null, p2, null,
                                MathUtils.getRandomNumberInRange(4f, 8f),
                                LIGHTNING_COLOR, Color.WHITE);
                    }
                }
            }

            @Override public void init(CombatEngineAPI engine) {}
            @Override public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {}
            @Override public void renderInWorldCoords(ViewportAPI viewport) {}
            @Override public void renderInUICoords(ViewportAPI viewport) {}
        }
    }
}