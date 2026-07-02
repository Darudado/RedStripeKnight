//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.boxutil.define.BoxEnum;
import org.boxutil.manager.CombatRenderingManager;
import org.boxutil.units.standard.entity.FlareEntity;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class VOW_NukeSubOnhit implements OnHitEffectPlugin {
    public VOW_NukeSubOnhit() {
    }

    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        MissileAPI subProj = (MissileAPI)engine.spawnProjectile(projectile.getSource(), projectile.getWeapon(), "vow_nuke_rift_mine", projectile.getLocation(), projectile.getFacing(), null);
        subProj.setFromMissile(true);
        GuidedMissileAI subAI = (GuidedMissileAI)subProj.getMissileAI();
        subAI.setTarget(target);

        MissileAPI subProj1 = (MissileAPI)engine.spawnProjectile(projectile.getSource(), projectile.getWeapon(), "vow_nuke_rift_mine", projectile.getLocation(), projectile.getFacing(), null);
        subProj1.setFromMissile(true);
        GuidedMissileAI subAI1 = (GuidedMissileAI)subProj.getMissileAI();
        subAI1.setTarget(target);

        FlareEntity mainFlare = new FlareEntity();
        mainFlare.setLocation(projectile.getLocation());
        mainFlare.setCoreColor(new Color(125, 146, 255,180));//光斑中心颜色
        mainFlare.setSize(100f,15f);//光斑长宽
        mainFlare.setFlick(true);//是否闪烁
        mainFlare.setNoisePower(0.3f);//噪声强度，推测是闪烁/模糊之类的参数
        mainFlare.setGlobalTimer(0.2f,0.3f,0.2f);//渐入/完全亮起/渐出
        mainFlare.setLayer(CombatEngineLayers.ABOVE_SHIPS_LAYER);
        CombatRenderingManager.addEntity(BoxEnum.ENTITY_FLARE,mainFlare);

        mainFlare = new FlareEntity();
        mainFlare.setLocation(projectile.getLocation());
        mainFlare.setCoreColor(new Color(125, 146, 255,180));//光斑中心颜色
        mainFlare.setSize(15f,80f);//光斑长宽
        mainFlare.setFlick(true);//是否闪烁
        mainFlare.setNoisePower(0.3f);//噪声强度，推测是闪烁/模糊之类的参数
        mainFlare.setGlobalTimer(0.2f,0.3f,0.2f);//渐入/完全亮起/渐出
        mainFlare.setLayer(CombatEngineLayers.ABOVE_SHIPS_LAYER);
        CombatRenderingManager.addEntity(BoxEnum.ENTITY_FLARE,mainFlare);

    }
}
