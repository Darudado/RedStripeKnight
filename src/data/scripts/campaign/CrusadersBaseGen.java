package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.awt.Color;
import java.util.*;

public class CrusadersBaseGen extends BaseIntelPlugin implements EveryFrameScript {

    public enum ColonyTier {
        TIER_1, TIER_2, TIER_3
    }

    private StarSystemAPI system;
    private MarketAPI market;
    private SectorEntityToken entity;
    private ColonyTier tier;
    private final boolean valid;
    private boolean done = false;

    // 劫掠相关
    private StarSystemAPI raidTarget;
    private final List<CampaignFleetAPI> raidingFleets = new ArrayList<>();
    private final IntervalUtil raidInterval = new IntervalUtil(30f, 60f); // 30~60天发动一次劫掠
    private float daysSinceLastRaid = 0f;
    private boolean raidInProgress = false;

    public CrusadersBaseGen(StarSystemAPI system, MarketAPI market, SectorEntityToken entity, ColonyTier tier) {
        this.system = system;
        this.market = market;
        this.entity = entity;
        this.tier = tier;
        this.valid = true;

        // 注册为脚本
        Global.getSector().addScript(this);
        Global.getSector().getIntelManager().addIntel(this, true);

        // 初次选择劫掠目标
        updateRaidTarget();
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (isEnding() || isEnded()) {
            done = true;
            return;
        }

        // 检查基地是否被摧毁
        if (entity == null || !entity.isAlive() || market == null || !market.isInEconomy()) {
            endImmediately();
            return;
        }

        float days = Global.getSector().getClock().convertToDays(amount);
        daysSinceLastRaid += days;

        // 劫掠逻辑
        if (raidInProgress) {
            // 清理已消失的突袭舰队
            raidingFleets.removeIf(f -> f == null || !f.isAlive() || f.isEmpty());
            if (raidingFleets.isEmpty()) {
                raidInProgress = false;
            }
        } else {
            if (daysSinceLastRaid >= raidInterval.getIntervalDuration()) {
                startRaid();
                daysSinceLastRaid = 0f;
                raidInterval.advance(0); // 重置间隔
            }
        }
    }

    private void updateRaidTarget() {
        // 选择一个有市场且阵营非十字军的星系
        WeightedRandomPicker<StarSystemAPI> picker = new WeightedRandomPicker<>();
        for (StarSystemAPI sys : Global.getSector().getEconomy().getStarSystemsWithMarkets()) {
            if (sys == this.system) continue;
            boolean validTarget = false;
            for (MarketAPI m : Misc.getMarketsInLocation(sys)) {
                if (!m.getFactionId().equals(CrusadersBaseConstruction.CRUSADERS_FACTION)
                        && !m.getFactionId().equals("neutral")) {
                    validTarget = true;
                    break;
                }
            }
            if (validTarget) {
                float dist = Misc.getDistanceLY(this.system.getLocation(), sys.getLocation());
                float weight = Math.max(1f, 10f - dist); // 偏好近距离
                picker.add(sys, weight);
            }
        }
        raidTarget = picker.pick();
    }

    private void startRaid() {
        if (raidTarget == null) {
            updateRaidTarget();
            if (raidTarget == null) return;
        }

        // 检查目标是否依然有效
        boolean hasTargetMarket = false;
        for (MarketAPI m : Misc.getMarketsInLocation(raidTarget)) {
            if (!m.getFactionId().equals(CrusadersBaseConstruction.CRUSADERS_FACTION)
                    && !m.getFactionId().equals("neutral")) {
                hasTargetMarket = true;
                break;
            }
        }
        if (!hasTargetMarket) {
            updateRaidTarget();
            return;
        }

        // 创建突袭舰队
        int fleetCount = 1 + tier.ordinal(); // TIER_1:1支, TIER_2:2支, TIER_3:3支
        for (int i = 0; i < fleetCount; i++) {
            CampaignFleetAPI fleet = createRaidingFleet(raidTarget);
            if (fleet != null) {
                raidingFleets.add(fleet);
            }
        }
        raidInProgress = !raidingFleets.isEmpty();

        // 为受影响的市场添加劫掠条件（通过市场修改器或条件实现）
        // 这里仅示意：可以给目标星系的市场添加一个自定义市场条件，如 "crusaders_raiding"
        // 实际实现需配合市场条件插件，此处略。
    }

