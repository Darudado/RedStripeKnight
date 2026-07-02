package data.scripts.campaign.Intel.hostile;

import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.intel.group.BlockadeFGI;
import com.fs.starfarer.api.impl.campaign.intel.group.FGBlockadeAction;

public class CR_HostileActivityExpedition extends BlockadeFGI implements EconomyTickListener {

    public CR_HostileActivityExpedition(GenericRaidParams params, FGBlockadeAction.FGBlockadeParams blockadeParams) {
        super(params, blockadeParams);
    }

    @Override
    public void reportEconomyTick(int iterIndex) {

    }

    @Override
    public void reportEconomyMonthEnd() {

    }
}