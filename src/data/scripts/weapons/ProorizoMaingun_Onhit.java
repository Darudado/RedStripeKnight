package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.loading.ProjectileSpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.combat.entities.terrain.Asteroid;
import org.boxutil.base.SimpleParticleControlData;
import org.boxutil.define.BoxEnum;
import org.boxutil.manager.CombatRenderingManager;
import org.boxutil.units.standard.entity.SpriteEntity;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProorizoMaingun_Onhit implements OnHitEffectPlugin {

    // 从第一个代码整合的常量
    private static final float damageDuration = 3f;
    private static final CollisionClass collisionClass = CollisionClass.MISSILE_FF;
    private static final CollisionClass collisionClassFighter = CollisionClass.MISSILE_FF;
    private static final float particleSizeMin = 10;
    private static final float particleSizeRange = 10;
    private static final float particleDuration = 1;
    private static final int particleCount = 15;
    public static final Color color = Color.RED;
    private static final Color expColor = null;
    private static final float DAMAGE_PERCENT = 0.25f;
    private static final float coreRadius = 600;
    private static final float radius = 1200;
    private static final float EMP_DAMAGE = 2000;
    private static final Color ARC_COLOR = new Color(163, 86, 91, 189);

    // 新增电弧效果常量
    private static final float ARC_DAMAGE_MULTIPLIER = 0.5f; // 电弧伤害倍数
    private static final float ARC_EMP_MULTIPLIER = 0.3f;    // 电弧EMP倍数
    private static final int MAX_ARCS = 2;                   // 最大电弧数量
    private static final float ARC_RANGE = 800f;             // 电弧最大跳跃距离
    private static final Color CHAIN_ARC_COLOR = new Color(200, 100, 255, 200); // 链式电弧颜色

    // 坐标转换方法 - 从第一个代码整合
    private static Vector2f LocationToOffset(CombatEntityAPI entity, Vector2f loc) {
        Vector2f offset = new Vector2f(loc);
        Vector2f.sub(offset, entity.getLocation(), offset);
        VectorUtils.rotate(offset, -entity.getFacing(), offset);
        return offset;
    }

    private static Vector2f OffsetToLocation(CombatEntityAPI entity, Vector2f offset) {
        Vector2f loc = new Vector2f(offset);
        VectorUtils.rotate(loc, entity.getFacing(), loc);
        Vector2f.add(loc, entity.getLocation(), loc);
        return loc;
    }

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        // 原有的视觉效果
        engine.addSmoothParticle(point, new Vector2f(), 300, 2, 0.05f, Color.WHITE);
        engine.addHitParticle(point, new Vector2f(), 200, 1, 0.2f, Color.PINK);
        engine.addHitParticle(point, new Vector2f(), 100, 0.1f, 0.5f, Color.RED);

        WeaponAPI weapon = projectile.getWeapon();
        ShipAPI weapon_ship = weapon.getShip();

        ProjectileSpecAPI spec = projectile.getProjectileSpec();
        engine.spawnEmpArcPierceShields(projectile.getSource(), point, target, target, DamageType.ENERGY, projectile.getDamageAmount() / 30.0F, projectile.getDamageAmount() / 2.0F, 100000.0F, "tachyon_lance_emp_impact", spec.getWidth() + 5.0F, spec.getFringeColor(), spec.getCoreColor());

        engine.spawnEmpArc(projectile.getSource(), projectile.getLocation(), target, target, DamageType.HIGH_EXPLOSIVE, 10.0F, 10.0F, 1000.0F, "tachyon_lance_emp_impact", spec.getWidth() + 5.0F, spec.getFringeColor(), spec.getCoreColor());

        // 整合的Flux-based延迟爆炸逻辑
        float delay = 1f; // 爆炸延时1秒
        ShipAPI sourceShip = projectile.getSource();
        float fluxValue;

        if (sourceShip != null) {
            // 伤害计算：当前Flux的25% + 6500基础伤害
            fluxValue = sourceShip.getFluxTracker().getCurrFlux() * DAMAGE_PERCENT + 2500;
            // 添加延迟爆炸插件
            engine.addPlugin(new FluxExplosionSpawner(target, LocationToOffset(target, point), fluxValue, delay, sourceShip));
        }

        // 原有的穿透效果逻辑
        if (!shieldHit && target instanceof ShipAPI){
            engine.spawnEmpArcPierceShields(projectile.getSource(), point, target, target,
                    DamageType.ENERGY, projectile.getDamageAmount() / 30.0F,
                    projectile.getDamageAmount() / 10.0F, 100000.0F, "tachyon_lance_emp_impact",
                    spec.getWidth() + 5.0F, spec.getFringeColor(), spec.getCoreColor());

            engine.spawnEmpArc(projectile.getSource(), projectile.getLocation(), target, target,
                    DamageType.HIGH_EXPLOSIVE, 25.0F, 10.0F, 1000.0F, "tachyon_lance_emp_impact",
                    spec.getWidth() + 5.0F, spec.getFringeColor(), spec.getCoreColor());

            // 命中粒子效果
            for(int i = 0; i < 50; i++) {
                float angle = VectorUtils.getAngleStrict(target.getLocation(), point);
                engine.addHitParticle(point, MathUtils.getRandomPointInCone(new Vector2f(),
                                MathUtils.getRandomNumberInRange(100, 200), angle - 15, angle + 15),
                        MathUtils.getRandomNumberInRange(2, 5), 2f,
                        MathUtils.getRandomNumberInRange(0.5f, 2f),
                        new Color(255, MathUtils.getRandomNumberInRange(150, 255), 150));
            }

            // 创建穿透效果监听器
            projectile.getSource().addListener(new ProjDeal(projectile, projectile.getSource(), point));
        }

        if (target.getClass() == Asteroid.class){
            for(int i = 0; i < 50; i++) {
                float angle = VectorUtils.getAngleStrict(target.getLocation(), point);
                engine.addHitParticle(point, MathUtils.getRandomPointInCone(new Vector2f(),
                                MathUtils.getRandomNumberInRange(100, 200), angle - 15, angle + 15),
                        MathUtils.getRandomNumberInRange(2, 5), 2f,
                        MathUtils.getRandomNumberInRange(0.5f, 2f),
                        new Color(255, MathUtils.getRandomNumberInRange(150, 255), 150));
            }

            projectile.getSource().addListener(new ProjDeal(projectile, projectile.getSource(), point));
        }
    }

    // 整合的Flux-based爆炸生成器 - 增强电弧效果
    private static class FluxExplosionSpawner extends BaseEveryFrameCombatPlugin {
        final CombatEntityAPI target;
        final Vector2f offset;
        final float damage;
        final float delay;
        float timer = 0;
        final ShipAPI source;
        final float fluxLevel;
        boolean done = false;
        // 新增：记录已经受到电弧伤害的目标
        final Set<ShipAPI> arcedTargets = new HashSet<>();

        private FluxExplosionSpawner(CombatEntityAPI target, Vector2f offset, float damage, float delay, ShipAPI source) {
            this.target = target;
            this.offset = offset;
            this.damage = damage;
            this.delay = delay;
            this.source = source;
            this.fluxLevel = (source != null) ? source.getFluxLevel() : 0f;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (Global.getCombatEngine().isPaused()) return;

            float emp = EMP_DAMAGE * DAMAGE_PERCENT * fluxLevel;

            if (done) {
                return;
            }

            timer += amount;
            if (timer >= delay) {
                // 创建爆炸效果
                DamagingExplosionSpec spec = new DamagingExplosionSpec(
                        damageDuration, radius, coreRadius, damage, 0,
                        collisionClass, collisionClassFighter, particleSizeMin,
                        particleSizeRange, particleDuration, particleCount, color, expColor
                );

                Vector2f explosionCenter = OffsetToLocation(target, offset);
                Global.getCombatEngine().spawnDamagingExplosion(
                        spec, source, explosionCenter
                );

                // 生成EMP电弧 - 增强为对所有敌方单位的链式电弧
                if (source != null) {
                    // 对主要目标发射电弧（如果目标是一个飞船且不是残骸）
                    if (target instanceof ShipAPI) {
                        ShipAPI mainTarget = (ShipAPI) target;
                        // 检查主要目标不是残骸且是敌方单位
                        if (!mainTarget.isHulk() && mainTarget.getOwner() != source.getOwner()) {
                            // 将主要目标添加到已电弧目标集合
                            arcedTargets.add(mainTarget);

                            Global.getCombatEngine().spawnEmpArcPierceShields(
                                    source, explosionCenter, mainTarget, mainTarget,
                                    DamageType.ENERGY, 500F, emp, 100000.0F,
                                    "tachyon_lance_emp_impact", 5.0F, ARC_COLOR, Color.RED
                            );
                        }
                    }

                    // 新增：为爆炸范围内所有敌方单位发射链式电弧
                    spawnChainArcs(explosionCenter, source, emp);

                    SpriteAPI glow = Global.getSettings().getSprite("fx", "CR_HITGLOW");
                    SpriteAPI explosion = Global.getSettings().getSprite("fx", "CR_NUKE_EXP_1");
                    SpriteAPI shockwave = Global.getSettings().getSprite("fx", "CR_NUKE_EXP_WAVE");

                    SimpleParticleControlData particle = new SimpleParticleControlData(512, 3.0f, -5120.0f, false);
                    SpriteEntity particleEntity = new SpriteEntity("graphics/fx/soot64.png");

                    MagicRender.battlespace(glow, explosionCenter, new Vector2f(0.0F, 0.0F), new Vector2f(650.0F , 650.0F ), new Vector2f(-140.0F , -140.0F ), 0.0F, 0.0F, new Color(255, 65, 73, 230), true, 2.0F , 1.0F , 3.0F );
                    MagicRender.battlespace(explosion, explosionCenter, new Vector2f(0.0F, 0.0F), new Vector2f(500.0F , 500.0F ), new Vector2f(-100.0F , -100.0F ), 0.0F, 0.0F, new Color(255, 115, 100, 240), true, 0.1F , 1.5F , 1.6F );
                    //MagicRender.battlespace(particle_1, explosionCenter, new Vector2f(0.0F, 0.0F), new Vector2f(250.0F , 250.0F ), new Vector2f(70.0F , 70.0F ), (float)MathUtils.getRandomNumberInRange(0, 360), 0.0F, new Color(255, 174, 73, 225), true, 0.1F , 3.0F , 4.0F );
                    //MagicRender.battlespace(particle_2, explosionCenter, new Vector2f(0.0F, 0.0F), new Vector2f(250.0F , 250.0F ), new Vector2f(-70.0F , -70.0F ), (float)MathUtils.getRandomNumberInRange(0, 360), 0.0F, new Color(255, 174, 73, 225), true, 0.1F , 3.0F , 4.0F );
                    MagicRender.battlespace(shockwave, explosionCenter, new Vector2f(0.0F, 0.0F), new Vector2f(650.0F , 650.0F ), new Vector2f(-300.0F , -300.0F ), 0.0F, 0.0F, new Color(255, 90, 73, 255), true, 0.1F , 0.3F , 0.1F );

                    Color Exp_Color = new Color(255, 45, 35, 225);

                    particleEntity.getMaterialData().setColor(Exp_Color);//为星云图上颜色
                    //particleEntity.getMaterialData().setEmissiveColor(new Color(125,125,255,95));//暂时没用，因为我把上面那行Emissive注释掉了。原理上是再叠一张图
                    particleEntity.getMaterialData().setColorAlpha(0.3f);//核心颜色透明度
                    //particleEntity.getMaterialData().setEmissiveColorAlpha(0.4f);
                    particleEntity.getMaterialData().setColorToEmissive(0.25f);//不知道
                    particleEntity.getMaterialData().setAlphaToEmissive(0.1f);//不知道
                    particleEntity.getMaterialData().setGlowPower(0.2f);//可能是亮度
                    particleEntity.setTileSize(5, 5);//取这种复数素材在一张图里用的
                    particleEntity.setBaseSizePerTiles(16.0f, 16.0f);//同上，每个单独素材的大小
                    particleEntity.setRandomTile(true);
                    particleEntity.setRandomTileEachInstance(true);
                    particleEntity.setAdditiveBlend();
                    particleEntity.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
                    particleEntity.setControlData(particle);
                    CombatRenderingManager.addEntity(BoxEnum.ENTITY_SPRITE, particleEntity);

                }

                done = true;
                Global.getCombatEngine().removePlugin(this);
            }
        }

        // 新增方法：生成链式电弧
        private void spawnChainArcs(Vector2f explosionCenter, ShipAPI source, float baseEmpDamage) {
            CombatEngineAPI engine = Global.getCombatEngine();

            // 获取爆炸范围内的所有飞船
            List<ShipAPI> shipsInRange = engine.getShips();
            int arcsSpawned = 0;

            for (ShipAPI ship : shipsInRange) {
                // 检查是否为敌方单位且在范围内，并且尚未受到电弧伤害，并且不是残骸
                if (ship.getOwner() != source.getOwner() &&
                        !ship.isHulk() && // 新增：确保不是残骸
                        MathUtils.isWithinRange(ship.getLocation(), explosionCenter, ARC_RANGE) &&
                        !arcedTargets.contains(ship)) {  // 检查是否已经受到电弧伤害

                    // 将目标添加到已电弧目标集合，确保每个目标只受到一次电弧
                    arcedTargets.add(ship);

                    // 计算伤害 - 距离越远伤害越低
                    float distance = MathUtils.getDistance(ship.getLocation(), explosionCenter);
                    float distanceMultiplier = 1f - (distance / ARC_RANGE) * 0.5f; // 距离衰减

                    float arcDamage = 500F * ARC_DAMAGE_MULTIPLIER * distanceMultiplier;
                    float arcEmp = baseEmpDamage * ARC_EMP_MULTIPLIER * distanceMultiplier;

                    // 生成电弧
                    engine.spawnEmpArcPierceShields(
                            source, explosionCenter, ship, ship,
                            DamageType.ENERGY,
                            arcDamage,
                            arcEmp,
                            100000.0F,
                            "tachyon_lance_emp_impact",
                            3.0F + (distanceMultiplier * 2f), // 厚度随距离变化
                            CHAIN_ARC_COLOR,
                            new Color(255, 150, 200, 255)
                    );

                    arcsSpawned++;

                    // 达到最大电弧数量时停止
                    if (arcsSpawned >= MAX_ARCS) {
                        break;
                    }
                }
            }

            // 电弧视觉效果
            if (arcsSpawned > 0) {
                for (int i = 0; i < 10; i++) {
                    Vector2f randomOffset = MathUtils.getRandomPointInCircle(new Vector2f(), 100f);
                    Vector2f particlePos = Vector2f.add(explosionCenter, randomOffset, new Vector2f());

                    engine.addHitParticle(
                            particlePos,
                            MathUtils.getRandomPointInCircle(new Vector2f(), 50f),
                            3f + (float) Math.random() * 3f,
                            1f,
                            0.3f + (float) Math.random() * 0.3f,
                            new Color(200, 100, 255, 200)
                    );
                }
            }
        }
    }

    // 原有的穿透效果内部类 - 保持不变
    private static class ProjDeal implements DamageDealtModifier, AdvanceableListener {
        final DamagingProjectileAPI originalProj;
        final ShipAPI ship;
        final int UID;
        final Vector2f hitPoint;

        Set<CombatEntityAPI> damaged = new HashSet<>();

        private ProjDeal(DamagingProjectileAPI proj, ShipAPI ship, Vector2f hitPoint) {
            this.originalProj = proj;
            this.ship = ship;
            this.hitPoint = new Vector2f(hitPoint);
            this.UID = Misc.random.nextInt(1000000);
        }

        @Override
        public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
            if (!damaged.contains(target)) {
                if (param instanceof DamagingProjectileAPI projectile) {
                    if (projectile.getCustomData().containsKey("rs_proorizo_MaingunSource")) {
                        int uid = (int) projectile.getCustomData().get("rs_proorizo_MaingunSource");
                        if (uid == UID) {
                            damaged.add(target);
                            damage.getModifier().modifyMult("init", 0.1f);
                            return "init";
                        }
                    }
                }
            }
            return null;
        }

        @Override
        public void advance(float amount) {
            CombatEngineAPI engine = Global.getCombatEngine();

            if (!engine.isEntityInPlay(ship) || engine.isPaused()) {
                ship.removeListener(this);
                return;
            }

            // 修正：沿着弹丸飞行方向的反方向移动25个单位
            // 获取弹丸的飞行方向（角度）

            // 计算反方向（向后180度）
            float reverseFacing = originalProj.getFacing();
            if (reverseFacing >= 360f) {
                reverseFacing -= 360f;
            }

            // 使用正确的方向计算新位置
            Vector2f newLocation = MathUtils.getPointOnCircumference(
                    hitPoint,
                    25f, // 向后25个单位
                    reverseFacing
            );

            DamagingProjectileAPI newProjectile = (DamagingProjectileAPI) engine.spawnProjectile(
                    originalProj.getSource(),
                    originalProj.getWeapon(),
                    "rs_proorizo",
                    newLocation,
                    originalProj.getFacing(), // 新弹丸仍然保持原来的飞行方向
                    originalProj.getVelocity()
            );

            newProjectile.setDamageAmount(originalProj.getDamageAmount() * 0.25f);
            newProjectile.setCustomData("rs_proorizo_MaingunSource", UID);
            engine.addPlugin(new PenetrationEffectPlugin(newProjectile));
            ship.removeListener(this);
        }
    }

    // 原有的穿透效果插件 - 保持不变
    private static class PenetrationEffectPlugin implements EveryFrameCombatPlugin {
        private final DamagingProjectileAPI projectile;
        private boolean hasPlayedEffect = false;

        public PenetrationEffectPlugin(DamagingProjectileAPI projectile) {
            this.projectile = projectile;
        }

        public void advance(CombatEngineAPI engine) {
            if (engine.isPaused() || projectile.isExpired() || projectile.isFading()) {
                return;
            }

            if (!hasPlayedEffect) {
                Vector2f point = projectile.getLocation();
                engine.addSmoothParticle(point, new Vector2f(), 150, 1.5f, 0.03f, Color.WHITE);
                engine.addHitParticle(point, new Vector2f(), 100, 0.8f, 0.15f, Color.PINK);
                hasPlayedEffect = true;
            }
        }

        @Override
        public void init(CombatEngineAPI engine) {}

        @Override
        public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {}

        @Override
        public void advance(float amount, List<InputEventAPI> events) {}

        @Override
        public void renderInWorldCoords(ViewportAPI viewport) {}

        @Override
        public void renderInUICoords(ViewportAPI viewport) {}
    }
}