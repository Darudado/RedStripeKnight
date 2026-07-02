package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

// 导入自定义电弧所需的类
import data.scripts.CombatPlugin;
import org.boxutil.units.standard.entity.TrailEntity;
import org.boxutil.units.standard.entity.FlareEntity;

public class RS_ArcMine implements OnFireEffectPlugin {
    public static float ARC = 30.0F;
    public static Color STANDARD_COLOR = new Color(200,15,35,255);
    public static Color EXPLOSION_UNDERCOLOR = new Color(100, 0, 25, 100);
    float size = 75.0F;

    // 地雷生成概率，0~1，默认总是生成
    public static float MINE_SPAWN_CHANCE = 1.0F;

    public RS_ArcMine() {
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        float emp = projectile.getEmpAmount();
        float dam = projectile.getDamageAmount();
        CombatEntityAPI target = this.findTarget(projectile, weapon);
        float thickness = 65F;
        float coreWidthMult = 0.67F;
        Color color = weapon.getShip().getVentCoreColor();
        Color color2 = weapon.getShip().getVentFringeColor();

        ShipAPI ship = weapon.getShip();

        Vector2f hitPoint; // 电弧命中点
        if (target != null) {
            float distance = Vector2f.sub(target.getLocation(), ship.getLocation(), null).length();
            hitPoint = new Vector2f(target.getLocation());
            if (distance > 0) {
                Vector2f direction = Vector2f.sub(target.getLocation(), ship.getLocation(), null);
                direction.normalise();
                // 将命中点从船舰中心向爆炸中心方向移动碰撞半径的距离
                hitPoint.x -= direction.x * target.getCollisionRadius();
                hitPoint.y -= direction.y * target.getCollisionRadius();
            }
            hitPoint = target.getLocation(); // 目标当前位置即为电弧终点
            EmpArcEntityAPI arc = engine.spawnEmpArc(projectile.getSource(), projectile.getLocation(),
                    weapon.getShip(), target, DamageType.ENERGY, dam, emp, 100000.0F,
                    "shock_repeater_emp_impact", thickness, color, color2);
            arc.setCoreWidthOverride(thickness * coreWidthMult);
            arc.setSingleFlickerMode();
        } else {
            hitPoint = this.pickNoTargetDest(projectile, weapon);
            // 生成自定义电弧实体
            Pair<TrailEntity, FlareEntity> pair = CombatPlugin.RS_BOX_spawnEmpArcVisual(
                    null, thickness, projectile.getLocation(), hitPoint,
                    color2, color, 15f, 1.25f, 0.1f
            );

            // 创建一个渲染插件来托管这两个实体
            ArcRenderingPlugin plugin = new ArcRenderingPlugin(pair.one, pair.two, 0.4f); // 总时长 0.2+0.2
            engine.addPlugin(plugin);
        }

        Vector2f vel = new Vector2f();
        if (projectile.getDamageTarget() != null) {
            vel.set(projectile.getDamageTarget().getVelocity());
        }
        float dur = projectile.getWeapon().getSpec().getBurstDuration() + projectile.getWeapon().getSpec().getChargeTime();
        // 在电弧命中点生成地雷
        if (MINE_SPAWN_CHANCE >= Math.random()) {
            spawnMine(projectile.getSource(), hitPoint);
            spawnMine(projectile.getSource(), hitPoint);
            this.spawnHitDarkening(STANDARD_COLOR,EXPLOSION_UNDERCOLOR, hitPoint, vel, size, dur);
        }
    }

    /**
     * 在指定位置生成一枚riftlance_minelayer地雷，完全复制RiftLanceEffect的实现
     * @param source 发射者（船）
     * @param mineLoc 生成位置（电弧命中点）
     */
    private void spawnMine(ShipAPI source, Vector2f mineLoc) {
        CombatEngineAPI engine = Global.getCombatEngine();
        MissileAPI mine = (MissileAPI) engine.spawnProjectile(source, null,
                "riftlance_minelayer", mineLoc, (float) Math.random() * 360.0F, null);
        if (source != null) {
            Global.getCombatEngine().applyDamageModifiersToSpawnedProjectileWithNullWeapon(source,
                    WeaponAPI.WeaponType.ENERGY, false, mine.getDamage());
        }

        float fadeInTime = 0.05F;
        mine.getVelocity().scale(0.0F);
        mine.fadeOutThenIn(fadeInTime);
        float liveTime = 0.0F;
        mine.setFlightTime(mine.getMaxFlightTime() - liveTime);
        mine.addDamagedAlready(source);
        mine.setNoMineFFConcerns(true);

    }

