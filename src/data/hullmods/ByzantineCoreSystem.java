package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.WeaponBaseRangeModifier;
import data.subsystems.ByzantineSupport;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.subsystems.MagicSubsystemsManager;

import java.util.List;

public class ByzantineCoreSystem extends BaseHullMod {
    public static final float MIN_WEAPON_RANGE = 900f;

    public static final float SMALL_COST_REDUCTION = 4F;
    public static final float MEDIUM_COST_REDUCTION = 4F;
    public static final float LARGE_COST_REDUCTION = 5F;

    // 新增：耗散增益百分比
    public static final float DISSIPATION_BOOST_PERCENT = 10f;
    // 新增：作用范围
    public static final float DISSIPATION_BOOST_RANGE = 2500f;

    public void applyEffectsBeforeShipCreation(MutableShipStatsAPI stats, String id) {
        //reset the "check" mutable stat so that it is applied next deployment
        //ship.addListener(new BethlehemSystem.HullmodListener(ship));
        stats.getEnergyWeaponRangeBonus().modifyPercent(id , 40f);
        stats.getBallisticWeaponRangeBonus().modifyPercent(id , 40f);
        stats.getSightRadiusMod().modifyFlat(id, 1500f);

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
        // 添加射程修正监听器
        if (!ship.hasListener(new ByzantineRangeModifier())) {
            ship.addListener(new ByzantineRangeModifier());
        }

        // 新增：添加舰队范围耗散增益监听器
        if (!ship.hasListener(new ByzantineFleetDissipationBoost(ship))) {
            ship.addListener(new ByzantineFleetDissipationBoost(ship));
        }
        try {
            ByzantineSupport Subsystem = new ByzantineSupport(ship);
            // 注册到MagicLib子系统管理器
            MagicSubsystemsManager.addSubsystemToShip(ship, Subsystem);
        } catch (Exception e) {
            // 记录错误日志，便于调试
            Global.getLogger(this.getClass()).error("添加子系统失败: " + e.getMessage(), e);
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

    // 修改：改为提供耗散增益的监听器
    public static class ByzantineFleetDissipationBoost  implements ShipSystemAIScript {
        private final ShipAPI sourceShip;
        private final String modId;

        public ByzantineFleetDissipationBoost(ShipAPI ship) {
            this.sourceShip = ship;
            this.modId = "byzantine_dissipation_boost_" + ship.getId();
        }

        public void advance(com.fs.starfarer.api.combat.CombatEngineAPI engine) {
            // 检查源舰船是否存活
            boolean sourceAlive = sourceShip.isAlive() && !sourceShip.isHulk();

            // 获取战斗中的所有友方舰船
            List<ShipAPI> ships = engine.getShips();
            for (ShipAPI targetShip : ships) {
                if (targetShip.getOwner() == sourceShip.getOwner() && // 同阵营
                        targetShip != sourceShip) { // 排除自己

                    MutableShipStatsAPI stats = targetShip.getMutableStats();

                    // 检查距离
                    float distance = com.fs.starfarer.api.util.Misc.getDistance(sourceShip.getLocation(), targetShip.getLocation());

                    if (sourceAlive && distance <= DISSIPATION_BOOST_RANGE) {
                        // 源舰存活且在范围内时应用耗散增益
                        stats.getFluxDissipation().modifyPercent(modId, DISSIPATION_BOOST_PERCENT);
                        stats.getHardFluxDissipationFraction().modifyPercent(modId, DISSIPATION_BOOST_PERCENT/2);
                    } else {
                        // 源舰被摧毁或超出范围时移除效果
                        stats.getFluxDissipation().unmodify(modId);
                        stats.getHardFluxDissipationFraction().unmodify(modId);
                    }
                }
            }
        }

        @Override
        public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {

        }

        @Override
        public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {

        }
    }

    public void addPostDescriptionSection(com.fs.starfarer.api.ui.TooltipMakerAPI tooltip,
                                          ShipAPI.HullSize hullSize, ShipAPI ship,
                                          float width, boolean isForModSpec) {
        float pads = 10f;
        float pad = 2f;
        com.fs.starfarer.api.util.Misc.getHighlightColor();
        com.fs.starfarer.api.util.Misc.getPositiveHighlightColor();

        tooltip.addPara("拜占庭的核心指挥系统能够完美协调舰队作战，整合了先进的传感器网络和指挥控制协议。", pads);

        tooltip.addPara("**舰队指挥系统:**", pads);
        tooltip.addPara("传感器视野范围 +%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "1500");
        tooltip.addPara("- 自身存活时，%s范围内的友军舰船获得：", pad);
        tooltip.addPara(" 辐能耗散 +%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "10%");
        tooltip.addPara(" 硬辐能耗散能力 +%s", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "5%");

        tooltip.addPara("**集成子系统:众志行征**", pads);
        tooltip.addPara("搭载拜占庭支援子系统，提供额外的战术能力", pad);
        tooltip.addPara("提升750/1500/2500单位距离内友方单位 +%s / +%s / +%s的机动能力与舰载机整备速率", pad,
                com.fs.starfarer.api.util.Misc.getPositiveHighlightColor(), "30%","20%","10%");

        tooltip.addPara("该指挥系统通过先进的数据链和战术网络，显著提升整个舰队的作战效率和生存能力。",
                com.fs.starfarer.api.util.Misc.getGrayColor(), pads);
    }
}