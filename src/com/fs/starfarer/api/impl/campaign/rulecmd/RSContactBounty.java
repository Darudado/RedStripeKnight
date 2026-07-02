package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickParams;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipCondition;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.*;

public class RSContactBounty extends BaseIntelPlugin {

    // 任务阶段
    private enum State { stage_1, stage_2, stage_3 }

    private final String MISSION_KEY = "$rs_custom_bounty";
    private StarSystemAPI system;
    private CampaignFleetAPI target;
    private PlanetAPI planet;
    private float credits = 500000f;
    private float playerpower;
    private State state = State.stage_1;
    private State curState = State.stage_1;
    private boolean missionfinish;
    // 标记此实例是否有效（用于重复任务检测）
    private boolean active = true;

    // 敌旗舰可选变体
    private final List<String> flagshipVariants = Arrays.asList(
            "cr_michaelangelus_Elite", "cr_dantealighieri_variant", "cr_theosphany_variant",
            "cr_malakbinger_variant"
    );
    // 候选敌对势力
    private final List<String> factionList = List.of("cinis_of_crusaders");
    // 军官性格池
    private final List<String> personalityList = Arrays.asList("steady", "aggressive", "reckless");
    // 军官技能
    private final List<String> combatSkills = Arrays.asList(
            "helmsmanship", "helmsmanship",
            "combat_endurance", "combat_endurance",
            "target_analysis", "target_analysis",
            "gunnery_implants", "gunnery_implants",
            "damage_control", "damage_control",
            "point_defense", "point_defense",
            "ballistic_mastery", "ballistic_mastery",
            "missile_specialization", "missile_specialization",
            "systems_expertise", "systems_expertise"
    );
    private final List<String> fleetSkills = Arrays.asList(
            "tactical_drills", "coordinated_maneuvers",
            "electronic_warfare", "carrier_group"
    );

    public RSContactBounty(InteractionDialogAPI dialog) {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        // 防止重复任务：若全局记忆体中已有活跃任务标记，则放弃本次初始化
        if (memory.contains(MISSION_KEY)) {
            active = false;
            missionfinish = true; // 标记为已完成状态，避免后续操作
            // 注意：不执行任何生成逻辑，也不将自己添加为 intel/script
            return;
        }
        // 正常初始化流程
        memory.set(MISSION_KEY, true);
        this.setImportant(true);
        this.spawnFleet();
        Global.getSector().addScript(this);
        if (dialog == null) {
            Global.getSector().getIntelManager().addIntel(this, false);
        } else {
            Global.getSector().getIntelManager().addIntel(this, false, dialog.getTextPanel());
        }
    }

    /**
     * 外部可调用此方法判断本实例是否真的创建了任务
     */
    public boolean isMissionActive() {
        return active && !missionfinish;
    }

    @Override
    public void advanceImpl(float amount) {
        // 无效实例直接跳过
        if (!active) return;

        // 防御：舰队意外消失则重建
        if (target == null) {
            spawnFleet();
            Global.getSector().getMemoryWithoutUpdate().set(MISSION_KEY, true);
            sendUpdateIfPlayerHasIntel(new Object(), false);
            return;
        }

        // 阶段1 -> 2：玩家进入目标星系
        if (state == State.stage_1 &&
                Global.getSector().getPlayerFleet().getStarSystem() != null &&
                Global.getSector().getPlayerFleet().getStarSystem().getId().equals(system.getId())) {
            state = State.stage_2;
        }

        // 阶段2 -> 3：目标舰队被全歼
        if (state == State.stage_2 && target.isEmpty()) {
            state = State.stage_3;
            if (!missionfinish) {
                missionfinish = true;
                // 移除全局任务标记，允许新任务启动
                Global.getSector().getMemoryWithoutUpdate().unset(MISSION_KEY);
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(credits);
                Global.getSoundPlayer().playSound("storyevent_diktat_execution", 1f, 1f,
                        Global.getSoundPlayer().getListenerPos(), new Vector2f());
                spawnRewardWreck();
            }
            endAfterDelay(3f);
        }

        // 状态变化时刷新界面
        if (curState != state) {
            curState = state;
            sendUpdateIfPlayerHasIntel(new Object(), false);
        }
    }

