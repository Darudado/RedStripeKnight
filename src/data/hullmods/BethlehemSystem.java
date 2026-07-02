package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.shade.RS_LinkNetworkRender;
import data.subsystems.BethlehemLauch;
import org.lazywizard.lazylib.combat.AIUtils;
import org.magiclib.subsystems.MagicSubsystemsManager;

import java.awt.*;
import java.util.*;
import java.util.List;

public class BethlehemSystem extends BaseHullMod {
    protected static final float BOUN_RANGE = 2000;//链接半径
    protected static final float SHIELD_BOOST = 0.85f;
    protected static final float DAMAGE_BUFF = 5f;
    protected static final float RETURN_SPEED_BOOST = 100f;

    private static final float CAPACITY_MULTIPLIER = 2.5f;
    private static final float REGEN_TIME_MULTIPLIER = 1.75f;
    private static final float MIN_REGEN_INTERVAL = 0.1f;
    private static final String DATA_KEY_PREFIX = "RS_Be_VIRTUAL_HANGAR_DATA_";
    private static final String STATUS_KEY_PREFIX = "RS_Be_VIRTUAL_HANGAR_STATUS_";

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

    public void applyEffectsBeforeShipCreation(ShipAPI ship, MutableShipStatsAPI stats, String id) {
        //reset the "check" mutable stat so that it is applied next deployment
        ship.addListener(new HullmodListener(ship));
        stats.getFighterRefitTimeMult().modifyMult(id ,0.9f);
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        try {
            BethlehemLauch Subsystem = new BethlehemLauch(ship);
            // 注册到MagicLib子系统管理器
            MagicSubsystemsManager.addSubsystemToShip(ship, Subsystem);
        } catch (Exception e) {
            // 记录错误日志，便于调试
            Global.getLogger(this.getClass()).error("添加子系统失败: " + e.getMessage(), e);
        }
    }


    public static class HullmodListener implements AdvanceableListener {
        private List<ShipAPI> linkedFighters = new ArrayList<>();
        private final ShipAPI ship;
        private final IntervalUtil timer = new IntervalUtil(0.1f, 0.15f);

        public HullmodListener(ShipAPI ship) {
            this.ship = ship;
        }


        @Override
        public void advance(float amount) {
            //指定一个加成id，避免复数插件重复提供加成
            String id = "RS_BE";

           // FighterWingSpecAPI fighter = (FighterWingSpecAPI) linkedFighters;
            //if (fighter.getOpCost(f.getMutableStats()) < 20) {

            if (ship.isHulk() || !ship.isAlive() || !Global.getCombatEngine().isEntityInPlay(ship)) {

                for (ShipAPI f : linkedFighters) {
                        f.getMutableStats().getShieldDamageTakenMult().unmodify(id);
                        f.getMutableStats().getBallisticWeaponDamageMult().unmodify(id);
                        f.getMutableStats().getEnergyWeaponDamageMult().unmodify(id);
                        f.getMutableStats().getMissileWeaponDamageMult().unmodify(id);
                        f.getMutableStats().getMaxSpeed().unmodify(id + "_Returning");
                    }
                    return;
                }
                timer.advance(amount);
                if (timer.intervalElapsed()) {
                    List<ShipAPI> tempFighters = new ArrayList<>();
                    for (ShipAPI f : AIUtils.getNearbyAllies(ship, BOUN_RANGE)) {
                        if (f.isFighter() && f.getOwner() == ship.getOwner() && f.isAlly()) {
                            tempFighters.add(f);
                            f.getMutableStats().getShieldDamageTakenMult().modifyMult(id, SHIELD_BOOST);
                            f.getMutableStats().getBallisticWeaponDamageMult().modifyPercent(id, DAMAGE_BUFF);
                            f.getMutableStats().getEnergyWeaponDamageMult().modifyPercent(id, DAMAGE_BUFF);
                            f.getMutableStats().getMissileWeaponDamageMult().modifyPercent(id, DAMAGE_BUFF);
                            if (f.getWing() != null && f.getWing().isReturning(f)) {
                                f.getMutableStats().getMaxSpeed().modifyPercent(id + "_Returning", RETURN_SPEED_BOOST);
                            }
                            if(!linkedFighters.contains(f)){
                                Objects.requireNonNull(RS_LinkNetworkRender.getInstance()).addRenderData(ship.getLocation(),f.getLocation(),0.3f,1f,0.5f, RS_LinkNetworkRender.CONTRAIL);
                            }
                        }
                    }
                    linkedFighters.removeAll(tempFighters);
                    for(ShipAPI f:linkedFighters){
                        f.getMutableStats().getMaxSpeed().unmodify(id);
                        f.getMutableStats().getBallisticWeaponDamageMult().unmodify(id);
                        f.getMutableStats().getEnergyWeaponDamageMult().unmodify(id);
                        f.getMutableStats().getMissileWeaponDamageMult().unmodify(id);
                        f.getMutableStats().getMaxSpeed().unmodify(id+"_Returning");
                    }
                    linkedFighters = tempFighters;
                }
        }
    }




    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 10f;//间隔

        Color highlight = Misc.getHighlightColor();
        Color gray = Misc.getGrayColor();
        Color postive = Misc.getPositiveHighlightColor();

        tooltip.addPara("舰载机整备时间 %s", pad, postive, "-10%");

        tooltip.addSectionHeading("管制网络", Alignment.MID, pad);//页眉
        tooltip.addPara("母舰于 %s 码范围内构建管制网络,范围内的舰载机将获得以下加成:", pad, highlight, "2000");
        tooltip.addPara("%s 造成的伤害 ", pad, postive, "+5%");
        tooltip.addPara("%s 受到的伤害 ", pad, postive, "-5%");
        tooltip.addPara("%s 舰载机返航速度 ", pad, postive, "+100%");

        tooltip.addSectionHeading("备用机库", Alignment.MID, pad);
        tooltip.addPara("每个飞行甲板拥有相当于编制数 %s 的后备战机储备", pad, highlight, "2.5倍");
        tooltip.addPara("战机损失时立即从储备中补充，无需等待整备", pad);
        tooltip.addPara("储备耗尽后以 %s 基础整备时间的速度恢复储备（最低 %s 秒/架）", pad, highlight, "175%", "0.1");
    }

}