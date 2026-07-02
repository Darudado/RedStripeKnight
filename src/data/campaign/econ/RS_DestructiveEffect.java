//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package data.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class RS_DestructiveEffect extends BaseMarketConditionPlugin {
    public static final String Key_Type = "$rs_starDestroyerCondition_type";
    public static final String Key_Time = "$rs_starDestroyerCondition_time";

    public RS_DestructiveEffect() {
    }

    public void apply(String id) {
        int type = this.market.getMemoryWithoutUpdate().getInt("$rs_starDestroyerCondition_type");
        float stab;
        float defe;
        switch (type) {
            case 1:
                stab = 3.0F;
                defe = 0.67F;
                break;
            case 2:
                stab = 4.0F;
                defe = 0.5F;
                break;
            case 3:
                stab = 5.0F;
                defe = 0.33F;
                break;
            case 4:
                stab = 10.0F;
                defe = 0.33F;
                break;
            case 5:
                stab = 10.0F;
                defe = 0.2F;
                break;
            case 6:
                stab = 10.0F;
                defe = 0.1F;
                break;
            default:
                return;
        }

        this.market.getStability().modifyFlat(id, -stab, "行星杀手打击");
        this.market.getStats().getDynamic().getMod("ground_defenses_mod").modifyMult(id, defe, "行星杀手打击");
    }

    public void unapply(String id) {
        this.market.getStability().unmodify(id);
        this.market.getStats().getDynamic().getMod("ground_defenses_mod").unmodify(id);
    }

    public void advance(float amount) {
        float time = this.market.getMemoryWithoutUpdate().getFloat(Key_Time);
        time -= getDay(amount);
        if (time < 0.0F) {
            removeCondition(this.market);
        } else {
            this.market.getMemoryWithoutUpdate().set(Key_Time, time);
        }
    }

    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        int type = this.market.getMemoryWithoutUpdate().getInt(Key_Time);
        String stab;
        String defe;
        switch (type) {
            case 1:
                stab = "3";
                defe = "33%";
                break;
            case 2:
                stab = "4";
                defe = "50%";
                break;
            case 3:
                stab = "5";
                defe = "67%";
                break;
            case 4:
                stab = "10";
                defe = "67%";
                break;
            case 5:
                stab = "12";
                defe = "80%";
                break;
            case 6:
                stab = "15";
                defe = "90%";
                break;
            default:
                return;
        }

        int time = (int)this.market.getMemoryWithoutUpdate().getFloat(Key_Time);
        tooltip.addPara("稳定性降低{%s，地面防御降低{%s，持续{%s}天.", 10.0F, Misc.getHighlightColor(), new String[]{stab, defe, "" + time});
    }

    public static void tryAddCondition(MarketAPI market, boolean full) {
        tryAddCondition(Global.getSector().getPlayerFleet(), market, full);
    }

    public static void tryAddCondition(CampaignFleetAPI fleet, MarketAPI market, boolean full) {
        if (market != null && !market.isPlanetConditionMarketOnly() && market.getFactionId() != null && !"neutral".equals(market.getFactionId()) && fleet != null) {
            int level = getLevel(fleet);
            if (full && level > 0) {
                level += 3;
            }

            if (level > 0 && level <= 6) {
                addCondition(market, level, full);
            }

        }
    }

    public static void addCondition(MarketAPI market, int type, boolean full) {
        if (!market.hasCondition("rs_starDestroyerCondition")) {
            market.addCondition("rs_starDestroyerCondition");
        }

        int oldType = market.getMemoryWithoutUpdate().getInt("$rs_starDestroyerCondition_type");
        if (type > oldType) {
            market.getMemoryWithoutUpdate().set("$rs_starDestroyerCondition_type", type);
        }

        float oldTime = market.getMemoryWithoutUpdate().getFloat(Key_Time);
        float time = 30.0F;
        if (type >= 4) {
            time = 120.0F;
        }

        if (time > oldTime) {
            market.getMemoryWithoutUpdate().set(Key_Time, time);
        }

    }

    public static void addCondition(MarketAPI market, int type, float day) {
        if (market != null) {
            if (!market.hasCondition("rs_starDestroyerCondition")) {
                market.addCondition("rs_starDestroyerCondition");
                market.getCondition("rs_starDestroyerCondition").setSurveyed(true);
            }

            int oldType = market.getMemoryWithoutUpdate().getInt("$rs_starDestroyerCondition_type");
            if (type > oldType) {
                market.getMemoryWithoutUpdate().set("$rs_starDestroyerCondition_type", type);
            }

            float oldTime = market.getMemoryWithoutUpdate().getFloat(Key_Time);
            if (day > oldTime) {
                market.getMemoryWithoutUpdate().set(Key_Time, day);
            }

        }
    }

    public static void removeCondition(MarketAPI market) {
        market.removeCondition("rs_starDestroyerCondition");
        market.getMemoryWithoutUpdate().unset(Key_Time);
        market.getMemoryWithoutUpdate().unset("$rs_starDestroyerCondition_type");
    }

    public static int getLevel(CampaignFleetAPI fleet) {
        if (fleet == null) {
            return 0;
        } else {
            int level = 0;

            for(FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
                if (!member.isMothballed()) {
                    int clv = Math.round(member.getStats().getDynamic().getValue("rs_sf_stardestroyer_level", 0.0F));
                    if (clv > level) {
                        level = clv;
                    }
                }
            }

            return level;
        }
    }

    public static float getDay(float second) {
        return Global.getSector().getClock().convertToDays(second);
    }
}
