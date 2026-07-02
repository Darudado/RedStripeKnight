//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package data.campaign.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.PirateRaidActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import indevo.industries.changeling.industry.population.HelldiversSubIndustry;
import indevo.industries.changeling.industry.population.SwitchablePopulation;
import indevo.industries.engineeringhub.industry.EngineeringHub;
import indevo.industries.privateer.intel.PrivateerBaseRaidIntel;
import indevo.utils.helper.MiscIE;
import indevo.utils.helper.StringHelper;
import indevo.utils.scripts.IndustryAddOrRemovePlugin;
import indevo.utils.timers.RaidTimeout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.log4j.Logger;

import static data.scripts.RSModPlugin.HAVE_INDEVO;

public class CR_RaidBase extends BaseIndustry implements EconomyTickListener, RaidIntel.RaidDelegate {
    public static final float TIMOUT_NO_CORE = 6.0F;
    public static final float TIMOUT_WITH_CORE = 3.0F;
    public static final float SUCCESS_OUTPUT_FRACT = 0.9F;
    public static final float FAIL_OUTPUT_FRACT = 0.45F;
    public RaidIntel currentIntel = null;
    private int raidTimeoutMonths = 0;
    private final int supplyDiminishmentPerMonth = 1;
    private float aiCoreFPBonus = 1.0F;
    private Map<String, Integer> supplyMemory = new HashMap<>();
    private final List<String> decayStopTimer = new ArrayList<>();
    private int raidAmounts = 0;
    public boolean debug = false;
    public static final Logger log = Global.getLogger(CR_RaidBase.class);
    private boolean isDemocratic = false;

    public CR_RaidBase() {
    }

    private void debugMessage(String Text) {
        if (this.debug) {
            log.info(Text);
        }

    }

    protected void updateSupplyAndDemandModifiers() {
        super.updateSupplyAndDemandModifiers();
        this.supplyBonus.unmodify();
    }

    public void apply() {
        super.apply(true);
        this.debug = Global.getSettings().isDevMode();
        this.isDemocratic = this.market.getIndustry("population") instanceof SwitchablePopulation && ((SwitchablePopulation)this.market.getIndustry("population")).getCurrent() instanceof HelldiversSubIndustry;
        Global.getSector().getListenerManager().addListener(this, true);
        if (this.supplyMemory != null && !this.supplyMemory.isEmpty()) {
            this.supply.clear();

            for(Map.Entry<String, Integer> e : this.supplyMemory.entrySet()) {
                this.supply(e.getKey(), e.getValue());
            }
        }

        Global.getSector().addTransientScript(new IndustryAddOrRemovePlugin(this.market, "IndEvo_pirateHavenSecondary", false));
    }

    public String getCurrentName() {
        return this.isDemocratic ? this.getString(16) : super.getCurrentName();
    }

    public void unapply() {
        super.unapply();
        Global.getSector().getListenerManager().removeListener(this);
        Global.getSector().addTransientScript(new IndustryAddOrRemovePlugin(this.market, "CR_pirateHavenSecondary", true));
    }

    public boolean isAvailableToBuild() {
        return false;
    }

    public boolean showWhenUnavailable() {
        return false;
    }

    public String getUnavailableReason() {
        if (Misc.getMaxIndustries(this.market) - Misc.getNumIndustries(this.market) < 2) {
            return this.getString(1);
        } else {
            return MiscIE.getAmountOfIndustryInSystem(this.getId(), this.market.getStarSystem(), this.market.getFaction()) > 1 ? this.getString(2) : super.getUnavailableReason();
        }
    }

