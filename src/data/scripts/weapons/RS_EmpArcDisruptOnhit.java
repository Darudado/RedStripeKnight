package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.util.Misc;
import org.boxutil.base.SimpleParticleControlData;
import org.boxutil.define.BoxEnum;
import org.boxutil.manager.CombatRenderingManager;
import org.boxutil.units.standard.attribute.Instance2Data;
import org.boxutil.units.standard.entity.DistortionEntity;
import org.boxutil.units.standard.entity.FlareEntity;
import org.boxutil.units.standard.entity.SpriteEntity;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class RS_EmpArcDisruptOnhit implements OnHitEffectPlugin {

    protected static final float RATIO = 0.02f;
    protected static final int CORE_NEBULA_COUNT = 20;
    protected static final float RANGE = 125f;//星云粒子分布半径
    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if(target instanceof ShipAPI){
            Color COLOR1 = null;
            Color COLOR2 = null;
            Color COLOR3 = null;
            if(!(projectile.getProjectileSpec() ==null)) {
                if (!(projectile.getProjectileSpec().getCoreColor() == null)) {
                    COLOR1 = projectile.getProjectileSpec().getCoreColor();
                }
            }else{
                COLOR1 = new Color(75,100,225,255);
            }
            if(!(projectile.getProjectileSpec() ==null)) {
                if (!(projectile.getProjectileSpec().getFringeColor() == null)) {
                    COLOR2 = projectile.getProjectileSpec().getFringeColor();
                }
            }else{
                COLOR2 = new Color(90,115,255,225);
            }
            if(!(projectile.getProjectileSpec() ==null)) {
                if (!(projectile.getProjectileSpec().getGlowColor() == null)) {
                    COLOR3 = projectile.getProjectileSpec().getGlowColor();
                }
            }else{
                COLOR3 = new Color(75,125,230,115);
            }


            engine.spawnEmpArcPierceShields(projectile.getSource(), point, target, target,
                    DamageType.ENERGY,
                    projectile.getDamageAmount()*0.67f,
                    projectile.getDamageAmount(), // emp
                    100000f, // max range
                    "tachyon_lance_emp_impact",
                    20f, // thickness
                    COLOR1,
                    COLOR2
            );

            engine.spawnEmpArc(projectile.getSource(), point, target, target,
                    DamageType.ENERGY,
                    projectile.getDamageAmount()*0.67f,
                    projectile.getDamageAmount(), // emp
                    100000f, // max range
                    "tachyon_lance_emp_impact",
                    20f, // thickness
                    COLOR1,
                    COLOR2
            );
            engine.spawnEmpArc(projectile.getSource(), point, target, target,
                    DamageType.ENERGY,
                    projectile.getDamageAmount()*0.67f,
                    projectile.getDamageAmount(), // emp
                    100000f, // max range
                    "tachyon_lance_emp_impact",
                    20f, // thickness
                    COLOR1,
                    COLOR2
            );
            engine.spawnEmpArc(projectile.getSource(), point, target, target,
                    DamageType.ENERGY,
                    projectile.getDamageAmount()*0.67f,
                    projectile.getDamageAmount(), // emp
                    100000f, // max range
                    "tachyon_lance_emp_impact",
                    20f, // thickness
                    COLOR1,
                    COLOR2
            );
            engine.spawnEmpArc(projectile.getSource(), point, target, target,
                    DamageType.ENERGY,
                    projectile.getDamageAmount()*0.67f,
                    projectile.getDamageAmount(), // emp
                    100000f, // max range
                    "tachyon_lance_emp_impact",
                    20f, // thickness
                    COLOR1,
                    COLOR2
            );
            engine.spawnEmpArc(projectile.getSource(), point, target, target,
                    DamageType.ENERGY,
                    projectile.getDamageAmount()*0.67f,
                    projectile.getDamageAmount(), // emp
                    100000f, // max range
                    "tachyon_lance_emp_impact",
                    20f, // thickness
                    COLOR1,
                    COLOR2
            );
            engine.spawnEmpArc(projectile.getSource(), point, target, target,
                    DamageType.ENERGY,
                    projectile.getDamageAmount()*0.67f,
                    projectile.getDamageAmount(), // emp
                    100000f, // max range
                    "tachyon_lance_emp_impact",
                    20f, // thickness
                    COLOR1,
                    COLOR2
            );
            engine.spawnEmpArc(projectile.getSource(), point, target, target,
                    DamageType.ENERGY,
                    projectile.getDamageAmount()*0.67f,
                    projectile.getDamageAmount(), // emp
                    100000f, // max range
                    "tachyon_lance_emp_impact",
                    20f, // thickness
                    COLOR1,
                    COLOR2
            );
            engine.spawnEmpArc(projectile.getSource(), point, target, target,
                    DamageType.ENERGY,
                    projectile.getDamageAmount()*0.67f,
                    projectile.getDamageAmount(), // emp
                    100000f, // max range
                    "tachyon_lance_emp_impact",
                    20f, // thickness
                    COLOR1,
                    COLOR2
            );

            //----------中心亮斑
            FlareEntity mainFlare = new FlareEntity();
            mainFlare.setLocation(projectile.getLocation());
            mainFlare.setCoreColor(COLOR1);//光斑中心颜色
            mainFlare.setSize(100f,15f);//光斑长宽
            mainFlare.setFlick(true);//是否闪烁
            mainFlare.setNoisePower(0.3f);//噪声强度，推测是闪烁/模糊之类的参数
            mainFlare.setGlobalTimer(0.2f,0.3f,0.2f);//渐入/完全亮起/渐出
            mainFlare.setLayer(CombatEngineLayers.ABOVE_SHIPS_LAYER);
            CombatRenderingManager.addEntity(BoxEnum.ENTITY_FLARE,mainFlare);

            mainFlare = new FlareEntity();
            mainFlare.setLocation(projectile.getLocation());
            mainFlare.setCoreColor(COLOR2);//光斑中心颜色
            mainFlare.setSize(15f,80f);//光斑长宽
            mainFlare.setFlick(true);//是否闪烁
            mainFlare.setNoisePower(0.3f);//噪声强度，推测是闪烁/模糊之类的参数
            mainFlare.setGlobalTimer(0.2f,0.3f,0.2f);//渐入/完全亮起/渐出
            mainFlare.setLayer(CombatEngineLayers.ABOVE_SHIPS_LAYER);
            CombatRenderingManager.addEntity(BoxEnum.ENTITY_FLARE,mainFlare);



            //扭曲效果
            DistortionEntity distortion = new DistortionEntity();
            distortion.setGlobalTimer(0.2f, 0.3f, 0.2f);//渐入/完全亮起/渐出
            distortion.setInnerFull(0.8f, 0.8f);//内部半径（较为清晰的部分）
            distortion.setInnerHardness(0.8f);
            distortion.setSizeIn(RANGE/2f, RANGE/2f);//初始尺寸
            distortion.setPowerIn(0f);//初始扭曲程度
            distortion.setPowerFull(0.5f);//完整扭曲程度
            distortion.setPowerOut(0f);//结束扭曲程度
            distortion.setSizeFull(RANGE/2f, RANGE/2f);//完整尺寸
            distortion.setSizeOut(RANGE/0.5f, RANGE/0.5f);//结束尺寸
            distortion.setLocation(new Vector2f(projectile.getLocation()));
            CombatRenderingManager.addEntity(BoxEnum.ENTITY_DISTORTION, distortion);
            //星云环
            SimpleParticleControlData particle = new SimpleParticleControlData(512, 3.0f, -5120.0f, false);
            SpriteEntity particleEntity = new SpriteEntity("graphics/fx/soot64.png");
            //particleEntity.getMaterialData().setEmissive(Global.getSettings().getSprite("graphics/fx/fx_clouds01.png"));
            particleEntity.getMaterialData().setColor(COLOR3);//为星云图上颜色
            //particleEntity.getMaterialData().setEmissiveColor(new Color(125,125,255,95));//暂时没用，因为我把上面那行Emissive注释掉了。原理上是再叠一张图
            particleEntity.getMaterialData().setColorAlpha(0.3f);//核心颜色透明度
            //particleEntity.getMaterialData().setEmissiveColorAlpha(0.4f);
            particleEntity.getMaterialData().setColorToEmissive(0.25f);//不知道
            particleEntity.getMaterialData().setAlphaToEmissive(0.1f);//不知道
            particleEntity.getMaterialData().setGlowPower(0.2f);//可能是亮度
            particleEntity.setTileSize(4, 4);//取这种复数素材在一张图里用的
            particleEntity.setBaseSizePerTiles(16.0f, 16.0f);//同上，每个单独素材的大小
            particleEntity.setRandomTile(true);
            particleEntity.setRandomTileEachInstance(true);
            particleEntity.setAdditiveBlend();
            particleEntity.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
            particleEntity.setControlData(particle);
            CombatRenderingManager.addEntity(BoxEnum.ENTITY_SPRITE, particleEntity);
            //---------内部星云----------
            //在中心圆范围内取点，即参数2
            for(int i = 0;i<CORE_NEBULA_COUNT;i++){
                Instance2Data addedParticle = particle.addParticle();
                if(addedParticle!=null){
                    addedParticle.setLocation(Misc.getPointWithinRadius(projectile.getLocation(),RANGE));
                    addedParticle.setScaleAll(1.0f + (float) Math.random() * 0.5f);
                    addedParticle.setScaleRateAll(2.0f);
                    addedParticle.setLowColor(new Color(75,125,200,95));
                    addedParticle.setAlpha((float) Math.random() * 0.2f + 0.2f);
                    addedParticle.setVelocity((float) Math.random() * 16f - 8f, (float) Math.random() * 16f - 8f);
                    addedParticle.setTimer(0.4f, 0.8f, 0.7f);
                }
            }

            ((ShipAPI) target).getFluxTracker().increaseFlux(((ShipAPI) target).getMaxFlux()*RATIO,false);
        }
    }
}
