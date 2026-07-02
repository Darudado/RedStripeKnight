package data.campaign.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolAssignmentAIV4;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.campaign.econ.Guardfleetmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Guardfleet1 extends BaseIndustry implements RouteManager.RouteFleetSpawner, FleetEventListener {
    public static float DEFENSE_BONUS = 2F;
    public static float FLEET_BONUS = 0.25F;

    // 可手动调整的舰队规模参数
    public static float FLEET_SIZE_MULTIPLIER = 2f; // 全局舰队规模系数
    public float customFleetSize = 1.0f; // 单个市场的自定义规模

    protected IntervalUtil tracker = new IntervalUtil(Global.getSettings().getFloat("averagePatrolSpawnInterval") * 0.75F, Global.getSettings().getFloat("averagePatrolSpawnInterval") * 1.3F);
    protected IntervalUtil respawnTracker = new IntervalUtil(3f, 5f); // 重生周期
    protected boolean waitingForRespawn = false; // 标记是否在等待重生
    protected String currentFleetKey = null; // 当前活跃舰队的唯一标识

    public String getRouteSourceId() {
        return this.getMarket().getId() + "_" + "FlagGuardFleet";
    }

    public Guardfleet1() {
    }

    @Override
    public void apply() {
        int size = this.market.getSize()+1;
        int extraDemand = 0;

        // 简化的舰队配置 - 只生成一个舰队
        int light = 8;
        int medium = 6;
        int heavy = 3;


        // 应用舰队规模手动调整
        float sizeMultiplier = customFleetSize * FLEET_SIZE_MULTIPLIER;
        light = Math.round(light * sizeMultiplier);
        medium = Math.round(medium * sizeMultiplier);
        heavy = Math.round(heavy * sizeMultiplier);

        // 确保至少有一个重型舰船
        light = Math.max(light, 10);
        medium = Math.max(medium, 8);
        heavy = Math.max(heavy, 6);

        if(market.getFaction().getId().equals("red_stripe")) {

            this.market.getStats().getDynamic().getMod("patrol_num_light_mod").modifyFlat(this.getModId(), (float) light);
            this.market.getStats().getDynamic().getMod("patrol_num_medium_mod").modifyFlat(this.getModId(), (float) medium);
            this.market.getStats().getDynamic().getMod("patrol_num_heavy_mod").modifyFlat(this.getModId(), (float) heavy);

            this.demand("supplies", size - 3 + extraDemand);
            this.demand("fuel", size - 3 + extraDemand);
            this.demand("ships", size - 3 + extraDemand);

            this.modifyStabilityWithBaseMod();
            float mult = this.getDeficitMult(new String[]{"supplies"});
            String extra = "";
            if (mult != 1.0F) {
                String com = this.getMaxDeficit(new String[]{"supplies"}).one;
                extra = " (" + getDeficitText(com).toLowerCase() + ")";
            }

            float bonus = DEFENSE_BONUS;
            this.market.getStats().getDynamic().getMod("ground_defenses_mod").modifyMult(this.getModId(), 1.0F + bonus * mult, this.getNameForModifier() + extra);
            MemoryAPI memory = this.market.getMemoryWithoutUpdate();
            Misc.setFlagWithReason(memory, "$command", this.getModId(), true, -1.0F);

            this.market.getStats().getDynamic().getMod("combat_fleet_size_mult").modifyMult(this.getModId(), 1.0F + FLEET_BONUS, " (" + this.getNameForModifier() + ")");
        }
    }

    public void unapply() {
        MemoryAPI memory = this.market.getMemoryWithoutUpdate();
        Misc.setFlagWithReason(memory, "$command", this.getModId(), false, -1.0F);
        this.unmodifyStabilityWithBaseMod();
        this.market.getStats().getDynamic().getMod("patrol_num_light_mod").unmodifyFlat(this.getModId());
        this.market.getStats().getDynamic().getMod("patrol_num_medium_mod").unmodifyFlat(this.getModId());
        this.market.getStats().getDynamic().getMod("patrol_num_heavy_mod").unmodifyFlat(this.getModId());
        this.market.getStats().getDynamic().getMod("ground_defenses_mod").unmodifyMult(this.getModId());
        this.market.getStats().getDynamic().getMod("combat_fleet_size_mult").unmodifyMult(this.getModId(0));
    }

    public void advance(float amount) {
        super.advance(amount);
        if (!Global.getSector().getEconomy().isSimMode() && this.isFunctional()) {
            float days = Global.getSector().getClock().convertToDays(amount);

            // 处理重生等待
            if (waitingForRespawn) {
                respawnTracker.advance(days);
                if (respawnTracker.intervalElapsed()) {
                    waitingForRespawn = false;
                    currentFleetKey = null; // 确保清除
                }
            }

            float spawnRate = 1.0F;
            float rateMult = this.market.getStats().getDynamic().getStat("combat_fleet_spawn_rate_mult").getModifiedValue();
            spawnRate *= rateMult;
            this.tracker.advance(days * spawnRate);

            // 只在没有活跃舰队且不在等待重生时生成
            if (this.tracker.intervalElapsed() && !hasActiveFleet() && !waitingForRespawn) {
                spawnSingleFleet();
            }
        }
    }

    protected boolean hasActiveFleet() {
        for (RouteManager.RouteData route : RouteManager.getInstance().getRoutesForSource(getRouteSourceId())) {
            if (route.getActiveFleet() != null) {
                return true;
            }
        }
        return false;
    }

    protected void spawnSingleFleet() {
        // 双重检查，确保不会重复生成
        if (currentFleetKey != null || waitingForRespawn) {
            return;
        }

        currentFleetKey = getRouteSourceId() + "_" +
                System.currentTimeMillis() + "_" +
                new Random().nextInt(10000);

        String sid = this.getRouteSourceId();
        FleetFactory.PatrolType type = determineFleetType();

        MilitaryBase.PatrolFleetData custom = new MilitaryBase.PatrolFleetData(type);
        RouteManager.OptionalFleetData extra = new RouteManager.OptionalFleetData(this.market);
        extra.fleetType = type.getFleetType();

        RouteManager.RouteData route = RouteManager.getInstance().addRoute(sid, this.market, Misc.genRandomSeed(), extra, this, custom);
        extra.strength = (float)getPatrolCombatFP(type, route.getRandom());
        extra.strength = Misc.getAdjustedStrength(extra.strength, this.market);


        // 创建环绕市场的路径
        createOrbitalPatrolRoute(route);
    }

    protected void createOrbitalPatrolRoute(RouteManager.RouteData route) {
        Random random = route.getRandom();
        float totalDays = 30.0F + random.nextFloat() * 30.0F;

        List<SectorEntityToken> points = new ArrayList<>();
        points.add(market.getPrimaryEntity());

        if (market.getContainingLocation() instanceof StarSystemAPI system) {
            List<SectorEntityToken> entities = system.getEntitiesWithTag(Tags.OBJECTIVE);
            if (!entities.isEmpty()) {
                points.add(entities.get(random.nextInt(entities.size())));
            }
        }

        // 只创建一次路线
        if (points.size() == 1) {
            route.addSegment(new RouteManager.RouteSegment(totalDays, points.get(0), null));
        } else {
            for (int i = 0; i < points.size(); i++) {
                SectorEntityToken from = points.get(i);
                SectorEntityToken to = points.get((i + 1) % points.size());
                float segmentDays = totalDays / points.size();
                route.addSegment(new RouteManager.RouteSegment(segmentDays, from, to));
            }
        }
    }

    protected FleetFactory.PatrolType determineFleetType() {
        // 根据你的需求，这里只生成重型舰队
        return FleetFactory.PatrolType.HEAVY;
    }

    public CampaignFleetAPI spawnFleet(RouteManager.RouteData route) {
        MilitaryBase.PatrolFleetData custom = (MilitaryBase.PatrolFleetData) route.getCustom();
        FleetFactory.PatrolType type = custom.type;

        Random random = route.getRandom();

        // 应用舰队规模调整
        float sizeMultiplier = customFleetSize * FLEET_SIZE_MULTIPLIER;

        float combat = Global.getSettings().getBattleSize() * (0.33f + 0.07f * random.nextFloat()) * sizeMultiplier;
        float tanker = Global.getSettings().getBattleSize() * 0.02f * sizeMultiplier;
        float freighter = Global.getSettings().getBattleSize() * 0.02f * sizeMultiplier;

        CampaignFleetAPI fleet = Guardfleetmanager.createGuardFleet1(combat, tanker, freighter ,market);
        System.out.println("Test fleet created: " + (fleet == null || fleet.isEmpty()));

        if (fleet.isEmpty()) return null;

        fleet.setFaction("red_stripe", true);
        fleet.setNoFactionInName(true);

        // 确保市场存在且不为空
        if (this.getMarket() == null || this.getMarket().getId() == null) {
            return null;
        }

        // 确保currentFleetKey不为空
        if (currentFleetKey == null) {
            currentFleetKey = getRouteSourceId() + "_" +
                    System.currentTimeMillis() + "_" +
                    new Random().nextInt(10000);
        }

        fleet.addEventListener(this);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true, 0.3f);
        fleet.getMemoryWithoutUpdate().set("$RS_GuardFleet0_source", this.getMarket().getId()); // 标记来源
        fleet.getMemoryWithoutUpdate().set("$RS_GuardFleet0", currentFleetKey); // 使用唯一的舰队标识

        if (type == FleetFactory.PatrolType.FAST || type == FleetFactory.PatrolType.COMBAT) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_CUSTOMS_INSPECTOR, true);
        }

        String postId = Ranks.POST_PATROL_COMMANDER;
        String rankId = switch (type) {
            case FAST -> Ranks.SPACE_LIEUTENANT;
            case COMBAT -> Ranks.SPACE_COMMANDER;
            case HEAVY -> Ranks.SPACE_CAPTAIN;
        };

        fleet.getCommander().setPostId(postId);
        fleet.getCommander().setRankId(rankId);

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isCapital()) {
                member.setVariant(member.getVariant().clone(), false, false);
                member.getVariant().setSource(VariantSource.REFIT);
                member.getVariant().addTag(Tags.TAG_NO_AUTOFIT);
                member.getVariant().addTag(Tags.VARIANT_CONSISTENT_WEAPON_DROPS);
            }
        }

        market.getContainingLocation().addEntity(fleet);
        fleet.setFacing((float) Math.random() * 360f);
        // 设置在市场附近的位置
        fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

        fleet.addScript(new PatrolAssignmentAIV4(fleet, route));

        if (custom.spawnFP <= 0) {
            custom.spawnFP = fleet.getFleetPoints();
        }

        return fleet;
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();

        // 安全地获取内存值，避免空指针异常
        String sourceMarket = null;
        if (memory.contains("$RS_GuardFleet0_source")) {
            Object sourceMarketObj = memory.get("$RS_GuardFleet0_source");
            if (sourceMarketObj != null) {
                sourceMarket = sourceMarketObj.toString();
            }
        }

        String fleetKey = null;
        if (memory.contains("$RS_GuardFleet0")) {
            Object fleetKeyObj = memory.get("$RS_GuardFleet0");
            if (fleetKeyObj != null) {
                fleetKey = fleetKeyObj.toString();
            }
        }

        // 只有当所有必要的值都存在时才进行处理
        if (this.getMarket() != null &&
                sourceMarket != null &&
                this.getMarket().getId() != null &&
                this.getMarket().getId().equals(sourceMarket) &&
                currentFleetKey != null &&
                fleetKey != null &&
                currentFleetKey.equals(fleetKey)) {

            // 处理所有消失原因，不仅仅是战斗摧毁
            waitingForRespawn = true;
            currentFleetKey = null;
            respawnTracker.forceIntervalElapsed();
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
        // 可以在这里添加战斗相关的逻辑
    }

    protected void buildingFinished() {
        super.buildingFinished();
        this.tracker.forceIntervalElapsed();
    }

    protected void upgradeFinished(Industry previous) {
        super.upgradeFinished(previous);
        this.tracker.forceIntervalElapsed();
    }

    @Override
    public boolean shouldCancelRouteAfterDelayCheck(RouteManager.RouteData route) {
        return false;
    }

    @Override
    public boolean shouldRepeat(RouteManager.RouteData route) {
        return true;  // 启用路线重复
    }

    @Override
    public void reportAboutToBeDespawnedByRouteManager(RouteManager.RouteData route) {
    }

    public static int getPatrolCombatFP(FleetFactory.PatrolType type, Random random) {
        float combat = 500.0F;
        switch (type) {
            case FAST -> combat = (float)Math.round(3.0F + random.nextFloat() * 2.0F) * 5.0F;
            case COMBAT -> combat = (float)Math.round(4.0F + random.nextFloat() * 3.0F) * 5.0F;
            case HEAVY -> combat = (float)Math.round(5.0F + random.nextFloat() * 5.0F) * 5.0F;
        }
        return Math.round(combat);
    }

    public boolean isDemandLegal(CommodityOnMarketAPI com) {
        return true;
    }

    public boolean isSupplyLegal(CommodityOnMarketAPI com) {
        return true;
    }

    public boolean isAvailableToBuild() {
        return false;
    }

    public boolean showWhenUnavailable() {
        return false;
    }
}