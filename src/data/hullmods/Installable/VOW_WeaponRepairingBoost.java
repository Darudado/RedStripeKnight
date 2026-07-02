package data.hullmods.Installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.*;

/**
 * 武器修复加速与恢复回血插件
 * <p>
 * 效果：
 * - 武器维修时间缩短 (REPAIR_BONUS %)
 * - 武器修复后短时间内（BONUS_TIME 秒）冷却速度大幅加快
 * - 武器从瘫痪恢复时，根据武器尺寸立即回复少量船体结构
 * - S-插件：额外减少武器生命值作为代价
 */
public class VOW_WeaponRepairingBoost extends BaseHullMod {

    public static final String ID = "VOW_WeaponRepairingBoost";

    // 武器维修时间倍率减免（0.5 代表减少50%）
    public static final float REPAIR_BONUS = 50f;
    // S-插件下武器生命值惩罚（取正数，代码中会变为负百分比）
    public static final float WEAPON_HEALTH_PENALTY = 25f;

    // 武器恢复后的冷却加速持续时间（秒）
    public static final float BONUS_TIME = 2.0f;
    // 冷却加速速率系数（与0.01相乘后约2，即每秒额外缩减冷却200%）
    public static final float BONUS_LEVEL = 200f;

    // 武器恢复时的回血量（对应小/中/大型）
    public static final float HEAL_SMALL = 50f;
    public static final float HEAL_MEDIUM = 100f;
    public static final float HEAL_LARGE = 150f;

    public float RECOIL_BONUS = 25f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // 基础维修时间减免（无论是否S-插件均生效）
        stats.getCombatWeaponRepairTimeMult().modifyMult(id, 1f - REPAIR_BONUS * 0.01f);

        stats.getMaxRecoilMult().modifyMult(id, 1f + (0.01f * RECOIL_BONUS));
        stats.getRecoilPerShotMult().modifyMult(id, 1f + (0.01f * RECOIL_BONUS));
        // slower recoil recovery, also, to match the reduced recoil-per-shot
        // overall effect is same as without skill but halved in every respect
        stats.getRecoilDecayMult().modifyMult(id, 1f + (0.01f * RECOIL_BONUS));

        if(stats.getVariant().hasHullMod("VOW_DesignSystem")){
            stats.getFluxCapacity().modifyPercent(id, 5f);
            stats.getHullBonus().modifyPercent(id ,5f);
        }

        // S-插件惩罚：武器生命值减少
        if (isSMod(stats)) {
            stats.getWeaponHealthBonus().modifyPercent(id, -WEAPON_HEALTH_PENALTY);
            stats.getCombatWeaponRepairTimeMult().modifyMult(id, 1f - (REPAIR_BONUS+WEAPON_HEALTH_PENALTY) * 0.01f);
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused()) return;
        if (!ship.isAlive()) return;
        if (!engine.isEntityInPlay(ship)) return;

        // 以下开关可启用仅限S-插件才激活冷却加速，若需要请取消注释
        //if (!isSMod(ship)) return;

        // 初始化或获取自定义数据容器
        if (!ship.getCustomData().containsKey(ID)) {
            ship.setCustomData(ID, new TotalOverloadState(ship));
        }
        TotalOverloadState data = (TotalOverloadState) ship.getCustomData().get(ID);

        // 维护武器状态表：移除已脱离舰船或变为装饰槽的武器，添加新出现的非装饰武器
        Set<WeaponAPI> currentValidWeapons = new HashSet<>();
        for (WeaponAPI w : ship.getAllWeapons()) {
            if (!w.isDecorative() && !w.getSlot().isDecorative() && !w.getSlot().isHidden()) {
                currentValidWeapons.add(w);
            }
        }
        // 移除无效武器
        data.allValidWeapons.keySet().removeIf(w -> !currentValidWeapons.contains(w));
        // 添加新出现的武器（初始状态为正常）
        for (WeaponAPI w : currentValidWeapons) {
            if (!data.allValidWeapons.containsKey(w)) {
                data.allValidWeapons.put(w, new WeaponOverloadState());
            }
        }

