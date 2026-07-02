package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;

public class RX78_GP03_D_DecoEffect implements EveryFrameWeaponEffectPlugin {
    protected WeaponAPI weapon;
    protected ShipAPI ship;

    private boolean leftModuleDestroyed = false;
    private boolean rightModuleDestroyed = false;

    public void init(WeaponAPI weapon) {
        this.weapon = weapon;
        this.ship = weapon.getShip();
        // 调试输出可用槽位
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (ship == null || !ship.isAlive()) return;

        ShipAPI leftModule = findChildModuleById(ship, "rs_RX78_GP03_D_Leftmodule");
        ShipAPI rightModule = findChildModuleById(ship, "rs_RX78_GP03_D_Rightmodule");

        if (leftModule != null && !leftModule.isAlive() && !leftModuleDestroyed) {
            System.out.println("Clearing slot WS0008 for left module");
            ship.getVariant().clearSlot("WS0008");
            ship.getVariant().addWeapon("WS0008", ""); // 强制更新槽位
            leftModuleDestroyed = true;
        }

        if (rightModule != null && !rightModule.isAlive() && !rightModuleDestroyed) {
            System.out.println("Clearing slot WS0009 for right module");
            ship.getVariant().clearSlot("WS0009");
            ship.getVariant().addWeapon("WS0009", "");
            rightModuleDestroyed = true;
        }
    }

    private ShipAPI findChildModuleById(ShipAPI ship, String moduleId) {
        for (ShipAPI module : ship.getChildModulesCopy()) {
            if (module.getHullSpec().getBaseHullId().equals(moduleId)) {
                return module;
            }
        }
        return null;
    }
}