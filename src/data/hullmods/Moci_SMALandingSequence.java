package data.hullmods;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import org.lazywizard.lazylib.combat.AIUtils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.ai.Moci_ReturnToCarrierModule;
import data.scripts.ships.ai.Moci_MechTurretAI;
import data.scripts.util.Moci_AIRestoreUtil;
import data.scripts.util.Moci_TextLoader;

/**
 * Moci_LandingSequence - 智能着陆序列管理器
 *
 * 这个船体模组为机动战士（MS）提供自动着陆和修理功能。
 *
 * # AI操作的着陆条件
 *
 * AI控制的MS会在以下情况下自动触发着陆序列：
 *
 * 1. 血量条件：舰船血量低于70%
 * 2. 战备值条件：当前CR低于部署时CR的50%（最低40%）
 * 3. 弹药条件：所有主武器（最大尺寸的有限弹药武器）的弹药都低于或等于25%
 *
 * 着陆距离限制（仅对弹药不足情况生效）：
 * - 如果所有主武器都打空：按 10000f 进行全图搜索
 * - 如果部分主武器有弹药：基础搜索距离800单位 + 每个弹药为0的非主武器增加800单位
 * - 如果是血量或CR不足：按 10000f 进行全图搜索
 *
 * AI会自动搜索最近的可用航母（有空闲修理舱的友军舰船），并启动着陆AI进行自动着陆。
 *
 * # 玩家手动着陆操作
 *
 * 玩家可以通过以下步骤手动控制MS着陆：
 *
 * 1. 按 R 键锁定目标：选择一艘有整备湾（修理舱）的友军航母作为目标
 * 2. 按 U 键启用自动驾驶：启用自动驾驶后，MS会自动执行着陆序列
 * 3. 等待着陆完成：着陆AI会自动导航MS飞向航母并完成着陆
 *
 * 注意事项：
 * - 目标航母必须有可用的修理舱（未被占用）
 * - 必须启用自动驾驶才能执行着陆，纯手动操作无法着陆
 * - 如果目标航母不适合着陆，会显示"降落导航错误"提示
 * - 着陆过程中可以随时取消自动驾驶来中止着陆
 *
 * # 技术实现
 *
 * - 使用 Moci_LandingAI 替换舰船的默认AI来执行着陆序列
 * - 通过 Moci_RepairBayScript 管理航母的修理舱状态
 * - 支持标准舰载机式着陆（带动画）和直接固定位置着陆（无动画）两种模式
 * - 着陆完成后会自动恢复原始AI状态
 */
public class Moci_SMALandingSequence extends BaseHullMod {
    private static final boolean ENABLE_DEBUG_LOGS = false;
    private static final String TEXT_ID = "Moci_LandingSequence";
    private static final String MECH_TURRET_TEXT_ID = "Moci_MechTurretAI";
    private static final String LOG_PREFIX = "[MOCI_REFIT_ROUTE] ";
    private static final String WINGMAN_LOG_PREFIX = "[Wingman preparation]";
    private static final float REFIT_SEARCH_INTERVAL_MIN = 0.8f;
    private static final float REFIT_SEARCH_INTERVAL_MAX = 1.25f;
    private static final float FULL_MAP_REFIT_SEARCH_DISTANCE = 10000f;
    private static final String REFIT_SEARCH_CACHE_KEY = "Moci_AutomaticRefitTargetCache";
    private static final String REFIT_SEARCH_NEXT_TIME_KEY = "Moci_AutomaticRefitTargetNextCheck";
    private static final String RETURN_TO_CARRIER_MODULE_KEY = "Moci_ReturnToCarrierModuleInstance";
    private static final String WINGMAN_REFIT_LAST_NEED_STATE_KEY = "Moci_WingmanRefitLastNeedState";
    private static final String WINGMAN_REFIT_LAST_SEARCH_TEXT_KEY = "Moci_WingmanRefitLastSearchText";
    private static final String WINGMAN_REFIT_PENDING_SEARCH_TEXT_KEY = "Moci_WingmanRefitPendingSearchText";
    private static final String MAIN_AMMO_EMPTY_AT_KEY = "Moci_MainAmmoEmptyAt";
    public static final String LAST_LANDING_CARRIER_ID_KEY = "Moci_LastLandingCarrierId";
    public static final String LAST_LANDING_BAY_SLOT_ID_KEY = "Moci_LastLandingBaySlotId";
    public static final String LAST_LANDING_LOCATION_KEY = "Moci_LastLandingLocation";

    /**
     * 是否在整备判定中忽略内置武器。
     *
     * 旧逻辑等价于 true：
     * - SMALL / MEDIUM 的内置武器完全不参与“是否需要整备”的判断
     * - LARGE 的内置武器仍然参与判断
     *
     * 现在按你的需求，默认改为 false：
     * - 关闭该开关后，内置武器和普通武器一样参与整备判定
     * - 也就是说，内置武器的缺弹/空弹会影响是否返航整备
     */
    public static final boolean SKIP_BUILT_IN_WEAPONS_FOR_REFIT = false;

