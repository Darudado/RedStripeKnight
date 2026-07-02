package data.hullmods;


import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;


import java.util.HashSet;
import java.util.Set;

public class EnergyReconstruction extends BaseHullMod{
    private static final Set<String> TARGET_WEAPON_IDS = new HashSet<>() {{
        add("cr_nimbusrend");
        add("cr_aetherclasm");
        add("cr_nimbusrend_m");
        add("rs_arondight");
    }};

    public static final float E_ProjectileSpeedMult = 1.5F;
    public static final float B_ProjectileSpeedMult = 1.5F;
    public static final float E_RoFMult=1.2F;
    public static final float B_RoFMult=1.3F;
    public static final float BeanHardFlux= 0.3F;


    public void applyEffectsAfterShipCreation(ShipAPI ship, MutableShipStatsAPI stats, String id ) {
        // 遍历所有已安装武器
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            // 精确匹配武器ID
            if (TARGET_WEAPON_IDS.contains(weapon.getId())) {

                stats.getBallisticProjectileSpeedMult().modifyPercent(id,B_ProjectileSpeedMult);
                stats.getEnergyProjectileSpeedMult().modifyPercent(id, E_ProjectileSpeedMult);
                stats.getEnergyRoFMult().modifyPercent(id, E_RoFMult);
                stats.getEnergyRoFMult().modifyPercent(id, B_RoFMult);
            }
        }
        for (WeaponAPI w : ship.getAllWeapons()) {
            if (!w.isBeam()){

            }
        }
    }

}