package data.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.fleets.*;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

import java.util.Random;

public class Guardfleet2 extends BaseHazardCondition implements RouteManager.RouteFleetSpawner, FleetEventListener{
    protected IntervalUtil tracker = new IntervalUtil(Global.getSettings().getFloat("averagePatrolSpawnInterval") * 0.75F, Global.getSettings().getFloat("averagePatrolSpawnInterval") * 1.3F);
    private CampaignFleetAPI stationFleet = null;




    public void apply(String id) {
        // Ignore uncolonized
        if (market.getFaction() == null || market.getFaction().getId().equals(Factions.NEUTRAL)) return;


        Industry industry = market.getIndustry(Industries.MILITARYBASE);
        if (industry != null) {
            if (industry.isFunctional()) {
                modifyAllFactionMarkets(id, market.getFaction());
            } else {
                unmodifyAllFactionMarkets(id, market.getFaction());
            }
        }

        industry = market.getIndustry(Industries.HIGHCOMMAND);
        if (industry != null) {
            if (industry.isFunctional()) {
                modifyAllFactionMarkets(id, market.getFaction());
            } else {
                unmodifyAllFactionMarkets(id, market.getFaction());
            }
        }
    }

    @Override
    public void unapply(String id) {
        unmodifyAllFactionMarkets(id, market.getFaction());
    }

    private void modifyAllFactionMarkets(String id, FactionAPI faction) {
        for (MarketAPI thisMarket : Misc.getFactionMarkets(faction)) {
            float PRODUCTION_BONUS = 0.50f;
            thisMarket.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyFlat(id, PRODUCTION_BONUS, "Guard Fleet Anchorage");
            super.apply(id);
        }
    }

    private void unmodifyAllFactionMarkets(String id, FactionAPI faction) {
        for (MarketAPI thisMarket : Misc.getFactionMarkets(faction)) {
            thisMarket.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(id);
            super.apply(id);
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

        float combat;
        float tanker;
        float freighter;
        combat = Global.getSettings().getBattleSize() * (0.33f + 0.07f * random.nextFloat());
        tanker = Global.getSettings().getBattleSize() * 0.02f;
        freighter = Global.getSettings().getBattleSize() * 0.02f;

        CampaignFleetAPI fleet = Guardfleetmanager.createGuardFleet2(combat, tanker, freighter,market);

        if (fleet.isEmpty()) return null;

        fleet.setFaction(market.getFactionId(), true);
        fleet.setNoFactionInName(true);

        fleet.addEventListener(this);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true, 0.3f);

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
        // this will get overridden by the patrol assignment AI, depending on route-time elapsed etc
        fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

        fleet.addScript(new PatrolAssignmentAIV4(fleet, route));

        if (custom.spawnFP <= 0) {
            custom.spawnFP = fleet.getFleetPoints();
        }

        return fleet;
    }

    @Override
    public boolean shouldCancelRouteAfterDelayCheck(RouteManager.RouteData route) {
        return false;
    }

    @Override
    public boolean shouldRepeat(RouteManager.RouteData route) {
        return false;
    }

    @Override
    public void reportAboutToBeDespawnedByRouteManager(RouteManager.RouteData route) {

    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param) {

    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {

    }


}