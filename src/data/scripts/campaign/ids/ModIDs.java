package data.scripts.campaign.ids;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc;

import static com.fs.starfarer.api.campaign.AICoreOfficerPlugin.AUTOMATED_POINTS_MULT;
import static com.fs.starfarer.api.impl.campaign.AICoreOfficerPluginImpl.*;
import static com.fs.starfarer.api.impl.campaign.ids.Commodities.*;

public class ModIDs {
    public static final String CR = "cinis_of_crusaders";
    public static String CR_SHIP = "cr_bp";
    public static String CR_SHIP_NOSPAW = "cr_bp_sp";
    public static String CR_AUTO = "SPAutomated";



    public static PersonAPI createAICorePerson(String commodityId) {
        PersonAPI person;
        switch (commodityId) {
            case "gamma_core":
                person = genGAMMA();
                break;
            case "beta_core":
                person = genBETA();
                break;
            default:
                person = genALPHA();
                break;
        }
        return person;
    }

    public static PersonAPI genGAMMA() {
        PersonAPI person = Misc.getAICoreOfficerPlugin(GAMMA_CORE).createPerson(GAMMA_CORE, CR, Misc.random);
        crusadersAICore(person);
        person.setFaction(CR);
        if (person.getFaction() == null) {
            Global.getLogger(ModIDs.class).warn("Created person with null faction: " + person.getNameString());
            person.setFaction(CR); // 强制设置
        }
        person.getMemoryWithoutUpdate().set(AUTOMATED_POINTS_MULT, Math.max(0, GAMMA_MULT - 0.25f));
        return person;
    }
    public static PersonAPI genBETA() {
        PersonAPI person = Misc.getAICoreOfficerPlugin(BETA_CORE).createPerson(BETA_CORE, CR, Misc.random);
        crusadersAICore(person);
        person.setFaction(CR);
        if (person.getFaction() == null) {
            Global.getLogger(ModIDs.class).warn("Created person with null faction: " + person.getNameString());
            person.setFaction(CR); // 强制设置
        }
        person.getMemoryWithoutUpdate().set(AUTOMATED_POINTS_MULT, Math.max(0, BETA_MULT - 0.25f));
        return person;
    }
    public static PersonAPI genALPHA() {
        PersonAPI person = Misc.getAICoreOfficerPlugin(ALPHA_CORE).createPerson(ALPHA_CORE, CR, Misc.random);
        crusadersAICore(person);
        person.setFaction(CR);
        if (person.getFaction() == null) {
            Global.getLogger(ModIDs.class).warn("Created person with null faction: " + person.getNameString());
            person.setFaction(CR); // 强制设置
        }
        person.getMemoryWithoutUpdate().set(AUTOMATED_POINTS_MULT, Math.max(0, ALPHA_MULT - 0.25f));
        return person;
    }
    public static boolean isCRUSADERS(FleetMemberAPI member) {
        return member.getHullSpec().hasTag(CR_SHIP) || member.getHullSpec().hasTag(CR_SHIP_NOSPAW);
    }


    public static void crusadersAICore(PersonAPI person, String forcedPrefix, String forcedInfex, String forcedSuffix) {
        PersonAPI temp;
        if (Global.getSector() != null) {
            temp = Global.getSector().getFaction(CR).createRandomPerson();
        } else {
            temp = Global.getSettings().createBaseFaction(CR).createRandomPerson();
        }
        if (temp == null) return;
        person.setPersonality(temp.getFaction().pickPersonality());

        person.setPortraitSprite(temp.getPortraitSprite());


        // override AI core ID
        if (person.getAICoreId() != null) {
            switch (person.getAICoreId()) {
                case "gamma_core":
                    person.setAICoreId(GAMMA_CORE);
                    person.setRankId(Ranks.SPACE_LIEUTENANT);
                    break;
                case "beta_core":
                    person.setAICoreId(BETA_CORE);
                    person.setRankId(Ranks.SPACE_CAPTAIN);
                    break;
                default:
                    person.setAICoreId(ALPHA_CORE);
                    person.setRankId(Ranks.SPACE_COMMANDER);
                    break;
            }
        }
        giveCrusadersName(person, forcedPrefix, forcedInfex, forcedSuffix);
    }
    public static void crusadersAICore(PersonAPI person) {
        crusadersAICore(person, null, null, null);
    }

    public static void giveCrusadersName(PersonAPI person, String forcedPrefix, String forcedInfex, String forcedSuffix) {
        FactionAPI faction;
        if (Global.getSector() != null) {
            faction = Global.getSector().getFaction(CR);
        } else {
            faction = Global.getSettings().createBaseFaction(CR);
        }

        if (faction == null) {
            // 处理派系不存在的情况
            return;
        }

        PersonAPI temp = faction.createRandomPerson();
        if (temp == null || temp.getFaction() == null) {
            return;
        }

// 现在安全使用
        person.setPersonality(temp.getFaction().pickPersonality());
        String prefix = forcedPrefix;
        String infex = forcedInfex;
        String suffix = forcedSuffix;
        if (prefix == null) {
            prefix = temp.getName().getFirst();
        }
        if (infex == null) {
            switch (person.getRankId()) {
                case "spaceSailor":
                    infex = "Trace";
                    break;
                case "spaceLieutenant":
                    infex = "Sliver";
                    break;
                case "spaceCaptain":
                    infex = "Echo";
                    break;
                case "spaceCommander":
                    infex = "Annex";
                    break;
                case "spaceAdmiral":
                    infex = "Affix";
                    break;
                default:
                    infex = "Echo";
                    break;
            }
        }
        if (suffix == null) {
            suffix = temp.getName().getLast();
        }
        //person.getMemoryWithoutUpdate().set("$sotf_prefix", prefix);
        //person.getMemoryWithoutUpdate().set("$sotf_suffix", suffix);
        //person.getName().setFirst(prefix + "-" + infex + "-" + suffix); // e.g Index-Annex-Optimum
        //person.getName().setLast("");
    }
}