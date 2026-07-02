package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

import exerelin.utilities.StringHelper;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 关键：添加导入
import com.fs.starfarer.api.campaign.SpecialItemData;

public class RSBluePrintExchange extends PaginatedOptions {

    public static final String PREFIX_CR = "cr_";
    public static final String TAG_MAU = "crusaders";
    public static final String OPTION_PREFIX = "RS_BPE_pick_";
    public static final String SELECTED_MEMBER_KEY = "$RS_BPE_selectedMember";

    protected CampaignFleetAPI playerFleet;
    protected CargoAPI playerCargo;
    protected FactionAPI playerFaction;
    protected SectorEntityToken entity;
    protected MarketAPI market;
    protected TextPanelAPI text;
    protected PersonAPI person;
    protected FactionAPI faction;

    protected List<String> disabledOpts = new ArrayList<>();
    protected List<FleetMemberAPI> eligibleMembers = new ArrayList<>();

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
        String arg = params.get(0).getString(memoryMap);
        setupVars(dialog, memoryMap);

        switch (arg) {
            case "init":
                break;
            case "hasOption":
                return hasEligibleShips();
            case "getExchangeOptions":
                setupDelegateDialog(dialog);
                populateExchangeOptions();
                showOptions();
                break;
            case "previewExchange":
                int index = Integer.parseInt(memoryMap.get(MemKeys.LOCAL).getString("$option").substring(OPTION_PREFIX.length()));
                previewExchange(index, dialog);
                break;
            case "confirmExchange":
                confirmExchange(dialog, memoryMap);
                break;
        }
        return true;
    }

    protected void setupVars(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        this.dialog = dialog;
        this.memoryMap = memoryMap;
        entity = dialog.getInteractionTarget();
        market = entity.getMarket();
        text = dialog.getTextPanel();
        playerFleet = Global.getSector().getPlayerFleet();
        playerCargo = playerFleet.getCargo();
        playerFaction = Global.getSector().getPlayerFaction();
        person = dialog.getInteractionTarget().getActivePerson();
        faction = person != null ? person.getFaction() : null;
    }

    protected void setupDelegateDialog(InteractionDialogAPI dialog) {
        originalPlugin = dialog.getPlugin();
        dialog.setPlugin(this);
        init(dialog);
    }

    @Override
    public void showOptions() {
        super.showOptions();
        for (String optId : disabledOpts) {
            dialog.getOptionPanel().setEnabled(optId, false);
        }
        dialog.getOptionPanel().setShortcut("RS_BPE_return", Keyboard.KEY_ESCAPE, false, false, false, false);
    }

    protected boolean hasEligibleShips() {
        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            if (isEligibleForExchange(member)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isEligibleForExchange(FleetMemberAPI member) {
        if (member.isFlagship()) return false;
        if (Misc.isUnremovable(member.getCaptain())) return false;
        if (playerFleet.getNumShips() <= 1) return false;

        String hullId = member.getHullId();
        if (!hullId.startsWith(PREFIX_CR)) return false;

        ShipHullSpecAPI hullSpec = member.getHullSpec();
        if (!hullSpec.hasTag(TAG_MAU)) return false;

        if (playerFaction.knowsShip(hullId)) return false;

        return true;
    }

    protected void populateExchangeOptions() {
        dialog.getOptionPanel().clearOptions();
        disabledOpts.clear();
        eligibleMembers.clear();

        List<FleetMemberAPI> members = playerFleet.getFleetData().getMembersListCopy();
        int index = 0;
        for (FleetMemberAPI member : members) {
            if (!isEligibleForExchange(member)) continue;

            eligibleMembers.add(member);
            String optId = OPTION_PREFIX + index;
            String hullName = member.getHullSpec().getHullNameWithDashClass();
            String str = "交换 " + hullName + " 的蓝图";
            addOption(str, optId);
            index++;
        }
        addOptionAllPages(StringHelper.getString("back", true), "RS_BPE_return");
    }

    protected void previewExchange(int index, InteractionDialogAPI dialog) {
        if (index < 0 || index >= eligibleMembers.size()) return;
        FleetMemberAPI member = eligibleMembers.get(index);
        memoryMap.get(MemKeys.LOCAL).set(SELECTED_MEMBER_KEY, member, 0);

        TextPanelAPI text = dialog.getTextPanel();
        ShipHullSpecAPI hullSpec = member.getHullSpec();
        Description desc = Global.getSettings().getDescription(hullSpec.getDescriptionId(), Description.Type.SHIP);

        dialog.getVisualPanel().showFleetMemberInfo(member, true);

        text.setFontSmallInsignia();
        text.addPara("确认用 " + member.getHullSpec().getHullNameWithDashClass() + " 交换其蓝图？");
        text.addPara(desc.getText1FirstPara());
        text.setFontInsignia();
    }

    protected void confirmExchange(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        FleetMemberAPI member = (FleetMemberAPI) memoryMap.get(MemKeys.LOCAL).get(SELECTED_MEMBER_KEY);
        if (member == null) return;

        String hullId = member.getHullId();

        // 从舰队移除舰船 —— 调用官方 API
        playerFleet.getFleetData().removeFleetMember(member);

        // 添加蓝图到货舱 —— 使用导入的 SpecialItemData
        SpecialItemData blueprint = new SpecialItemData(Items.SHIP_BP, hullId);
        playerCargo.addSpecial(blueprint, 1);

        // 在文本面板显示结果（可选，因为 CSV 中也有“交换完成。”文本）
        text.setFontSmallInsignia();
        text.addPara("获得蓝图：" + member.getHullSpec().getHullNameWithDashClass(),
                Misc.getPositiveHighlightColor(), Misc.getHighlightColor(),
                member.getHullSpec().getHullNameWithDashClass());
        text.setFontInsignia();

        // 清理内存
        memoryMap.get(MemKeys.LOCAL).unset(SELECTED_MEMBER_KEY);
    }
}