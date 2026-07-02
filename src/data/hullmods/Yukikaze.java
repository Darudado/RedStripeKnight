package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

public class Yukikaze extends BaseHullMod {


    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id ,ShipAPI ship) {
        stats.getEngineHealthBonus().modifyPercent(id,50f);
        stats.getSensorProfile().modifyPercent(id,25f);
        stats.getSensorStrength().modifyPercent(id,25f);
        stats.getEccmChance().modifyPercent(id,50f);
        ship.setCollisionClass(CollisionClass.FIGHTER);
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id ,FleetMemberAPI member) {
        ship.setCollisionClass(CollisionClass.FIGHTER);

        String aiCoreId = "omega_core";
        PersonAPI aiCore = Global.getFactory().createPerson();

        // 设置AI所属势力（与母舰相同）
        String factionId = "player";
        aiCore.setFaction(factionId);

        member.setCaptain(aiCore);

        // 配置AI核心属性
        aiCore.setAICoreId(aiCoreId);
        aiCore.setPortraitSprite("graphics/portraits/portrait_ai1b.png"); // 指定AI头像
        aiCore.getStats().setLevel(9); // 设置AI等级为3
        aiCore.getStats().setSkillLevel("target_analysis", 2.0F);   // 目标分析技能2级
        aiCore.getStats().setSkillLevel("helmsmanship", 2.0F);       // 操舵术技能2级
        aiCore.getStats().setSkillLevel("combat_endurance", 2.0F);  // 战斗耐力技能2级
        aiCore.getStats().setSkillLevel("systems_expertise", 2.0F);
        aiCore.getStats().setSkillLevel("gunnery_implants", 2.0F);
        aiCore.getStats().setSkillLevel("energy_weapon_mastery", 2.0F);
        aiCore.getStats().setSkillLevel("damage_control", 2.0F);
        aiCore.getStats().setSkillLevel("missile_specialization", 2.0F);
        aiCore.getStats().setSkillLevel("point_defense", 2.0F);
        aiCore.setPersonality("reckless");  // 设置为"鲁莽"性格
        aiCore.setRankId(Ranks.SPACE_CAPTAIN); // 军衔设为太空上尉
        aiCore.setPostId(null); // 无具体职位
    }


    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        String aiCoreId = "omega_core";
        PersonAPI aiCore = Global.getFactory().createPerson();

        // 设置AI所属势力（与母舰相同）
        String factionId = "player";
        aiCore.setFaction(factionId);

        member.setCaptain(aiCore);

        // 配置AI核心属性
        aiCore.setAICoreId(aiCoreId);
        aiCore.setPortraitSprite("graphics/portraits/portrait_ai1b.png"); // 指定AI头像
        aiCore.getStats().setLevel(9); // 设置AI等级为3
        aiCore.getStats().setSkillLevel("target_analysis", 2.0F);   // 目标分析技能2级
        aiCore.getStats().setSkillLevel("helmsmanship", 2.0F);       // 操舵术技能2级
        aiCore.getStats().setSkillLevel("combat_endurance", 2.0F);  // 战斗耐力技能2级
        aiCore.getStats().setSkillLevel("systems_expertise", 2.0F);
        aiCore.getStats().setSkillLevel("gunnery_implants", 2.0F);
        aiCore.getStats().setSkillLevel("energy_weapon_mastery", 2.0F);
        aiCore.getStats().setSkillLevel("damage_control", 2.0F);
        aiCore.getStats().setSkillLevel("missile_specialization", 2.0F);
        aiCore.getStats().setSkillLevel("point_defense", 2.0F);
        aiCore.setPersonality("reckless");  // 设置为"鲁莽"性格
        aiCore.setRankId(Ranks.SPACE_CAPTAIN); // 军衔设为太空上尉
        aiCore.setPostId(null); // 无具体职位

    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship != null && ship.getSystem() != null) {
            boolean systemActive = ship.getSystem().isActive();
            boolean systemChargeup = ship.getSystem().isChargeup();
            boolean systemChargedown = ship.getSystem().isChargedown();
            boolean Cooldown =ship.getSystem().isCoolingDown();
            boolean On=ship.getSystem().isOn();
            boolean Alive= ship.isAlive();
            if (systemActive && systemChargeup && systemChargedown && Cooldown && On && Alive) {
                ship.setCollisionClass(CollisionClass.FIGHTER);
            }
        }

        if (ship != null) {
            String aiCoreId = "omega_core";
            PersonAPI aiCore = Global.getFactory().createPerson();


            // 设置AI所属势力（与母舰相同）
            String factionId = "player";
            if (ship != null && ship.getFleetMember() != null && ship.getFleetMember().getFleetData() != null/* 嵌套null检查 */) {
                factionId = ship.getFleetMember().getFleetData().getFleet().getFaction().getId();
            }
            aiCore.setFaction(factionId);

            // 配置AI核心属性
            aiCore.setAICoreId(aiCoreId);
            aiCore.setPortraitSprite("graphics/portraits/portrait_ai1b.png"); // 指定AI头像
            aiCore.getStats().setLevel(9); // 设置AI等级为3
            aiCore.getStats().setSkillLevel("target_analysis", 2.0F);   // 目标分析技能2级
            aiCore.getStats().setSkillLevel("helmsmanship", 2.0F);       // 操舵术技能2级
            aiCore.getStats().setSkillLevel("combat_endurance", 2.0F);  // 战斗耐力技能2级
            aiCore.getStats().setSkillLevel("systems_expertise", 2.0F);
            aiCore.getStats().setSkillLevel("gunnery_implants", 2.0F);
            aiCore.getStats().setSkillLevel("energy_weapon_mastery", 2.0F);
            aiCore.getStats().setSkillLevel("damage_control", 2.0F);
            aiCore.getStats().setSkillLevel("missile_specialization", 2.0F);
            aiCore.getStats().setSkillLevel("point_defense", 2.0F);
            aiCore.setPersonality("reckless");  // 设置为"鲁莽"性格
            aiCore.setRankId(Ranks.SPACE_CAPTAIN); // 军衔设为太空上尉
            aiCore.setPostId(null); // 无具体职位

            ship.setCaptain(aiCore);
            ship.setCollisionClass(CollisionClass.FIGHTER);
        }
    }

}