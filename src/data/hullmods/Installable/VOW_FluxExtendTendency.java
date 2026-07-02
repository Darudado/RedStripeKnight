package data.hullmods.Installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;
import java.util.List;

/**
 * 辐能延滞 (修复版)
 * 效果：
 * - 所有武器辐能消耗变为原消耗的 1/n 倍 (硬辐能)
 * - 每秒记录武器开火产生的总辐能 (已修正)，并在接下来的 n 秒内均匀施加等量的软辐能
 * - 舰船过载或强排时不增加软辐能
 * 总辐能 = 立即支付硬辐能 + 债务软辐能 = 原始消耗 (守恒)
 */
public class VOW_FluxExtendTendency extends BaseHullMod {
    private static final float DELAY_SECONDS = 5.0f;
    public float SMOD_BONUS = 30f;
    public float BONUS = 25f;

    // 记录非光束武器上一帧的冷却剩余时间，用于检测弹药发射
    private final Map<WeaponAPI, Float> cooldownRemainingMap = new HashMap<>();
    // 用于冷却为0的武器退化为上升沿检测
    private final Map<WeaponAPI, Boolean> lastFiringMap = new HashMap<>();

    private float currentSecondTotal = 0f;
    private float secondTimer = 0f;
    private final List<FluxDebt> debts = new ArrayList<>();