    private CampaignFleetAPI createRaidingFleet(StarSystemAPI target) {
        FleetParamsV3 params = new FleetParamsV3(
                market,
                target.getLocation(),
                CrusadersBaseConstruction.CRUSADERS_FACTION,
                1f,
                FleetTypes.PATROL_SMALL,
                1f, 0f, 0f, 0f, 0f, 0f, 1.2f
        );
        params.random = new Random();
        params.maxNumShips = 3 + tier.ordinal() * 2;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null) return null;

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
        fleet.setFaction(CrusadersBaseConstruction.CRUSADERS_FACTION, true);
        fleet.getMemoryWithoutUpdate().set("$crusaders_raid_fleet", true);

        // 在目标星系跳跃点生成
        JumpPointAPI jumpPoint = RouteLocationCalculator.findJumpPointToUse(
                Global.getSector().getFaction(CrusadersBaseConstruction.CRUSADERS_FACTION), target.getCenter());
        if (jumpPoint != null) {
            target.spawnFleet(jumpPoint, 0, 0, fleet);
        } else {
            target.spawnFleet(target.getCenter(), 0, 0, fleet);
        }

        // 添加简单行为：前往随机市场轨道
        List<MarketAPI> markets = Misc.getMarketsInLocation(target);
        if (!markets.isEmpty()) {
            SectorEntityToken dest = markets.get(0).getPrimaryEntity();
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, dest, 60f);
            fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, dest, 20f);
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, jumpPoint != null ? jumpPoint : target.getCenter(), 30f);
        }

        return fleet;
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();

        // 清理突袭舰队
        for (CampaignFleetAPI f : raidingFleets) {
            if (f != null && f.isAlive()) {
                f.despawn();
            }
        }
        raidingFleets.clear();

        if (market != null && market.isInEconomy()) {
            Global.getSector().getEconomy().removeMarket(market);
        }
        if (entity != null) {
            Misc.fadeAndExpire(entity);
        }

        // 通知管理器已摧毁
        CrusadersBaseManager manager = CrusadersBaseManager.getInstance();
        if (manager != null) {
            manager.incrDestroyed();
        }

        done = true;
    }

    @Override
    public boolean isDone() {
        return done || isEnding() || isEnded();
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    // 情报相关
    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "pirate_base");
    }

    @Override
    public String getName() {
        if (market != null) {
            return "Crusaders Colony - " + market.getName();
        }
        return "Crusaders Colony";
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction(CrusadersBaseConstruction.CRUSADERS_FACTION);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        float pad = 3f;
        float opad = 10f;

        FactionAPI faction = getFactionForUIColors();
        info.addImage(faction.getLogo(), width, 128, opad);

        info.addPara("A hidden colony of the Crusaders has been established in the " +
                system.getNameWithLowercaseType() + ".", opad);

        if (raidInProgress) {
            info.addPara("Current raid target: " + (raidTarget != null ? raidTarget.getNameWithLowercaseType() : "unknown"),
                    opad);
        } else {
            info.addPara("No active raids at the moment.", opad);
        }

        info.addSectionHeading("Colony Details", faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, opad);
        info.addPara("Size: " + market.getSize(), pad);
        info.addPara("Tier: " + tier.name(), pad);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_EXPLORATION);
        tags.add(CrusadersBaseConstruction.CRUSADERS_FACTION);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (entity != null && entity.isDiscoverable()) {
            return system.getCenter();
        }
        return entity;
    }

    // 以下为劫掠惩罚示例，供外部市场条件调用
    public float getAccessibilityPenalty() {
        return raidInProgress ? 0.1f : 0f;
    }

    public float getStabilityPenalty() {
        return raidInProgress ? 1f : 0f;
    }
}