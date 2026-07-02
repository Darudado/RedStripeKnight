package data.campaign.econ;


import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.ArrayList;
import java.util.List;

import static com.fs.starfarer.api.impl.campaign.ids.HullMods.STABILIZEDSHIELDEMITTER;

public class Guardfleetmanager{
    public enum Type{
        CARRIER,COMBAT,HEAVY
    }

    public static WeightedRandomPicker<Type> typePicker = new WeightedRandomPicker<>();
    static {
        typePicker.add(Type.COMBAT);
        typePicker.add(Type.HEAVY);
        typePicker.add(Type.CARRIER);
    }

    public static CampaignFleetAPI createGuardFleet1(float combat, float tanker, float freighter , MarketAPI market) {
        Type type = typePicker.pick();
        FactionAPI faction = Global.getSector().getFaction("red_stripe");
        CampaignFleetAPI fleet = createFlgeFleet(String.valueOf(faction), FleetTypes.PATROL_LARGE, market);
        PersonAPI admiral = createAdmiral();
        fleet.setCommander(admiral);
        if (type == Type.HEAVY) {
            fleet.getMemoryWithoutUpdate().set("$RS_FlagFleet", true);
        }
        switch(type) {
            case HEAVY:
            {
                FleetMemberAPI f = fleet.getFleetData().addFleetMember("rs_byzantine_Standerd");
                ShipVariantAPI v = f.getVariant().clone();
                v.setSource(VariantSource.REFIT);
                v.addPermaMod(HullMods.AUTOREPAIR, true);
                v.addPermaMod(HullMods.TURRETGYROS, true);
                v.addPermaMod(HullMods.AUTOREPAIR, true);
                f.setVariant(v, false, false);
                f.updateStats();
                f.setCaptain(admiral);
                f.setFlagship(true);
                float combatDiva = combat * 0.1f;
                while (combatDiva > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_Energy");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    vfy.addPermaMod(HullMods.AUTOREPAIR, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDiva -= fy.getFleetPointCost();
                }
                float combatDivb = combat * 0.1f;
                while (combatDivb > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_Ballistc");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    vfy.addPermaMod(HullMods.MAGAZINES, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDivb -= fy.getFleetPointCost();
                }
                float combatDivc = combat * 0.1f;
                while (combatDivc > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_tiumphus_Standerd");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    vfy.addPermaMod(HullMods.AUTOREPAIR, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDivc -= fy.getFleetPointCost();
                }
                float combatDivd = combat * 0.1f;
                while (combatDivd > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_tiumphus_Standerd");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    vfy.addPermaMod(HullMods.MAGAZINES, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDivd -= fy.getFleetPointCost();
                }
                float combatDivsub = combat * 0.1f;
                while (combatDivsub > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_bethlehem_Elite");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    vfy.addPermaMod(HullMods.EXPANDED_DECK_CREW, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDivsub -= fy.getFleetPointCost();
                }
                float combatDiv1 = combat * 0.3f;
                while (combatDiv1 > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_sophia_variant");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.AUTOREPAIR, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDiv1 -= fy.getFleetPointCost();
                }
                float combatDiv1a = combat * 0.3f;
                while (combatDiv1a > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_kharybdis_Staderd");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.AUTOREPAIR, true);
                    vfy.addPermaMod(HullMods.MAGAZINES, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDiv1a -= fy.getFleetPointCost();
                }
                float combatDiv2 = combat * 0.3f;
                while (combatDiv2 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_typhaon_variant");
                    sf.setCaptain(createLowerCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv2 -= sf.getFleetPointCost();
                }
                float combatDiv3 = combat * 0.3f;
                while (combatDiv3 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv3 -= sf.getFleetPointCost();
                }
                float combatDiv4 = combat * 0.2f;
                while (combatDiv4 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Strike");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv4 -= sf.getFleetPointCost();
                }
                float combatDiv5 = combat * 0.2f;
                while (combatDiv5 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_SDA_MA_variant");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.ECM, true);
                    vsf.addPermaMod("UniversalRangeFinder", true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv5 -= sf.getFleetPointCost();
                }
                float combatDiv6 = combat * 0.3f;
                while (combatDiv6 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_TMA_MA_Strike");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv6 -= sf.getFleetPointCost();
                }
                float combatDiv7 = combat * 0.1f;
                while (combatDiv7 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_assault_Standerd");
                    sf.setCaptain(createLowerCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv7 -= sf.getFleetPointCost();
                }
                float combatDiv8 = combat * 0.1f;
                while (combatDiv8 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_firesupport_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv8 -= sf.getFleetPointCost();
                }
                float combatDiv9 = combat * 0.1f;
                while (combatDiv9 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_defense_Standerd");
                    sf.setCaptain(createLowerCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv9 -= sf.getFleetPointCost();
                }
        }
            break;
            case COMBAT: {
                FleetMemberAPI f = fleet.getFleetData().addFleetMember("rs_byzantine_Standerd");
                ShipVariantAPI v = f.getVariant().clone();
                v.setSource(VariantSource.REFIT);
                v.addPermaMod(HullMods.AUTOREPAIR, true);
                v.addPermaMod(HullMods.TURRETGYROS, true);
                v.addPermaMod(HullMods.AUTOREPAIR, true);
                f.setVariant(v, false, false);
                f.updateStats();
                f.setCaptain(admiral);
                f.setFlagship(true);
                float combatDiva = combat * 0.1f;
                while (combatDiva > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_Energy");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    vfy.addPermaMod(HullMods.AUTOREPAIR, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDiva -= fy.getFleetPointCost();
                }
                float combatDivb = combat * 0.1f;
                while (combatDivb > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_Ballistc");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    vfy.addPermaMod(HullMods.MAGAZINES, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDivb -= fy.getFleetPointCost();
                }
                float combatDivc = combat * 0.1f;
                while (combatDivc > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_missile_Energy");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    vfy.addPermaMod(HullMods.AUTOREPAIR, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDivc -= fy.getFleetPointCost();
                }
                float combatDivd = combat * 0.1f;
                while (combatDivd > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_missile_Ballistc");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    vfy.addPermaMod(HullMods.MAGAZINES, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDivd -= fy.getFleetPointCost();
                }
                float combatDivsub = combat * 0.1f;
                while (combatDivsub > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_vincere_variant");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    vfy.addPermaMod(HullMods.EXPANDED_DECK_CREW, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDivsub -= fy.getFleetPointCost();
                }
                float combatDiv2 = combat * 0.3f;
                while (combatDiv2 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_typhaon_variant");
                    sf.setCaptain(createLowerCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv2 -= sf.getFleetPointCost();
                }
                float combatDiv3 = combat * 0.4f;
                while (combatDiv3 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv3 -= sf.getFleetPointCost();
                }
                float combatDiv4 = combat * 0.4f;
                while (combatDiv4 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Strike");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv4 -= sf.getFleetPointCost();
                }
                float combatDiv5 = combat * 0.2f;
                while (combatDiv5 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_SDA_MA_variant");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.ECM, true);
                    vsf.addPermaMod("UniversalRangeFinder", true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv5 -= sf.getFleetPointCost();
                }
                float combatDiv6 = combat * 0.5f;
                while (combatDiv6 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_TMA_MA_Strike");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv6 -= sf.getFleetPointCost();
                }
                float combatDiv7 = combat * 0.1f;
                while (combatDiv7 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_assault_Standerd");
                    sf.setCaptain(createLowerCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv7 -= sf.getFleetPointCost();
                }
                float combatDiv8 = combat * 0.1f;
                while (combatDiv8 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_firesupport_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv8 -= sf.getFleetPointCost();
                }
                float combatDiv9 = combat * 0.1f;
                while (combatDiv9 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_defense_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv9 -= sf.getFleetPointCost();
                }
            }
            break;
            case CARRIER:
            {
                FleetMemberAPI f = fleet.getFleetData().addFleetMember("rs_byzantine_Standerd");
                ShipVariantAPI v = f.getVariant().clone();
                v.setSource(VariantSource.REFIT);
                v.addPermaMod(HullMods.AUTOREPAIR, true);
                v.addPermaMod(HullMods.TURRETGYROS, true);
                v.addPermaMod(HullMods.AUTOREPAIR, true);
                f.setVariant(v, false, false);
                f.updateStats();
                f.setCaptain(admiral);
                f.setFlagship(true);
                float combatDiva = combat * 0.1f;
                while (combatDiva > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_Energy");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    vfy.addPermaMod(HullMods.AUTOREPAIR, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDiva -= fy.getFleetPointCost();
                }
                float combatDivb = combat * 0.1f;
                while (combatDivb > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_Ballistc");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    vfy.addPermaMod(HullMods.MAGAZINES, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDivb -= fy.getFleetPointCost();
                }
                float combatDivc = combat * 0.1f;
                while (combatDivc > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_bethlehem_Elite");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    vfy.addPermaMod(HullMods.AUTOREPAIR, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDivc -= fy.getFleetPointCost();
                }
                float combatDivd = combat * 0.1f;
                while (combatDivd > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_missile_Ballistc");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    vfy.addPermaMod(HullMods.MAGAZINES, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDivd -= fy.getFleetPointCost();
                }
                //float combatDivsub = combat * 0.1f;
                //while (combatDivsub > 0) {
                    //FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_bethlehem_Elite");
                    //fy.setCaptain(createHigherCaptain());
                    //ShipVariantAPI vfy = fy.getVariant().clone();
                    //vfy.setSource(VariantSource.REFIT);
                    //vfy.addPermaMod(HullMods.ARMOREDWEAPONS, true);
                    //vfy.addPermaMod(HullMods.EXPANDED_DECK_CREW, true);
                    //fy.setVariant(vfy, false, false);
                    //fy.updateStats();
                    //combatDivsub -= fy.getFleetPointCost();
                //}
                float combatDiv1 = combat * 0.3f;
                while (combatDiv1 > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_sophia_variant");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.AUTOREPAIR, true);
                    fy.setVariant(vfy, false, false);
                    fy.updateStats();
                    combatDiv1 -= fy.getFleetPointCost();
                }
                float combatDiv2 = combat * 0.3f;
                while (combatDiv2 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_typhaon_variant");
                    sf.setCaptain(createLowerCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv2 -= sf.getFleetPointCost();
                }
                float combatDiv3 = combat * 0.4f;
                while (combatDiv3 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv3 -= sf.getFleetPointCost();
                }
                float combatDiv4 = combat * 0.4f;
                while (combatDiv4 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Strike");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv4 -= sf.getFleetPointCost();
                }
                float combatDiv5 = combat * 0.2f;
                while (combatDiv5 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_SDA_MA_variant");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.ECM, true);
                    vsf.addPermaMod("UniversalRangeFinder", true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv5 -= sf.getFleetPointCost();
                }
                float combatDiv6 = combat * 0.5f;
                while (combatDiv6 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_TMA_MA_Strike");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv6 -= sf.getFleetPointCost();
                }
                float combatDiv7 = combat * 0.1f;
                while (combatDiv7 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_assault_Standerd");
                    sf.setCaptain(createLowerCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv7 -= sf.getFleetPointCost();
                }
                float combatDiv8 = combat * 0.1f;
                while (combatDiv8 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_firesupport_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv8 -= sf.getFleetPointCost();
                }
                float combatDiv9 = combat * 0.1f;
                while (combatDiv9 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_defense_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv9 -= sf.getFleetPointCost();
                }
            }
            break;
        }
        FleetMemberAPI elegy = fleet.getFleetData().addFleetMember("rs_huo_standard");
        elegy.setCaptain(createHigherCaptain());
        elegy.updateStats();

        elegy = fleet.getFleetData().addFleetMember("rs_huo_standard");
        elegy.setCaptain(createHigherCaptain());
        elegy.updateStats();


        float t = tanker;
        while (t>0){
            FleetMemberAPI f = fleet.getFleetData().addFleetMember("rs_you_standard");
            t-=f.getFleetPointCost();
        }

        float f = freighter;
        while (f>0){
            FleetMemberAPI m = fleet.getFleetData().addFleetMember("rs_you_standard");
            f-=m.getFleetPointCost();
        }

        fleet.getMemoryWithoutUpdate().set("$RS_FlagFleet",true);

        return fleet;
    }



    public static CampaignFleetAPI createGuardFleet3(float combat, float tanker, float freighter, MarketAPI market){
        Type type = typePicker.pick();
        FactionAPI faction = Global.getSector().getFaction("red_stripe");
        CampaignFleetAPI fleet = createGaurdFleet(String.valueOf(faction), FleetTypes.PATROL_LARGE,market);
        PersonAPI admiral = createAdmiral();
        fleet.setCommander(admiral);
        if(type == Type.COMBAT){
            fleet.getMemoryWithoutUpdate().set("$RS_FullAssault",true);
        }
        switch (type) {
            case CARRIER:
            {
                FleetMemberAPI f = fleet.getFleetData().addFleetMember("rs_lordanes_Standerd");
                ShipVariantAPI v = f.getVariant().clone();
                v.setSource(VariantSource.REFIT);
                v.addPermaMod("CR_ImprovedWeaponControlling",true);
                v.addPermaMod("CR_EngineRegularBoost",true);
                f.setVariant(v,false,false);
                f.updateStats();
                f.setCaptain(admiral);
                f.setFlagship(true);
                float combatDiv1 = combat * 0.3f;
                while (combatDiv1 > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_Tr_5_fiver_Standerd");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.AUTOREPAIR,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDiv1 -= fy.getFleetPointCost();
                }
                float combatDiv2 = combat * 0.4f;
                while (combatDiv2 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_4_Dandelion_Standerd");
                    sf.setCaptain(createLowerCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv2 -= sf.getFleetPointCost();
                }
                float combatDiv2a = combat * 0.2f;
                while (combatDiv2a > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_defense_Standerd");
                    sf.setCaptain(createLowerCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv2a -= sf.getFleetPointCost();
                }
                float combatDiv3 = combat * 0.4f;
                while (combatDiv3 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv3 -= sf.getFleetPointCost();
                }
                float combatDiv4 = combat * 0.4f;
                while (combatDiv4 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Strike");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv4 -= sf.getFleetPointCost();
                }
                float combatDiv5 = combat * 0.2f;
                while (combatDiv5 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_SDA_MA_variant");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.ECM,true);
                    vsf.addPermaMod("UniversalRangeFinder",true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv5 -= sf.getFleetPointCost();
                }
                float combatDiv6 = combat * 0.5f;
                while (combatDiv6 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_assault_Standerd");
                    sf.setCaptain(createLowerCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv6 -= sf.getFleetPointCost();
                }
                float combatDiv7 = combat * 0.1f;
                while (combatDiv7 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_firesupport_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv7 -= sf.getFleetPointCost();
                }
            }
            break;
            case COMBAT:
            {
                FleetMemberAPI f = fleet.getFleetData().addFleetMember("rs_lordanes_Standerd");
                ShipVariantAPI v = f.getVariant().clone();
                v.setSource(VariantSource.REFIT);
                v.addPermaMod("CR_ImprovedWeaponControlling",true);
                v.addPermaMod("CR_EngineRegularBoost",true);
                f.setVariant(v,false,false);
                f.updateStats();
                f.setCaptain(admiral);
                f.setFlagship(true);
                float combatDiv1 = combat * 0.2f;
                while (combatDiv1 > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember( "rs_Tr_5_fiver_Standerd");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.AUTOREPAIR,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDiv1 -= fy.getFleetPointCost();
                }
                float combatDiv2= combat * 0.2f;
                while (combatDiv2 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_4_Dandelion_Standerd");
                    sf.setCaptain(createHigherCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv2 -= sf.getFleetPointCost();
                }
                float combatDiv3 = combat * 0.2f;
                while (combatDiv3 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_2_Owsal_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv3 -= sf.getFleetPointCost();
                }
                float combatDiv4 = combat * 0.1f;
                while (combatDiv4 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_fidelitas_Ballistic");
                    sf.setCaptain(createRSJuniorCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv4 -= sf.getFleetPointCost();
                }
                float combatDiv5 = combat * 0.1f;
                while (combatDiv5 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_fidelitas_Energy");
                    sf.setCaptain(createRSJuniorCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv5 -= sf.getFleetPointCost();
                }
                float combatDiv6 = combat * 0.4f;
                while (combatDiv6 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_2_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv6 -= sf.getFleetPointCost();
                }
                float combatDiv7 = combat * 0.4f;
                while (combatDiv7 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Strike");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv7 -= sf.getFleetPointCost();
                }
                float combatDiv8 = combat * 0.2f;
                while (combatDiv8 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_SDA_MA_variant");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.ECM,true);
                    vsf.addPermaMod("UniversalRangeFinder",true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv8 -= sf.getFleetPointCost();
                }
                float combatDiv11 = combat * 0.1f;
                while (combatDiv11 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_assault_Standerd");
                    sf.setCaptain(createLowerCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv11 -= sf.getFleetPointCost();
                }
                float combatDiv9 = combat * 0.1f;
                while (combatDiv9 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_firesupport_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv9 -= sf.getFleetPointCost();
                }
                float combatDiv10 = combat * 0.1f;
                while (combatDiv10 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_defense_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv10 -= sf.getFleetPointCost();
                }
            }
            break;
            case HEAVY:
            {
                FleetMemberAPI f = fleet.getFleetData().addFleetMember("rs_Tr_6_Inle_Standerd");
                ShipVariantAPI v = f.getVariant().clone();
                v.setSource(VariantSource.REFIT);
                v.addPermaMod(HullMods.AUTOREPAIR,true);
                v.addPermaMod(HullMods.MAGAZINES,true);
                v.addPermaMod("CR_ImprovedWeaponControlling",true);
                f.setVariant(v,false,false);
                f.updateStats();
                f.setCaptain(admiral);
                f.setFlagship(true);
                float combatDiva = combat * 0.25f;
                while (combatDiva > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_Tr_5_fiver_Standerd");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS,true);
                    vfy.addPermaMod(HullMods.AUTOREPAIR,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDiva -= fy.getFleetPointCost();
                }
                float combatDivb = combat * 0.15f;
                while (combatDivb > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_Tr_4_Dandelion_Standerd");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS,true);
                    vfy.addPermaMod(HullMods.MAGAZINES,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDivb -= fy.getFleetPointCost();
                }
                float combatDiv1 = combat * 0.3f;
                while (combatDiv1 > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember( "rs_fidelitas_Ballistic");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.AUTOREPAIR,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDiv1 -= fy.getFleetPointCost();
                }
                float combatDiv2 = combat * 0.3f;
                while (combatDiv2 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_fidelitas_Energy");
                    sf.setCaptain(createLowerCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv2 -= sf.getFleetPointCost();
                }
                float combatDiv3 = combat * 0.3f;
                while (combatDiv3 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_2_Owsal_Standerd");
                    sf.setCaptain(createLowerCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv3 -= sf.getFleetPointCost();
                }
                float combatDiv4 = combat * 0.1f;
                while (combatDiv4 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_2_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv4 -= sf.getFleetPointCost();
                }
                float combatDiv5 = combat * 0.4f;
                while (combatDiv5 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv5 -= sf.getFleetPointCost();
                }
                float combatDiv6 = combat * 0.2f;
                while (combatDiv6 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Strike");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv6 -= sf.getFleetPointCost();
                }
                float combatDiv7 = combat * 0.2f;
                while (combatDiv7 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_TMA_MA_Strike");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv7 -= sf.getFleetPointCost();
                }
                float combatDiv8 = combat * 0.2f;
                while (combatDiv8 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_assault_Standerd");
                    sf.setCaptain(createLowerCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv8 -= sf.getFleetPointCost();
                }
                float combatDiv9 = combat * 0.25f;
                while (combatDiv9 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_firesupport_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv9 -= sf.getFleetPointCost();
                }
                float combatDiv10 = combat * 0.15f;
                while (combatDiv10 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_defense_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv10 -= sf.getFleetPointCost();
                }

            }
            break;
        }
        FleetMemberAPI elegy = fleet.getFleetData().addFleetMember("rs_huo_standard");
        elegy.setCaptain(createHigherCaptain());
        elegy.updateStats();

        elegy = fleet.getFleetData().addFleetMember("rs_huo_standard");
        elegy.setCaptain(createHigherCaptain());
        elegy.updateStats();


        float t = tanker;
        while (t>0){
            FleetMemberAPI f = fleet.getFleetData().addFleetMember("rs_you_standard");
            t-=f.getFleetPointCost();
        }

        float f = freighter;
        while (f>0){
            FleetMemberAPI m = fleet.getFleetData().addFleetMember("rs_you_standard");
            f-=m.getFleetPointCost();
        }

        fleet.getMemoryWithoutUpdate().set("$RS_GuardFleet",true);

        return fleet;
    }

    public static CampaignFleetAPI createGuardFleet2(float combat, float tanker, float freighter, MarketAPI market){
        Type type = typePicker.pick();
        FactionAPI faction = Global.getSector().getFaction("red_stripe");
        CampaignFleetAPI fleet = createGaurdFleet(String.valueOf(faction), FleetTypes.PATROL_LARGE,market);
        PersonAPI admiral = createAdmiral();
        fleet.setCommander(admiral);
        if(type == Type.COMBAT){
            fleet.getMemoryWithoutUpdate().set("$RS_FullAssault",true);
        }
        switch (type) {
            case CARRIER:
            {
                FleetMemberAPI f = fleet.getFleetData().addFleetMember("rs_bethlehem_Elite");
                ShipVariantAPI v = f.getVariant().clone();
                v.setSource(VariantSource.REFIT);
                v.addPermaMod(HullMods.AUTOREPAIR,true);
                v.addPermaMod(HullMods.TURRETGYROS,true);
                f.setVariant(v,false,false);
                f.updateStats();
                f.setCaptain(admiral);
                f.setFlagship(true);
                float combatDiva = combat * 0.1f;
                while (combatDiva > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_Energy");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS,true);
                    vfy.addPermaMod(HullMods.AUTOREPAIR,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDiva -= fy.getFleetPointCost();
                }
                float combatDivb = combat * 0.1f;
                while (combatDivb > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_Ballistc");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS,true);
                    vfy.addPermaMod(HullMods.MAGAZINES,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDivb -= fy.getFleetPointCost();
                }
                float combatDivc = combat * 0.1f;
                while (combatDivc > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_missile_Energy");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS,true);
                    vfy.addPermaMod(HullMods.AUTOREPAIR,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDivc -= fy.getFleetPointCost();
                }
                float combatDivd = combat * 0.1f;
                while (combatDivd > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_missile_Ballistc");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS,true);
                    vfy.addPermaMod(HullMods.MAGAZINES,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDivd -= fy.getFleetPointCost();
                }
                float combatDiv1 = combat * 0.3f;
                while (combatDiv1 > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_sophia_variant");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.AUTOREPAIR,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDiv1 -= fy.getFleetPointCost();
                }
                float combatDiv2 = combat * 0.3f;
                while (combatDiv2 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_typhaon_variant");
                    sf.setCaptain(createLowerCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv2 -= sf.getFleetPointCost();
                }
                float combatDiv2a = combat * 0.2f;
                while (combatDiv2a > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_kharybdis_Staderd");
                    sf.setCaptain(createLowerCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv2a -= sf.getFleetPointCost();
                }
                float combatDiv3 = combat * 0.4f;
                while (combatDiv3 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv3 -= sf.getFleetPointCost();
                }
                float combatDiv5 = combat * 0.2f;
                while (combatDiv5 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_SDA_MA_variant");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.ECM,true);
                    vsf.addPermaMod("UniversalRangeFinder",true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv5 -= sf.getFleetPointCost();
                }
                float combatDiv7 = combat * 0.1f;
                while (combatDiv7 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_assault_Standerd");
                    sf.setCaptain(createLowerCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv7 -= sf.getFleetPointCost();
                }
                float combatDiv8 = combat * 0.1f;
                while (combatDiv8 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_firesupport_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv8 -= sf.getFleetPointCost();
                }
                float combatDiv9 = combat * 0.1f;
                while (combatDiv9 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_defense_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv9 -= sf.getFleetPointCost();
                }
                float combatDiv10 = combat * 0.1f;
                while (combatDiv10 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_6_haznthley_assault");
                    sf.setCaptain(createLowerCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv10 -= sf.getFleetPointCost();
                }
                float combatDiv11 = combat * 0.1f;
                while (combatDiv11 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_6_haznthley_support");
                    sf.setCaptain(createLowerCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv11 -= sf.getFleetPointCost();
                }
            }
            break;
            case COMBAT:
            {
                FleetMemberAPI f = fleet.getFleetData().addFleetMember("rs_bethlehem_Elite");
                ShipVariantAPI v = f.getVariant().clone();
                v.setSource(VariantSource.REFIT);
                v.addPermaMod(HullMods.AUTOREPAIR,true);
                v.addPermaMod(HullMods.TURRETGYROS,true);
                f.setVariant(v,false,false);
                f.updateStats();
                f.setCaptain(admiral);
                f.setFlagship(true);
                float combatDiva = combat * 0.1f;
                while (combatDiva > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_Energy");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS,true);
                    vfy.addPermaMod(HullMods.AUTOREPAIR,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDiva -= fy.getFleetPointCost();
                }
                float combatDivb = combat * 0.1f;
                while (combatDivb > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_Ballistc");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS,true);
                    vfy.addPermaMod(HullMods.MAGAZINES,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDivb -= fy.getFleetPointCost();
                }
                float combatDivc = combat * 0.1f;
                while (combatDivc > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_missile_Energy");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS,true);
                    vfy.addPermaMod(HullMods.AUTOREPAIR,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDivc -= fy.getFleetPointCost();
                }
                float combatDivd = combat * 0.1f;
                while (combatDivd > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_missile_Ballistc");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS,true);
                    vfy.addPermaMod(HullMods.MAGAZINES,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDivd -= fy.getFleetPointCost();
                }
                float combatDiv1 = combat * 0.2f;
                while (combatDiv1 > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember( "rs_vincere_variant");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.AUTOREPAIR,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDiv1 -= fy.getFleetPointCost();
                }
                float combatDiv2= combat * 0.2f;
                while (combatDiv2 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_sophia_variant");
                    sf.setCaptain(createHigherCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv2 -= sf.getFleetPointCost();
                }
                float combatDiv3 = combat * 0.2f;
                while (combatDiv3 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_cerberus_variant");
                    sf.setCaptain(createRSJuniorCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv3 -= sf.getFleetPointCost();
                }
                float combatDiv4 = combat * 0.1f;
                while (combatDiv4 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_tiumphus_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv4 -= sf.getFleetPointCost();
                }
                float combatDiv5 = combat * 0.1f;
                while (combatDiv5 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_livoroculorum_variant");
                    sf.setCaptain(createRSJuniorCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv5 -= sf.getFleetPointCost();
                }
                float combatDiv7 = combat * 0.4f;
                while (combatDiv7 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Strike");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv7 -= sf.getFleetPointCost();
                }
                float combatDiv8 = combat * 0.2f;
                while (combatDiv8 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_SDA_MA_variant");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.ECM,true);
                    vsf.addPermaMod("UniversalRangeFinder",true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv8 -= sf.getFleetPointCost();
                }
                float combatDiv9 = combat * 0.1f;
                while (combatDiv9 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_firesupport_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv9 -= sf.getFleetPointCost();
                }
                float combatDiv10 = combat * 0.1f;
                while (combatDiv10 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_defense_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv10 -= sf.getFleetPointCost();
                }
                float combatDiv12 = combat * 0.1f;
                while (combatDiv12 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_haznthley_Standerd");
                    sf.setCaptain(createLowerCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv12 -= sf.getFleetPointCost();
                }
            }
            break;
            case HEAVY:
            {
                FleetMemberAPI f = fleet.getFleetData().addFleetMember("rs_bethlehem_Elite");
                ShipVariantAPI v = f.getVariant().clone();
                v.setSource(VariantSource.REFIT);
                v.addPermaMod(HullMods.AUTOREPAIR,true);
                v.addPermaMod(HullMods.TURRETGYROS,true);
                f.setVariant(v,false,false);
                f.updateStats();
                f.setCaptain(admiral);
                f.setFlagship(true);
                float combatDiva = combat * 0.1f;
                while (combatDiva > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_Energy");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS,true);
                    vfy.addPermaMod(HullMods.AUTOREPAIR,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDiva -= fy.getFleetPointCost();
                }
                float combatDivb = combat * 0.1f;
                while (combatDivb > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_Ballistc");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS,true);
                    vfy.addPermaMod(HullMods.MAGAZINES,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDivb -= fy.getFleetPointCost();
                }
                float combatDivc = combat * 0.1f;
                while (combatDivc > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_missile_Energy");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS,true);
                    vfy.addPermaMod(HullMods.AUTOREPAIR,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDivc -= fy.getFleetPointCost();
                }
                float combatDivd = combat * 0.1f;
                while (combatDivd > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember("rs_nazaret_missile_Ballistc");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.ARMOREDWEAPONS,true);
                    vfy.addPermaMod(HullMods.MAGAZINES,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDivd -= fy.getFleetPointCost();
                }
                float combatDiv1 = combat * 0.3f;
                while (combatDiv1 > 0) {
                    FleetMemberAPI fy = fleet.getFleetData().addFleetMember( "rs_vincere_variant");
                    fy.setCaptain(createHigherCaptain());
                    ShipVariantAPI vfy = fy.getVariant().clone();
                    vfy.setSource(VariantSource.REFIT);
                    vfy.addPermaMod(HullMods.AUTOREPAIR,true);
                    fy.setVariant(vfy,false,false);
                    fy.updateStats();
                    combatDiv1 -= fy.getFleetPointCost();
                }
                float combatDiv2 = combat * 0.3f;
                while (combatDiv2 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_scylla_variant");
                    sf.setCaptain(createLowerCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv2 -= sf.getFleetPointCost();
                }
                float combatDiv3 = combat * 0.3f;
                while (combatDiv3 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_typhaon_variant");
                    sf.setCaptain(createLowerCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv3 -= sf.getFleetPointCost();
                }
                float combatDiv4 = combat * 0.1f;
                while (combatDiv4 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_superbia_variant");
                    sf.setCaptain(createRSJuniorCaptain());
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv4 -= sf.getFleetPointCost();
                }
                float combatDiv5 = combat * 0.4f;
                while (combatDiv5 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv5 -= sf.getFleetPointCost();
                }
                float combatDiv6 = combat * 0.4f;
                while (combatDiv6 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_MA_01_Strike");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv6 -= sf.getFleetPointCost();
                }
                float combatDiv7 = combat * 0.5f;
                while (combatDiv7 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_TMA_MA_Strike");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR,true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER,true);
                    sf.setVariant(vsf,false,false);
                    sf.updateStats();
                    combatDiv7 -= sf.getFleetPointCost();
                }
                float combatDiv8 = combat * 0.1f;
                while (combatDiv8 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_assault_Standerd");
                    sf.setCaptain(createLowerCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv8 -= sf.getFleetPointCost();
                }
                float combatDiv9 = combat * 0.1f;
                while (combatDiv9 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_firesupport_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv9 -= sf.getFleetPointCost();
                }
                float combatDiv10 = combat * 0.1f;
                while (combatDiv10 > 0) {
                    FleetMemberAPI sf = fleet.getFleetData().addFleetMember("rs_Tr_1_owsla_defense_Standerd");
                    sf.setCaptain(createRSJuniorCaptain());
                    sf.updateStats();
                    ShipVariantAPI vsf = sf.getVariant().clone();
                    vsf.setSource(VariantSource.REFIT);
                    vsf.addPermaMod(HullMods.AUTOREPAIR, true);
                    vsf.addPermaMod(STABILIZEDSHIELDEMITTER, true);
                    sf.setVariant(vsf, false, false);
                    sf.updateStats();
                    combatDiv10 -= sf.getFleetPointCost();
                }

            }
            break;
        }
        FleetMemberAPI elegy = fleet.getFleetData().addFleetMember("rs_huo_standard");
        elegy.setCaptain(createHigherCaptain());
        elegy.updateStats();

        elegy = fleet.getFleetData().addFleetMember("rs_huo_standard");
        elegy.setCaptain(createHigherCaptain());
        elegy.updateStats();


        float t = tanker;
        while (t>0){
            FleetMemberAPI f = fleet.getFleetData().addFleetMember("rs_you_standard");
            t-=f.getFleetPointCost();
        }

        float f = freighter;
        while (f>0){
            FleetMemberAPI m = fleet.getFleetData().addFleetMember("rs_you_standard");
            f-=m.getFleetPointCost();
        }

        fleet.getMemoryWithoutUpdate().set("$RS_GuardFleet",true);

        return fleet;
    }

    public static PersonAPI createAdmiral() {
        PersonAPI person = Global.getFactory().createPerson();
        person.setPersonality(Personalities.AGGRESSIVE);
        person.setPortraitSprite(Global.getSector().getFaction("red_stripe").getPortraits(person.getGender()).pick());
        person.getStats().setSkipRefresh(true);

        person.getStats().setLevel(12);
        person.getStats().setSkillLevel("GeniusCommand",1);
        person.getStats().setSkillLevel("rs_polarized_armor", 2);
        person.getStats().setSkillLevel(Skills.HELMSMANSHIP, 2);
        person.getStats().setSkillLevel(Skills.TARGET_ANALYSIS, 2);
        person.getStats().setSkillLevel(Skills.GUNNERY_IMPLANTS, 2);
        person.getStats().setSkillLevel(Skills.COMBAT_ENDURANCE, 2);
        person.getStats().setSkillLevel(Skills.SYSTEMS_EXPERTISE,2);
        person.getStats().setSkillLevel(Skills.POINT_DEFENSE,2);
        person.getStats().setSkillLevel(Skills.FIELD_MODULATION, 2);
        person.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 2);

        person.getStats().setSkillLevel(Skills.NAVIGATION, 1);
        person.getStats().setSkillLevel(Skills.CREW_TRAINING,1);
        person.getStats().setSkillLevel(Skills.FLUX_REGULATION,1);
        person.getStats().setSkillLevel(Skills.CYBERNETIC_AUGMENTATION,1);


        person.getStats().setSkipRefresh(false);

        return person;
    }

    public static PersonAPI createHigherCaptain() {
        PersonAPI person = Global.getSector().getFaction("red_stripe").createRandomPerson();
        //person.setId(Misc.genUID());
        person.setPersonality(Personalities.AGGRESSIVE);
        person.getStats().setSkipRefresh(true);

        person.getStats().setLevel(8);
        person.getStats().setSkillLevel("rs_polarized_armor", 2);
        person.getStats().setSkillLevel(Skills.HELMSMANSHIP, 2);
        person.getStats().setSkillLevel(Skills.TARGET_ANALYSIS, 2);
        person.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 2);
        person.getStats().setSkillLevel(Skills.GUNNERY_IMPLANTS, 2);
        person.getStats().setSkillLevel(Skills.ENERGY_WEAPON_MASTERY, 2);
        person.getStats().setSkillLevel(Skills.COMBAT_ENDURANCE, 2);
        person.getStats().setSkillLevel(Skills.FIELD_MODULATION, 2);
        person.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 2);
        person.getStats().setSkillLevel(Skills.SYSTEMS_EXPERTISE,2);
        person.getStats().setSkillLevel(Skills.POINT_DEFENSE,2);


        person.getStats().setSkipRefresh(false);

        return person;
    }

    public static PersonAPI createLowerCaptain() {
        PersonAPI person = Global.getSector().getFaction("red_stripe").createRandomPerson();
        person.setPersonality(Personalities.AGGRESSIVE);
        person.getStats().setSkipRefresh(true);
        //person.setId(Misc.genUID());

        person.getStats().setLevel(5);
        person.getStats().setSkillLevel("rs_polarized_armor", 2);
        person.getStats().setSkillLevel(Skills.HELMSMANSHIP, 2);
        person.getStats().setSkillLevel(Skills.TARGET_ANALYSIS, 2);
        person.getStats().setSkillLevel(Skills.FIELD_MODULATION, 2);
        person.getStats().setSkillLevel(Skills.SYSTEMS_EXPERTISE,2);
        person.getStats().setSkillLevel(Skills.COMBAT_ENDURANCE, 2);


        person.getStats().setSkipRefresh(false);

        return person;
    }
    public static PersonAPI createRSJuniorCaptain() {
        PersonAPI person = Global.getSector().getFaction("red_stripe").createRandomPerson();
        person.setPersonality(Personalities.AGGRESSIVE);
        person.getStats().setSkipRefresh(true);
        //person.setId(Misc.genUID());

        person.getStats().setLevel(3);
        person.getStats().setSkillLevel(Skills.HELMSMANSHIP, 1);
        person.getStats().setSkillLevel(Skills.DAMAGE_CONTROL, 2);
        person.getStats().setSkillLevel(Skills.SYSTEMS_EXPERTISE,1);


        person.getStats().setSkipRefresh(false);

        return person;
    }

    private static List<String> startingAbilities = null;
    public static CampaignFleetAPI createGaurdFleet(String factionId, String fleetType, MarketAPI market) {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        String fleetName = faction.getFleetTypeName(fleetType)+ "hussar";
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(factionId, fleetName, true);
        fleet.getMemoryWithoutUpdate().set("$RS_GuardFleet", fleetType);

        if (market != null && !market.getId().equals("fake")) {
            fleet.getMemoryWithoutUpdate().set("$RS_GuardFleet", market.getId());
        }

        if (startingAbilities == null) {
            startingAbilities = new ArrayList<>();
            for (String id : Global.getSettings().getSortedAbilityIds()) {
                AbilitySpecAPI spec = Global.getSettings().getAbilitySpec(id);
                if (spec.isAIDefault()) {
                    startingAbilities.add(id);
                }
            }
        }

        for (String id : startingAbilities) {
            fleet.addAbility(id);
        }

        return fleet;
    }

    public static CampaignFleetAPI createFlgeFleet(String factionId, String fleetType, MarketAPI market) {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        String fleetName = faction.getFleetTypeName(fleetType)+ "Kingo";
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(factionId, fleetName, true);
        fleet.getMemoryWithoutUpdate().set("$RS_FlagFleet", fleetType);

        if (market != null && !market.getId().equals("fake")) {
            fleet.getMemoryWithoutUpdate().set("$RS_FlagFleet", market.getId());
        }

        if (startingAbilities == null) {
            startingAbilities = new ArrayList<>();
            for (String id : Global.getSettings().getSortedAbilityIds()) {
                AbilitySpecAPI spec = Global.getSettings().getAbilitySpec(id);
                if (spec.isAIDefault()) {
                    startingAbilities.add(id);
                }
            }
        }

        for (String id : startingAbilities) {
            fleet.addAbility(id);
        }

        return fleet;
    }

}


