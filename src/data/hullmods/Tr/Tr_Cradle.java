package data.hullmods.Tr;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;

public class Tr_Cradle extends BaseHullMod {
    float EDM = 35f;
    float ERM = 15f;

    public String id= "Cradle_sacrifice";

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getDynamic().getStat("explosion_damage_mult").modifyMult(id, EDM);
        stats.getDynamic().getStat("explosion_radius_mult").modifyMult(id, ERM);
    }


    public void advanceInCombat(ShipAPI ship, float amount){
        if (ship == null || !ship.isAlive() || ship.isHulk()) {
            return;
        }

        if(ship.getHitpoints() <= ship.getMaxHitpoints() * 0.4f){
            ship.getMutableStats().getAcceleration().modifyFlat(id, 500f);
            ship.getMutableStats().getMaxSpeed().modifyFlat(id, 3000f);
            ship.getMutableStats().getMaxSpeed().modifyMult(id,5f);
            ship.getMutableStats().getTurnAcceleration().modifyMult(id,0);
            ship.giveCommand(ShipCommand.ACCELERATE,ship,0);
            ship.turnOnTravelDrive();
            //if(ship.getHitpoints() <= ship.getMaxHitpoints() * 0.25f) {
                //ship.getEngineController().forceFlameout(true);
            //}
        }
    }
}