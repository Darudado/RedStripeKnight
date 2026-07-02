//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package data.scripts.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCells;
import com.fs.starfarer.api.loading.HullModSpecAPI;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class CR_Defense extends BaseMarketConditionPlugin {
    public CR_Defense() {
    }

    public void apply(String id) {
        this.market.getStability().modifyFlat(id, 3.0F, "十字军防御系统");

        for(Industry industry : this.market.getIndustries()) {
            removeDemand(industry, "crew", id, "AI系统");
            removeDemand(industry, "marines", id, "AI系统");
            removeDemand(industry, "organs", id, "AI系统");
            removeDemand(industry, "food", id, "AI系统");
            removeDemand(industry, "drugs", id, "AI系统");
            removeSupply(industry, "crew", id, "AI系统");
            removeSupply(industry, "marines", id, "AI系统");
            removeSupply(industry, "organs", id, "AI系统");
            removeSupply(industry, "domestic_goods", id, "AI系统");
            removeSupply(industry, "luxury_goods", id, "AI系统");
        }

    }

    public void unapply(String id) {
        this.market.getStability().unmodify(id);

        for(Industry industry : this.market.getIndustries()) {
            unremoveDemand(industry, "crew", id);
            unremoveDemand(industry, "marines", id);
            unremoveDemand(industry, "organs", id);
            unremoveDemand(industry, "food", id);
            unremoveDemand(industry, "drugs", id);
            unremoveSupply(industry, "crew", id);
            unremoveSupply(industry, "marines", id);
            unremoveSupply(industry, "organs", id);
            unremoveSupply(industry, "domestic_goods", id);
            unremoveSupply(industry, "luxury_goods", id);
        }

    }

    public static void removeDemand(Industry industry, String cid, String id, String desc) {
        industry.getDemand(cid).getQuantity().modifyMult(id, 0.0F, desc);
    }

    public static void unremoveDemand(Industry industry, String cid, String id) {
        industry.getDemand(cid).getQuantity().unmodify(id);
    }

    public static void removeSupply(Industry industry, String cid, String id, String desc) {
        MutableStat stat = industry.getSupply(cid).getQuantity();
        stat.modifyMult(id, 0.5F, desc);
        if (stat.getModifiedInt() <= 0) {
            unremoveSupply(industry, cid, id);
        }

    }

    public static void unremoveSupply(Industry industry, String cid, String id) {
        industry.getSupply(cid).getQuantity().unmodify(id);
    }

    public void advance(float amount) {
        boolean paused = Global.getSector() != null && Global.getSector().isPaused();

            if (!paused) {


                PersonAPI admin = this.market.getAdmin();
                if (admin != null && !admin.isDefault() && !admin.isAICore() && !admin.isPlayer() && admin.getMemoryWithoutUpdate().getBoolean("$suspectedAI")) {
                    admin.getMemoryWithoutUpdate().set("$suspectedAI", false);
                    admin.getStats().setSkillLevel("hypercognition", 1.0F);
                }

                if (this.market.hasCondition("pather_cells")) {
                    MarketConditionPlugin admin1 = this.market.getCondition("pather_cells").getPlugin();
                    if (admin1 instanceof LuddicPathCells plugin) {
                        if (plugin.getIntel() != null) {
                            plugin.getIntel().endImmediately();
                        }
                    }

                    this.market.removeCondition("pather_cells");
                }

                if (this.market.hasCondition("rogue_ai_core")) {
                    this.market.removeCondition("rogue_ai_core");
                }

                if (this.market.hasCondition("dissident")) {
                    this.market.removeCondition("dissident");
                }

                if (this.market.hasCondition("decivilized")) {
                    this.market.removeCondition("decivilized");
                }

                if (this.market.hasCondition("decivilized_subpop")) {
                    this.market.removeCondition("decivilized_subpop");
                }

            }


            for(SubmarketAPI submarket : this.market.getSubmarketsCopy()) {
                if (submarket.getCargoNullOk() != null && !submarket.getSpecId().contains("storage")) {
                    CargoAPI cargo = submarket.getCargoNullOk();
                    boolean removed = false;

                    for(CargoStackAPI stack : cargo.getStacksCopy()) {
                        if (stack.isSpecialStack() && stack.getSpecialDataIfSpecial().getId().equals("modspec")) {
                            HullModSpecAPI spec = stack.getHullModSpecIfHullMod();
                            if (spec.hasTag("no_sell")) {
                                cargo.removeStack(stack);
                                removed = true;
                            }
                        }
                    }

                    if (removed) {
                        cargo.sort();
                    }
                }
            }

            Set<PersonAPI> personsToCheck = new HashSet<>(this.market.getPeopleCopy());
            if (this.market.getCommDirectory() != null) {
                for(CommDirectoryEntryAPI cde : this.market.getCommDirectory().getEntriesCopy()) {
                    if (cde.getEntryData() instanceof PersonAPI) {
                        personsToCheck.add((PersonAPI)cde.getEntryData());
                    }
                }
            }

            for(PersonAPI person : personsToCheck) {
                if (person.getMemoryWithoutUpdate().getBoolean("$ome_hireable") || person.getMemoryWithoutUpdate().getBoolean("$sc_hireable")) {
                    String psid = person.getPortraitSprite();
                    if (this.market.getFaction() != null) {
                        ArrayList pslist = new ArrayList(this.market.getFaction().getPortraits(Gender.MALE).getItems());
                        if (pslist.contains(psid)) {
                            String newPs = (String)Global.getSector().getFaction("independent").getPortraits(person.getGender()).pick();
                            if (newPs != null && !newPs.isEmpty()) {
                                person.setPortraitSprite(newPs);
                            }
                        }
                    }
                }
            }

            PersonAPI admin = this.market.getAdmin();
            if (admin != null && !admin.isDefault() && !admin.isAICore() && !admin.isPlayer() && !admin.getMemoryWithoutUpdate().getBoolean("$suspectedAI")) {
                admin.getMemoryWithoutUpdate().set("$suspectedAI", true);
                if (this.market.getFactionId().equals("cinis_of_crusaders")) {

                        admin.getStats().setSkillLevel("hypercognition", 1.0F);

                }

            }


    }

    public boolean runWhilePaused() {
        return true;
    }
}