    public void reportEconomyTick(int iterIndex) {
        if (!HAVE_INDEVO) {
            return;  // 无 IndEvo 则禁用所有劫掠活动
        }

        if (this.debug) {
            this.startRaid(this.getRaidTarget(), this.getBaseRaidFP());
            this.debugOutputs();
        }

//        if (this.isFunctional() && !this.market.hasCondition("IndEvo_pirate_subpop")) {
//            this.market.addCondition("IndEvo_pirate_subpop");
//        }

        int lastIterInMonth = (int)Global.getSettings().getFloat("economyIterPerMonth") - 1;
        if (iterIndex == lastIterInMonth) {
            this.debugMessage("reporting last tick");
            this.supplyDiminishment();
            this.raidTimeoutMonths -= this.raidTimeoutMonths > 0 ? 1 : 0;
            if (this.raidTimeoutMonths <= 0 && this.currentIntel == null && this.isFunctional()) {
                StarSystemAPI target = this.getRaidTarget();
                if (target != null) {
                    this.startRaid(target, this.getBaseRaidFP());
                } else if (this.market.isPlayerOwned()) {
                    MessageIntel intel;
                    if (this.isDemocratic) {
                        intel = new MessageIntel(this.getString(17), Misc.getTextColor());
                    } else {
                        intel = new MessageIntel(this.getString(3), Misc.getTextColor());
                    }

                    intel.addLine(this.getString(4), Misc.getTextColor(), this.getHighlightsString(4), Misc.getHighlightColor());
                    intel.setIcon(Global.getSettings().getSpriteName("IndEvo", "notification"));
                    intel.setSound(BaseIntelPlugin.getSoundStandardPosting());
                    Global.getSector().getCampaignUI().addMessage(intel, MessageClickAction.COLONY_INFO, this.market);
                }
            }

        }
    }

    public void reportEconomyMonthEnd() {
    }

    public float getBaseRaidFP() {
        float alcoholismBonus = 1.0F + this.getAlcoholBonus();
        return (float)(this.market.getSize() * 50) * this.market.getStats().getDynamic().getStat("combat_fleet_size_mult").computeMultMod() * (0.75F + (float)Math.random() * 0.5F) * this.aiCoreFPBonus * this.aiCoreFPBonus * alcoholismBonus;
    }

    private float getAlcoholBonus() {
        float alcoholismBonus = 0.0F;

        for(CommodityOnMarketAPI commodity : this.market.getAllCommodities()) {
            if (commodity.getAvailable() > 0 && commodity.getId().contains("alcoholism")) {
                alcoholismBonus += 0.03F * (float)Math.min(commodity.getAvailable(), commodity.getMaxSupply());
            }
        }

        return alcoholismBonus;
    }

    public void notifyRaidEnded(RaidIntel raid, RaidIntel.RaidStageStatus status) {
        if (!HAVE_INDEVO) return;

        if (this.currentIntel != null && raid == this.currentIntel) {
            this.debugMessage("raid ended: " + raid.getSystem());
            this.currentIntel = null;
            StarSystemAPI system = raid.getSystem();
            if (status == RaidStageStatus.SUCCESS) {
                this.debugMessage("succesful");
                this.setRaidIndustryOutput(system, true);
                float timeout = this.aiCoreId != null && this.aiCoreId.equals("beta_core") ? TIMOUT_WITH_CORE : TIMOUT_NO_CORE;
                RaidTimeout.addRaidedSystem(system, timeout, !this.market.isPlayerOwned());
                if (this.market.isPlayerOwned()) {
                    this.spoilsOfWar(system);
                }

                if (this.isDemocratic) {
                    HelldiversSubIndustry subIndustry = (HelldiversSubIndustry)((SwitchablePopulation)this.market.getIndustry("population")).getCurrent();
                    subIndustry.reportPrivateerBaseRaidFinished(system, true);
                }
            } else {
                ++this.raidTimeoutMonths;
                RaidTimeout.addRaidedSystem(raid.getSystem(), 1.0F, !this.market.isPlayerOwned());
                if (this.isDemocratic) {
                    HelldiversSubIndustry subIndustry = (HelldiversSubIndustry)((SwitchablePopulation)this.market.getIndustry("population")).getCurrent();
                    subIndustry.reportPrivateerBaseRaidFinished(system, false);
                }

                if (raid.getFailStage() >= raid.getStageIndex(raid.getActionStage())) {
                    this.setRaidIndustryOutput(raid.getSystem(), false);
                    if (this.market.isPlayerOwned()) {
                        String h1 = system.getName();
                        if (this.isDemocratic) {
                            Global.getSector().getCampaignUI().addMessage(new MessageIntel(this.getString(18).replace("$starSystemName", h1), Global.getSettings().getColor("standardTextColor"), this.getHighlightsString(18, "$starSystemName", h1), this.getHighlightsColors(18, raid.getFaction().getColor(), Misc.getNegativeHighlightColor())));
                        } else {
                            Global.getSector().getCampaignUI().addMessage(new MessageIntel(this.getString(5).replace("$starSystemName", h1), Global.getSettings().getColor("standardTextColor"), this.getHighlightsString(5, "$starSystemName", h1), this.getHighlightsColors(5, raid.getFaction().getColor(), Misc.getNegativeHighlightColor())));
                        }
                    }
                } else if (this.market.isPlayerOwned()) {
                    String h1 = system.getName();
                    if (this.isDemocratic) {
                        Global.getSector().getCampaignUI().addMessage(this.getString(19).replace("$starSystemName", h1), Misc.getNegativeHighlightColor(), h1, null, raid.getFaction().getColor(), null);
                    } else {
                        Global.getSector().getCampaignUI().addMessage(this.getString(6).replace("$starSystemName", h1), Misc.getNegativeHighlightColor(), h1, null, raid.getFaction().getColor(), null);
                    }
                }

                this.debugMessage("raidTimeout: " + this.raidTimeoutMonths);
            }

        }
    }

