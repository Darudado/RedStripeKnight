package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.IntervalUtil;

import java.awt.Color;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;
import java.util.List;
import java.util.ArrayList;

public class ChargeRingEffect implements EveryFrameWeaponEffectPlugin {
    // 动画相关变量
    private int currentFrame = 0;
    private float timer = 0;
    private float currentSpeedMultiplier = 1f;
    private float targetSpeedMultiplier = 1f;

    // 速度变化相关常量
    private static final float BASE_ANIMATION_SPEED = 10f;
    private static final float MAX_MAINGUN_SPEED = 60f;
    private static final float MAX_SYSTEM_SPEED = 35f;
    private static final float SPEED_TRANSITION_RATE = 5f;

    // 缓存的主炮武器引用
    private List<WeaponAPI> mainGuns = null;
    private boolean initialized = false;

    // === 新增特效相关变量 ===

    // 颜色定义
    private static final Color CHARGEUP_PARTICLE_COLOR = new Color(200, 35, 25, 150); // 蓝色调充能粒子
    private static final Color CHARGEUP_ARC_COLOR = new Color(200, 0, 10, 200);       // 蓝色电弧// 蓝色枪口闪光

    // 光晕尺寸

    // 特效间隔控制
    private final IntervalUtil particleInterval = new IntervalUtil(0.02F, 0.02F);
    private final IntervalUtil arcInterval = new IntervalUtil(0.05F, 0.05F);

