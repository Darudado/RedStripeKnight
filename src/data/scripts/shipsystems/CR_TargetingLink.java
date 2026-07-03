package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class CR_TargetingLink extends BaseShipSystemScript {

    // ===== 常量定义 =====
    public static final float BASE_FIGHTER_SPEED = 50f;                 // 舰载机基础速度加成（节）
    public static final float BASE_RANGE_BONUS = 20f;                   // 本舰非导弹武器射程加成（百分比）

    // 船插ID
    private static final String MOD_POLARIPHASE = "PolariphaseDrive";
    private static final String MOD_PHASE_DEFENSE = "PhaseDefenseUnit";
    private static final String MOD_WEAPON_OVERLOAD = "WeaponOverLoad";

    // 监听器标记（现在存储监听器实例）
    private static final String RENDER_LISTENER_KEY = "IntegratedShipSystem_RenderListener";

    private final Object STATUSKEY1 = new Object();
    private final Object STATUSKEY2 = new Object();
    private final Object STATUSKEY3 = new Object();
    private final Object STATUSKEY4 = new Object();

    // ===== 系统脚本核心方法 =====
    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;

        // 添加连接线渲染监听器（仅在第一次激活时添加，之后永久存在）
        if (!ship.getCustomData().containsKey(RENDER_LISTENER_KEY)) {
            IntegratedSystemRenderListener listener = new IntegratedSystemRenderListener(ship);
            ship.addListener(listener);
            ship.setCustomData(RENDER_LISTENER_KEY, listener);
        }

        // 获取监听器并设置连线激活状态
        IntegratedSystemRenderListener listener = (IntegratedSystemRenderListener) ship.getCustomData().get(RENDER_LISTENER_KEY);
        if (listener != null) {
            listener.setActive(effectLevel > 0f);
        }

        // 仅在系统激活时应用加成
        if (effectLevel > 0f) {
            // ---- 本舰自身加成 ----
            if (ship.getVariant().hasHullMod(MOD_POLARIPHASE)) {
                stats.getTimeMult().modifyPercent(id + "_time", 50f * effectLevel);      // 本舰时流+50%
            } else if (ship.getVariant().hasHullMod(MOD_PHASE_DEFENSE)) {
                stats.getShieldDamageTakenMult().modifyMult(id + "_shield", 0.75f);      // 护盾减伤+25%
                stats.getFluxDissipation().modifyPercent(id + "_flux", 25f * effectLevel); // 耗散+25%
            } else if (ship.getVariant().hasHullMod(MOD_WEAPON_OVERLOAD)) {
                stats.getBallisticWeaponDamageMult().modifyPercent(id + "_dmg", 25f * effectLevel);
                stats.getEnergyWeaponDamageMult().modifyPercent(id + "_dmg", 25f * effectLevel);
                stats.getBallisticWeaponFluxCostMod().modifyMult(id + "_fluxcost", 0.75f);
                stats.getEnergyWeaponFluxCostMod().modifyMult(id + "_fluxcost", 0.75f);
            } else {
                // 基础效果：非导弹武器射程+20%
                stats.getBallisticWeaponRangeBonus().modifyPercent(id + "_range", BASE_RANGE_BONUS * effectLevel);
                stats.getEnergyWeaponRangeBonus().modifyPercent(id + "_range", BASE_RANGE_BONUS * effectLevel);
            }

            // ---- 舰载机加成 ----
            for (ShipAPI fighter : getFighters(ship)) {
                if (fighter.isHulk()) continue;
                MutableShipStatsAPI fStats = fighter.getMutableStats();
                if (ship.getVariant().hasHullMod(MOD_POLARIPHASE)) {
                    fStats.getTimeMult().modifyPercent(id + "_fighter_time", 100f * effectLevel);
                } else if (ship.getVariant().hasHullMod(MOD_WEAPON_OVERLOAD)) {
                    fStats.getBallisticWeaponDamageMult().modifyPercent(id + "_fighter_dmg", 50f * effectLevel);
                    fStats.getEnergyWeaponDamageMult().modifyPercent(id + "_fighter_dmg", 50f * effectLevel);
                    fStats.getMissileWeaponDamageMult().modifyPercent(id + "_fighter_dmg", 50f * effectLevel);
                    fStats.getBallisticWeaponFluxCostMod().modifyMult(id + "_fighter_flux", 0.5f);
                    fStats.getEnergyWeaponFluxCostMod().modifyMult(id + "_fighter_flux", 0.5f);
                } else if (ship.getVariant().hasHullMod(MOD_PHASE_DEFENSE)) {
                    fStats.getShieldDamageTakenMult().modifyMult(id + "_fighter_hold", 0.5f);
                    fStats.getHullDamageTakenMult().modifyMult(id + "_fighter_hold", 0.5f);
                    fStats.getArmorDamageTakenMult().modifyMult(id + "_fighter_hold", 0.5f);
                } else {
                    fStats.getMaxSpeed().modifyFlat(id + "_fighter_speed", BASE_FIGHTER_SPEED * effectLevel);
                    fStats.getBallisticWeaponDamageMult().modifyPercent(id + "_fighter_dmg", 20f * effectLevel);
                    fStats.getEnergyWeaponDamageMult().modifyPercent(id + "_fighter_dmg", 20f * effectLevel);
                }
            }

            // 玩家舰船状态显示（根据船插显示对应加成，不叠加）
            if (ship == Global.getCombatEngine().getPlayerShip()) {
                if (ship.getVariant().hasHullMod(MOD_POLARIPHASE)) {
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            STATUSKEY1,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "This ship’s time flow +50%",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            STATUSKEY2,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Fighter time flow +100%",
                            false
                    );
                } else if (ship.getVariant().hasHullMod(MOD_PHASE_DEFENSE)) {
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            STATUSKEY1,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Shield damage reduction +25%",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            STATUSKEY2,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Flux dissipation +25%",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            STATUSKEY3,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Fighter damage reduction +50%",
                            false
                    );
                } else if (ship.getVariant().hasHullMod(MOD_WEAPON_OVERLOAD)) {
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            STATUSKEY1,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "This ship's weapon damage +25%",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            STATUSKEY2,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "This ship's weapon flux cost -25%",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            STATUSKEY3,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Fighter weapon damage +50%",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            STATUSKEY4,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Fighter weapon flux cost -50%",
                            false
                    );
                } else {
                    // 无船插：基础效果
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            STATUSKEY1,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "The ship's non-missile weapon range +20%",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            STATUSKEY2,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Fighter speed +50 knots",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            STATUSKEY3,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Fighter weapon damage +20%",
                            false
                    );
                }
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;

        // 获取监听器并关闭连线
        IntegratedSystemRenderListener listener = (IntegratedSystemRenderListener) ship.getCustomData().get(RENDER_LISTENER_KEY);
        if (listener != null) {
            listener.setActive(false);
        }

        // ---- 移除本舰自身加成 ----
        stats.getBallisticWeaponRangeBonus().unmodify(id + "_range");
        stats.getEnergyWeaponRangeBonus().unmodify(id + "_range");
        stats.getTimeMult().unmodify(id + "_time");
        stats.getShieldDamageTakenMult().unmodify(id + "_shield");
        stats.getFluxDissipation().unmodify(id + "_flux");
        stats.getBallisticWeaponDamageMult().unmodify(id + "_dmg");
        stats.getEnergyWeaponDamageMult().unmodify(id + "_dmg");
        stats.getBallisticWeaponFluxCostMod().unmodify(id + "_fluxcost");
        stats.getEnergyWeaponFluxCostMod().unmodify(id + "_fluxcost");

        // ---- 移除舰载机加成 ----
        for (ShipAPI fighter : getFighters(ship)) {
            if (fighter.isHulk()) continue;
            MutableShipStatsAPI fStats = fighter.getMutableStats();
            fStats.getMaxSpeed().unmodify(id + "_fighter_speed");
            fStats.getTimeMult().unmodify(id + "_fighter_time");
            fStats.getBallisticWeaponDamageMult().unmodify(id + "_fighter_dmg");
            fStats.getEnergyWeaponDamageMult().unmodify(id + "_fighter_dmg");
            fStats.getBallisticWeaponFluxCostMod().unmodify(id + "_fighter_flux");
            fStats.getEnergyWeaponFluxCostMod().unmodify(id + "_fighter_flux");
            fStats.getShieldDamageTakenMult().unmodify(id + "_fighter_hold");
            fStats.getHullDamageTakenMult().unmodify(id + "_fighter_hold");
            fStats.getArmorDamageTakenMult().unmodify(id + "_fighter_hold");
        }
    }

    // ===== 辅助方法：获取从本舰放飞的舰载机 =====
    private List<ShipAPI> getFighters(ShipAPI carrier) {
        List<ShipAPI> result = new ArrayList<>();
        for (ShipAPI ship : Global.getCombatEngine().getShips()) {
            if (ship.isFighter() && ship.getWing() != null && ship.getWing().getSourceShip() == carrier) {
                result.add(ship);
            }
        }
        return result;
    }

    // ===== 内部渲染监听器（负责连接线绘制，受 active 标志控制） =====
    private static class IntegratedSystemRenderListener implements AdvanceableListener {
        private final ShipAPI ship;
        private final IntervalUtil timer = new IntervalUtil(0.1f, 0.15f);
        private final Color linkColor = new Color(100, 200, 255, 200);
        private boolean active = false; // 连线是否激活

        public IntegratedSystemRenderListener(ShipAPI ship) {
            this.ship = ship;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        @Override
        public void advance(float amount) {
            // 舰船失效或系统未激活，不绘制连线
            if (ship.isHulk() || !ship.isAlive() || !Global.getCombatEngine().isEntityInPlay(ship) || !active) {
                return;
            }

            timer.advance(amount);
            if (!timer.intervalElapsed()) return;

            // 遍历所有从本舰放飞的舰载机，绘制连接线
            for (ShipAPI fighter : Global.getCombatEngine().getShips()) {
                if (fighter.isFighter() && fighter.getWing() != null && fighter.getWing().getSourceShip() == ship) {
                    RS_TargetingLinkRender.getInstance().addRenderData(
                            ship.getLocation(),
                            fighter.getLocation(),
                            0.3f, 1f, 0.5f,
                            linkColor
                    );
                }
            }
        }
    }
}