    private void debugOutputs() {
        ++this.raidAmounts;
        this.debugMessage("RaidAmount" + this.raidAmounts);
        ArrayList<FactionAPI> factionList = this.getActiveHostileFactions();
        StarSystemAPI bestSystem = null;

        for(int j = 1; j <= factionList.size(); ++j) {
            int rnd = (new Random()).nextInt(factionList.size());
            FactionAPI targetFaction = factionList.get(rnd);
            StarSystemAPI target = this.getBestTargetSystem(targetFaction);
            if (target != null) {
                bestSystem = target;
                break;
            }

            factionList.remove(targetFaction);
        }

        if (bestSystem != null) {
            this.setRaidIndustryOutput(bestSystem, true);
            this.spoilsOfWar(bestSystem);
        }
    }

    private void spoilsOfWar(StarSystemAPI system) {
        ArrayList<MarketAPI> shipUsers = this.getHostileShipUser(system);
        ArrayList<MarketAPI> raidedMarkets = this.getHostileMarketsInSystem(system);
        if (!shipUsers.isEmpty()) {
            for(MarketAPI market : shipUsers) {
                this.getLoot(market, true);
            }

            for(MarketAPI nonShipMarket : raidedMarkets) {
                if (!shipUsers.contains(nonShipMarket)) {
                    this.getLoot(nonShipMarket, false);
                }
            }

            String h1 = system.getName();
            if (this.isDemocratic) {
                Global.getSector().getCampaignUI().addMessage(new MessageIntel(this.getString(20).replace("$starSystemName", h1), Global.getSettings().getColor("standardTextColor"), this.getHighlightsString(20, "$starSystemName", h1), this.getHighlightsColors(20, Misc.getHighlightColor(), Misc.getPositiveHighlightColor())));
            } else {
                Global.getSector().getCampaignUI().addMessage(new MessageIntel(this.getString(7).replace("$starSystemName", h1), Global.getSettings().getColor("standardTextColor"), this.getHighlightsString(7, "$starSystemName", h1), this.getHighlightsColors(7, Misc.getHighlightColor(), Misc.getPositiveHighlightColor())));
            }
        } else {
            for(MarketAPI nonShipMarket : raidedMarkets) {
                this.getLoot(nonShipMarket, false);
            }

            String h1 = system.getName();
            if (this.isDemocratic) {
                Global.getSector().getCampaignUI().addMessage(new MessageIntel(this.getString(21).replace("$starSystemName", h1), Global.getSettings().getColor("standardTextColor"), this.getHighlightsString(21, "$starSystemName", h1), this.getHighlightsColors(21, Misc.getHighlightColor(), Misc.getHighlightColor())));
            } else {
                Global.getSector().getCampaignUI().addMessage(new MessageIntel(this.getString(8).replace("$starSystemName", h1), Global.getSettings().getColor("standardTextColor"), this.getHighlightsString(8, "$starSystemName", h1), this.getHighlightsColors(8, Misc.getHighlightColor(), Misc.getHighlightColor())));
            }
        }

    }

