package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.*;
import java.util.List;

public class WeaponOverLoad extends BaseHullMod {
    private static final Set<String> BLOCKED_OTHER = new HashSet<>();
    private static final Set<String> BLOCKED_OMNI = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER_PLAYER_ONLY = new HashSet<>();
    private static final String ERROR = "IncompatibleHullmodWarning";
    private float check = 0.0F;

    // 存储光束武器上一帧的开火状态
    private Map<WeaponAPI, Boolean> beamWeaponLastFiringMap = new HashMap<>();
    // 存储非光束武器上一帧的冷却状态
    private Map<WeaponAPI, Float> nonBeamWeaponLastCooldownMap = new HashMap<>();

    public boolean isApplicableToShip(ShipAPI ship) {
        return ship.getVariant().getHullMods().contains("CrusadersCore")&&
                !ship.getVariant().getHullMods().contains("PhaseDefenseUnit") &&
                !ship.getVariant().getHullMods().contains("PolariphaseDrive");
    }

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getFluxDissipation().modifyMult(id, 1.75f);
        stats.getFluxCapacity().modifyMult(id, 0.75f);
        stats.getEnergyWeaponDamageMult().modifyMult(id, 1.3f);
        stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1.15f);
        stats.getBallisticWeaponDamageMult().modifyMult(id, 1.3f);
        stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1.15f);
        stats.getAcceleration().modifyMult(id, 0.75f);
        stats.getDeceleration().modifyMult(id, 0.75f);
        stats.getMaxTurnRate().modifyMult(id, 0.75f);
        stats.getTurnAcceleration().modifyMult(id, 0.75f);
    }

    public static final String FLUX_DATA_KEY = "weapon_overload_flux_data";

    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!Global.getCombatEngine().isPaused() && ship.isAlive()) {
            // 获取或创建数据实例
            WeaponFluxData fluxData = (WeaponFluxData) ship.getCustomData().get(FLUX_DATA_KEY);
            if (fluxData == null) {
                fluxData = new WeaponFluxData();
                ship.getCustomData().put(FLUX_DATA_KEY, fluxData);
            }

            // 重置当前帧数据
            fluxData.resetFrameData();

            List<WeaponAPI> weapons = ship.getUsableWeapons();
            for (WeaponAPI weapon : weapons) {
                float fluxToAdd;

                if (weapon.isBeam()) {
                    fluxToAdd = handleBeamWeapon(ship, weapon);
                } else {
                    fluxToAdd = handleNonBeamWeapon(ship, weapon);
                }

                // 累计当前帧产生的辐能
                if (fluxToAdd > 0) {
                    fluxData.currentFluxToAdd += fluxToAdd;
                    fluxData.totalFluxAdded += fluxToAdd;
                }
            }
        }
    }

    private float handleBeamWeapon(ShipAPI ship, WeaponAPI weapon) {
        // 获取上一帧的开火状态（默认为false）
        boolean lastFiring = beamWeaponLastFiringMap.getOrDefault(weapon, false);
        boolean currentFiring = weapon.isFiring();

        // 更新状态记录
        beamWeaponLastFiringMap.put(weapon, currentFiring);
        if (!lastFiring && currentFiring) {
            float fluxCost = weapon.getFluxCostToFire();
            float fluxToAdd = fluxCost * 0.30f;
            ship.getFluxTracker().increaseFlux(fluxToAdd, true);
            return fluxToAdd; // 返回产生的flux值
        }
        return 0;

    }

    private float handleNonBeamWeapon(ShipAPI ship, WeaponAPI weapon) {
        // 获取上一帧的冷却状态（默认为0）
        float lastCooldown = nonBeamWeaponLastCooldownMap.getOrDefault(weapon, 0f);
        float currentCooldown = weapon.getCooldownRemaining();
        // 更新状态记录
        nonBeamWeaponLastCooldownMap.put(weapon, currentCooldown);

        if (lastCooldown <= 0.001f && currentCooldown > 0.001f) {
            float fluxCost = weapon.getFluxCostToFire();
            float fluxToAdd = fluxCost * 0.4f;
            ship.getFluxTracker().increaseFlux(fluxToAdd, false);
            return fluxToAdd; // 返回产生的flux值
        }
        return 0;
    }

    public static class WeaponFluxData {
        public float currentFluxToAdd;  // 当前帧待转移的辐能量
        public float totalFluxAdded;    // 累计总量（调试用）

        public void resetFrameData() {
            currentFluxToAdd = 0;
        }
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.getMutableStats();
        if (check > 0.0F && --check < 1.0F) {
            ship.getVariant().removeMod(ERROR);
        }

        for (Set<String> strings : Arrays.asList(BLOCKED_OMNI, BLOCKED_OTHER, BLOCKED_OTHER_PLAYER_ONLY)) {
            checkAndRemoveBlockedMods(ship, strings);
        }

    }

    private void checkAndRemoveBlockedMods(ShipAPI ship, @NotNull Set<String> blockedMods) {
        List<String> shipMods = new ArrayList<>(ship.getVariant().getHullMods());
        for (String mod : shipMods) {
            if (blockedMods.contains(mod)) {
                // 移除冲突船插
                ship.getVariant().removeMod(mod);
                if (!ship.getVariant().hasHullMod(ERROR)) {
                    ship.getVariant().addMod(ERROR);
                }
                // 记录日志
                Global.getLogger(this.getClass()).info(
                        "Removed conflicting hullmod [" + mod + "] from " + ship.getName()
                );
            }
        }
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        float pad = 2f;
        Color highlight = Misc.getHighlightColor();
        Color h = Misc.getPositiveHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        tooltip.addPara("Enhancement kits that provide specific upgrades to ships. Each ship can be equipped with a special enhancement kit.",  opad,h);
        tooltip.addSectionHeading("Effect", Alignment.MID, opad);
        tooltip.addPara("Redirects the majority of the ship's energy to weapons systems.", pad, h);
        tooltip.addPara("Non-missile weapon damage + %s", pad, h, "" +30 + "%");
        tooltip.addPara("At the same time, the required radiation energy + %s", pad, bad, "" +15 + "%");
        tooltip.addPara("Ship dissipation increased by %s", pad, h,  "" + 75+ "%" );
        tooltip.addPara("Ship radiation capacity decreased by %s", pad, bad,  "" + 25+ "%");
        tooltip.addPara("The ship's maneuverability is reduced by %s", pad, bad,  "" + 25+ "%");
        tooltip.addPara("Gives additional effects to ship weapons, hold %s to view detailed mechanics", opad, highlight,  "F3" );
        if (Keyboard.isKeyDown(Keyboard.KEY_F3)) {
            tooltip.addPara("When the weapon is fired, an additional %s of soft radiation required by the weapon is generated as temporary stored radiation.", pad, bad,"" +40+ "%");
            tooltip.addPara("The temporarily stored radiant energy will occupy the power grid and be slowly dissipated, and the ship's plating will be restored based on %s of its total amount.", pad, h,  "" + 5+ "%");

        }

        tooltip.addSectionHeading("The benefits of ship plugs to the tactical system", Alignment.MID, opad);
        if((ship == null)){
            tooltip.addPara("Compliant ships and tactical systems not detected", pad);
        }
        if(!(ship == null)) {
            if (!(ship.getSystem() == null)) {
                tooltip.addPara("Press and hold %s to view details", opad, h, "F4");
                if (Keyboard.isKeyDown(Keyboard.KEY_F4)) {
                    if (ship.getSystem().getId().equals("CR_PhaseboostDrive")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/ammo_feeder.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("Increases the damage of live ammunition and energy weapons by 50% within 1 second after using the tactical system", pad);
                        tooltip.addImageWithText(15f);
                    }


                    if (ship.getSystem().getId().equals("CR_PhaseVerbJet")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/ammo_feeder.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("Increases non-missile weapon damage, fire rate, and radiation consumption by 50% while the tactical system is enabled", pad);
                        tooltip.addImageWithText(15f);
                    }


                    if (ship.getSystem().getId().equals("CR_PhaseDrift")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/ammo_feeder.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("During the duration of the tactical system, a dem drone will be spawned to attack enemy units every 75 unit movements.", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("RS_WeaponOverloading")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/high_energy_focus.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("Strengthen the effect of the tactical system on weapons", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("CR_TargetingLink")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/high_energy_focus.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("Greatly enhance the weapon damage and energy consumption of battleships and launched fighters", pad);
                        tooltip.addImageWithText(15f);
                    }
                    if (ship.getSystem().getId().equals("RS_FortressShieldStats")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/high_energy_focus.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("Moderately pressurizes the shield and reduces weapon radiation consumption, during which the ship can fire", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("CR_PhaseCrossing")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/high_energy_focus.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("Tear apart space and form a powerful explosion at the point where the ship jumps.", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("CR_DamperBurn")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/high_energy_focus.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("Increases the damage and rate of fire of non-missile weapons while the system lasts", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("RS_MABurn")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/high_energy_focus.png", 35f);
                        imageText.addPara("Add effects to the tactical system:", pad);
                        imageText.addPara("Increases the damage and rate of fire of non-missile weapons while the system lasts", pad);
                        tooltip.addImageWithText(15f);
                    }
                }
            }
        }
    }

    static {
        BLOCKED_OMNI.add("PhaseDefenseUnit");
        BLOCKED_OMNI.add("PolariphaseDrive");
        //BLOCKED_OMNI.add("targetingunit");


        BLOCKED_OTHER.add("PhaseDefenseUnit");
        BLOCKED_OTHER.add("PolariphaseDrive");
        //BLOCKED_OTHER.add("targetingunit");

        BLOCKED_OTHER_PLAYER_ONLY.add("PhaseDefenseUnit");
        BLOCKED_OTHER_PLAYER_ONLY.add("PolariphaseDrive");
        //BLOCKED_OTHER_PLAYER_ONLY.add("targetingunit");
    }
}


