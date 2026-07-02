package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import data.scripts.utils.CR_AuroraUtrennyayaRender;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;


public class CR_AuroraUtrennyaya extends BaseShipSystemScript {

    private static final float MAX_PHANTOM_RANGE = 1000f;
    private static final float MAX_PHANTOM_ANGLE = 270f;
    private static final float PHANTOM_ALPHA_MULT = 0.25f;
    private static final float PHANTOM_ANGLE_SPEED = 0.5f;
    private static final float PHANTOM_FACING_SPEED = 12f;
    private static final float HOLO_GENERATOR_OFFSET = 50f;
    private boolean hasHolo = false;
    private ShipAPI phantom;
    private float phantomAngle = 0.0f;
    private float phantomFacing = 0.0f;
    private float phantomAlphaMult = 0.25f;
    private final IntervalUtil intervalUtil = new IntervalUtil(0.05f, 0.1f);
    private final FaderUtil faderUtil = new FaderUtil(0.4f,0.5f,0.8f);
    private CR_AuroraUtrennyayaRender renderPlugin;

    public static final Color JITTER_COLOR = new Color(90, 165, 255, 55);
    public static final Color JITTER_UNDER_COLOR = new Color(90, 165, 255, 155);

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        CombatEngineAPI engine = Global.getCombatEngine();
        ShipAPI ship = (ShipAPI) stats.getEntity();

        if (engine.isPaused() ) {
            return;
        }

        if(ship == null || !ship.isAlive()){
            removePhantom(engine);
            return;
        }

        Vector2f shipLocation = ship.getLocation();
        if (null == shipLocation) {
            return;
        }

        ShipAPI shipTarget = ship.getShipTarget();
        Vector2f mouseTarget = ship.getMouseTarget();

        float elapsedInLastFrame = Global.getCombatEngine().getElapsedInLastFrame();
        float shipfacing = ship.getFacing();
        Vector2f holoGeneratorPoint = MathUtils.getPoint(shipLocation, HOLO_GENERATOR_OFFSET, shipfacing);
        float targetAngle = shipfacing;
        if (null != shipTarget && phantom != shipTarget) {
            targetAngle = VectorUtils.getAngle(holoGeneratorPoint, shipTarget.getLocation());
        } else if( null != mouseTarget){
            targetAngle = VectorUtils.getAngle(holoGeneratorPoint, mouseTarget);
        }

        if (!hasHolo) {
            phantomAngle = targetAngle;
        } else {
            if (MathUtils.getShortestRotation(targetAngle, phantomAngle) > 0) {
                phantomAngle += elapsedInLastFrame * PHANTOM_ANGLE_SPEED;
            } else {
                phantomAngle -= elapsedInLastFrame * PHANTOM_ANGLE_SPEED;
            }
        }

        float half = MAX_PHANTOM_ANGLE / 2; //135f
        if (shipfacing < 10f + half && phantomAngle > 180f) {
            phantomAngle -= 360f;
        }
        if (shipfacing > 350f - half && phantomAngle < 180f) {
            phantomAngle += 360f;
        }
        phantomAngle = MathUtils.clampAngle(MathUtils.clamp(phantomAngle, shipfacing - half, shipfacing + half));
        Vector2f phantomPoint = MathUtils.getPointOnCircumference(holoGeneratorPoint, MAX_PHANTOM_RANGE, phantomAngle);

        float targetFacing = shipfacing;
        if (null != shipTarget && phantom != shipTarget) {
            targetFacing = VectorUtils.getAngle(phantomPoint, shipTarget.getLocation());
        } else if( null != mouseTarget){
            targetFacing = VectorUtils.getAngle(phantomPoint, mouseTarget);
        }

        if (!hasHolo) {
            phantomFacing = shipfacing;
        } else {
            if (MathUtils.getShortestRotation(phantomFacing, targetFacing) > 0) {
                phantomFacing += elapsedInLastFrame * PHANTOM_FACING_SPEED;
            } else {
                phantomFacing -= elapsedInLastFrame * PHANTOM_FACING_SPEED;
            }
        }