    protected void getLoot(MarketAPI target, boolean withBP) {
        FactionAPI playerFaction = Global.getSector().getPlayerFaction();
        float chanceOfDrop = 0.7F + (this.aiCoreId != null && this.aiCoreId.equals("alpha_core") ? 0.2F : 0.0F);
        float chanceOfExtraDrop = 0.3F + (this.aiCoreId != null && this.aiCoreId.equals("alpha_core") ? 0.1F : 0.0F);
        CargoAPI cargo = MiscIE.getStorageCargo(this.market);
        if (cargo == null) {
            cargo = Global.getSector().getPlayerFaction().getProduction().getGatheringPoint().getSubmarket("storage").getCargo();
        }

        Random random = new Random();
        String ship = "MarketCMD_ship____";
        String weapon = "MarketCMD_weapon__";
        String fighter = "MarketCMD_fighter_";
        if (withBP) {
            WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
            //boolean raidUnknownOnly = Settings.getBoolean("IndEvo_RaidForUnknownOnly");

            for(String id : target.getFaction().getKnownShips()) {
                if (!playerFaction.knowsShip(id)) {
                    picker.add(ship + id, 1.0F);
                }
            }

            for(String id : target.getFaction().getKnownWeapons()) {
                if (!playerFaction.knowsWeapon(id)) {
                    picker.add(weapon + id, 1.0F);
                }
            }

            for(String id : target.getFaction().getKnownFighters()) {
                if (!playerFaction.knowsFighter(id)) {
                    picker.add(fighter + id, 1.0F);
                }
            }

            int num = this.getNumPicks(random, chanceOfDrop, chanceOfExtraDrop * 0.5F);

            for(int i = 0; i < num && !picker.isEmpty(); ++i) {
                String id = picker.pickAndRemove();
                if (id != null) {
                    if (id.startsWith(ship)) {
                        String specId = id.substring(ship.length());
                        if (!Global.getSettings().getHullSpec(specId).hasTag("no_bp_drop") && !Global.getSettings().getHullSpec(specId).getHints().contains(ShipTypeHints.UNBOARDABLE)) {
                            if (EngineeringHub.isTiandong(specId)) {
                                cargo.addSpecial(new SpecialItemData("tiandong_retrofit_bp", specId), 1.0F);
                            } else if (EngineeringHub.isRoider(specId)) {
                                cargo.addSpecial(new SpecialItemData("roider_retrofit_bp", specId), 1.0F);
                            } else {
                                cargo.addSpecial(new SpecialItemData("ship_bp", specId), 1.0F);
                            }
                        }
                    } else if (id.startsWith(weapon)) {
                        String specId = id.substring(weapon.length());
                        if (!Global.getSettings().getWeaponSpec(specId).hasTag("no_bp_drop")) {
                            cargo.addSpecial(new SpecialItemData("weapon_bp", specId), 1.0F);
                        }
                    } else if (id.startsWith(fighter)) {
                        String specId = id.substring(fighter.length());
                        if (!Global.getSettings().getFighterWingSpec(specId).hasTag("no_bp_drop")) {
                            cargo.addSpecial(new SpecialItemData("fighter_bp", specId), 1.0F);
                        }
                    }
                }
            }
        }

        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();

        for(String id : target.getFaction().getKnownWeapons()) {
            WeaponSpecAPI w = Global.getSettings().getWeaponSpec(id);
            if (!w.hasTag("no_drop") && !w.getAIHints().contains(AIHints.SYSTEM) && (withBP || w.getTier() <= 1 && w.getSize() != WeaponSize.LARGE)) {
                picker.add(weapon + id, w.getRarity());
            }
        }

        for(String id : target.getFaction().getKnownFighters()) {
            FighterWingSpecAPI f = Global.getSettings().getFighterWingSpec(id);
            if (!f.hasTag("no_drop") && (withBP || f.getTier() <= 0)) {
                picker.add(fighter + id, f.getRarity());
            }
        }

        int num = this.getNumPicks(random, chanceOfDrop, chanceOfExtraDrop * 0.5F) * 3;
        if (withBP) {
            num += target.getSize();
        }

        for(int i = 0; i < num && !picker.isEmpty(); ++i) {
            String id = picker.pickAndRemove();
            if (id != null) {
                if (id.startsWith(weapon)) {
                    cargo.addWeapons(id.substring(weapon.length()), 1);
                } else if (id.startsWith(fighter)) {
                    cargo.addFighters(id.substring(fighter.length()), 1);
                }
            }
        }

    }

