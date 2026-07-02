package data.scripts.ai;

import com.fs.starfarer.api.combat.*;

public class Launchai implements ShipAIPlugin {
    final ShipAPI ship;

    public Launchai(ShipAPI ship) {
        this.ship = ship;
    }

    @Override
    public void setDoNotFireDelay(float amount) {

    }

    @Override
    public void forceCircumstanceEvaluation() {

    }

    float dealy = 3;

    @Override
    public void advance(float amount) {
        if (dealy > 0) {
            dealy -= amount;
            ship.giveCommand(ShipCommand.ACCELERATE, ship.getLocation(), 0);
        } else {
            ship.resetDefaultAI();
        }
    }

    @Override
    public boolean needsRefit() {
        return false;
    }

    ShipwideAIFlags flags = new ShipwideAIFlags();

    @Override
    public ShipwideAIFlags getAIFlags() {
        return flags;
    }

    @Override
    public void cancelCurrentManeuver() {

    }

    ShipAIConfig config = new ShipAIConfig();
    @Override
    public ShipAIConfig getConfig() {
        return config;
    }
}
