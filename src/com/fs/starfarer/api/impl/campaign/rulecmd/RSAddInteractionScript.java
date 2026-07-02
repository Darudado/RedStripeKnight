package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class RSAddInteractionScript extends BaseCommandPlugin {
    public RSAddInteractionScript() {
    }

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) {
            return false;
        }else {
            String id = ((Misc.Token)params.get(0)).getString(memoryMap);
            if (id.contentEquals("RSContactBounty")) {
                if (!Global.getSector().hasScript(RSContactBounty.class)) {
                    Global.getSector().addScript(new RSContactBounty(dialog));
                }

                return true;
            }else {
                return false;
            }
        }
    }
}