    public Vector2f pickNoTargetDest(DamagingProjectileAPI projectile, WeaponAPI weapon) {
        float spread = 50.0F;
        float range = weapon.getRange() - spread;
        Vector2f from = projectile.getLocation();
        Vector2f dir = Misc.getUnitVectorAtDegreeAngle(weapon.getCurrAngle());
        dir.scale(range);
        Vector2f.add(from, dir, dir);
        dir = Misc.getPointWithinRadius(dir, spread);
        return dir;
    }

    // 新增：检查目标是否为可攻击的有效目标
    private boolean isValidTarget(CombatEntityAPI target, DamagingProjectileAPI projectile, WeaponAPI weapon) {
        if (target == null) return false;
        // 敌对检查
        if (target.getOwner() == weapon.getShip().getOwner()) return false;

        if (target instanceof ShipAPI shipTarget) {
            if (shipTarget.isHulk() || shipTarget.isPhased() || !shipTarget.isTargetable()) {
                return false;
            }
        } else if (target instanceof MissileAPI) {
            // 锁定目标理论上不会返回导弹，保守处理
            return false;
        }

        if (target.getCollisionClass() == CollisionClass.NONE) return false;

        // 距离与电弧角度检查
        Vector2f from = projectile.getLocation();
        float radius = Misc.getTargetingRadius(from, target, false);
        float dist = Misc.getDistance(from, target.getLocation()) - radius;
        if (dist > weapon.getRange()) return false;
        return Misc.isInArc(weapon.getCurrAngle(), ARC, from, target.getLocation());
    }

    // 修改后的目标搜索方法
    public CombatEntityAPI findTarget(DamagingProjectileAPI projectile, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();

        // 1. 优先使用舰船的锁定目标
        if (ship != null) {
            ShipAPI lockedTarget = ship.getShipTarget();
            if (isValidTarget(lockedTarget, projectile, weapon)) {
                return lockedTarget;
            }
        }

        // 2. 无有效锁定目标，在武器范围内搜索
        float range = weapon.getRange();
        Vector2f from = projectile.getLocation();
        Iterator<Object> iter = Global.getCombatEngine().getAllObjectGrid().getCheckIterator(from,
                range * 2.0F, range * 2.0F);
        int owner = weapon.getShip().getOwner();

        CombatEntityAPI bestShip = null;
        CombatEntityAPI bestFighter = null;
        float minScoreShip = Float.MAX_VALUE;
        float minScoreFighter = Float.MAX_VALUE;

        boolean ignoreFlares = ship != null &&
                ship.getMutableStats().getDynamic().getValue("pd_ignores_flares", 0.0F) >= 1.0F;
        ignoreFlares |= weapon.hasAIHint(WeaponAPI.AIHints.IGNORES_FLARES);

        while (iter.hasNext()) {
            Object o = iter.next();
            if (!(o instanceof MissileAPI || o instanceof ShipAPI)) continue;

            CombatEntityAPI other = (CombatEntityAPI) o;
            if (other.getOwner() == owner) continue;

            // 舰船状态过滤
            if (other instanceof ShipAPI otherShip) {
                if (otherShip.isHulk() || otherShip.isPhased() || !otherShip.isTargetable()) {
                    continue;
                }
            }

            if (other.getCollisionClass() == CollisionClass.NONE) continue;

            // 忽略flare
            if (ignoreFlares && other instanceof MissileAPI missile) {
                if (missile.isFlare()) continue;
            }

            // 排除导弹（仅保留飞机）
            if (other instanceof MissileAPI missile) {
                if (missile.isMine() && missile.isGuided() && missile.isMirv()&& missile.isFlare()) {
                    continue;   // 不打击导弹
                }
            }

            // 距离与电弧筛选
            float radius = Misc.getTargetingRadius(from, other, false);
            float dist = Misc.getDistance(from, other.getLocation()) - radius;
            if (dist > range) continue;
            if (!Misc.isInArc(weapon.getCurrAngle(), ARC, from, other.getLocation())) continue;

            // 分别记录最佳舰船和最佳飞机
            if (other instanceof ShipAPI) {
                if (dist < minScoreShip) {
                    minScoreShip = dist;
                    bestShip = other;
                }
            } else if (other instanceof MissileAPI) { // 此时一定是飞机
                if (dist < minScoreFighter) {
                    minScoreFighter = dist;
                    bestFighter = other;
                }
            }
        }

        // 优先级：舰船 > 飞机
        if (bestShip != null) return bestShip;
        return bestFighter;
    }

