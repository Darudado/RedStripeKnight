package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;

public class Rhongomyniad extends BaseHullMod {
    private boolean loaded = false;
    float basehardflux = 0.05F;
    public static final String MOD_NAME = getString(1);

    public boolean isApplicableToShip(ShipAPI ship) {
        return false;
    }

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getEnergyWeaponDamageMult().modifyMult(id , 1.05f);
        stats.getEnergyProjectileSpeedMult().modifyMult(id , 1.05f);
        stats.getBallisticWeaponDamageMult().modifyMult(id , 1.05f);
        stats.getBallisticProjectileSpeedMult().modifyMult(id , 1.05f);
        stats.getShieldUpkeepMult().modifyMult(id , 1.75f);
    }


    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!Global.getCombatEngine().isPaused() && ship.isAlive()) {
            if (!this.loaded) {
                this.loaded = true;
            }
            float fluxRatio = ship.getFluxTracker().getFluxLevel();
            float fluxRatioRes = 100.0F * fluxRatio;
            float fluxdamBouns = fluxRatioRes / 100;
            float dam = 1.05f+fluxdamBouns*0.25f;
            ship.getMutableStats().getEnergyWeaponDamageMult().modifyMult("Rhongomyniad2" , dam);
            ship.getMutableStats().getBallisticWeaponDamageMult().modifyMult("Rhongomyniad2" , dam);
            //=========================
            // 光束通量穿透逻辑迁移部分
            //=========================
            // 计算通量穿透系数（优化公式）
            float DamBouns = 0.35f * fluxRatio + basehardflux; // fluxRatio本身是0-1，无需乘以100

            ship.getMutableStats().getBallisticWeaponFluxCostMod().modifyMult("Rhongomyniad2" , 1-DamBouns);
            ship.getMutableStats().getEnergyWeaponFluxCostMod().modifyMult("Rhongomyniad2" , 1-DamBouns);

            // 遍历战斗中的所有光束
            //for (BeamAPI beam : Global.getCombatEngine().getBeams()) {
                // 只处理当前舰船发射的光束
                //if (beam.getSource() != ship) continue;

                // 检查光束是否有有效目标
                //CombatEntityAPI target = beam.getDamageTarget();
                //if (!(target instanceof ShipAPI)) continue;

                //ShipAPI beamTarget = (ShipAPI) target;

                // 检测是否命中护盾
                //boolean hitShield = beamTarget.getShield() != null&& beamTarget.getShield().isWithinArc(beam.getTo());
                //if (!hitShield) continue;

                // 计算护盾实际吸收效率
                //float shieldEfficiency = Math.min(1.0f,
                        //beamTarget.getShield().getFluxPerPointOfDamage()
                               // * beamTarget.getMutableStats().getShieldDamageTakenMult().getModifiedValue()
                               // * beamTarget.getMutableStats().getShieldAbsorptionMult().getModifiedValue()
                //);

                // 计算穿透通量值
                //float damageDealt = beam.getDamage().computeDamageDealt(amount);
                //float pierceFlux = damageDealt * (1.0f - shieldEfficiency) * beanfluxBouns;

                // 应用硬通量
                //if (pierceFlux > 0) {
                   // beamTarget.getFluxTracker().increaseFlux(pierceFlux, true);
                //}
           // }
            // 玩家状态显示
            if (ship == Global.getCombatEngine().getPlayerShip() && fluxRatio > 0) {
                String damBonusStr = String.format("%.0f%%", (dam - 1f) * 100f); // 伤害加成百分比
                String fluxPierceStr = String.format("%.0f%%", DamBouns * 100f); // 通量穿透百分比

                Global.getCombatEngine().maintainStatusForPlayerShip(
                        "RhongomyniadStatus",
                        "graphics/icons/hullsys/lidar_barrage.png", // 使用自定义图标
                        MOD_NAME,
                        getString(2)
                                .replace("$damBonus", damBonusStr)
                                .replace("$fluxPierce", fluxPierceStr),
                        false
                );
            }

        }
    }

    private static String getString(int ID) {
        return Global.getSettings().getString("cr_hullmods", String.format("%s_%d", "Rhongomyniad", ID));
    }
}