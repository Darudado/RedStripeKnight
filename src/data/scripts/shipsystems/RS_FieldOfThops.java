package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.TimeoutTracker;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;

import java.awt.*;
import java.util.*;
import java.util.List;

public class RS_FieldOfThops extends BaseShipSystemScript {
    private ShipAPI ship;
    private static final Color JITTER_COLOR = new Color(128, 0, 0, 55);
    private static final Color JITTER_UNDER_COLOR = new Color(128, 0, 0, 55);
    private static final Color AFTERIMAGE_COLOR = new Color(128, 0, 0, 155);

    private final IntervalUtil afterImageTest = new IntervalUtil(1f, 1f);
    private final CombatEngineAPI engine = Global.getCombatEngine();
    private IntervalUtil projChecker = new IntervalUtil(0.1f, 0.1f);
    private TimeoutTracker<BeamAPI> deflectBeam = new TimeoutTracker<>();
    private Map<BeamAPI, Vector2f> beamEnd = new HashMap<>();

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (ship == null) {
            if (stats.getEntity() instanceof ShipAPI) {
                ship = (ShipAPI) stats.getEntity();
            } else {
                return;
            }
        }

        if (effectLevel > 0) {
                // 应用时间流速修改到主体舰船
                float timeMult = (0.5f + effectLevel);
                ship.getMutableStats().getTimeMult().modifyMult(id, timeMult);

                // 同步模块的时间流速
                syncModuleTimeMult(id, timeMult);

                // 如果是玩家舰船，调整全局时间流速
                //if (ship == Global.getCombatEngine().getPlayerShip()) {
                    //Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / timeMult);
                //}

                // 视觉效果
                float jitterLevel = (float) (Math.pow(effectLevel, 2));
                ship.setJitter(this, JITTER_COLOR, jitterLevel, 1, 0, jitterLevel * 3f);
                ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, 15, 0f, 4f + jitterLevel * 3f);

                afterImageTest.advance(Global.getCombatEngine().getElapsedInLastFrame());
                if (afterImageTest.intervalElapsed()) {
                    ship.addAfterimage(AFTERIMAGE_COLOR, 0, 0, ship.getVelocity().getX() * (-1),
                            ship.getVelocity().getY() * (-1), 1f, 0.1f, 0.3f, 0.1f, false, true, false);
                }


            // 投射物偏转逻辑（保持不变）
            projChecker.advance(engine.getElapsedInLastFrame());
            deflectBeam.advance(engine.getElapsedInLastFrame());

            if (!deflectBeam.getItems().isEmpty()) {
                List<BeamAPI> toRemove = new ArrayList<>();
                for (BeamAPI beam : deflectBeam.getItems()) {
                    if (beam.getBrightness() <= 0) {
                        toRemove.add(beam);
                    }
                }
                for (BeamAPI b : toRemove) {
                    deflectBeam.remove(b);
                    beamEnd.remove(b);
                }
            }

            if (!beamEnd.isEmpty()) {
                List<BeamAPI> toRemove = new ArrayList<>();
                for (BeamAPI key : beamEnd.keySet()) {
                    if (deflectBeam.contains(key)) continue;
                    toRemove.add(key);
                }
                for (BeamAPI b : toRemove) {
                    beamEnd.remove(b);
                }
                for (BeamAPI b : beamEnd.keySet()) {
                    b.getTo().set(beamEnd.get(b));
                }
            }

            // 偏转投射物
            if (projChecker.intervalElapsed()) {
                for (DamagingProjectileAPI proj : engine.getProjectiles()) {
                    if (proj.getOwner() != ship.getOwner()) {
                        if (Misc.getDistance(proj.getLocation(),
                                ship.getLocation()) < (ship.getCollisionRadius() + 600f)) {
                            if (proj.getCustomData().containsKey("RS_FieldOfThopsDeflected")) continue;
                            float dis = (float) (700f + 600f * Math.random());
                            float angle;
                            float diff = MathUtils.getShortestRotation(ship.getFacing(), VectorUtils.getFacing(proj.getVelocity()));
                            if (diff < -120f) {
                                angle = VectorUtils.getFacing(proj.getVelocity()) + 55f + (float) Math.random() * 55f;
                            } else if (diff > 120f) {
                                angle = VectorUtils.getFacing(proj.getVelocity()) - 55f - (float) Math.random() * 55f;
                            } else {
                                angle = VectorUtils.getFacing(proj.getVelocity());
                            }
                            Vector2f vec = Misc.getUnitVectorAtDegreeAngle(angle);
                            float distMult = (proj instanceof MissileAPI) ? 3f : 1f;
                            vec.scale(dis * distMult);
                            proj.getLocation().set(Vector2f.add(ship.getLocation(), vec, null));
                            engine.addNebulaParticle(proj.getLocation(), ship.getVelocity(),
                                    proj.getCollisionRadius(),
                                    0.2f, 0.7f, 0.4f, 1f, AFTERIMAGE_COLOR);
                            proj.getVelocity().set((Vector2f) Misc.getUnitVectorAtDegreeAngle(angle).scale(proj.getVelocity().length()));
                            proj.setCustomData("RS_FieldOfThopsDeflected", true);
                        }
                    }
                }
            }

            // 偏转光束
            if (projChecker.intervalElapsed()) {
                for (BeamAPI beam : engine.getBeams()) {
                    if (beam.getSource().getOwner() != ship.getOwner() && !deflectBeam.contains(beam)) {
                        Vector2f nearest = MathUtils.getNearestPointOnLine(ship.getLocation(), beam.getFrom(), beam.getTo());
                        float dist = MathUtils.getDistance(ship.getLocation(), nearest);
                        if (dist < ship.getCollisionRadius() + 300f) {
                            deflectBeam.add(beam, 3f);
                        }
                        Vector2f end = Vector2f.add(ship.getLocation(), (Vector2f) VectorUtils.getDirectionalVector(ship.getLocation(), beam.getFrom()).scale(ship.getCollisionRadius() + 100f), null);
                        beamEnd.put(beam, end);
                    }
                }
            }
        }
    }

    /**
     * 同步模块的时间流速
     */
    private void syncModuleTimeMult(String id, float timeMult) {
        List<ShipAPI> modules = ship.getChildModulesCopy();
        if (modules != null && !modules.isEmpty()) {
            for (ShipAPI module : modules) {
                if (module.isAlive()) {
                    module.getMutableStats().getTimeMult().modifyMult(id, timeMult);
                }
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        if (ship == null) {
            if (stats.getEntity() instanceof ShipAPI) {
                ship = (ShipAPI) stats.getEntity();
            } else {
                return;
            }
        }

        // 移除主体的时间流速修改
        ship.getMutableStats().getTimeMult().unmodify(id);

        // 移除模块的时间流速修改
        List<ShipAPI> modules = ship.getChildModulesCopy();
        if (modules != null && !modules.isEmpty()) {
            for (ShipAPI module : modules) {
                module.getMutableStats().getTimeMult().unmodify(id);
            }
        }

        // 移除全局时间流速修改
        Global.getCombatEngine().getTimeMult().unmodify(id);
    }

    // public StatusData getStatusData(int index, State state, float effectLevel) {
    //     if (index == 0) {
    //         return new StatusData(HSII18nUtil.getShipSystemString("RS_FieldOfThops"), false);
    //     }
    //     return null;
    // }
}