package data.hullmods.Installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class CR_Retinere extends BaseHullMod {

    // 常量定义
    private static final float ALLY_RANGE = 1000f;
    private static final float ALLY_BONUS_PER_SHIP = 10f; // 每艘10%
    private static final float MAX_ALLY_BONUS = 50f;
    private static final float COMBAT_BONUS_PER_SECOND = 15f; // 每秒15%
    private static final float MAX_COMBAT_BONUS = 150f;
    private static final float OUT_OF_COMBAT_DELAY = 5f; // 脱战5秒后开始
    private static final float CAPACITY_BONUS_PER_SECOND = 9f; // 每秒9%
    private static final float COMBAT_TIMEOUT = 1f; // 无事件1秒后视为脱战

    // modifier id 常量
    private static final String ALLY_BONUS_ID = "cr_defending_ally";
    private static final String COMBAT_BONUS_ID = "cr_defending_combat";

    // 内部数据类
    private static class DefendingData {
        float lastCombatTime = 0f;          // 最后一次战斗事件时间（受击或开火）
        float combatBonus = 0f;              // 当前战斗耗散加成（百分比）
        float outOfCombatTimer = 0f;          // 脱离战斗计时器（秒）
        boolean wasInCombat = false;          // 上一帧是否在战斗中
    }

    // 伤害监听器：记录受击时间
    private static class CombatDamageListener implements DamageTakenModifier {
        private final ShipAPI ship;

        public CombatDamageListener(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
            if (target == ship) {
                DefendingData data = (DefendingData) ship.getCustomData().get("CR_Retinere_data");
                if (data != null) {
                    data.lastCombatTime = Global.getCombatEngine().getTotalElapsedTime(false);
                }
            }
            return null; // 不修改伤害
        }
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getShieldDamageTakenMult().modifyMult(id, 0.95f);
        stats.getBallisticWeaponFluxCostMod().modifyMult(id ,2.5f);
        stats.getEnergyWeaponFluxCostMod().modifyMult(id ,2.5f);
        stats.getBallisticRoFMult().modifyMult(id , 0.5f);
        stats.getEnergyRoFMult().modifyMult(id , 0.5f);
        stats.getMissileAmmoBonus().modifyMult(id , 0.5f);
        stats.getMissileAmmoRegenMult().modifyMult(id , 0.5f);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 添加伤害监听器（如果尚未添加）
        if (!ship.hasListenerOfClass(CombatDamageListener.class)) {
            ship.addListener(new CombatDamageListener(ship));
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused() || !ship.isAlive()) return;

        // 获取或创建数据
        String dataKey = "CR_Retinere_data";
        DefendingData data = (DefendingData) ship.getCustomData().get(dataKey);
        if (data == null) {
            data = new DefendingData();
            ship.setCustomData(dataKey, data);
        }

        // 1. 友方数量加成（硬辐能耗散）
        updateAllyBonus(ship);

        // 2. 检测战斗状态
        boolean inCombat = isInCombat(ship, engine, data);
        float currentTime = engine.getTotalElapsedTime(false);

        // 3. 战斗耗散加成
        if (inCombat) {
            // 刚进入战斗：重置脱战相关数据
            if (!data.wasInCombat) {
                data.combatBonus = 0f;
                data.outOfCombatTimer = 0f;
            }
            // 累积战斗加成
            data.combatBonus += amount * COMBAT_BONUS_PER_SECOND;
            if (data.combatBonus > MAX_COMBAT_BONUS) data.combatBonus = MAX_COMBAT_BONUS;
            ship.getMutableStats().getFluxDissipation().modifyPercent(COMBAT_BONUS_ID, data.combatBonus);
        } else {
            // 不在战斗：移除战斗加成
            if (data.combatBonus > 0f) {
                ship.getMutableStats().getFluxDissipation().unmodify(COMBAT_BONUS_ID);
                data.combatBonus = 0f;
            }
            // 脱战软辐能增加（过载时暂停）
            if (!ship.getFluxTracker().isOverloaded()) {
                data.outOfCombatTimer += amount;
                if (data.outOfCombatTimer >= OUT_OF_COMBAT_DELAY) {
                    // 每秒增加9%容量的软辐能
                    float fluxToAdd;
                    if(ship.getHullSpec().getHullId().startsWith("rs_")){
                        fluxToAdd = ship.getMaxFlux() * CAPACITY_BONUS_PER_SECOND * 0.005f * amount;
                    }else{
                        fluxToAdd = ship.getMaxFlux() * CAPACITY_BONUS_PER_SECOND * 0.01f * amount;
                    }
                    ship.getFluxTracker().increaseFlux(fluxToAdd, false);
                }
            } else {
                // 过载时计时器暂停（不增加），辐能减少
                float fluxToDec = ship.getMaxFlux() * CAPACITY_BONUS_PER_SECOND * 0.01f * amount;
                ship.getFluxTracker().decreaseFlux(fluxToDec);
            }
        }

        data.wasInCombat = inCombat;
    }

    // 更新友方数量加成
    private void updateAllyBonus(ShipAPI ship) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        int allyCount = 0;
        for (ShipAPI other : engine.getShips()) {
            if (other == ship) continue;
            if (!other.isAlive()) continue;
            if (other.getOwner() != ship.getOwner()) continue;
            if (MathUtils.getDistance(ship.getLocation(), other.getLocation()) <= ALLY_RANGE) {
                allyCount++;
            }
        }
        float bonus = Math.min(allyCount * ALLY_BONUS_PER_SHIP, MAX_ALLY_BONUS);
        if (bonus > 0) {
            ship.getMutableStats().getHardFluxDissipationFraction().modifyFlat(ALLY_BONUS_ID, bonus * 0.01f);
        } else {
            ship.getMutableStats().getHardFluxDissipationFraction().unmodify(ALLY_BONUS_ID);
        }
    }

    // 判断是否在战斗中（1秒内有开火或受击）
    private boolean isInCombat(ShipAPI ship, CombatEngineAPI engine, DefendingData data) {
        float currentTime = engine.getTotalElapsedTime(false);

        // 检查武器开火
        for (WeaponAPI w : ship.getAllWeapons()) {
            if (w.isFiring()) {
                data.lastCombatTime = currentTime;
                break;
            }
        }

        return currentTime - data.lastCombatTime <= COMBAT_TIMEOUT;
    }


    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        float pad = 2f;
        Color h = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        tooltip.addPara("Enhancement kits that provide specific upgrades to ships. Each ship can be equipped with a special enhancement kit.", opad, h);
        tooltip.addSectionHeading("Effect", Alignment.MID, opad);

        tooltip.addPara("Non-missile weapon fire rate %s, flux generation increased by %s.", pad, bad, "halved","150%");
        tooltip.addPara("Missile weapon capacity and ammunition recovery rate %s.", pad, bad, "halved");

        // 护盾减伤
        tooltip.addPara("Shield takes %s less damage.", pad, h, "5%");
        // 友方硬辐能耗散比例加成
        tooltip.addPara("Each friendly ship within %s units increases hard flux dissipation by %s, up to a maximum of %s.", pad, h,
                String.valueOf((int) ALLY_RANGE), "10%", "50%");
        // 战斗耗散速率加成
        tooltip.addPara("Increases the flux dissipation rate by %s per second during combat, up to a maximum of %s.", pad, h,
                (int) COMBAT_BONUS_PER_SECOND + "%", (int) MAX_COMBAT_BONUS + "%");
        // 脱战效果
        tooltip.addPara("After being out of combat for %s seconds, soft flux is dissipated by %s of maximum capacity per second; when overloaded, soft flux is dissipated at the same rate.", pad, h,
                String.valueOf((int) OUT_OF_COMBAT_DELAY), "9%");
        // 战斗状态判定
        tooltip.addPara("Combat status is determined by firing or being attacked and lasts for %s seconds.", pad, h, String.valueOf((int) COMBAT_TIMEOUT));

        tooltip.addPara("Press and hold %s to view detailed mechanics", opad, h, "F3");

        if (Keyboard.isKeyDown(Keyboard.KEY_F3)) {
            tooltip.addSectionHeading("Detailed mechanism", Alignment.MID, opad);
            //tooltip.addPara("硬辐能耗散比例：舰船耗散辐能时，该比例的耗散将用于减少硬辐能。", pad);
            tooltip.addPara("Combat status determination: The ship is considered to be in combat within %s seconds after its weapons fire or receive damage, during which the combat dissipation bonus is accumulated.", pad, h, String.valueOf((int) COMBAT_TIMEOUT));
            tooltip.addPara("Out-of-combat timer: Starts counting after leaving combat, and begins dissipating soft flux after %s seconds. If the ship is overloaded, the timer is paused and soft flux is dissipated directly.", pad, h, String.valueOf((int) OUT_OF_COMBAT_DELAY));
            tooltip.addPara("All bonuses are independently multiplied, and combat dissipation bonuses are removed immediately after leaving combat.", pad);
        }
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null) return false;
        if (ship.getShield() == null) return false;
        if(ship.getVariant().hasHullMod("CR_Votum")) return false;
        if(ship.getVariant().hasHullMod("CR_Circumvenire")) return false;
        if(ship.isStationModule()) return false;
        return ship.getVariant().hasHullMod("CrusadersCore");
    }

    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) return "ship does not exist";
        if (ship.getShield() == null) return "The ship has no shields";
        if(ship.getVariant().hasHullMod("CR_Circumvenire")) return "Ship systems have been overwritten";
        if(ship.getVariant().hasHullMod("CR_Votum")) return "Ship systems have been overwritten";
        if (!ship.getVariant().hasHullMod("CrusadersCore")) return "Requires Crusader Core";
        if(ship.isStationModule()) return "Cannot be installed on ship modules";
        return null;
    }

}