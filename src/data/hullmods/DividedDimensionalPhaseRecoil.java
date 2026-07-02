package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.scripts.utils.MathPersonal;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import particleengine.Emitter;
import particleengine.Particles;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static data.scripts.RSModPlugin.particleEngineEnabled;

public class DividedDimensionalPhaseRecoil extends BaseHullMod implements HullModFleetEffect{
    public static float MIN_CR = 0.1F;
    public static String MOD_KEY = "core_PhaseField";
    public static float PROFILE_MULT = 0.4F;
    public static float MIN_FIELD_MULT = 0.25F;
    public static float ACTIVATION_COST_MULT = 0.5F;
    public static float FLUX_THRESHOLD_INCREASE_PERCENT = 35.0F;
    public static float PHASE_COOLDOWN_REDUCTION = 75F;
    public static float PHASE_SPEED_PERCENT = 2.0F;

    public static float EXPLOSION_DAMAGE = 3000f;
    public static float EXPLOSION_RADIUS = 450f;
    public static float EXPLOSION_EMP = 1500f;
    public static String EXPLOSION_SOUND = "";

    // 新增视觉效果参数
    public static final Color PHASE_COLOR_1 = new Color(185, 17, 17, 255);  // 主色调 - 深红
    public static final Color PHASE_COLOR_2 = new Color(255, 60, 60, 255);  // 次级色调 - 亮红
    public static final Color PHASE_JITTER_COLOR = new Color(255, 100, 100, 150);  // 抖动效果颜色
    public static final float AFTERIMAGE_INTENSITY = 0.8f;  // 残影强度
    public static final float JITTER_INTENSITY = 0.6f;  // 抖动强度
    public static final int JITTER_FRAMES = 5;  // 抖动帧数
    public static final float JITTER_RANGE = 20f;  // 抖动范围

