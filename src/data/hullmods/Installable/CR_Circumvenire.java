package data.hullmods.Installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.*;
import java.util.List;

public class CR_Circumvenire extends BaseHullMod {

    public static final String MOD_ID = "CR_Circumvenire";
    public static final String KILL_BONUS_ID = "cr_venari_kill_bonus"; // 用于击杀增益的修正ID

    // ========== 减益插件常量 ==========
    private static final float SCAN_SIZE = 2000f;               // 扫描范围半宽
    private static final float MAX_WEAPON_RANGE_CAP = 1200f;    // 武器有效射程上限
    private static final int DEBUFF_TRIGGER_COUNT = 2;          // 触发减益所需的小型船数量
    private static final String DEBUFF_ID_PREFIX = "cr_venari_debuff_"; // 减益ID前缀

    // 减益系数
    private static final float RECOIL_DECAY_MULT = 0.8f;
    private static final float RECOIL_PER_SHOT_MULT = 1.2f;
    private static final float MAX_RECOIL_MULT = 1.2f;
    private static final float SPEED_MULT = 0.85f;
    private static final float ACCEL_MULT = 0.85f;
    private static final float TURN_ACCEL_MULT = 0.85f;
    private static final float TURN_RATE_MULT = 0.85f;
    private static final float DAMAGE_TAKEN_MULT = 1.05f;

    // ========== 击杀增益常量 ==========
    private static final float PEAK_TIME_INCREMENT = 15f;       // 每次击杀增加的峰值时间（秒）
    private static final float DAMAGE_INCREMENT = 1f;           // 每次击杀增加的伤害百分比
    private static final float MAX_DAMAGE_BONUS = 10f;          // 伤害加成上限（百分比）

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getAcceleration().modifyPercent(id, 50f);
        stats.getDeceleration().modifyPercent(id, 50f);
        stats.getTurnAcceleration().modifyPercent(id, 50f);
        stats.getMaxTurnRate().modifyPercent(id, 50f);
        if (stats.getVariant().getHullSpec().getHullId().startsWith("cr_")) {
            stats.getArmorBonus().modifyMult(id, 0.75f);
            stats.getHullBonus().modifyMult(id, 0.75f);
        } else {
            stats.getArmorBonus().modifyMult(id, 0.5f);
            stats.getHullBonus().modifyMult(id, 0.5f);
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        int owner = ship.getOwner();
        // 每个阵营只添加一次全局减益插件
        if (Global.getCombatEngine() != null &&
                !Global.getCombatEngine().getCustomData().containsKey("cr_venari_plugin_added_" + owner)) {
            Global.getCombatEngine().getCustomData().put("cr_venari_plugin_added_" + owner, true);
            Global.getCombatEngine().addPlugin(new TrappedPreyScript(owner));
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship.isHulk()) return;
        // 获取或创建击杀增益插件，并将此船加入对应阵营的存活集合
        KillBonusPlugin plugin = KillBonusPlugin.getInstance();
        if (plugin == null) return;
        if (ship.getOwner() == 0) {
            plugin.ships0.add(ship);
        } else {
            plugin.ships1.add(ship);
        }
    }

    // ---------- 优化后的减益插件 ----------
    private static class TrappedPreyScript extends BaseEveryFrameCombatPlugin {
        private final int owner;
        private final IntervalUtil interval = new IntervalUtil(0.25f, 0.25f);
        private final Map<ShipAPI, Float> weaponRangeCache = new HashMap<>(); // 缓存舰船最大武器射程

        public TrappedPreyScript(int owner) {
            this.owner = owner;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) return;

            interval.advance(amount);
            if (!interval.intervalElapsed()) return;

            // 清理武器射程缓存中已摧毁的舰船
            weaponRangeCache.keySet().removeIf(ship -> !ship.isAlive());

