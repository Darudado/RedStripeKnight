package data.campaign.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.fleets.*;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

public class CRAutoControlSystem extends BaseIndustry implements RouteManager.RouteFleetSpawner, FleetEventListener {
    public static float DEFENSE_BONUS = 1.25F;
    public static int IMPROVE_NUM_PATROLS_BONUS = 5;
    public static float FLEET_BONUS = 1.5F;
    protected IntervalUtil tracker = new IntervalUtil(Global.getSettings().getFloat("averagePatrolSpawnInterval") * 0.75F, Global.getSettings().getFloat("averagePatrolSpawnInterval") * 1.3F);
    protected float returningPatrolValue = 0.0F;

    public CRAutoControlSystem() {
    }

    @Override
    public void apply() {
        int size = this.market.getSize()+1;
        int extraDemand = 0;
        int light = 1;
        int medium = 0;
        int heavy = 0;
        if (size <= 3) {
            extraDemand = 0;
        } else if (size == 4) {
            extraDemand = 1;
        } else if (size == 5) {
            extraDemand = 2;
        }else if (size >= 6) {
            extraDemand = 3;
        }

        if (size <= 3) {
            light = 2;
            medium = 0;
            heavy = 0;
        } else if (size == 4) {
            light = 2;
            medium = 0;
            heavy = 0;
        } else if (size == 5) {
            light = 2;
            medium = 1;
            heavy = 0;
        } else if (size == 6) {
            light = 3;
            medium = 1;
            heavy = 0;
        } else if (size == 7) {
            light = 3;
            medium = 2;
            heavy = 0;
        } else if (size == 8) {
            light = 3;
            medium = 3;
            heavy = 0;
        } else if (size >= 9) {
            light = 4;
            medium = 3;
            heavy = 0;
        }

        this.market.getStats().getDynamic().getMod("patrol_num_light_mod").modifyFlat(this.getModId(), (float)light);
        this.market.getStats().getDynamic().getMod("patrol_num_medium_mod").modifyFlat(this.getModId(), (float)medium);
        this.market.getStats().getDynamic().getMod("patrol_num_heavy_mod").modifyFlat(this.getModId(), (float)heavy);
        this.demand("supplies", size - 3 + extraDemand);
        this.demand("fuel", size - 3 + extraDemand);
        this.demand("ships", size - 3 + extraDemand);

        this.modifyStabilityWithBaseMod();
        float mult = this.getDeficitMult(new String[]{"supplies"});
        String extra = "";
        if (mult != 1.0F) {
            String com = (String)this.getMaxDeficit(new String[]{"supplies"}).one;
            extra = " (" + getDeficitText(com).toLowerCase() + ")";
        }

        if(market.getFaction().getId().equals("cinis_of_crusaders")) {

            float bonus = DEFENSE_BONUS;
            this.market.getStats().getDynamic().getMod("ground_defenses_mod").modifyMult(this.getModId(), 1.0F + bonus * mult, this.getNameForModifier() + extra);
            MemoryAPI memory = this.market.getMemoryWithoutUpdate();
            Misc.setFlagWithReason(memory, "$command", this.getModId(), true, -1.0F);

            this.market.getStats().getDynamic().getMod("combat_fleet_size_mult").modifyMult(this.getModId(), 1.0F + FLEET_BONUS, " (" + this.getNameForModifier() + ")");
        }
    }

    public void unapply() {
        super.unapply();
        MemoryAPI memory = this.market.getMemoryWithoutUpdate();
        Misc.setFlagWithReason(memory, "$command", this.getModId(), false, -1.0F);
        this.unmodifyStabilityWithBaseMod();
        this.market.getStats().getDynamic().getMod("patrol_num_light_mod").unmodifyFlat(this.getModId());
        this.market.getStats().getDynamic().getMod("patrol_num_medium_mod").unmodifyFlat(this.getModId());
        this.market.getStats().getDynamic().getMod("patrol_num_heavy_mod").unmodifyFlat(this.getModId());
        this.market.getStats().getDynamic().getMod("ground_defenses_mod").unmodifyMult(this.getModId());
        this.market.getStats().getDynamic().getMod("combat_fleet_size_mult").unmodifyMult(this.getModId(0));
    }