    private final Map<ShipAPI, Boolean> lastPhaseState = new HashMap<>();
    private final Map<ShipAPI, Float> phaseEffectLevel = new HashMap<>();  // 新增：相位效果等级跟踪

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getSensorProfile().modifyMult(id, PROFILE_MULT);
        stats.getDynamic().getMod("phase_cloak_flux_level_for_min_speed_mod").modifyPercent(id, FLUX_THRESHOLD_INCREASE_PERCENT);
        stats.getPhaseCloakActivationCostBonus().modifyMult(id, ACTIVATION_COST_MULT);
        stats.getPhaseCloakCooldownBonus().modifyMult(id, 1.0F + PHASE_COOLDOWN_REDUCTION / 100.0F);
        stats.getDynamic().getMod("phase_cloak_speed").modifyMult(id ,PHASE_SPEED_PERCENT);
    }





    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        super.advanceInCombat(ship, amount);

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused() || !ship.isAlive()) return;
        if (engine.getTotalElapsedTime(false) % 0.033f > amount) return;
        // 更新相位效果等级
        boolean isPhased = ship.isPhased();
        float currentEffectLevel = phaseEffectLevel.getOrDefault(ship, 0f);

        if (isPhased) {
            // 相位时逐渐增加效果等级
            currentEffectLevel = Math.min(1f, currentEffectLevel + amount * 2f);
        } else {
            // 非相位时逐渐减少效果等级
            currentEffectLevel = Math.max(0f, currentEffectLevel - amount * 4f);
        }
        phaseEffectLevel.put(ship, currentEffectLevel);

        // 获取当前相位状态
        Boolean wasPhased = lastPhaseState.get(ship);

        // 初始化状态
        if (wasPhased == null) {
            lastPhaseState.put(ship, isPhased);
            return;
        }

        // 检测相位结束（从相位状态变为非相位状态）
        if (wasPhased && !isPhased) {
            spawnPhaseExitVisuals(ship, engine);
        }

        // 相位期间的视觉效果增强
        if (isPhased && currentEffectLevel > 0.1f) {
            // 增强版残影效果 - 参考示例代码的视觉层次
            Color phaseColor = colorBlend(PHASE_COLOR_1, PHASE_COLOR_2, ship.getFluxLevel());
            float afterimageAlpha = AFTERIMAGE_INTENSITY * currentEffectLevel;

            // 多层残影，增强视觉深度
            for (int i = 0; i < 3; i++) {
                float offsetFactor = (i + 1) * 0.3f;
                ship.addAfterimage(
                        phaseColor,
                        0.0F,
                        0.0F,
                        ship.getVelocity().x * (-3f - offsetFactor * 2),
                        ship.getVelocity().y * (-3f - offsetFactor * 2),
                        0F,
                        0.0F,
                        0.0F,
                        afterimageAlpha * (0.7f - i * 0.2f),
                        false, false, false
                );
            }

            // 船体抖动效果 - 参考示例代码的setJitter用法
            float jitterLevel = JITTER_INTENSITY * currentEffectLevel * (0.5f + ship.getFluxLevel() * 0.5f);
            ship.setJitter(this, PHASE_JITTER_COLOR, jitterLevel, JITTER_FRAMES, JITTER_RANGE * currentEffectLevel);

            // 引擎火焰颜色变化 - 参考示例代码的fadeToOtherColor
            ship.getEngineController().fadeToOtherColor(this, phaseColor, null, currentEffectLevel, 0.5f);
            ship.getEngineController().extendFlame(this, 0.5f * currentEffectLevel, 0.5f * currentEffectLevel, 0.25f * currentEffectLevel);
        }

        // 更新状态
        lastPhaseState.put(ship, isPhased);

        cleanupDeadShips();
    }

    // 生成相位结束视觉效果 - 增强版
    private void spawnPhaseExitVisuals(ShipAPI ship, CombatEngineAPI engine) {
        Vector2f location = ship.getLocation();
        float explosionRadius = EXPLOSION_RADIUS * (1f + ship.getFluxLevel() * 0.5f);

        // 使用参考代码的颜色混合方式
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

        // 1. 核心能量闪光 - 增强版
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

        // 2. 多重爆炸效果
        for (int wave = 0; wave < 2; wave++) {
            float waveDelay = wave * 0.1f;
            float waveRadius = explosionRadius * (1.5f + wave * 0.5f);

            engine.spawnExplosion(
                    MathUtils.getRandomPointInCircle(location, ship.getCollisionRadius() * 0.3f),
                    ship.getVelocity(),
                    colorBlend(baseColor, waveColor, wave * 0.5f),
                    waveRadius,
                    0.6f + wave * 0.2f
            );
        }

        // 3. 粒子引擎效果
        if(particleEngineEnabled) {
            spawnEnhancedParticleEffects(location);
        }

        // 4. 冲击波扩散特效 - 增强版
        int shockwavePoints = 36;
        for (int i = 0; i < shockwavePoints; i++) {
            float angle = i * (360f / shockwavePoints);
            Vector2f point = MathUtils.getPointOnCircumference(location, explosionRadius * 1.2f, angle);

            // 向外扩散的冲击波粒子
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

        // 5. 扭曲效果 - 增强版
        RippleDistortion ripple = new RippleDistortion(location, new Vector2f());
        ripple.setSize(explosionRadius * 2f);
        ripple.setIntensity(200f + ship.getFluxLevel() * 100f);
        ripple.setFrameRate(180f);
        ripple.fadeInSize(0.3f);
        ripple.fadeInIntensity(0.2f);
        ripple.setLifetime(1.0f);
        ripple.fadeOutIntensity(0.5f);
        DistortionShader.addDistortion(ripple);

        // 6. 动态电弧效果 - 增强版
        int arcCount = 16 + (int)(ship.getFluxLevel() * 8);
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

            // 随机电弧分支
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

        // 7. 能量粒子爆发 - 增强版
        int particleCount = 80 + (int)(ship.getFluxLevel() * 40);
        for (int i = 0; i < particleCount; i++) {
            float angle = MathUtils.getRandomNumberInRange(0, 360);
            float speed = MathUtils.getRandomNumberInRange(100f, 400f) * (1f + ship.getFluxLevel());
            Vector2f velocity = new Vector2f(
                    (float)Math.cos(Math.toRadians(angle)) * speed,
                    (float)Math.sin(Math.toRadians(angle)) * speed
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



        engine.addPlugin(new EveryFrameCombatPlugin() {

            @Override
            public void init(CombatEngineAPI engine) {

            }

            private boolean executed = false;
            private final Vector2f explosionLocation = new Vector2f(location);

            @Override
            public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {

            }



            @Override
            public void advance(float amount, List<InputEventAPI> events) {
                if (executed || engine == null) return;
                if (!ship.isAlive()) return;
                executed = true;

                // 对范围内的所有实体应用伤害
                for (CombatEntityAPI entity : engine.getShips()) {
                    // 跳过自身和友方单位
                    if (entity == ship || entity.getOwner() == ship.getOwner()) {
                        continue;
                    }

                    // 计算距离
                    float distance = Vector2f.sub(entity.getLocation(), explosionLocation, null).length();

                    Vector2f hitPoint = new Vector2f(entity.getLocation());
                    if (distance > 0) {
                        Vector2f direction = Vector2f.sub(entity.getLocation(), explosionLocation, null);
                        direction.normalise();
                        // 将命中点从船舰中心向爆炸中心方向移动碰撞半径的距离
                        hitPoint.x -= direction.x * entity.getCollisionRadius();
                        hitPoint.y -= direction.y * entity.getCollisionRadius();
                    }

                    // 如果实体在爆炸范围内
                    if (distance <= EXPLOSION_RADIUS * 3f) {
                        // 计算伤害衰减因子（距离越远伤害越低）
                        float damageMult = 1f - (distance / (EXPLOSION_RADIUS * 3f));

                        // 应用能量伤害
                        engine.applyDamage(
                                entity,
                                hitPoint,
                                EXPLOSION_DAMAGE * damageMult,
                                DamageType.ENERGY,
                                0,
                                false,
                                false,
                                ship
                        );

                        // 应用EMP伤害
                        engine.applyDamage(
                                entity,
                                hitPoint,
                                EXPLOSION_EMP * damageMult,
                                DamageType.ENERGY,
                                0,
                                false,
                                false,
                                ship
                        );
                    }
                }
            }

            @Override
            public void renderInWorldCoords(ViewportAPI viewport) {

            }

            @Override
            public void renderInUICoords(ViewportAPI viewport) {

            }
        });
    }

    // 增强粒子效果
    private void spawnEnhancedParticleEffects(Vector2f location) {
        // 主冲击波环
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
        ringEmitter.color(PHASE_COLOR_1);
        ringEmitter.randomHSVA(20f, 1.5f, 0f, 0f);
        Particles.burst(ringEmitter, 60);

        // 能量核心粒子
        Emitter coreEmitter = Particles.initialize(location, "graphics/fx/explosion_ring0.png");
        coreEmitter.setLayer(CombatEngineLayers.BELOW_SHIPS_LAYER);
        coreEmitter.life(0.4f, 0.7f);
        coreEmitter.fadeTime(0f, 0.05f, 0.2f, 0.4f);
        coreEmitter.radialVelocity(0f, 100f);
        coreEmitter.radialAcceleration(-200f, -300f);
        coreEmitter.size(300f, 400f);
        coreEmitter.growthRate(-400f, -600f);
        coreEmitter.color(PHASE_COLOR_2);
        Particles.burst(coreEmitter, 3);
    }

    // 颜色混合方法 - 参考示例代码的实现
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

    public void advanceInCampaign(CampaignFleetAPI fleet) {
        String key = "$updatedPhaseFieldModifier";
        if (fleet.isPlayerFleet() && fleet.getMemoryWithoutUpdate() != null && !fleet.getMemoryWithoutUpdate().getBoolean(key) && fleet.getMemoryWithoutUpdate().getBoolean("$justToggledTransponder")) {
            this.onFleetSync(fleet);
            fleet.getMemoryWithoutUpdate().set(key, true, 0.1F);
        }
    }

    public boolean withAdvanceInCampaign() {
        return true;
    }

    public boolean withOnFleetSync() {
        return true;
    }

    public void onFleetSync(CampaignFleetAPI fleet) {
        float mult = getPhaseFieldMultBaseProfileAndTotal(fleet, null, 0.0F, 0.0F)[0];
        if (fleet.isTransponderOn()) {
            mult = 1.0F;
        }

        if (mult <= 0.0F) {
            fleet.getDetectedRangeMod().unmodifyMult(MOD_KEY);
        } else {
            fleet.getDetectedRangeMod().modifyMult(MOD_KEY, mult, "Phase ships in fleet");
        }
    }

    public static float[] getPhaseFieldMultBaseProfileAndTotal(CampaignFleetAPI fleet, String skipId, float addProfile, float addSensor) {
        List<FleetMemberAPI> members = new ArrayList<>();
        List<FleetMemberAPI> phase = new ArrayList<>();

        for(FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (!member.getId().equals(skipId)) {
                members.add(member);
                if (!member.isMothballed() && !(member.getRepairTracker().getCR() < MIN_CR) && member.getVariant().hasHullMod("DividedDimensionalPhaseRecoil")) {
                    phase.add(member);
                }
            }
        }

        float[] profiles;
        if (addProfile <= 0.0F) {
            profiles = new float[members.size()];
        } else {
            profiles = new float[members.size() + 1];
        }

        float[] phaseSensors;
        if (addSensor <= 0.0F) {
            phaseSensors = new float[phase.size()];
        } else {
            phaseSensors = new float[phase.size() + 1];
        }

        int i = 0;

        for(FleetMemberAPI member : members) {
            profiles[i] = member.getStats().getSensorProfile().getModifiedValue();
            ++i;
        }

        if (addProfile > 0.0F) {
            profiles[i] = addProfile;
        }

        i = 0;

        for(FleetMemberAPI member : phase) {
            phaseSensors[i] = member.getStats().getSensorStrength().getModifiedValue();
            ++i;
        }

        if (addSensor > 0.0F) {
            phaseSensors[i] = addSensor;
        }

        int numProfileShips = Global.getSettings().getInt("maxSensorShips");
        float totalProfile = getTopKValuesSum(profiles, numProfileShips);
        float totalPhaseSensors = getTopKValuesSum(phaseSensors, numProfileShips);
        float total = Math.max(totalProfile + totalPhaseSensors, 1.0F);
        float mult = totalProfile / total;
        if (totalPhaseSensors <= 0.0F) {
            mult = 1.0F;
        }

        if (mult < MIN_FIELD_MULT) {
            mult = MIN_FIELD_MULT;
        }

        if (mult > 1.0F) {
            mult = 1.0F;
        }

        return new float[]{mult, totalProfile, totalPhaseSensors};
    }

    public static float getTopKValuesSum(float[] arr, int k) {
        k = Math.min(k, arr.length);
        float kVal = Misc.findKth(arr, arr.length - k);
        float total = 0.0F;
        int found = 0;

        for (float v : arr) {
            if (v > kVal) {
                ++found;
                total += v;
            }
        }

        if (k > found) {
            total += (float)(k - found) * kVal;
        }

        return total;
    }

    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) {
            return (int)FLUX_THRESHOLD_INCREASE_PERCENT + "%";
        }else if (index == 1) {
            return (int)((1-ACTIVATION_COST_MULT)*100) + "%";
        } else if(index == 2){
            return (int)((1-PROFILE_MULT)*100) + "%" ;
        }else {
            return index == 3 ? (int)PHASE_COOLDOWN_REDUCTION + "%" : null;
        }
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        return !ship.getVariant().hasHullMod("adaptive_coils") && ship.getHullSpec().isPhase();
    }

    public String getUnapplicableReason(ShipAPI ship) {
        if (ship.getVariant().hasHullMod("adaptive_coils")) {
            return "Incompatible with Adaptive Phase Coils";
        } else {
            return !ship.getHullSpec().isPhase() ? "Can only be installed on phase ships" : super.getUnapplicableReason(ship);
        }
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara("Different from traditional phase coils, the distributed dimensional difference phase coil used in the Crusade Project is more like squeezing the ship into the gap between P space and real space; to a certain extent, the ship can be said to have disappeared briefly, but it can still be observed and return to real space.", opad, h);
        tooltip.addPara("Naturally, the almost desperate approach paid off, but it also required a certain price. The most direct result is that even though a far larger than normal number of phase coils and a high-efficiency distributed design are used, the cooling time of the entire system is still outrageously long.", opad, h);
        tooltip.addPara("Therefore, only experienced commanders or advanced AI cores can better control this beast.", opad, h);
        tooltip.addPara("At the end of the phase, a violent dimensional rebound will be released, resulting in strong visual disturbance and energy release effects. During the phase, the ship's hull will produce multi-layered afterimages and jitter effects.", opad, Misc.getPositiveHighlightColor());
        tooltip.addPara("Is it not sinking into P space, but null? Perhaps only beings who can understand the pulse of the universe can use this technology to its extreme——from a certain damaged file.", opad, h);
    }
    private void cleanupDeadShips() {
        lastPhaseState.entrySet().removeIf(entry -> !entry.getKey().isAlive());
        phaseEffectLevel.entrySet().removeIf(entry -> !entry.getKey().isAlive());
    }
}