    protected int getNumPicks(Random random, float pAny, float pMore) {
        if (random.nextFloat() >= pAny) {
            return 0;
        } else {
            int result = 1;

            for(int i = 0; i < 10 && !(random.nextFloat() >= pMore); ++i) {
                ++result;
            }

            return result;
        }
    }

    private void supplyDiminishment() {
        this.debugMessage("supplyDiminishment");

        for(MutableCommodityQuantity sup : this.getAllSupply()) {
            String id = sup.getCommodityId();
            if (this.decayStopTimer.contains(id)) {
                this.decayStopTimer.remove(id);
            } else {
                int q = this.getBaseSupply(id) - 1;
                this.supply.remove(id);
                if (q > 0) {
                    this.supply(id, q);
                    this.supplyMemory.put(id, q);
                } else {
                    this.supplyMemory.remove(id);
                }
            }
        }

    }

    private int getBaseSupply(String id) {
        return this.supplyMemory.getOrDefault(id, 0);
    }

    private void setRaidIndustryOutput(StarSystemAPI raidedSystem, boolean successful) {
        for(MarketAPI raidedMarket : this.getHostileMarketsInSystem(raidedSystem)) {
            this.debugMessage("Setting Base Output for raided Market " + raidedMarket.getName());

            for(Map.Entry<String, Integer> raidSupply : this.getMarketProductionAmounts(raidedMarket).entrySet()) {
                String id = raidSupply.getKey();
                if (!id.equals("ships")) {
                    float successMult = successful ? SUCCESS_OUTPUT_FRACT : FAIL_OUTPUT_FRACT;
                    int raidSupplyValue = Math.round((float) raidSupply.getValue() * successMult);
                    int currentSupply = this.getBaseSupply(id);
                    if (currentSupply < raidSupplyValue) {
                        this.supply.remove(id);
                        this.supplyMemory.put(id, raidSupplyValue);
                        this.supply(id, raidSupplyValue);
                        if (this.getAICoreId() != null && this.getAICoreId().equals("gamma_core")) {
                            this.decayStopTimer.add(raidSupply.getKey());
                        }
                    }
                }
            }
        }

    }

    private StarSystemAPI getRaidTarget() {
        ArrayList<FactionAPI> factionList = this.getActiveHostileFactions();
        StarSystemAPI bestSystem = null;
        if (!factionList.isEmpty()) {
            for(int j = 0; j <= factionList.size(); ++j) {
                int rnd = (new Random()).nextInt(factionList.size());
                FactionAPI targetFaction = factionList.get(rnd);
                if (targetFaction != null) {
                    StarSystemAPI target = this.getBestTargetSystem(targetFaction);
                    if (target != null) {
                        bestSystem = target;
                        break;
                    }

                    factionList.remove(targetFaction);
                }
            }
        }

        return bestSystem;
    }

