package data.hullmods.Installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;
import org.magiclib.util.MagicIncompatibleHullmods;

import java.awt.*;

public class CR_ImprovedWeaponControlling extends BaseHullMod {
    public final float TURN_ACC = 50F;
    public float RECOIL_BONUS = 20f;
    public float RANGE_BONUS = 5f;
    public float FLU_BONUS = 10f;
    public float SMOD_BONUS = 25f;

    // 动态射速加成参数
    private static final float FIRE_RATE_PER_SECOND = 2f;   // 每秒增加1%
    private static final float MAX_FIRE_RATE_BONUS = 10f;   // 最高5%
    private static final float RESET_DELAY = 1f;           // 停火1秒后归零
    private static final String FIRE_RATE_MOD_ID = "CR_IWC_fireRate";

    // 内部数据类：记录开火时间与累积加成
    private static class FireRateData {
        float fireAccumTime = 0f;   // 累计开火时间（秒），用于计算当前加成
        float lastFireTime = -999f; // 最后一次开火时刻
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getWeaponTurnRateBonus().modifyPercent(id, TURN_ACC);
        stats.getMaxRecoilMult().modifyMult(id, 1f - (0.01f * RECOIL_BONUS));
        stats.getRecoilPerShotMult().modifyMult(id, 1f - (0.01f * RECOIL_BONUS));
        stats.getRecoilDecayMult().modifyMult(id, 1f - (0.01f * RECOIL_BONUS));

        stats.getBallisticWeaponRangeBonus().modifyPercent(id, RANGE_BONUS);
        stats.getEnergyWeaponRangeBonus().modifyPercent(id, RANGE_BONUS);

        stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - (0.01f * FLU_BONUS));
        stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1f - (0.01f * FLU_BONUS));

        if (stats.getVariant().hasHullMod("CR_FighterProducingOverloading")) {
            MagicIncompatibleHullmods.removeHullmodWithWarning(stats.getVariant(), "CR_FighterProducingOverloading", spec.getId());
        }

        boolean sMod = isSMod(stats);
        if (sMod) {
            stats.getAutofireAimAccuracy().modifyMult(id, 1f + SMOD_BONUS * 0.01f);
            stats.getBallisticProjectileSpeedMult().modifyMult(id, 1f + SMOD_BONUS * 0.01f);
            stats.getEnergyProjectileSpeedMult().modifyMult(id, 1f + SMOD_BONUS * 0.01f);
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused() || !ship.isAlive()) return;

        // 获取或创建数据
        String dataKey = "CR_ImprovedWeaponControlling_data";
        FireRateData data = (FireRateData) ship.getCustomData().get(dataKey);
        if (data == null) {
            data = new FireRateData();
            ship.setCustomData(dataKey, data);
        }

        float currentTime = engine.getTotalElapsedTime(false);

        // 检查是否有武器正在开火
        boolean isFiring = false;
        for (WeaponAPI w : ship.getAllWeapons()) {
            if (w.isFiring()) {
                isFiring = true;
                break;
            }
        }

        if (isFiring) {
            // 开火：更新最后开火时间，累加开火时长（上限5秒）
            data.lastFireTime = currentTime;
            data.fireAccumTime += amount;
            if (data.fireAccumTime > MAX_FIRE_RATE_BONUS) {
                data.fireAccumTime = MAX_FIRE_RATE_BONUS;
            }
        } else {
            // 停火超过1秒，重置累积时间
            if (currentTime - data.lastFireTime > RESET_DELAY) {
                data.fireAccumTime = 0f;
            }
        }

        // 应用射速加成（1% * 累积秒数）
        float bonus = data.fireAccumTime * FIRE_RATE_PER_SECOND;
        MutableShipStatsAPI stats = ship.getMutableStats();
        if (bonus > 0f) {
            stats.getRecoilPerShotMult().modifyMult(FIRE_RATE_MOD_ID,1-bonus* 0.01f);
            stats.getRecoilDecayMult().modifyMult(FIRE_RATE_MOD_ID,1-bonus* 0.01f);
            stats.getMaxRecoilMult().modifyMult(FIRE_RATE_MOD_ID,1-bonus* 0.01f);
        } else {
            stats.getRecoilPerShotMult().unmodify(FIRE_RATE_MOD_ID);
            stats.getRecoilDecayMult().unmodify(FIRE_RATE_MOD_ID);
            stats.getMaxRecoilMult().unmodify(FIRE_RATE_MOD_ID);
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return String.format("%.0f", TURN_ACC) + "%";
        if (index == 1) return String.format("%.0f", RECOIL_BONUS) + "%";
        if (index == 2) return String.format("%.0f", RANGE_BONUS) + "%";
        if (index == 3) return String.format("%.0f", FLU_BONUS) + "%";
        return null;
    }

    @Override
    public String getSModDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return SMOD_BONUS + "%";
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null) return false;
        if (!ship.getVariant().hasHullMod("CrusadersCore")) return false;
        return !ship.getVariant().hasHullMod("CR_FighterProducingOverloading");
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) return "The ship does not exist";
        if (!ship.getVariant().hasHullMod("CrusadersCore")) return "Can only be installed on ships with energy cores";
        return null;
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        float pad = 2f;
        Color h = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        tooltip.addSectionHeading("Extra effects", Alignment.MID, opad);
        // 动态后坐力减免描述（注意所有 % 都转义为 %%）
        tooltip.addPara("When the weapon is continuously fired, the recoil is reduced every second." + String.format("%.0f", FIRE_RATE_PER_SECOND) + "%%" +
                ", at most superimposed" + String.format("%.0f", MAX_FIRE_RATE_BONUS) + "seconds (total" +
                String.format("%.0f", FIRE_RATE_PER_SECOND * MAX_FIRE_RATE_BONUS) + "%%）。", pad, h);
        tooltip.addPara("stop firing" + String.format("%.0f", RESET_DELAY) + "The bonus will return to zero after seconds.", pad, h);
    }

}