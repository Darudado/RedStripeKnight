package data.hullmods.VOW;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.HullModFleetEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

import static com.fs.starfarer.api.impl.campaign.ids.Stats.DMOD_EFFECT_MULT;
import static com.fs.starfarer.api.impl.campaign.ids.Stats.ELECTRONIC_WARFARE_FLAT;

public class VOW_DesignSystem extends BaseHullMod implements HullModFleetEffect {
    public static final float CRLoss = 25f;
    public static final float Repair_BOUNs = 25f;
    public static final float ELE_BOUNs = 5f;
    public static final float DMOD_EFFECT = 30f;
    public static float PROFILE_MULT = 0.75F;

    public static float MIN_CR = 0.25F;
    public static String MOD_KEY = "VOW_Design";
    public static float MIN_FIELD_MULT = 0.35F;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getCRLossPerSecondPercent().modifyPercent(id ,CRLoss);
        //stats.getHullCombatRepairRatePercentPerSecond().modifyPercent(id ,Repair_BOUNs);
        stats.getRepairRatePercentPerDay().modifyPercent(id ,Repair_BOUNs);
        stats.getSuppliesToRecover().modifyPercent(id ,-Repair_BOUNs);


        stats.getDynamic().getMod(ELECTRONIC_WARFARE_FLAT).modifyFlat(id , ELE_BOUNs);
        stats.getDynamic().getMod(DMOD_EFFECT_MULT).modifyPercent(id , -DMOD_EFFECT);

        stats.getSensorProfile().modifyMult(id, PROFILE_MULT);
    }



    public void advanceInCampaign(CampaignFleetAPI fleet) {
        String key = "$updatedVOWDesign";
        if (fleet.isPlayerFleet() && fleet.getMemoryWithoutUpdate() != null && !fleet.getMemoryWithoutUpdate().getBoolean(key) && fleet.getMemoryWithoutUpdate().getBoolean("$justToggledTransponder")) {
            this.onFleetSync(fleet);
            fleet.getMemoryWithoutUpdate().set(key, true, 0.1F);
        }

    }

    @Override
    public boolean withAdvanceInCampaign() {
        return true;
    }

    @Override
    public boolean withOnFleetSync() {
        return true;
    }

    public void onFleetSync(CampaignFleetAPI fleet) {
        float mult = getPhaseFieldMultBaseProfileAndTotal(fleet, null, 0.0F, 0.0F)[0];
        if (fleet.isTransponderOn()) {
            mult = 1.0F;
        }

        if (mult <= 0.0F) {
            fleet.getDetectedRangeMod().unmodifyMult(MOD_KEY);
        } else {
            fleet.getDetectedRangeMod().modifyMult(MOD_KEY, mult, "Oathkeeper ships carried in the fleet");
        }

    }

    public static float[] getPhaseFieldMultBaseProfileAndTotal(CampaignFleetAPI fleet, String skipId, float addProfile, float addSensor) {
        List<FleetMemberAPI> members = new ArrayList<>();
        List<FleetMemberAPI> phase = new ArrayList<>();

        for(FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (!member.getId().equals(skipId)) {
                members.add(member);
                if (!member.isMothballed() && !(member.getRepairTracker().getCR() < MIN_CR) && member.getVariant().hasHullMod("VOW_DesignSystem")) {
                    phase.add(member);
                }
            }
        }

        float[] profiles;
        if (addProfile <= 0.0F) {
            profiles = new float[members.size()];
        } else {
            profiles = new float[members.size() + 1];
        }

        float[] phaseSensors;
        if (addSensor <= 0.0F) {
            phaseSensors = new float[phase.size()];
        } else {
            phaseSensors = new float[phase.size() + 1];
        }

        int i = 0;

        for(FleetMemberAPI member : members) {
            profiles[i] = member.getStats().getSensorProfile().getModifiedValue();
            ++i;
        }

        if (addProfile > 0.0F) {
            profiles[i] = addProfile;
        }

        i = 0;

        for(FleetMemberAPI member : phase) {
            phaseSensors[i] = member.getStats().getSensorStrength().getModifiedValue();
            ++i;
        }

        if (addSensor > 0.0F) {
            phaseSensors[i] = addSensor;
        }

        int numProfileShips = Global.getSettings().getInt("maxSensorShips");
        float totalProfile = getTopKValuesSum(profiles, numProfileShips);
        float totalPhaseSensors = getTopKValuesSum(phaseSensors, numProfileShips);
        float total = Math.max(totalProfile + totalPhaseSensors, 1.0F);
        float mult = totalProfile / total;
        if (totalPhaseSensors <= 0.0F) {
            mult = 1.0F;
        }

        if (mult < MIN_FIELD_MULT) {
            mult = MIN_FIELD_MULT;
        }

        if (mult > 1.0F) {
            mult = 1.0F;
        }

        return new float[]{mult, totalProfile, totalPhaseSensors};
    }

    public static float getTopKValuesSum(float[] arr, int k) {
        k = Math.min(k, arr.length);
        float kVal = Misc.findKth(arr, arr.length - k);
        float total = 0.0F;
        int found = 0;

        for (float v : arr) {
            if (v > kVal) {
                ++found;
                total += v;
            }
        }

        if (k > found) {
            total += (float)(k - found) * kVal;
        }

        return total;
    }


    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return (int)Repair_BOUNs + "%";
        if (index == 1) return (int)Repair_BOUNs + "%";
        if (index == 2) return String.valueOf((int)ELE_BOUNs);
        if (index == 3) return (int) CRLoss + "%";
        if (index == 4) return (int)(1-PROFILE_MULT)*100 + "%";
        return null;
    }

}