    private static class FluxDebt {
        float totalFlux;
        float remainingSeconds;
        FluxDebt(float totalFlux, float remainingSeconds) {
            this.totalFlux = totalFlux;
            this.remainingSeconds = remainingSeconds;
        }
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // 所有武器硬辐能消耗变为原始的 1/DELAY_SECONDS (游戏自动扣除)
        stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f / DELAY_SECONDS);
        stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1f / DELAY_SECONDS);
        stats.getMissileWeaponFluxCostMod().modifyMult(id, 1f / DELAY_SECONDS);

        stats.getVentRateMult().modifyPercent(id, BONUS);
        stats.getHullBonus().modifyPercent(id, BONUS-20f);
        stats.getFluxDissipation().modifyPercent(id, -BONUS);

        if (stats.getVariant().hasHullMod("VOW_DesignSystem")) {
            stats.getFluxCapacity().modifyPercent(id, 10f);
            stats.getHullBonus().modifyPercent(id, BONUS-10f);
        }

        boolean sMod = isSMod(stats);
        if (sMod) {
            stats.getVentRateMult().modifyPercent(id, SMOD_BONUS);
            stats.getHullBonus().modifyPercent(id, SMOD_BONUS / 2);
            stats.getFluxCapacity().modifyPercent(id, SMOD_BONUS / 2);
        }

        // 以下兼容性检查留空，无实质操作
        if (stats.getVariant().hasHullMod("PolariphaseDrive")) {
        } else if (stats.getVariant().hasHullMod("PhaseDefenseUnit")) {
        } else if (stats.getVariant().hasHullMod("WeaponOverLoad")) {
        }

        if (stats.getVariant().hasHullMod("CR_Retinere")) {
        } else if (stats.getVariant().hasHullMod("CR_Votum")) {
        } else if (stats.getVariant().hasHullMod("CR_Circumvenire")) {
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (Global.getCombatEngine().isPaused() || !ship.isAlive()) return;

        MutableShipStatsAPI stats = ship.getMutableStats();
        List<WeaponAPI> weapons = ship.getUsableWeapons();
        Set<WeaponAPI> currentWeapons = new HashSet<>(weapons);

        float frameFlux = 0f; // 本帧累计的修正后消耗 (债务基准)

        for (WeaponAPI weapon : weapons) {
            if (weapon.isBeam()) {
                // 光束武器：按时间累计修正后的每秒消耗
                if (weapon.isFiring()) {
                    float baseFluxPerSec = weapon.getFluxCostToFire(); // 基础每秒消耗
                    float modifier = getWeaponFluxCostModifier(stats, weapon.getType());
                    frameFlux += baseFluxPerSec * modifier * amount;
                }
            } else {
                // 非光束武器：检测每发弹药发射
                boolean firedThisFrame = false;

                // 获取武器冷却参数
                float maxCooldown = weapon.getCooldown();
                float currentCooldown = weapon.getCooldownRemaining();

                if (maxCooldown > 0f) {
                    // 有冷却的武器：冷却时间重置表示刚发射了一发
                    Float prevCooldown = cooldownRemainingMap.get(weapon);
                    if (prevCooldown != null && currentCooldown > prevCooldown && currentCooldown > 0f) {
                        firedThisFrame = true;
                    }
                    cooldownRemainingMap.put(weapon, currentCooldown);
                } else {
                    // 无冷却武器 (罕见)：退化为上升沿检测
                    boolean firingNow = weapon.isFiring();
                    Boolean wasFiring = lastFiringMap.get(weapon);
                    if (wasFiring != null && !wasFiring && firingNow) {
                        firedThisFrame = true;
                    }
                    lastFiringMap.put(weapon, firingNow);
                }

                if (firedThisFrame) {
                    float baseCostPerShot = weapon.getFluxCostToFire(); // 基础每发消耗
                    float modifier = getWeaponFluxCostModifier(stats, weapon.getType());
                    frameFlux += baseCostPerShot * modifier;
                }
            }
        }

        // 清理不存在的武器
        cooldownRemainingMap.keySet().removeIf(w -> !currentWeapons.contains(w));
        lastFiringMap.keySet().removeIf(w -> !currentWeapons.contains(w));

        // 累计本帧辐能到当前秒
        currentSecondTotal += frameFlux;
        secondTimer += amount;

        // 每秒生成债务：债务 = 本秒修正消耗 × (DELAY_SECONDS - 1)
        while (secondTimer >= 1.0f) {
            secondTimer -= 1.0f;
            if (currentSecondTotal > 0) {
                float debtTotal = currentSecondTotal * (DELAY_SECONDS - 1);
                debts.add(new FluxDebt(debtTotal, DELAY_SECONDS));
            }
            currentSecondTotal = 0f;
        }

        // 施加软辐能债务（过载/排散时暂停）
        if (!ship.getFluxTracker().isOverloadedOrVenting()) {
            Iterator<FluxDebt> iter = debts.iterator();
            while (iter.hasNext()) {
                FluxDebt debt = iter.next();
                float fluxThisFrame = debt.totalFlux * (amount / DELAY_SECONDS);
                ship.getFluxTracker().increaseFlux(fluxThisFrame, false);
                debt.remainingSeconds -= amount;
                if (debt.remainingSeconds <= 0f) {
                    iter.remove();
                }
            }
        }
    }

    /**
     * 根据武器类型获取当前舰船统计中该类型武器的辐能消耗总修正系数
     */
    private float getWeaponFluxCostModifier(MutableShipStatsAPI stats, WeaponAPI.WeaponType type) {
        switch (type) {
            case BALLISTIC:
                return 0.2f;
            case ENERGY:
                return 0.2f;
            case MISSILE:
                return 0.2f;
            default:
                return 0.2f;
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return (int) BONUS + "%";
        if (index == 1) return (int) BONUS-20f + "%";
        if (index == 2) return (int) BONUS + "%";
        return null;
    }

    public String getSModDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return SMOD_BONUS + "%";
        if (index == 1) return SMOD_BONUS / 2 + "%";
        if (index == 2) return SMOD_BONUS / 2 + "%";
        return null;
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float pads = 10f;
        float pad = 2f;
        tooltip.addPara("The ship is equipped with a power grid system designed to adapt to high-radiation weapons.", Misc.getHighlightColor(), pads);
        tooltip.addSectionHeading("Energy consumption adjustment", Alignment.MID, pads);
        tooltip.addPara("Weapon energy consumption reduced:" + (1 - 1 / DELAY_SECONDS) * 100 + "%", Misc.getHighlightColor(), pad);
        tooltip.addPara("However, within %s seconds after the weapon is fired, the ship will be delivered to the ship with radiation energy equivalent to the weapon fire required per second.", pad, Misc.getNegativeHighlightColor(), String.valueOf((int) (DELAY_SECONDS)));
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null) return false;
        return ship.getVariant().hasHullMod("CrusadersCore");
    }

    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) return "ship does not exist";
        if (!ship.getVariant().hasHullMod("CrusadersCore")) return "Requires Crusader Core";
        return null;
    }
}