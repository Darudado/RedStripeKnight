package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import data.scripts.utils.RSUtil;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

import static org.magiclib.plugins.MagicRenderPlugin.addBattlespace;

public class DecoWeaponVenting implements EveryFrameWeaponEffectPlugin {
    private final SpriteAPI ventingSprite = Global.getSettings().getSprite("misc", "dust_particles");
    private float particleCooldown = 0f;
    private static final float PARTICLE_INTERVAL = 0.05f; // 粒子生成间隔（秒）
    private WeaponAPI target;
    private boolean init = false;
    private boolean wasFiring = false; // 记录上一帧是否在开火

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused()) {
            return;
        }

        // 初始化目标武器
        if (!init) {
            init = true;
            ShipAPI ship = weapon.getShip();
            String slotId = weapon.getSlot().getId();
            String targetId = "MAINGUN";

            // 查找MAINGUN武器
            for (WeaponAPI w : ship.getAllWeapons()) {
                if (w.getSlot().getId().endsWith(targetId)) {  // 检查每个武器的槽位ID
                    target = w;
                    break;
                }
            }
        }

        // 如果没有找到目标武器，直接返回
        if (target == null) {
            Global.getCombatEngine().addFloatingText(weapon.getLocation(),
                    "DecoWeaponVenting: Target weapon not found!", 20f, Color.RED, weapon.getShip(), 1f, 1f);
            return;
        }

        // 检测MAINGUN武器的开火状态变化
        boolean isFiring = target.isFiring();

        // 检测开火结束（从开火状态变为非开火状态）
        boolean fireEnded = wasFiring && !isFiring;

        // 更新上一帧状态
        wasFiring = isFiring;

        // 检查是否应该触发粒子效果：开火结束或处于冷却状态
        boolean shouldVent = fireEnded || target.getCooldownRemaining() > 0;

        if (!shouldVent) {
            particleCooldown = 0f;
            return;
        }

        // 更新粒子冷却计时器
        particleCooldown -= amount;

        // 当冷却时间结束且引擎存在时生成粒子
        if (particleCooldown <= 0f) {
            generateVentingParticles(weapon, target);
            particleCooldown = PARTICLE_INTERVAL;
        }
    }

    private void generateVentingParticles(WeaponAPI weapon, WeaponAPI targetWeapon) {
        // 获取装饰武器位置和方向
        Vector2f weaponLocation = weapon.getLocation();
        float weaponFacing = weapon.getCurrAngle();

        // 计算散热粒子的基础位置（稍微偏离武器中心）
        float offsetDistance = weapon.getRange() * 0.1f;
        Vector2f offset = new Vector2f(
                (float) Math.cos(Math.toRadians(weaponFacing)) * offsetDistance,
                (float) Math.sin(Math.toRadians(weaponFacing)) * offsetDistance
        );
        Vector2f particleLocation = Vector2f.add(weaponLocation, offset, null);

        // 计算粒子速度（基于武器朝向和随机散布）
        float baseSpeed = 150f + (float) Math.random() * 150f;
        float spreadAngle = 45f; // 散热散布角度
        float angleVariation = (float) Math.random() * spreadAngle - spreadAngle * 0.5f;
        float particleAngle = weaponFacing + angleVariation;

        Vector2f particleVelocity = new Vector2f(
                (float) Math.cos(Math.toRadians(particleAngle)) * baseSpeed,
                (float) Math.sin(Math.toRadians(particleAngle)) * baseSpeed
        );

        // 根据目标武器状态调整粒子参数
        float sizeMultiplier;
        Color particleColor;

        if (targetWeapon.getCooldownRemaining() > 0) {
            // 冷却状态 - 蓝色调散热介质
            particleColor = new Color(255, 225, 225, 175);
            sizeMultiplier = 1f;

            // 冷却时间越长，粒子效果越强
            float cooldownRatio = targetWeapon.getCooldownRemaining() / targetWeapon.getCooldown();
            sizeMultiplier *= (1f + cooldownRatio * 0.75f);
        } else {
            // 开火结束状态 - 橙色调散热介质（更强烈的效果）
            particleColor = new Color(245, 245, 255, 255);
            sizeMultiplier = 2.5f;
        }

        // 添加一些随机性
        float randomSize = 35f +  weapon.getCooldown() * 35f;
        float size = randomSize * sizeMultiplier;
        float endSizeMult = 1f + (float) Math.random();
        float spin = 20f+ (float) Math.random() * 25f;
        float lifetime = 0.15f + (float) Math.random() * 0.15f;

        // 生成主要散热粒子
        WeaponVenting(ventingSprite, particleColor, particleLocation, particleVelocity,
                size, endSizeMult, spin, lifetime, CombatEngineLayers.BELOW_SHIPS_LAYER);

        // 开火结束时生成额外的粒子团
        if (targetWeapon.getCooldownRemaining() == 0) {
            // 开火结束的爆发效果
            for (int i = 0; i < 3; i++) {
                Vector2f burstParticleVel = new Vector2f(
                        particleVelocity.x * (0.7f + (float) Math.random() * 0.6f),
                        particleVelocity.y * (0.7f + (float) Math.random() * 0.6f)
                );
                Color burstColor = new Color(
                        particleColor.getRed(),
                        particleColor.getGreen(),
                        particleColor.getBlue(),
                        180
                );
                WeaponVenting(ventingSprite, burstColor, particleLocation, burstParticleVel,
                        size * (0.8f + (float) Math.random() * 0.4f),
                        endSizeMult * (1f + (float) Math.random() * 0.3f),
                        spin * (0.5f + (float) Math.random()),
                        lifetime * (0.8f + (float) Math.random() * 0.4f),
                        CombatEngineLayers.BELOW_SHIPS_LAYER);
            }
        } else {
            // 冷却期间的常规粒子效果
            if (Math.random() < 0.3f) {
                Vector2f largeParticleVel = new Vector2f(particleVelocity.x * 0.5f, particleVelocity.y * 0.5f);
                Color largeParticleColor = new Color(particleColor.getRed(), particleColor.getGreen(),
                        particleColor.getBlue(), 150);
                WeaponVenting(ventingSprite, largeParticleColor, particleLocation, largeParticleVel,
                        size * 1.5f, endSizeMult * 1.2f, spin * 0.5f, lifetime * 1.5f,
                        CombatEngineLayers.BELOW_SHIPS_LAYER);
            }
        }
    }

    public static void WeaponVenting(SpriteAPI venting, Color color, Vector2f location, Vector2f vel,
                                     float size, float endSizeMult, float spin, float time, CombatEngineLayers layer) {
        size *= 2f * 0.25f;

        venting.setColor(color);
        venting.setSize(size * 2f, size * 2f);
        RSUtil.shapeFrom4x4Sprite(venting);

        Vector2f growth = new Vector2f(size * (endSizeMult - 1f), size * (endSizeMult - 1f));
        spin = spin - spin * 1.5f * (float) Math.random();

        // 生成多个粒子实例以增强效果
        addBattlespace(venting, location, vel, growth, spin, 0f, 0f, time, layer);
        addBattlespace(venting, location, vel, growth, spin * 0.5f, 0f, 0f, time * 1.2f, layer);

        // 添加第三个粒子以增加密度
        if (size > 5f) {
            Vector2f thirdParticleVel = new Vector2f(vel.x * 0.8f, vel.y * 0.8f);
            addBattlespace(venting, location, thirdParticleVel, growth,
                    -spin * 0.3f, 0f, 0f, time * 0.8f, layer);
        }
    }
}