    private void spawnRewardWreck() {
        if (target == null || target.getContainingLocation() == null) return;
        LocationAPI loc = target.getContainingLocation();
        Vector2f pos = target.getLocation();

        ShipRecoverySpecial.PerShipData ship = new ShipRecoverySpecial.PerShipData(
                "hyperion_Strike", ShipCondition.WRECKED, 0f);
        ship.shipName = "RS Bounty Prize";
        DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(ship, false);
        CustomCampaignEntityAPI entity = (CustomCampaignEntityAPI) BaseThemeGenerator.addSalvageEntity(
                loc, "wreck", "neutral", params);
        Misc.makeImportant(entity, "unique_bounty_reward");
        entity.getLocation().set(pos.x + (50f - (float)Math.random() * 100f),
                pos.y + (50f - (float)Math.random() * 100f));

        ShipRecoverySpecial.ShipRecoverySpecialData data = new ShipRecoverySpecial.ShipRecoverySpecialData(null);
        data.notNowOptionExits = true;
        data.noDescriptionText = true;
        data.addShip(ship);
        Misc.setSalvageSpecial(entity, data);
    }

    private void spawnFleet() {
        WeightedRandomPicker<PlanetAPI> picker = new WeightedRandomPicker<>();
        for (StarSystemAPI sys : Global.getSector().getStarSystems()) {
            if (sys.getTags().contains("theme_core") || sys.getTags().contains("theme_hidden")
                    || sys.hasBlackHole() || sys.hasPulsar() || sys.getPlanets().isEmpty()) continue;
            for (PlanetAPI p : sys.getPlanets()) {
                if (p.getMarket() != null && !p.getMarket().hasIndustry("population")) {
                    picker.add(p, 1f);
                }
            }
        }
        planet = picker.pick();
        if (planet == null) {
            // 极端情况无可用殖民地，取一个非核心星系中心点
            for (StarSystemAPI sys : Global.getSector().getStarSystems()) {
                if (!sys.getTags().contains("theme_core")) {
                    system = sys;
                    break;
                }
            }
            if (system == null) system = Global.getSector().getStarSystems().get(0);
            // 没有 planet 时，舰队生成在系统中心
        } else {
            system = planet.getStarSystem();
        }

        target = FleetFactoryV3.createEmptyFleet("neutral", "patrolLarge", null);
        target.setTransponderOn(false);
        target.setNoAutoDespawn(true);
        target.setName("RS Bounty Fleet");
        target.setId("RS_bounty_target");
        target.setNoFactionInName(true);
        Misc.makeImportant(target, "interception");

        String faction = factionList.get(new Random().nextInt(factionList.size()));

        // 计算玩家强度
        playerpower = 0;
        for (FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            float smod = m.getVariant().getSMods().size();
            playerpower += m.getHullSpec().getFleetPoints() * (1f + smod * 0.1f);
        }
        credits = 500000f + playerpower * 1000f;

        float capPoint = playerpower * 0.3f;
        float friPoint = playerpower * 0.3f;
        float desPoint = playerpower * 0.2f;
        float cruPoint = playerpower * 0.2f;

        addFleetMemberByRole(faction, "combatSmall", friPoint);
        addFleetMemberByRole(faction, "combatMedium", desPoint);
        addFleetMemberByRole(faction, "combatLarge", cruPoint);
        addFleetMemberByRole(faction, "combatCapital", capPoint);

        int index = (int)(Math.random() * flagshipVariants.size());
        FleetMemberAPI flagship = target.getFleetData().addFleetMember(flagshipVariants.get(index));
        flagship.getVariant().addTag("always_recoverable");
        flagship.getVariant().addTag("priority_deployment_enm");
        flagship.getRepairTracker().setCR(1f);

        PersonAPI officer = Global.getSector().getFaction("cinis_of_crusaders").createRandomPerson();
        officer.getStats().setLevel(14);
        officer.setPersonality(personalityList.get(index % personalityList.size()));
        for (String skill : combatSkills) {
            float lvl = officer.getStats().getSkillLevel(skill) + 1f;
            officer.getStats().setSkillLevel(skill, lvl);
        }
        for (String skill : fleetSkills) {
            float lvl = officer.getStats().getSkillLevel(skill) + 1f;
            officer.getStats().setSkillLevel(skill, lvl);
        }
        target.setCommander(officer);
        flagship.setCaptain(officer);
        flagship.setFlagship(true);

        // 添加欧米茄大型舰船
        List<ShipRolePick> omegaPicks = Global.getSector().getFaction("omega")
                .pickShip("combatMedium", ShipPickParams.priority());
        if (!omegaPicks.isEmpty()) {
            FleetMemberAPI omegaMember = target.getFleetData().addFleetMember(omegaPicks.get(0).variantId);
            omegaMember.getVariant().addTag("priority_deployment_enm");
            omegaMember.setCaptain(Global.getSector().getFaction("omega").createRandomPerson());
        }
        
        // 行为设置
        target.getMemoryWithoutUpdate().set("$cfai_makeHostile", true);
        target.getMemoryWithoutUpdate().set("$cfai_makeAggressive", true);
        target.getMemoryWithoutUpdate().set("$noRepImpact", true);
        target.getMemoryWithoutUpdate().set("$lowRepImpact", true);
        target.getMemoryWithoutUpdate().set("$cfai_makeAlwaysPursue", true);

        LocationAPI location = planet != null ? planet.getContainingLocation() : system;
        location.addEntity(target);
        if (planet != null) {
            target.setLocation(planet.getLocation().x, planet.getLocation().y);
            target.getAI().addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, planet, 1000000f, null);
        } else {
            target.setLocation(system.getLocation().x, system.getLocation().y);
            target.getAI().addAssignment(FleetAssignment.PATROL_SYSTEM, null, 1000000f, null);
        }
        target.forceSync();
    }

    private void addFleetMemberByRole(String faction, String role, float maxPoints) {
        float used = 0;
        FactionAPI fac = Global.getSector().getFaction(faction);
        while (used < maxPoints) {
            WeightedRandomPicker<ShipRolePick> rolePicker = new WeightedRandomPicker<>();
            rolePicker.addAll(fac.pickShip(role, ShipPickParams.priority()));
            ShipRolePick pick = rolePicker.pick();
            if (pick == null) break;
            FleetMemberAPI member = target.getFleetData().addFleetMember(pick.variantId);
            member.getRepairTracker().setCR(0.7f);
            used += member.getVariant().getHullSpec().getFleetPoints();
        }
    }

    @Override
    public boolean runWhilePaused() {
        return super.runWhilePaused();
    }

    @Override
    public void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
        // 无效实例或无有效数据时不添加信息
        if (!active || system == null) return;
        Color h = Misc.getHighlightColor();
        if (state == State.stage_1) {
            info.addPara("Go to" + system.getName(), initPad, tc, h);
        } else if (state == State.stage_2) {
            info.addPara("beat" + (target != null ? target.getName() : "target fleet"), initPad, tc, h);
        } else if (state == State.stage_3) {
            info.addPara("Mission accomplished", initPad, tc, h);
        }
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        if (!active) return;
        Color c = getTitleColor(mode);
        info.addPara(getSmallDescriptionTitle(), c, 0f);
        addBulletPoints(info, mode);
    }

    @Override
    public String getSmallDescriptionTitle() { return "Cleanup of out-of-control drone fleet"; }
    @Override
    public String getName() { return "Cleanup of out-of-control drone fleet"; }
    @Override
    public FactionAPI getFactionForUIColors() {
        return super.getFactionForUIColors();
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        if (!active) return;
        Color h = Misc.getHighlightColor();
        info.addSectionHeading("mission briefing", Alignment.MID, 3f);
        info.addPara("There are reports of a fleet of out-of-control drones at the edge of the Sector. Find and destroy them.", 10f);
        if (state == State.stage_1) {
            info.addPara("Target galaxy: %s", 3f, h, system.getName());
        } else if (state == State.stage_2) {
            info.addPara("are" + (planet != null ? planet.getName() : "Target location") + "Fighting nearby", 3f, h);
            info.addPara("Estimated player fleet strength: %s points | Reward: %s star coins", 3f, h,
                    "" + (int)playerpower, Misc.getWithDGS(credits));
            if (target != null && !target.isEmpty()) {
                info.addShipList(6, 2, width/6f - 3f, Color.WHITE,
                        target.getMembersWithFightersCopy(), 10f);
            }
        } else if (state == State.stage_3) {
            info.addPara("Bounty distributed: %s Star Coins", 3f, h, Misc.getWithDGS(credits));
            info.addPara("There are unique ships in the wreckage that can be salvaged.", 3f);
        }
    }

    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("campaignMissions", "tutorial");
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Clean up the out-of-control drone fleet");
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (!active) return null;
        return target != null ? target : system != null ? system.getCenter() : null;
    }

    // 战斗配置
    public static class BountyFIDConfig implements FleetInteractionDialogPluginImpl.FIDConfigGen {
        @Override
        public FleetInteractionDialogPluginImpl.FIDConfig createConfig() {
            FleetInteractionDialogPluginImpl.FIDConfig config = new FleetInteractionDialogPluginImpl.FIDConfig();
            config.showTransponderStatus = false;
            config.showEngageText = false;
            config.alwaysPursue = true;
            config.dismissOnLeave = false;
            config.withSalvage = true;
            config.printXPToDialog = true;
            config.noSalvageLeaveOptionText = "Continue";
            config.delegate = new FleetInteractionDialogPluginImpl.BaseFIDDelegate() {
                @Override
                public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
                    bcc.aiRetreatAllowed = false;
                    bcc.objectivesAllowed = false;
                    bcc.fightToTheLast = true;
                    bcc.enemyDeployAll = true;
                }
                @Override
                public void postPlayerSalvageGeneration(InteractionDialogAPI dialog,
                                                        FleetEncounterContext context,
                                                        CargoAPI salvage) {
                    // 可在此添加额外掉落
                }
            };
            return config;
        }
    }
}