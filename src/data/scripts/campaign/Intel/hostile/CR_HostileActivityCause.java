package data.scripts.campaign.Intel.hostile;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityCause2;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;

public class CR_HostileActivityCause extends BaseHostileActivityCause2{

    public CR_HostileActivityCause(HostileActivityEventIntel intel) {
        super(intel);
    }
}