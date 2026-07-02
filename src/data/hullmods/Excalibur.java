package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;

public class Excalibur extends BaseHullMod {
    private boolean loaded = false;
    public static final String MOD_NAME = getString(1);

    public boolean isApplicableToShip(ShipAPI ship) {
        return false;
    }

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getEmpDamageTakenMult().modifyMult(id, 1.25f);
        stats.getZeroFluxSpeedBoost().modifyFlat(id, 25f);
        stats.getHullDamageTakenMult().modifyMult(id, 1.25f);
        stats.getArmorDamageTakenMult().modifyMult(id, 1.25f);
        stats.getShieldDamageTakenMult().modifyMult(id, 1.35f);
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!Global.getCombatEngine().isPaused() && ship.isAlive()) {
                if (!this.loaded) {
                    this.loaded = true;
                }
            float fluxRatio = ship.getFluxTracker().getFluxLevel(); // 当前幅能比例 (0.0~1.0)

            // 1. 时间流速：100%辐能时1.50倍
            float timeMult = 1f + fluxRatio*0.50f; // 线性增加：0%辐能=1倍，100%辐能=1.50倍
            ship.getMutableStats().getTimeMult().modifyMult("Excalibur2", timeMult);

            // 2. 最高航速：100%辐能时1.35倍
            float speedMult = 1f + 0.25f * fluxRatio; // 0%辐能=1倍，100%辐能=1.25倍
            ship.getMutableStats().getMaxSpeed().modifyMult("Excalibur2", speedMult);

            // 3. 机动性：100%辐能时1.25倍（+25%）
            float maneuverBonus = 25f * fluxRatio; // 0%辐能=0%，100%辐能=+25%
            ship.getMutableStats().getAcceleration().modifyPercent("Excalibur2", maneuverBonus);
            ship.getMutableStats().getDeceleration().modifyPercent("Excalibur2", maneuverBonus);
            ship.getMutableStats().getTurnAcceleration().modifyPercent("Excalibur2", maneuverBonus);
            ship.getMutableStats().getMaxTurnRate().modifyPercent("Excalibur2", maneuverBonus);

            // 4. 玩家状态提示
            if (ship == Global.getCombatEngine().getPlayerShip() && fluxRatio > 0.0F) {
                // 计算显示值（百分比形式）
                int timePercent = Math.round((ship.getMutableStats().getTimeMult().getMult() - 1f) * 100f);
                int speedPercent = Math.round((speedMult - 1f) * 100f);
                int maneuverPercent = Math.round(maneuverBonus);

                Global.getCombatEngine().maintainStatusForPlayerShip(
                        "Excalibur",
                        "graphics/icons/hullsys/infernium_injector.png",
                        MOD_NAME,
                        getString(2)
                                .replace("$time", String.valueOf(timePercent))
                                .replace("$speed", String.valueOf(speedPercent))
                                .replace("$maneuver", String.valueOf(maneuverPercent)),
                        false
                );
            }
        }
    }
    private static String getString(int ID) {
        return Global.getSettings().getString("cr_hullmods", String.format("%s_%d", "Excalibur", ID));
    }
}
