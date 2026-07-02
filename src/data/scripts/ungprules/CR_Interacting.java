package data.scripts.ungprules;

import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import ungp.api.rules.UNGP_BaseRuleEffect;
import ungp.api.rules.tags.UNGP_CombatTag;
import ungp.api.rules.tags.UNGP_PlayerShipSkillTag;
import ungp.scripts.campaign.specialist.UNGP_SpecialistSettings.Difficulty;

/**
 * 规则效果：猎杀阵线
 * 在战斗中，敌方的驱逐舰和护卫舰受到伤害减少15%、造成伤害增加15%、幅能容量和耗散增加15%。
 * 同时，玩家方的驱逐舰和护卫舰获得部署点减免和电网增强，带"MS"标签的舰船效果翻倍。
 */
public class CR_Interacting extends UNGP_BaseRuleEffect implements UNGP_CombatTag , UNGP_PlayerShipSkillTag {

	public float BOUNS = 0.05f;
	public float AD_BOUNS = 0.1f;

	@Override
	public void updateDifficultyCache(Difficulty difficulty) {
	}

	@Override
	public void advanceInCombat(CombatEngineAPI engine, float amount) {}

	@Override
	public void applyEnemyShipInCombat(float amount, ShipAPI enemy) {
		// 此处留空，因为已在 applyEffectsToEnemyShip 中处理
	}

	@Override
	public void applyPlayerShipInCombat(float amount, CombatEngineAPI engine, ShipAPI ship) {
		// 仅对驱逐舰和护卫舰生效
		ShipAPI.HullSize size = ship.getHullSize();
		if (size != ShipAPI.HullSize.FRIGATE && size != ShipAPI.HullSize.DESTROYER) {
			return;
		}

		// 检查是否带有 "MS" 标签
		boolean hasTag = false;
		FleetMemberAPI member = ship.getFleetMember();
		if (member != null) {
			hasTag = member.getHullSpec().getTags().contains("crusaders_ma") || member.getHullSpec().getTags().contains("moci_ms");
		} else {
			// 如果没有FleetMember（如模拟战），尝试从变体标签获取
			if (ship.getVariant() != null || ship.getHullSpec() != null) {
				hasTag = ship.getHullSpec().getTags().contains("crusaders_ma") || ship.getHullSpec().getTags().contains("moci_ms");
			}
		}

		float boostPercent = hasTag ? 10f : 5f;     // 电网增强百分比

		MutableShipStatsAPI stats = ship.getMutableStats();


		// 增强电网：幅能容量和耗散
		stats.getFluxCapacity().modifyPercent(buffID, boostPercent);
		stats.getFluxDissipation().modifyPercent(buffID, boostPercent);

		// 标记已应用
		ship.setCustomData(buffID + "_player_applied", true);
	}

	@Override
	public String getDescriptionParams(int index, Difficulty difficulty) {
		if (index == 0) return getPercentString(BOUNS * 200);
		if (index == 1) return getPercentString(BOUNS * 100);
		if (index == 2) return getPercentString(AD_BOUNS * 200);
		if (index == 3) return getPercentString(AD_BOUNS * 100);
		return null;
	}

	@Override
	public void apply(FleetDataAPI fleetDataAPI, FleetMemberAPI member, MutableShipStatsAPI stats, ShipAPI.HullSize hullSize) {
		if (hullSize != ShipAPI.HullSize.FRIGATE && hullSize != ShipAPI.HullSize.DESTROYER) return;

		// 防止 member 为 null 导致 NPE
		if (member == null) return;

		boolean hasTag = member.getHullSpec().getTags().contains("crusaders_ma") ||
				member.getHullSpec().getTags().contains("moci_ms");

		float reductionPercent = hasTag ? 0.2f : 0.1f;
		float baseDP = stats.getSuppliesToRecover().getBaseValue();
		int reduction = (int) Math.ceil(baseDP * reductionPercent);
		stats.getDynamic().getMod("deployment_points_mod").modifyFlat(buffID, -reduction);
	}

	@Override
	public void unapply(MutableShipStatsAPI mutableShipStatsAPI, ShipAPI.HullSize hullSize) {

	}
}