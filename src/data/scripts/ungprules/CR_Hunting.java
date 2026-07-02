package data.scripts.ungprules;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import ungp.api.rules.UNGP_BaseRuleEffect;
import ungp.api.rules.tags.UNGP_CampaignTag;
import ungp.api.rules.tags.UNGP_CombatTag;
import ungp.scripts.campaign.everyframe.UNGP_CampaignPlugin;
import ungp.scripts.campaign.specialist.UNGP_SpecialistSettings.Difficulty;

/**
 * 规则效果：猎杀阵线
 * 在战斗中，敌方的驱逐舰和护卫舰受到伤害减少15%、造成伤害增加15%、幅能容量和耗散增加15%。
 */
public class CR_Hunting extends UNGP_BaseRuleEffect implements UNGP_CampaignTag, UNGP_CombatTag {

	public static final float BOUNS = 0.15f;
	public static final float BOUNS_GAP= 0.025f;

	@Override
	public void updateDifficultyCache(Difficulty difficulty) {
    }
	@Override
	public void advanceInCombat(CombatEngineAPI engine, float amount) {}

	/**
	 * 对单艘敌舰施加规则效果
	 */
	private void applyEffectsToEnemyShip(ShipAPI enemy) {
		// 仅对驱逐舰和护卫舰生效
		ShipAPI.HullSize size = enemy.getHullSize();
		float DP = enemy.getMutableStats().getSuppliesToRecover().getBaseValue();
		if (size != ShipAPI.HullSize.FRIGATE && size != ShipAPI.HullSize.DESTROYER) {
			return;
		}

		MutableShipStatsAPI stats = enemy.getMutableStats();

		if(DP > 0  && DP <= 8){
			stats.getHullDamageTakenMult().modifyMult(buffID, 1-BOUNS+BOUNS_GAP*2);
			stats.getArmorDamageTakenMult().modifyMult(buffID, 1-BOUNS+BOUNS_GAP*2);
			stats.getShieldDamageTakenMult().modifyMult(buffID, 1-BOUNS+BOUNS_GAP*2);
			stats.getEmpDamageTakenMult().modifyMult(buffID, 1-BOUNS*2+BOUNS_GAP*2);

			// 造成伤害增加15% (乘数1.15)
			stats.getDamageToTargetHullMult().modifyMult(buffID, 1+BOUNS-BOUNS_GAP*2);
			stats.getDamageToTargetShieldsMult().modifyMult(buffID, 1+BOUNS-BOUNS_GAP*2);
			stats.getDamageToTargetEnginesMult().modifyMult(buffID, 1+BOUNS-BOUNS_GAP*2);
			stats.getDamageToTargetWeaponsMult().modifyMult(buffID, 1+BOUNS*2-BOUNS_GAP*2);

			// 幅能容量和耗散增加15% (乘数1.15)
			stats.getFluxCapacity().modifyMult(buffID, 1+BOUNS-BOUNS_GAP*2);
			stats.getFluxDissipation().modifyMult(buffID, 1+BOUNS-BOUNS_GAP*2);
		}

		if(DP > 9 && DP <= 16 ){
			stats.getHullDamageTakenMult().modifyMult(buffID, 1-BOUNS+BOUNS_GAP);
			stats.getArmorDamageTakenMult().modifyMult(buffID, 1-BOUNS+BOUNS_GAP);
			stats.getShieldDamageTakenMult().modifyMult(buffID, 1-BOUNS+BOUNS_GAP);
			stats.getEmpDamageTakenMult().modifyMult(buffID, 1-BOUNS*2+BOUNS_GAP);

			// 造成伤害增加15% (乘数1.15)
			stats.getDamageToTargetHullMult().modifyMult(buffID, 1+BOUNS-BOUNS_GAP);
			stats.getDamageToTargetShieldsMult().modifyMult(buffID, 1+BOUNS-BOUNS_GAP);
			stats.getDamageToTargetEnginesMult().modifyMult(buffID, 1+BOUNS-BOUNS_GAP);
			stats.getDamageToTargetWeaponsMult().modifyMult(buffID, 1+BOUNS*2-BOUNS_GAP);

			// 幅能容量和耗散增加15% (乘数1.15)
			stats.getFluxCapacity().modifyMult(buffID, 1+BOUNS-BOUNS_GAP);
			stats.getFluxDissipation().modifyMult(buffID, 1+BOUNS-BOUNS_GAP);
		}
		if(DP > 17 && DP <= 24 ){
			stats.getHullDamageTakenMult().modifyMult(buffID, 1-BOUNS);
			stats.getArmorDamageTakenMult().modifyMult(buffID, 1-BOUNS);
			stats.getShieldDamageTakenMult().modifyMult(buffID, 1-BOUNS);
			stats.getEmpDamageTakenMult().modifyMult(buffID, 1-BOUNS*2);

			// 造成伤害增加15% (乘数1.15)
			stats.getDamageToTargetHullMult().modifyMult(buffID, 1+BOUNS);
			stats.getDamageToTargetShieldsMult().modifyMult(buffID, 1+BOUNS);
			stats.getDamageToTargetEnginesMult().modifyMult(buffID, 1+BOUNS);
			stats.getDamageToTargetWeaponsMult().modifyMult(buffID, 1+BOUNS*2);

			// 幅能容量和耗散增加15% (乘数1.15)
			stats.getFluxCapacity().modifyMult(buffID, 1+BOUNS);
			stats.getFluxDissipation().modifyMult(buffID, 1+BOUNS);
		}

		if(DP > 25){
			stats.getHullDamageTakenMult().modifyMult(buffID, 1-BOUNS-BOUNS_GAP);
			stats.getArmorDamageTakenMult().modifyMult(buffID, 1-BOUNS-BOUNS_GAP);
			stats.getShieldDamageTakenMult().modifyMult(buffID, 1-BOUNS-BOUNS_GAP);
			stats.getEmpDamageTakenMult().modifyMult(buffID, 1-BOUNS*2-BOUNS_GAP);

			// 造成伤害增加15% (乘数1.15)
			stats.getDamageToTargetHullMult().modifyMult(buffID, 1+BOUNS+BOUNS_GAP);
			stats.getDamageToTargetShieldsMult().modifyMult(buffID, 1+BOUNS+BOUNS_GAP);
			stats.getDamageToTargetEnginesMult().modifyMult(buffID, 1+BOUNS+BOUNS_GAP);
			stats.getDamageToTargetWeaponsMult().modifyMult(buffID, 1+BOUNS*2+BOUNS_GAP);

			// 幅能容量和耗散增加15% (乘数1.15)
			stats.getFluxCapacity().modifyMult(buffID, 1+BOUNS+BOUNS_GAP);
			stats.getFluxDissipation().modifyMult(buffID, 1+BOUNS+BOUNS_GAP);
		}

		// 受到伤害减少15% (乘数0.85)
		stats.getHullDamageTakenMult().modifyMult(buffID, 1-BOUNS);
		stats.getArmorDamageTakenMult().modifyMult(buffID, 1-BOUNS);
		stats.getShieldDamageTakenMult().modifyMult(buffID, 1-BOUNS);
		stats.getEmpDamageTakenMult().modifyMult(buffID, 1-BOUNS*2);

		// 造成伤害增加15% (乘数1.15)
		stats.getDamageToTargetHullMult().modifyMult(buffID, 1+BOUNS);
		stats.getDamageToTargetShieldsMult().modifyMult(buffID, 1+BOUNS);
		stats.getDamageToTargetEnginesMult().modifyMult(buffID, 1+BOUNS);
		stats.getDamageToTargetWeaponsMult().modifyMult(buffID, 1+BOUNS*2);

		// 幅能容量和耗散增加15% (乘数1.15)
		stats.getFluxCapacity().modifyMult(buffID, 1+BOUNS);
		stats.getFluxDissipation().modifyMult(buffID, 1+BOUNS);


		// 标记已应用
		enemy.setCustomData(buffID + "_applied", true);
	}

