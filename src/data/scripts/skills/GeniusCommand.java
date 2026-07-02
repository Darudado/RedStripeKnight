package data.scripts.skills;

import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.characters.PersonAPI;

public class GeniusCommand  {
    public static final float FLEET_DP_REDUCTION = 15f; // 15%
    // 军官所在舰船额外部署点减免
    public static final float OFFICER_SHIP_DP_REDUCTION = 50f; // 50%

    public static class Level0 implements ShipSkillEffect {
        @Override
        public String getEffectDescription(float level) {
            return "Reduce overall fleet deployment point requirements" + FLEET_DP_REDUCTION + "%";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.FLEET;
        }

        @Override
        public void apply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id, float level) {
            stats.getDynamic().getMod("deployment_points_mod").modifyPercent(id, -FLEET_DP_REDUCTION);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id) {
            stats.getDynamic().getMod("deployment_points_mod").unmodify(id);
        }
    }

    public static class Level1 implements ShipSkillEffect {

        @Override
        public void apply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id, float level) {
            // 获取舰队成员
            FleetMemberAPI member = stats.getFleetMember();
            if (member == null) return;

            // 检查舰船是否有军官指挥
            PersonAPI captain = member.getCaptain();
            if (captain == null || captain.isDefault()) return;

            if (member.getHullId().equals("rs_byzantine")) {
                // 应用部署点减免
                stats.getDynamic().getMod("deployment_points_mod").modifyPercent("command_ship", -OFFICER_SHIP_DP_REDUCTION);
            }
            else if (member.getHullId().equals("rs_Tr_6_Inle")) {
                // 应用部署点减免
                stats.getDynamic().getMod("deployment_points_mod").modifyPercent("command_ship", -OFFICER_SHIP_DP_REDUCTION/1.5f);
            }
            else if (member.getHullId().equals("rs_bethlehem")) {
                // 应用部署点减免
                stats.getDynamic().getMod("deployment_points_mod").modifyPercent("command_ship", -OFFICER_SHIP_DP_REDUCTION/2f);
            }
            else{
                stats.getDynamic().getMod("deployment_points_mod").modifyPercent("command_ship", -FLEET_DP_REDUCTION);
            }

        }

        @Override
        public void unapply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id) {
            stats.getDynamic().getMod("deployment_points_mod").unmodify("command_ship");
        }

        @Override
        public String getEffectDescription(float level) {
            return "Ship exemption" + OFFICER_SHIP_DP_REDUCTION + "% Deployment point requirements";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return null;
        }
    }

    public static class Level2 implements ShipSkillEffect {

        @Override
        public void apply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id, float level) {
            stats.getFluxDissipation().modifyMult(id ,1.25f);
            stats.getFighterRefitTimeMult().modifyMult(id, 0.75f);
            stats.getCriticalMalfunctionDamageMod().modifyMult(id, 0.75f);
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id) {
            stats.getFluxDissipation().unmodify(id);
            stats.getFighterRefitTimeMult().unmodify(id);
            stats.getCriticalMalfunctionDamageMod().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "Increase the radiant energy dissipation rate of the entire fleet by 25% and reduce fighter aircraft maintenance efficiency by 25%";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return null;
        }
    }
}