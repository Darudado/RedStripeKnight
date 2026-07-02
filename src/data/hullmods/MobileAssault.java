package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

public class MobileAssault extends BaseHullMod {
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.setCollisionClass(CollisionClass.FIGHTER);
    }
    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getHullDamageTakenMult().modifyMult(id,1.35f);
        stats.getEmpDamageTakenMult().modifyMult(id,1.25f);
        stats.getShieldDamageTakenMult().modifyMult(id,1.1f);
    }

    public void advanceInCampaign(FleetMemberAPI member, float amount) {
        // 创建Gamma级AI核心
        String aiCoreId = "gamma_core";
        PersonAPI aiCore = Global.getFactory().createPerson();


        // 配置AI核心属性
        aiCore.setAICoreId(aiCoreId);
        aiCore.setPortraitSprite("graphics/portraits/portrait_ai1b.png"); // 指定AI头像
        aiCore.getStats().setLevel(3); // 设置AI等级为3
        aiCore.getStats().setSkillLevel("target_analysis", 2.0F);   // 目标分析技能2级
        aiCore.getStats().setSkillLevel("helmsmanship", 2.0F);       // 操舵术技能2级
        aiCore.getStats().setSkillLevel("combat_endurance", 2.0F);  // 战斗耐力技能2级
        aiCore.setPersonality("reckless");  // 设置为"鲁莽"性格
        aiCore.setRankId(Ranks.SPACE_CAPTAIN); // 军衔设为太空上尉
        aiCore.setPostId(null); // 无具体职位
        member.setCaptain(aiCore);
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        // 创建Gamma级AI核心
        String aiCoreId = "gamma_core";
        PersonAPI aiCore = Global.getFactory().createPerson();

        // 设置AI所属势力（与母舰相同）
        String factionId = "player";
        if (ship != null && ship.getFleetMember() != null &&ship.getFleetMember().getFleetData()!= null/* 嵌套null检查 */) {
            factionId = ship.getFleetMember().getFleetData().getFleet().getFaction().getId();
        }
        aiCore.setFaction(factionId);

        // 配置AI核心属性
        aiCore.setAICoreId(aiCoreId);
        aiCore.setPortraitSprite("graphics/portraits/portrait_ai1b.png"); // 指定AI头像
        aiCore.getStats().setLevel(3); // 设置AI等级为3
        aiCore.getStats().setSkillLevel("target_analysis", 2.0F);   // 目标分析技能2级
        aiCore.getStats().setSkillLevel("helmsmanship", 2.0F);       // 操舵术技能2级
        aiCore.getStats().setSkillLevel("combat_endurance", 2.0F);  // 战斗耐力技能2级
        aiCore.setPersonality("reckless");  // 设置为"鲁莽"性格
        aiCore.setRankId(Ranks.SPACE_CAPTAIN); // 军衔设为太空上尉
        aiCore.setPostId(null); // 无具体职位
        if (ship != null) {
            ship.setCaptain(aiCore);
            ship.setCollisionClass(CollisionClass.FIGHTER);
        }
    }
}