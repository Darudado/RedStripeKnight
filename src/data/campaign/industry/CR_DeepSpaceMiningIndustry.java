package data.campaign.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.fleets.*;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CR_DeepSpaceMiningIndustry extends BaseIndustry implements RouteManager.RouteFleetSpawner, CampaignEventListener, FleetEventListener {

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {

    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {

    }

    public static class ExpeditionFleetResults {
        public PlanetAPI planet;
        public float duration;
        public long timestamp;
        public boolean hasTechmining;
        public float resourcesGained;
        public String targetType; // "ASTEROID" 或 "PLANET"

        public ExpeditionFleetResults(PlanetAPI planet, float duration, long timestamp, boolean hasTechmining, float resourcesGained, String targetType) {
            this.planet = planet;
            this.duration = duration;
            this.timestamp = timestamp;
            this.hasTechmining = hasTechmining;
            this.resourcesGained = resourcesGained;
            this.targetType = targetType;
        }
    }

    public ArrayList<ExpeditionFleetResults> expeditionResults = new ArrayList<>();
    public long deployTimestamp = Global.getSector().getClock().getTimestamp();
    public float deploymentIntervalDays = 25f; // 综合部署间隔
    public static float EXPEDITION_RANGE_LY = 30f; // 综合勘探范围
    public float lastingTime = 90f; // 资源持续时间

    protected IntervalUtil tracker = new IntervalUtil(20f, 30f); // 20-30天间隔
    protected float returningExpeditionValue = 0.0f;

    // 资源条件分类（来自第二个代码）
    public List<String> food_conditions = java.util.Arrays.asList(
            Conditions.FARMLAND_ADEQUATE, Conditions.FARMLAND_BOUNTIFUL,
            Conditions.FARMLAND_POOR, Conditions.FARMLAND_RICH
    );
    public List<String> ore_conditions = java.util.Arrays.asList(
            Conditions.ORE_ABUNDANT, Conditions.ORE_MODERATE, Conditions.ORE_RICH,
            Conditions.ORE_SPARSE, Conditions.ORE_ULTRARICH
    );
    public List<String> rare_ore_conditions = java.util.Arrays.asList(
            Conditions.RARE_ORE_ABUNDANT, Conditions.RARE_ORE_MODERATE, Conditions.RARE_ORE_RICH,
            Conditions.RARE_ORE_SPARSE, Conditions.RARE_ORE_ULTRARICH
    );
    public List<String> organics_conditions = java.util.Arrays.asList(
            Conditions.ORGANICS_ABUNDANT, Conditions.ORGANICS_COMMON,
            Conditions.ORGANICS_PLENTIFUL, Conditions.ORGANICS_TRACE
    );
    public List<String> volatiles_conditions = java.util.Arrays.asList(
            Conditions.VOLATILES_ABUNDANT, Conditions.VOLATILES_DIFFUSE,
            Conditions.VOLATILES_PLENTIFUL, Conditions.VOLATILES_TRACE
    );



    @Override
    public void apply() {
        super.apply(false);

        // 更新供应和需求
        if (isFunctional()) {
            // 基础小行星开采供应（来自第一个代码）
            supply(Commodities.ORE, MathUtils.getRandomNumberInRange(3, 8), "小行星开采");
            supply(Commodities.RARE_ORE, MathUtils.getRandomNumberInRange(2, 6), "小行星开采");
            supply(Commodities.VOLATILES, MathUtils.getRandomNumberInRange(2, 6), "小行星开采");
            supply(Commodities.ORGANICS, MathUtils.getRandomNumberInRange(1, 4), "小行星开采");
            supply(Commodities.FUEL, MathUtils.getRandomNumberInRange(1, 3), "小行星开采");

            // 添加对勘探舰队的支持需求（综合两个代码的需求）
            demand(Commodities.SUPPLIES, 2);
            demand(Commodities.FUEL, 2);
            demand(Commodities.SHIPS, (int) MathUtils.clamp(market.getSize() - 2f, 1f, 8f));

            // 刷新勘探资源供应状态（来自第二个代码）
            refreshResourceState();

        } else {
            // 清除所有供应和需求修正
            unapplyAllSupplies();
        }

        Global.getSector().getListenerManager().removeListener(this);
        // 注册事件监听器
        Global.getSector().getListenerManager().addListener(this);
    }

    @Override
    public void unapply() {
        super.unapply();
        // 清理所有相关数据
        Global.getSector().getListenerManager().removeListener(this);
        unapplyAllSupplies();

        ArrayList<CampaignFleetAPI> fleets = getFleets();
        for (CampaignFleetAPI fleet : fleets) {
            fleet.removeEventListener(this);
            if (fleet.isAlive()) {
                fleet.despawn();
            }
        }
        fleets.clear();
    }

    /**
     * 清除所有资源供应修正
     */
    private void unapplyAllSupplies() {
        // 清除基础小行星供应
        getSupply(Commodities.ORE).getQuantity().unmodifyFlat("CR_asteroid_0");
        getSupply(Commodities.RARE_ORE).getQuantity().unmodifyFlat("CR_asteroid_1");
        getSupply(Commodities.VOLATILES).getQuantity().unmodifyFlat("CR_asteroid_2");
        getSupply(Commodities.ORGANICS).getQuantity().unmodifyFlat("CR_asteroid_3");
        getSupply(Commodities.FUEL).getQuantity().unmodifyFlat("CR_asteroid_4");

        // 清除勘探资源供应（来自第二个代码）
        for (int i = 0; i < 10; i++) {
            getSupply(Commodities.FOOD).getQuantity().unmodifyFlat("cr_deepspace_food_" + i);
            getSupply(Commodities.ORE).getQuantity().unmodifyFlat("cr_deepspace_ore_" + i);
            getSupply(Commodities.RARE_ORE).getQuantity().unmodifyFlat("cr_deepspace_rareore_" + i);
            getSupply(Commodities.ORGANICS).getQuantity().unmodifyFlat("cr_deepspace_organics_" + i);
            getSupply(Commodities.VOLATILES).getQuantity().unmodifyFlat("cr_deepspace_volatiles_" + i);
            getSupply(Commodities.METALS).getQuantity().unmodifyFlat("cr_deepspace_metals_" + i);
            getSupply(Commodities.SHIPS).getQuantity().unmodifyFlat("cr_deepspace_ships_" + i);
            getSupply(Commodities.HEAVY_MACHINERY).getQuantity().unmodifyFlat("cr_deepspace_heavymach_" + i);
        }
    }

    private transient boolean isInitializing = false;

    @Override
    public void advance(float amount) {
        if (isInitializing) {
            // 初始化完成后再执行正常逻辑
            isInitializing = false;
            return;
        }
        super.advance(amount);

        if (!Global.getSector().getEconomy().isSimMode() && this.isFunctional()) {
            float days = Global.getSector().getClock().convertToDays(amount);

            // 处理返回的勘探舰队价值（来自第一个代码）
            if (this.returningExpeditionValue > 0.0f) {
                this.returningExpeditionValue -= days;
                if (this.returningExpeditionValue < 0.0f) {
                    this.returningExpeditionValue = 0.0f;
                }
            }

            // 推进计时器
            this.tracker.advance(days);

            // 检查并生成勘探舰队（结合两个代码的逻辑）
            if (this.tracker.intervalElapsed()) {
                spawnExpeditionFleetIfPossible();
            }

            // 检查返回的舰队并处理结果（来自第二个代码）
            checkReturnedFleets();

            // 清理过期的勘探结果（来自第二个代码）
            cleanupExpiredExpeditions();
        }
    }

    /**
     * 生成勘探舰队（如果条件允许）- 结合两个代码的逻辑
     */
    private void spawnExpeditionFleetIfPossible() {
        if (!isFunctional()) return;

        // 检查是否有足够的资源支持勘探
        if (!hasSufficientResourcesForExpedition()) return;

        // 随机选择勘探类型：小行星带或行星
        boolean targetAsteroids = Math.random() > 0.5f;
        SectorEntityToken target = null;
        String expeditionType = "";

        if (targetAsteroids) {
            // 寻找小行星带目标（来自第一个代码）
            StarSystemAPI system = findSuitableExpeditionTarget();
            if (system != null) {
                target = system.getCenter();
                expeditionType = "ASTEROID";
            }
        } else {
            // 寻找行星目标（来自第二个代码）
            PlanetAPI planet = findSuitablePlanet();
            if (planet != null) {
                target = planet;
                expeditionType = "PLANET";
            }
        }

        if (target == null) return;

        // 创建勘探舰队（使用RouteManager方式）
        String sid = this.getRouteSourceId();
        RouteManager.OptionalFleetData extra = new RouteManager.OptionalFleetData(this.market);

        // 创建自定义数据存储目标信息
        ExpeditionFleetData custom = new ExpeditionFleetData(target, expeditionType);

        RouteManager.RouteData route = RouteManager.getInstance().addRoute(sid, this.market, Misc.genRandomSeed(), extra, this, custom);

        // 设置舰队强度
        extra.strength = getExpeditionFleetStrength();

        // 设置航线段：前往目标并返回
        float expeditionDays = 45f + (float) Math.random() * 30f; // 45-75天的任务
        route.addSegment(new RouteManager.RouteSegment(expeditionDays * 0.4f, target));
        route.addSegment(new RouteManager.RouteSegment(expeditionDays * 0.2f, target)); // 勘探停留
        route.addSegment(new RouteManager.RouteSegment(expeditionDays * 0.4f, this.market.getPrimaryEntity())); // 返回
    }

    /**
     * 查找合适的小行星带勘探目标（来自第一个代码）
     */
    private StarSystemAPI findSuitableExpeditionTarget() {
        List<StarSystemAPI> allSystems = Global.getSector().getStarSystems();
        WeightedRandomPicker<StarSystemAPI> picker = new WeightedRandomPicker<>();

        Vector2f marketLocation = this.market.getPrimaryEntity().getLocation();

        for (StarSystemAPI system : allSystems) {
            // 检查距离
            float distance = MathUtils.getDistance(marketLocation, system.getLocation());
            if (distance > EXPEDITION_RANGE_LY) continue;

            // 跳过当前系统
            if (system == this.market.getStarSystem()) continue;

            // 检查系统中是否有小行星带
            if (!hasAsteroidBelts(system)) continue;

            // 根据距离和资源丰富程度设置权重
            float weight = (EXPEDITION_RANGE_LY - distance) * getSystemResourceValue(system);
            picker.add(system, weight);
        }

        return picker.pick();
    }

    /**
     * 寻找合适的行星目标（来自第二个代码）
     */
    private PlanetAPI findSuitablePlanet() {
        float range = EXPEDITION_RANGE_LY;
        Vector2f location = market.getLocationInHyperspace();

        List<StarSystemAPI> starSystems = Global.getSector().getStarSystems();
        List<StarSystemAPI> filteredSystems = new ArrayList<>();

        for (StarSystemAPI system : starSystems) {
            float distance = MathUtils.getDistance(location, system.getLocation());
            if (distance < range && !system.hasTag(Tags.THEME_HIDDEN) &&
                    !system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) {

                if (!system.hasBlackHole() && !system.hasPulsar() &&
                        !system.hasTag(Tags.THEME_REMNANT_MAIN)) {
                    filteredSystems.add(system);
                }
            }
        }

        // 如果没有找到理想系统，放宽条件
        if (filteredSystems.isEmpty()) {
            for (StarSystemAPI system : starSystems) {
                float distance = MathUtils.getDistance(location, system.getLocation());
                if (distance < range && !system.hasBlackHole() && !system.hasPulsar()) {
                    filteredSystems.add(system);
                }
            }
        }

        // 最后尝试所有在范围内的系统
        if (filteredSystems.isEmpty()) {
            for (StarSystemAPI system : starSystems) {
                float distance = MathUtils.getDistance(location, system.getLocation());
                if (distance < range) {
                    filteredSystems.add(system);
                }
            }
        }

        if (filteredSystems.isEmpty()) return null;

        // 收集所有合适的行星
        List<PlanetAPI> planets = new ArrayList<>();
        for (StarSystemAPI system : filteredSystems) {
            for (PlanetAPI planet : system.getPlanets()) {
                if (!planet.isStar() && planet.getFaction().getId().equals(Factions.NEUTRAL) &&
                        planet.getMarket() != null && !planet.isGasGiant()) {
                    planets.add(planet);
                }
            }
        }

        if (planets.isEmpty()) return null;

        // 使用加权随机选择最佳目标
        WeightedRandomPicker<PlanetAPI> picker = new WeightedRandomPicker<>();
        for (PlanetAPI planet : planets) {
            float weight = 1.0f;
            MarketAPI planetMarket = planet.getMarket();

            // 计算资源丰富度
            int resourceCount = 0;
            for (MarketConditionAPI condition : planet.getMarket().getConditions()) {
                String conditionId = condition.getId();
                if (food_conditions.contains(conditionId) || ore_conditions.contains(conditionId) ||
                        rare_ore_conditions.contains(conditionId) || organics_conditions.contains(conditionId) ||
                        volatiles_conditions.contains(conditionId)) {
                    resourceCount++;
                }
            }

            if (resourceCount == 2) weight += 3f;
            if (resourceCount >= 3) weight += 5f;

            // 特殊地点加成
            if (Misc.hasFarmland(planetMarket)) weight += 5f;
            if (Misc.hasRuins(planetMarket)) weight += 7f;

            picker.add(planet, weight);
        }

        return picker.pick();
    }

    /**
     * 检查系统中是否有小行星带（来自第一个代码）
     */
    private boolean hasAsteroidBelts(StarSystemAPI system) {
        for (PlanetAPI planet : system.getPlanets()) {
            if (planet.getMarket() != null && planet.getMarket().hasCondition("asteroid_belt")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 评估系统的资源价值（来自第一个代码）
     */
    private float getSystemResourceValue(StarSystemAPI system) {
        float value = 1f; // 基础价值

        for (PlanetAPI planet : system.getPlanets()) {
            if (planet.getMarket() != null) {
                // 检查是否有特殊资源
                if (planet.getMarket().hasCondition("ore_rich") ||
                        planet.getMarket().hasCondition("rare_ore_rich") ||
                        planet.getMarket().hasCondition("volatiles_rich")) {
                    value += 2f;
                }

                // 检查是否有小行星带
                if (planet.getMarket().hasCondition("asteroid_belt")) {
                    value += 1.5f;
                }
            }
        }

        return value;
    }

    /**
     * 检查是否有足够的资源支持勘探（来自第一个代码）
     */
    private boolean hasSufficientResourcesForExpedition() {
        // 检查当前活跃的勘探舰队数量
        int activeExpeditions = getActiveExpeditionCount();
        int maxExpeditions = getMaxExpeditions();

        // 检查资源赤字（来自第二个代码）
        boolean shipDeficit = getMaxDeficit(Commodities.SHIPS).two != 0;
        boolean fuelDeficit = getMaxDeficit(Commodities.FUEL).two != 0;
        boolean suppliesDeficit = getMaxDeficit(Commodities.SUPPLIES).two != 0;

        return activeExpeditions < maxExpeditions && !shipDeficit && !fuelDeficit && !suppliesDeficit;
    }

    /**
     * 获取活跃勘探舰队数量（来自第一个代码）
     */
    private int getActiveExpeditionCount() {
        int count = 0;
        for (RouteManager.RouteData data : RouteManager.getInstance().getRoutesForSource(this.getRouteSourceId())) {
            if (data.getCustom() instanceof ExpeditionFleetData) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取最大勘探舰队数量（结合两个代码）
     */
    private int getMaxExpeditions() {
        // 基于市场规模的限制
        int maxFleets = Math.max(1, this.market.getSize() - 2);
        maxFleets = MathUtils.clamp(maxFleets, 2, 6);
        if (isImproved()) maxFleets += 1;
        return maxFleets;
    }

    /**
     * 获取勘探舰队强度（来自第一个代码）
     */
    private float getExpeditionFleetStrength() {
        // 基于市场规模的舰队强度
        return 30f + (this.market.getSize() * 10f);
    }

    // RouteManager.RouteFleetSpawner 接口实现（来自第一个代码，修改后）
    @Override
    public CampaignFleetAPI spawnFleet(RouteManager.RouteData route) {
        Object customObj = route.getCustom();

        // 添加空值检查
        if (!(customObj instanceof ExpeditionFleetData custom)) {
            // 如果自定义数据为空或类型不正确，记录错误并返回null
            System.out.println("CR_DeepSpaceMiningIndustry: Invalid custom data in route");
            return null;
        }

        Random random = route.getRandom();

        // 根据目标类型选择舰队类型
        String fleetType = custom.targetType.equals("ASTEROID") ? FleetTypes.INVESTIGATORS : FleetTypes.PATROL_MEDIUM;

        // 创建勘探舰队参数
        float combat = 20f + random.nextFloat() * 20f; // 战斗力量
        float freighter = 15f + random.nextFloat() * 20f; // 货船容量
        float tanker = 8f + random.nextFloat() * 12f; // 油船容量

        FleetParamsV3 params = new FleetParamsV3(
                market,
                null, // 位置（在市场中生成）
                market.getFactionId(),
                route.getQualityOverride(),
                fleetType,
                combat, // 战斗力量
                freighter, // 货船
                tanker, // 油船
                0f, // 客运
                0f, // 陆战队
                0f, // 民用
                0f  // 质量
        );

        params.timestamp = route.getTimestamp();
        params.random = random;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        if (fleet == null || fleet.isEmpty()) {
            return null;
        }

        custom.spawnFP = fleet.getFleetPoints();

        // 设置舰队属性
        fleet.setFaction(market.getFactionId(), true);
        fleet.setNoFactionInName(false);

        // 根据目标类型设置舰队名称
        String fleetName = custom.targetType.equals("ASTEROID") ?
                "小行星开采舰队 - " + market.getName() :
                "行星勘探舰队 - " + market.getName();
        fleet.setName(fleetName);

        // 添加记忆标记
        fleet.getMemoryWithoutUpdate().set("$exploration_fleet", true);
        fleet.getMemoryWithoutUpdate().set("$CR_asteroid_expedition", true);
        fleet.getMemoryWithoutUpdate().set("$CR_expedition_target", custom.target.getName());
        fleet.getMemoryWithoutUpdate().set("$CR_expedition_source", this.market.getId());
        fleet.getMemoryWithoutUpdate().set("$CR_expedition_type", custom.targetType);
        fleet.getMemoryWithoutUpdate().set("$cr_deepspace_target_planet", custom.target); // 兼容第二个代码

        // 设置指挥官
        fleet.getCommander().setRankId(Ranks.SPACE_COMMANDER);

        // 添加事件监听器
        fleet.addEventListener(this);

        // 将舰队添加到市场内存中
        getFleets().add(fleet);

        // 添加到星系中
        market.getContainingLocation().addEntity(fleet);
        fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

        // 设置舰队AI - 确保customData不为null
        if (custom.target != null) {
            fleet.addScript(new ExpeditionAssignmentAIV4(fleet, route, custom));
        } else {
            // 如果目标为空，让舰队立即返回
            fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, market.getPrimaryEntity(), 1f, "紧急返回");
            fleet.getMemoryWithoutUpdate().set("$CR_emergency_return", true);
        }

        return fleet;
    }

    @Override
    public void reportAboutToBeDespawnedByRouteManager(RouteManager.RouteData route) {
        // 清理工作
    }

    @Override
    public boolean shouldRepeat(RouteManager.RouteData route) {
        return false; // 不重复相同的路线
    }

    @Override
    public boolean shouldCancelRouteAfterDelayCheck(RouteManager.RouteData route) {
        return false;
    }

    public String getRouteSourceId() {
        return this.market.getId() + "_" + "deepspace_mining";
    }

    // CampaignEventListener 接口实现
    @Override
    public void reportFleetDespawned(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        // 只处理非 FleetEventListener 相关的事件
        if (!fleet.getMemoryWithoutUpdate().getBoolean("$CR_asteroid_expedition")) {
            return;
        }

        if (reason == CampaignEventListener.FleetDespawnReason.REACHED_DESTINATION) {
            // 舰队成功返回
            handleExpeditionReturn(fleet);
        }

        // 从内存中移除舰队
        getFleets().remove(fleet);
    }

    @Override
    public void reportFleetSpawned(CampaignFleetAPI fleet) {
    }

    @Override
    public void reportFleetReachedEntity(CampaignFleetAPI fleet, SectorEntityToken entity) {
        // 当舰队到达目标实体时的处理
        if (fleet.getMemoryWithoutUpdate().getBoolean("$CR_asteroid_expedition")) {
            // 可以在这里处理舰队到达勘探目标的情况
            MemoryAPI memory = fleet.getMemoryWithoutUpdate();
            String targetType = memory.getString("$CR_expedition_type");

            if (targetType != null && targetType.equals("ASTEROID")) {
                // 小行星带勘探舰队到达目标
                // 可以在这里添加到达后的行为
            }
        }
    }

    /**
     * 检查返回的舰队并处理结果（来自第二个代码）
     */
    private void checkReturnedFleets() {
        ArrayList<CampaignFleetAPI> fleets = getFleets();
        ArrayList<CampaignFleetAPI> fleetsToRemove = new ArrayList<>();

        for (CampaignFleetAPI fleet : fleets) {
            // 检查舰队是否正在基地轨道卸载资源
            if (isFleetReturnedAndUnloading(fleet)) {
                // 获取舰队勘探的目标
                SectorEntityToken target = (SectorEntityToken) fleet.getMemoryWithoutUpdate().get("$cr_deepspace_target_planet");
                if (target instanceof PlanetAPI) {
                    handleExpeditionReturn(fleet, (PlanetAPI) target);
                    fleetsToRemove.add(fleet);
                }
            }

            // 检查舰队是否已经消失或死亡但没有正常处理
            if ((fleet.isDespawning() || !fleet.isAlive()) && !fleetsToRemove.contains(fleet)) {
                fleetsToRemove.add(fleet);
                deployTimestamp = Global.getSector().getClock().getTimestamp();
                deploymentIntervalDays = MathUtils.getRandomNumberInRange(5f, 10f);
            }
        }

        // 移除已处理的舰队
        fleets.removeAll(fleetsToRemove);
    }

    /**
     * 检查舰队是否已经返回并正在卸载资源（来自第二个代码）
     */
    private boolean isFleetReturnedAndUnloading(CampaignFleetAPI fleet) {
        if (fleet == null || !fleet.isAlive()) return false;

        // 检查舰队是否在基地附近
        SectorEntityToken marketEntity = market.getPrimaryEntity();
        if (marketEntity.getContainingLocation() != fleet.getContainingLocation()) {
            return false;
        }

        float distance = MathUtils.getDistance(fleet.getLocation(), marketEntity.getLocation());
        if (distance > 500f) { // 如果距离基地太远，说明还没返回
            return false;
        }

        // 检查舰队当前任务是否是卸载资源
        FleetAssignment currentAssignment = fleet.getCurrentAssignment().getAssignment();
        String currentAction = fleet.getCurrentAssignment().getActionText();

        return (currentAssignment == FleetAssignment.ORBIT_PASSIVE &&
                currentAction != null && currentAction.contains("卸载资源")) ||
                // 或者检查舰队内存中的状态
                fleet.getMemoryWithoutUpdate().getBoolean("$cr_deepspace_returned");
    }

    /**
     * 处理勘探舰队返回（综合两个代码的逻辑）
     */
    private void handleExpeditionReturn(CampaignFleetAPI fleet) {
        if (fleet.getMemoryWithoutUpdate().getBoolean("$CR_expedition_processed")) {
            return;
        }
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        String targetName = memory.getString("$CR_expedition_target");
        String expeditionType = memory.getString("$CR_expedition_type");

        // 查找目标
        PlanetAPI targetPlanet = null;
        if (expeditionType.equals("PLANET")) {
            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                for (PlanetAPI planet : system.getPlanets()) {
                    if (planet.getName().equals(targetName)) {
                        targetPlanet = planet;
                        break;
                    }
                }
                if (targetPlanet != null) break;
            }
        } else {
            // 对于小行星带，使用系统中心作为参考
            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                if (system.getName().equals(targetName)) {
                    targetPlanet = system.getPlanets().isEmpty() ? null : system.getPlanets().get(0);
                    break;
                }
            }
        }

        if (targetPlanet != null) {
            // 计算获得的资源
            float resourcesGained = calculateExpeditionResources(targetPlanet, expeditionType, fleet);

            // 创建结果记录
            boolean hasTechmining = checkForTechMining(targetPlanet.getStarSystem());

            ExpeditionFleetResults result = new ExpeditionFleetResults(
                    targetPlanet,
                    lastingTime,
                    Global.getSector().getClock().getTimestamp(),
                    hasTechmining,
                    resourcesGained,
                    expeditionType
            );

            expeditionResults.add(result);

            // 刷新资源状态
            refreshResourceState();

            // 更新返回的勘探价值（用于冷却时间）
            this.returningExpeditionValue += resourcesGained * 0.1f;
        }
    }

    /**
     * 处理勘探舰队返回（重载方法，来自第二个代码）
     */
    private void handleExpeditionReturn(CampaignFleetAPI fleet, PlanetAPI planet) {
        String expeditionType = fleet.getMemoryWithoutUpdate().getString("$CR_expedition_type");
        if (expeditionType == null) {
            expeditionType = "PLANET"; // 默认类型
        }

        boolean hasTechmining = Misc.hasRuins(planet.getMarket());

        ExpeditionFleetResults result = new ExpeditionFleetResults(
                planet,
                lastingTime,
                Global.getSector().getClock().getTimestamp(),
                hasTechmining,
                calculateExpeditionResources(planet, expeditionType, fleet),
                expeditionType
        );

        expeditionResults.add(result);
        refreshResourceState();
    }

    /**
     * 计算勘探获得的资源（综合两个代码的逻辑）
     */
    private float calculateExpeditionResources(PlanetAPI planet, String expeditionType, CampaignFleetAPI fleet) {
        float resources = 0f;

        if (expeditionType.equals("ASTEROID")) {
            // 小行星带资源计算（基于第一个代码）
            resources = 50f;

            // 基于系统资源价值调整
            resources *= getSystemResourceValue(planet.getStarSystem());

            // 基于舰队完好程度调整
            float fleetIntegrity = 1.0f;
            if (fleet.getFleetPoints() > 0) {
                fleetIntegrity = Math.min(1.0f, fleet.getFleetPoints() / 50f);
            }
            resources *= fleetIntegrity;

        } else {
            // 行星资源计算（基于第二个代码）
            MarketAPI planetMarket = planet.getMarket();

            if (planetMarket == null) return resources;

            // 根据行星条件计算资源价值
            for (MarketConditionAPI condition : planetMarket.getConditions()) {
                String conditionId = condition.getId();
                if (food_conditions.contains(conditionId) || ore_conditions.contains(conditionId) ||
                        rare_ore_conditions.contains(conditionId) || organics_conditions.contains(conditionId) ||
                        volatiles_conditions.contains(conditionId)) {
                    resources += 2f;
                }
            }

            // 特殊地点加成
            if (Misc.hasFarmland(planetMarket)) resources += 3f;
            if (Misc.hasRuins(planetMarket)) resources += 5f;
        }

        // 随机波动
        resources *= 0.8f + (float) Math.random() * 0.4f;

        return MathUtils.clamp(resources, 5f, 35f);
    }

    /**
     * 检查系统中是否有科技挖掘潜力（来自第一个代码）
     */
    private boolean checkForTechMining(StarSystemAPI system) {
        for (PlanetAPI planet : system.getPlanets()) {
            if (planet.getMarket() != null &&
                    (planet.getMarket().hasCondition("ruins_extensive") ||
                            planet.getMarket().hasCondition("ruins_vast"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 刷新资源供应状态（来自第二个代码，修改后）
     */
    private void refreshResourceState() {
        // 清理过期的勘探结果
        cleanupExpiredExpeditions();

        int maxSupply = 2 + market.getSize();
        maxSupply = MathUtils.clamp(maxSupply, 0, 10);

        // 初始化资源计数
        int food = 0, ore = 0, rare_ore = 0, organics = 0, volatiles = 0;
        int metals = 0, ships = 0, heavy_machinery = 0;

        // 统计所有有效勘探结果的资源
        for (ExpeditionFleetResults result : expeditionResults) {
            PlanetAPI planet = result.planet;
            if (planet.getMarket() == null) continue;

            // 根据勘探类型调整资源权重
            float typeMultiplier = result.targetType.equals("ASTEROID") ? 1.2f : 1.0f;

            for (MarketConditionAPI condition : planet.getMarket().getConditions()) {
                String conditionId = condition.getId();
                switch (conditionId) {
                    case Conditions.FARMLAND_POOR: food += (int)(2 * typeMultiplier); break;
                    case Conditions.FARMLAND_ADEQUATE: food += (int)(3 * typeMultiplier); break;
                    case Conditions.FARMLAND_RICH: food += (int)(4 * typeMultiplier); break;
                    case Conditions.FARMLAND_BOUNTIFUL: food += (int)(5 * typeMultiplier); break;

                    case Conditions.ORE_SPARSE: ore += (int)(1 * typeMultiplier); break;
                    case Conditions.ORE_MODERATE: ore += (int)(1 * typeMultiplier); break;
                    case Conditions.ORE_ABUNDANT: ore += (int)(2 * typeMultiplier); break;
                    case Conditions.ORE_RICH: ore += (int)(2 * typeMultiplier); break;
                    case Conditions.ORE_ULTRARICH: ore += (int)(3 * typeMultiplier); break;

                    case Conditions.RARE_ORE_SPARSE: rare_ore += (int)(1 * typeMultiplier); break;
                    case Conditions.RARE_ORE_MODERATE: rare_ore += (int)(1 * typeMultiplier); break;
                    case Conditions.RARE_ORE_ABUNDANT: rare_ore += (int)(2 * typeMultiplier); break;
                    case Conditions.RARE_ORE_RICH: rare_ore += (int)(2 * typeMultiplier); break;
                    case Conditions.RARE_ORE_ULTRARICH: rare_ore += (int)(3 * typeMultiplier); break;

                    case Conditions.ORGANICS_TRACE: organics += (int)(1 * typeMultiplier); break;
                    case Conditions.ORGANICS_COMMON: organics += (int)(1 * typeMultiplier); break;
                    case Conditions.ORGANICS_ABUNDANT: organics += (int)(2 * typeMultiplier); break;
                    case Conditions.ORGANICS_PLENTIFUL: organics += (int)(3 * typeMultiplier); break;

                    case Conditions.VOLATILES_TRACE: volatiles += (int)(1 * typeMultiplier); break;
                    case Conditions.VOLATILES_DIFFUSE: volatiles += (int)(1 * typeMultiplier); break;
                    case Conditions.VOLATILES_ABUNDANT: volatiles += (int)(2 * typeMultiplier); break;
                    case Conditions.VOLATILES_PLENTIFUL: volatiles += (int)(3 * typeMultiplier); break;

                    case Conditions.RUINS_SCATTERED: metals += (int)(1 * typeMultiplier); ships += (int)(1 * typeMultiplier); break;
                    case Conditions.RUINS_WIDESPREAD: metals += (int)(1 * typeMultiplier); ships += (int)(2 * typeMultiplier); heavy_machinery += (int)(1 * typeMultiplier); break;
                    case Conditions.RUINS_EXTENSIVE: metals += (int)(2 * typeMultiplier); ships += (int)(3 * typeMultiplier); heavy_machinery += (int)(1 * typeMultiplier); break;
                    case Conditions.RUINS_VAST: metals += (int)(3 * typeMultiplier); ships += (int)(4 * typeMultiplier); heavy_machinery += (int)(2 * typeMultiplier); break;
                }
            }
        }

        // 应用资源供应
        supply("cr_deepspace_food_0", Commodities.FOOD, MathUtils.clamp(food, 0, maxSupply), getCurrentName());
        supply("cr_deepspace_ore_0", Commodities.ORE, MathUtils.clamp(ore, 0, maxSupply), getCurrentName());
        supply("cr_deepspace_rareore_0", Commodities.RARE_ORE, MathUtils.clamp(rare_ore, 0, maxSupply), getCurrentName());
        supply("cr_deepspace_organics_0", Commodities.ORGANICS, MathUtils.clamp(organics, 0, maxSupply), getCurrentName());
        supply("cr_deepspace_volatiles_0", Commodities.VOLATILES, MathUtils.clamp(volatiles, 0, maxSupply), getCurrentName());
        supply("cr_deepspace_metals_0", Commodities.METALS, MathUtils.clamp(metals, 0, maxSupply), getCurrentName());
        supply("cr_deepspace_ships_0", Commodities.SHIPS, MathUtils.clamp(ships, 0, maxSupply), getCurrentName());
        supply("cr_deepspace_heavymach_0", Commodities.HEAVY_MACHINERY, MathUtils.clamp(heavy_machinery, 0, maxSupply), getCurrentName());
    }

    /**
     * 清理过期的勘探结果（来自第二个代码）
     */
    private void cleanupExpiredExpeditions() {
        List<ExpeditionFleetResults> toRemove = new ArrayList<>();
        for (ExpeditionFleetResults result : expeditionResults) {
            if (Global.getSector().getClock().getElapsedDaysSince(result.timestamp) > result.duration && !result.hasTechmining) {
                toRemove.add(result);
            }
        }
        expeditionResults.removeAll(toRemove);
    }

    /**
     * 勘探舰队数据类（修改后）
     */
    public static class ExpeditionFleetData {
        public SectorEntityToken target;
        public String targetType;
        public float spawnFP;  // 存储生成的舰队点数

        public ExpeditionFleetData(SectorEntityToken target, String targetType) {
            this.target = target;
            this.targetType = targetType;
            this.spawnFP = 0f;  // 初始化为0，在spawnFleet中设置
        }
    }

    // 原有的工具提示方法增强（综合两个代码）
    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, Industry.IndustryTooltipMode mode) {
        if (tooltip != null) {
            tooltip.addSpacer(10f);

            tooltip.addPara("深空采矿作业结合了小行星开采和行星勘探功能。派遣的舰队会前往附近星系的小行星带或资源丰富的行星进行资源采集。" +
                            "\n\n" +
                            "舰队规模取决于殖民地规模，同时派遣的舰队数量也受殖民地规模限制。" +
                            "\n\n" +
                            "从舰队卸载的资源可为殖民地供应约" + (int)lastingTime + "天。舰队将前往市场所在星系" + (int)EXPEDITION_RANGE_LY + "光年半径内的系统进行勘探。",
                    0f, Misc.getTextColor(), Misc.getHighlightColor(),
                    "" + (int)lastingTime, "" + (int)EXPEDITION_RANGE_LY + "光年");

            // 显示活跃的勘探舰队
            int activeExpeditions = getActiveExpeditionCount();
            int maxExpeditions = getMaxExpeditions();
            tooltip.addPara("活跃勘探舰队: " + activeExpeditions + " / " + maxExpeditions,
                    Misc.getHighlightColor(), 10f);

            // 显示最近的勘探结果
            if (!expeditionResults.isEmpty()) {
                tooltip.addSpacer(5f);
                tooltip.addPara("最近的勘探结果:", Misc.getTextColor(), 0f);

                int count = 0;
                for (int i = expeditionResults.size() - 1; i >= 0 && count < 3; i--, count++) {
                    ExpeditionFleetResults result = expeditionResults.get(i);
                    String typeText = result.targetType.equals("ASTEROID") ? "小行星带" : "行星";
                    tooltip.addPara("- " + (result.planet != null ? result.planet.getName() : "未知目标") +
                                    " (" + typeText + "): " + (int)result.resourcesGained + " 资源单位",
                            Misc.getGrayColor(), 0f);
                }
            }
        }
    }

    // 原有的舰队管理方法
    public ArrayList<CampaignFleetAPI> getFleets() {
        ArrayList<CampaignFleetAPI> fleets = (ArrayList<CampaignFleetAPI>) market.getMemoryWithoutUpdate().get("$CR_asteroid_fleets");
        if (fleets == null) {
            fleets = new ArrayList<>();
            market.getMemoryWithoutUpdate().set("$CR_asteroid_fleets", fleets);
        }
        return fleets;
    }

    // 其他原有方法保持不变...
    @Override
    public boolean canInstallAICores() {
        return false;
    }

    public boolean isDemandLegal(CommodityOnMarketAPI com) {
        return true;
    }

    public boolean isSupplyLegal(CommodityOnMarketAPI com) {
        return true;
    }

    @Override
    public boolean isAvailableToBuild() {
        return false;
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }

    @Override
    protected boolean canImproveToIncreaseProduction() {
        return super.canImproveToIncreaseProduction();
    }

    @Override
    public boolean canUpgrade() {
        return false;
    }

    @Override
    public boolean canDowngrade() {
        return false;
    }

    @Override
    public String getCurrentName() {
        return "深空采矿作业";
    }

    @Override
    public String getUnavailableReason() {
        return "需要玩家控制的殖民地和合适的勘探目标";
    }

    // 其他事件方法保持空实现...
    @Override
    public void reportPlayerOpenedMarket(MarketAPI market) {}
    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {}
    @Override
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {}
    @Override
    public void reportEncounterLootGenerated(FleetEncounterContextPlugin plugin, CargoAPI loot) {}
    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {}
    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {}
    @Override
    public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {}
    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {}
    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpPointAPI.JumpDestination to) {}
    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {}
    @Override
    public void reportPlayerReputationChange(String faction, float delta) {}
    @Override
    public void reportPlayerReputationChange(PersonAPI person, float delta) {}
    @Override
    public void reportPlayerActivatedAbility(AbilityPlugin ability, Object param) {}
    @Override
    public void reportPlayerDeactivatedAbility(AbilityPlugin ability, Object param) {}
    @Override
    public void reportPlayerDumpedCargo(CargoAPI cargo) {}
    @Override
    public void reportPlayerDidNotTakeCargo(CargoAPI cargo) {}
    @Override
    public void reportEconomyTick(int iterIndex) {
        if (isFunctional()) {
            refreshResourceState();
        }
    }
    @Override
    public void reportEconomyMonthEnd() {}

    /**
     * 自定义勘探舰队 AI - 基于 PatrolAssignmentAIV4 的模式
     * 修改：添加自定义数据参数，避免空指针异常
     */
    public class ExpeditionAssignmentAIV4 extends RouteFleetAssignmentAI implements FleetActionTextProvider {

        public static final String TRAVEL_TO_TARGET_STAGE = "travel_to_target";
        public static final String EXPLORATION_STAGE = "exploration";
        public static final String RETURN_STAGE = "return";
        public static final String UNLOAD_STAGE = "unload";

        private ExpeditionFleetData customData;

        // 修改构造函数，直接传入customData
        public ExpeditionAssignmentAIV4(CampaignFleetAPI fleet, RouteManager.RouteData route, ExpeditionFleetData customData) {
            super(fleet, route);
            this.customData = customData;

            // 安全检查
            if (this.customData == null || this.customData.target == null) {
                // 如果customData无效，让舰队立即返回
                giveEmergencyReturnAssignments();
                return;
            }

            giveInitialAssignments();
        }

        @Override
        protected void giveInitialAssignments() {
            // 再次检查customData
            if (customData == null || customData.target == null) {
                giveEmergencyReturnAssignments();
                return;
            }

            SectorEntityToken target = customData.target;
            SectorEntityToken source = route.getMarket().getPrimaryEntity();

            // 获取路线段
            List<RouteManager.RouteSegment> segments = route.getSegments();
            if (segments.size() < 3) {
                return;
            }

            RouteManager.RouteSegment travelToSegment = segments.get(0); // 前往目标
            RouteManager.RouteSegment exploreSegment = segments.get(1); // 勘探阶段
            RouteManager.RouteSegment returnSegment = segments.get(2);  // 返回阶段

            fleet.clearAssignments();

            // 第一阶段：前往目标
            if (travelToSegment.daysMax > 0) {
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, target,
                        travelToSegment.daysMax, TRAVEL_TO_TARGET_STAGE);
            }

            // 第二阶段：在目标处进行勘探
            if (exploreSegment.daysMax > 0) {
                fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target,
                        exploreSegment.daysMax, EXPLORATION_STAGE,
                        false,
                        () -> {
                            // 开始勘探时的初始化
                            fleet.getMemoryWithoutUpdate().set("$CR_exploration_started", true);
                            fleet.getMemoryWithoutUpdate().set("$CR_mining_time", 0f);
                        },
                        () -> {
                            // 勘探结束时的清理
                            fleet.getMemoryWithoutUpdate().set("$CR_exploration_completed", true);
                        }
                );
            }

            // 第三阶段：返回基地
            if (returnSegment.daysMax > 0) {
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, source,
                        returnSegment.daysMax, RETURN_STAGE);
            }

            // 第四阶段：在基地卸载资源
            fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, source, 2f, UNLOAD_STAGE,
                    false,
                    () -> {
                        // 标记为已返回，开始卸载
                        fleet.getMemoryWithoutUpdate().set("$cr_deepspace_returned", true);
                        fleet.getMemoryWithoutUpdate().set("$CR_unloading_started", true);
                    },
                    () -> {
                        // 卸载完成，处理结果
                        handleExpeditionReturn(fleet);
                        // 准备消失
                        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN,
                                source, 1000f, UNLOAD_STAGE);
                    }
            );

            fleet.getAI().setActionTextProvider(this);
        }

        /**
         * 紧急返回任务分配
         */
        private void giveEmergencyReturnAssignments() {
            fleet.clearAssignments();
            SectorEntityToken source = route.getMarket().getPrimaryEntity();
            fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, source, 1f, "紧急返回（数据丢失）");
            fleet.getMemoryWithoutUpdate().set("$CR_emergency_return", true);

            // 直接触发返回处理
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, source, 1000f, "紧急返回完成");
        }

        @Override
        public String getActionText(CampaignFleetAPI fleet) {
            FleetAssignmentDataAPI curr = fleet.getCurrentAssignment();
            if (curr == null) return null;

            String stage = curr.getActionText();
            SectorEntityToken target = curr.getTarget();

            String name = "";
            if (target != null) {
                name = target.getName();
            }

            if (TRAVEL_TO_TARGET_STAGE.equals(stage)) {
                if (customData != null && customData.targetType.equals("ASTEROID")) {
                    return "前往小行星带 " + name;
                } else {
                    return "前往行星 " + name;
                }
            } else if (EXPLORATION_STAGE.equals(stage)) {
                if (customData != null && customData.targetType.equals("ASTEROID")) {
                    return "在小行星带 " + name + " 开采资源";
                } else {
                    return "在行星 " + name + " 进行勘探";
                }
            } else if (RETURN_STAGE.equals(stage)) {
                return "返回 " + market.getName();
            } else if (UNLOAD_STAGE.equals(stage)) {
                return "在 " + market.getName() + " 卸载资源";
            }

            return null;
        }

        @Override
        public void advance(float amount) {
            super.advance(amount);

            // 在勘探阶段更新采矿进度
            FleetAssignmentDataAPI current = fleet.getCurrentAssignment();
            if (current != null && EXPLORATION_STAGE.equals(current.getActionText())) {
                float currentTime = fleet.getMemoryWithoutUpdate().getFloat("$CR_mining_time");
                fleet.getMemoryWithoutUpdate().set("$CR_mining_time", currentTime +
                        Global.getSector().getClock().convertToDays(amount));
            }

            // 检查舰队状态
            checkFleetStatus();
        }

        private void checkFleetStatus() {
            // 如果舰队严重受损，提前返回
            if (customData != null && fleet.getFleetPoints() < customData.spawnFP * 0.3f) {
                FleetAssignmentDataAPI current = fleet.getCurrentAssignment();
                if (current != null &&
                        (TRAVEL_TO_TARGET_STAGE.equals(current.getActionText()) ||
                                EXPLORATION_STAGE.equals(current.getActionText()))) {

                    // 中断当前任务，立即返回
                    fleet.clearAssignments();
                    SectorEntityToken source = route.getMarket().getPrimaryEntity();
                    fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, source, 1000f, "紧急返回");
                    fleet.getMemoryWithoutUpdate().set("$CR_emergency_return", true);
                }
            }
        }

        private void handleExpeditionReturn(CampaignFleetAPI fleet) {
            // 调用外部类的处理方法
            CR_DeepSpaceMiningIndustry.this.handleExpeditionReturn(fleet);

            // 设置记忆标记，确保不会重复处理
            fleet.getMemoryWithoutUpdate().set("$CR_expedition_processed", true);
        }
    }
}