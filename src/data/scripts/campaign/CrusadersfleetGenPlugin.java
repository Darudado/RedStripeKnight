package data.scripts.campaign;

import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantOfficerGeneratorPlugin;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.*;

public class CrusadersfleetGenPlugin extends RemnantOfficerGeneratorPlugin {
    protected boolean putCoresOnCivShips = false;
    protected boolean forceIntegrateCores = false;
    protected boolean forceNoCommander = false;
    protected boolean derelictMode = false;
    protected float coreMult = 1.0F;

    public static final List<HubMissionWithTriggers.OfficerQuality> NO_HIGH;

    public CrusadersfleetGenPlugin() {
    }

    public CrusadersfleetGenPlugin(boolean derelictMode, float coreMult) {
        this.derelictMode = derelictMode;
        this.coreMult = coreMult;
    }



    public int getHandlingPriority(Object params) {
        if (!(params instanceof GenerateFleetOfficersPickData data)) return -1;
        if (data.params != null && !data.params.withOfficers) return -1;
        if (data.params != null && data.params.aiCores != null) return GenericPluginManagerAPI.CORE_SUBSET;
        if (data.fleet == null || !data.fleet.getFaction().getId().equals("cinis_of_crusaders")) return -1;

        return GenericPluginManagerAPI.CORE_SUBSET;

    }

    public boolean isForceNoCommander() {
        return this.forceNoCommander;
    }

    public void setForceNoCommander(boolean forceNoCommander) {
        this.forceNoCommander = forceNoCommander;
    }

    public boolean isPutCoresOnCivShips() {
        return this.putCoresOnCivShips;
    }

    public void setPutCoresOnCivShips(boolean putCoresOnCivShips) {
        this.putCoresOnCivShips = putCoresOnCivShips;
    }

    public boolean isForceIntegrateCores() {
        return this.forceIntegrateCores;
    }

    public void setForceIntegrateCores(boolean forceIntegrateCores) {
        this.forceIntegrateCores = forceIntegrateCores;
    }

    @Override
    public void addCommanderAndOfficers(CampaignFleetAPI fleet, FleetParamsV3 params, Random random) {
        if (random == null) {
            random = Misc.random;
        }

        List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
        if (!members.isEmpty()) {
            Map<String, AICoreOfficerPlugin> plugins = new HashMap<>();
            plugins.put("alpha_core", Misc.getAICoreOfficerPlugin("alpha_core"));
            plugins.put("beta_core", Misc.getAICoreOfficerPlugin("beta_core"));
            plugins.put("gamma_core", Misc.getAICoreOfficerPlugin("gamma_core"));
            float fleetFP = 0.0F;
            FleetMemberAPI flagShip = null;
            int flagFP = -1;

            for (FleetMemberAPI member : members) {
                fleetFP += (float) member.getFleetPointCost();
                if (!member.isFighterWing() && member.getFleetPointCost() > flagFP) {
                    flagFP = member.getFleetPointCost();
                    flagShip = member;
                }
            }

            int numCommanderSkills = 0;
            if (fleetFP > 75.0F) {
                ++numCommanderSkills;
            }

            if (fleetFP > 125.0F) {
                ++numCommanderSkills;
            }

            if (fleetFP > 175.0F) {
                ++numCommanderSkills;
            }

            if (params != null && params.noCommanderSkills != null && params.noCommanderSkills) {
                numCommanderSkills = 0;
            }

            for (FleetMemberAPI member : members) {
                if (!member.isFighterWing()) {
                    if (member == flagShip) {
                        AICoreOfficerPlugin plugin = plugins.get("alpha_core");
                        if (plugin != null) {
                            PersonAPI person = plugin.createPerson("alpha_core", fleet.getFaction().getId(), random);
                            person.setName(fleet.getFaction().createRandomPerson().getName());
                            member.setCaptain(person);
                        }

                        if (params != null && params.officerNumberMult <= 0.0F) {
                            break;
                        }
                    } else if (params == null || !(params.officerNumberMult <= 0.0F)) {
                        boolean civ = member.isCivilian();
                        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
                        if (!civ) {
                            float fpr = (float) member.getFleetPointCost() / (float) flagFP;
                            picker.add("alpha_core", fpr);
                            picker.add("beta_core", fpr * 1.2f);
                            picker.add("gamma_core", fpr * 1.5f);
                        }

                        String pick = picker.pick();
                        AICoreOfficerPlugin plugin = plugins.get(pick);
                        if (plugin != null) {
                            PersonAPI person = plugin.createPerson(pick, fleet.getFaction().getId(), random);
                            person.setName(fleet.getFaction().createRandomPerson().getName());
                            member.setCaptain(person);
                        }

                        if (civ && member.getVariant() != null) {
                            member.getVariant().addTag("no_ai_core_drop");
                        }
                    }
                }
            }

            if (flagShip != null && flagShip.getCaptain() != null) {
                PersonAPI commander = flagShip.getCaptain();
                commander.setRankId(Ranks.SPACE_COMMANDER);
                commander.setPostId(Ranks.POST_FLEET_COMMANDER);
                fleet.setCommander(commander);
                fleet.getFleetData().setFlagship(flagShip);
                commander.getStats().setSkipRefresh(true);

                commander.getStats().setSkillLevel("crew_training", 1);
                commander.getStats().setSkillLevel("officer_training", 1);
                commander.getStats().setSkillLevel("support_doctrine", 1);
                commander.getStats().setSkillLevel("electronic_warfare", 1);
                commander.getStats().setSkillLevel("flux_regulation", 1);
                commander.getStats().setSkillLevel("coordinated_maneuvers", 1);

                RemnantOfficerGeneratorPlugin.addCommanderSkills(commander, fleet, params, numCommanderSkills, random);
            }

        }
    }

