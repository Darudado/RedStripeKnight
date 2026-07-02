package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RSmpaPut extends BaseCommandPlugin {
    public RSmpaPut() {
    }

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null ) {
            return false;
        }
        else if (!Global.getSector().getMemory().contains("$RS_datamap")) {
            return false;
        } else {
            String str = (params.get(0)).getString(memoryMap);
            boolean key = (params.get(1)).getBoolean(memoryMap);
            if (str == null) {
                return false;
            } else {
                HashMap map = (HashMap)Global.getSector().getMemory().get("$RS_datamap");
                map.put(str, key);
                Global.getSector().getMemory().set("$RS_datamap", map);
                return true;
            }
        }

    }
}
