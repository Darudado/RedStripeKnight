package data.hullmods.Installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * 远距离伤害衰减船插：
 * 当攻击者距离超过本舰规模对应的射程上限时，伤害大幅降低，
 * 距离越远减伤越高，最多免疫全部伤害。
 */
public class CR_Votum extends BaseHullMod {

    // ===== 射程上限配置（按舰船规模） =====
    private static final Map<ShipAPI.HullSize, Float> RANGE_CAP = new HashMap<>();
    static {
        RANGE_CAP.put(ShipAPI.HullSize.FIGHTER, 500f);      // 战斗机
        RANGE_CAP.put(ShipAPI.HullSize.FRIGATE, 600f);      // 护卫舰
        RANGE_CAP.put(ShipAPI.HullSize.DESTROYER, 700f);     // 驱逐舰
        RANGE_CAP.put(ShipAPI.HullSize.CRUISER, 800f);      // 巡洋舰
        RANGE_CAP.put(ShipAPI.HullSize.CAPITAL_SHIP, 900f);  // 主力舰
        RANGE_CAP.put(ShipAPI.HullSize.DEFAULT, 700f);       // 默认值
    }

    // 减伤参数
    private static final float BASE_REDUCTION = 0.75f;        // 基础减伤75% → 承受25%
    private static final float EXTRA_REDUCTION_PER_STEP = 0.05f; // 每150单位额外减伤5%
    private static final float STEP_DISTANCE = 150f;          // 步长150单位
    private static final float MAX_REDUCTION = 1.0f;           // 最高减伤100%

    // 监听器ID
    private static final String DAMAGE_MOD_ID = "cr_votum_damage_reduction";

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // 导弹伤害减免（与距离无关的固定减伤）
       // stats.getMissileDamageTakenMult().modifyMult(id, 0.25f);
        //stats.getMissileShieldDamageTakenMult().modifyMult(id, 0.25f);

        // 禁止强制排散
        stats.getVentRateMult().modifyMult(id, 0f);
        if(stats.getVariant().getHullSpec().getHullId().startsWith("vow_")){
            stats.getFluxCapacity().modifyMult(id, 0.7f);
            stats.getMaxSpeed().modifyMult(id, 1.2f);
            stats.getAcceleration().modifyMult(id, 1.6f);
        }else{
            stats.getMaxSpeed().modifyMult(id, 1.1f);
            stats.getAcceleration().modifyMult(id, 1.3f);
            stats.getFluxCapacity().modifyMult(id, 0.7f);
        }

