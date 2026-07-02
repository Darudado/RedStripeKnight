package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static data.hullmods.HugeOpenPort.countTotalFighters;

public class AssaultCombatCarrier extends BaseHullMod {

    private static final float CAPACITY_MULTIPLIER = 2f;
    private static final float REGEN_TIME_MULTIPLIER = 2.5f;
    private static final float MIN_REGEN_INTERVAL = 0.1f;
    private static final String DATA_KEY_PREFIX = "RS_VIRTUAL_HANGAR_DATA_";
    private static final String STATUS_KEY_PREFIX = "RS_VIRTUAL_HANGAR_STATUS_";

    public static final float SMALL_COST_REDUCTION = 2F;
    public static final float MEDIUM_COST_REDUCTION = 5F;
    public static final float LARGE_COST_REDUCTION = 8F;

    public static final float BASE_MIN = 675F;
    public static final float RANGE_BOUNS = 15F;

    private static class DeckState {
        String wingId;
        int numPerWing;
        float refitTime;
        int capacityMax;
        int capacityNow;
        int lastNumLost;
        float regenProgress;
    }

    private static class CarrierCombatData {
        final Map<Integer, DeckState> deckStates = new LinkedHashMap<>();
        float uiTimer = 0f;
    }

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {

        stats.getFighterRefitTimeMult().modifyMult(id, 0.8f);
        stats.getSensorStrength().modifyMult(id, 1 + 0.5f);

        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.SMALL_BALLISTIC_MOD).modifyFlat(id, -SMALL_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.SMALL_ENERGY_MOD).modifyFlat(id, -SMALL_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.MEDIUM_BALLISTIC_MOD).modifyFlat(id, -MEDIUM_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.MEDIUM_ENERGY_MOD).modifyFlat(id, -MEDIUM_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.LARGE_BALLISTIC_MOD).modifyFlat(id, -LARGE_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.LARGE_ENERGY_MOD).modifyFlat(id, -LARGE_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.FIGHTER_COST_MOD).modifyFlat(id, -10F);

        int totalFighters = countTotalFighters(stats);
        stats.getRecoilPerShotMult().modifyMult(id , 1 - RANGE_BOUNS/500*totalFighters);
        stats.getRecoilPerShotMult().modifyMult(id , 1 - RANGE_BOUNS/500*totalFighters);
        stats.getRecoilDecayMult().modifyMult(id , 1 - RANGE_BOUNS/500*totalFighters);

    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 添加射程修正监听器
        if (!ship.hasListener(new AssaultCombatCarrierRangeModifier())) {
            ship.addListener(new AssaultCombatCarrierRangeModifier());
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;
        if (ship == null) return;

        // 舰船被摧毁时清理数据并退出
        if (!ship.isAlive()) {
            cleanupData(engine, ship);
            return;
        }

        if (ship.getNumFighterBays() <= 0) return;

        CarrierCombatData data = getOrCreateData(engine, ship);
        syncDeckStateWithCurrentBays(ship, data);

        List<FighterLaunchBayAPI> bays = ship.getLaunchBaysCopy();
        for (int i = 0; i < bays.size(); i++) {
            FighterLaunchBayAPI bay = bays.get(i);
            DeckState state = data.deckStates.get(i);
            if (state == null) continue;

            updateLossAndInstantReplacement(bay, state);
            updateDeckRegen(ship, state, amount);
        }

        updateStatusUI(engine, ship, data, amount);
    }

    /** 清理该舰船存储的战斗数据和状态栏显示 */
    private void cleanupData(CombatEngineAPI engine, ShipAPI ship) {
        String dataKey = DATA_KEY_PREFIX + ship.getId();
        engine.getCustomData().remove(dataKey);
        String statusKey = STATUS_KEY_PREFIX + ship.getId();
        engine.getCustomData().remove(statusKey);
    }

    private CarrierCombatData getOrCreateData(CombatEngineAPI engine, ShipAPI ship) {
        String key = DATA_KEY_PREFIX + ship.getId();
        Object obj = engine.getCustomData().get(key);
        if (obj instanceof CarrierCombatData) return (CarrierCombatData) obj;

        CarrierCombatData created = new CarrierCombatData();
        engine.getCustomData().put(key, created);
        return created;
    }

    private void syncDeckStateWithCurrentBays(ShipAPI ship, CarrierCombatData data) {
        List<FighterLaunchBayAPI> bays = ship.getLaunchBaysCopy();

        List<Integer> removeKeys = new ArrayList<>();
        for (Integer key : data.deckStates.keySet()) {
            if (key >= bays.size()) {
                removeKeys.add(key);
            }
        }
        for (Integer key : removeKeys) {
            data.deckStates.remove(key);
        }

        for (int i = 0; i < bays.size(); i++) {
            FighterLaunchBayAPI bay = bays.get(i);
            FighterWingAPI wing = bay.getWing();
            if (wing == null) continue;

            String wingId = wing.getWingId();
            FighterWingSpecAPI spec;
            try {
                spec = Global.getSettings().getFighterWingSpec(wingId);
            } catch (Exception ex) {
                continue;
            }

            int numPerWing = Math.max(1, spec.getNumFighters());
            float refitTime = Math.max(MIN_REGEN_INTERVAL, spec.getRefitTime());
            int capacityMax = Math.max(1, Math.round(numPerWing * CAPACITY_MULTIPLIER));

            DeckState state = data.deckStates.get(i);
            if (state == null) {
                state = new DeckState();
                state.wingId = wingId;
                state.numPerWing = numPerWing;
                state.refitTime = refitTime;
                state.capacityMax = capacityMax;
                state.capacityNow = capacityMax;
                state.lastNumLost = bay.getNumLost();
                state.regenProgress = 0f;
                data.deckStates.put(i, state);
            } else if (!wingId.equals(state.wingId) || state.capacityMax != capacityMax) {
                state.wingId = wingId;
                state.numPerWing = numPerWing;
                state.refitTime = refitTime;
                state.capacityMax = capacityMax;
                state.capacityNow = Math.min(state.capacityNow, capacityMax);
                state.lastNumLost = bay.getNumLost();
                state.regenProgress = 0f;
            } else {
                state.refitTime = refitTime;
            }
        }
    }