    static {
        NO_HIGH = Arrays.asList(HubMissionWithTriggers.OfficerQuality.AI_GAMMA, HubMissionWithTriggers.OfficerQuality.AI_BETA, HubMissionWithTriggers.OfficerQuality.AI_BETA_OR_GAMMA);
    }

    public static void integrateAndAdaptCoreForAIFleet(FleetMemberAPI member) {
        PersonAPI person = member.getCaptain();
        if (person.isAICore()) {
            person.getStats().setLevel(person.getStats().getLevel() + 1);
            person.getStats().setSkipRefresh(true);
            if (member.getVariant() != null && member.getVariant().getWeaponGroups() != null) {
                float weight = 0.0F;
                float pdWeight = 0.0F;
                float missileWeight = 0.0F;

                for (String slotId : member.getVariant().getFittedWeaponSlots()) {
                    WeaponSpecAPI spec = member.getVariant().getWeaponSpec(slotId);
                    if (spec != null) {
                        float w = 1.0F;
                        if (spec.getSize() == WeaponAPI.WeaponSize.MEDIUM) {
                            w = 2.0F;
                        }

                        if (spec.getSize() == WeaponAPI.WeaponSize.LARGE) {
                            w = 4.0F;
                        }

                        weight += w;
                        if (spec.getAIHints().contains(WeaponAPI.AIHints.PD)) {
                            pdWeight += w;
                        }

                        if (spec.getType() == WeaponAPI.WeaponType.MISSILE) {
                            missileWeight += w;
                        }
                    }
                }

                float decks = (float) member.getNumFlightDecks();
                if (decks > 0.0F) {
                    weight += decks * 4.0F;
                    pdWeight += decks * 4.0F;
                }

                boolean hasUsefulPD = pdWeight > weight * 0.25F;
                boolean hasEnoughMissiles = missileWeight > weight * 0.2F;
                if (hasUsefulPD && !hasEnoughMissiles) {
                    person.getStats().setSkillLevel("point_defense", 2.0F);
                    person.getStats().setSkipRefresh(false);
                    return;
                }
            }

            if (member.getHullSpec() != null && member.getHullSpec().hasTag("derelict") && person.getStats().getSkillLevel("ballistic_mastery") <= 0.0F) {
                person.getStats().setSkillLevel("ballistic_mastery", 2.0F);
            } else if (person.getStats().getSkillLevel("energy_weapon_mastery") <= 0.0F) {
                person.getStats().setSkillLevel("energy_weapon_mastery", 2.0F);
            } else {
                person.getStats().setSkillLevel("missile_specialization", 2.0F);
            }

            if ((member.isCapital() || member.isStation()) && person.getStats().getSkillLevel("polarized_armor") <= 0.0F) {
                person.getStats().setSkillLevel("combat_endurance", 0.0F);
                person.getStats().setSkillLevel("polarized_armor", 2.0F);
            }

            person.getStats().setSkipRefresh(false);
        }
    }

    public static OfficerManagerEvent.SkillPickPreference getSkillPrefForShip(FleetMemberAPI member) {
        return FleetFactoryV3.getSkillPrefForShip(member);
    }

    public static void addCommanderSkills(PersonAPI commander, CampaignFleetAPI fleet, FleetParamsV3 params, int numSkills, Random random) {
        if (random == null) {
            random = new Random();
        }

        if (numSkills > 0) {
            MutableCharacterStatsAPI stats = commander.getStats();
            FactionDoctrineAPI doctrine = fleet.getFaction().getDoctrine();
            if (params != null && params.doctrineOverride != null) {
                doctrine = params.doctrineOverride;
            }

            List<String> skills = new ArrayList<>(doctrine.getCommanderSkills());
            if (!skills.isEmpty()) {
                if (random.nextFloat() < doctrine.getCommanderSkillsShuffleProbability()) {
                    Collections.shuffle(skills, random);
                }

                stats.setSkipRefresh(true);

                int picks = 0;

                for (String skillId : skills) {

                    stats.setSkillLevel(skillId, 1.0F);
                    ++picks;
                    if (picks >= numSkills) {
                        break;
                    }
                }

                stats.setSkipRefresh(false);
                stats.refreshCharacterStatsEffects();
            }
        }
    }
}