    private StarSystemAPI getBestTargetSystem(FactionAPI faction) {
        HashMap<StarSystemAPI, Float> systemRatingMap = new HashMap<>();
        this.debugMessage("targeted faction: " + faction.getDisplayName());

        for(StarSystemAPI system : this.getFactionStarSystemList(faction)) {
            if (system != null) {
                String var10001 = system.getName();
                this.debugMessage("system name: " + var10001);
                if (RaidTimeout.containsSystem(system, !this.market.isPlayerOwned())) {
                    this.debugMessage("System locked");
                } else {
                    float totalSystemVal = 0.0F;

                    for(MarketAPI market : this.getHostileMarketsInSystem(system)) {
                        if (!market.getPrimaryEntity().isInHyperspace()) {
                            boolean hasStation = market.hasIndustry("orbitalstation") || market.hasIndustry("orbitalstation_mid") || market.hasIndustry("orbitalstation_high");
                            boolean milHQ = market.hasIndustry("patrolhq");
                            boolean milBA = market.hasIndustry("militarybase");
                            boolean milCO = market.hasIndustry("highcommand");
                            float marketSize = (float)market.getSize();
                            boolean hasHIorOW = market.hasIndustry("heavyindustry") || market.hasIndustry("orbitalworks") || market.hasIndustry("ms_modularFac") || market.hasIndustry("ms_massIndustry");
                            int totalOutputAmounts = 0;

                            for(Map.Entry<String, Integer> output : this.getMarketProductionAmounts(market).entrySet()) {
                                totalOutputAmounts += output.getValue();
                            }

                            float defenceRating = 0.0F;

                            for(CampaignFleetAPI fleet : Misc.getFleetsInOrNearSystem(system)) {
                                if (fleet.getFaction().isHostileTo(this.market.getFaction())) {
                                    defenceRating -= (float)fleet.getFleetPoints();
                                }
                            }

                            defenceRating += this.getBaseRaidFP() * 1.5F;
                            totalSystemVal += defenceRating;
                            totalSystemVal -= hasStation ? 20.0F : -10.0F;
                            totalSystemVal -= milHQ ? 1.5F * marketSize : -5.0F;
                            totalSystemVal -= milBA ? 3.0F * marketSize : 0.0F;
                            totalSystemVal -= milCO ? 4.0F * marketSize : 0.0F;
                            totalSystemVal += marketSize;
                            totalSystemVal += hasHIorOW ? 20.0F : 0.0F;
                            totalSystemVal += (float)((int)((float)totalOutputAmounts / 2.0F));
                            this.debugMessage("system market: " + market.getName());
                        }
                    }

                    this.debugMessage("system rating: " + totalSystemVal);
                    systemRatingMap.put(system, totalSystemVal);
                }
            }
        }

        float bestSystemValue = -Float.MAX_VALUE;
        StarSystemAPI bestSystem = null;

        for(Map.Entry<StarSystemAPI, Float> ratedSystem : systemRatingMap.entrySet()) {
            if (ratedSystem.getValue() > bestSystemValue) {
                bestSystemValue = ratedSystem.getValue();
                bestSystem = ratedSystem.getKey();
            }
        }

        if (bestSystem != null) {
            this.debugMessage("raid target: " + bestSystem.getName());
        } else {
            this.debugMessage("no valid target!");
        }

        return bestSystem;
    }

    public void startRaid(StarSystemAPI target, float baseRaidFP) {
        this.debugMessage("starting a raid");
        StarSystemAPI system = this.market.getStarSystem();
        SectorEntityToken entity = this.market.getPrimaryEntity();
        FactionAPI faction = this.market.getFaction();
        boolean hasTargets = false;

        for(MarketAPI curr : MiscIE.getMarketsInLocation(target)) {
            if (curr.getFaction().isHostileTo(faction)) {
                hasTargets = true;
                break;
            }
        }

        if (hasTargets) {
            RaidIntel raid = new PrivateerBaseRaidIntel(target, faction, this);
            float successMult = 0.5F;
            JumpPointAPI gather = null;
            List<JumpPointAPI> points = system.getEntities(JumpPointAPI.class);
            float min = Float.MAX_VALUE;

            for(JumpPointAPI curr : points) {
                float dist = Misc.getDistance(entity.getLocation(), curr.getLocation());
                if (dist < min) {
                    min = dist;
                    gather = curr;
                }
            }

            AssembleStage assemble = new AssembleStage(raid, gather);
            assemble.addSource(this.market);
            assemble.setSpawnFP(baseRaidFP);
            assemble.setAbortFP(baseRaidFP * successMult);
            raid.addStage(assemble);
            SectorEntityToken raidJump = RouteLocationCalculator.findJumpPointToUse(faction, target.getCenter());
            TravelStage travel = new TravelStage(raid, gather, raidJump, false);
            travel.setAbortFP(baseRaidFP * successMult);
            raid.addStage(travel);
            PirateRaidActionStage action = new PirateRaidActionStage(raid, target);
            action.setAbortFP(baseRaidFP * successMult);
            raid.addStage(action);
            raid.addStage(new ReturnStage(raid));
            String var10001 = target.getName();
            this.debugMessage("Raid target: " + var10001 + " / faction: " + faction.getDisplayName() + " / FP: " + baseRaidFP);
            Global.getSector().getIntelManager().addIntel(raid, false);
            this.currentIntel = raid;
            RaidTimeout.addRaidedSystem(target, 3.0F, !this.market.isPlayerOwned());
        }
    }

