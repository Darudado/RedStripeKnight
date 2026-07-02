package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.combat.AIUtils;

import java.util.*;

public class ByzantineModuleSystem extends BaseHullMod {
    public static final float MIN_WEAPON_RANGE = 850f;
    public static final float ALLY_BOOST = 25f; // 5%射程加成
    public static final float ALLY_RANGE_RADIUS = 2500f; // 2500码作用范围

    public static final float SMALL_COST_REDUCTION = 3F;
    public static final float MEDIUM_COST_REDUCTION = 4F;
    public static final float LARGE_COST_REDUCTION = 5F;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // 自身武器射程加成
        stats.getEnergyWeaponRangeBonus().modifyPercent(id , 20f); // 40%加成
        stats.getBallisticWeaponRangeBonus().modifyPercent(id , 20f); // 40%加成
        stats.getAutofireAimAccuracy().modifyPercent(id , 75f);


        // 武器部署点减少
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.SMALL_BALLISTIC_MOD).modifyFlat(id, -SMALL_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.SMALL_ENERGY_MOD).modifyFlat(id, -SMALL_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.MEDIUM_BALLISTIC_MOD).modifyFlat(id, -MEDIUM_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.MEDIUM_ENERGY_MOD).modifyFlat(id, -MEDIUM_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.LARGE_BALLISTIC_MOD).modifyFlat(id, -LARGE_COST_REDUCTION);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.LARGE_ENERGY_MOD).modifyFlat(id, -LARGE_COST_REDUCTION);
    }

    public boolean affectsOPCosts() {
        return true;
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 添加自身射程修正监听器
        if (!ship.hasListenerOfClass(ByzantineRangeModifier.class)) {
            ship.addListener(new ByzantineRangeModifier());
        }

        // 添加友军舰船射程加成监听器
        if (!ship.hasListenerOfClass(ByzantineFleetRangeListener.class)) {
            ship.addListener(new ByzantineFleetRangeListener(ship));
        }
    }

    public static class ByzantineRangeModifier implements WeaponBaseRangeModifier {
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

            // 如果基础射程低于1000，返回差值
            if (baseRange < MIN_WEAPON_RANGE) {
                return MIN_WEAPON_RANGE - baseRange;
            }

            // 否则不修改射程
            return 0f;
        }
    }

    // 新增：舰队范围射程加成监听器
    public static class ByzantineFleetRangeListener implements AdvanceableListener {
        private List<ShipAPI> linkedAllies = new ArrayList<>();
        private ShipAPI ship;
        private final IntervalUtil timer = new IntervalUtil(0.1f, 0.15f);
        private final String buffId = "BYZ_ALLY_RANGE";

        public ByzantineFleetRangeListener(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void advance(float amount) {
            // 如果母舰被摧毁或不在战场中，移除所有友军的加成
            if (ship.isHulk() || !ship.isAlive() || !Global.getCombatEngine().isEntityInPlay(ship)) {
                for (ShipAPI ally : linkedAllies) {
                    removeAllyBuffs(ally);
                }
                linkedAllies.clear();
                return;
            }

            timer.advance(amount);
            if (timer.intervalElapsed()) {
                List<ShipAPI> tempAllies = new ArrayList<>();

                // 查找范围内的友军舰船（不包括自身）
                for (ShipAPI ally : AIUtils.getNearbyAllies(ship, ALLY_RANGE_RADIUS)) {
                    if (ally != ship && ally.getOwner() == ship.getOwner() && ally.isAlive()) {
                        tempAllies.add(ally);

                        // 应用加成
                        ally.getMutableStats().getEmpDamageTakenMult().modifyPercent(buffId, ALLY_BOOST);
                        ally.getMutableStats().getSystemCooldownBonus().modifyPercent(buffId, ALLY_BOOST-15f);
                    }
                }

                // 移除不再在范围内友军的加成
                linkedAllies.removeAll(tempAllies);
                for (ShipAPI ally : linkedAllies) {
                    removeAllyBuffs(ally);
                }

                linkedAllies = tempAllies;
            }
        }

        private void removeAllyBuffs(ShipAPI ally) {
            ally.getMutableStats().getEmpDamageTakenMult().unmodify(buffId);
            ally.getMutableStats().getSystemCooldownBonus().unmodify(buffId);
        }
    }

    // 添加描述信息
    @Override
    public void addPostDescriptionSection(com.fs.starfarer.api.ui.TooltipMakerAPI tooltip,
                                          ShipAPI.HullSize hullSize, ShipAPI ship,
                                          float width, boolean isForModSpec) {
        float pads = 10f;
        float pad = 2f;
        com.fs.starfarer.api.util.Misc.getHighlightColor();
        com.fs.starfarer.api.util.Misc.getPositiveHighlightColor();

        tooltip.addPara("Byzantine's modular fire control system is specially designed", pads);
        tooltip.addPara("Can bring powerful benefits to yourself and your teammates", pad);

        tooltip.addPara("**Self Effect:**", pads);
        tooltip.addPara("- Energy and live weapon range +40%%", pad);
        tooltip.addPara("- Minimum weapon range increased to %s", pad, com.fs.starfarer.api.util.Misc.getHighlightColor(), "1000");
        tooltip.addPara("- All weapons auto-fire accuracy +%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "75%");
        tooltip.addPara("- All weapon deployment points are reduced: small - %s, medium - %s, large - %s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(),
                String.valueOf(SMALL_COST_REDUCTION),
                String.valueOf(MEDIUM_COST_REDUCTION),
                String.valueOf(LARGE_COST_REDUCTION));

        tooltip.addPara("**Fleet command effect:**", pads);
        tooltip.addPara("- Allied ships within %s yards gain:", pad,
                com.fs.starfarer.api.util.Misc.getHighlightColor(), "2500");
        tooltip.addPara("- All weapons auto-fire accuracy +%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "25%");
        tooltip.addPara("- Tactical system cooldown reduction +%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "10%");
    }
}