package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;

import java.util.List;
import java.awt.Color;

import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;


public class tempOnhit implements OnHitEffectPlugin {
    private static final float damageDuration = 3f; // 爆炸持续时间
    private static final CollisionClass collisionClass = CollisionClass.MISSILE_FF; // 爆炸碰撞类型
    private static final CollisionClass collisionClassFighter = CollisionClass.MISSILE_FF; // 爆炸对战机碰撞类型
    private static final float particleSizeMin = 10; // 粒子最小大小
    private static final float particleSizeRange = 10; // 粒子随机大小范围
    private static final float particleDuration = 1; // 粒子持续时间
    private static final int particleCount = 15; // 粒子数量
    public static final Color color = Color.RED; // 粒子颜色
    private static final Color expColor = null; // 爆炸颜色 可以null
    private static final float DAMAGE_PERCENT = 0.25f; // 伤害与 Flux 比例，1 表示读取 100% 当前 Flux 数值作为基数
    private static final float coreRadius = 600; // 满伤的核心爆炸范围
    private static final float radius = 1200; // 伤害逐渐递减的边缘爆炸范围
    private static final float NORMAL_ARC_RADIUS = 800;
    private static final float EMP_DAMAGE = 2000;
    private static final Color ARC_COLOR = new Color(163, 86, 91, 189);

    private static Vector2f LocationToOffset(CombatEntityAPI entity, Vector2f loc) {
        // 将绝对位置转换为相对于目标实体的偏移量
        Vector2f offset = new Vector2f(loc);
        Vector2f.sub(offset, entity.getLocation(), offset);
        VectorUtils.rotate(offset, -entity.getFacing(), offset);
        return offset;
    }

    private static Vector2f OffsetToLocation(CombatEntityAPI entity, Vector2f offset) {
        // 将偏移量转换为绝对位置
        Vector2f loc = new Vector2f(offset);
        VectorUtils.rotate(loc, entity.getFacing(), loc);
        Vector2f.add(loc, entity.getLocation(), loc);
        return loc;
    }

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        float delay = 1; // 爆炸延时

        ShipAPI sourceShip = projectile.getSource();
        float fluxLevel ;
        float fluxValue = 0f;

        if (sourceShip != null) {
            fluxLevel = sourceShip.getFluxLevel();


        fluxValue =sourceShip.getFluxTracker().getCurrFlux() * DAMAGE_PERCENT + 6500;
        }

        Global.getCombatEngine().addPlugin(new explosionSpawner(target, LocationToOffset(target, point), fluxValue, delay, sourceShip));
    }


    private static class explosionSpawner extends BaseEveryFrameCombatPlugin {
        final CombatEntityAPI target;
        final Vector2f offset;
        final float damage ;
        final float delay;
        float timer = 0;
        final ShipAPI source;
        float fluxLevel;


        private explosionSpawner(CombatEntityAPI target, Vector2f offset, float damage, float delay, ShipAPI source) {
            this.target = target;
            this.offset = offset;
            this.damage = damage;
            this.delay = delay;
            this.source = source;
            this.fluxLevel = (source != null) ? source.getFluxLevel() : 0f;
        }

        boolean done = false;

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            float emp  = EMP_DAMAGE *DAMAGE_PERCENT * fluxLevel;

            if (done) {
                Global.getCombatEngine().removePlugin(this);
                return;
            }
            timer += amount;
            if (timer >= delay) {
                DamagingExplosionSpec spec = new DamagingExplosionSpec(damageDuration, radius, coreRadius, damage, 0, collisionClass, collisionClassFighter, particleSizeMin, particleSizeRange, particleDuration, particleCount, color, expColor);
                Global.getCombatEngine().spawnDamagingExplosion(spec, source, OffsetToLocation(target, offset));
                if (source != null && target instanceof ShipAPI) {
                    Global.getCombatEngine().spawnEmpArcPierceShields(source, offset, target, target, DamageType.ENERGY, 2000F, emp, 100000.0F, "tachyon_lance_emp_impact", 5.0F, ARC_COLOR, Color.RED);
                }

                done = true;
            }

        }
    }
}