    public ArrayList<FactionAPI> getActiveHostileFactions() {
        ArrayList<FactionAPI> activeHostileFactions = new ArrayList<>();

        for(MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            FactionAPI targetFaction = market.getFaction();
            if (!activeHostileFactions.contains(targetFaction) && targetFaction.isHostileTo(this.market.getFaction()) && (targetFaction.isShowInIntelTab() || targetFaction.isPlayerFaction()) && !market.isHidden() && market.isInEconomy()) {
                activeHostileFactions.add(market.getFaction());
            }
        }

        return activeHostileFactions;
    }

    public ArrayList<StarSystemAPI> getFactionStarSystemList(FactionAPI faction) {
        ArrayList<StarSystemAPI> systemList = new ArrayList<>();

        for(MarketAPI market : Misc.getFactionMarkets(faction)) {
            if (!systemList.contains(market.getStarSystem()) && !market.isHidden() && market.isInEconomy()) {
                systemList.add(market.getStarSystem());
            }
        }

        return systemList;
    }

    public ArrayList<MarketAPI> getHostileMarketsInSystem(StarSystemAPI system) {
        ArrayList<MarketAPI> marketList = new ArrayList<>();

        for(MarketAPI market : MiscIE.getMarketsInLocation(system)) {
            if (market.getFaction().isHostileTo(this.market.getFaction())) {
                marketList.add(market);
            }
        }

        return marketList;
    }

    public HashMap<String, Integer> getMarketProductionAmounts(MarketAPI market) {
        HashMap<String, Integer> prod = new HashMap<>();

        for(Industry ind : market.getIndustries()) {
            for(MutableCommodityQuantity sup : ind.getAllSupply()) {
                if (prod.containsKey(sup.getCommodityId()) && prod.get(sup.getCommodityId()) < sup.getQuantity().getModifiedInt()) {
                    prod.put(sup.getCommodityId(), sup.getQuantity().getModifiedInt());
                } else if (!prod.containsKey(sup.getCommodityId())) {
                    prod.put(sup.getCommodityId(), sup.getQuantity().getModifiedInt());
                }
            }
        }

        return prod;
    }