        // 根据舰船规模设置武器射程上限
        float rangeCap = getRangeCap(hullSize);
        stats.getWeaponRangeThreshold().modifyFlat(id, rangeCap);
        stats.getWeaponRangeMultPastThreshold().modifyMult(id, 0.05f); // 超出阈值后射程缩减为原来的5%
    }


    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 添加伤害修改监听器（如果尚未添加）
        if (!ship.hasListenerOfClass(VotumDamageModifier.class)) {
            ship.addListener(new VotumDamageModifier(ship));
        }
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (ship.isHulk() || !engine.isEntityInPlay(ship)) return;

        ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize,
                                          ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        Color bad =Misc.getNegativeHighlightColor();

        tooltip.addSectionHeading("效果", Alignment.MID, opad);

        float rangeCap = getRangeCap(hullSize);
        //tooltip.addPara("受到的导弹伤害减免 %s。", 5f, h, "25%");
        //tooltip.addPara("受到攻击为非导弹武器时：", opad, h);
        tooltip.addPara("舰船射程上限锁定为 %s ,但是舰船加速性能提升 %s 最高速度提升 %s ", 5f, bad, String.valueOf((int) rangeCap),"30%","10%");
        tooltip.addPara("舰船辐能容量减少 %s ", 5f, bad, "30%");
        tooltip.addPara("当攻击者距离本舰超过 %s 单位时，触发远距离伤害衰减：", 5f, h, String.valueOf((int) rangeCap));
        tooltip.addPara("基础伤害减免 %s。", 5f, h, "75%");
        tooltip.addPara("每超出 %s 单位，伤害减免额外提高 %s。", 5f, h, String.valueOf((int) STEP_DISTANCE), "5%");
        tooltip.addPara("最高可减免 %s 伤害（完全免疫）。", 5f, h, "100%");
        tooltip.addPara(" %s 舰船进行强制排散。", 5f, bad, "禁止");
        tooltip.addPara("你将 %s 。", 5f,  bad, "无法后退");

        tooltip.addPara("按住 %s 查看详细说明", opad, h, "F3");
        if (Keyboard.isKeyDown(Keyboard.KEY_F3)) {
            tooltip.addSectionHeading("详细机制", Alignment.MID, opad);
            tooltip.addPara("射程上限与舰船规模对应关系：", 5f);
            tooltip.addPara("计算方式：", 5f);
            tooltip.addPara("  减伤比例 = min(1.0, 0.75 + 0.05 × floor((距离 - 上限)/150))", 3f);
            tooltip.addPara("  实际承受伤害 = 原伤害 × (1 - 减伤比例)", 3f);
            tooltip.addPara("注意：只对超出射程上限的攻击生效，距离未超上限时无影响。", 5f);
        }
    }

    /** 获取指定规模对应的射程上限 */
    private float getRangeCap(ShipAPI.HullSize hullSize) {
        return RANGE_CAP.getOrDefault(hullSize, RANGE_CAP.get(ShipAPI.HullSize.DEFAULT));
    }

    // ===== 伤害修改监听器 =====
    private class VotumDamageModifier implements DamageTakenModifier {
        private final ShipAPI ship;

        public VotumDamageModifier(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage,
                                        Vector2f point, boolean shieldHit) {
            // 只对本舰生效
            if (target != ship) return null;

            // 获取伤害来源实体
            CombatEntityAPI source = null;
            if (param instanceof DamagingProjectileAPI) {
                source = ((DamagingProjectileAPI) param).getSource();
            } else if (param instanceof BeamAPI) {
                source = ((BeamAPI) param).getSource();
            } else if (param instanceof ShipAPI) {
                source = (ShipAPI) param; // 冲撞伤害
            }
            // 若无法获取来源或来源为自身，不处理
            if (source == null || source == ship) return null;

            // 计算攻击者与本舰的距离
            float distance = MathUtils.getDistance(ship.getLocation(), source.getLocation());
            float rangeCap = getRangeCap(ship.getHullSize());

            // 若距离未超过上限，不修改
            if (distance <= rangeCap) return null;

            // 计算超出距离的步数（向下取整）
            float excess = distance - rangeCap;
            int steps = (int) (excess / STEP_DISTANCE); // 整数除法自动向下取整

            // 计算减伤比例
            float reduction = BASE_REDUCTION + steps * EXTRA_REDUCTION_PER_STEP;
            if (reduction > MAX_REDUCTION) reduction = MAX_REDUCTION;

            // 伤害乘数 = 1 - 减伤比例
            float mult = 1f - reduction;
            if (mult < 0f) mult = 0f;

            // 应用伤害修改
            damage.getModifier().modifyMult(DAMAGE_MOD_ID, mult);

            // 返回ID以便后续覆盖（通常返回null也可，但返回ID可管理）
            return DAMAGE_MOD_ID;
        }
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null) return false;
        if(ship.getVariant().hasHullMod("CR_Circumvenire")) return false;
        if(ship.getVariant().hasHullMod("CR_Retinere")) return false;
        if(!(ship.getParentStation() == null)) return false;
        return ship.getVariant().hasHullMod("CrusadersCore");
    }

    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) return "船只不存在";
        if(ship.getVariant().hasHullMod("CR_Circumvenire")) return "舰船系统已被覆写";
        if(ship.getVariant().hasHullMod("CR_Retinere")) return "舰船系统已被覆写";
        if (!ship.getVariant().hasHullMod("CrusadersCore")) return "需要十字军核心";
        if(!(ship.getParentStation() == null)) return "不能安装于舰船模块上";
        return null;
    }

}