    // 充能状态跟踪
    private float lastMaxChargeLevel = 0.0F;
    private boolean wasCharging = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused() || weapon.getAnimation() == null || !weapon.getShip().isAlive()) {
            return;
        }

        // 初始化缓存
        if (!initialized) {
            initializeWeaponCache(weapon);
            initialized = true;
        }

        // 暂停自动动画播放，改为手动控制
        weapon.getAnimation().pause();

        // 计算目标速度倍数
        calculateTargetSpeed(weapon);

        // 平滑过渡到目标速度
        updateSpeedMultiplier(amount);

        // 更新动画帧
        updateAnimation(amount, weapon);

        // 设置当前帧
        weapon.getAnimation().setFrame(currentFrame);

        // === 新增：处理充能特效 ===
        //handleChargeEffects(amount, engine, weapon);
    }

    /**
     * 处理充能相关的粒子、电弧和光晕特效
     */
    private void handleChargeEffects(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        // 获取当前主炮的最大充能等级
        float currentMaxChargeLevel = getMaxMainGunChargeLevel();

        // 检查主炮是否正在充能
        boolean isCharging = isMainGunCharging();

        // 更新特效间隔
        particleInterval.advance(amount);
        arcInterval.advance(amount);

        // 充能粒子效果
        if (isCharging && particleInterval.intervalElapsed()) {
            spawnChargeParticles(engine, weapon, currentMaxChargeLevel);
        }

        // 充能电弧效果
        if (isCharging && arcInterval.intervalElapsed()) {
            spawnChargeArcs(engine, weapon, currentMaxChargeLevel);
        }

        // 充能光晕效果（随机触发）
        if (isCharging && Math.random() < (double)(amount * 20.0F * currentMaxChargeLevel)) {
            spawnChargeFlares(weapon, currentMaxChargeLevel);
        }

        // 充能结束检测（用于可能的爆发效果）
        if (wasCharging && !isCharging && lastMaxChargeLevel > 0.5f) {
            // 充能中断或结束，可以在这里添加爆发效果
            spawnChargeReleaseEffect(engine, weapon, lastMaxChargeLevel);
        }

        // 更新状态跟踪
        wasCharging = isCharging;
        lastMaxChargeLevel = currentMaxChargeLevel;
    }

    /**
     * 生成充能粒子效果
     */
    private void spawnChargeParticles(CombatEngineAPI engine, WeaponAPI weapon, float chargeLevel) {
        Vector2f weaponLocation = weapon.getLocation();
        ShipAPI ship = weapon.getShip();
        float shipFacing = ship.getFacing();
        Vector2f shipVelocity = ship.getVelocity();

        // 粒子数量与充能等级成正比
        int particleCount = (int)(15.0F * chargeLevel);

        for (int i = 0; i < particleCount; ++i) {
            // 粒子从武器位置向外扩散
            float distance = MathUtils.getRandomNumberInRange(50.0F, 150.0F * chargeLevel);
            float size = MathUtils.getRandomNumberInRange(5.0F, 15.0F) * (1f + chargeLevel);
            float angle = MathUtils.getRandomNumberInRange(-180.0F, 180.0F);

            Vector2f spawnLocation = MathUtils.getPointOnCircumference(weaponLocation, distance, angle + shipFacing);

            // 粒子速度与充能等级相关
            float speed = distance * (2.0f + chargeLevel * 8.0f);
            Vector2f particleVelocity = MathUtils.getPointOnCircumference(shipVelocity, speed, 180.0F + angle + shipFacing);

            // 粒子透明度与充能等级相关
            Color particleColor = new Color(
                    CHARGEUP_PARTICLE_COLOR.getRed(),
                    CHARGEUP_PARTICLE_COLOR.getGreen(),
                    CHARGEUP_PARTICLE_COLOR.getBlue(),
                    (int)(CHARGEUP_PARTICLE_COLOR.getAlpha() * chargeLevel)
            );

            engine.addHitParticle(spawnLocation, particleVelocity, size, 1.0f, 0.8f, particleColor);
        }
    }

    /**
     * 生成充能电弧效果
     */
    private void spawnChargeArcs(CombatEngineAPI engine, WeaponAPI weapon, float chargeLevel) {
        ShipAPI ship = weapon.getShip();
        Vector2f weaponLocation = weapon.getLocation();

        // 电弧数量与充能等级相关
        int arcCount = 1 + (int)(chargeLevel * 4);

        for (int i = 0; i < arcCount; ++i) {
            // 电弧目标点随机分布在武器周围，范围随充能等级增大
            float arcDistance = MathUtils.getRandomNumberInRange(100.0F, 350.0F * chargeLevel);
            Vector2f arcEndPoint = MathUtils.getRandomPointInCircle(weaponLocation, arcDistance);

            // 电弧厚度与充能等级相关
            float arcThickness = chargeLevel * 5.0F + 1.0F;

            // 电弧颜色强度与充能等级相关
            Color arcColor = new Color(
                    CHARGEUP_ARC_COLOR.getRed(),
                    CHARGEUP_ARC_COLOR.getGreen(),
                    CHARGEUP_ARC_COLOR.getBlue(),
                    (int)(CHARGEUP_ARC_COLOR.getAlpha() * chargeLevel)
            );

            engine.spawnEmpArc(
                    ship, weaponLocation, null, new SimpleEntity(arcEndPoint),
                    DamageType.ENERGY,
                    0.0F, // 伤害
                    0.0F, // EMP伤害
                    1000.0F, // 最大范围
                    null,
                    arcThickness,
                    arcColor,
                    arcColor
            );
        }
    }

    /**
     * 生成充能光晕效果
     */
    private void spawnChargeFlares(WeaponAPI weapon, float chargeLevel) {
        ShipAPI ship = weapon.getShip();
        Vector2f weaponLocation = weapon.getLocation();
        Vector2f offset = new Vector2f(weaponLocation);
        Vector2f.sub(offset, ship.getLocation(), offset);


    }

    /**
     * 充能释放效果（当充能中断时触发）
     */
    private void spawnChargeReleaseEffect(CombatEngineAPI engine, WeaponAPI weapon, float chargeLevel) {
        Vector2f weaponLocation = weapon.getLocation();
        ShipAPI ship = weapon.getShip();
        Vector2f shipVelocity = ship.getVelocity();

        // 小型能量爆发
        engine.spawnExplosion(weaponLocation, shipVelocity,
                new Color(80, 120, 255, 150),
                100.0F * chargeLevel, 0.2F);

        // 爆发粒子
        for (int i = 0; i < (int)(10 * chargeLevel); ++i) {
            float distance = MathUtils.getRandomNumberInRange(20.0F, 80.0F);
            float size = MathUtils.getRandomNumberInRange(4.0F, 10.0F);
            float angle = MathUtils.getRandomNumberInRange(0.0F, 360.0F);
            Vector2f spawnLocation = MathUtils.getPointOnCircumference(weaponLocation, distance, angle);
            float speed = distance * 4.0f;
            Vector2f particleVelocity = MathUtils.getPointOnCircumference(shipVelocity, speed, angle + 180.0F);

            engine.addHitParticle(spawnLocation, particleVelocity, size, 1.0f, 0.5f, CHARGEUP_PARTICLE_COLOR);
        }
    }

    /**
     * 获取主炮的最大充能等级（用于特效强度）
     */
    private float getMaxMainGunChargeLevel() {
        if (mainGuns == null || mainGuns.isEmpty()) {
            return 0.0f;
        }

        float maxCharge = 0.0f;
        for (WeaponAPI mainGun : mainGuns) {
            if (mainGun.getChargeLevel() > maxCharge) {
                maxCharge = mainGun.getChargeLevel();
            }
        }
        return maxCharge;
    }

    // === 原有的方法保持不变 ===

    private void initializeWeaponCache(WeaponAPI weapon) {
        mainGuns = new ArrayList<>();
        String currentSlotId = weapon.getSlot().getId();
        String[] mainGunPatterns = {
                "MainGun",
                currentSlotId + "_MAIN",
                "MAIN_" + currentSlotId,
        };

        for (WeaponAPI w : weapon.getShip().getAllWeapons()) {
            String slotId = w.getSlot().getId();
            for (String pattern : mainGunPatterns) {
                if (pattern.equals(slotId)) {
                    mainGuns.add(w);
                    break;
                }
            }
        }
    }

    private void calculateTargetSpeed(WeaponAPI weapon) {
        float newTargetSpeed;
        boolean systemActive = isSystemActive(weapon);
        boolean mainGunCharging = isMainGunCharging();

        if (mainGunCharging) {
            newTargetSpeed = MAX_MAINGUN_SPEED;
        } else if (systemActive && isAnyWeaponFiring(weapon)) {
            newTargetSpeed = MAX_MAINGUN_SPEED;
        } else if (systemActive) {
            newTargetSpeed = MAX_SYSTEM_SPEED;
        } else {
            newTargetSpeed = BASE_ANIMATION_SPEED;
        }

        targetSpeedMultiplier = newTargetSpeed;
    }

    private boolean isSystemActive(WeaponAPI weapon) {
        ShipSystemAPI system = weapon.getShip().getSystem();
        return system != null && system.isActive();
    }

    private boolean isMainGunCharging() {
        if (mainGuns == null || mainGuns.isEmpty()) {
            return false;
        }
        for (WeaponAPI mainGun : mainGuns) {
            boolean isCharging = mainGun.getChargeLevel() > 0 &&
                    (mainGun.getCooldownRemaining() == 0 || mainGun.isInBurst());
            if (isCharging) {
                return true;
            }
        }
        return false;
    }

    private boolean isAnyWeaponFiring(WeaponAPI weapon) {
        if (isMainGunCharging()) {
            return true;
        }
        for (WeaponAPI w : weapon.getShip().getAllWeapons()) {
            if (mainGuns != null && mainGuns.contains(w)) {
                continue;
            }
            if (w.getChargeLevel() > 0 || w.getCooldownRemaining() > 0) {
                return true;
            }
        }
        return false;
    }

    private void updateSpeedMultiplier(float amount) {
        if (currentSpeedMultiplier < targetSpeedMultiplier) {
            currentSpeedMultiplier = Math.min(
                    currentSpeedMultiplier + amount * SPEED_TRANSITION_RATE,
                    targetSpeedMultiplier
            );
        } else if (currentSpeedMultiplier > targetSpeedMultiplier) {
            currentSpeedMultiplier = Math.max(
                    currentSpeedMultiplier - amount * SPEED_TRANSITION_RATE,
                    targetSpeedMultiplier
            );
        }
    }

    private void updateAnimation(float amount, WeaponAPI weapon) {
        int totalFrames = weapon.getAnimation().getNumFrames();
        if (totalFrames == 0) return;

        timer += amount * currentSpeedMultiplier * weapon.getAnimation().getFrameRate();
        while (timer >= 1f) {
            timer -= 1f;
            currentFrame++;
            if (currentFrame >= totalFrames) {
                currentFrame = 0;
            }
        }
    }

}