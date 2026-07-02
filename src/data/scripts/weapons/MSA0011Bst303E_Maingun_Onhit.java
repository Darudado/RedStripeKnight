package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.ProjectileSpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.combat.entities.Ship;
import com.fs.starfarer.combat.entities.terrain.Asteroid;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MSA0011Bst303E_Maingun_Onhit implements OnHitEffectPlugin {
    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        // 视觉效果
        engine.addSmoothParticle(point, new Vector2f(), 300, 2, 0.05f, Color.WHITE);
        engine.addHitParticle(point, new Vector2f(), 200, 1, 0.2f, Color.PINK);
        engine.addHitParticle(point, new Vector2f(), 100, 0.1f, 0.5f, Color.RED);

        WeaponAPI weapon = projectile.getWeapon();
        ShipAPI weapon_ship = weapon.getShip();

        ProjectileSpecAPI spec = projectile.getProjectileSpec();
        engine.spawnEmpArcPierceShields(projectile.getSource(), point, target, target, DamageType.ENERGY, projectile.getDamageAmount() / 30.0F, projectile.getDamageAmount() / 2.0F, 100000.0F, "tachyon_lance_emp_impact", spec.getWidth() + 5.0F, spec.getFringeColor(), spec.getCoreColor());

        engine.spawnEmpArc(projectile.getSource(), projectile.getLocation(), target, target, DamageType.HIGH_EXPLOSIVE, 10.0F, 10.0F, 1000.0F, "tachyon_lance_emp_impact", spec.getWidth() + 5.0F, spec.getFringeColor(), spec.getCoreColor());

        // 只在非护盾命中时创建穿透效果
        if (!shieldHit && target.getClass() == Ship.class){
            // 生成EMP电弧效果
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

    }

    // 内部类 - 处理穿透效果
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
            // 只处理未伤害过的目标
            if (!damaged.contains(target)) {
                if (param instanceof DamagingProjectileAPI projectile) {

                    // 检查弹丸是否带有特定标识
                    if (projectile.getCustomData().containsKey("MSA0011Bst303E_MaingunSource")) {
                        int uid = (int) projectile.getCustomData().get("MSA0011Bst303E_MaingunSource");

                        // 确认UID匹配
                        if (uid == UID) {
                            damaged.add(target);

                            // 增强伤害（这里实际上是减少为10%）
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

            // 检查是否应该移除监听器
            if (!engine.isEntityInPlay(ship) || engine.isPaused()) {
                ship.removeListener(this);
                return;
            }

            // 生成一个新弹丸
            Vector2f newLocation = new Vector2f(hitPoint);
            DamagingProjectileAPI newProjectile = (DamagingProjectileAPI) engine.spawnProjectile(
                    originalProj.getSource(),
                    originalProj.getWeapon(),
                    "rs_MSA0011(Bst)303E_Maingun", // 使用特殊ID
                    newLocation,
                    originalProj.getFacing(),
                    originalProj.getVelocity()
            );

            // 设置弹丸属性
            newProjectile.setDamageAmount(originalProj.getDamageAmount() * 0.25f); // 减少伤害

            // 添加自定义数据标识来源
            newProjectile.setCustomData("MSA0011Bst303E_MaingunSource", UID);

            // 添加命中效果
            engine.addPlugin(new PenetrationEffectPlugin(newProjectile));

            // 立即移除监听器，因为我们只生成一个弹丸
            ship.removeListener(this);
        }
    }

    // 内部类 - 处理穿透弹丸的命中效果
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

            // 在弹丸位置持续产生视觉效果
            if (!hasPlayedEffect) {
                Vector2f point = projectile.getLocation();

                // 添加视觉效果
                engine.addSmoothParticle(point, new Vector2f(), 150, 1.5f, 0.03f, Color.WHITE);
                engine.addHitParticle(point, new Vector2f(), 100, 0.8f, 0.15f, Color.PINK);

                hasPlayedEffect = true;
            }
        }

        @Override
        public void init(CombatEngineAPI engine) {
            // 初始化代码
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
    }
}