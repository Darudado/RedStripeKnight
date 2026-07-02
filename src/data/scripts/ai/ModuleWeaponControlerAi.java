package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.Misc;
import data.scripts.weapons.RX78_GP03D_ModuleMissileContro_Abandon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModuleWeaponControlerAi implements ShipAIPlugin {
    private final ShipwideAIFlags flags = new ShipwideAIFlags();
    private final ShipAPI ship;
    private final List<WeaponAPI> weapons = new ArrayList<>();
    private WeaponAPI master = null;
    private RX78_GP03D_ModuleMissileContro_Abandon masterEffect = null;
    private static final String DIRECT_CONTROL_SLOT_KEY = "Missile_";
    private static final Set<String> Controllers = new HashSet<>();
    boolean init = false;

    public ModuleWeaponControlerAi(ShipAPI ship) {


        this.ship = ship;
        ship.setCustomData("AGC_doNotReplaceShipAI", true);
    }

    static {
        Controllers.add("rs_RX78_GP03D_controller_Left");
        Controllers.add("rs_RX78_GP03D_controller_Right");
    }

    public void setDoNotFireDelay(float amount) {
    }

    public void forceCircumstanceEvaluation() {
    }

    private void init() {
        this.init = true;
        float fireFluxCost = 0.0F;

        for(WeaponAPI weapon : this.ship.getAllWeapons()) {
            if (weapon != null && weapon.getSlot() != null && (weapon.getSlot().getId().contains("Missile_") )) {
                this.weapons.add(weapon);
                if (weapon.isBeam()) {
                    if (weapon.isBurstBeam()) {
                        fireFluxCost += weapon.getDerivedStats().getBurstFireDuration() * weapon.getDerivedStats().getFluxPerSecond();
                    } else {
                        fireFluxCost += weapon.getDerivedStats().getFluxPerSecond();
                    }
                } else {
                    fireFluxCost += weapon.getFluxCostToFire();
                }
            }
        }

        ShipAPI parent = this.ship.getParentStation();
        if (parent != null) {
            for(WeaponAPI weapon : parent.getAllWeapons()) {
                if (weapon != null && Controllers.contains(weapon.getId())) {
                    this.master = weapon;
                    this.masterEffect = (RX78_GP03D_ModuleMissileContro_Abandon)weapon.getEffectPlugin();
                    this.masterEffect.setChildModule(this.ship);
                    this.master.getSprite().setColor(Misc.setAlpha(this.master.getSprite().getColor(), 0));
                    break;
                }
            }
        }

    }

    public void advance(float amount) {
        if (!this.init) {
            this.init();
        }

        if (!this.weapons.isEmpty() && this.master != null && this.ship.getStationSlot() != null) {
            if (Global.getCombatEngine() != null && !Global.getCombatEngine().isPaused()) {
                boolean shouldFire = !this.master.isDisabled() && (this.master.isFiring() || this.masterEffect.getFireCommand()) && !this.ship.getParentStation().getFluxTracker().isOverloadedOrVenting();

                for(WeaponAPI weapon : this.weapons) {
                    if (shouldFire) {
                        weapon.setForceFireOneFrame(true);
                    } else if (weapon.isBeam() && !weapon.isBurstBeam()) {
                        if (this.master.isFiring()) {
                            weapon.setForceFireOneFrame(true);
                        } else {
                            weapon.setForceNoFireOneFrame(true);
                        }
                    } else {
                        weapon.setForceNoFireOneFrame(true);
                    }

                    if (this.master.isPermanentlyDisabled()) {
                        weapon.disable(true);
                    }
                }

            }
        }
    }

    public boolean needsRefit() {
        return false;
    }

    public ShipwideAIFlags getAIFlags() {
        return this.flags;
    }

    public void cancelCurrentManeuver() {
    }

    public ShipAIConfig getConfig() {
        return null;
    }
}