            for (ShipAPI enemy : engine.getShips()) {
                if (enemy.getOwner() == owner) continue; // 只检测敌方舰船
                if (!enemy.isAlive()) continue;

                int count = 0;
                Iterator<Object> iter = engine.getShipGrid().getCheckIterator(enemy.getLocation(), SCAN_SIZE, SCAN_SIZE);
                while (iter.hasNext()) {
                    Object obj = iter.next();
                    if (!(obj instanceof ShipAPI other)) continue;

                    // 只考虑己方（owner）存活的小型舰船，且必须装备本插件
                    if (other.getOwner() != owner) continue;
                    if (!other.isAlive()) continue;
                    if (other.getParentStation() != null) continue;
                    if (other.isFighter()) continue; // 排除战斗机
                    if (!other.isFrigate() && !other.isDestroyer()) continue; // 只考虑护卫舰/驱逐舰
                    //if (!other.getVariant().hasHullMod(CR_Venari.MOD_ID)) continue; // 必须装备本插件

                    if (isInRange(other, enemy)) {
                        count++;
                    }
                }

                MutableShipStatsAPI stats = enemy.getMutableStats();
                String buffId = DEBUFF_ID_PREFIX + owner; // 使用阵营唯一ID
                if (count >= DEBUFF_TRIGGER_COUNT) {
                    // 施加减益
                    stats.getRecoilDecayMult().modifyMult(buffId, RECOIL_DECAY_MULT);
                    stats.getRecoilPerShotMult().modifyMult(buffId, RECOIL_PER_SHOT_MULT);
                    stats.getMaxRecoilMult().modifyMult(buffId, MAX_RECOIL_MULT);

                    stats.getMaxSpeed().modifyMult(buffId, SPEED_MULT);
                    stats.getAcceleration().modifyMult(buffId, ACCEL_MULT);
                    stats.getDeceleration().modifyMult(buffId, ACCEL_MULT);
                    stats.getTurnAcceleration().modifyMult(buffId, TURN_ACCEL_MULT);
                    stats.getMaxTurnRate().modifyMult(buffId, TURN_RATE_MULT);

                    stats.getHullDamageTakenMult().modifyMult(buffId, DAMAGE_TAKEN_MULT);
                    stats.getArmorDamageTakenMult().modifyMult(buffId, DAMAGE_TAKEN_MULT);
                    stats.getShieldDamageTakenMult().modifyMult(buffId, DAMAGE_TAKEN_MULT);
                } else {
                    // 移除减益
                    stats.getRecoilDecayMult().unmodify(buffId);
                    stats.getRecoilPerShotMult().unmodify(buffId);
                    stats.getMaxRecoilMult().unmodify(buffId);

                    stats.getMaxSpeed().unmodify(buffId);
                    stats.getAcceleration().unmodify(buffId);
                    stats.getDeceleration().unmodify(buffId);
                    stats.getTurnAcceleration().unmodify(buffId);
                    stats.getMaxTurnRate().unmodify(buffId);

                    stats.getHullDamageTakenMult().unmodify(buffId);
                    stats.getArmorDamageTakenMult().unmodify(buffId);
                    stats.getShieldDamageTakenMult().unmodify(buffId);
                }
            }
        }

        /** 判断ship能否用武器攻击到target（考虑武器最大射程，并限制上限） */
        private boolean isInRange(ShipAPI ship, ShipAPI target) {
            // 从缓存获取最大射程，若不存在则计算并缓存
            Float range = weaponRangeCache.get(ship);
            if (range == null) {
                range = computeMaxWeaponRange(ship);
                weaponRangeCache.put(ship, range);
            }
            float distance = MathUtils.getDistance(ship, target);
            return distance <= range;
        }

