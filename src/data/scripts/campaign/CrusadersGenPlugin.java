//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package data.scripts.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import java.util.HashSet;
import java.util.Set;

public class CrusadersGenPlugin extends BasicGen {
    public CrusadersGenPlugin(SectorAPI sector) {
    }

    public void afterEconomyLoad(SectorAPI sector) {
        FactionAPI CR= sector.getFaction("cinis_of_crusaders");
        Set<String> corvusSpawnPoints = new HashSet<>();




        for(FactionAPI faction : Global.getSector().getAllFactions()) {
            String factionId = faction.getId();
            if (!factionId.equals("cinis_of_crusaders")) {
                CR.setRelationship(factionId, -0.85F);
            }
        }

    }

}
