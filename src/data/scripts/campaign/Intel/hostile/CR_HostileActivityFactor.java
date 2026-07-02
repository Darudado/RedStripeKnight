package data.scripts.campaign.Intel.hostile;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;

public class CR_HostileActivityFactor extends BaseHostileActivityFactor implements FleetGroupIntel.FGIEventListener {

    public CR_HostileActivityFactor(HostileActivityEventIntel intel) {
        super(intel);
    }

    @Override
    public void reportFGIAborted(FleetGroupIntel intel) {

    }
}