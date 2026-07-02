package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;

public class PennaVelox extends BaseHullMod {
    private boolean loaded = false;
    public static final String MOD_NAME = getString(1);

    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        float fluxRatio = ship.getFluxTracker().getFluxLevel(); // 当前幅能比例 (0.0~1.0)
        float speedBonu =1f + 0.25f*fluxRatio;
        float timeBonu =1.1f + 0.25f*fluxRatio;
        float dam = 1f + 0.25f*fluxRatio;
        fighter.getMutableStats().getMaxSpeed().modifyMult(id, speedBonu);
        fighter.getMutableStats().getAcceleration().modifyMult(id, speedBonu);
        fighter.getMutableStats().getDeceleration().modifyMult(id, speedBonu);
        fighter.getMutableStats().getMaxTurnRate().modifyMult(id, speedBonu);
        fighter.getMutableStats().getTurnAcceleration().modifyMult(id, speedBonu);
        fighter.getMutableStats().getTimeMult().modifyMult(id ,timeBonu);
        fighter.getMutableStats().getDamageToFighters().modifyMult(id ,dam);
        fighter.getMutableStats().getDamageToCruisers().modifyMult(id ,dam);
        fighter.getMutableStats().getDamageToDestroyers().modifyMult(id ,dam);
        fighter.getMutableStats().getHullBonus().modifyMult(id, 0.5F);
        fighter.getMutableStats().getArmorDamageTakenMult().modifyMult(id, 1.25F);
        fighter.getMutableStats().getShieldDamageTakenMult().modifyMult(id, 1.25F);
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!Global.getCombatEngine().isPaused() && ship.isAlive()) {
            if (!this.loaded) {
                this.loaded = true;
            }
            float fluxRatio = ship.getFluxTracker().getFluxLevel(); // 当前幅能比例 (0.0~1.0)
            float replaceBonu =1f + 1.25f*fluxRatio;
            ship.getMutableStats().getFighterRefitTimeMult().modifyMult("PennaVelox2", replaceBonu);
            float rangeBonu =1f + 0.5f*fluxRatio;
            ship.getMutableStats().getFighterWingRange().modifyMult("PennaVelox2", rangeBonu);

            float speedBonu =1f + 0.25f*fluxRatio;
            float timeBonu =1.1f + 0.25f*fluxRatio;
            float dam = 1f + 0.25f*fluxRatio;

            // ==================== 新增UI状态显示 ====================
            if (ship == Global.getCombatEngine().getPlayerShip() && fluxRatio > 0.0F) {
                // 计算显示值（百分比形式）
                int speedPercent = Math.round((speedBonu - 1f) * 100f);        // 速度加成百分比
                int timePercent = Math.round((timeBonu - 1f) * 100f);          // 时间流速加成百分比
                int damagePercent = Math.round((dam - 1f) * 100f);             // 伤害加成百分比
                int replacePercent = Math.round((replaceBonu - 1f) * 100f);    // 整备时间增加百分比
                int rangePercent = Math.round((rangeBonu - 1f) * 100f);        // 航程加成百分比

                // 创建状态提示文本
                String statusText = getString(2)
                        .replace("$speed", String.valueOf(speedPercent))
                        .replace("$time", String.valueOf(timePercent))
                        .replace("$damage", String.valueOf(damagePercent));

                // 显示状态提示（使用战斗机图标）
                Global.getCombatEngine().maintainStatusForPlayerShip(
                        "PennaVeloxStatus1",           // 状态ID（确保唯一）
                        "graphics/icons/hullsys/targeting_feed.png",  // 图标路径
                        MOD_NAME,                     // 标题
                        statusText,                   // 格式化后的文本
                        false                         // 不是关键状态（不闪烁）
                );

                String statusText2 = getString(3)
                        .replace("$replace", String.valueOf(replacePercent))
                        .replace("$range", String.valueOf(rangePercent));

                Global.getCombatEngine().maintainStatusForPlayerShip(
                        "PennaVeloxStatus2",           // 状态ID（确保唯一）
                        "graphics/icons/hullsys/targeting_feed.png",  // 图标路径
                        MOD_NAME,                     // 标题
                        statusText2,                   // 格式化后的文本
                        false                         // 不是关键状态（不闪烁）
                );
            }
        }
    }

    private static String getString(int ID) {
        return Global.getSettings().getString("cr_hullmods", String.format("%s_%d", "PennaVelox", ID));
    }
}