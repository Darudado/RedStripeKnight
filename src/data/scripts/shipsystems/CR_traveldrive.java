package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.dark.shaders.distortion.WaveDistortion;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.*;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.util.MagicLensFlare;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CR_traveldrive extends BaseShipSystemScript {
    // 红色系颜色常量
    private static final Color RED_COLOR_1 = new Color(255, 80, 80, 200);
    private static final Color RED_COLOR_2 = new Color(200, 20, 20, 255);
    private static final Color AFTERIMAGE_COLOR = new Color(255, 100, 100, 38); // 红色调残影

    private static final String SWOOSH_SPRITE = "cr_SKR_drill_swoosh";
    private static final String GLOW_SPRITE = "cr_skr_can_glow";

    private WaveDistortion wave = null;
    private boolean systemActive = false;
    private float arcTimer = 0f; // 电弧生成计时器
    private final List<EmpArcEntity> activeArcs = new ArrayList<>(); // 存储活跃的电弧

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;
        if (ship.getHullSpec() == null) return;

        // 相位状态和透明度设置
        boolean shouldBePhased = state == State.IN || state == State.ACTIVE;
        ship.setPhased(shouldBePhased);
        ship.setExtraAlphaMult(0f * effectLevel);
        ship.setApplyExtraAlphaToEngines(true);

        for (ShipAPI child : ship.getChildModulesCopy()) {
            if (child != null) {
                child.setPhased(shouldBePhased);
                child.setExtraAlphaMult( 0f * effectLevel);
                child.setApplyExtraAlphaToEngines(true);
            }
        }

        // === 跃迁扭曲效果 ===
        if (state == State.IN && wave == null) {
            wave = createWaveDistortion(ship);
        } else if (wave != null) {
            updateWaveDistortion(ship, effectLevel);
        }

        // === 跃迁视觉特效 ===
        if ((state == State.ACTIVE || state == State.IN) && effectLevel > 0.1f) {
            Vector2f loc = ship.getLocation();
            renderVortexParticles(ship, loc, effectLevel);
            renderCoreGlow(ship, loc, effectLevel);


            Color color_core;
            if(!(ship.getVentCoreColor() ==null)){
                color_core = ship.getVentCoreColor();
            }else{
                color_core = new Color(255, 45, 45, 225);
            }

            Color color_fringe;
            if(!(ship.getVentFringeColor() ==null)){
                color_fringe =ship.getVentFringeColor();
            }else{
                color_fringe = new Color(255, 45, 75, 175);
            }


            // 4. 镜头光晕
            if (effectLevel > 0.5f && Math.random() < 0.3) {
                MagicLensFlare.createSharpFlare(
                        Global.getCombatEngine(), ship, loc,
                        MathUtils.getRandomNumberInRange(3f, 5f),
                        ship.getCollisionRadius() * (1f + effectLevel),
                        ship.getFacing() + 90f,
                        color_fringe, color_core
                );
            }
        }

        // === 新增：红色电弧特效 (仅在进场阶段) ===
        if (state == State.IN && effectLevel > 0.2f) {
            float elapsed = Global.getCombatEngine().getElapsedInLastFrame();
            arcTimer -= elapsed;

            // 每0.05-0.15秒生成新电弧
            if (arcTimer <= 0) {
                generateArcingEffect(ship, effectLevel);
                arcTimer = MathUtils.getRandomNumberInRange(0.05f, 0.15f) / (effectLevel * 2f);
            }

            // 更新现有电弧
            updateArcs(ship, effectLevel, elapsed);
        } else {
            // 清除所有电弧
            activeArcs.clear();
        }

        // 系统激活标志 - 简化逻辑
        if (state == State.IN) {
            systemActive = true;
        }

        // 速度系统
        if (state == State.OUT) {
            stats.getMaxSpeed().unmodify(id);
        } else {
            stats.getMaxSpeed().modifyFlat(id, 600f * effectLevel);
            stats.getAcceleration().modifyFlat(id, 600f * effectLevel);
        }
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);

        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;

        Vector2f loc = ship.getLocation();

        // 关键修复：总是恢复舰船和子模块的状态，不依赖于systemActive标志
        // 这样可以确保状态总是同步，避免渲染层级错误
        ship.setPhased(false);
        ship.setExtraAlphaMult(1f);
        ship.setApplyExtraAlphaToEngines(false);

        for (ShipAPI child : ship.getChildModulesCopy()) {
            if (child != null) {
                child.setPhased(false);
                child.setExtraAlphaMult(1f);
                child.setApplyExtraAlphaToEngines(false);
            }
        }

        // 仅在系统真正激活过时才播放退出特效
        if (systemActive) {
            // === 跃迁结束特效 ===
            // 1. 核心光球爆发
            renderCoreExplosion(ship, loc);

            // 2. 漩涡粒子爆发
            renderVortexExplosion(ship, loc);

            renderAfterimages(ship);

            // 5. 扭曲涟漪
            createRippleDistortion(ship);

            // 播放音效
            //Global.getSoundPlayer().playSound("SKR_keep_arrival", 1f, 1f, loc, ship.getVelocity());

            systemActive = false;
        }

        // 清理扭曲效果
        if (wave != null) {
            wave.fadeOutSize(0.5f);
            wave = null;
        }

        // 清除所有电弧
        activeArcs.clear();
    }

    // ===== 新增电弧特效方法 =====
    private void generateArcingEffect(ShipAPI ship, float effectLevel) {
        Vector2f shipLoc = ship.getLocation();
        float radius = ship.getCollisionRadius();

        // 创建电弧实体
        EmpArcEntity arc = new EmpArcEntity();
        arc.start = MathUtils.getRandomPointInCircle(shipLoc, radius * 0.8f);

        // 电弧终点在舰船周围或随机目标
        if (Math.random() < 0.7f) {
            // 连接到舰船表面
            arc.end = MathUtils.getRandomPointInCircle(shipLoc, radius * 1.2f);
        } else {
            // 连接到随机位置（模拟空间放电）
            float distance = radius * (2f + effectLevel * 3f);
            float angle = MathUtils.getRandomNumberInRange(0f, 360f);
            arc.end = MathUtils.getPointOnCircumference(shipLoc, distance, angle);
        }

        arc.duration = MathUtils.getRandomNumberInRange(0.1f, 0.3f);
        arc.width = MathUtils.getRandomNumberInRange(2f, 5f) * (0.5f + effectLevel);
        arc.age = 0f;

        activeArcs.add(arc);
    }

    private void updateArcs(ShipAPI ship, float effectLevel, float elapsed) {
        List<EmpArcEntity> toRemove = new ArrayList<>();

        for (EmpArcEntity arc : activeArcs) {
            arc.age += elapsed;

            // 渲染电弧
            renderArc(ship, arc, effectLevel);

            // 移除过期电弧
            if (arc.age >= arc.duration) {
                toRemove.add(arc);
            }
        }

        activeArcs.removeAll(toRemove);
    }

    private void renderArc(ShipAPI ship, EmpArcEntity arc, float effectLevel) {
        if (ship == null) return;

        float progress = arc.age / arc.duration;
        float alphaMult = (1f - progress) * effectLevel;
        alphaMult = Math.max(0f, Math.min(1f, alphaMult));

        Color coreColor = null;

        if (ship.getShield() != null) {
            coreColor = ship.getShield().getRingColor();
        }
        if (coreColor == null) {
            List<ShipEngineControllerAPI.ShipEngineAPI> engines = ship.getEngineController().getShipEngines();
            if (engines != null && !engines.isEmpty()) {
                coreColor = engines.get(0).getContrailColor();
            }
        }
        if (coreColor == null) {
            coreColor = ship.getExplosionFlashColorOverride();
        }

        if (coreColor == null) {
            coreColor = ship.getVentFringeColor(); // 或你之前定义好的 AFTERIMAGE_COLOR
        }
        if(coreColor == null){
            coreColor = RED_COLOR_1;
        }

        // ===== 硬编码红色系，完全不用 ship 的颜色 =====
//        Color coreColor = new Color(
//                RED_COLOR_1.getRed(),
//                RED_COLOR_1.getGreen(),
//                RED_COLOR_1.getBlue(),
//                Math.max(0, Math.min(255, (int)(RED_COLOR_1.getAlpha() * alphaMult)))
//        );

        Color fringeColor = new Color(
                RED_COLOR_2.getRed(),
                RED_COLOR_2.getGreen(),
                RED_COLOR_2.getBlue(),
                Math.max(0, Math.min(255, (int)(RED_COLOR_2.getAlpha() * alphaMult)))
        );

        // 抖动位置
        Vector2f startJittered = MathUtils.getRandomPointInCircle(arc.start, arc.width * 2f);
        Vector2f endJittered = MathUtils.getRandomPointInCircle(arc.end, arc.width * 4f);

        // 调用前再次确保颜色非空（防御性编程）
        if (coreColor != null && fringeColor != null) {
            Global.getCombatEngine().spawnEmpArcVisual(
                    startJittered, ship,
                    endJittered, null,
                    arc.width * (1f - progress * 0.5f),
                    RED_COLOR_1,
                    RED_COLOR_2
            );
        } else {
            // 如果连这个都 null 了，输出错误日志
            Global.getLogger(CR_traveldrive.class).error(
                    "CR_traveldrive: coreColor or fringeColor is null in renderArc, skipping arc render."
            );
        }
    }

    // ===== 原有视觉效果辅助方法 =====
    private WaveDistortion createWaveDistortion(ShipAPI ship) {
        WaveDistortion wave = new WaveDistortion(ship.getLocation(), new Vector2f(0, 0));
        wave.setSize(ship.getCollisionRadius() * 1.5f);
        wave.setIntensity(20f);
        wave.setArc(0f, 360f);
        wave.flip(true);
        DistortionShader.addDistortion(wave);
        return wave;
    }

    private void updateWaveDistortion(ShipAPI ship, float effectLevel) {
        wave.setLocation(ship.getLocation());
        wave.setSize(ship.getCollisionRadius() * (1.5f + effectLevel * 0.5f));
        wave.setIntensity(20f + effectLevel * 30f);
    }

    private void renderVortexParticles(ShipAPI ship, Vector2f loc, float effectLevel) {
        int particles = (int) (6 * effectLevel);
        for (int i = 0; i < particles; i++) {
            Vector2f randomLoc = MathUtils.getRandomPointInCircle(loc, ship.getCollisionRadius() * 0.8f);

            float size = MathUtils.getRandomNumberInRange(100f, 200f) * effectLevel;
            float growth = MathUtils.getRandomNumberInRange(50f, 100f) * effectLevel;
            float rotation = MathUtils.getRandomNumberInRange(ship.getFacing() - 60f, ship.getFacing() - 120f);
            float spin = MathUtils.getRandomNumberInRange(-15f, 15f);

            MagicRender.battlespace(
                    Global.getSettings().getSprite("fx", SWOOSH_SPRITE),
                    randomLoc,
                    MathUtils.getRandomPointInCone(new Vector2f(), 30f, ship.getFacing() + 150f, ship.getFacing() + 210f),
                    new Vector2f(size, size),
                    new Vector2f(growth, growth),
                    rotation,
                    spin,
                    new Color(255, 100, 100, (int)(150 * effectLevel)),
                    true,
                    0f, 0.2f, MathUtils.getRandomNumberInRange(0.5f, 1.5f)
            );
        }
    }

    private void renderCoreGlow(ShipAPI ship, Vector2f loc, float effectLevel) {
        float sizeBase = ship.getCollisionRadius() * 2f;
        float glowSize = sizeBase * effectLevel;

        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", GLOW_SPRITE),
                loc,
                new Vector2f(ship.getVelocity()),
                new Vector2f(glowSize, glowSize),
                new Vector2f(glowSize * 0.5f, glowSize * 0.5f),
                (float)Math.random() * 360f,
                MathUtils.getRandomNumberInRange(-5f, 5f),
                new Color(255, 100, 100, (int)(150 * effectLevel)),
                true,
                0f, 0f, 0f, 0f, 0f, 0f,
                0.1f, 0.2f,
                CombatEngineLayers.ABOVE_SHIPS_LAYER
        );
    }

    private void renderCoreExplosion(ShipAPI ship, Vector2f loc) {
        if(ship==null)return;
        float explosionSize = ship.getCollisionRadius() * 4f;

        Color color_core;
        if(!(ship.getVentCoreColor() ==null)){
            color_core = ship.getVentCoreColor();
        }else{
            color_core = new Color(255, 45, 45, 225);
        }

        MagicRender.battlespace(
                Global.getSettings().getSprite("fx", GLOW_SPRITE),
                loc,
                new Vector2f(ship.getVelocity()),
                new Vector2f(explosionSize, explosionSize),
                new Vector2f(explosionSize * 0.5f, explosionSize * 0.5f),
                (float)Math.random() * 360f,
                0f,
                color_core,
                true,
                0f, 0f, 0f, 0f, 0f, 0f,
                0.1f, 0.3f,
                CombatEngineLayers.ABOVE_SHIPS_LAYER
        );
    }

    private void renderVortexExplosion(ShipAPI ship, Vector2f loc) {
        if(ship==null) return;
        Color color_fringe;
        if(!(ship.getVentFringeColor() ==null)){
            color_fringe =ship.getVentFringeColor();
        }else{
            color_fringe = new Color(255, 45, 75, 175);
        }

        for (int i = 0; i < 12; i++) {
            float size = MathUtils.getRandomNumberInRange(200f, 400f);
            float growth = MathUtils.getRandomNumberInRange(100f, 200f);
            float rotation = MathUtils.getRandomNumberInRange(ship.getFacing() - 60f, ship.getFacing() - 120f);
            float spin = MathUtils.getRandomNumberInRange(-15f, 15f);

            MagicRender.battlespace(
                    Global.getSettings().getSprite("fx", SWOOSH_SPRITE),
                    MathUtils.getRandomPointInCircle(loc, ship.getCollisionRadius() * 1.5f),
                    MathUtils.getRandomPointInCone(new Vector2f(), 64f, ship.getFacing() + 150f, ship.getFacing() + 210f),
                    new Vector2f(size, size),
                    new Vector2f(growth, growth),
                    rotation,
                    spin,
                    color_fringe,
                    true,
                    0f, 0.2f, MathUtils.getRandomNumberInRange(0.5f, 1.5f)
            );
        }
    }

    private void renderAfterimages(ShipAPI ship) {
        // 创建残影效果模拟空间涟漪
        for (int i = 1; i <= 30; i++) {
            float distance = i * 15f;
            float duration = 2.5f - (i * 0.05f);
            Vector2f offset = MathUtils.getPointOnCircumference(
                    new Vector2f(),
                    distance,
                    ship.getFacing() + 180f
            );

            Color AFTERIMAGE = null;

            if (ship.getShield() != null) {
                AFTERIMAGE = ship.getShield().getRingColor();
            }
            if (AFTERIMAGE == null) {
                List<ShipEngineControllerAPI.ShipEngineAPI> engines = ship.getEngineController().getShipEngines();
                if (engines != null && !engines.isEmpty()) {
                    AFTERIMAGE = engines.get(0).getContrailColor();
                }
            }
            if (AFTERIMAGE == null) {
                AFTERIMAGE = ship.getExplosionFlashColorOverride();
            }

            if (AFTERIMAGE == null) {
                AFTERIMAGE = ship.getVentFringeColor(); // 或你之前定义好的 AFTERIMAGE_COLOR
            }

            ship.addAfterimage(
                    AFTERIMAGE,
                    offset.x, offset.y,
                    ship.getVelocity().x * 0.2f, ship.getVelocity().y * 0.2f,
                    0.1f, 0f, 0.1f,
                    duration,
                    false, true, false
            );
        }
    }

    private void createRippleDistortion(ShipAPI ship) {
        RippleDistortion ripple = new RippleDistortion(ship.getLocation(), new Vector2f(ship.getVelocity()));
        ripple.setSize(ship.getCollisionRadius() * 4f);
        ripple.setIntensity(100f);
        ripple.setFrameRate(30f);
        ripple.fadeInSize(0.5f);
        ripple.fadeOutIntensity(1f);
        DistortionShader.addDistortion(ripple);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("跃迁驱动", false);
        }
        return null;
    }

    // ===== 电弧实体类 =====
    private static class EmpArcEntity {
        Vector2f start;
        Vector2f end;
        float width;
        float duration;
        float age;
    }
}