    public ArrayList<MarketAPI> getHostileShipUser(StarSystemAPI system) {
        ArrayList<MarketAPI> list = new ArrayList<>();

        for(MarketAPI market : this.getHostileMarketsInSystem(system)) {
            boolean HI = market.hasIndustry("heavyindustry");
            boolean OW = market.hasIndustry("orbitalworks");
            boolean SY = market.hasIndustry("ms_modularFac") || market.hasIndustry("ms_massIndustry");
            boolean MB = market.hasIndustry("militarybase");
            boolean HC = market.hasIndustry("highcommand");
            if (HI || OW || MB || HC || SY) {
                list.add(market);
            }
        }

        return list;
    }

    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, Industry.IndustryTooltipMode mode) {
        if (this.market.isPlayerOwned()) {
            if (this.currTooltipMode == IndustryTooltipMode.ADD_INDUSTRY && this.isAvailableToBuild()) {
                tooltip.addPara(this.getString(1), Misc.getHighlightColor(), 10.0F);
                tooltip.addPara(this.getString(2), Misc.getHighlightColor(), 3.0F);
            }

            if (this.isFunctional()) {
                if (this.isFunctional() && this.currTooltipMode == IndustryTooltipMode.NORMAL) {
                    if (this.currentIntel != null) {
                        String h1 = this.currentIntel.getSystem().getBaseName();
                        if (this.isDemocratic) {
                            tooltip.addPara(this.getString(22).replace("$starSystemName", h1), 10.0F, Misc.getHighlightColor(), this.getHighlightsString(22, "$starSystemName", h1));
                        } else {
                            tooltip.addPara(this.getString(9).replace("$starSystemName", h1), 10.0F, Misc.getHighlightColor(), this.getHighlightsString(9, "$starSystemName", h1));
                        }
                    } else if (this.raidTimeoutMonths < 1) {
                        if (this.isDemocratic) {
                            tooltip.addPara(this.getString(23), 10.0F, Misc.getHighlightColor(), this.getHighlightsString(23));
                        } else {
                            tooltip.addPara(this.getString(10), 10.0F, Misc.getHighlightColor(), this.getHighlightsString(10));
                        }
                    } else {
                        String h1 = Misc.getRoundedValue((float)this.raidTimeoutMonths);
                        if (this.isDemocratic) {
                            tooltip.addPara(this.getString(24).replace("$raidCooldownTime", h1), 10.0F, Misc.getHighlightColor(), this.getHighlightsString(24, "$raidCooldownTime", h1));
                        } else {
                            tooltip.addPara(this.getString(11).replace("$raidCooldownTime", h1), 10.0F, Misc.getHighlightColor(), this.getHighlightsString(11, "$raidCooldownTime", h1));
                        }
                    }

                    if (Global.getSettings().getModManager().isModEnabled("alcoholism")) {
                        String h2 = StringHelper.getAbsPercentString(this.getAlcoholBonus(), true);
                        tooltip.addPara(this.getString(12).replace("$bonusPercent", h2), 10.0F, Misc.getPositiveHighlightColor(), h2 + "%");
                    }
                }
            }

        }
    }

    protected void addAlphaCoreDescription(TooltipMakerAPI tooltip, Industry.AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Currently assigned Alpha level AI cores.";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Alpha level AI core.";
        }

        String[] highlights = this.getHighlightsString(13);
        String effect = this.getString(13);
        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlights);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + effect, opad, highlight, highlights);
        }

    }

    protected void addBetaCoreDescription(TooltipMakerAPI tooltip, Industry.AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Currently allocated beta AI cores.";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Beta-level AI core.";
        }

        String[] highlights = this.getHighlightsString(14);
        String effect = this.getString(14);
        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlights);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + effect, opad, highlight, highlights);
        }

    }

    protected void addGammaCoreDescription(TooltipMakerAPI tooltip, Industry.AICoreDescriptionMode mode) {
        float opad = 10.0F;
        Color highlight = Misc.getHighlightColor();
        String pre = "Currently assigned gamma level AI cores.";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Gamma-level AI core.";
        }

        String[] highlights = this.getHighlightsString(15);
        String effect = this.getString(15);
        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(this.aiCoreId);
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48.0F);
            text.addPara(pre + effect, 0.0F, highlight, highlights);
            tooltip.addImageWithText(opad);
        } else {
            tooltip.addPara(pre + effect, 0.0F, highlight, highlights);
        }

    }

    protected void applyAICoreToIncomeAndUpkeep() {
        if ("alpha_core".equals(this.aiCoreId)) {
            this.aiCoreFPBonus = 1.2F;
        } else if ("beta_core".equals(this.aiCoreId)) {
            this.aiCoreFPBonus = 1.1F;
        } else {
            this.aiCoreFPBonus = 1.0F;
        }

    }

    protected void updateAICoreToSupplyAndDemandModifiers() {
    }

    private String getString(int ID) {
        return Global.getSettings().getString("IndEvo_industries", String.format("%s_%d", this.getClass().getSimpleName(), ID));
    }

    private String[] getHighlightsString(int ID, String... toReplace) {
        String t1 = Global.getSettings().getString("IndEvo_industries", String.format("%s_%d_highlights", this.getClass().getSimpleName(), ID));
        String t2 = null;

        for(String t3 : toReplace) {
            if (t2 == null) {
                t2 = t3;
            } else {
                t1 = t1.replace(t2, t3);
                t2 = null;
            }
        }

        if (t1.contains(" || ")) {
            return t1.split(" \\|\\| ");
        } else if (t1.contains("||")) {
            return t1.split("\\|\\|");
        } else {
            return new String[]{t1};
        }
    }

    private Color[] getHighlightsColors(int ID, Color... colors) {
        String t1 = Global.getSettings().getString("IndEvo_industries", String.format("%s_%d_highlights_colors", this.getClass().getSimpleName(), ID));
        if (!t1.contains(" || ")) {
            return colors;
        } else {
            ArrayList<Color> t2 = new ArrayList<>();

            for(String t3 : t1.split(" \\|\\| ")) {
                int t4 = Integer.parseInt(t3.trim());
                t2.add(colors[t4 - 1]);
            }

            return t2.toArray(new Color[0]);
        }
    }


}
