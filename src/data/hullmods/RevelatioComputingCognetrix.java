package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.impl.campaign.skills.BaseSkillEffectDescription;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * 战后消耗CR12%，战备恢复3%，恢复成本75，部署点40，巅峰时间900，船员配额450，主力舰，装配点575，维护消耗50，载货量500，最大载员1000，必要船员450，燃料容量750，，最大宇宙航速8，侦测/探测范围150，结构27500，装甲值1750，全盾，角度360，护盾维持400，盾效0.7，幅能容量20000，散耗1200，最高航速50
 * 4大导弹（死雷），7中实弹/能量混合（高打），14小槽实弹（火神炮），内置激光蓄力1.5秒发射三秒共计造成5000伤害，装弹25秒
 * <p>
 * 天启计算阵列:武器开火所需幅能减少10%，3000米直径内敌人被摧毁时就回复自身5%结构值5%装甲值，每损失5%镀层值就提升1%伤害，无上限叠加。
 */
public class RevelatioComputingCognetrix extends BaseHullMod {
    private static final int FLUX_COST_DECREASE = 15;
    private static final int HP_HEAL = 10;
    private static final int ARMOR_HEAL = 10;
    private static final int PLATING_LOST = 5;  // 修改：改为PLATING_LOST
    private static final int DAMAGE_INCREASE = 1;

    private static final int decModL = -5;
    private static final int decModS = -3;

    private static final float ARC_BASE_CHANCE = 5f;    // 基础触发概率 5%
    private static final float ARC_MAX_CHANCE = 35f;    // 最大触发概率 35%
    private static final float ARC_DAMAGE_MULT = 0.5f;  // 电弧伤害系数 (50%)
    private static final float ARC_RANGE = 300f;        // 电弧跳跃距离
    private static final float ARC_EMP_DAMAGE = 200f;   // 电弧EMP伤害
    // 电弧EMP持续时间
    private static final Color ARC_COLOR = new Color(100, 255, 240, 255); // 电弧颜色