    protected boolean hasPostDemandSection(boolean hasDemand, Industry.IndustryTooltipMode mode) {
        return mode != IndustryTooltipMode.NORMAL || this.isFunctional();
    }

    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, Industry.IndustryTooltipMode mode) {
        if (mode != IndustryTooltipMode.NORMAL || this.isFunctional()) {
            this.addStabilityPostDemandSection(tooltip, hasDemand, mode);
            boolean command = this.getSpec().hasTag("command");
            float bonus = DEFENSE_BONUS;
            if (command) {
                bonus = DEFENSE_BONUS;
            }

            this.addGroundDefensesImpactSection(tooltip, bonus, new String[]{"supplies"});
        }

    }

    protected int getBaseStabilityMod() {
        boolean command = this.getSpec().hasTag("command");
        int stabilityMod = 1;
        if (command) {
            stabilityMod = 2;
        }

        return stabilityMod;
    }

    public String getNameForModifier() {
        return this.getSpec().getName().contains("CRDF") ? this.getSpec().getName() : Misc.ucFirst(this.getSpec().getName().toLowerCase());
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

    public MarketCMD.RaidDangerLevel adjustCommodityDangerLevel(String commodityId, MarketCMD.RaidDangerLevel level) {
        return level.next();
    }

    public MarketCMD.RaidDangerLevel adjustItemDangerLevel(String itemId, String data, MarketCMD.RaidDangerLevel level) {
        return level.next();
    }

    protected void buildingFinished() {
        super.buildingFinished();
        this.tracker.forceIntervalElapsed();
    }

    protected void upgradeFinished(Industry previous) {
        super.upgradeFinished(previous);
        this.tracker.forceIntervalElapsed();
    }

    public void advance(float amount) {
        super.advance(amount);
        if (!Global.getSector().getEconomy().isSimMode()) {
            if (this.isFunctional()) {
                float days = Global.getSector().getClock().convertToDays(amount);
                float spawnRate = 1.0F;
                float rateMult = this.market.getStats().getDynamic().getStat("combat_fleet_spawn_rate_mult").getModifiedValue();
                spawnRate *= rateMult;
                if (Global.getSector().isInNewGameAdvance()) {
                    spawnRate *= 3.0F;
                }

                float extraTime = 0.0F;
                if (this.returningPatrolValue > 0.0F) {
                    float interval = this.tracker.getIntervalDuration();
                    extraTime = interval * days;
                    this.returningPatrolValue -= days;
                    if (this.returningPatrolValue < 0.0F) {
                        this.returningPatrolValue = 0.0F;
                    }
                }

                this.tracker.advance(days * spawnRate + extraTime);
                if (DebugFlags.FAST_PATROL_SPAWN) {
                    this.tracker.advance(days * spawnRate * 100.0F);
                }

                if (this.tracker.intervalElapsed()) {
                    String sid = this.getRouteSourceId();
                    int light = this.getCount(FleetFactory.PatrolType.FAST);
                    int medium = this.getCount(FleetFactory.PatrolType.COMBAT);
                    int heavy = this.getCount(FleetFactory.PatrolType.HEAVY);
                    int maxLight = this.getMaxPatrols(FleetFactory.PatrolType.FAST);
                    int maxMedium = this.getMaxPatrols(FleetFactory.PatrolType.COMBAT);
                    int maxHeavy = this.getMaxPatrols(FleetFactory.PatrolType.HEAVY);
                    WeightedRandomPicker<FleetFactory.PatrolType> picker = new WeightedRandomPicker<>();
                    picker.add(FleetFactory.PatrolType.HEAVY, (float)(maxHeavy - heavy));
                    picker.add(FleetFactory.PatrolType.COMBAT, (float)(maxMedium - medium));
                    picker.add(FleetFactory.PatrolType.FAST, (float)(maxLight - light));
                    if (picker.isEmpty()) {
                        return;
                    }

                    FleetFactory.PatrolType type = (FleetFactory.PatrolType)picker.pick();
                    MilitaryBase.PatrolFleetData custom = new MilitaryBase.PatrolFleetData(type);
                    RouteManager.OptionalFleetData extra = new RouteManager.OptionalFleetData(this.market);
                    extra.fleetType = type.getFleetType();
                    RouteManager.RouteData route = RouteManager.getInstance().addRoute(sid, this.market, Misc.genRandomSeed(), extra, this, custom);
                    extra.strength = (float)getPatrolCombatFP(type, route.getRandom());
                    extra.strength = Misc.getAdjustedStrength(extra.strength, this.market);
                    float patrolDays = 35.0F + (float)Math.random() * 10.0F;
                    route.addSegment(new RouteManager.RouteSegment(patrolDays, this.market.getPrimaryEntity()));
                }

            }
        }
    }

    public void reportAboutToBeDespawnedByRouteManager(RouteManager.RouteData route) {
    }

    public boolean shouldRepeat(RouteManager.RouteData route) {
        return false;
    }

    public int getCount(FleetFactory.PatrolType... types) {
        int count = 0;

        for(RouteManager.RouteData data : RouteManager.getInstance().getRoutesForSource(this.getRouteSourceId())) {
            if (data.getCustom() instanceof MilitaryBase.PatrolFleetData custom) {

                for(FleetFactory.PatrolType type : types) {
                    if (type == custom.type) {
                        ++count;
                        break;
                    }
                }
            }
        }

        return count;
    }


    public int getMaxPatrols(FleetFactory.PatrolType type) {
        if (type == FleetFactory.PatrolType.FAST) {
            return (int)this.market.getStats().getDynamic().getMod("patrol_num_light_mod").computeEffective(0.0F);
        } else if (type == FleetFactory.PatrolType.COMBAT) {
            return (int)this.market.getStats().getDynamic().getMod("patrol_num_medium_mod").computeEffective(0.0F);
        } else {
            return type == FleetFactory.PatrolType.HEAVY ? (int)this.market.getStats().getDynamic().getMod("patrol_num_heavy_mod").computeEffective(0.0F) : 0;
        }
    }

    public String getRouteSourceId() {
        return this.getMarket().getId() + "_" + "military";
    }

    public boolean shouldCancelRouteAfterDelayCheck(RouteManager.RouteData route) {
        return false;
    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
    }

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {
        if (this.isFunctional()) {
            if (reason == CampaignEventListener.FleetDespawnReason.REACHED_DESTINATION) {
                RouteManager.RouteData route = RouteManager.getInstance().getRoute(this.getRouteSourceId(), fleet);
                if (route.getCustom() instanceof MilitaryBase.PatrolFleetData custom) {
                    if (custom.spawnFP > 0) {
                        float fraction = (float)(fleet.getFleetPoints() / custom.spawnFP);
                        this.returningPatrolValue += fraction;
                    }
                }
            }

        }
    }

    public static int getPatrolCombatFP(FleetFactory.PatrolType type, Random random) {
        float combat = 0.0F;
        switch (type) {
            case FAST -> combat = (float)Math.round(3.0F + random.nextFloat() * 2.0F) * 5.0F;
            case COMBAT -> combat = (float)Math.round(6.0F + random.nextFloat() * 3.0F) * 5.0F;
            case HEAVY -> combat = (float)Math.round(10.0F + random.nextFloat() * 5.0F) * 5.0F;
        }

        return Math.round(combat);
    }

    public CampaignFleetAPI spawnFleet(RouteManager.RouteData route) {

        MilitaryBase.PatrolFleetData custom = (MilitaryBase.PatrolFleetData) route.getCustom();
        FleetFactory.PatrolType type = custom.type;

        Random random = route.getRandom();

        float combat = 0f;
        float tanker = 0f;
        float freighter = 0f;
        combat = Global.getSettings().getBattleSize()*(0.33f+0.07f*random.nextFloat());
        tanker = Global.getSettings().getBattleSize()*0.02f;
        freighter = Global.getSettings().getBattleSize()*0.02f;

        CampaignFleetAPI fleet = createPatrol(type, this.market.getFactionId(), route, this.market, (Vector2f)null, random);

        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setFaction(market.getFactionId(), true);
        fleet.setNoFactionInName(true);

        fleet.addEventListener(this);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true, 0.3f);

        if (type == FleetFactory.PatrolType.FAST || type == FleetFactory.PatrolType.COMBAT) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_CUSTOMS_INSPECTOR, true);
        }

        String postId = Ranks.POST_PATROL_COMMANDER;
        String rankId = Ranks.SPACE_COMMANDER;
        switch (type) {
            case FAST:
                rankId = Ranks.SPACE_LIEUTENANT;
                break;
            case COMBAT:
                rankId = Ranks.SPACE_COMMANDER;
                break;
            case HEAVY:
                rankId = Ranks.SPACE_CAPTAIN;
                break;
        }

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
        // this will get overridden by the patrol assignment AI, depending on route-time elapsed etc
        fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

        fleet.addScript(new PatrolAssignmentAIV4(fleet, route));

        //market.getContainingLocation().addEntity(fleet);
        //fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

        if (custom.spawnFP <= 0) {
            custom.spawnFP = fleet.getFleetPoints();
        }

        return fleet;
    }

    public static CampaignFleetAPI createPatrol(FleetFactory.PatrolType type, String factionId, RouteManager.RouteData route, MarketAPI market, Vector2f locInHyper, Random random) {
        return createPatrol(type, 0.0F, factionId, route, market, locInHyper, random);
    }

    public static CampaignFleetAPI createPatrol(FleetFactory.PatrolType type, float extraTankerPts, String factionId, RouteManager.RouteData route, MarketAPI market, Vector2f locInHyper, Random random) {
        if (random == null) {
            random = new Random();
        }

        float combat = (float)getPatrolCombatFP(type, random);
        float tanker = 0.0F;
        float freighter = 0.0F;
        String fleetType = type.getFleetType();
        switch (type) {
            case FAST:
            default:
                break;
            case COMBAT:
                tanker = (float)Math.round(random.nextFloat() * 5.0F);
                break;
            case HEAVY:
                tanker = (float)Math.round(random.nextFloat() * 10.0F);
                freighter = (float)Math.round(random.nextFloat() * 10.0F);
        }

        tanker += extraTankerPts;
        FleetParamsV3 params = new FleetParamsV3(market, locInHyper, factionId, route == null ? null : route.getQualityOverride(), fleetType, combat, freighter, tanker, 0.0F, 0.0F, 0.0F, 0.0F);
        if (route != null) {
            params.timestamp = route.getTimestamp();
        }

        params.random = random;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet != null && !fleet.isEmpty()) {
            if (!fleet.getFaction().getCustomBoolean("patrolsHaveNoPatrolMemoryKey")) {
                fleet.getMemoryWithoutUpdate().set("$isPatrol", true);
                if (type == FleetFactory.PatrolType.FAST || type == FleetFactory.PatrolType.COMBAT) {
                    fleet.getMemoryWithoutUpdate().set("$isCustomsInspector", true);
                }
            } else if (fleet.getFaction().getCustomBoolean("pirateBehavior")) {
                fleet.getMemoryWithoutUpdate().set("$isPirate", true);
                if (market != null && market.isHidden()) {
                    fleet.getMemoryWithoutUpdate().set("$isRaider", true);
                }
            }

            String postId = Ranks.POST_PATROL_COMMANDER;
            String rankId = Ranks.SPACE_COMMANDER;
            switch (type) {
                case FAST -> rankId = Ranks.SPACE_LIEUTENANT;
                case COMBAT -> rankId = Ranks.SPACE_COMMANDER;
                case HEAVY -> rankId = Ranks.SPACE_CAPTAIN;
            }

            fleet.getCommander().setPostId(postId);
            fleet.getCommander().setRankId(rankId);
            return fleet;
        } else {
            return null;
        }
    }

    protected void applyImproveModifiers() {
        String key = "mil_base_improve";
            boolean command = this.getSpec().hasTag("command");
            if (command) {
                this.market.getStats().getDynamic().getMod("patrol_num_medium_mod").modifyFlat(key, (float)IMPROVE_NUM_PATROLS_BONUS);
            } else {
                this.market.getStats().getDynamic().getMod("patrol_num_heavy_mod").modifyFlat(key, (float)IMPROVE_NUM_PATROLS_BONUS);
            }

    }

}