    public void spawnHitDarkening(Color color, Color undercolor, Vector2f point, Vector2f vel, float size, float baseDuration) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.getViewport().isNearViewport(point, 100.0F + size * 2.0F)) {
            Color c = color;

            for(int i = 0; i < 5; ++i) {
                float dur = baseDuration + baseDuration * (float)Math.random();
                Vector2f pt = Misc.getPointWithinRadius(point, size * 0.5F);
                Vector2f v = Misc.getUnitVectorAtDegreeAngle((float)Math.random() * 360.0F);
                v.scale(size + size * (float)Math.random() * 0.5F);
                v.scale(0.2F);
                Vector2f.add(vel, v, v);
                float maxSpeed = size * 1.5F * 0.2F;
                float minSpeed = size * 1.0F * 0.2F;
                float overMin = v.length() - minSpeed;
                if (overMin > 0.0F) {
                    float durMult = 1.0F - overMin / (maxSpeed - minSpeed);
                    if (durMult < 0.1F) {
                        durMult = 0.1F;
                    }

                    dur *= 0.5F + 0.5F * durMult;
                }

                engine.addNegativeNebulaParticle(pt, v, size, 2.0F, 0.5F / dur, 0.0F, dur, c);
            }

            float rampUp = 0.0F;
            c = undercolor;

            for(int i = 0; i < 12; ++i) {
                Vector2f loc = new Vector2f(point);
                loc = Misc.getPointWithinRadius(loc, size);
                float s = size * 3.0F * (0.5F + (float)Math.random() * 0.5F);
                engine.addNebulaParticle(loc, vel, s, 1.5F, rampUp, 0.0F, baseDuration, c);
            }

        }
    }

    private static class ArcRenderingPlugin implements CombatLayeredRenderingPlugin, EveryFrameCombatPlugin {
        private final TrailEntity trail;
        private final FlareEntity flare;
        private float elapsed = 0f;
        private final float totalDuration;

        public ArcRenderingPlugin(TrailEntity trail, FlareEntity flare, float duration) {
            this.trail = trail;
            this.flare = flare;
            this.totalDuration = duration;
        }

        @Override
        public void advance(float amount) {
            elapsed += amount;
            if (elapsed >= totalDuration) {
                // 生命周期结束，从引擎移除自身
                Global.getCombatEngine().removePlugin(this);
                // 可以在这里清理实体资源（如果有需要）
            }
            // 如果有每帧更新的参数（如 UV 偏移），可以在这里设置
            // trail.setUVOffset(...);
        }

        @Override
        public EnumSet<CombatEngineLayers> getActiveLayers() {
            return null;
        }

        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            // 在合适的图层调用实体的绘制方法
            if (layer == CombatEngineLayers.ABOVE_PARTICLES_LOWER) { // 选择一个合适的图层
                if (trail != null) trail.glDraw();
                if (flare != null) flare.glDraw();
            }
        }

        @Override
        public float getRenderRadius() {
            return 1000f; // 根据需要设置，影响可见性剔除
        }

        // 其他必须实现的方法可以留空或简单返回
        @Override
        public void init(CombatEntityAPI entity) {
        }

        @Override
        public void cleanup() {

        }

        @Override
        public boolean isExpired() {
            return false;
        }

        @Override
        public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {

        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {

        }

        @Override
        public void renderInWorldCoords(ViewportAPI viewport) {

        }

        @Override
        public void renderInUICoords(ViewportAPI viewport) {

        }

        @Override
        public void init(CombatEngineAPI engine) {

        }
    }

}