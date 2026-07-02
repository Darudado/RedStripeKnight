package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RichathaoirnaBandeSystem extends BaseHullMod {
    private float check = 0.0F;
    private static final String ERROR = "IncompatibleHullmodWarning";
    private static final Set<String> BLOCKED_OMNI = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER_PLAYER_ONLY = new HashSet<>();

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getBallisticWeaponRangeBonus().modifyPercent(id,50f);
        stats.getEnergyWeaponRangeBonus().modifyPercent(id,50f);
        stats.getFighterRefitTimeMult().modifyPercent(id, 0.8f);
        stats.getDynamic().getStat(Stats.REPLACEMENT_RATE_DECREASE_MULT).modifyMult(id, 0f);
        stats.getDynamic().getStat(Stats.REPLACEMENT_RATE_INCREASE_MULT).modifyMult(id, 125f);
        stats.getWeaponTurnRateBonus().modifyPercent(id,15f);
        stats.getWeaponHealthBonus().modifyPercent(id,50f);
        stats.getSensorProfile().modifyPercent(id,50f);
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        super.applyEffectsToFighterSpawnedByShip(fighter, ship, id);
        fighter.getMutableStats().getMaxSpeed().modifyPercent(id ,15f);
        fighter.getMutableStats().getTurnAcceleration().modifyPercent(id,15f);
        fighter.getMutableStats().getFighterWingRange().modifyPercent(id,50f);
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id, FleetMemberAPI member) {
        ship.getMutableStats();

        if (check > 0.0F && --check < 1.0F) {
            ship.getVariant().removeMod(ERROR);
        }

        String hullId = ship.getHullSpec().getBaseHullId();
        if (isModule(hullId)) {
            checkAndRemoveBlockedMods(ship, BLOCKED_OMNI);
            checkAndRemoveBlockedMods(ship, BLOCKED_OTHER);
            checkAndRemoveBlockedMods(ship, BLOCKED_OTHER_PLAYER_ONLY);
        }

        String aiCoreId = "omega_core";
        PersonAPI aiCore = Global.getFactory().createPerson();



        // 设置AI所属势力（与母舰相同）
        String factionId = "player";
        aiCore.setFaction(factionId);
        member.setCaptain(aiCore);

        // 配置AI核心属性
        aiCore.setAICoreId(aiCoreId);
        aiCore.setPortraitSprite("graphics/portraits/portrait_ai1b.png"); // 指定AI头像
        aiCore.getStats().setLevel(11);
        aiCore.getStats().setSkillLevel("target_analysis", 2.0F);   // 目标分析技能2级
        aiCore.getStats().setSkillLevel("helmsmanship", 2.0F);       // 操舵术技能2级
        aiCore.getStats().setSkillLevel("combat_endurance", 2.0F);  // 战斗耐力技能2级
        aiCore.getStats().setSkillLevel("missile_specialization", 2.0F);
        aiCore.getStats().setSkillLevel("systems_expertise", 2.0F);
        aiCore.getStats().setSkillLevel("gunnery_implants", 2.0F);
        aiCore.getStats().setSkillLevel("point_defense", 2.0F);
        aiCore.getStats().setSkillLevel("ballistic_mastery", 2.0F);
        aiCore.getStats().setSkillLevel("energy_weapon_mastery", 2.0F);
        aiCore.getStats().setSkillLevel("field_modulation", 2.0F);
        aiCore.getStats().setSkillLevel("damage_control", 2.0F);

        aiCore.setPersonality("steady");
        aiCore.setRankId(Ranks.SPACE_ADMIRAL);
        aiCore.setPostId(null);
    }

    private static void advanceChild(ShipAPI child, ShipAPI parent) {
        if (child.hasLaunchBays()) {
            if (child.isPullBackFighters() ^ parent.isPullBackFighters()) {
                child.giveCommand(ShipCommand.PULL_BACK_FIGHTERS, null, 0);
            }

            if (child.getAIFlags() != null) {
                Object target = (Global.getCombatEngine().getPlayerShip() == parent || parent.getAIFlags() == null) ?
                        parent.getShipTarget() : parent.getAIFlags().getCustom(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET);
                if (target != null) {
                    child.getAIFlags().setFlag(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET, 1.0F, target);
                }
            }
        }

        if (parent.getShipTarget() != null) {  //  检测母舰是否有锁定目标
            child.setShipTarget(parent.getShipTarget()); //  强制同步目标
        }

        if (child.hasLaunchBays()) {
            if (child.isPullBackFighters() ^ parent.isPullBackFighters()) {
                child.giveCommand(ShipCommand.PULL_BACK_FIGHTERS, null, 0);
            }
            if (child.getAIFlags() != null) {
                if (((Global.getCombatEngine().getPlayerShip() == parent) || (parent.getAIFlags() == null))
                        && (parent.getShipTarget() != null)) {
                    child.getAIFlags().setFlag(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET, 1f, parent.getShipTarget());
                } else if ((parent.getAIFlags() != null)
                        && parent.getAIFlags().hasFlag(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET)
                        && (parent.getAIFlags().getCustom(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET) != null)) {
                    child.getAIFlags().setFlag(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET, 1f, parent.getAIFlags().getCustom(ShipwideAIFlags.AIFlags.CARRIER_FIGHTER_TARGET));
                }
            }
        }

        MutableShipStatsAPI parentStats = parent.getMutableStats();
        MutableShipStatsAPI childStats = child.getMutableStats();

        if (parentStats != null && childStats != null) {
            float parentTimeMult = parentStats.getTimeMult().getModifiedValue();
            parentStats.getTimeMult().getModifiedValue();

            if (parent.isAlive()) {
                childStats.getTimeMult().modifyMult("CR_LinkedHull_TimeSync", parentTimeMult);
            }
        }
    }

    private static void advanceParent(ShipAPI parent, List<ShipAPI> children) {


    }
    

    public void advanceInCombat(ShipAPI ship, float amount) {
        ShipAPI parent = ship.getParentStation();
        if (parent != null) advanceChild(ship, parent);

        if (parent != null) {
            if (parent.getTravelDrive().isActive()) {
                ship.toggleTravelDrive();
            } else {
                ship.getTravelDrive().deactivate();
            }
        }

        List<ShipAPI> children = ship.getChildModulesCopy();
        if (children != null && !children.isEmpty()) {
            advanceParent(ship, children);
        }


    }



    private boolean isModule(String hullId) {
        return hullId.equals("cr_bansee_module1R") ||
                hullId.equals("cr_bansee_module1L") ||
                hullId.equals("cr_bansee_module2R") ||
                hullId.equals("cr_bansee_module2L") ||
                hullId.equals("cr_bansee_module3R") ||
                hullId.equals("cr_bansee_module3L");
    }

    private void checkAndRemoveBlockedMods(ShipAPI ship, @NotNull Set<String> blockedMods) {
        for (String mod : blockedMods) {
            if (ship.getVariant().hasHullMod(mod)) {
                ship.getVariant().removeMod(mod);
                ship.getVariant().addMod(ERROR);
            }
        }
    }



    static {
        BLOCKED_OMNI.add("high_scatter_amp");

        BLOCKED_OTHER.add("shield_shunt");
        BLOCKED_OTHER.add("unstable_injector");
        BLOCKED_OTHER.add("safetyoverrides");
        BLOCKED_OTHER.add("recovery_shuttles");
        BLOCKED_OTHER.add("additional_berthing");
        BLOCKED_OTHER.add("augmentedengines");
        BLOCKED_OTHER.add("auxiliary_fuel_tanks");
        BLOCKED_OTHER.add("efficiency_overhaul");
        BLOCKED_OTHER.add("expanded_cargo_holds");
        BLOCKED_OTHER.add("hiressensors");
        BLOCKED_OTHER.add("militarized_subsystems");

        BLOCKED_OTHER_PLAYER_ONLY.add("converted_hangar");
        BLOCKED_OTHER_PLAYER_ONLY.add("TSC_converted_hangar");
    }
}