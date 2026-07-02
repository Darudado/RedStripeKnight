//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import org.boxutil.base.SimpleParticleControlData;
import org.boxutil.define.BoxEnum;
import org.boxutil.manager.CombatRenderingManager;
import org.boxutil.units.standard.entity.DistortionEntity;
import org.boxutil.units.standard.entity.SpriteEntity;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class VOW_NukeOnhit implements OnHitEffectPlugin {
    private static final Color EXPLOSION_COLOR = new Color(195, 75, 115, 255);
    private static final Color PARTICLE_COLOR = new Color(195, 75, 115, 255);
    private static final float ATTRACT_FORCE = 150f;
    protected static final float RANGE = 500f;//星云粒子分布半径
    private static final float VEL_MIN = 0.2F;
    private static final float VEL_MAX = 0.3F;
    private static final float A_2 = 75.0F;

    public VOW_NukeOnhit() {
    }

    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        MissileAPI subProj = (MissileAPI)engine.spawnProjectile(projectile.getSource(), projectile.getWeapon(), "vow_nuke_sub_bomblet", projectile.getLocation(), projectile.getFacing(), null);
        subProj.setFromMissile(true);
        GuidedMissileAI subAI = (GuidedMissileAI)subProj.getMissileAI();
        subAI.setTarget(target);
        MissileAPI subProj1 = (MissileAPI)engine.spawnProjectile(projectile.getSource(), projectile.getWeapon(), "vow_nuke_sub_bomblet", projectile.getLocation(), projectile.getFacing(), null);
        subProj1.setFromMissile(true);
        GuidedMissileAI subAI1 = (GuidedMissileAI)subProj1.getMissileAI();
        subAI1.setTarget(target);
        MissileAPI subProj2 = (MissileAPI)engine.spawnProjectile(projectile.getSource(), projectile.getWeapon(), "vow_nuke_rift_mine", projectile.getLocation(), projectile.getFacing(), null);
        subProj2.setFromMissile(true);
        GuidedMissileAI subAI2 = (GuidedMissileAI)subProj.getMissileAI();
        subAI2.setTarget(target);

        DamagingExplosionSpec spec = new DamagingExplosionSpec(
                2.5F, 750.0F, 750.0F/2, 3500, 1500,
                CollisionClass.PROJECTILE_FF, CollisionClass.PROJECTILE_FIGHTER, 5.0F, 500.0F, 1.5F, 50,
                PARTICLE_COLOR, EXPLOSION_COLOR
        );

        if (target instanceof ShipAPI && target instanceof FighterWingAPI && Math.random() <= (double)1.0F) {
            engine.applyDamage(target, point, (float)MathUtils.getRandomNumberInRange(400, 750), DamageType.HIGH_EXPLOSIVE, 0.0F, false, false, projectile.getSource());
            engine.spawnDamagingExplosion(spec, projectile.getSource(), point, true);
            float speed = projectile.getVelocity().length();
            float facing = projectile.getFacing();

            for(int i = 0; i <= 5; ++i) {
                float angle = MathUtils.getRandomNumberInRange(facing - 75.0F, facing + 75.0F);
                float vel = MathUtils.getRandomNumberInRange(speed * -0.2F, speed * -0.3F);
                Vector2f vector = MathUtils.getPointOnCircumference(null, vel, angle);
                engine.addHitParticle(point, vector, 7.0F, 255.0F, 1.2F, PARTICLE_COLOR);
            }

            //Global.getSoundPlayer().playSound("bbplus_shockmed_crit", 1.0F, 1.0F, target.getLocation(), target.getVelocity());
        }

        List<ShipAPI> ships = engine.getShips();
        List<MissileAPI> missiles = engine.getMissiles();
        //List<FighterWingAPI> fighters = engine.getShips();
        int sourceOwner = projectile.getSource().getOwner();
        for (ShipAPI ship : ships) {
            if (ship.isAlive() && ship.getOwner() != sourceOwner) {
                float distance = MathUtils.getDistance(ship.getLocation(), point);
                if (distance <= RANGE && distance > 0f) {
                    // 方向向量：从舰船指向命中点
                    Vector2f dir = Vector2f.sub(point, ship.getLocation(), null);
                    dir.normalise();
                    dir.scale(ATTRACT_FORCE);
                    // 将吸引力叠加到舰船速度上
                    Vector2f.add(ship.getVelocity(), dir, ship.getVelocity());
                }
            }
        }
        for (MissileAPI missile : missiles) {
            if(!missile.isFading() && !missile.isExpired() && missile.getOwner() != sourceOwner){
                float distance = MathUtils.getDistance(missile.getLocation(), point);
                if (distance <= RANGE && distance > 0f) {
                    // 方向向量：从舰船指向命中点
                    Vector2f dir = Vector2f.sub(point, missile.getLocation(), null);
                    dir.normalise();
                    dir.scale(ATTRACT_FORCE);
                    // 将吸引力叠加到舰船速度上
                    Vector2f.add(missile.getVelocity(), dir, missile.getVelocity());
                }
            }
        }

        DistortionEntity distortion = new DistortionEntity();
        distortion.setGlobalTimer(0.2f, 0.3f, 0.2f);//渐入/完全亮起/渐出
        distortion.setInnerFull(0.8f, 0.8f);//内部半径（较为清晰的部分）
        distortion.setInnerHardness(0.8f);
        distortion.setSizeIn(RANGE/2f, RANGE/2f);//初始尺寸
        distortion.setPowerIn(0f);//初始扭曲程度
        distortion.setPowerFull(0.5f);//完整扭曲程度
        distortion.setPowerOut(0f);//结束扭曲程度
        distortion.setSizeFull(RANGE/3f, RANGE/3f);//完整尺寸
        distortion.setSizeOut(RANGE/0.5f, RANGE/0.5f);//结束尺寸
        distortion.setLocation(new Vector2f(projectile.getLocation()));
        CombatRenderingManager.addEntity(BoxEnum.ENTITY_DISTORTION, distortion);
        //星云环
        SimpleParticleControlData particle = new SimpleParticleControlData(512, 3.0f, -5120.0f, false);
        SpriteEntity particleEntity = new SpriteEntity("graphics/fx/nebula_colorless.png");
        //particleEntity.getMaterialData().setEmissive(Global.getSettings().getSprite("graphics/fx/fx_clouds01.png"));
        particleEntity.getMaterialData().setColor(new Color(75,125,200,95));//为星云图上颜色
        //particleEntity.getMaterialData().setEmissiveColor(new Color(125,125,255,95));//暂时没用，因为我把上面那行Emissive注释掉了。原理上是再叠一张图
        particleEntity.getMaterialData().setColorAlpha(0.2f);//核心颜色透明度
        //particleEntity.getMaterialData().setEmissiveColorAlpha(0.4f);
        particleEntity.getMaterialData().setColorToEmissive(0.2f);//不知道
        particleEntity.getMaterialData().setAlphaToEmissive(0.0f);//不知道
        particleEntity.getMaterialData().setGlowPower(0.2f);//可能是亮度
        particleEntity.setTileSize(4, 4);//取这种复数素材在一张图里用的
        particleEntity.setBaseSizePerTiles(16.0f, 16.0f);//同上，每个单独素材的大小
        particleEntity.setRandomTile(true);
        particleEntity.setRandomTileEachInstance(true);
        particleEntity.setAdditiveBlend();
        particleEntity.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
        particleEntity.setControlData(particle);
        CombatRenderingManager.addEntity(BoxEnum.ENTITY_SPRITE, particleEntity);

    }
}
