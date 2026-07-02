package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;

public class RX78_GP03D_ModuleMissileControl_Left1 implements EveryFrameWeaponEffectPlugin {
    ShipAPI child;
    String childID = "rs_RX78_GP03_D_Leftmodule";

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        if(ship.getChildModulesCopy() != null)
            for(ShipAPI C : engine.getShips()) {
                if (C.getId().equals(childID))
                    child = C;
            }
        if(!child.isAlive()){
            weapon.disable(true);
        }
    }
}