	@Override
	public void applyEnemyShipInCombat(float amount, ShipAPI enemy) {
		// 这个方法可能被某些框架调用，但我们已在advanceInCombat中统一处理，此处可留空或保留兼容调用
		applyEffectsToEnemyShip(enemy);
	}

	@Override
	public void applyPlayerShipInCombat(float v, CombatEngineAPI combatEngineAPI, ShipAPI shipAPI) {

	}

	@Override
	public String getDescriptionParams(int index, Difficulty difficulty) {
		if (index == 0) return getPercentString((BOUNS-BOUNS_GAP*2)*100);
		if (index == 1) return getPercentString((BOUNS-BOUNS_GAP)*100);
		if (index == 2) return getPercentString(BOUNS*100);
		if (index == 3) return getPercentString((BOUNS+BOUNS_GAP)*100);
		if (index == 4) return getPercentString((BOUNS-BOUNS_GAP*2)*200);
		if (index == 5) return getPercentString((BOUNS-BOUNS_GAP)*200);
		if (index == 6) return getPercentString(BOUNS*200);
		if (index == 7) return getPercentString((BOUNS+BOUNS_GAP)*200);
		if (index == 8) return getPercentString((BOUNS-BOUNS_GAP*2)*100);
		if (index == 9) return getPercentString((BOUNS-BOUNS_GAP)*100);
		if (index == 10) return getPercentString(BOUNS*100);
		if (index == 11) return getPercentString((BOUNS+BOUNS_GAP)*100);
		if (index == 12) return getPercentString((BOUNS-BOUNS_GAP*2)*100);
		if (index == 13) return getPercentString((BOUNS-BOUNS_GAP)*100);
		if (index == 14) return getPercentString(BOUNS*100);
		if (index == 15) return getPercentString((BOUNS+BOUNS_GAP)*100);
		return null;
	}

	@Override
	public void advanceInCampaign(float v, UNGP_CampaignPlugin.TempCampaignParams tempCampaignParams) {
		// 留空，由UNGP_CampaignTag要求实现
	}

}