        /** 计算舰船所有非导弹/协同武器的最大射程，并限制不超过MAX_WEAPON_RANGE_CAP */
        private float computeMaxWeaponRange(ShipAPI ship) {
            float maxRange = 0f;
            for (WeaponAPI weapon : ship.getAllWeapons()) {
                WeaponAPI.WeaponType type = weapon.getType();
                if (type == WeaponAPI.WeaponType.MISSILE || type == WeaponAPI.WeaponType.SYNERGY) continue;
                if (weapon.getRange() > maxRange) {
                    maxRange = weapon.getRange();
                }
            }
            return Math.min(maxRange, MAX_WEAPON_RANGE_CAP);
        }
    }

    // ---------- 击杀增益插件（使用常量优化） ----------
    private static class KillBonusPlugin extends BaseEveryFrameCombatPlugin {
        private static final String KEY = "cr_venari_kill_bonus_plugin";
        private CombatEngineAPI engine;
        public Set<ShipAPI> ships0 = new HashSet<>(); // 阵营0存活且有船插的船
        public Set<ShipAPI> ships1 = new HashSet<>(); // 阵营1存活且有船插的船
        private final Set<ShipAPI> processedHulks = new HashSet<>(); // 已处理过的残骸，避免重复触发

        public static KillBonusPlugin getInstance() {
            if (Global.getCombatEngine() == null) return null;
            KillBonusPlugin plugin = (KillBonusPlugin) Global.getCombatEngine().getCustomData().get(KEY);
            if (plugin == null) {
                plugin = new KillBonusPlugin();
                Global.getCombatEngine().addPlugin(plugin);
                Global.getCombatEngine().getCustomData().put(KEY, plugin);
            }
            return plugin;
        }

        @Override
        public void init(CombatEngineAPI engine) {
            this.engine = engine;
            // 预填开局时已存在的残骸，避免错误触发
            for (ShipAPI ship : engine.getShips()) {
                if (ship.isHulk()) {
                    processedHulks.add(ship);
                }
            }
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (engine == null) return;

            // 清理两个集合中已残骸或脱离战斗的船
            cleanSet(ships0);
            cleanSet(ships1);

            // 检测新的残骸（敌方舰船被摧毁）
            for (ShipAPI ship : engine.getShips()) {
                if (ship.isHulk() && !processedHulks.contains(ship)) {
                    processedHulks.add(ship);
                    int owner = ship.getOriginalOwner(); // 残骸的原始所属阵营
                    if (owner == 0) {
                        // 阵营0的船被摧毁，为阵营1的友方船加增益
                        applyBonusToSet(ships1);
                    } else if (owner == 1) {
                        // 阵营1的船被摧毁，为阵营0的友方船加增益
                        applyBonusToSet(ships0);
                    }
                    // 其他阵营（如中立）忽略
                }
            }
        }

        private void cleanSet(Set<ShipAPI> set) {
            set.removeIf(ship -> ship.isHulk() || !engine.isEntityInPlay(ship));
        }

        private void applyBonusToSet(Set<ShipAPI> set) {
            for (ShipAPI ship : set) {
                addPeakTime(ship);
                addDamageBonus(ship);
            }
        }

        private void addPeakTime(ShipAPI ship) {
            if (ship.getMutableStats().getPeakCRDuration().getMult() <= 0f) return; // 无限峰值时间的船不处理
            Float current = (Float) ship.getCustomData().get(KEY + "_peak");
            if (current == null) current = 0f;
            current += PEAK_TIME_INCREMENT;
            ship.setCustomData(KEY + "_peak", current);
            ship.getMutableStats().getPeakCRDuration().modifyFlat(CR_Circumvenire.KILL_BONUS_ID, current);
        }

        private void addDamageBonus(ShipAPI ship) {
            Float currentPercent = (Float) ship.getCustomData().get(KEY + "_dmg");
            if (currentPercent == null) currentPercent = 0f;
            currentPercent += DAMAGE_INCREMENT;
            if (currentPercent > MAX_DAMAGE_BONUS) currentPercent = MAX_DAMAGE_BONUS;
            ship.setCustomData(KEY + "_dmg", currentPercent);
            ship.getMutableStats().getEnergyWeaponDamageMult().modifyPercent(CR_Circumvenire.KILL_BONUS_ID, currentPercent);
            ship.getMutableStats().getBallisticWeaponDamageMult().modifyPercent(CR_Circumvenire.KILL_BONUS_ID, currentPercent);
        }
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize,
                                          ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        Color good = Misc.getPositiveHighlightColor();

        tooltip.addSectionHeading("效果", Alignment.MID, opad);

        // 机动增益
        tooltip.addPara("舰船舰体强度 %s ", opad ,bad ,"减半");
        tooltip.addPara("舰船加速度、减速度、转向加速度、最大转向率 %s", 5f, good, "+50%");

        tooltip.addPara("每当敌方一艘舰船被摧毁，本舰船获得增益：", opad);
        tooltip.addPara("峰值时间 +%s 秒", 5f, good, String.valueOf((int) PEAK_TIME_INCREMENT));
        tooltip.addPara("能量和实弹武器伤害 %s（每次击杀叠加，上限 %s）", 5f, good, "+1%", "+10%");

        tooltip.addPara("在本舰参与的围猎行动中”。", opad);
        tooltip.addPara("若在敌方舰船周围 %s 单位范围内有至少 %s 友方舰船时，对该敌方舰船施加减益：",
                5f, h, String.valueOf((int) SCAN_SIZE), String.valueOf(DEBUFF_TRIGGER_COUNT));
        //tooltip.addPara("最大速度、加速度/减速度、转向加速度/转向率 %s", 5f, bad, "-15%");
        //tooltip.addPara("后坐力衰减 %s，每发射击后坐力 %s，最大后坐力 %s", 5f, bad, "-20%", "+20%", "+20%");
        //tooltip.addPara("受到的伤害 %s", 5f, bad, "+5%");


        // 按住F3显示详细说明
        tooltip.addPara("按住 %s 查看详细机制", opad, h, "F3");
        if (Keyboard.isKeyDown(Keyboard.KEY_F3)) {
            tooltip.addSectionHeading("详细机制", Alignment.MID, opad);

            //tooltip.addPara("猎手效应触发判定：", 5f);
            //tooltip.addPara("  以敌方舰船为中心，扫描 %s x %s 矩形区域，统计区域内本阵营存活的小型舰船（护卫舰/驱逐舰，不含战斗机）。",3f, h, String.valueOf((int) SCAN_SIZE), String.valueOf((int) SCAN_SIZE));
            //tooltip.addPara("  若数量 ≥ %s，则对该敌方舰船施加所有减益；否则移除减益。", 3f, h, String.valueOf(DEBUFF_TRIGGER_COUNT));
            tooltip.addPara("减益机制：", opad);
            tooltip.addPara("速度/机动相关：x0.85（-15%%）", 5f);
            tooltip.addPara("后坐力衰减：x0.80（-20%%）", 5f);
            tooltip.addPara("每发射击后坐力：x1.20（+20%%）", 5f);
            tooltip.addPara("最大后坐力：x1.20（+20%%）", 5f);
            tooltip.addPara("受到的伤害：x1.05（+5%%）", 5f);

            tooltip.addPara("猎杀奖励：", opad);
            tooltip.addPara("每一艘敌方舰船被摧毁，本舰获得永久增益叠加。", 5f);
            tooltip.addPara(" 峰值时间每次 +%s 秒（平加），武器伤害每次 +%s%%（百分比加算，上限 %s%%）。", 5f, h, String.valueOf(PEAK_TIME_INCREMENT), String.valueOf(DAMAGE_INCREMENT), String.valueOf(MAX_DAMAGE_BONUS));
        }
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null) return false;
        if(ship.getVariant().hasHullMod("CR_Votum")) return false;
        if(ship.getVariant().hasHullMod("CR_Retinere")) return false;
        if(ship.isStationModule()) return false;
        return ship.getVariant().hasHullMod("CrusadersCore");
    }

    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) return "船只不存在";
        if(ship.getVariant().hasHullMod("CR_Votum")) return "舰船系统已被覆写";
        if(ship.getVariant().hasHullMod("CR_Retinere")) return "舰船系统已被覆写";
        if (!ship.getVariant().hasHullMod("CrusadersCore")) return "需要十字军核心";
        if(ship.isStationModule()) return "不能安装于舰船模块上";
        return null;
    }

}