    /**
     * 主要的战斗逻辑推进方法
     * 每帧调用，负责监控舰船状态并触发着陆序列
     */
    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        advanceLandingLogic(ship, amount);
    }

    /**
     * 可复用的着陆逻辑入口。
     * 供旧的 LandingSequence 船插和新的 MobileSuits ID 卡共用，
     * 避免复制整套着陆/整备判定流程。
     */
    public static void advanceLandingLogic(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if(engine == null) return;
        if(!ship.isAlive()||!engine.isEntityInPlay(ship)) return;

        RS_Moci_MobileSuitRepairTracker tracker = RS_Moci_MobileSuitRepairTracker.get(ship);
        if (tracker != null) {
            tracker.advance(amount);
        }

        if (Moci_MechTurretAI.isDocked(ship) && areAllAmmoWeaponsEmpty(ship)) {
            Moci_MechTurretAI.releaseTurretTarget(ship);
        }

        // 检查是否需要恢复自动驾驶状态
        if (ship.getCustomData().containsKey("Moci_ShouldRestoreAI") &&
                (Boolean)ship.getCustomData().get("Moci_ShouldRestoreAI") == true &&
                ship.getCustomData().containsKey("Moci_OriginalAI")) {

            // 如果没有计时器，创建一个
            if (!ship.getCustomData().containsKey("Moci_AIRestoreTimer")) {
                ship.setCustomData("Moci_AIRestoreTimer", 0f);
            }

            // 安全获取计时器值
            Object timerObj = ship.getCustomData().get("Moci_AIRestoreTimer");
            float timer = 0f;
            if (timerObj instanceof Float) {
                timer = (Float)timerObj;
            }

            // 更新计时器
            timer += amount;
            ship.setCustomData("Moci_AIRestoreTimer", timer);

            // 1秒后恢复自动驾驶
            if (timer >= 1.0f) {
                // 恢复自动驾驶
                ShipAIPlugin originalAI = (ShipAIPlugin)ship.getCustomData().get("Moci_OriginalAI");
                if (originalAI != null && ship.getShipAI() == null) {
                    ship.setShipAI(originalAI);
                }

                // 清理标记
                ship.setCustomData("Moci_ShouldRestoreAI", false);
                ship.setCustomData("Moci_OriginalAI", null);
                ship.setCustomData("Moci_AIRestoreTimer", null);
            }
        }

        // 获取当前舰船的基本状态信息
        boolean isPlayerShip = ship.equals(engine.getPlayerShip());  // 是否为玩家控制的舰船
        ShipAPI selectedTarget = ship.getShipTarget();
        boolean selectedFriendly = selectedTarget != null && selectedTarget.getOwner() == ship.getOwner();
        Moci_RS_RepairBayScript.Moci_RepairBay automaticRefitTarget = findAutomaticRefitTarget(ship);
        boolean needsrefit = automaticRefitTarget != null;          // 是否应该触发修理
        boolean selectedCarrier = selectedFriendly;  // 是否选择了友方友军目标

        // 添加状态机监控 - 检查是否有LandingAI且是否卡住
        if (ship.getShipAI() instanceof Moci_RS_LandingAI) {
            Moci_RS_LandingAI landingAI = (Moci_RS_LandingAI) ship.getShipAI();

            // 检查是否需要中止着陆
            if (landingAI.shouldRemove()) {
                ship.setCustomData("Moci_LandingAIActive", false);
                Moci_AIRestoreUtil.restoreDefaultAI(ship, ship.getShipAI() != null ? ship.getShipAI().getConfig() : null);
            }
        }

        boolean landingAIActive = ship.getCustomData().containsKey("Moci_LandingAIActive")
                && ship.getCustomData().get("Moci_LandingAIActive") instanceof Boolean
                && (Boolean) ship.getCustomData().get("Moci_LandingAIActive");

        if(isPlayerShip){
            // === 玩家舰船的处理逻辑 ===
            if(selectedCarrier){
                Moci_MechTurretAI.advancePlayerNavigationModeSwitch(ship, selectedTarget);
                boolean landingAvailable = canManualLandOnTarget(ship, selectedTarget);
                boolean turretFeatureEnabled = Moci_MechTurretAI.shouldAssignTurretAI(ship);
                boolean turretAvailable = turretFeatureEnabled && Moci_MechTurretAI.canUseAsTurretHost(ship, selectedTarget);
                boolean allowLanding = shouldUseRefitModeForSelectedTarget(ship, selectedTarget, landingAvailable, turretAvailable);

                if(ship.getShipAI() != null && allowLanding) {
                    if(!ship.getCustomData().containsKey("Moci_LandingAIActive")
                            || (ship.getCustomData().containsKey("Moci_LandingAIActive")
                            && !(Boolean)ship.getCustomData().get("Moci_LandingAIActive"))) {
                        ship.setCustomData("Moci_LandingAIActive", true);
                        Moci_MechTurretAI.releaseTurretTarget(ship);
                        Moci_RS_RepairBayScript script = Moci_RS_RepairBayHullModEffect.ensureRepairBayScript(selectedTarget);
                        if (script != null) {
                            ship.setShipAI(new Moci_RS_LandingAI(ship, ship.getAIFlags(), ship.getShipAI().getConfig(), script.getVacancy()));
                        } else {
                            ship.setCustomData("Moci_LandingAIActive", false);
                        }
                    }
                } else if (!turretFeatureEnabled) {
                    if (landingAvailable) {
                        if (ship.getShipAI() == null) {
                            engine.maintainStatusForPlayerShip("Moci_CarrierTarget",
                                    Global.getSettings().getSpriteName("ui", "icon_tactical_bdeck"),
                                    Moci_TextLoader.getText(TEXT_ID, "status.landing_nav"),
                                    Moci_TextLoader.getText(TEXT_ID, "status.enable_autopilot"), true);
                        }
                    } else {
                        engine.maintainStatusForPlayerShip("Moci_ImproperCarrierTarget",
                                Global.getSettings().getSpriteName("ui", "icon_tactical_bdeck"),
                                Moci_TextLoader.getText(TEXT_ID, "status.landing_error"),
                                Moci_TextLoader.getText(TEXT_ID, "status.invalid_target"), true);
                    }
                }
            }else{
                // 玩家未选择目标，检查是否需要自动着陆
                if(ship.getShipAI()!=null&&(!ship.getCustomData().containsKey("Moci_LandingAIActive")||(ship.getCustomData().containsKey("Moci_LandingAIActive")&&!(Boolean)ship.getCustomData().get("Moci_LandingAIActive")))) {
                    if(needsrefit){
                        // 需要修理且有自动驾驶，搜索最近的可用航母
                        Moci_MechTurretAI.releaseTurretTarget(ship);
                        ship.setCustomData("Moci_LandingAIActive", true);
                        ship.setShipAI(new Moci_RS_LandingAI(ship, ship.getAIFlags(), ship.getShipAI().getConfig(), automaticRefitTarget));
                    }
                }
            }
        }else{
            // === AI舰船的处理逻辑 ===
            if(needsrefit && ship.getShipAI() != null && !landingAIActive){
                Moci_MechTurretAI.releaseTurretTarget(ship);
                clearReturnToCarrierModule(ship);
                RS_Moci_MobileSuitRepairTracker.getOrCreate(ship).beginApproach(automaticRefitTarget.getShip(), automaticRefitTarget);
                ship.setCustomData("Moci_LandingAIActive", true);
                ship.setShipTarget(automaticRefitTarget.getShip());
                ship.setShipAI(new Moci_RS_LandingAI(ship,ship.getAIFlags(), ship.getShipAI().getConfig(),automaticRefitTarget));
            } else {
                clearReturnToCarrierModule(ship);
            }
        }

        // 清理着陆AI状态（当AI被重置时）
        if(ship.getShipAI() == null){
            ship.setCustomData("Moci_LandingAIActive", false);
            clearReturnToCarrierModule(ship);
        }
    }

    /**
     * 搜索可用的着陆点
     * 在所有友军舰船中寻找最近的可用航母修理舱
     * 新增：基于非最大尺寸空弹药武器数量的距离限制（仅对弹药不足情况生效）
     *
     * @param ship 需要着陆的舰船
     * @return 找到的修理舱武器槽，如果没有找到则返回null
     */
    public static Moci_RS_RepairBayScript.Moci_RepairBay searchForLand(ShipAPI ship){
        ShipAPI carrier = null;
        float score = Float.MAX_VALUE;
        int allyCount = 0;
        int availableCarrierCount = 0;
        int distanceRejectedCount = 0;
        Map<String, Integer> rejectReasons = new HashMap<String, Integer>();

        // 检查是否因为弹药不足而需要着陆
        boolean isAmmoRefit = isAmmoRefitReason(ship);
        String preferredCarrierId = getRememberedLandingCarrierId(ship);
        String preferredBaySlotId = getRememberedLandingBaySlotId(ship);

        // 获取距离限制（仅在弹药不足且仍有主武器剩余弹药时应用）
        float maxSearchDistance = isAmmoRefit ? getMaxSearchDistance(ship) : FULL_MAP_REFIT_SEARCH_DISTANCE;
        String searchDistanceText = String.format("%.0f", maxSearchDistance);

        Global.getLogger(Moci_SMALandingSequence.class).debug(
                ship.getName() + "Search landing site, reason for landing:" + (isAmmoRefit ? "Not enough ammunition" : "Insufficient HP/CR") +
                        ", the maximum search distance:" + searchDistanceText
        );

        // 遍历所有友军舰船
        for(ShipAPI s: AIUtils.getAlliesOnMap(ship)){
            allyCount++;
            String rejectReason = getCarrierLandingBlockReason(s);
            if (rejectReason != null) {
                incrementReasonCount(rejectReasons, rejectReason);
                continue;
            }

            float currDist = Misc.getDistance(s.getLocation(),ship.getLocation());

            // 应用距离限制：仅在弹药不足且距离超过限制时跳过
            if (isAmmoRefit && currDist > maxSearchDistance) {
                distanceRejectedCount++;
                incrementReasonCount(rejectReasons, "distance_limit");
                Global.getLogger(Moci_SMALandingSequence.class).debug(
                        ship.getName() + "Skip a carrier that is too far away:" + s.getName() +
                                "(distance:" + String.format("%.0f", currDist) + "> Limitations:" + searchDistanceText + ")"
                );
                continue;
            }

            availableCarrierCount++;
            float candidateScore = currDist;
            if (preferredCarrierId != null && preferredCarrierId.equals(s.getId())) {
                candidateScore -= 1500f;
            }

            if(candidateScore < score){
                // 找到更近的可用航母
                carrier = s;
                score = candidateScore;
            }
        }

        String summary = carrier != null
                ? "Search: middle mother=" + carrier.getName()
                : "Search: empty because =" + formatRejectReasons(rejectReasons);
        ship.setCustomData(WINGMAN_REFIT_PENDING_SEARCH_TEXT_KEY, summary);

        if(carrier == null) {
            Global.getLogger(Moci_SMALandingSequence.class).debug(
                    ship.getName() + "No available landing site found (search distance limit:" + searchDistanceText + ")"
            );
            return null;
        }

        Global.getLogger(Moci_SMALandingSequence.class).debug(
                ship.getName() + "Find the landing target:" + carrier.getName() +
                        "(Score:" + String.format("%.0f", score) + ")"
        );

        // 返回找到的航母的空闲修理舱
        Moci_RS_RepairBayScript script = Moci_RS_RepairBayScript.getInstance(carrier);
        if (script == null) {
            return null;
        }
        return script.getVacancy(preferredCarrierId != null && preferredCarrierId.equals(carrier.getId()) ? preferredBaySlotId : null);
    }

    /**
     * 实际是否应该进入整备流程。
     * 已处于炮塔模式时，除非所有有限弹药武器全部打空，否则不触发整备。
     */
    public static boolean shouldTriggerRefit(ShipAPI ship) {
        if (!needsRefit(ship)) {
            return false;
        }

        if (Moci_MechTurretAI.isDocked(ship) && !areAllAmmoWeaponsEmpty(ship)) {
            return false;
        }

        return true;
    }

    private static void clearAutomaticRefitSearchState(ShipAPI ship) {
        if (ship == null) {
            return;
        }
        ship.removeCustomData(REFIT_SEARCH_CACHE_KEY);
        ship.removeCustomData(REFIT_SEARCH_NEXT_TIME_KEY);
    }

    private static boolean isCachedRefitTargetUsable(Moci_RS_RepairBayScript.Moci_RepairBay bay) {
        if (bay == null) {
            return false;
        }

        ShipAPI host = bay.getShip();
        if (!isSuitableCarrierToLand(host)) {
            return false;
        }

        Moci_RS_RepairBayScript script = Moci_RS_RepairBayHullModEffect.ensureRepairBayScript(host);
        if (script == null) {
            return false;
        }

        Moci_RS_RepairBayScript.Moci_RepairBayStatus status = script.getBay(bay);
        return status != null && !status.isOccupied();
    }

    private static float getNextAutomaticRefitSearchDelay() {
        return REFIT_SEARCH_INTERVAL_MIN
                + (float) Math.random() * (REFIT_SEARCH_INTERVAL_MAX - REFIT_SEARCH_INTERVAL_MIN);
    }

    /**
     * 自动整备目标查询。
     * 返回 null 说明当前不应进入自动整备，或虽然应整备但当前没有可用港位。
     */
    public static Moci_RS_RepairBayScript.Moci_RepairBay findAutomaticRefitTarget(ShipAPI ship) {
        if (ship == null || Global.getCombatEngine() == null) {
            return null;
        }
        if (!shouldTriggerRefit(ship)) {
            clearAutomaticRefitSearchState(ship);
            return null;
        }

        Object cachedObj = ship.getCustomData().get(REFIT_SEARCH_CACHE_KEY);
        Moci_RS_RepairBayScript.Moci_RepairBay cached =
                cachedObj instanceof Moci_RS_RepairBayScript.Moci_RepairBay
                        ? (Moci_RS_RepairBayScript.Moci_RepairBay) cachedObj
                        : null;
        if (cached != null && !isCachedRefitTargetUsable(cached)) {
            cached = null;
            ship.removeCustomData(REFIT_SEARCH_CACHE_KEY);
        }

        float now = Global.getCombatEngine().getTotalElapsedTime(false);
        Object nextObj = ship.getCustomData().get(REFIT_SEARCH_NEXT_TIME_KEY);
        float nextTime = nextObj instanceof Float ? (Float) nextObj : -1f;

        if (cached == null || nextTime < 0f || now >= nextTime) {
            cached = searchForLand(ship);
            logWingmanSearchChanged(ship);
            if (cached != null) {
                ship.setCustomData(REFIT_SEARCH_CACHE_KEY, cached);
            } else {
                ship.removeCustomData(REFIT_SEARCH_CACHE_KEY);
            }
            ship.setCustomData(REFIT_SEARCH_NEXT_TIME_KEY, now + getNextAutomaticRefitSearchDelay());
        }

        return cached;
    }

    /**
     * 检查舰船及其子模块上的所有有限弹药武器是否已经全部清空。
     * 该判定用于炮塔模式与整备的兼容逻辑，比“主武器低弹药”更严格。
     */
    public static boolean areAllAmmoWeaponsEmpty(ShipAPI ship) {
        int ammoWeaponCount = 0;

        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (!isFiniteAmmoWeapon(weapon)) continue;
            ammoWeaponCount++;
            if (weapon.getAmmo() > 0) {
                return false;
            }
        }

        for (ShipAPI module : ship.getChildModulesCopy()) {
            for (WeaponAPI weapon : module.getAllWeapons()) {
                if (!isFiniteAmmoWeapon(weapon)) continue;
                ammoWeaponCount++;
                if (weapon.getAmmo() > 0) {
                    return false;
                }
            }
        }

        return ammoWeaponCount > 0;
    }

    /**
     * 计算最大搜索距离
     * 如果所有主武器都打空 -> 10000f 全图搜索
     * 否则：基础距离800f，每个弹药为0的非主武器增加800f
     *
     * @param ship 需要着陆的舰船
     * @return 最大搜索距离
     */
    private static float getMaxSearchDistance(ShipAPI ship) {
        // 检查是否所有主武器都打空
        Object allEmptyObj = ship.getCustomData().get("Moci_AllMainWeaponsEmpty");
        boolean allMainWeaponsEmpty = false;
        if (allEmptyObj instanceof Boolean) {
            allMainWeaponsEmpty = (Boolean) allEmptyObj;
        }

        // 如果所有主武器都打空，使用全图搜索距离
        if (allMainWeaponsEmpty) {
            Global.getLogger(Moci_SMALandingSequence.class).debug(
                    ship.getName() + "All main weapons are empty, use the whole map search distance:" + FULL_MAP_REFIT_SEARCH_DISTANCE
            );
            return FULL_MAP_REFIT_SEARCH_DISTANCE;
        }

        float baseDistance = 800f; // 基础搜索距离
        float distancePerWeapon = 800f; // 每个弹药为0的非主武器增加的距离

        // 获取弹药为0的非主武器数量
        int emptyNonMainWeaponCount = 0;
        Object customData = ship.getCustomData().get("Moci_EmptyNonLargestWeapons");
        if (customData instanceof Integer) {
            emptyNonMainWeaponCount = (Integer) customData;
        }

        // 如果没有弹药为0的非主武器，使用基础距离
        if (emptyNonMainWeaponCount == 0) {
            Global.getLogger(Moci_SMALandingSequence.class).debug(
                    ship.getName() + "Non-primary weapons without empty ammo, use basic search distance:" + baseDistance
            );
            return baseDistance;
        }

        // 计算实际搜索距离
        float maxDistance = baseDistance + (emptyNonMainWeaponCount * distancePerWeapon);

        Global.getLogger(Moci_SMALandingSequence.class).debug(
                ship.getName() + "Distance Calculation: Basics" + baseDistance + " + " +
                        emptyNonMainWeaponCount + "empty ammo non-main weapon ×" + distancePerWeapon + " = " + maxDistance
        );

        return maxDistance;
    }

    /**
     * 检查舰船是否因为弹药不足而需要着陆
     *
     * @param ship 要检查的舰船
     * @return 如果是因为弹药不足则返回true，如果是因为血量或CR则返回false
     */
    private static boolean isAmmoRefitReason(ShipAPI ship) {
        Object reason = ship.getCustomData().get("Moci_NeedsRefitReason");
        if (reason instanceof String) {
            return "AMMO".equals(reason);
        }
        return false;
    }

    /**
     * 检查指定航母是否适合着陆
     *
     * @param carrier 候选航母
     * @return 如果航母存活且有可用修理舱则返回true
     */
    protected static boolean isSuitableCarrierToLand(ShipAPI carrier){
        return getCarrierLandingBlockReason(carrier) == null;
    }

    public static void recordLandingMemory(ShipAPI ship, Moci_RS_RepairBayScript.Moci_RepairBay bay) {
        if (ship == null || bay == null || bay.getShip() == null) {
            return;
        }

        ship.setCustomData(LAST_LANDING_CARRIER_ID_KEY, bay.getShip().getId());
        ship.setCustomData(LAST_LANDING_BAY_SLOT_ID_KEY, bay.getSlotId());
        if (bay.getLocation() != null) {
            ship.setCustomData(LAST_LANDING_LOCATION_KEY, new org.lwjgl.util.vector.Vector2f(bay.getLocation()));
        }
        logRouteEvent(ship, "Record landing memory. carrier=" + bay.getShip().getName()
                + ", bay=" + bay.getSlotId());
    }

    public static String getRememberedLandingCarrierId(ShipAPI ship) {
        if (ship == null) {
            return null;
        }
        Object value = ship.getCustomData().get(LAST_LANDING_CARRIER_ID_KEY);
        return value instanceof String ? (String) value : null;
    }

    public static String getRememberedLandingBaySlotId(ShipAPI ship) {
        if (ship == null) {
            return null;
        }
        Object value = ship.getCustomData().get(LAST_LANDING_BAY_SLOT_ID_KEY);
        return value instanceof String ? (String) value : null;
    }

    private static Moci_ReturnToCarrierModule getOrCreateReturnToCarrierModule(ShipAPI ship) {
        Object data = ship.getCustomData().get(RETURN_TO_CARRIER_MODULE_KEY);
        if (data instanceof Moci_ReturnToCarrierModule) {
            return (Moci_ReturnToCarrierModule) data;
        }

        Moci_ReturnToCarrierModule module = new Moci_ReturnToCarrierModule(ship);
        ship.setCustomData(RETURN_TO_CARRIER_MODULE_KEY, module);
        return module;
    }

    private static void clearReturnToCarrierModule(ShipAPI ship) {
        if (ship == null) {
            return;
        }
        Object data = ship.getCustomData().get(RETURN_TO_CARRIER_MODULE_KEY);
        if (data instanceof Moci_ReturnToCarrierModule) {
            ((Moci_ReturnToCarrierModule) data).clearTarget();
        }
        ship.removeCustomData(RETURN_TO_CARRIER_MODULE_KEY);
    }

    private static void logRouteEvent(ShipAPI ship, String message) {
        if (!ENABLE_DEBUG_LOGS) {
            return;
        }
        if (ship == null) {
            return;
        }
        Global.getLogger(Moci_SMALandingSequence.class).info(LOG_PREFIX + ship.getName() + " - " + message);
    }

    private static boolean isCombatSquadWingman(ShipAPI ship) {
        return ship != null && ship.getCustomData().get("Moci_WingmanCommander") instanceof ShipAPI;
    }

    private static String getCarrierLandingBlockReason(ShipAPI carrier) {
        if (carrier == null) {
            return "carrier_null";
        }
        if (!carrier.isAlive() || carrier.isHulk()) {
            return "carrier_dead";
        }
        if (carrier.isPhased()) {
            return "carrier_phased";
        }
        if (!Moci_RS_RepairBayHullModEffect.canActAsRepairCarrier(carrier)) {
            return "not_repair_carrier";
        }

        Moci_RS_RepairBayScript script = Moci_RS_RepairBayHullModEffect.ensureRepairBayScript(carrier);
        if (script == null) {
            return "repair_script_missing";
        }
        if (!script.hasVacancy()) {
            return "no_vacancy";
        }
        return null;
    }

    private static void incrementReasonCount(Map<String, Integer> rejectReasons, String reason) {
        Integer count = rejectReasons.get(reason);
        rejectReasons.put(reason, count == null ? 1 : count + 1);
    }

    private static String formatRejectReasons(Map<String, Integer> rejectReasons) {
        if (rejectReasons == null || rejectReasons.isEmpty()) {
            return "None";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : rejectReasons.entrySet()) {
            if (!first) {
                builder.append("|");
            }
            first = false;
            builder.append(toShortRejectReason(entry.getKey())).append(entry.getValue());
        }
        return builder.toString();
    }

    private static String toShortRejectReason(String reason) {
        if ("distance_limit".equals(reason)) {
            return "Over distance";
        }
        if ("not_repair_carrier".equals(reason)) {
            return "Not under maintenance";
        }
        if ("no_vacancy".equals(reason)) {
            return "No place";
        }
        if ("carrier_phased".equals(reason)) {
            return "Phase";
        }
        if ("carrier_dead".equals(reason)) {
            return "Destroyed";
        }
        if ("repair_script_missing".equals(reason)) {
            return "no script";
        }
        if ("carrier_null".equals(reason)) {
            return "Empty";
        }
        return reason;
    }

    private static float getCombatElapsedTime() {
        CombatEngineAPI engine = Global.getCombatEngine();
        return engine != null ? engine.getTotalElapsedTime(false) : 0f;
    }

    private static void clearMainAmmoEmptyMarker(ShipAPI ship) {
        if (ship == null) {
            return;
        }
        ship.removeCustomData(MAIN_AMMO_EMPTY_AT_KEY);
    }

    private static void resetWingmanRefitLogState(ShipAPI ship) {
        if (ship == null) {
            return;
        }
        ship.removeCustomData(WINGMAN_REFIT_LAST_NEED_STATE_KEY);
        ship.removeCustomData(WINGMAN_REFIT_LAST_SEARCH_TEXT_KEY);
        ship.removeCustomData(WINGMAN_REFIT_PENDING_SEARCH_TEXT_KEY);
        clearMainAmmoEmptyMarker(ship);
    }

    private static void logWingmanNeedChanged(ShipAPI ship, String stateKey, String text) {
        if (ship == null || !isCombatSquadWingman(ship)) {
            return;
        }
        Object last = ship.getCustomData().get(WINGMAN_REFIT_LAST_NEED_STATE_KEY);
        if (stateKey != null && stateKey.equals(last)) {
            return;
        }
        ship.setCustomData(WINGMAN_REFIT_LAST_NEED_STATE_KEY, stateKey);
        ship.removeCustomData(WINGMAN_REFIT_LAST_SEARCH_TEXT_KEY);
        ship.removeCustomData(WINGMAN_REFIT_PENDING_SEARCH_TEXT_KEY);
        Global.getLogger(Moci_SMALandingSequence.class).info(WINGMAN_LOG_PREFIX + ship.getName() + " - " + text);
    }

    private static void logWingmanSearchChanged(ShipAPI ship) {
        if (ship == null || !isCombatSquadWingman(ship)) {
            return;
        }
        Object need = ship.getCustomData().get(WINGMAN_REFIT_LAST_NEED_STATE_KEY);
        if (!(need instanceof String)) {
            return;
        }
        Object summary = ship.getCustomData().get(WINGMAN_REFIT_PENDING_SEARCH_TEXT_KEY);
        if (!(summary instanceof String)) {
            return;
        }
        Object last = ship.getCustomData().get(WINGMAN_REFIT_LAST_SEARCH_TEXT_KEY);
        if (summary.equals(last)) {
            return;
        }
        ship.setCustomData(WINGMAN_REFIT_LAST_SEARCH_TEXT_KEY, summary);
        Global.getLogger(Moci_SMALandingSequence.class).info(WINGMAN_LOG_PREFIX + ship.getName() + " - " + summary);
    }

    private static void updateMainAmmoEmptyState(ShipAPI ship, boolean allMainWeaponsEmpty,
                                                 String largestWeaponSize, int mainWeaponCount, int mainWeaponEmpty) {
        if (ship == null) {
            return;
        }
        Object existing = ship.getCustomData().get(MAIN_AMMO_EMPTY_AT_KEY);
        if (allMainWeaponsEmpty) {
            if (!(existing instanceof Float)) {
                float now = getCombatElapsedTime();
                ship.setCustomData(MAIN_AMMO_EMPTY_AT_KEY, now);
            }
        } else {
            clearMainAmmoEmptyMarker(ship);
        }
    }

    private static String describeBay(Moci_RS_RepairBayScript.Moci_RepairBay bay) {
        if (bay == null) {
            return "none";
        }
        return (bay.getShip() != null ? bay.getShip().getName() : "null")
                + "/" + (bay.getSlotId() != null ? bay.getSlotId() : "no_slot");
    }

    private static String getRefitReasonSummary(ShipAPI ship) {
        if (ship == null) {
            return "ship=null";
        }

        Object reason = ship.getCustomData().get("Moci_NeedsRefitReason");
        if ("HULL_AND_CR".equals(reason)) {
            return "Blood CR";
        }
        if ("HULL_LOW".equals(reason)) {
            return "Hull";
        }
        if ("CR_LOW".equals(reason)) {
            return "CR";
        }
        if ("AMMO".equals(reason)) {
            return "Ammo";
        }
        return "None";
    }

    private static float getCRRefitThreshold(ShipAPI ship) {
        if (ship == null) {
            return 0.4f;
        }
        return Math.max(ship.getCRAtDeployment() * 0.5f, 0.4f);
    }

    public static boolean canManualLandOnTarget(ShipAPI ship, ShipAPI target) {
        return ship != null
                && target != null
                && target.getOwner() == ship.getOwner()
                && isSuitableCarrierToLand(target);
    }

    public static boolean shouldUseTurretModeForSelectedTarget(ShipAPI ship, ShipAPI target) {
        boolean landingAvailable = canManualLandOnTarget(ship, target);
        boolean turretAvailable = Moci_MechTurretAI.canUseAsTurretHost(ship, target);
        return shouldUseTurretModeForSelectedTarget(ship, target, landingAvailable, turretAvailable);
    }

    public static boolean shouldUseTurretModeForSelectedTarget(ShipAPI ship, ShipAPI target,
                                                               boolean landingAvailable, boolean turretAvailable) {
        if (ship == null || target == null || target.getOwner() != ship.getOwner()) {
            return false;
        }
        return turretAvailable && (!landingAvailable || !Moci_MechTurretAI.isRefitModeSelected(ship));
    }

    private static boolean shouldUseRefitModeForSelectedTarget(ShipAPI ship, ShipAPI target,
                                                               boolean landingAvailable, boolean turretAvailable) {
        if (ship == null || target == null || target.getOwner() != ship.getOwner()) {
            return false;
        }
        return landingAvailable && (!turretAvailable || Moci_MechTurretAI.isRefitModeSelected(ship));
    }

    private static void maintainSelectedFriendlyTargetStatus(CombatEngineAPI engine, ShipAPI ship,
                                                             boolean landingAvailable, boolean turretAvailable,
                                                             boolean autopilotEnabled) {
        if (engine == null || ship == null || engine.getPlayerShip() != ship) {
            return;
        }

        String icon = Global.getSettings().getSpriteName("ui", "icon_tactical_bdeck");
        Map<String, String> replacements = Moci_TextLoader.mapOf(
                "switch_key", org.lwjgl.input.Keyboard.getKeyName(Moci_MechTurretAI.modeSwitchKey)
        );
        if (landingAvailable && turretAvailable) {
            boolean turretMode = !Moci_MechTurretAI.isRefitModeSelected(ship);
            engine.maintainStatusForPlayerShip(
                    "Moci_SelectedFriendlyRouteMode",
                    icon,
                    turretMode
                            ? Moci_TextLoader.getText(MECH_TURRET_TEXT_ID, "status.navigation_title_turret")
                            : Moci_TextLoader.getText(MECH_TURRET_TEXT_ID, "status.navigation_title_refit"),
                    turretMode
                            ? Moci_TextLoader.getTextWithReplacements(MECH_TURRET_TEXT_ID, "status.mode_switch_to_refit", replacements)
                            : Moci_TextLoader.getTextWithReplacements(MECH_TURRET_TEXT_ID, "status.mode_switch_to_turret", replacements),
                    turretMode);
        }

        boolean turretMode = !Moci_MechTurretAI.isRefitModeSelected(ship);
        boolean currentModeAvailable = turretMode ? turretAvailable : landingAvailable;
        String detail = turretMode
                ? Moci_TextLoader.getText(MECH_TURRET_TEXT_ID, "status.target_enter_turret")
                : Moci_TextLoader.getText(MECH_TURRET_TEXT_ID, "status.target_enter_refit");

        engine.maintainStatusForPlayerShip(
                "Moci_SelectedFriendlyRoute",
                icon,
                currentModeAvailable
                        ? Moci_TextLoader.getText(MECH_TURRET_TEXT_ID, "status.target_title_available")
                        : Moci_TextLoader.getText(MECH_TURRET_TEXT_ID, "status.target_title_unavailable"),
                detail,
                !currentModeAvailable);
    }

    /**
     * 检查舰船是否需要修理
     * 检查三个维度：血量、战备值(CR)、弹药
     * 新增过滤：1.内置武器跳过 2.非最大尺寸武器影响距离阈值
     *
     * @param ship 要检查的舰船
     * @return 如果需要修理则返回true
     */
    public static boolean needsRefit(ShipAPI ship){
        if (Moci_MechTurretAI.isDocked(ship) && !areAllAmmoWeaponsEmpty(ship)) {
            ship.setCustomData("Moci_EmptyNonLargestWeapons", 0);
            ship.setCustomData("Moci_NeedsRefitReason", null);
            ship.setCustomData("Moci_AllMainWeaponsEmpty", false);
            resetWingmanRefitLogState(ship);
            return false;
        }

        // 血量检查：低于70%或战备值低于部署CR的50%（阈值最低为40%）
        boolean hullLow = ship.getHullLevel() <= 0.7f;
        float crThreshold = getCRRefitThreshold(ship);
        boolean crLow = ship.getCurrentCR() < crThreshold;
        if(hullLow || crLow){
            // 因为血量或CR需要修理，清除弹药相关标记，使用 10000f 全图搜索距离
            ship.setCustomData("Moci_EmptyNonLargestWeapons", 0);
            ship.setCustomData("Moci_AllMainWeaponsEmpty", false);
            clearMainAmmoEmptyMarker(ship);
            if (hullLow && crLow) {
                ship.setCustomData("Moci_NeedsRefitReason", "HULL_AND_CR");
            } else if (hullLow) {
                ship.setCustomData("Moci_NeedsRefitReason", "HULL_LOW");
            } else {
                ship.setCustomData("Moci_NeedsRefitReason", "CR_LOW");
            }
            logWingmanNeedChanged(ship, getRefitReasonSummary(ship),
                    "Needs repair original=" + getRefitReasonSummary(ship)
                            + "blood =" + String.format("%.2f", ship.getHullLevel())
                            + " CR=" + String.format("%.2f", ship.getCurrentCR()));
            return true;
        }

        // 弹药检查：只关注最大尺寸的有限弹药武器（主武器）
        String largestWeaponSize = null;   // 最大武器尺寸

        // 首先确定舰船上“最大尺寸的有限弹药武器”。
        // 不能把无限弹药武器混进来，否则会出现：
        // - 更大尺寸的能量武器存在
        // - 真正需要补给的最大尺寸有限弹药武器反而被跳过
        for(WeaponAPI weapon : ship.getAllWeapons()) {
            if(!isFiniteAmmoWeapon(weapon)) continue;
            if(shouldSkipBuiltInWeaponForRefit(weapon)) continue;

            String weaponSize = weapon.getSize().name();
            if (largestWeaponSize == null || isLargerWeaponSize(weaponSize, largestWeaponSize)) {
                largestWeaponSize = weaponSize;
            }
        }

        // 检查子模块武器的最大尺寸
        for(ShipAPI module : ship.getChildModulesCopy()) {
            for(WeaponAPI weapon : module.getAllWeapons()) {
                if(!isFiniteAmmoWeapon(weapon)) continue;
                if(shouldSkipBuiltInWeaponForRefit(weapon)) continue;

                String weaponSize = weapon.getSize().name();
                if (largestWeaponSize == null || isLargerWeaponSize(weaponSize, largestWeaponSize)) {
                    largestWeaponSize = weaponSize;
                }
            }
        }

        // 如果没有找到任何武器，不需要弹药补给
        if (largestWeaponSize == null) {
            ship.setCustomData("Moci_EmptyNonLargestWeapons", 0);
            ship.setCustomData("Moci_NeedsRefitReason", null);
            ship.setCustomData("Moci_AllMainWeaponsEmpty", false);
            resetWingmanRefitLogState(ship);
            return false;
        }

        Global.getLogger(Moci_SMALandingSequence.class).debug(
                ship.getName() + "Maximum weapon size:" + largestWeaponSize
        );

        // 统计主武器（最大尺寸的有限弹药武器）的弹药情况
        int mainWeaponCount = 0;           // 主武器数量
        int mainWeaponLowAmmo = 0;         // 弹药 ≤ 25% 的主武器数量
        int mainWeaponEmpty = 0;           // 弹药为0的主武器数量
        int emptyNonMainWeaponCount = 0;   // 弹药为0的非主武器数量

        // 检查主舰体武器
        for(WeaponAPI weapon : ship.getAllWeapons()){
            // 跳过装饰性武器
            if(weapon.isDecorative()) continue;
            if(shouldSkipBuiltInWeaponForRefit(weapon)) continue;

            // 只统计有限弹药武器
            if(isFiniteAmmoWeapon(weapon)) {
                if (weapon.getSize().name().equals(largestWeaponSize)) {
                    // 这是主武器
                    mainWeaponCount++;

                    // 检查弹药比例
                    float ammoRatio = (float)weapon.getAmmo() / (float)weapon.getMaxAmmo();
                    if (ammoRatio <= 0.25f) {
                        mainWeaponLowAmmo++;
                    }
                    if (weapon.getAmmo() == 0) {
                        mainWeaponEmpty++;
                    }
                } else {
                    // 这是非主武器（小武器），只统计弹药为0的
                    if (weapon.getAmmo() == 0) {
                        emptyNonMainWeaponCount++;
                    }
                }
            }
        }

        // 检查子模块武器
        for(ShipAPI module : ship.getChildModulesCopy()){
            for(WeaponAPI weapon : module.getAllWeapons()){
                // 跳过装饰性武器
                if(weapon.isDecorative()) continue;
                if(shouldSkipBuiltInWeaponForRefit(weapon)) continue;

                // 只统计有限弹药武器
                if(isFiniteAmmoWeapon(weapon)) {
                    if (weapon.getSize().name().equals(largestWeaponSize)) {
                        // 这是主武器
                        mainWeaponCount++;

                        // 检查弹药比例
                        float ammoRatio = (float)weapon.getAmmo() / (float)weapon.getMaxAmmo();
                        if (ammoRatio <= 0.25f) {
                            mainWeaponLowAmmo++;
                        }
                        if (weapon.getAmmo() == 0) {
                            mainWeaponEmpty++;
                        }
                    } else {
                        // 这是非主武器（小武器），只统计弹药为0的
                        if (weapon.getAmmo() == 0) {
                            emptyNonMainWeaponCount++;
                        }
                    }
                }
            }
        }

        // 判断是否需要弹药补给：所有主武器的弹药都 ≤ 25%
        boolean needsAmmoRefill = mainWeaponCount > 0 && mainWeaponLowAmmo == mainWeaponCount;

        // 判断是否所有主武器都打空了
        boolean allMainWeaponsEmpty = mainWeaponCount > 0 && mainWeaponEmpty == mainWeaponCount;
        updateMainAmmoEmptyState(ship, allMainWeaponsEmpty, largestWeaponSize, mainWeaponCount, mainWeaponEmpty);

        if (needsAmmoRefill) {
            // 存储弹药为0的非主武器数量，用于距离计算
            ship.setCustomData("Moci_EmptyNonLargestWeapons", emptyNonMainWeaponCount);
            ship.setCustomData("Moci_NeedsRefitReason", "AMMO");
            ship.setCustomData("Moci_AllMainWeaponsEmpty", allMainWeaponsEmpty);

            Global.getLogger(Moci_SMALandingSequence.class).debug(
                    ship.getName() + "Ammo supply required - Number of primary weapons:" + mainWeaponCount +
                            ", low ammo primary weapon:" + mainWeaponLowAmmo +
                            ", blank ammunition main weapon:" + mainWeaponEmpty +
                            ", Blank Ammo Number of Non-Main Weapons:" + emptyNonMainWeaponCount +
                            ", all main weapons empty:" + allMainWeaponsEmpty
            );
            Float emptyAt = ship.getCustomData().get(MAIN_AMMO_EMPTY_AT_KEY) instanceof Float
                    ? (Float) ship.getCustomData().get(MAIN_AMMO_EMPTY_AT_KEY) : null;
            String timeText = emptyAt != null ? String.format("%.2f", emptyAt) : "None";
            String ammoState = allMainWeaponsEmpty ? "run out of bullets" : "low bounce";
            logWingmanNeedChanged(ship, ammoState,
                    "Needs repair original = bullet"
                            + (allMainWeaponsEmpty ? "Exhaust" : "Low")
                            + "ruler =" + largestWeaponSize
                            + "main=" + mainWeaponCount
                            + "empty =" + mainWeaponEmpty
                            + "time =" + timeText);
        } else {
            // 不需要修理时清除标记
            ship.setCustomData("Moci_EmptyNonLargestWeapons", 0);
            ship.setCustomData("Moci_NeedsRefitReason", null);
            ship.setCustomData("Moci_AllMainWeaponsEmpty", false);
            resetWingmanRefitLogState(ship);
        }

        return needsAmmoRefill;
    }

    private static boolean isFiniteAmmoWeapon(WeaponAPI weapon) {
        return weapon != null
                && !weapon.isDecorative()
                && weapon.usesAmmo()
                && weapon.getAmmoPerSecond() <= 0
                && weapon.getMaxAmmo() > 0;
    }

    /**
     * 是否在整备判定里忽略某个内置武器。
     *
     * 保留旧设计的一个细节：
     * 即使开启“忽略内置武器”开关，LARGE 内置武器仍然参与整备判定，
     * 因为它们通常就是机体的主力武装。
     */
    private static boolean shouldSkipBuiltInWeaponForRefit(WeaponAPI weapon) {
        if (weapon == null || weapon.getSlot() == null || !weapon.getSlot().isBuiltIn()) {
            return false;
        }
        if (!SKIP_BUILT_IN_WEAPONS_FOR_REFIT) {
            return false;
        }
        return !weapon.getSize().name().equals("LARGE");
    }

    /**
     * 比较武器尺寸大小
     * @param size1 武器尺寸1
     * @param size2 武器尺寸2
     * @return 如果size1比size2大则返回true
     */
    private static boolean isLargerWeaponSize(String size1, String size2) {
        // 武器尺寸优先级：LARGE > MEDIUM > SMALL
        Map<String, Integer> sizeOrder = new HashMap<String, Integer>();
        sizeOrder.put("SMALL", 1);
        sizeOrder.put("MEDIUM", 2);
        sizeOrder.put("LARGE", 3);

        Integer order1Value = sizeOrder.get(size1);
        int order1 = (order1Value != null) ? order1Value.intValue() : 0;

        Integer order2Value = sizeOrder.get(size2);
        int order2 = (order2Value != null) ? order2Value.intValue() : 0;

        return order1 > order2;
    }

    /**
     * 检查该船体模组是否适用于指定舰船
     * 只有安装了Moci_MobileSuits船插的舰船才能使用
     */
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship != null && ship.getVariant() != null) {
            return ship.getVariant().getHullMods().contains("Moci_MobileSuits");
        }
        return false;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) return Moci_TextLoader.getText(TEXT_ID, "description.unapplicable_ship_null");
        if (ship.getVariant() == null) return Moci_TextLoader.getText(TEXT_ID, "description.unapplicable_variant_null");
        if (!ship.getVariant().getHullMods().contains("Moci_MobileSuits")) {
            return Moci_TextLoader.getText(TEXT_ID, "description.unapplicable_only_ms");
        }
        return null;
    }

    /**
     * 在改装界面是否显示该船体模组
     */
    @Override
    public boolean showInRefitScreenModPickerFor(ShipAPI ship) {
        return isApplicableToShip(ship);
    }

    /**
     * 添加船插描述信息
     */
    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 2f;
        Color h = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        tooltip.addSectionHeading(Moci_TextLoader.getText(TEXT_ID, "description.ai_heading"), com.fs.starfarer.api.ui.Alignment.MID, pad);

        tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.ai_intro"), 0f, new Color[]{h, h},
                Moci_TextLoader.getHighlights(TEXT_ID, "description.ai_intro_highlights").toArray(new String[0]));
        tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.ai_cond_hull"), 0f, h,
                Moci_TextLoader.getHighlights(TEXT_ID, "description.ai_cond_hull_highlights").toArray(new String[0]));
        tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.ai_cond_cr"), 0f, h,
                Moci_TextLoader.getHighlights(TEXT_ID, "description.ai_cond_cr_highlights").toArray(new String[0]));
        tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.ai_cond_ammo"), 0f, h,
                Moci_TextLoader.getHighlights(TEXT_ID, "description.ai_cond_ammo_highlights").toArray(new String[0]));

        tooltip.addSectionHeading(Moci_TextLoader.getText(TEXT_ID, "description.player_heading"), com.fs.starfarer.api.ui.Alignment.MID, pad);

        tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.player_intro"), pad);
        tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.player_step_target"), 0f, h,
                Moci_TextLoader.getHighlights(TEXT_ID, "description.player_step_target_highlights").toArray(new String[0]));
        tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.player_step_auto"), 0f, h,
                Moci_TextLoader.getHighlights(TEXT_ID, "description.player_step_auto_highlights").toArray(new String[0]));

        tooltip.addSectionHeading(Moci_TextLoader.getText(TEXT_ID, "description.notes_heading"), com.fs.starfarer.api.ui.Alignment.MID, pad);

        tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.notes_carrier"), 0f, new Color[]{h, h},
                Moci_TextLoader.getHighlights(TEXT_ID, "description.notes_carrier_highlights").toArray(new String[0]));
        tooltip.addPara(Moci_TextLoader.getText(TEXT_ID, "description.notes_cancel"), 0f, h,
                Moci_TextLoader.getHighlights(TEXT_ID, "description.notes_cancel_highlights").toArray(new String[0]));
    }

    /**
     * 统一的起降动画判定。
     *
     * 当前规则已经改为：
     * - 所有整备湾都必须播放着陆动画
     * - 所有整备湾都必须播放起飞动画
     *
     * 之所以保留这个方法名，是为了兼容现有调用点，避免大范围重构。
     */
    public static boolean bayShouldHidden(Moci_RS_RepairBayScript.Moci_RepairBay bay){
        return true;
    }
}
