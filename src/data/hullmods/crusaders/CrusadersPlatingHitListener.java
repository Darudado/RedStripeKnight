package data.hullmods.crusaders;

import java.awt.Color;

import com.fs.starfarer.api.combat.*;
import org.lazywizard.lazylib.combat.DefenseUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;

class CrusadersPlatingHitListener implements DamageTakenModifier {
    public final ShipAPI ship;
    
    public CrusadersPlatingHitListener(ShipAPI ship) {
    	this.ship = ship;
    }
    
    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
    	if (!ship.isAlive()) {
    		ship.removeListener(this);
    		return null;
    	}
        if (!ship.isFighter() && !shieldHit) {
        	var provider = (CrusadersRequestProvider) ship.getCustomData().computeIfAbsent(
        		CrusadersPlating.PROVIDER_KEY, k->new CrusadersRequestProvider(ship)
	        );
        	if (damage.isDps() && param instanceof BeamAPI beam) 
        		provider.putPoint(point, CrusadersPlateRenderer.DEFAULT_RIPPLE_SIZE, false, beam);
        	else 
        		provider.putPoint(point, CrusadersPlateRenderer.DEFAULT_RIPPLE_SIZE, false, null);
        }

        if (!shieldHit && ship.getCustomData().get("crusaders_currenthpplating") != null && !(Boolean) ship.getCustomData().get("crusaders_shutdown")) {
            ship.setCustomData("crusaders_recentlydamaged", true);
            ship.setCustomData("crusaders_lastdamagedtime", Global.getCombatEngine().getTotalElapsedTime(false));

            float currenthp = (Float) ship.getCustomData().get("crusaders_currenthpplating");
            if (currenthp <= 0) return null;

            float rawDamage = damage.getDamage();

            // 1. 计算原版的 damageamount（类型/阈值预处理）
            float damageamount;
            if (damage.isDps()) {
                damageamount = (rawDamage > 750f) ? rawDamage * 0.5f * 0.75f : rawDamage * 0.75f;
            } else if (damage.getType().equals(DamageType.FRAGMENTATION)) {
                damageamount = (rawDamage > 750f) ? rawDamage * 0.5f * 0.65f : rawDamage * 0.65f;
            } else if (damage.getType().equals(DamageType.HIGH_EXPLOSIVE)) {
                damageamount = (rawDamage > 750f) ? rawDamage * 0.5f * 0.9f : rawDamage * 0.9f;
            } else {
                damageamount = (rawDamage > 750f) ? rawDamage * 0.5f : rawDamage;
            }

            // 2. 计算装甲/辐能加成（完全保持原样）
            float armorLevel = DefenseUtils.getArmorLevel(ship, point);
            float minArmorFraction = ship.getMutableStats().getMinArmorFraction().getModifiedValue();
            float fluxCap = ship.getMutableStats().getFluxCapacity().getModifiedValue();
            float fluxFactor;
            if (fluxCap <= 30000f) {
                fluxFactor = fluxCap / 6f;
            } else {
                // 30000 时基础值 5000，超出部分以平方根递减
                float excess = fluxCap - 30000f;
                fluxFactor = 5000f + (float)Math.sqrt(excess) * 2f; // 系数可调
            }
            float armorBonus = fluxFactor * Math.max(armorLevel * 1.1f, minArmorFraction * 1.25f);

            // 3. 原版伤害乘数 —— 直接使用，保证减伤效率一致
            float damagemult = damageamount / (currenthp + armorBonus + damageamount);
            damage.getModifier().modifyMult("crusaders_neutroniumplating", damagemult);

            // 4. 护盾扣除，限制在“实际减免伤害”范围内，消除放大
            float absorbedByShield = rawDamage * (1f - damagemult);         // 船体实际减免的伤害
            float originalDeduct = damagemult * damageamount;               // 原版扣除量
            float actualDeduct = Math.min(originalDeduct, absorbedByShield);
            float newHP = Math.max(currenthp - actualDeduct, 0);
            ship.setCustomData("crusaders_currenthpplating", newHP);

            // 5. 视觉效果和音效保留原逻辑（可使用 actualDeduct 或 damageamount 作为强度参考）
            float maxhp = (Float) ship.getCustomData().get("crusaders_maxhpplating");
            Color colorDamaged = CrusadersPlating.getDamagedShieldColor(newHP, maxhp);
            if (damage.isDps()) {
                ship.setCustomData("crusaders_damagedbybeam", true);
                CrusadersPlating.applyArmorGlow(ship, colorDamaged,
                        Math.min(Math.max(damageamount / 3500f, 0.0015f), 0.05f), 1, 0.1f, 0.1f);
            } else {
                if (newHP > 0) {
                    CrusadersPlating.applyArmorGlow(ship, colorDamaged,
                            Math.max(damageamount / 5000f, 0.025f), 1, 0.1f, 0.1f);
                }
                Global.getSoundPlayer().playSound("cr_phaseplatinghit", 1f,
                        Math.max(damageamount / 300f, 0.5f), ship.getLocation(), ship.getVelocity());
            }


        }
        return null;
    }
}