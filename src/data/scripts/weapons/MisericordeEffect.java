//By Tartiflette
package data.scripts.weapons;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import data.scripts.utils.MathPersonal;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class MisericordeEffect implements EveryFrameWeaponEffectPlugin , OnHitEffectPlugin {
        
    private float delay = 0.1f;
    private float timer = 0;
    private float SPINUP = 0.02f;
    private float SPINDOWN = 10f;
    
    private boolean runOnce=false;
    private boolean hidden=false;
    private AnimationAPI theAnim;
    private int maxFrame;
    private int frame;

    float ex_dam = MathPersonal.randBetween(5,25);
    
    @Override
    public void advance (float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        
        if(engine.isPaused()){return;}
        
        if(!runOnce){
            runOnce=true;
            if(weapon.getSlot().isHidden()){
                hidden=true;
            } else {
                theAnim=weapon.getAnimation();
                maxFrame=theAnim.getNumFrames();
                frame=MathUtils.getRandomNumberInRange(0, maxFrame-1);
            }               
            SPINUP=0.03f;
            SPINDOWN=7.5f;
        }
        
        timer+=amount;
        if (timer >= delay){
            timer-=delay;
            if (weapon.getChargeLevel()>0){
                delay = Math.max(
                            delay - SPINUP,
                            0.02f
                        );
            } else {
                delay = Math.min(
                            delay + delay/SPINDOWN,
                            0.1f
                        );
            }
            if (!hidden && delay!=0.1f){
                frame++;
                if (frame==maxFrame){
                    frame=0;
                }
            }
        }
        
        //play the spinning sound
        if (weapon.getChargeLevel()>0){       
            
            Global.getSoundPlayer().playLoop(
                    "Misericorde_fire",
                    weapon,
                    1,
                    Math.max(0,10*weapon.getChargeLevel()-9),
                    weapon.getLocation(),
                    weapon.getShip().getVelocity()
            );
            
            Global.getSoundPlayer().playLoop(
                    "Misericorde_spin",
                    weapon,
                    0.25f+ weapon.getChargeLevel(),
                    0.25f,
//                    0.5f+0.5f*weapon.getChargeLevel(),
                    weapon.getLocation(),
                    weapon.getShip().getVelocity()
            );            
        }
        
        if (!hidden){            
            theAnim.setFrame(frame);
        }
    }

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        Object source = projectile.getSource();
        engine.applyDamage(target ,point , ex_dam , DamageType.HIGH_EXPLOSIVE , 15 , false ,false ,source);

    }
}
