package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReparingCarrier extends BaseHullMod {

    private static final float CAPACITY_MULTIPLIER = 3.5f;
    private static final float REGEN_TIME_MULTIPLIER = 1.75f;
    private static final float MIN_REGEN_INTERVAL = 0.1f;
    private static final String DATA_KEY_PREFIX = "CR_VIRTUAL_HANGAR_DATA_";
    private static final String STATUS_KEY_PREFIX = "CR_VIRTUAL_HANGAR_STATUS_";


    public static final float SMALL_COST_REDUCTION = 3F;
    public static final float MEDIUM_COST_REDUCTION = 4F;

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
        stats.getDynamic().getStat(Stats.REPLACEMENT_RATE_DECREASE_MULT).modifyMult(id, 0f);
        stats.getDynamic().getStat(Stats.REPLACEMENT_RATE_INCREASE_MULT).modifyMult(id, 100f);
        stats.getFuelMod().modifyMult(id, 1 + 0.5f);
        stats.getCargoMod().modifyMult(id, 1 + 0.5f);
        stats.getSensorStrength().modifyMult(id, 1.5f);

        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.SMALL_BALLISTIC_MOD).modifyFlat(id, -SMALL_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.SMALL_ENERGY_MOD).modifyFlat(id, -SMALL_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.MEDIUM_BALLISTIC_MOD).modifyFlat(id, -MEDIUM_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.MEDIUM_ENERGY_MOD).modifyFlat(id, -MEDIUM_COST_REDUCTION);

        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.FIGHTER_COST_MOD).modifyFlat(id, -10F);

        stats.getDynamic().getStat(Stats.REPLACEMENT_RATE_DECREASE_MULT).modifyMult(id, 1.15f);
        stats.getDynamic().getStat(Stats.SALVAGE_VALUE_MULT_FLEET_INCLUDES_RARE).modifyMult(id, 1.15f);
        stats.getDynamic().getStat(Stats.FUEL_SALVAGE_VALUE_MULT_FLEET).modifyMult(id, 1.15f);
        stats.getDynamic().getStat(Stats.SALVAGE_VALUE_MULT_FLEET_NOT_RARE).modifyMult(id, 1.15f);
        stats.getDynamic().getStat(Stats.BATTLE_SALVAGE_MULT_FLEET).modifyMult(id, 1.15f);
        stats.getDynamic().getStat(Stats.SALVAGE_VALUE_MULT_MOD).modifyMult(id, 1.15f);

    }

    public boolean affectsOPCosts() {
        return true;
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 添加射程修正监听器
        if (!ship.hasListener(new AssaultCombatCarrier.AssaultCombatCarrierRangeModifier())) {
            ship.addListener(new AssaultCombatCarrier.AssaultCombatCarrierRangeModifier());
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

        String title = "fighter aircraft combat readiness";
        String main = "Number of reserve fighters on deck:" + deckText;
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
        float pad = 10f;
        Color h = Misc.getHighlightColor();

        tooltip.addSectionHeading("Aircraft carrier dispatch and command", Color.ORANGE, Color.BLACK, Alignment.MID, 15f);
        tooltip.addPara("Each deck is provided with independent hangar capacity, with a capacity three times the number of fighter aircraft formations.", pad);
        tooltip.addPara("When fighter aircraft are lost, they can be replenished immediately from the hangar.", pad);
        tooltip.addPara("The capacity will automatically recover over time, and the recovery speed is twice the speed of fighter aircraft maintenance.", pad, h);
    }

    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return  50 + "%";
        if (index == 1) return  15 + "%";
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship != null && ship.getNumFighterBays() > 0;
    }
}