    private static final int[] LARGE_WEAPON_OP_MODS = {-8, -6, -4, -2, 0, 2, 4, 6, 8, 10};

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // 修正幅能消耗计算：应该为 (100 - FLUX_COST_DECREASE) * 0.01f
        stats.getBallisticWeaponFluxCostMod().modifyMult(id, (100 - FLUX_COST_DECREASE) * 0.01f);
        stats.getEnergyWeaponFluxCostMod().modifyMult(id, (100 - FLUX_COST_DECREASE) * 0.01f);
        stats.getMissileWeaponFluxCostMod().modifyMult(id, (100 - FLUX_COST_DECREASE) * 0.01f);

        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.LARGE_BALLISTIC_MOD)
                .modifyFlat(id + "_large_op", decModL);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.LARGE_ENERGY_MOD)
                .modifyFlat(id + "_large_op", decModL);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.LARGE_MISSILE_MOD)
                .modifyFlat(id + "_large_op", decModL);

        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.SMALL_BALLISTIC_MOD)
                .modifyFlat(id + "_small_op", decModS);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.SMALL_ENERGY_MOD)
                .modifyFlat(id + "_small_op", decModS);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.SMALL_MISSILE_MOD)
                .modifyFlat(id + "_small_op", decModS);

    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        Global.getCombatEngine().getListenerManager().addListener(new hullModListener(ship));


        //int largeWeaponCount = countLargeWeapons(ship.getMutableStats().getVariant());
        //applyLargeWeaponOPMod(ship.getMutableStats(), id, largeWeaponCount);

    }

    public boolean affectsOPCosts() {
        return true;
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!ship.isAlive()) return;

        // 检查是否有十字军镀层系统
        boolean hasCrusadersPlating = ship.getVariant().hasHullMod("CrusadersPlating");
        Boolean shutdown = (Boolean) ship.getCustomData().get("crusaders_shutdown");

        // 如果没有十字军镀层，使用原来的HP机制
        if (!hasCrusadersPlating && !shutdown) {
            // 原来的HP机制（保持兼容性）
            applyHPBasedDamageBoost(ship);
            return;
        }

        for (ShipAPI target : Global.getCombatEngine().getShips()) {
            if (target != null && target.isAlive() && !target.isFighter() && !target.isDrone() && target.getOwner() != ship.getOwner()) {
                // 检查是否已经添加了电弧监听器
                boolean hasArcListener = false;
                for (DamageListener listener : target.getListeners(DamageListener.class)) {
                    if (listener instanceof ArcDamageListener) {
                        hasArcListener = true;
                        break;
                    }
                }
                if (!hasArcListener) {
                    target.addListener(new ArcDamageListener(target, ship));
                }
            }
        }


        // 使用镀层容量机制
        applyPlatingBasedDamageBoost(ship);
    }

    private int countLargeWeapons(ShipVariantAPI variant) {
        int count = 0;
        for (String slotId : variant.getNonBuiltInWeaponSlots()) {
            String weaponId = variant.getWeaponId(slotId);
            if (weaponId != null && !weaponId.isEmpty()) {
                WeaponSpecAPI weaponSpec = Global.getSettings().getWeaponSpec(weaponId);
                if (weaponSpec.getSize() == WeaponAPI.WeaponSize.LARGE) {
                    count++;
                }
            }
        }
        return Math.min(count, 8); // 最大限制为8个
    }

    private void applyLargeWeaponOPMod(MutableShipStatsAPI stats, String id, int largeWeaponCount) {
        if (largeWeaponCount == 0) return;

        // 获取OP修正值（索引需要减1，因为数组从0开始）
        int opMod = 0;
        if (largeWeaponCount >= 1 && largeWeaponCount <= 8) {
            opMod = LARGE_WEAPON_OP_MODS[largeWeaponCount - 1];
        } else if (largeWeaponCount > 8) {
            opMod = LARGE_WEAPON_OP_MODS[7]; // 使用第8个武器的修正值
        }

        // 应用OP修正到大型武器
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.LARGE_BALLISTIC_MOD)
                .modifyFlat(id + "_large_op", opMod);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.LARGE_ENERGY_MOD)
                .modifyFlat(id + "_large_op", opMod);
        stats.getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.LARGE_MISSILE_MOD)
                .modifyFlat(id + "_large_op", opMod);
    }

    // 原来的HP机制（保持向后兼容）
    private void applyHPBasedDamageBoost(ShipAPI ship) {
        float lastFrameHP;
        if (ship.getCustomData().containsKey("lastFrameHP")) {
            lastFrameHP = (float) ship.getCustomData().get("lastFrameHP");
        } else {
            lastFrameHP = ship.getMaxHitpoints();
        }
        float recordLostHP;
        if (ship.getCustomData().containsKey("recordLostHP")) {
            recordLostHP = (float) ship.getCustomData().get("recordLostHP");
        } else {
            recordLostHP = 0;
        }
        int increaseDamage;
        if (ship.getCustomData().containsKey("increaseDamage")) {
            increaseDamage = (int) ship.getCustomData().get("increaseDamage");
        } else {
            increaseDamage = 0;
        }
        float currentHP = ship.getHitpoints();
        if (currentHP < lastFrameHP) {
            recordLostHP += lastFrameHP - currentHP;
            float lost_ = ship.getMaxHitpoints() * PLATING_LOST * 0.01f;
            while (recordLostHP > lost_) {
                recordLostHP -= lost_;
                increaseDamage += DAMAGE_INCREASE;
            }
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        if (ship == engine.getPlayerShip()) {
            String id = spec.getId() + "StatusData";
            if (!engine.getCustomData().containsKey(id)) {
                engine.getCustomData().put(id, new HashMap<ShipAPI, Object>());
            }
            Map<ShipAPI, Object> StatusDataKey = (Map<ShipAPI, Object>) engine.getCustomData().get(id);
            if (!StatusDataKey.containsKey(ship)) {
                Object[] array = new Object[1];
                for (int i = 0; i < array.length; i++) {
                    array[i] = new Object();
                }
                StatusDataKey.put(ship, array);
            }
            String spritename = "graphics/icons/hullsys/high_energy_focus.png";
            engine.maintainStatusForPlayerShip(((Object[]) StatusDataKey.get(ship))[0], spritename,
                    spec.getDisplayName(), "Damage increased" + increaseDamage + "%", false);
        }
        MutableShipStatsAPI stats = ship.getMutableStats();
        stats.getDamageToTargetHullMult().modifyPercent(spec.getId(), increaseDamage);
        stats.getDamageToTargetShieldsMult().modifyPercent(spec.getId(), increaseDamage);
        ship.setCustomData("lastFrameHP", currentHP);
        ship.setCustomData("recordLostHP", recordLostHP);
        ship.setCustomData("increaseDamage", increaseDamage);
    }

    // 新的镀层容量机制
    private void applyPlatingBasedDamageBoost(ShipAPI ship) {
        // 获取镀层数据
        Float currentPlating = (Float) ship.getCustomData().get("crusaders_currenthpplating");
        Float maxPlating = (Float) ship.getCustomData().get("crusaders_maxhpplating");

        // 如果镀层数据不存在，使用HP机制
        if (currentPlating == null || maxPlating == null) {
            applyHPBasedDamageBoost(ship);
            return;
        }

        // 获取上一帧的镀层容量
        Float lastFramePlating;
        if (ship.getCustomData().containsKey("lastFramePlating")) {
            lastFramePlating = (Float) ship.getCustomData().get("lastFramePlating");
        } else {
            lastFramePlating = maxPlating;
        }

        // 获取累积损失镀层容量
        Float recordLostPlating;
        if (ship.getCustomData().containsKey("recordLostPlating")) {
            recordLostPlating = (Float) ship.getCustomData().get("recordLostPlating");
        } else {
            recordLostPlating = 0f;
        }

        // 获取伤害提升
        int increaseDamage;
        if (ship.getCustomData().containsKey("increaseDamage")) {
            increaseDamage = (int) ship.getCustomData().get("increaseDamage");
        } else {
            increaseDamage = 0;
        }

        // 检查镀层是否关闭
        Boolean shutdown = (Boolean) ship.getCustomData().get("crusaders_shutdown");
        if (shutdown == null) shutdown = false;

        // 只有镀层未关闭时才计算损失
        if (!shutdown && currentPlating < lastFramePlating) {
            float platingLost = lastFramePlating - currentPlating;
            recordLostPlating += platingLost;

            // 每损失3%最大镀层容量，伤害提升1%
            float lostThreshold = maxPlating * PLATING_LOST * 0.01f;
            while (recordLostPlating > lostThreshold) {
                recordLostPlating -= lostThreshold;
                increaseDamage += DAMAGE_INCREASE;
            }
        }

        // 更新自定义数据
        ship.setCustomData("lastFramePlating", currentPlating);
        ship.setCustomData("recordLostPlating", recordLostPlating);
        ship.setCustomData("increaseDamage", increaseDamage);

        // 应用伤害提升
        MutableShipStatsAPI stats = ship.getMutableStats();
        stats.getDamageToTargetHullMult().modifyPercent(spec.getId(), increaseDamage);
        stats.getDamageToTargetShieldsMult().modifyPercent(spec.getId(), increaseDamage);

        // 为玩家显示状态信息
        CombatEngineAPI engine = Global.getCombatEngine();
        if (ship == engine.getPlayerShip()) {
            String id = spec.getId() + "StatusData";
            if (!engine.getCustomData().containsKey(id)) {
                engine.getCustomData().put(id, new HashMap<ShipAPI, Object>());
            }
            Map<ShipAPI, Object> StatusDataKey = (Map<ShipAPI, Object>) engine.getCustomData().get(id);
            if (!StatusDataKey.containsKey(ship)) {
                Object[] array = new Object[1];
                for (int i = 0; i < array.length; i++) {
                    array[i] = new Object();
                }
                StatusDataKey.put(ship, array);
            }
            String spritename = "graphics/icons/hullsys/high_energy_focus.png";
            String description;
            if (shutdown) {
                description = "Plating is off - damage increased" + increaseDamage + "%";
            } else {
                description = "Plating:" + Math.round(currentPlating) + "/" + Math.round(maxPlating) +
                        "- Damage increased" + increaseDamage + "%";
            }
            engine.maintainStatusForPlayerShip(((Object[]) StatusDataKey.get(ship))[0], spritename,
                    spec.getDisplayName(), description, false);
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return FLUX_COST_DECREASE + "%";
        }
        if (index == 1) {
            return HP_HEAL + "%";
        }
        if (index == 2) {
            return ARMOR_HEAL + "%";
        }
        if (index == 3) {
            return PLATING_LOST + "%";
        }
        if (index == 4) {
            return DAMAGE_INCREASE + "%";
        }
        return null;
    }

    private static class hullModListener implements DamageListener {
        final ShipAPI ship;
        final Set<CombatEntityAPI> recorder = new HashSet<>();
        private static final float HEAL_RANGE = 3000f; // 添加治疗范围常量

        private hullModListener(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
            // 条件1: 伤害来源必须是本舰
            if (source != ship) {
                return;
            }

            // 条件2: 目标必须是ShipAPI
            if (!(target instanceof ShipAPI targetShp)) {
                return;
            }

            // 条件3: 避免重复触发
            if (recorder.contains(target)) {
                return;
            }

            // 条件4: 必须是敌对关系
            if (target.getOwner() + ship.getOwner() != 1) {
                return;
            }

            // 条件5: 排除特定类型的无人机
            if (targetShp.getHullSpec().getHullId().contains("dem_drone")) {
                return;
            }

            // 条件6: 添加距离检查 - 目标必须在3000范围内
            float distance = Vector2f.sub(ship.getLocation(), target.getLocation(), null).length();
            if (distance > HEAL_RANGE) {
                return;
            }

            // 条件7: 目标必须被击毁
            float hp = target.getHitpoints();
            float damage = result.getDamageToHull();
            if (hp <= damage) {
                recorder.add(target);

                // 恢复自身结构值
                float currentHP = ship.getHitpoints();
                float maxHP = ship.getMaxHitpoints();
                float hpHealAmount = maxHP * HP_HEAL * 0.01f;
                float newHP = Math.min(maxHP, currentHP + hpHealAmount);
                ship.setHitpoints(newHP);

                // 恢复所有装甲格装甲值
                ArmorGridAPI grid = ship.getArmorGrid();
                if (grid != null) {
                    float maxArmor = grid.getMaxArmorInCell();
                    if (maxArmor > 0) {
                        float[][] cells = grid.getGrid();
                        int x = cells.length;
                        int y = cells[0].length;

                        for (int i = 0; i < x; i++) {
                            for (int j = 0; j < y; j++) {
                                float currentArmor = grid.getArmorValue(i, j);
                                float armorHealAmount = maxArmor * ARMOR_HEAL * 0.01f;
                                float newArmor = Math.min(maxArmor, currentArmor + armorHealAmount);
                                grid.setArmorValue(i, j, newArmor);
                            }
                        }
                    }
                }

                // 可选：添加视觉反馈
                CombatEngineAPI engine = Global.getCombatEngine();
                if (engine != null) {

                    // 添加治疗特效
                    engine.addHitParticle(
                            ship.getLocation(),
                            new Vector2f(),
                            50f, // 尺寸
                            1.0f, // 亮度
                            0.5f, // 持续时间
                            new Color(100, 255, 100, 150) // 绿色治疗特效
                    );
                }
            }
        }
    }

    private class ArcDamageListener implements DamageListener {
        private final ShipAPI target;
        private final ShipAPI ship;
        private final Random random = new Random();
        private float cachedArcChance = 0f;

        public ArcDamageListener(ShipAPI targets, ShipAPI ship) {
            target = targets;
            this.ship = ship;
        }

        @Override
        public void reportDamageApplied(Object source, CombatEntityAPI targets, ApplyDamageResultAPI result) {
            // 确保伤害来源是本舰
            if (source instanceof ShipAPI) {
                if (source != ship) {
                    return;
                }
            } else {
                // 如果不是ShipAPI来源（比如导弹、战机等），且不是本舰发射的，则返回
                // 注意：这里假设电弧只由本舰直接攻击触发，不包括导弹等
                return;
            }

            float totalDamage = result.getDamageToHull() + result.getDamageToPrimaryArmorCell() + result.getDamageToShields();
            // 如果是DPS伤害或者总伤害小于等于35，则不触发
            if (result.isDps() || totalDamage <= 35) return;

            // 获取或计算电弧触发概率
            float arcChance = getArcChance();

            // 概率触发电弧
            if (random.nextFloat() * 100f < arcChance) {
                triggerArc(target, totalDamage);
            }
        }


        /**
         * 获取电弧触发概率
         */
        private float getArcChance() {
            // 如果已缓存，直接返回
            if (cachedArcChance > 0) {
                return cachedArcChance;
            }

            // 计算武器总OP
            int weaponOP = calculateTotalWeaponOP(ship);

            // 计算电弧概率
            cachedArcChance = calculateArcChance(weaponOP);

            return cachedArcChance;
        }

        /**
         * 触发电弧效果
         */
        private void triggerArc(ShipAPI target, float totalDamage) {
            CombatEngineAPI engine = Global.getCombatEngine();

            // 选择电弧起点（从本舰随机武器位置）
            Vector2f sourcePoint = getRandomWeaponLocation(ship);

            // 选择电弧终点（到目标舰随机位置）
            Vector2f targetPoint = getRandomShipLocation(target);

            // 计算电弧伤害（基于原始伤害的50%）
            float arcDamage = totalDamage * ARC_DAMAGE_MULT;

            // 生成电弧效果
            if (sourcePoint != null && targetPoint != null) {
                // 创建简单实体作为电弧目标点
                CombatEntityAPI arcTarget = new SimpleEntity(targetPoint);

                // 生成EMP电弧
                engine.spawnEmpArc(
                        ship,                    // 来源
                        sourcePoint,             // 起点
                        ship,                    // 起点实体
                        arcTarget,               // 目标
                        DamageType.ENERGY,       // 伤害类型
                        arcDamage,               // 伤害
                        ARC_EMP_DAMAGE,          // EMP伤害
                        100000f,                 // 最大范围
                        "tachyon_lance_emp_impact", // 音效
                        ARC_RANGE / 50f,         // 电弧厚度
                        ARC_COLOR,               // 电弧颜色
                        Color.white              // 淡出颜色
                );

                // 视觉特效
                engine.addHitParticle(
                        sourcePoint,
                        new Vector2f(),
                        ARC_RANGE,
                        1f,
                        0.1f,
                        ARC_COLOR
                );

                // 可选：显示电弧触发提示
                if (ship == engine.getPlayerShip()) {
                    engine.addFloatingText(
                            ship.getLocation(),
                            "Arc trigger!",
                            20f,
                            Color.CYAN,
                            ship,
                            1f,
                            1f
                    );
                }
            }
        }

        /**
         * 获取随机武器位置
         */
        private Vector2f getRandomWeaponLocation(ShipAPI ship) {
            // 优先使用SYSTEM类型的武器槽位
            List<WeaponSlotAPI> systemSlots = new ArrayList<>();

            // 获取所有武器槽位并筛选SYSTEM类型
            for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
                if (slot.getWeaponType() == WeaponAPI.WeaponType.SYSTEM) {
                    systemSlots.add(slot);
                }
            }

            // 如果有SYSTEM槽位，随机选择一个
            if (!systemSlots.isEmpty()) {
                WeaponSlotAPI randomSlot = systemSlots.get(random.nextInt(systemSlots.size()));
                // 计算槽位在当前船体上的位置
                return randomSlot.computePosition(ship);
            }

            // 如果没有SYSTEM槽位，则使用所有已安装的武器作为备选
            List<WeaponAPI> weapons = ship.getAllWeapons();
            if (!weapons.isEmpty()) {
                WeaponAPI randomWeapon = weapons.get(random.nextInt(weapons.size()));
                return randomWeapon.getLocation();
            }

            // 如果连武器都没有，返回船体中心
            return ship.getLocation();
        }

        /**
         * 获取舰船随机位置
         */
        private Vector2f getRandomShipLocation(ShipAPI ship) {
            Vector2f location = ship.getLocation();
            float radius = Math.max(ship.getCollisionRadius() * 0.5f, 50f);

            float angle = random.nextFloat() * 360f;
            float distance = random.nextFloat() * radius;

            Vector2f offset = new Vector2f(
                    (float) Math.cos(Math.toRadians(angle)) * distance,
                    (float) Math.sin(Math.toRadians(angle)) * distance
            );

            return Vector2f.add(location, offset, null);
        }
    }

    private float calculateArcChance(int weaponOP) {
        // 设定OP范围：假设0-400 OP对应5%-35%概率
        float minOP = 0f;
        float maxOP = 250f; // 可以根据需要调整这个值

        float normalizedOP = Math.min(Math.max(weaponOP, minOP), maxOP);

        return ARC_BASE_CHANCE + (ARC_MAX_CHANCE - ARC_BASE_CHANCE) * (normalizedOP / maxOP);
    }


    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize,
                                          ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 3f;
        float opad = 10f;

        // 获取颜色
        Color highlightColor = com.fs.starfarer.api.util.Misc.getHighlightColor();
        Color positiveColor = com.fs.starfarer.api.util.Misc.getPositiveHighlightColor();
        Color grayColor = com.fs.starfarer.api.util.Misc.getGrayColor();

        tooltip.addPara("The Apocalypse Computing Array is an advanced combat analysis system that can optimize weapon systems in real time during combat, analyze enemy and friendly damage, and continuously improve combat efficiency through adaptive algorithms. This system is particularly suitable for protracted wars and wars of attrition, and can become stronger with each war.",
                grayColor, opad);

        tooltip.addSectionHeading("Core functions", Alignment.MID, opad);

        // 核心效果
        tooltip.addPara("Amplitude energy optimization system:", opad);
        tooltip.addPara("• All weapon energy costs reduced by %s", pad,
                highlightColor, FLUX_COST_DECREASE + "%");
        tooltip.addPara("• Large weapon Ordnance Point cost reduced by %s", 0,
                positiveColor, Math.abs(decModL) + "");
        tooltip.addPara("• Small weapon Ordnance Point cost reduced by %s", pad,
                positiveColor, Math.abs(decModS) + "");

        // 战利品回收系统
        tooltip.addPara("Loot recovery system:", opad);
        tooltip.addPara("• When destroying enemies within 3000 meters:", 0);
        tooltip.addPara("↳Restore %s hull integrity", 0,
                positiveColor, HP_HEAL + "%");
        tooltip.addPara("↳ Restores %s armor across the armor grid", pad,
                positiveColor, ARMOR_HEAL + "%");

        // 自适应伤害增幅
        tooltip.addPara("Adaptive damage increase:", opad);
        tooltip.addPara("• Damage increased by %s for every %s hull/plating integrity lost", 0,
                highlightColor, PLATING_LOST + "%", DAMAGE_INCREASE + "%");
        tooltip.addPara("• Damage boosting effect:", 0);
        tooltip.addPara("↳ Unlimited stacking", 0, positiveColor);
        tooltip.addPara("↳ Also effective against fighters and shields", 0, positiveColor);
        tooltip.addPara("↳ Real-time updates, no need to wait", pad, positiveColor);

        // 电弧连锁系统
        tooltip.addPara("Arc interlocking system:", opad);
        tooltip.addPara("• There is a probability of triggering an arc when the weapon attacks:", 0);
        tooltip.addPara("↳ Base probability: %s", 0,
                highlightColor, ARC_BASE_CHANCE + "%");
        tooltip.addPara("↳ Maximum probability: %s", 0,
                highlightColor, ARC_MAX_CHANCE + "%");
        tooltip.addPara("• Arc effect:", 0);
        tooltip.addPara("↳ Damage: %s of original damage", 0,
                highlightColor, (int)(ARC_DAMAGE_MULT * 100) + "%");
        tooltip.addPara("↳ EMP damage: %s", 0,
                highlightColor, (int)ARC_EMP_DAMAGE + "");
        tooltip.addPara("↳ Jump distance: %s units", pad,
                highlightColor, (int)ARC_RANGE + "");
        tooltip.addPara("• The probability increases with weapon Ordnance Points (OP):", 0);
        tooltip.addPara("↳ The higher the total OP, the higher the probability of arc triggering", 0, positiveColor);
        tooltip.addPara("↳ Maximum OP limit: 400 OP corresponds to the maximum probability", pad, highlightColor);


        // 当前状态显示（如果有船引用）
        if (ship != null && !isForModSpec) {
            tooltip.addSectionHeading("Current status", Alignment.MID, opad);

            // 计算武器OP
            int weaponOP = calculateTotalWeaponOP(ship);
            float arcChance = calculateArcChance(weaponOP);


            // 获取伤害增幅
            int currentDamageBoost = 0;
            if (ship.getCustomData().containsKey("increaseDamage")) {
                currentDamageBoost = (int) ship.getCustomData().get("increaseDamage");
            }

            // 显示武器信息
            tooltip.addPara("Weapon system:", 0);
            tooltip.addPara("• Total weapon OP: %s OP", 0,
                    highlightColor, weaponOP + "");
            tooltip.addPara("• Arc trigger probability: %s", 0,
                    positiveColor, String.format("%.1f%%", arcChance));

            // 显示伤害增幅
            tooltip.addPara("Damage increase:", opad);
            tooltip.addPara("• Current increase: %s", 0,
                    positiveColor, currentDamageBoost + "%");

            // 如果安装了十字军镀层，显示镀层状态

            // 性能评估
            tooltip.addSectionHeading("Performance evaluation", Alignment.MID, opad);
            tooltip.addPara("• Arc system: %s", 0,
                    arcChance > ARC_BASE_CHANCE ? positiveColor : highlightColor,
                    arcChance > ARC_BASE_CHANCE ? "Optimized" : "base state");
            tooltip.addPara("• Weapon configuration: %s", pad,
                    weaponOP > 200 ? positiveColor : highlightColor,
                    weaponOP > 200 ? "High OP configuration" : "General configuration");
        }

        // 系统限制
        tooltip.addSectionHeading("System limitations", Alignment.MID, opad);
        tooltip.addPara("Loot recovery is only effective for targets within 3000 meters", 0, grayColor);
        tooltip.addPara("The arc requires a projectile with a certain power to trigger it.", 0, grayColor);
        tooltip.addPara("Damage amplification resets after combat", 0, grayColor);
    }

    private int calculateTotalWeaponOP(ShipAPI ship) {
        int totalOP = 0;

        // 遍历所有武器槽位（不包括内置武器）
        for (String slotId : ship.getVariant().getNonBuiltInWeaponSlots()) {
            String weaponId = ship.getVariant().getWeaponId(slotId);
            if (ship.getMutableStats().getVariant() != null && weaponId != null && !weaponId.isEmpty()) {
                MutableCharacterStatsAPI cStats = BaseSkillEffectDescription.getCommanderStats(ship.getMutableStats());
                totalOP += ship.getMutableStats().getVariant().computeWeaponOPCost(cStats);
            }
        }
        return totalOP;
    }
}