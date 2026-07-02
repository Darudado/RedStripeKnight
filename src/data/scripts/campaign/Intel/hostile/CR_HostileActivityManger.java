package data.scripts.campaign.Intel.hostile;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;

import java.util.HashMap;
import java.util.Map;

public class CR_HostileActivityManger implements EveryFrameScript, ColonyPlayerHostileActListener {

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {

    }

    @Override
    public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData, CargoAPI cargo) {

    }

    @Override
    public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData, Industry industry) {

    }

    @Override
    public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData) {
        if (market.getSize() < 3) return;
    }

    @Override
    public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, MarketAPI market, MarketCMD.TempData actionData) {
        if (market.getSize() < 3) return;
    }

    public static class ActivityCounter{
        private final Map<String, RaidData> raid = new HashMap<>();

        public void count(MarketAPI market) {
            count(market, 100000);
        }

        public void count(MarketAPI market, float expired) {
            String name = market.getName();
            if (!raid.containsKey(name)) raid.put(name, new RaidData());

            raid.get(name).count++;
            raid.get(name).points += market.getSize() * market.getSize();
            raid.get(name).expired = expired; // not +=
        }
    }

    public static class RaidData {
        int count = 1;
        int points = 0;
        float expired = 100000;
    }

}