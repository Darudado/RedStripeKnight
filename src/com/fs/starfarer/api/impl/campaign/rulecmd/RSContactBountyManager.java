package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.missions.cb.BaseCustomBountyCreator;
import com.fs.starfarer.api.impl.campaign.missions.cb.CBStats;
import com.fs.starfarer.api.impl.campaign.missions.cb.CustomBountyCreator;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers.FleetQuality;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers.FleetSize;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers.OfficerNum;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers.OfficerQuality;

public class RSContactBountyManager extends BaseCustomBountyCreator {
    public static float PROB_IN_SYSTEM_WITH_BASE = 0.5F;
    public static int MIN_LIGMA_DROP = 3;
    public static int MAX_LIGMA_DROP = 5;
    public static int MIN_SDS_DROP = 1;
    public static int MAX_SDS_DROP = 2;

    public RSContactBountyManager() {
    }

    public float getFrequency(HubMissionWithBarEvent mission, int difficulty) {
        return super.getFrequency(mission, difficulty);
    }

    public String getBountyNamePostfix(HubMissionWithBarEvent mission, CustomBountyCreator.CustomBountyData data) {
        return " - 遗弃的十字军计划失控单元";
    }

    public String getIconName() {
        return Global.getSettings().getSpriteName("campaignMissions", "rs_CinisOfCrusaders_bounty");
    }

    public CustomBountyCreator.CustomBountyData createBounty(MarketAPI createdAt, HubMissionWithBarEvent mission, int difficulty, Object bountyStage) {
        CustomBountyCreator.CustomBountyData data = new CustomBountyCreator.CustomBountyData();
        data.difficulty = difficulty;

        // 设置星系偏好标签并选取星系
        mission.preferSystemTags(ReqMode.ANY, "theme_derelict", "theme_core_unpopulated", "theme_remnant_no_fleets", "theme_interesting");
        data.system = mission.pickSystem();
        // 关键空检查：若没有合适的星系则放弃该赏金
        if (data.system == null) {
            return null;
        }

        // 根据难度配置舰队参数
        HubMissionWithTriggers.FleetSize size = FleetSize.LARGE;
        HubMissionWithTriggers.FleetQuality fleetDanger = FleetQuality.SMOD_1;
        HubMissionWithTriggers.OfficerQuality officerFuckyou = OfficerQuality.HIGHER;
        HubMissionWithTriggers.OfficerNum officerCount = OfficerNum.MORE;
        String fleetType = "patrolLarge";

        if (difficulty <= 0) {
            size = FleetSize.SMALL;
            fleetDanger = FleetQuality.VERY_HIGH;
            officerFuckyou = OfficerQuality.DEFAULT;
            fleetType = "patrolSmall";
        } else if (difficulty <= 4) {
            size = FleetSize.MEDIUM;
            fleetDanger = FleetQuality.VERY_HIGH;
            fleetType = "patrolMedium";
        } else if (difficulty <= 9) {
            size = FleetSize.LARGER;
            fleetDanger = FleetQuality.SMOD_2;
            officerFuckyou = OfficerQuality.UNUSUALLY_HIGH;
            fleetType = "patrolLarge";
        } else if (difficulty <= 20) {
            size = FleetSize.VERY_LARGE;
            fleetDanger = FleetQuality.SMOD_3;
            officerFuckyou = OfficerQuality.UNUSUALLY_HIGH;
            fleetType = "taskForce";
        }

        // 开始创建舰队（参考 Nex_CBHegInspector 的顺序）
        this.beginFleet(mission, data);

        // 配置舰队各种触发器
        mission.triggerCreateFleet(size, fleetDanger, "cinis_of_crusaders", fleetType, data.system);
        mission.triggerSetFleetOfficers(officerCount, officerFuckyou);
        mission.triggerAutoAdjustFleetSize(size, size.next());
        mission.setRepFactionChangesHigh();

        // 可选：添加战利品掉落（原代码已注释，保留注释）
        // mission.triggerAddCommodityDrop("istl_sigma_matter1", MathUtils.getRandomNumberInRange(MIN_LIGMA_DROP, MAX_LIGMA_DROP), true);
        // mission.triggerAddCommodityDrop("istl_sigma_matter2", MathUtils.getRandomNumberInRange(MIN_LIGMA_DROP, MAX_LIGMA_DROP), true);
        // mission.triggerAddCommodityDrop("istl_sigma_matter3", MathUtils.getRandomNumberInRange(MIN_LIGMA_DROP, MAX_LIGMA_DROP), true);
        // mission.triggerAddCommodityDrop("istl_securedata", MathUtils.getRandomNumberInRange(MIN_SDS_DROP, MAX_SDS_DROP), true);

        mission.triggerSetWarFleet();
        mission.triggerFleetAllowLongPursuit();

        // 选择生成位置并生成舰队（原代码重复两次，现仅触发一次）
        mission.triggerPickLocationAtInSystemJumpPoint(data.system);
        mission.triggerSpawnFleetAtPickedLocation(null, null);

        mission.triggerFleetAddCommanderSkill("flux_regulation", 1);
        mission.triggerFleetAddCommanderSkill("electronic_warfare", 1);
        mission.triggerFleetAddCommanderSkill("coordinated_maneuvers", 1);
        mission.triggerFleetSetName("Incursion Fleet");
        mission.triggerFleetSetPatrolActionText("looking for a prey");
        mission.triggerFleetSetTravelActionText("looking for a prey");
        mission.triggerOrderFleetPatrol(data.system, true, "jump_point", "salvageable", "planet", "gas_giant");
        mission.triggerMakeHostileAndAggressive();
        mission.triggerSetGlobalMemoryValue("$cfai_makeHostile", true);
        mission.triggerSetGlobalMemoryValue("$cfai_longPursuit", true);
        mission.triggerSetGlobalMemoryValue("$cfai_makeAlwaysPursue", true);
        mission.triggerSetGlobalMemoryValue("$cfai_ignoredByOtherFleets", true);
        mission.triggerSetGlobalMemoryValue("$core_fightToTheLast", true);
        mission.triggerSetFleetFlag("$core_fightToTheLast");
        mission.triggerSetFleetFlag("$core_fightToTheLast", true);

        // 实际创建舰队并检查有效性
        data.fleet = this.createFleet(mission, data);
        if (data.fleet == null) {
            return null;
        }

        // 设置赏金奖励和声望变化
        this.setRepChangesBasedOnDifficulty(data, difficulty);
        data.baseReward = CBStats.getBaseBounty(difficulty, 3.0F, mission);
        return data;
    }
}