        if (!hasHolo) {
            if (null != phantom) {
                Global.getCombatEngine().removeEntity(phantom);
            }
            //spawn
            phantom = engine.createFXDrone(ship.getVariant().clone());
            phantom.setHullSize(ShipAPI.HullSize.CAPITAL_SHIP);
            phantom.setCollisionRadius(150f);
            if(null != phantom.getShield()){
                ShieldAPI shield = phantom.getShield();
                shield.setRadius(125f);
            }
            phantom.setLayer(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
            phantom.setOwner(ship.getOwner());
            phantom.setInvalidTransferCommandTarget(true);
            phantom.setCollisionClass(CollisionClass.NONE);
            phantom.setDrone(true);

            Global.getCombatEngine().addEntity(phantom);
            //locksystem
            phantom.setShipSystemDisabled(true);
            phantom.setDefenseDisabled(false);
            //lockWeapon
            phantom.getMutableStats().getBallisticWeaponFluxCostMod().modifyFlat(id, 1000);
            phantom.getMutableStats().getEnergyWeaponFluxCostMod().modifyFlat(id, 1000);
            phantom.getMutableStats().getMissileWeaponFluxCostMod().modifyFlat(id, 1000);
//weapon damage decreased
            phantom.getMutableStats().getEnergyWeaponDamageMult().modifyMult(id, 0.1f);    // 能量武器
            phantom.getMutableStats().getBallisticWeaponDamageMult().modifyMult(id, 0.1f);  // 弹道武器
            phantom.getMutableStats().getMissileWeaponDamageMult().modifyMult(id, 0.1f);    // 导弹武器
            //datasync
            phantom.setCRAtDeployment(ship.getCRAtDeployment());
            phantom.setCurrentCR(ship.getCurrentCR());
            phantom.setHitpoints(ship.getHitpoints());
            FluxTrackerAPI fluxTracker = ship.getFluxTracker();
            FluxTrackerAPI phantomFluxTracker = phantom.getFluxTracker();
            phantomFluxTracker.setCurrFlux(fluxTracker.getCurrFlux());
            phantomFluxTracker.setHardFlux(fluxTracker.getHardFlux());

            //visualEffect
            phantom.getSpriteAPI().setAlphaMult(0.05f);
            phantom.setForceHideFFOverlay(true);

            faderUtil.setBrightness(0.4f);
            if (null != phantom) {
                hasHolo = true;
            }
            if(null == renderPlugin){
                renderPlugin = new CR_AuroraUtrennyayaRender(ship, phantom);
                engine.addLayeredRenderingPlugin(renderPlugin);
                renderPlugin.startRender(phantom);
            }

        } else {

            //visualEffect
            ShipEngineControllerAPI systemShipEngine = ship.getEngineController();
            faderUtil.fadeOut();
            if(systemShipEngine.isAccelerating()){
                faderUtil.fadeIn();
            }else if(systemShipEngine.isStrafingLeft() || systemShipEngine.isStrafingRight()){
                if(faderUtil.getBrightness() < 0.8f){
                    faderUtil.fadeIn();
                }
            }
            if(faderUtil.getBrightness() < 0.4f){
                faderUtil.setBrightness(0.4f);
            }

            float level = faderUtil.getBrightness();
            ShipEngineControllerAPI engineController = phantom.getEngineController();
            for (ShipEngineControllerAPI.ShipEngineAPI shipEngine : engineController.getShipEngines()) {
                engineController.setFlameLevel(shipEngine.getEngineSlot(),level);
            }
            faderUtil.advance(elapsedInLastFrame);

            float jitterLevel = effectLevel;
            if (state == State.OUT) {
                jitterLevel *= jitterLevel;
            }
            float maxRangeBonus = Math.min(Math.round(ship.getCollisionRadius() / 12f), 5f);
            float jitterRangeBonus = jitterLevel * maxRangeBonus;
            phantom.setJitter(this, JITTER_COLOR, jitterLevel, 4, 1f, 2f + jitterRangeBonus);
            phantom.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, 5, 1f, jitterRangeBonus * 1.5f);


            if (intervalUtil.intervalElapsed()) {
                phantomAlphaMult = PHANTOM_ALPHA_MULT + (0.05f * MathUtils.getRandomNumberInRange(-0.8f, 1.5f));
            }
            phantom.setAlphaMult(phantomAlphaMult * effectLevel);
            intervalUtil.advance(elapsedInLastFrame);

            //datasync
            phantom.setCurrentCR(ship.getCurrentCR());
            phantom.setHitpoints(ship.getHitpoints());
            FluxTrackerAPI fluxTracker = ship.getFluxTracker();
            FluxTrackerAPI phantomFluxTracker = phantom.getFluxTracker();
            phantomFluxTracker.setCurrFlux(fluxTracker.getCurrFlux());
            phantomFluxTracker.setHardFlux(fluxTracker.getHardFlux());

            //core
            phantom.setFacing(phantomFacing);
            phantom.getLocation().set(phantomPoint);
            phantom.getVelocity().set(ship.getVelocity());

            if(null != renderPlugin){
                renderPlugin.setReaderLoc(holoGeneratorPoint);
            }
        }
    }

    private void removePhantom(CombatEngineAPI engine){
        if (null != phantom) {
            engine.removeEntity(phantom);
        }
        if(null != renderPlugin){
            renderPlugin.endRender();
            renderPlugin = null;
        }
        hasHolo = false;
        phantomFacing = 0f;
        phantomAngle = 0f;
    }


    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        removePhantom(Global.getCombatEngine());
    }
}
