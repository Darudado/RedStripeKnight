package data.ai;

import com.fs.starfarer.api.Global;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.subsystems.drones.PIDController;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

import data.ai.Moci_CombatSquadAIV2._STATE_S;
import data.hullmods.Moci_RS_LandingAI;
import data.hullmods.Moci_SMALandingSequence;
import data.hullmods.Moci_RS_RepairBayScript;

public class Moci_RetreatModule extends Moci_BaseAIModule{

    private _STATE_S state = _STATE_S.RETREAT_FIGHT;
    private IntervalUtil AIInterval = new IntervalUtil(0.2f,0.3f);
    private Moci_CombatSquadAIV2 mainAI;
    private boolean shouldEvaluate = true;
    private Moci_RS_LandingAI landingAI = null;
    private float YLim = 0;

    public Moci_RetreatModule(ShipAPI ship, ShipAPI mothership, PIDController controller,Moci_CombatSquadAIV2 mainAI){
        super(ship,mothership,controller);
        this.mainAI = mainAI;
        YLim = (ship.getOwner()==0)?(-Global.getCombatEngine().getMapHeight()/2f+1500f):Global.getCombatEngine().getMapHeight()/2f-1500f;
    }

    @Override
    public void advance(float amount) {
        if(!shouldEvaluate){
            AIInterval.advance(amount);
            if(AIInterval.intervalElapsed()) shouldEvaluate = true;
        }
        if(shouldEvaluate){
            Moci_RS_RepairBayScript.Moci_RepairBay bay = Moci_SMALandingSequence.findAutomaticRefitTarget(ship);
            if(bay != null) {
                state = _STATE_S.RETREAT_LANDING;
            } else if(Moci_SMALandingSequence.shouldTriggerRefit(ship)){
                state = _STATE_S.TOWARD;
            }
            if(mothership == null || !mothership.isAlive()||mothership.isHulk()){
                if(!AIUtils.getNearbyEnemies(ship, 1000f).isEmpty()||
                        (ship.getOwner() == 0?ship.getLocation().getY()>YLim:ship.getLocation().getY()<YLim)){
                    state = _STATE_S.RETREAT_FIGHT;
                }else{
                    state = _STATE_S.DIRECT_RETREAT;
                }
            }
            shouldEvaluate = false;
        }
        Vector2f expectedLocation = new Vector2f(ship.getLocation());
        float expectedFacing = 90f;
        if(ship.getOwner() == 0){
            expectedFacing = 270f;
        }
        Vector2f retreatDir;
        if(ship.getOwner() == 0){
            retreatDir = new Vector2f(0,-100f);
        }else{
            retreatDir = new Vector2f(0,100f);
        }

        switch (state){
            case RETREAT_FIGHT:
                Vector2f avoidDir = new Vector2f();
                for(ShipAPI e:AIUtils.getNearbyEnemies(ship,1000f)){
                    Vector2f.add(avoidDir,(Vector2f) Misc.getUnitVector(e.getLocation(),ship.getLocation()).scale(10f*(e.getHullSize().ordinal()-1)),avoidDir);
                }
                Vector2f.add(retreatDir,avoidDir,retreatDir);
                Vector2f.add(expectedLocation,retreatDir,expectedLocation);
                ShipAPI nearest = AIUtils.getNearestEnemy(ship);
                if(nearest!=null&&Misc.getDistance(ship.getLocation(),nearest.getLocation())<600f+nearest.getCollisionRadius()){
                    expectedFacing = Misc.getAngleInDegrees(ship.getLocation(),nearest.getLocation());
                }
                controller.move(expectedLocation,ship);
                controller.rotate(expectedFacing,ship);
                ship.setRetreating(true,false);
                break;
            case DIRECT_RETREAT:
                boolean travelDriveOn = ship.getTravelDrive() != null && ship.getTravelDrive().isOn();
                
                // 如果旅行驱动已经开启，不再使用PID控制器
                if (!travelDriveOn) {
                    Vector2f.add(expectedLocation,retreatDir,expectedLocation);
                    controller.move(expectedLocation,ship);
                    controller.rotate(expectedFacing,ship);
                    if(ship.getVelocity().getX()>0){
                        ship.getVelocity().set(ship.getVelocity().getX()*0.99f,ship.getVelocity().getY());
                    }
                }
                
                ship.setRetreating(true,true);
                
                float facingError = Math.abs(MathUtils.getShortestRotation(ship.getFacing(),expectedFacing));
                
                // 调试日志
                if (Global.getCombatEngine().getPlayerShip() == ship || 
                    (Global.getCombatEngine().getPlayerShip() != null && 
                     MathUtils.getDistance(ship, Global.getCombatEngine().getPlayerShip()) < 2000f)) {
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                        "retreat_debug_" + ship.getId(),
                        Global.getSettings().getSpriteName("ui", "icon_tactical_cr_penalty"),
                        "Retreat debugging",
                        "Orientation error:" + String.format("%.1f", facingError) + "° | Travel Drive:" + (travelDriveOn ? "turn on" : "closure") + " | Y: " + String.format("%.0f", ship.getLocation().getY()),
                        false
                    );
                }
                
                // 尝试激活旅行驱动
                if(facingError <= 1f && !travelDriveOn){
                    ship.turnOnTravelDrive();
                    Global.getLogger(Moci_RetreatModule.class).info(
                        "wingman" + ship.getName() + "Activate travel driver, current Y coordinate:" + ship.getLocation().getY()
                    );
                }
                break;
            case RETREAT_LANDING:
                if(landingAI!=null&&landingAI.shouldRemove()) landingAI = null;
                if(landingAI!=null){
                    landingAI.advance(amount);
                }else{
                        Moci_RS_RepairBayScript.Moci_RepairBay end = Moci_SMALandingSequence.findAutomaticRefitTarget(ship);
                        if(end!=null){
                            landingAI = new Moci_RS_LandingAI(ship,ship.getAIFlags(),ship.getShipAI().getConfig(), end,false);
                            landingAI.forceCircumstanceEvaluation();
                            landingAI.advance(amount);
                        }
                }
                break;
            case TOWARD:
                if (mainAI != null) {
                    mainAI.getEscort().advance(amount);
                }
                break;

        }
        // 注释掉状态栏显示 - 显示编队成员舰船名称和状态
        // Global.getCombatEngine().maintainStatusForPlayerShip(ship, Global.getSettings().getSpriteName("ui", "icon_tactical_bdeck"),ship.getName()+ship.getVariant().getDesignation(),"RETREAT"+":"+state.toString(),true);

    }

    @Override
    public void forceEvaluateCircumstance() {
        shouldEvaluate = true;
    }
}
