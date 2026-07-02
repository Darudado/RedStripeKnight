package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RSdataMapRead extends BaseCommandPlugin {
    public RSdataMapRead() {
    }

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) {
            return false;
        } else if (!Global.getSector().getMemory().contains("$RS_datamap")) {
            //dialog.getTextPanel().addPara("error");
            return false;
        } else {
            String str = ((Misc.Token)params.get(0)).getString(memoryMap);
            if (str == null) {
                return false;
            } else {
                HashMap map = (HashMap)Global.getSector().getMemory().get("$RS_datamap");
                boolean key;
                if (!(map.get(str) instanceof Boolean)) {
                    return false;
                } else {
                    key = (Boolean)map.get(str);
                    return key;
                }
            }
        }
    }
}