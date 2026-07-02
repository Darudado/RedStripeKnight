package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.IntervalUtil;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class LEDEffect implements EveryFrameWeaponEffectPlugin {
    private boolean once = true;
    private FaderUtil blinker;
    private IntervalUtil timer;
    private boolean withL = false;
    private StandardLight light;
    private Color c;

    // 新增战术系统相关字段
    private float systemBoost = 1f;
    private static final float BASE_BOOST = 1.5f;
    private static final float MAX_INTENSITY = 3f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused()) return;

        // 初始化阶段
        if (once) {
            initializeComponents(weapon);
            once = false;
        }

        // 舰船状态检测
        final ShipAPI ship = weapon.getShip();
        if (ship == null) {
            cleanUpLight();
            return;
        }

        // 战术系统检测
        updateSystemBoost(weapon, ship); // 修改参数传递

        // 时间推进逻辑
        final float adv = calculateAdvanceRate(ship, amount);

        // 光源生命周期管理
        if (!ship.isAlive()) {
            cleanUpLight();
            return;
        }

        // 主逻辑流程
        if (blinker.isFadedOut()) {
            handleFadedOutState(adv);
        } else {
            handleActiveState(adv, weapon);
        }
    }

    private void initializeComponents(WeaponAPI weapon) {
        c = weapon.getSpec().getGlowColor();

        // 间隔时间配置（修改标签逻辑）
        float interval = 4f;
        if (weapon.getSpec().hasTag("rs_short_interval")) {
            interval = 2f;
        } else if (weapon.getSpec().hasTag("rs_long_interval")) {
            interval = 8f;
        }
        timer = new IntervalUtil(interval, interval);

        // 闪烁时长配置
        float blinkTime = 2f;
        if (weapon.getSpec().hasTag("rs_fast_blink")) {
            blinkTime = 0.8f;
        }
        blinker = new FaderUtil(0f, blinkTime/2f, blinkTime/2f, false, true);
        blinker.fadeIn();

        // 光源初始化（使用独立标签）
        withL = weapon.getSpec().hasTag("rs_has_light");
        if (withL) {
            light = new StandardLight(
                    weapon.getLocation(),
                    new Vector2f(0, 0),
                    new Vector2f(0, 0),
                    weapon.getShip()
            );
            light.makePermanent();
            LightShader.addLight(light);
        }
    }

    // 修改方法签名和实现
    private void updateSystemBoost(WeaponAPI weapon, ShipAPI ship) {
        float activeBoost = BASE_BOOST;
        if (ship.getSystem() != null && ship.getSystem().isActive()) {
            // 从当前武器获取规格
            if (weapon.getSpec().hasTag("rs_tactical_boost")) {
                activeBoost = 2f;
            }
            systemBoost = activeBoost;
        } else {
            systemBoost = 1f;
        }
    }


    private float calculateAdvanceRate(ShipAPI ship, float amount) {
        return ship.getFluxTracker().isOverloadedOrVenting() ? 2f * amount : amount;
    }

    private void handleFadedOutState(float adv) {
        timer.advance(adv);
        if (timer.intervalElapsed()) {
            blinker.fadeIn();
        }
    }

    private void handleActiveState(float adv, WeaponAPI weapon) {
        blinker.advance(adv);

        // 更新武器透明度
        updateSpriteAlpha(weapon);

        // 更新光源参数
        if (withL) {
            updateLightProperties(weapon);
        }
    }

    private void updateSpriteAlpha(WeaponAPI weapon) {
        float baseAlpha = blinker.getBrightness() * systemBoost;
        baseAlpha = Math.min(baseAlpha, 1f);

        Color original = weapon.getSprite().getColor();
        weapon.getSprite().setColor(
                new Color(
                        original.getRed(),
                        original.getGreen(),
                        original.getBlue(),
                        (int) (baseAlpha * 255)
                )
        );
    }

    private void updateLightProperties(WeaponAPI weapon) {
        if (light == null) {
            // 异常恢复机制
            light = new StandardLight(
                    weapon.getLocation(),
                    new Vector2f(0, 0),
                    new Vector2f(0, 0),
                    weapon.getShip()
            );
            LightShader.addLight(light);
        }

        // 强度计算带系统加成
        float intensity = (0.8f + 0.8f * blinker.getBrightness()) * systemBoost;
        intensity = Math.min(intensity, MAX_INTENSITY);

        // 大小保持原有逻辑
        float size = 200f + 200f * blinker.getBrightness();

        light.setLocation(weapon.getLocation());
        if (c != null) light.setColor(c);
        light.setIntensity(intensity);
        light.setSize(size);
    }

    private void cleanUpLight() {
        if (light != null) {
            LightShader.removeLight(light);
            light = null;
        }
    }
}