        // 遍历所有有效武器，更新状态机
        for (Map.Entry<WeaponAPI, WeaponOverloadState> entry : data.allValidWeapons.entrySet()) {
            WeaponAPI weapon = entry.getKey();
            WeaponOverloadState weaponData = entry.getValue();
            WeaponState state = weaponData.currentState;

            // 状态1：武器当前瘫痪
            if (weapon.isDisabled() && state != WeaponState.DISABLED) {
                weaponData.currentState = WeaponState.DISABLED;
                weaponData.timeLeft = 0f; // 清空残留加速计时
                continue;
            }

            // 状态2：武器刚刚从瘫痪恢复
            if (!weapon.isDisabled() && state == WeaponState.DISABLED) {
                weaponData.currentState = WeaponState.BOOST;
                weaponData.timeLeft = BONUS_TIME;
                // 触发回血效果
                applyRepairHeal(ship, weapon);
                continue;
            }

            // 状态3：处于加速期 (BOOST)
            if (state == WeaponState.BOOST) {
                // 若加速期间武器又被打瘫痪，则会在下一帧由状态1捕获，这里无需重复处理
                float timeLeft = weaponData.timeLeft;
                if (timeLeft <= 0f) {
                    weaponData.currentState = WeaponState.NORMAL;
                    continue;
                }

                if (!isSMod(ship)) return;
                // 加速冷却：冷却剩余时间根据剩余时间动态减少
                if (weapon.getCooldownRemaining() > 0f) {
                    // 速率随时间线性递减，从最大2.0降至0
                    float rate = 2f * timeLeft / BONUS_TIME;
                    float reduction = amount * rate * BONUS_LEVEL * 0.01f;
                    weapon.setRemainingCooldownTo(weapon.getCooldownRemaining() - reduction);
                }
                weaponData.timeLeft -= amount;
            }
        }
    }

    /**
     * 根据武器尺寸为舰船回复一定量的船体结构
     */
    private void applyRepairHeal(ShipAPI ship, WeaponAPI weapon) {
        float maxHP = ship.getMaxHitpoints();
        float currentHP = ship.getHitpoints();
        float missingHP = maxHP - currentHP;
        if (missingHP <= 0f) return;

        float healAmount = switch (weapon.getSize()) {
            case MEDIUM -> HEAL_MEDIUM;
            case LARGE -> HEAL_LARGE;
            default -> HEAL_SMALL;
        };

        healAmount = Math.min(healAmount, missingHP);
        if (healAmount > 0f) {
            ship.setHitpoints(currentHP + healAmount);
            Global.getCombatEngine().addFloatingText(
                    weapon.getLocation(),
                    String.format("HP + %.0f", healAmount),
                    20f,
                    Color.green,
                    ship,
                    1f,
                    1f
            );
        }
    }

    // ---------- 以下为tooltip描述重写示例，可根据实际本地化需求调整 ----------

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 10f;
        float padS = 2f;
        tooltip.addPara("武器维修时间缩短 %s " , padS, Misc.getHighlightColor(), (int) REPAIR_BONUS + "%");
        tooltip.addPara("武器修复后  %s 秒内冷却速率提高  %s " , padS, Misc.getHighlightColor(), String.format("%.1f", BONUS_TIME),(int) BONUS_LEVEL + "%");
        tooltip.addPara("武器恢复时根据尺寸回复舰船结构：小型+" + (int) HEAL_SMALL + "，中型+" + (int) HEAL_MEDIUM + "，大型+" + (int) HEAL_LARGE, pad);
        tooltip.addPara("但是武器后坐力增加 %s " , padS, Misc.getNegativeHighlightColor(),(int) RECOIL_BONUS + "%");
    }


    public void addSModEffectSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec, boolean isForBuildInList) {
        float pad = 10f;
        float padS = 2f;
        tooltip.addPara("武器生命值额外减少 " , pad, Misc.getNegativeHighlightColor(), (int) WEAPON_HEALTH_PENALTY + "%");
        tooltip.addPara("但武器维修时间缩短 " , padS, Misc.getHighlightColor(), (int) REPAIR_BONUS + "%");
    }

    @Override
    public boolean isSModEffectAPenalty() {
        return true; // S-插件部分为负面效果
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship != null && ship.getVariant().hasHullMod("CrusadersCore");
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) return "舰船不存在";
        if (!ship.getVariant().hasHullMod("CrusadersCore")) return "需要十字军核心";
        return null;
    }

    // ---------- 内部状态类 ----------

    /**
     * 舰船级状态容器，保存所有武器状态，存储在CustomData中
     */
    private static class TotalOverloadState {
        Map<WeaponAPI, WeaponOverloadState> allValidWeapons = new HashMap<>();

        TotalOverloadState(ShipAPI ship) {
            for (WeaponAPI w : ship.getAllWeapons()) {
                if (w.isDecorative() || w.getSlot().isDecorative() || w.getSlot().isHidden()) continue;
                allValidWeapons.put(w, new WeaponOverloadState());
            }
        }
    }

    /**
     * 单武器状态
     */
    private static class WeaponOverloadState {
        WeaponState currentState = WeaponState.NORMAL;
        float timeLeft = 0f; // 剩余加速时间
    }

    private enum WeaponState {
        NORMAL,    // 正常运作
        DISABLED,  // 瘫痪
        BOOST      // 加速冷却中
    }
}