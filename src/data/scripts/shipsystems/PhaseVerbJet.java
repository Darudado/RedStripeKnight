package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI.ShipEngineAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

import java.awt.*;

public class PhaseVerbJet extends BaseShipSystemScript {
    public static final float ROF_BONUS = 0.1f;
    public static final float FLUX_REDUCTION = 20f;
    private static final Color JITTER_COLOR = new Color(128, 0, 0, 75);
    private static final Color ENGINE_COLOR_STANDARD = new Color(255, 10, 10);
    Color ENGINE_COLOR = ENGINE_COLOR_STANDARD;
    private final Object STATUSKEY1 = new Object();
    private final Object STATUSKEY2 = new Object();
    private final Object STATUSKEY3 = new Object();
    private final Object STATUSKEY4 = new Object();

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        float mult = 1f + ROF_BONUS * effectLevel;
        float jitterLevel = (float) (Math.pow(effectLevel, 2));


        if (stats.getEntity() instanceof ShipAPI ship) {
            String key = ship.getId() + "_" + id;
            Object test = Global.getCombatEngine().getCustomData().get(key);
            //ship.setJitter(this, JITTER_COLOR, jitterLevel, 1, 0, jitterLevel * 3f);
            if (state == State.IN) {
                if (test == null && effectLevel > 0.2f) {
                    Global.getCombatEngine().getCustomData().put(key, new Object());
                    ship.getEngineController().getExtendLengthFraction().advance(1f);
                    for (ShipEngineAPI engine : ship.getEngineController().getShipEngines()) {
                        if (engine.isSystemActivated()) {
                            ship.getEngineController().setFlameLevel(engine.getEngineSlot(), 1f);
                        }
                    }
                }
            }else {
                Global.getCombatEngine().getCustomData().remove(key);
            }

                if (ship.getVariant().hasHullMod("PolariphaseDrive")) {
                    Color jitterColor = new Color(ENGINE_COLOR.getRed(), ENGINE_COLOR.getGreen(), ENGINE_COLOR.getBlue(),
                            Math.min(255, Math.max(0, Math.round(0.2F * effectLevel * ENGINE_COLOR.getAlpha())))/3);

                    Color jitterUnderColor = new Color(ENGINE_COLOR.getRed(), ENGINE_COLOR.getGreen(), ENGINE_COLOR.getBlue(),
                            Math.min(255, Math.max(0, Math.round(0.3F * effectLevel * ENGINE_COLOR.getAlpha()))));



                    ship.setJitter(this, jitterColor, 1.0F, 5, 0.0F, 3.0F + effectLevel * 5.0F);
                    ship.setJitterUnder(this, jitterUnderColor, 1.0F, 25, 0.0F, 7.0F + effectLevel * 10.0F);

                    stats.getMaxSpeed().modifyFlat(id, 75f);
                    stats.getAcceleration().modifyPercent(id, 150f * effectLevel);
                    stats.getDeceleration().modifyPercent(id, 150f * effectLevel);
                    stats.getTurnAcceleration().modifyFlat(id, 45f * effectLevel);
                    stats.getTurnAcceleration().modifyPercent(id, 100f * effectLevel);
                    stats.getMaxTurnRate().modifyPercent(id, 125f);
                    stats.getTimeMult().modifyPercent(id, 75f);



                    if (Global.getCombatEngine().getPlayerShip() == ship) {
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                this.STATUSKEY1,
                                ship.getSystem().getSpecAPI().getIconSpriteName(),
                                ship.getSystem().getDisplayName(),
                                "最大航速提升75节",
                                false
                        );
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                this.STATUSKEY2,
                                ship.getSystem().getSpecAPI().getIconSpriteName(),
                                ship.getSystem().getDisplayName(),
                                "时间流速上升75%",
                                false
                        );
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                this.STATUSKEY3,
                                ship.getSystem().getSpecAPI().getIconSpriteName(),
                                ship.getSystem().getDisplayName(),
                                "机动性能增强",
                                false
                        );
                    }
                } else if (ship.getVariant().hasHullMod("PhaseDefenseUnit")) {
                stats.getMaxSpeed().modifyFlat(id, 50f);
                stats.getAcceleration().modifyPercent(id, 75f * effectLevel);
                stats.getDeceleration().modifyPercent(id, 750f * effectLevel);
                stats.getTurnAcceleration().modifyPercent(id, 100f * effectLevel);
                stats.getMaxTurnRate().modifyPercent(id, 75f);
                stats.getShieldDamageTakenMult().modifyMult(id, 0.25f);
                    if (Global.getCombatEngine().getPlayerShip() == ship) {
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                this.STATUSKEY1,
                                ship.getSystem().getSpecAPI().getIconSpriteName(),
                                ship.getSystem().getDisplayName(),
                                "最大航速提升50节",
                                false
                        );
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                this.STATUSKEY2,
                                ship.getSystem().getSpecAPI().getIconSpriteName(),
                                ship.getSystem().getDisplayName(),
                                "机动性能增强",
                                false
                        );
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                this.STATUSKEY3,
                                ship.getSystem().getSpecAPI().getIconSpriteName(),
                                ship.getSystem().getDisplayName(),
                                "护盾减伤25%",
                                false
                        );
                    }
            } else if (ship.getVariant().hasHullMod("WeaponOverLoad")) {
                    stats.getMaxSpeed().modifyFlat(id, 50f);
                    stats.getAcceleration().modifyPercent(id, 75f * effectLevel);
                    stats.getDeceleration().modifyPercent(id, 750f * effectLevel);
                    stats.getTurnAcceleration().modifyPercent(id, 100f * effectLevel);
                    stats.getMaxTurnRate().modifyPercent(id, 75f);
                    stats.getBallisticRoFMult().modifyMult(id, 1.75f);
                    stats.getBallisticWeaponFluxCostMod().modifyMult(id, 0.5f);
                    stats.getEnergyRoFMult().modifyMult(id, 1.75f);
                    stats.getEnergyWeaponFluxCostMod().modifyMult(id, 0.5f);
                    if (Global.getCombatEngine().getPlayerShip() == ship) {
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                this.STATUSKEY1,
                                ship.getSystem().getSpecAPI().getIconSpriteName(),
                                ship.getSystem().getDisplayName(),
                                "最大航速提升50节",
                                false
                        );
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                this.STATUSKEY2,
                                ship.getSystem().getSpecAPI().getIconSpriteName(),
                                ship.getSystem().getDisplayName(),
                                "机动性能增强",
                                false
                        );
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                this.STATUSKEY3,
                                ship.getSystem().getSpecAPI().getIconSpriteName(),
                                ship.getSystem().getDisplayName(),
                                "非导弹武器射速增加75%",
                                false
                        );
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                this.STATUSKEY4,
                                ship.getSystem().getSpecAPI().getIconSpriteName(),
                                ship.getSystem().getDisplayName(),
                                "非导弹武器辐能需求降低50%",
                                false
                        );
                    }
            }else{
                    stats.getBallisticRoFMult().modifyMult(id, mult);
                    stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1f - (FLUX_REDUCTION * 0.01f));
                    stats.getEnergyRoFMult().modifyMult(id, mult);
                    stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1f - (FLUX_REDUCTION * 0.01f));
                    stats.getMissileAccelerationBonus().modifyMult(id, mult);

                    if (state == ShipSystemStatsScript.State.OUT) {
                        stats.getMaxSpeed().unmodify(id); // to slow down ship to its regular top speed while powering drive down
                        stats.getMaxTurnRate().unmodify(id);
                    } else {
                        stats.getMaxSpeed().modifyFlat(id, 100f);
                        stats.getAcceleration().modifyPercent(id, 250f * effectLevel);
                        stats.getDeceleration().modifyPercent(id, 250f * effectLevel);
                        stats.getTurnAcceleration().modifyFlat(id, 45f * effectLevel);
                        stats.getTurnAcceleration().modifyPercent(id, 200f * effectLevel);
                        stats.getMaxTurnRate().modifyFlat(id, 15f);
                        stats.getMaxTurnRate().modifyPercent(id, 125f);
                    }

                    if (Global.getCombatEngine().getPlayerShip() == ship) {
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                this.STATUSKEY1,
                                ship.getSystem().getSpecAPI().getIconSpriteName(),
                                ship.getSystem().getDisplayName(),
                                "最大航速提升100节",
                                false
                        );
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                this.STATUSKEY2,
                                ship.getSystem().getSpecAPI().getIconSpriteName(),
                                ship.getSystem().getDisplayName(),
                                "机动性能增强",
                                false
                        );
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                this.STATUSKEY3,
                                ship.getSystem().getSpecAPI().getIconSpriteName(),
                                ship.getSystem().getDisplayName(),
                                "非导弹武器射速增加10%",
                                false
                        );
                        Global.getCombatEngine().maintainStatusForPlayerShip(
                                this.STATUSKEY4,
                                ship.getSystem().getSpecAPI().getIconSpriteName(),
                                ship.getSystem().getDisplayName(),
                                "非导弹武器辐能需求降低10%",
                                false
                        );
                    }


                }



        }

    }
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship != null) {
            String globalId = id + "_" + ship.getId();
            stats.getMaxSpeed().unmodify(id);
            stats.getMaxTurnRate().unmodify(id);
            stats.getTurnAcceleration().unmodify(id);
            stats.getAcceleration().unmodify(id);
            stats.getDeceleration().unmodify(id);
            stats.getBallisticRoFMult().unmodify(id);
            stats.getBallisticWeaponFluxCostMod().unmodify(id);
            stats.getFluxDissipation().unmodify(id);
            stats.getTimeMult().unmodifyMult(id);
            stats.getShieldDamageTakenMult().unmodifyMult(id);

            // 清除抖动效果
            ship.setJitter(this, ENGINE_COLOR_STANDARD, 0.0F, 5, 0.0F, 13.0F);
            ship.setJitterUnder(this, ENGINE_COLOR_STANDARD, 0.0F, 25, 0.0F, 17.0F);
        }
    }
}
