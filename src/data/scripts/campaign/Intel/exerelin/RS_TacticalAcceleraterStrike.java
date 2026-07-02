package data.scripts.campaign.Intel.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.intel.groundbattle.GroundBattleAI;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;
import exerelin.campaign.intel.groundbattle.dialog.AbilityDialogPlugin.OptionId;
import exerelin.campaign.intel.groundbattle.plugins.FireSupportAbilityPlugin;
import exerelin.utilities.StringHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static data.campaign.econ.RS_DestructiveEffect.getLevel;

public class RS_TacticalAcceleraterStrike extends FireSupportAbilityPlugin {
    public static final float MORALE_LOSE_1 = -0.07F;
    public static final float MORALE_LOSE_2 = -0.1F;
    public static final float MORALE_LOSE_3 = -0.15F;
    public static final float DAMAGE_MULT_1 = 1.5F;
    public static final float DAMAGE_MULT_2 = 2.0F;
    public static final float DAMAGE_MULT_3 = 3.0F;
    protected transient Integer level = null;

    public RS_TacticalAcceleraterStrike() {
    }

    public void activate(InteractionDialogAPI dialog, PersonAPI user) {
        if (this.level == null) {
            // 硬编码：存在拜占庭时视为1级（可在此基础上修改伤害/士气倍率）
            this.level = hasShip(user.getFleet()) ? 1 : 0;
            if (this.level == 0) {
                // 理论上不会执行到这里，因为 getDisabledReason 已经阻止调用
                return;
            }
        }
        super.activate(dialog, user);

        float ml = this.getMoraleMod();

        for(GroundUnit unit : this.getIntel().getSide(!this.side.isAttacker()).getUnits()) {
            unit.modifyMorale(ml);
        }

        if (dialog != null) {
            dialog.getTextPanel().setFontSmallInsignia();
            dialog.getTextPanel().addPara("所有敌方单位士气降低 %s", Misc.getHighlightColor(), StringHelper.toPercent(-ml));
        }

    }

    public Pair<String, Map<String, Object>> getDisabledReason(PersonAPI user) {
        CampaignFleetAPI fleet = null;
        if (user != null) {
            if (user.isPlayer()) {
                fleet = Global.getSector().getPlayerFleet();
            } else {
                fleet = user.getFleet();
            }
        }

        if (getLevel(fleet) <= 0) {
            if (!hasShip(fleet)) {
                Map<String, Object> params = new HashMap<>();
                params.put("desc", "未携带拥有行星打击能力舰船");
            }
        }
            return super.getDisabledReason(user);
    }

    public static boolean hasShip(CampaignFleetAPI fleet) {
        if (fleet == null) return false;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isMothballed()) continue;
            if ("rs_byzantine".equals(member.getHullId())) return true;
        }
        return false;
    }

    public boolean showIfDisabled(Pair<String, Map<String, Object>> disableReason) {
        return hasShip(Global.getSector().getPlayerFleet());
    }

    public void dialogAddIntro(InteractionDialogAPI dialog) {
        TooltipMakerAPI tooltip = dialog.getTextPanel().beginTooltip();
        this.generateTooltip(tooltip);
        dialog.getTextPanel().addTooltip();
        boolean canAfford = dialog.getTextPanel().addCostPanel(null, "fuel", this.getFuelCost(Global.getSector().getPlayerFleet()), true);
        if (!canAfford) {
            dialog.getOptionPanel().setEnabled(OptionId.ACTIVATE, false);
        }

        this.addCooldownDialogText(dialog);
    }

    public void generateTooltip(TooltipMakerAPI tooltip) {
        tooltip.addPara("使用幽灵部队的行星歼灭舰船进行定点轰炸，相比普通轨道轰炸会消耗更多燃料并造成更强力的杀伤，具体效果取决于舰队中最大的行星歼灭舰船类型.", 0.0F);
        tooltip.addPara("消耗{%s}燃料对指定位置的敌方单位造成{%s}伤害，并使所有敌方单位士气降低{%s}.", 10.0F, Misc.getHighlightColor(), "" + this.getFuelCost(Global.getSector().getPlayerFleet()), "" + this.getDamage(), StringHelper.toPercent(-this.getMoraleMod()));
    }

    public int getFuelCost(CampaignFleetAPI fleet) {
        int marketSize = this.getIntel().getMarket().getSize();
        if (marketSize < 3) {
            marketSize = 3;
        }

        float cost = BASE_COST * 5 *(float)marketSize;
        cost = this.side.getBombardmentCostMod().computeEffective(cost);
        if (cost < 0.0F) {
            cost = 0.0F;
        }

        return Math.round(cost);
    }

    public int getDamage() {
        if (this.level == null) {
            this.level = getLevel(Global.getSector().getPlayerFleet());
        }

        if (this.level == 1) {
            return Math.round((float)super.getDamage() * 1.5F);
        } else if (this.level == 2) {
            return Math.round((float)super.getDamage() * 2.0F);
        } else {
            return this.level == 3 ? Math.round((float)super.getDamage() * 3.0F) : 0;
        }
    }

    public float getMoraleMod() {
        if (this.level == null) {
            this.level = getLevel(Global.getSector().getPlayerFleet());
        }

        if (this.level == 1) {
            return -0.07F;
        } else if (this.level == 2) {
            return -0.1F;
        } else {
            return this.level == 3 ? -0.15F : 0.0F;
        }
    }

    public float getAIUsePriority(GroundBattleAI ai) {
        return super.getAIUsePriority(ai) * 1.2F;
    }

    public boolean aiExecute(GroundBattleAI ai, PersonAPI user) {
        CampaignFleetAPI bomber = null;

        for (CampaignFleetAPI fleet : this.getIntel().getSupportingFleets(this.side.isAttacker())) {
            if (fleet.isPlayerFleet()) continue;
            if (fleet.getAI() != null && (fleet.getAI().isFleeing() || fleet.getAI().isMaintainingContact()))
                continue;
            if (fleet.getCargo().getMaxFuel() < (float) this.getFuelCost(fleet)) continue;

            if (hasShip(fleet)) {
                bomber = fleet;
                break;  // 找到第一个拥有拜占庭的舰队即可
            }
        }

        if (bomber == null) {
            return false;
        }

        List<IndustryForBattle> valid = this.getTargetIndustries();
        for (GroundBattleAI.IFBStrengthRecord desired : ai.getIndustriesWithEnemySorted()) {
            if (valid.contains(desired.industry)) {
                this.target = desired.industry;
                // 设置固定等级
                this.level = 1;  // 或使用 hasByzantium 再次确认后赋值
                this.activate((InteractionDialogAPI) null, bomber.getCommander());
                if (this.side.getIntel().shouldNotify()) {
                    this.playUISound();
                }
                return true;
            }
        }
        return false;
    }
}