    private void updateLossAndInstantReplacement(FighterLaunchBayAPI bay, DeckState state) {
        int currentLost = Math.max(0, bay.getNumLost());
        int deltaLost = currentLost - state.lastNumLost;
        if (deltaLost < 0) deltaLost = 0;

        if (deltaLost > 0) {
            for (int i = 0; i < deltaLost; i++) {
                if (state.capacityNow <= 0) break;

                state.capacityNow -= 1;
                bay.setFastReplacements(bay.getFastReplacements() + 1);
                bay.makeCurrentIntervalFast();
                bay.setCurrRate(0f);
            }
        }

        state.lastNumLost = currentLost;
    }

    private void updateDeckRegen(ShipAPI ship, DeckState state, float amount) {
        if (state.capacityNow >= state.capacityMax) return;

        float shipRefitMult = ship.getMutableStats().getFighterRefitTimeMult().getModifiedValue();
        float regenInterval = Math.max(MIN_REGEN_INTERVAL, state.refitTime * REGEN_TIME_MULTIPLIER * shipRefitMult);

        state.regenProgress += amount;
        while (state.regenProgress >= regenInterval && state.capacityNow < state.capacityMax) {
            state.regenProgress -= regenInterval;
            state.capacityNow += 1;
        }
    }

    private void updateStatusUI(CombatEngineAPI engine, ShipAPI ship, CarrierCombatData data, float amount) {
        if (ship != engine.getPlayerShip()) return;

        data.uiTimer += amount;
        if (data.uiTimer < 0.08f) return;
        data.uiTimer = 0f;

        int totalNow = 0;
        StringBuilder deckText = new StringBuilder();
        List<FighterLaunchBayAPI> bays = ship.getLaunchBaysCopy();

        for (int i = 0; i < bays.size(); i++) {
            DeckState state = data.deckStates.get(i);
            int capacity = state != null ? state.capacityNow : 0;
            totalNow += capacity;

            if (i > 0) deckText.append(" | ");
            deckText.append(capacity);
        }

        String title = "战机战备";
        String main = "甲板后备战机数量：" + deckText;
        engine.maintainStatusForPlayerShip(
                STATUS_KEY_PREFIX + ship.getId(),
                "graphics/icons/hullsys/targeting_feed.png",
                title,
                main,
                totalNow <= 0
        );
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        float pad = 2f;
        Color h = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        tooltip.addSectionHeading("前线母舰调度系统",  Alignment.MID, opad);

        tooltip.addSectionHeading("火控升级", Color.ORANGE, Color.BLACK, Alignment.MID, opad);
        tooltip.addPara("对于非导弹武器，小型/中型/大型炮塔安装时花费的装配点减少 %s / %s / %s 。", pad , h , "2","5","8");
        tooltip.addPara("对于非导弹武器，基础射程最低为 %s ，甲板中每有一架舰载机增加 %s 点基础射程。", pad , h , String.valueOf(BASE_MIN), String.valueOf(RANGE_BOUNS));
        tooltip.addPara("且甲板中每有一架舰载机增加 %s 点武器连射精度。", pad , h , String.valueOf(RANGE_BOUNS/500));

        tooltip.addSectionHeading("机库管控", Color.ORANGE, Color.BLACK, Alignment.MID, opad);
        tooltip.addPara("为每个甲板提供独立的机库容量，容量为战机编队数量的 %s 倍。", pad , h , "2");
        tooltip.addPara("战机损失时可立即从机库补充。", pad);
        tooltip.addPara("容量会随时间自动恢复，恢复速度为战机整备速度的 %s 倍。", pad, bad , "2.5");
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship != null && ship.getNumFighterBays() > 0;
    }


    public boolean affectsOPCosts() {
        return true;
    }

    // 清理状态，避免在战斗外保留效果


    public static class AssaultCombatCarrierRangeModifier implements WeaponBaseRangeModifier {
        @Override
        public float getWeaponBaseRangePercentMod(ShipAPI ship, WeaponAPI weapon) {
            return 0f; // 不使用百分比修正
        }

        @Override
        public float getWeaponBaseRangeMultMod(ShipAPI ship, WeaponAPI weapon) {
            return 1f; // 不使用乘数修正
        }

        @Override
        public float getWeaponBaseRangeFlatMod(ShipAPI ship, WeaponAPI weapon) {
            // 获取武器基础射程
            float baseRange = weapon.getSpec().getMaxRange();
            int totalFighters = countTotalFighters(ship.getMutableStats());

            float MIN_WEAPON_RANGE;

            if (totalFighters > 0) {
                MIN_WEAPON_RANGE = BASE_MIN + RANGE_BOUNS * totalFighters;
            }else{
                MIN_WEAPON_RANGE = BASE_MIN;
            }

            // 如果基础射程低于1000，返回差值
            if (baseRange < MIN_WEAPON_RANGE) {
                return MIN_WEAPON_RANGE - baseRange;
            }

            // 否则不修改射程
            return 0f;
        }
    }


}