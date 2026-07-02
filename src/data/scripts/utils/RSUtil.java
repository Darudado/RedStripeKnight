package data.scripts.utils;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.MissileSpecAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.loading.WeaponGroupSpec;
import com.fs.starfarer.api.loading.WeaponGroupType;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.scripts.CombatPlugin;
import lunalib.backend.ui.refit.RefitButtonAdder;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lazywizard.lazylib.combat.WeaponUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.vector.Vector2f;
import data.scripts.RSModPlugin;
import org.magiclib.util.MagicRender;
import particleengine.Emitter;
import particleengine.Particles;

import java.awt.*;
import java.util.*;
import java.util.List;

import static com.fs.starfarer.api.util.Misc.normalizeAngle;

public class RSUtil {
    private static final String SHIP_SYSTEM_SCRIPT = "shipSystem";
    private static final String HULL_MOD_SCRIPT = "hullMod";
    private static final String ECON_SCRIPT = "econ";
    private static final String CAMPAIGN_SCRIPT = "campaign";
    private static final String WEAPON_SCRIPT = "weapon";

    private static final Logger log = Global.getLogger(RSUtil.class);

    public static String getString(String category, String id) {
        return Global.getSettings().getString(category, id);
    }

    public static String getShipSystemString(String id) {
        return getString(SHIP_SYSTEM_SCRIPT, id);
    }

    public static String getHullModString(String id) {
        return getString(HULL_MOD_SCRIPT, id);
    }

    public static String getEconString(String id){
        return getString(ECON_SCRIPT,id);
    }

    public static String getCampaignString(String id) {
        return getString(CAMPAIGN_SCRIPT, id);
    }

    public static String getWeaponString(String id){
        return getString(WEAPON_SCRIPT, id);
    }


    private static final List<DamagingProjectileAPI> new_proj = new ArrayList<>();

    public static boolean isInPlayerFleet(Object data) {
        if (data == null) {
            return false;
        }
        if (data instanceof FleetMemberAPI member) {
            PersonAPI commander = member.getFleetCommanderForStats();
            if (commander == null) {
                commander = member.getFleetCommander();
            }
            return commander != null && commander.isPlayer();
        } else if (data instanceof ShipAPI ship) {
            PersonAPI commander = ship.getFleetCommander();
            return (commander != null && commander.isPlayer()) || isInPlayerFleet(ship.getFleetMember());
        } else if (data instanceof ShipVariantAPI variant) {
            return isInPlayerFleet(variant.getStatsForOpCosts());
        } else if (data instanceof MutableShipStatsAPI stats) {
            return isInPlayerFleet(stats.getFleetMember());
        } else {
            return false;
        }
    }

    public static Vector2f moveVec(Vector2f vec, float x, float y, float facing) {
        return new Vector2f(vec.x + addVec(x, y, facing).x, vec.y + addVec(x, y, facing).y);
    }

    public static void shapeFrom4x4Sprite(SpriteAPI sprite) {
        float i = Misc.random.nextInt(4);
        float j = Misc.random.nextInt(4);
        sprite.setTexWidth(0.25f);
        sprite.setTexHeight(0.25f);
        sprite.setTexX(i * 0.25f);
        sprite.setTexY(j * 0.25f);
    }

    public static Vector2f addVec(float x, float y, float facing) {
        return new Vector2f(
                x * (float)Math.cos(Math.toRadians(facing - 90f)) - y * (float)Math.sin(Math.toRadians(facing - 90f)),
                x * (float)Math.sin(Math.toRadians(facing - 90f)) + y * (float)Math.cos(Math.toRadians(facing - 90f))
        );
    }

    public static void Setjitter(SpriteAPI sprite, Vector2f loc, float facing, Color color, float intensity, int copies, float range) {
        sprite.setAlphaMult(intensity);
        float c = copies > 0 ? copies : 1;
        Random random = Misc.random;

        for (int i = 0; i < c; i++) {
            MagicRender.singleframe(sprite, moveVec(loc, random.nextFloat() * range, 0f, random.nextFloat() * 360f),
                    new Vector2f(sprite.getWidth(), sprite.getHeight()), facing - 90f, color, false);
        }
    }

    public static void refreshRefitUI() {
        if (Global.getCurrentState() == GameState.CAMPAIGN &&
                Global.getSector() != null &&
                Global.getSector().getCampaignUI() != null &&
                Global.getSector().getCampaignUI().getCurrentCoreTab() == CoreUITabId.REFIT) {

            try {
                RefitButtonAdder.setRequiresVariantUpdate(true);
            } catch (Throwable var1) {
                throw new RuntimeException(var1);
            }
        }
    }

    public abstract static class PhaseTorpedoTrail {
        public static Emitter trail(Vector2f loc, float amp) {
            Emitter emitter = Particles.initialize(loc, "graphics/fx/smoke32.png");
            emitter.color(0.98f, 0.53f, 0.69f, 0.12f);
            emitter.randomHSVA(30f, 0f, 0f, 0f);
            emitter.saturationShift(-0.12f, -0.15f);
            emitter.velocity(-90f, -80f, 0f, 0f);
            emitter.size(20f, 30f, 10f, 15f);
            emitter.growthRate(8f, 12f, 8f, 12f);
            emitter.fadeTime(0f, 0f, 2f, 3f);
            emitter.life(3f, 3.5f);
            emitter.facing(0, 360f);
            emitter.turnRate(-5f, 5f);
            emitter.sinusoidalMotionY(amp * 0.5f, amp * 0.75f, 0.5f, 0.5f, 0f, 0f);
            return emitter;
        }

        public static void makeTrail(final CombatEntityAPI follow) {
            for (float f = -15f; f <= 15f; f += 30f) {
                Emitter trailEmitter = trail(follow.getLocation(), f);
                Particles.stream(trailEmitter, 1, 250, 100f, emitter -> {
                    emitter.setAxis(follow.getFacing());
                    emitter.setLocation(follow.getLocation());
                    return Global.getCombatEngine().isEntityInPlay(follow);
                });
            }
        }
    }

    public abstract static class SparkTrail {
        private static final float ARC_INTERVAL = 0.5f;
        private static final float ARC_WIDTH = 8f;

        public static Emitter trail(Vector2f loc, float amp) {
            Emitter emitter = Particles.initialize(loc, "graphics/fx/smoke32.png");
            emitter.color(1f, 0.2f, 0.2f, 0.8f);
            emitter.velocity(-90f, -80f, 0f, 0f);
            emitter.size(25f, 25f, 25f, 25f);
            emitter.fadeTime(0f, 0f, 3f, 3.5f);
            emitter.life(3f, 3.5f);
            emitter.facing(0, 360f);
            emitter.turnRate(-5f, 5f);
            emitter.sinusoidalMotionY(amp * 0.5f, amp * 0.75f, 0.5f, 0.5f, 0f, 0f);
            return emitter;
        }

        public static void makeTrail(final CombatEntityAPI follow) {
            for (float f = -15f; f <= 15f; f += 30f) {
                Vector2f sideOffset = new Vector2f(f, 0);
                Emitter trailEmitter = trail(follow.getLocation(), f);
                Particles.stream(trailEmitter, 1, 250, 100f, emitter -> {
                    emitter.setAxis(follow.getFacing());
                    emitter.setLocation(follow.getLocation());

                    if (Global.getCombatEngine().getTotalElapsedTime(false) % ARC_INTERVAL < 0.1f) {
                        generateEMPArcs(follow, sideOffset);
                    }

                    return Global.getCombatEngine().isEntityInPlay(follow);
                });
            }
        }

        private static void generateEMPArcs(CombatEntityAPI follow, Vector2f offset) {
            CombatEngineAPI engine = Global.getCombatEngine();
            Vector2f basePos = follow.getLocation();

            Vector2f spawnPos = Vector2f.add(
                    basePos,
                    MathUtils.getRandomPointInCircle(offset, 30f),
                    new Vector2f()
            );

            engine.spawnEmpArcVisual(
                    follow.getLocation(),
                    follow,
                    spawnPos,
                    follow,
                    ARC_WIDTH + MathUtils.getRandomNumberInRange(-2f, 2f),
                    java.awt.Color.red,
                    java.awt.Color.red
            );
        }
    }

    public static boolean canCollide(Object o, Collection<? extends CombatEntityAPI> ignoreList, ShipAPI source, boolean friendlyFire) {
        int owner = source == null ? 100 : source.getOwner();
        if (!(o instanceof CombatEntityAPI entity)) return false;
        if ((o instanceof DamagingProjectileAPI) && !(o instanceof MissileAPI)) return false;
        if (ignoreList != null && ignoreList.contains(o)) return false;

        if (CollisionClass.NONE.equals(entity.getCollisionClass())) return false;
        if (entity instanceof ShipAPI && ((ShipAPI) entity).isPhased()) return false;
        if (entity instanceof ShipAPI && Objects.equals(getBaseShip((ShipAPI) entity), getBaseShip(source)))
            return false;
        if (entity.getOwner() == owner && (o instanceof MissileAPI || isFighter(entity))) return false;
        return friendlyFire || entity.getOwner() != owner;
    }

    public static Pair<Vector2f, Vector2f> getShieldBounds(@NotNull ShieldAPI shield) {
        Vector2f shieldBound1 = Misc.getUnitVectorAtDegreeAngle(shield.getFacing() + shield.getActiveArc() / 2f);
        shieldBound1.scale(shield.getRadius());
        Vector2f.add(shieldBound1, shield.getLocation(), shieldBound1);
        Vector2f shieldBound2 = Misc.getUnitVectorAtDegreeAngle(shield.getFacing() - shield.getActiveArc() / 2f);
        shieldBound2.scale(shield.getRadius());
        Vector2f.add(shieldBound2, shield.getLocation(), shieldBound2);
        return new Pair<>(shieldBound1, shieldBound2);
    }

    public static ShipAPI getBaseShip(ShipAPI shipOrModule) {
        return getBaseShip(shipOrModule, new HashSet<>());
    }

    public static ShipAPI getBaseShip(ShipAPI shipOrModule, Set<ShipAPI> seenShips) {
        if (shipOrModule == null) {
            return null;
        }
        if (seenShips.contains(shipOrModule)) {
            return shipOrModule;
        }
        seenShips.add(shipOrModule);
        if (shipOrModule.isStationModule()) {
            ShipAPI base = null;
            if (shipOrModule.getParentStation() == null) {
                if (shipOrModule.getFleetMember() != null) {
                    base = shipOrModule;
                }
            } else {
                base = getBaseShip(shipOrModule.getParentStation(), seenShips);
            }
            return base;
        }
        return shipOrModule;
    }

    public static boolean isFighter(CombatEntityAPI entity) {
        return entity instanceof ShipAPI && ((ShipAPI) entity).isFighter();
    }

    public static Vector2f rayCollisionCheckShield(Vector2f a, Vector2f b, ShieldAPI shield) {
        if (shield == null) return null;

        if (Misc.getDistance(a, shield.getLocation()) <= shield.getRadius() && shield.isWithinArc(a)) {
            return a;
        }

        Pair<Vector2f, Vector2f> shieldBounds = getShieldBounds(shield);

        List<Vector2f> pts = new ArrayList<>();
        pts.add(Misc.intersectSegments(a, b, shield.getLocation(), shieldBounds.one));
        pts.add(Misc.intersectSegments(a, b, shield.getLocation(), shieldBounds.two));
        pts.addAll(intersectSegmentCircle(a, b, shield.getLocation(), shield.getRadius()));

        Vector2f closestPt = null;
        float closestDist = Float.MAX_VALUE;
        for (Vector2f pt : pts) {
            if (pt == null || !shield.isWithinArc(pt)) continue;
            float dist = Misc.getDistance(a, pt);
            if ((closestPt == null) || Misc.getDistance(a, pt) < closestDist) {
                closestPt = pt;
                closestDist = dist;
            }
        }

        return closestPt;
    }

    public static void ensureCloneVariant(FleetMemberAPI member, boolean cloneModules) {
        if (member != null) {
            if (member.getVariant() == null) {
                String hull = member.getHullId();
                if (member.getHullSpec() != null && member.getHullSpec().isDefaultDHull()) {
                    hull = member.getHullSpec().getDParentHullId();
                }

                member.setVariant(Global.getSettings().getVariant(hull + "_Hull"), false, false);
                if (member.getVariant() == null) {
                    return;
                }
            }

            if (member.getVariant().isStockVariant() || member.getVariant().isEmptyHullVariant()) {
                ShipVariantAPI newv = member.getVariant().clone();
                newv.setSource(VariantSource.REFIT);
                newv.setHullVariantId(Misc.genUID());
                member.setVariant(newv, false, true);
            }

            if (cloneModules) {
                for (String slot : member.getVariant().getModuleSlots()) {
                    ShipVariantAPI v = member.getVariant().getModuleVariant(slot);
                    if (v != null && (v.isStockVariant() || v.isEmptyHullVariant())) {
                        v = v.clone();
                        v.setSource(VariantSource.REFIT);
                        v.setHullVariantId(member.getVariant().getHullVariantId() + "_" + Misc.genUID());
                        member.getVariant().setModuleVariant(slot, v);
                    }
                }
            }
        }
    }

    public static List<Vector2f> intersectSegmentCircle(Vector2f a, Vector2f b, Vector2f o, float r) {
        List<Vector2f> pts = new ArrayList<>();

        float x1 = a.x - o.x, y1 = a.y - o.y;
        float x2 = b.x - o.x, y2 = b.y - o.y;
        float dx = x2 - x1, dy = y2 - y1, dr = (float) Math.sqrt(dx * dx + dy * dy), D = x1 * y2 - x2 * y1;

        float disc = r * r * dr * dr - D * D;
        if (disc < 0) {
            return pts;
        }

        float vx = MathPersonal.sgnPos(dy) * dx * (float) Math.sqrt(disc);
        float vy = Math.abs(dy) * (float) Math.sqrt(disc);
        float rx1 = (D * dy + vx) / (dr * dr) + o.x, rx2 = (D * dy - vx) / (dr * dr) + o.x;
        float ry1 = (-D * dx + vy) / (dr * dr) + o.y, ry2 = (-D * dx - vy) / (dr * dr) + o.y;

        float mx = Math.min(a.x, b.x), Mx = Math.max(a.x, b.x);
        float my = Math.min(a.y, b.y), My = Math.max(a.y, b.y);
        if (rx1 <= Mx && rx1 >= mx && ry1 <= My && ry1 >= my) {
            pts.add(new Vector2f(rx1, ry1));
        }
        if (rx2 <= Mx && rx2 >= mx && ry2 <= My && ry2 >= my) {
            pts.add(new Vector2f(rx2, ry2));
        }
        return pts;
    }

    public static Vector2f rayCollisionCheckBounds(Vector2f a, Vector2f b, CombatEntityAPI entity) {
        if (Misc.getDistance(a, entity.getLocation()) > entity.getCollisionRadius()
                && Misc.getDistance(b, entity.getLocation()) > entity.getCollisionRadius()
                && Misc.intersectSegmentAndCircle(a, b, entity.getLocation(), entity.getCollisionRadius()) == null) {
            return null;
        }

        return rayCollisionCheckBoundsNoEarlyExit(a, b, entity);
    }

    private static Vector2f rayCollisionCheckBoundsNoEarlyExit(Vector2f a, Vector2f b, CombatEntityAPI entity) {
        BoundsAPI bounds = entity.getExactBounds();

        if (bounds == null || entity instanceof DamagingProjectileAPI) {
            if (Misc.getDistance(a, entity.getLocation()) <= entity.getCollisionRadius()) {
                return a;
            }
            return Misc.intersectSegmentAndCircle(a, b, entity.getLocation(), entity.getCollisionRadius());
        }

        bounds.update(entity.getLocation(), entity.getFacing());
        List<BoundsAPI.SegmentAPI> segments = bounds.getSegments();

        List<Vector2f> boundVerts = new ArrayList<>();
        boundVerts.add(segments.get(0).getP1());  // 修改这里：getFirst() -> get(0)
        for (BoundsAPI.SegmentAPI segment : segments) {
            boundVerts.add(segment.getP2());
        }
        if (Misc.isPointInBounds(a, boundVerts)) {
            return a;
        }

        Vector2f closestPoint = null;
        float closestDist = Float.MAX_VALUE;
        for (BoundsAPI.SegmentAPI segment : segments) {
            Vector2f collisionPoint =
                    Misc.intersectSegments(segment.getP1(), segment.getP2(), a, b);
            if (collisionPoint != null) {
                float dist = Misc.getDistance(a, collisionPoint);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestPoint = collisionPoint;
                }
            }
        }

        return closestPoint;
    }

    public static Pair<Vector2f, Boolean> rayCollisionCheckEntity(Vector2f a, Vector2f b, CombatEntityAPI entity) {
        if (Misc.getDistance(a, entity.getLocation()) > entity.getCollisionRadius()
                && Misc.getDistance(b, entity.getLocation()) > entity.getCollisionRadius()
                && Misc.intersectSegmentAndCircle(a, b, entity.getLocation(), entity.getCollisionRadius()) == null) {
            return new Pair<>(null, false);
        }

        ShieldAPI thisShield = entity.getShield();
        Vector2f bestShieldHitPt = null;
        float bestShieldHitDist = Float.MAX_VALUE;

        List<ShieldAPI> allShields = new ArrayList<>();
        if (thisShield != null) {
            allShields.add(thisShield);
        }
        if (entity instanceof ShipAPI ship) {
            for (ShipAPI child : ship.getChildModulesCopy()) {
                if (child.getShield() != null) {
                    allShields.add(child.getShield());
                }
            }
            if (ship.getParentStation() != null) {
                ShipAPI parent = ship.getParentStation();
                if (parent.getShield() != null) {
                    allShields.add(parent.getShield());
                } else {
                    for (ShipAPI child : parent.getChildModulesCopy()) {
                        if (child.getShield() != null) {
                            allShields.add(child.getShield());
                        }
                    }
                }
            }
        }

        for (ShieldAPI shield : allShields) {
            Vector2f hitLoc = rayCollisionCheckShield(a, b, shield);
            if (hitLoc != null) {
                float dist = Misc.getDistance(a, hitLoc);
                if (dist < bestShieldHitDist) {
                    bestShieldHitDist = dist;
                    bestShieldHitPt = hitLoc;
                }
            }
        }

        Vector2f boundsHitLoc = rayCollisionCheckBounds(a, b, entity);

        if (bestShieldHitPt == null) return new Pair<>(boundsHitLoc, false);
        if (boundsHitLoc == null) return new Pair<>(bestShieldHitPt, true);

        float boundsHitDist = Misc.getDistance(a, boundsHitLoc);

        return bestShieldHitDist < boundsHitDist ? new Pair<>(bestShieldHitPt, true) : new Pair<>(boundsHitLoc, false);
    }

    public abstract static class PhaseTorpedoSecondaryExplosion {
        public static void makeStaticRing(Vector2f loc) {
            Emitter emitter = Particles.initialize(loc, "graphics/fx/explosion_ring0.png");
            emitter.setBlendMode(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL14.GL_FUNC_ADD);
            emitter.setLayer(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
            emitter.setSyncSize(true);

            Pair<Float, Float> radVelAcc = MathPersonal.getRateAndAcceleration(0f, 1500f, 1500f, 5.5f);
            emitter.size(30f, 50f);
            emitter.growthRate(radVelAcc.one * 0.9f, radVelAcc.one * 1.1f);
            emitter.growthAcceleration(radVelAcc.two * 0.9f, radVelAcc.two * 1.1f);

            emitter.facing(0f, 360f);
            emitter.color(1f, 0.75f, 0.5f, 0.8f);
            emitter.turnRate(-10f, 10f);
            emitter.life(5f, 6f);
            emitter.fadeTime(0f, 0f, 4f, 5f);
            emitter.randomHSVA(20f, 0f, 0f, 0f);
            emitter.saturationShift(-0.1f, -0.2f);
            emitter.colorValueShift(-0.1f, -0.2f);
            emitter.hueShift(-10f, 10f);
            Particles.burst(emitter, 5);
        }

        public static void makeRing(Vector2f loc, int numParticles) {
            Emitter emitter = Particles.initialize(loc, "graphics/fx/smoke32.png");
            emitter.setLayer(CombatEngineLayers.ABOVE_PARTICLES);
            emitter.setSyncSize(true);
            emitter.life(5f, 6f);
            emitter.fadeTime(0f, 0f, 4f, 5f);
            emitter.circleOffset(5f, 45f);

            Pair<Float, Float> radVelAcc = MathPersonal.getRateAndAcceleration(0f, 500f, 500f, 5.5f);
            emitter.radialVelocity(radVelAcc.one * 0.9f, radVelAcc.one * 1.1f);
            emitter.radialAcceleration(radVelAcc.two * 0.9f, radVelAcc.two * 1.1f);

            emitter.facing(0f, 360f);
            emitter.size(120f, 150f);
            emitter.growthRate(40f, 60f);
            emitter.growthAcceleration(-8f, -12f);
            emitter.turnRate(-45f, 45f);
            emitter.color(1f, 0.75f, 0.5f, 0.15f);
            emitter.randomHSVA(16f, 1.2f, 0f, 0f);
            Particles.burst(emitter, numParticles);
        }
    }

    public abstract static class Explosion {
        public static Emitter core(Vector2f loc, float scale, float dur, float[] color, String particlePath) {
            Emitter emitter = Particles.initialize(loc, particlePath);
            emitter.setSyncSize(true);
            emitter.circleOffset(0f, scale * 0.1f);
            emitter.color(color);
            emitter.facing(0f, 360f);
            emitter.fadeTime(0f, 0f, dur * 0.6f, dur * 0.7f);

            float initialSize = scale * 0.4f;
            Pair<Float, Float> growthRateAcceleration = MathPersonal.getRateAndAcceleration(initialSize, scale * 0.95f, scale, dur);
            emitter.size(initialSize * 0.9f, initialSize * 1.1f);
            emitter.growthAcceleration(growthRateAcceleration.two * 0.9f, growthRateAcceleration.two * 1.1f);
            emitter.growthRate(growthRateAcceleration.one * 0.9f, growthRateAcceleration.one * 1.1f);
            emitter.life(dur * 0.9f, dur * 1.1f);

            emitter.randomHSVA(35f, 1f, 0f, 0f);
            emitter.revolutionRate(-5f, 5f);
            emitter.saturationShift(-0.2f, -0.1f);
            emitter.turnRate(-20f, 20f);
            return emitter;
        }

        public static Emitter ring(Vector2f loc, float scale, float dur, float[] color) {
            Emitter emitter = Particles.initialize(loc, "graphics/fx/wpnxt_explosion_ring.png");
            emitter.setSyncSize(true);
            emitter.life(dur * 0.5f, dur * 0.6f);
            emitter.fadeTime(0f, 0f, dur * 0.3f, dur * 0.4f);

            float initialSize = scale * 0.2f;
            Pair<Float, Float> growthRateAndAcceleration = MathPersonal.getRateAndAcceleration(initialSize, scale * 1.8f, scale * 1.8f, dur);
            emitter.size(initialSize * 0.9f, initialSize * 1.1f);
            emitter.growthRate(growthRateAndAcceleration.one * 0.9f, growthRateAndAcceleration.one * 1.1f);
            emitter.growthAcceleration(growthRateAndAcceleration.two * 0.9f, growthRateAndAcceleration.two * 1.1f);

            emitter.color(color);
            emitter.randomHSVA(10f, 0.2f, 0f, 0f);
            return emitter;
        }

        public static Emitter debris(Vector2f loc, float scale, float dur, float[] color) {
            Emitter emitter = Particles.initialize(loc, "graphics/fx/particlealpha64sq.png");
            emitter.setSyncSize(true);
            emitter.life(dur * 0.6f, dur * 1.2f);
            emitter.fadeTime(0f, 0f, dur * 0.2f, dur * 0.3f);
            float debrisScale = (float) Math.sqrt(scale) * 1.2f;
            emitter.size(debrisScale * 0.8f, debrisScale * 1.2f);
            emitter.circleOffset(0f, scale * 0.15f);
            emitter.radialVelocity(scale * 0.2f / dur, scale * 1.3f / dur);
            emitter.growthRate(-debrisScale / dur, -debrisScale / dur);
            emitter.color(color);
            emitter.randomHSVA(15f, 0.4f, 0f, 0f);
            emitter.saturationShift(-0.2f, 0.2f);
            return emitter;
        }

        public static Emitter glow(Vector2f loc, float scale, float dur, float[] color) {
            Emitter emitter = Particles.initialize(loc, "graphics/fx/particlealpha64sq.png");
            emitter.setSyncSize(true);
            emitter.life(dur, dur * 1.2f);
            emitter.fadeTime(0f, 0f, dur * 0.7f, dur * 0.8f);

            float initialSize = scale * 1.5f;
            Pair<Float, Float> growthRateAndAcceleration = MathPersonal.getRateAndAcceleration(initialSize, initialSize, scale * 1.8f, dur);
            emitter.size(initialSize * 0.9f, initialSize * 1.1f);
            emitter.growthRate(growthRateAndAcceleration.one * 0.9f, growthRateAndAcceleration.one * 1.1f);
            emitter.growthAcceleration(growthRateAndAcceleration.two * 0.9f, growthRateAndAcceleration.two * 1.1f);

            emitter.color(color);
            return emitter;
        }

        public static void makeExplosion(Vector2f loc, float scale, int coreCount, int ringCount, int debrisCount) {
            makeExplosion(loc, scale, coreCount, ringCount, debrisCount, new float[]{1f, 0.75f, 0.5f, 0.3f}, new float[]{1f, 0.75f, 0.5f, 1f}, new float[]{1f, 0.5f, 0.2f, 0.3f}, new float[]{1f, 0.75f, 0.5f, 1f});
        }

        public static void makeExplosion(Vector2f loc, float scale, float dur, int coreCount, int ringCount, int debrisCount) {
            makeExplosion(loc, scale, dur, coreCount, ringCount, debrisCount, new float[]{1f, 0.75f, 0.5f, 0.3f}, new float[]{1f, 0.75f, 0.5f, 1f}, new float[]{1f, 0.5f, 0.2f, 0.3f}, new float[]{1f, 0.75f, 0.5f, 1f});
        }

        public static void makeExplosion(Vector2f loc, float scale, int coreCount, int ringCount, int debrisCount, float[] coreColor, float[] ringColor, float[] debrisColor, float[] glowColor) {
            makeExplosion(loc, scale, coreCount, ringCount, debrisCount, coreColor, ringColor, debrisColor, glowColor, "graphics/fx/explosion3.png");
        }

        public static void makeExplosion(Vector2f loc, float scale, float dur, int coreCount, int ringCount, int debrisCount, float[] coreColor, float[] ringColor, float[] debrisColor, float[] glowColor) {
            makeExplosion(loc, scale, dur, coreCount, ringCount, debrisCount, coreColor, ringColor, debrisColor, glowColor, "graphics/fx/explosion3.png");
        }

        public static void makeExplosion(Vector2f loc, float scale, int coreCount, int ringCount, int debrisCount, float[] coreColor, float[] ringColor, float[] debrisColor, float[] glowColor, String particlePath) {
            makeExplosion(loc, scale, 1.5f, coreCount, ringCount, debrisCount, coreColor, ringColor, debrisColor, glowColor, particlePath);
        }

        public static void makeExplosion(Vector2f loc, float scale, float dur, int coreCount, int ringCount, int debrisCount, float[] coreColor, float[] ringColor, float[] debrisColor, float[] glowColor, String particlePath) {
            Particles.burst(core(loc, scale, dur, coreColor, particlePath), coreCount);
            Particles.burst(ring(loc, scale, dur, ringColor), ringCount);
            Particles.burst(debris(loc, scale, dur, debrisColor), debrisCount);
            Particles.burst(glow(loc, scale, dur, glowColor), 1);
        }
    }

    public static void spawnFakeMine(Vector2f loc, float fakeRadius, float fakeDamageAmount, DamageType fakeDamageType, float dur) {
        String dummyWeapon = RSModPlugin.GiaoMissileWeapon;
        MissileSpecAPI dummyProjSpec = (MissileSpecAPI) Global.getSettings().getWeaponSpec(dummyWeapon).getProjectileSpec();
        dummyProjSpec.getDamage().setDamage(fakeDamageAmount);
        dummyProjSpec.setLaunchSpeed(0f);
        dummyProjSpec.getDamage().setType(fakeDamageType);

        final MissileAPI dummyProj = (MissileAPI) Global.getCombatEngine().spawnProjectile(null, null, dummyWeapon, loc, 0f, new Vector2f());
        dummyProj.setMine(true);
        dummyProj.setNoMineFFConcerns(true);
        dummyProj.setMinePrimed(true);
        dummyProj.setUntilMineExplosion(0f);
        dummyProj.setMineExplosionRange(fakeRadius);
        CombatPlugin.queueAction(() -> Global.getCombatEngine().removeEntity(dummyProj), dur);
    }

    public static void spawnInstantaneousExplosion(Vector2f loc, float radius, float damageAmount, float empAmount, DamageType damageType, DamagingProjectileAPI projSource, Set<CombatEntityAPI> alreadyDamaged, CombatEngineAPI engine) {
        Iterator<Object> itr = engine.getAllObjectGrid().getCheckIterator(loc, 2f * radius, 2f * radius);
        ShipAPI source = projSource.getSource();
        if (alreadyDamaged == null) {
            alreadyDamaged = new HashSet<>();
        } else {
            alreadyDamaged = new HashSet<>(alreadyDamaged);
        }
        while (itr.hasNext()) {
            Object o = itr.next();
            if (!canCollide(o, null, source, true)) continue;
            CombatEntityAPI entity = (CombatEntityAPI) o;
            if (alreadyDamaged.contains(entity)) continue;

            alreadyDamaged.add(entity);
            Pair<Vector2f, Boolean> pair =
                    rayCollisionCheckEntity(
                            loc,
                            entity instanceof ShipAPI && !((ShipAPI) entity).isFighter() ? MathPersonal.getVertexCenter(entity) : entity.getLocation(),
                            entity);
            if (pair.one == null) continue;
            float dist = Misc.getDistance(pair.one, loc);
            if (dist > radius) continue;
            float damage = damageAmount * 0.5f + damageAmount * 0.5f * (radius - dist) / radius;
            float emp = empAmount * 0.5f + empAmount * 0.5f * (radius - dist) / radius;

            engine.applyDamage(entity, entity, pair.one, damage, damageType, emp, false, false, source, true);
        }
    }

    public static void addPenetratingProjectile(DamagingProjectileAPI proj,CombatEntityAPI target,DamageType damageType) {
        proj.setCollisionClass(CollisionClass.NONE);
        new_proj.add(proj);
        float timer = 0;
        while (timer >= 0.01) {
            timer -= 0.01f;
            Global.getCombatEngine().applyDamage(target, target, proj.getLocation(), proj.getDamageAmount(), damageType, proj.getDamageAmount()/2, false, false, proj.getSource(), true);
        }
    }
    public static boolean isTreatenedbyProjectile(ShipAPI ship,float search_range){

        List<DamagingProjectileAPI> near = CombatUtils.getProjectilesWithinRange(ship.getLocation(),search_range);
        List<MissileAPI> near_missiles = CombatUtils.getMissilesWithinRange(ship.getLocation(),search_range);
        if (!near.isEmpty()) {
            float threatPoint = 0f;

            for (MissileAPI m : near_missiles) {
                if (m.getOwner() != ship.getOwner()) {
                    if (!m.didDamage() && !m.isFading()) {

                        float speed = m.getVelocity().length();
                        float dist = Misc.getDistance(ship.getLocation(), m.getLocation()) - ship.getCollisionRadius();
                        //先不考虑弹丸的飞行方向，距离/速度小于2秒认为有必要计算威胁度 （视需要提升火降低秒数或者直接移除这个判定）
                        if (dist < speed * 2) {
                            //弹丸命中飞船所需要的攻击角度
                            float angle = (float) Math.abs(Math.toDegrees(Math.acos(ship.getCollisionRadius() / dist)));
                            //弹丸速度的方向与飞船连线的夹角
                            float facing = Math.abs(normalizeAngle(VectorUtils.getFacing(m.getVelocity()) - VectorUtils.getAngle(m.getLocation(), ship.getLocation())));
                            if (facing < angle) {
                                float damage = m.getDamageType().getArmorMult() * m.getDamageAmount() + m.getEmpAmount();
                                //用伤害*距离系数判断威胁的大小，叠加到总威胁点数上
                                threatPoint += damage * (1+(search_range - dist) / search_range);
                            }
                        }
                    }
                }
            }


            for (DamagingProjectileAPI p : near) {
                if (p.getOwner() != ship.getOwner()) {
                    if (!p.didDamage() && !p.isFading()) {

                        float speed = p.getVelocity().length();
                        float dist = Misc.getDistance(ship.getLocation(), p.getLocation()) - ship.getCollisionRadius();
                        //先不考虑弹丸的飞行方向，距离/速度小于2秒认为有必要计算威胁度 （视需要提升火降低秒数或者直接移除这个判定）
                        if (dist < speed * 2) {

                            //弹丸命中飞船所需要的攻击角度
                            float angle = (float) Math.abs(Math.toDegrees(Math.acos(ship.getCollisionRadius() / dist)));
                            //弹丸速度的方向与飞船连线的夹角
                            float facing = Math.abs(normalizeAngle(VectorUtils.getFacing(p.getVelocity()) - VectorUtils.getAngle(p.getLocation(), ship.getLocation())));
                            if (facing < angle) {

                                float damage = p.getDamageType().getArmorMult() * p.getDamageAmount() + p.getEmpAmount();
                                //用伤害*距离系数判断威胁的大小，叠加到总威胁点数上
                                threatPoint += damage * (1+(search_range - dist) / search_range);
                            }
                        }
                    }
                }
            }
            return threatPoint >= 750;
        }
        return false;
    }

    public static boolean  BeamweaponFiring(ShipAPI ship, float search_range){
        List<WeaponAPI> weapons;
        List<ShipAPI> nearship = AIUtils.getNearbyEnemies(ship,search_range);
        ShipAPI Primary_threat = ship.getShipTarget();
        boolean beamthreat = false;
        if(Primary_threat!=null)
        {
            if(IsAimedByBeam(ship,Primary_threat))
                beamthreat = true;
        }

        for(ShipAPI enemy : nearship){

            if(enemy.getShipTarget()!=ship&& !ship.isFighter())
                continue;

            if(IsAimedByBeam(ship,enemy))
                beamthreat = true;
        }
        return beamthreat;
    }

    public  static boolean IsAimedByBeam(ShipAPI ship, ShipAPI enemy){

        List<WeaponAPI> weapons;
        Vector2f AsumeAimline = null;
        if(enemy!=null)
        {
            weapons=enemy.getAllWeapons();
            for(WeaponAPI w:weapons){
                if(w.isBeam()&&!w.isDecorative()&&!w.isDisabled()){
                    float beamDamage= w.getDamage().getDamage();
                    if(beamDamage>=ship.getHullSpec().getHitpoints()*0.25f || beamDamage>= 500f){
                        if(WeaponUtils.isWithinArc(ship,w))
                        {
                            AsumeAimline = new Vector2f(ship.getLocation().getX()-w.getLocation().getX(),ship.getLocation().getY()-w.getLocation().getY());

                            float Currangle=w.getCurrAngle();

                            float AsumeAimlineAngle=VectorUtils.getFacing(AsumeAimline);


                            if(Math.abs(AsumeAimlineAngle-w.getCurrAngle())<=5)
                                return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static ShipAPI addDroneattack(CombatEngineAPI engine, ShipAPI sourse, String weaponid, Vector2f renderlocation,float offsetrange, Vector2f firelocation, float firetime){
        ShipHullSpecAPI spec = Global.getSettings().getHullSpec("dem_drone");
        ShipVariantAPI v = Global.getSettings().createEmptyVariant("dem_drone", spec);

        v.addWeapon("WS 000", weaponid);
        WeaponGroupSpec g = new WeaponGroupSpec(WeaponGroupType.LINKED);
        g.addSlot("WS 000");
        v.addWeaponGroup(g);


        //v.addWeapon("WS 001", payloadWeaponId);
        //g = new WeaponGroupSpec(WeaponGroupType.LINKED);
        //g.addSlot("WS 001");
        //v.addWeaponGroup(g);

        ShipAPI Drone = engine.createFXDrone(v);
        Drone.setLayer(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
        Drone.setOwner(sourse.getOriginalOwner());
        //Drone.setHullSize(ShipAPI.HullSize.FIGHTER);
        Drone.getMutableStats().getHullDamageTakenMult().modifyMult("dem", 0f); // so it's non-targetable
        Drone.getAIFlags().setFlag(ShipwideAIFlags.AIFlags.DRONE_MOTHERSHIP, 100000f, sourse);
        //Drone.getMutableStats().getEnergyWeaponDamageMult().applyMods(sourse.getMutableStats().getMissileWeaponDamageMult());
        //Drone.getMutableStats().getMissileWeaponDamageMult().applyMods(sourse.getMutableStats().getMissileWeaponDamageMult());
        //Drone.getMutableStats().getBallisticWeaponDamageMult().applyMods(sourse.getMutableStats().getMissileWeaponDamageMult());
        Drone.setCollisionClass(CollisionClass.FIGHTER);
        Drone.giveCommand(ShipCommand.SELECT_GROUP, null, 0);
        Global.getCombatEngine().addEntity(Drone);

        Vector2f location = Misc.getPointWithinRadius(renderlocation,offsetrange);
        Drone.getLocation().set(location);

        int type = (int)Math.round(Math.random() * 3);
        engine.addLayeredRenderingPlugin(new DroneAttack_plugin(Drone,firelocation,firetime,type,1f));

        return Drone;
    }
    public static class DroneAttack_plugin extends BaseCombatLayeredRenderingPlugin {
        String id = "DroneAttack_plugin_effect";
        public ShipAPI ship ;
        private CombatEngineAPI engine = null;
        float time = 0f;
        Color color_1 = new Color(255,255,255);
        Vector2f firelocation = new Vector2f();
        boolean should_exexpired = false;
        float rendersize = 1f;
        float firetime = 2f;
        int type;
        float rotate = 0f;
        float alphamult = 0f;
        float facing = 0f;
        boolean shapedexplosionkey = false;
        private boolean init = false;
        public SpriteAPI sprite1 = Global.getSettings().getSprite("fx", "RING");
        public SpriteAPI sprite2 = Global.getSettings().getSprite("fx", "RING");

        //private WaveDistortion wave = null;
        public DroneAttack_plugin(ShipAPI drone , Vector2f firelocation ,float firetime,int type,float rendersize) {
            this.rendersize = rendersize;
            this.ship = drone;
            this.firelocation = firelocation;
            this.layer = CombatEngineLayers.ABOVE_PARTICLES;
            this.firetime = firetime;
            this.type = type;
            switch (type){
                case 1 : {sprite1 = Global.getSettings().getSprite("fx", "RING");break;}
                case 2 : {sprite1 = Global.getSettings().getSprite("fx", "RING");break;}
                case 3 : {sprite1 = Global.getSettings().getSprite("fx", "RING");break;}
            }
        }
        public boolean shouldPause() {
            if (engine == null) return true;
            return engine.isPaused();
        }

        @Override
        public boolean isExpired() {
            return should_exexpired;
        }

        @Override
        public void advance(float amount) {
            if (engine == null) {
                engine = Global.getCombatEngine();
            }
            if (engine.isPaused() || should_exexpired) {
                return;
            }

            if (shouldPause()) return;

            engine = Global.getCombatEngine();

            time += amount;

            WeaponAPI wea = ship.getAllWeapons().get(0);
            if (wea == null) {
                wea = ship.getAllWeapons().get(0);
            }

            ship.fadeToColor(this,Misc.zeroColor,0.01f,0.01f,1f);

            if(wea.getSprite() != null){wea.getSprite().setAlphaMult(0f);}
            if(wea.getAnimation() != null){wea.getAnimation().setAlphaMult(0f);}
            if(wea.getGlowSpriteAPI() != null){wea.getGlowSpriteAPI().setAlphaMult(0f);}
            if(wea.getBarrelSpriteAPI() != null){wea.getBarrelSpriteAPI().setAlphaMult(0f);}
            if(wea.getUnderSpriteAPI() != null){wea.getUnderSpriteAPI().setAlphaMult(0f);}
            ship.setWeaponGlow(1f, Misc.zeroColor, EnumSet.allOf(WeaponAPI.WeaponType.class));



            rotate += amount * 60f;

            if(time >= firetime + 3f){
                Global.getCombatEngine().removeEntity(ship);
                should_exexpired = true;
                return;
            }

            if (time < firetime * 0.25f) {
                alphamult = Math.min(100f, alphamult + 300f * amount);
            } else if (time >= firetime) {
                alphamult = Math.max(0f, alphamult - 200f * amount);
            }
            if(time >= firetime * 0.25f && time <= firetime){
                facing = Misc.getAngleInDegrees(ship.getLocation(),firelocation);
                WeaponAPI payload = ship.getWeaponGroupsCopy().get(0).getWeaponsCopy().get(0);
                payload.setFacing(facing);
                ship.giveCommand(ShipCommand.FIRE, firelocation, 0);
                if(!shapedexplosionkey){
                    shapedexplosionkey = true;
                    spawnShapedExplosion(ship,ship.getLocation(),facing);
                }


            }else if(time >= firetime){      //赞美伟大的机魂
                ship.giveCommand(ShipCommand.HOLD_FIRE,null,0);
                if(alphamult >= 3){alphamult -= 200f * amount;}
                else alphamult = 0f;
            }

        }
        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            // 提前检查是否在视口内
            if (!viewport.isNearViewport(ship.getLocation(), 500f)) {
                return;
            }

            // 批处理渲染
            renderBothEffects(viewport);
        }

        private void renderBothEffects(ViewportAPI viewport) {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);

            // 绑定纹理一次，渲染两个效果
            sprite1.bindTexture();
            renderSingleEffect(1.0f, 0f, false);
            renderSingleEffect(3.0f, 20f, true);

            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        private void renderSingleEffect(float sizeMultiplier, float zOffset, boolean reverseRotation) {
            float alpha = color_1.getAlpha() * alphamult / 100f;
            if (alpha <= 0.01f) return;

            GL11.glPushMatrix();
            GL11.glTranslatef(ship.getLocation().x, ship.getLocation().y, 0f);
            GL11.glRotatef(Misc.getAngleInDegrees(ship.getLocation(), firelocation), 0f, 0f, 1f);
            GL11.glRotatef(75f, 0f, 1f, 0f);
            GL11.glRotatef(reverseRotation ? -rotate : rotate, 0f, 0f, 1f);

            float r = 32f * rendersize * sizeMultiplier;

            // 使用顶点数组而不是立即模式（如果支持）
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4ub((byte) color_1.getRed(), (byte) color_1.getGreen(),
                    (byte) color_1.getBlue(), (byte) alpha);

            GL11.glTexCoord2f(0f, 0f);
            GL11.glVertex3f(-0.5f * r, -0.5f * r, zOffset);

            GL11.glTexCoord2f(1f, 0f);
            GL11.glVertex3f(0.5f * r, -0.5f * r, zOffset);

            GL11.glTexCoord2f(1f, 1f);
            GL11.glVertex3f(0.5f * r, 0.5f * r, zOffset);

            GL11.glTexCoord2f(0f, 1f);
            GL11.glVertex3f(-0.5f * r, 0.5f * r, zOffset);

            GL11.glEnd();
            GL11.glPopMatrix();
        }

        @Override
        public float getRenderRadius() {
            // 返回实际需要的渲染半径，减少不必要的渲染调用
            return 500f;
        }

        public void spawnShapedExplosion(ShipAPI ship,Vector2f loc, float angle) {
/*龙炎
        "shapedExplosionNumParticles":50,
		"shapedExplosionMinParticleDur":0.7,
		"shapedExplosionMaxParticleDur":1.1,
		"shapedExplosionMinParticleSize":50,
		"shapedExplosionMaxParticleSize":70,
		"shapedExplosionArc":45,
		"shapedExplosionMinParticleVel":50,
		"shapedExplosionMaxParticleVel":250,
		"shapedExplosionColor":[255,40,40,155],
		"shapedExplosionEndSizeMin":1,
		"shapedExplosionEndSizeMax":2,
		"shapedExplosionScatter":100,
 */
            if (Global.getCombatEngine().getViewport().isNearViewport(ship.getLocation(), 800f)) {
                if (!Global.getCombatEngine().getViewport().isNearViewport(ship.getLocation(), 800f)) {
                    return;
                }
                int numParticles = 50;//p.shapedExplosionNumParticles;

                float minDur = 0.7f;//p.shapedExplosionMinParticleDur;
                float maxDur = 1.1f;//p.shapedExplosionMaxParticleDur;
                float minSize = 10f;//p.shapedExplosionMinParticleSize;
                float maxSize = 15f;//p.shapedExplosionMaxParticleSize;

                Color pc = new Color(250,190,255,155);//p.shapedExplosionColor;
                float arc = 45f;//p.shapedExplosionArc;
                float minVel = 10f;//p.shapedExplosionMinParticleVel;
                float maxVel = 25f;//p.shapedExplosionMaxParticleVel;

                float scatter = 100f;//p.shapedExplosionScatter;
                float endSizeMin = 0.2f;//p.shapedExplosionEndSizeMin;
                float endSizeMax = 0.4f;//p.shapedExplosionEndSizeMax;

                Vector2f spawnPoint = new Vector2f(loc);
                for (int i = 0; i < numParticles; i++) {
                    //p.setMaxAge(500 + (int)(Math.random() * 1000f));
                    float angleOffset = (float) Math.random();
                    if (angleOffset > 0.2f) {
                        angleOffset *= angleOffset;
                    }
                    float speedMult = 1f - angleOffset;
                    speedMult = 0.5f + speedMult * 0.5f;
                    angleOffset *= Math.signum((float) Math.random() - 0.5f);
                    angleOffset *= arc/2f;
                    float theta = (float) Math.toRadians(angle + angleOffset);
                    float r = (float) (Math.random() * Math.random() * scatter);
                    float x = (float)Math.cos(theta) * r;
                    float y = (float)Math.sin(theta) * r;
                    Vector2f pLoc = new Vector2f(spawnPoint.x + x, spawnPoint.y + y);

                    float speed = minVel + (maxVel - minVel) * (float) Math.random();
                    speed *= speedMult;

                    Vector2f pVel = Misc.getUnitVectorAtDegreeAngle((float) Math.toDegrees(theta));
                    pVel.scale(speed);

                    float pSize = minSize + (maxSize - minSize) * (float) Math.random();
                    float pDur = minDur + (maxDur - minDur) * (float) Math.random();
                    float endSize = endSizeMin + (endSizeMax - endSizeMin) * (float) Math.random();
                    //Global.getCombatEngine().addSmoothParticle(pLoc, pVel, pSize, 1f, pDur, pc);
                    Global.getCombatEngine().addNebulaParticle(pLoc, pVel, pSize, endSize, 0.1f, 0.5f, pDur, pc);
                    //Global.getCombatEngine().addNebulaSmoothParticle(pLoc, pVel, pSize, endSize, 0.1f, 0.5f, pDur, pc);
                    //Global.getCombatEngine().addSwirlyNebulaParticle(pLoc, pVel, pSize, endSize, 0.1f, 0.5f, pDur, pc, false);
                }
            }
        }
    }

    public static void safelyRemoveWeaponFromGroups(
            CombatEngineAPI engine,
            ShipAPI ship,
            WeaponAPI weapon,
            boolean disablePermanently) {

        if (engine == null || ship == null || weapon == null) {
            log.warn("Unable to remove weapon: parameter is null");
            return;
        }

        // 注册临时插件
        engine.addPlugin(new BaseEveryFrameCombatPlugin() {
            @Override
            public void advance(float amount, List<InputEventAPI> events) {
                try {
                    // 执行移除操作
                    ship.removeWeaponFromGroups(weapon);

                    // 禁用武器
                    if (disablePermanently) {
                        weapon.disable(true);
                    }

                    log.info("Weapon removed successfully:" + weapon.getId() +
                            ", slot:" + weapon.getSlot().getId());

                } catch (Exception e) {
                    log.error("Failed to remove weapon:" + weapon.getId(), e);
                } finally {
                    // 无论成功失败，都移除插件
                    engine.removePlugin(this);
                }
            }
        });
    }

    /**
     * 批量移除武器（通过临时全局插件）
     *
     * @param engine 战斗引擎
     * @param ship 舰船
     * @param weapons 要移除的武器列表
     * @param disablePermanently 是否永久禁用武器
     */
    public static void safelyRemoveWeaponsFromGroups(
            CombatEngineAPI engine,
            ShipAPI ship,
            List<WeaponAPI> weapons,
            boolean disablePermanently) {

        if (engine == null || ship == null || weapons == null || weapons.isEmpty()) {
            log.warn("Unable to batch remove weapons: parameter is null or list is empty");
            return;
        }

        // 注册临时插件
        engine.addPlugin(new BaseEveryFrameCombatPlugin() {
            @Override
            public void advance(float amount, List<InputEventAPI> events) {
                try {
                    int successCount = 0;

                    for (WeaponAPI weapon : weapons) {
                        if (weapon != null) {
                            ship.removeWeaponFromGroups(weapon);

                            if (disablePermanently) {
                                weapon.disable(true);
                            }

                            successCount++;
                        }
                    }

                    log.info("successfully removed" + successCount + "weapons, ships:" + ship.getName());

                } catch (Exception e) {
                    log.error("Failed to remove weapons in batches", e);
                } finally {
                    // 无论成功失败，都移除插件
                    engine.removePlugin(this);
                }
            }
        });
    }

    /**
     * 根据槽位ID移除武器（通过临时全局插件）
     *
     * @param engine 战斗引擎
     * @param ship 舰船
     * @param slotId 槽位ID
     * @param disablePermanently 是否永久禁用武器
     */
    public static void safelyRemoveWeaponBySlotId(
            CombatEngineAPI engine,
            ShipAPI ship,
            String slotId,
            boolean disablePermanently) {

        if (engine == null || ship == null || slotId == null) {
            log.warn("Unable to remove weapon based on slot: parameter is null");
            return;
        }

        // 注册临时插件
        engine.addPlugin(new BaseEveryFrameCombatPlugin() {
            @Override
            public void advance(float amount, List<InputEventAPI> events) {
                try {
                    WeaponAPI targetWeapon = null;

                    // 查找目标武器
                    for (WeaponAPI weapon : ship.getAllWeapons()) {
                        if (slotId.equals(weapon.getSlot().getId())) {
                            targetWeapon = weapon;
                            break;
                        }
                    }

                    // 移除武器
                    if (targetWeapon != null) {
                        ship.removeWeaponFromGroups(targetWeapon);

                        if (disablePermanently) {
                            targetWeapon.disable(true);
                        }

                        log.info("Successfully removed slot weapon:" + slotId);
                    } else {
                        log.warn("Slot weapon not found:" + slotId);
                    }

                } catch (Exception e) {
                    log.error("Removing weapons based on slot fails:" + slotId, e);
                } finally {
                    // 无论成功失败，都移除插件
                    engine.removePlugin(this);
                }
            }
        });
    }

    /**
     * 根据槽位ID前缀批量移除武器（通过临时全局插件）
     *
     * @param engine 战斗引擎
     * @param ship 舰船
     * @param slotIdPrefix 槽位ID前缀（例如 "DECO_" 会匹配所有以此开头的槽位）
     * @param disablePermanently 是否永久禁用武器
     */
    public static void safelyRemoveWeaponsBySlotPrefix(
            CombatEngineAPI engine,
            ShipAPI ship,
            String slotIdPrefix,
            boolean disablePermanently) {

        if (engine == null || ship == null || slotIdPrefix == null) {
            log.warn("Unable to remove weapon based on slot prefix: parameter is null");
            return;
        }

        // 注册临时插件
        engine.addPlugin(new BaseEveryFrameCombatPlugin() {
            @Override
            public void advance(float amount, List<InputEventAPI> events) {
                try {
                    int successCount = 0;

                    // 查找所有匹配前缀的武器
                    for (WeaponAPI weapon : ship.getAllWeapons()) {
                        String slotId = weapon.getSlot().getId();
                        if (slotId != null && slotId.startsWith(slotIdPrefix)) {
                            ship.removeWeaponFromGroups(weapon);

                            if (disablePermanently) {
                                weapon.disable(true);
                            }

                            successCount++;
                        }
                    }

                    log.info("successfully removed" + successCount + "prefixed with '" + slotIdPrefix + "' weapons");

                } catch (Exception e) {
                    log.error("Batch removal of weapons based on slot prefix failed:" + slotIdPrefix, e);
                } finally {
                    // 无论成功失败，都移除插件
                    engine.removePlugin(this);
                }
            }
        });
    }


    public static final String TAG_NO_RECOIL = "NO_RECOIL";
    public static final String TAG_HIDE_RECOIL = "HIDE_RECOIL";
    public static final String TAG_FOLLOW_RECOIL = "FOLLOW_RECOIL";

    /**
     * 装饰武器后坐力状态
     * 记录武器各层sprite的原始中心点，避免每帧累积偏移。
     */
    public static class SpriteRecoilState {
        public static class LayerState {
            public boolean initialized = false;
            public float originalCenterX = 0f;
            public float originalCenterY = 0f;
        }

        public boolean initialized = false;
        public final LayerState main = new LayerState();
        public final LayerState barrel = new LayerState();
        public final LayerState under = new LayerState();
        public final LayerState glow = new LayerState();
    }

    /**
     * 捕获武器sprite的原始中心点。
     */
    public static void captureSpriteRecoilOrigin(WeaponAPI weapon, SpriteRecoilState state) {
        if (weapon == null || state == null || state.initialized) {
            return;
        }

        captureSpriteLayerOrigin(weapon.getSprite(), state.main);
        captureSpriteLayerOrigin(weapon.getBarrelSpriteAPI(), state.barrel);
        captureSpriteLayerOrigin(weapon.getUnderSpriteAPI(), state.under);
        captureSpriteLayerOrigin(weapon.getGlowSpriteAPI(), state.glow);
        state.initialized = state.main.initialized
                || state.barrel.initialized
                || state.under.initialized
                || state.glow.initialized;
    }

    /**
     * 按武器开火/冷却进度对装饰武器施加sprite后坐力。
     * 对 burst beam 一类武器，满充进入照射的瞬间会立即进入最大后坐，
     * 待照射结束后再按冷却进度回位。
     * recoilX/recoilY 是相对原始中心点的本地sprite偏移量。
     */
    public static void applyCooldownSpriteRecoil(WeaponAPI decorativeWeapon, WeaponAPI sourceWeapon,
                                                 SpriteRecoilState state, float recoilX, float recoilY) {
        if (decorativeWeapon == null || state == null) {
            return;
        }

        captureSpriteRecoilOrigin(decorativeWeapon, state);
        if (!state.initialized) {
            return;
        }

        float recoilLevel = getWeaponRecoilLevel(sourceWeapon);

        float offsetX = recoilX * recoilLevel;
        float offsetY = recoilY * recoilLevel;
        applySpriteLayerRecoil(decorativeWeapon.getSprite(), state.main, offsetX, offsetY);
        applySpriteLayerRecoil(decorativeWeapon.getBarrelSpriteAPI(), state.barrel, offsetX, offsetY);
        applySpriteLayerRecoil(decorativeWeapon.getUnderSpriteAPI(), state.under, offsetX, offsetY);
        applySpriteLayerRecoil(decorativeWeapon.getGlowSpriteAPI(), state.glow, offsetX, offsetY);
    }

    public static float getWeaponRecoilLevel(WeaponAPI sourceWeapon) {
        if (sourceWeapon == null) {
            return 0f;
        }

        // Burst beam/持续照射武器在真正出光的瞬间就后坐，而不是等整段照射结束后才触发。
        if (sourceWeapon.isBeam() && sourceWeapon.isFiring() && sourceWeapon.getChargeLevel() >= 1f) {
            return 1f;
        }

        if (sourceWeapon.getCooldown() <= 0f) {
            return 0f;
        }

        float recoilLevel = sourceWeapon.getCooldownRemaining() / sourceWeapon.getCooldown();
        return Math.max(0f, Math.min(1f, recoilLevel));
    }

    /**
     * 重置装饰武器sprite中心点到原始位置。
     */
    public static void resetSpriteRecoil(WeaponAPI weapon, SpriteRecoilState state) {
        if (weapon == null || state == null) {
            return;
        }

        captureSpriteRecoilOrigin(weapon, state);
        if (!state.initialized) {
            return;
        }

        resetSpriteLayerRecoil(weapon.getSprite(), state.main);
        resetSpriteLayerRecoil(weapon.getBarrelSpriteAPI(), state.barrel);
        resetSpriteLayerRecoil(weapon.getUnderSpriteAPI(), state.under);
        resetSpriteLayerRecoil(weapon.getGlowSpriteAPI(), state.glow);
    }

    private static void captureSpriteLayerOrigin(SpriteAPI sprite, SpriteRecoilState.LayerState layerState) {
        if (sprite == null || layerState == null || layerState.initialized) {
            return;
        }

        layerState.originalCenterX = sprite.getCenterX();
        layerState.originalCenterY = sprite.getCenterY();
        layerState.initialized = true;
    }

    private static void applySpriteLayerRecoil(SpriteAPI sprite, SpriteRecoilState.LayerState layerState,
                                               float offsetX, float offsetY) {
        if (sprite == null || layerState == null || !layerState.initialized) {
            return;
        }

        sprite.setCenterX(layerState.originalCenterX + offsetX);
        sprite.setCenterY(layerState.originalCenterY + offsetY);
    }

    private static void resetSpriteLayerRecoil(SpriteAPI sprite, SpriteRecoilState.LayerState layerState) {
        if (sprite == null || layerState == null || !layerState.initialized) {
            return;
        }

        sprite.setCenterX(layerState.originalCenterX);
        sprite.setCenterY(layerState.originalCenterY);
    }

    public static boolean hasWeaponTag(WeaponAPI weapon, String tag) {
        return weapon != null && weapon.getSpec() != null && tag != null && weapon.getSpec().hasTag(tag);
    }

    public static boolean hasAnyWeaponTag(String tag, WeaponAPI... weapons) {
        if (tag == null || weapons == null) {
            return false;
        }

        for (WeaponAPI weapon : weapons) {
            if (hasWeaponTag(weapon, tag)) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldDisableRecoil(WeaponAPI... weapons) {
        return hasAnyWeaponTag(TAG_NO_RECOIL, weapons);
    }

    public static boolean shouldApplyHiddenSourceRecoil(WeaponAPI sourceWeapon, WeaponAPI... relatedWeapons) {
        if (shouldDisableRecoil(sourceWeapon) || shouldDisableRecoil(relatedWeapons)) {
            return false;
        }
        return hasWeaponTag(sourceWeapon, TAG_HIDE_RECOIL);
    }

    /**
     * 判断舰船系统是否处于开启状态。
     */
    public static boolean isShipSystemActive(ShipAPI ship) {
        return ship != null && ship.getSystem() != null && (ship.getSystem().isOn() || ship.getSystem().isActive());
    }

    /**
     * 判断舰船是否正在散热或过载。
     */
    public static boolean isVentingOrOverloaded(ShipAPI ship) {
        return ship != null && ship.getFluxTracker() != null &&
                (ship.getFluxTracker().isVenting() || ship.getFluxTracker().isOverloaded());
    }

    /**
     * 系统开启时返回设定加成，否则返回 0。
     */
    public static float getSystemActiveBonus(ShipAPI ship, float activeBonus) {
        return isShipSystemActive(ship) ? activeBonus : 0f;
    }

    /**
     * 基础特效数值计算：base + primary * primaryBonus + additive * additiveBonus。
     */
    public static float computeEffectValue(
            float baseValue,
            float primaryLevel,
            float primaryBonus,
            float additiveLevel,
            float additiveBonus
    ) {
        return baseValue + primaryLevel * primaryBonus + additiveLevel * additiveBonus;
    }

    /**
     * 基础特效数值计算：在单一主输入之外，额外叠加第二输入。
     */
    public static float computeEffectValue(
            float baseValue,
            float primaryLevel,
            float primaryBonus,
            float secondaryLevel,
            float secondaryBonus,
            float additiveLevel,
            float additiveBonus
    ) {
        return baseValue +
                primaryLevel * primaryBonus +
                secondaryLevel * secondaryBonus +
                additiveLevel * additiveBonus;
    }

    /**
     * 对特效数值施加惩罚并设置下限，避免出现过低值。
     */
    public static float applyPenaltyWithFloor(float value, boolean applyPenalty, float penalty, float minValue) {
        float result = applyPenalty ? value - penalty : value;
        return Math.max(minValue, result);
    }

    /**
     * 基础特效数值计算：按两项输入线性叠加，并限制最大值。
     */
    public static float computeCappedEffectValue(
            float primaryLevel,
            float primaryBonus,
            float additiveLevel,
            float additiveBonus,
            float maxValue
    ) {
        return Math.min(maxValue, primaryLevel * primaryBonus + additiveLevel * additiveBonus);
    }

    /**
     * 按每秒次数触发
     * 用于控制粒子生成频率等
     *
     * @param times 每秒触发次数
     * @param amount 时间增量（通常是engine.getElapsedInLastFrame()）
     * @return 是否触发
     */
    public static boolean timesPerSec(float times, float amount) {
        return Math.random() < amount * times;
    }

    /**
     * 获取范围内的所有弹幕和导弹
     *
     * @param location 中心位置
     * @param range 范围
     * @return 弹幕列表
     */
    public static List<DamagingProjectileAPI> getProjectilesAndMissilesWithinRange(Vector2f location, float range) {
        List<DamagingProjectileAPI> result = new ArrayList<>();

        // 获取所有弹幕
        for (DamagingProjectileAPI proj : Global.getCombatEngine().getProjectiles()) {
            if (proj.getLocation() == null) continue;
            float distance = Vector2f.sub(proj.getLocation(), location, null).length();
            if (distance <= range) {
                result.add(proj);
            }
        }

        // 获取所有导弹
        for (MissileAPI missile : Global.getCombatEngine().getMissiles()) {
            if (missile.getLocation() == null) continue;
            float distance = Vector2f.sub(missile.getLocation(), location, null).length();
            if (distance <= range) {
                result.add(missile);
            }
        }

        return result;
    }

    /**
     * 获取范围内的敌方弹幕和导弹
     *
     * @param location 中心位置
     * @param range 范围
     * @param owner 己方阵营
     * @return 敌方弹幕列表
     */
    public static List<DamagingProjectileAPI> getEnemyProjectilesAndMissilesWithinRange(Vector2f location, float range, int owner) {
        List<DamagingProjectileAPI> result = new ArrayList<>();

        for (DamagingProjectileAPI proj : getProjectilesAndMissilesWithinRange(location, range)) {
            if (proj.getOwner() != owner) {
                result.add(proj);
            }
        }

        return result;
    }

    /**
     * 添加光源（使用GraphicsLib）
     * 如果GraphicsLib不可用，则不执行任何操作
     *
     * @param location 光源位置
     * @param size 光源大小
     * @param intensity 光源强度
     * @param duration 持续时间
     * @param color 光源颜色
     */
    public static void addLight(Vector2f location, float size, float intensity, float duration, Color color) {
        // 检查是否有GraphicsLib
        try {
            Class.forName("org.dark.shaders.light.LightShader");
            // 如果有GraphicsLib，使用反射调用
            Class<?> lightShaderClass = Class.forName("org.dark.shaders.light.LightShader");
            Class<?> standardLightClass = Class.forName("org.dark.shaders.light.StandardLight");

            Object light = standardLightClass.getConstructor(
                    Vector2f.class, Vector2f.class, Vector2f.class, CombatEntityAPI.class
            ).newInstance(location, new Vector2f(), new Vector2f(), null);

            standardLightClass.getMethod("setSize", float.class).invoke(light, size);
            standardLightClass.getMethod("setIntensity", float.class).invoke(light, intensity);
            standardLightClass.getMethod("setLifetime", float.class).invoke(light, duration);
            standardLightClass.getMethod("setColor", Color.class).invoke(light, color);
            standardLightClass.getMethod("fadeIn", float.class).invoke(light, 0.1f);
            standardLightClass.getMethod("fadeOut", float.class).invoke(light, duration * 0.5f);

            lightShaderClass.getMethod("addLight", standardLightClass).invoke(null, light);
        } catch (Exception e) {
            // GraphicsLib不可用，忽略
        }
    }

    /**
     * 显示浮动文本
     *
     * @param anchor 锚点实体
     * @param at 显示位置
     * @param text 文本内容
     */
    public static void showText(CombatEntityAPI anchor, Vector2f at, String text) {
        Global.getCombatEngine().addFloatingText(at, text, 25f, Color.RED, anchor, 1f, 1f);
    }

    /**
     * 显示浮动数字
     *
     * @param anchor 锚点实体
     * @param at 显示位置
     * @param amount 数字
     */
    public static void showText(CombatEntityAPI anchor, Vector2f at, float amount) {
        showText(anchor, at, "" + amount);
    }

    // ==================== Grid遍历工具方法 ====================

    /**
     * 使用Grid遍历获取范围内的导弹
     * 性能优化：使用空间索引，只检查指定区域内的导弹
     *
     * @param center 中心位置
     * @param radius 检查半径
     * @return 范围内的导弹列表
     */
    public static List<MissileAPI> getMissilesInRange(Vector2f center, float radius) {
        List<MissileAPI> result = new ArrayList<>();
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return result;

        float checkSize = radius * 2f;
        java.util.Iterator<Object> iter = engine.getMissileGrid().getCheckIterator(
                center, checkSize, checkSize
        );

        while (iter.hasNext()) {
            Object obj = iter.next();
            if (!(obj instanceof MissileAPI)) continue;

            MissileAPI missile = (MissileAPI) obj;
            if (missile.isExpired() || !engine.isEntityInPlay(missile)) continue;

            float dist = Vector2f.sub(missile.getLocation(), center, null).length();
            if (dist <= radius) {
                result.add(missile);
            }
        }

        return result;
    }

    /**
     * 使用Grid遍历获取范围内的敌方导弹
     *
     * @param center 中心位置
     * @param radius 检查半径
     * @param owner 己方阵营
     * @return 范围内的敌方导弹列表
     */
    public static List<MissileAPI> getEnemyMissilesInRange(Vector2f center, float radius, int owner) {
        List<MissileAPI> result = new ArrayList<>();
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return result;

        float checkSize = radius * 2f;
        java.util.Iterator<Object> iter = engine.getMissileGrid().getCheckIterator(
                center, checkSize, checkSize
        );

        while (iter.hasNext()) {
            Object obj = iter.next();
            if (!(obj instanceof MissileAPI)) continue;

            MissileAPI missile = (MissileAPI) obj;
            if (missile.getOwner() == owner) continue;
            if (missile.isExpired() || !engine.isEntityInPlay(missile)) continue;

            float dist = Vector2f.sub(missile.getLocation(), center, null).length();
            if (dist <= radius) {
                result.add(missile);
            }
        }

        return result;
    }

    /**
     * 使用Grid遍历获取范围内的所有弹丸（包括导弹）
     *
     * @param center 中心位置
     * @param radius 检查半径
     * @return 范围内的弹丸列表
     */
    public static List<DamagingProjectileAPI> getAllProjectilesInRange(Vector2f center, float radius) {
        List<DamagingProjectileAPI> result = new ArrayList<>();
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return result;

        float checkSize = radius * 2f;
        java.util.Iterator<Object> iter = engine.getAllObjectGrid().getCheckIterator(
                center, checkSize, checkSize
        );

        while (iter.hasNext()) {
            Object obj = iter.next();
            if (!(obj instanceof DamagingProjectileAPI)) continue;

            DamagingProjectileAPI proj = (DamagingProjectileAPI) obj;
            if (proj.isExpired() || !engine.isEntityInPlay(proj)) continue;

            float dist = Vector2f.sub(proj.getLocation(), center, null).length();
            if (dist <= radius) {
                result.add(proj);
            }
        }

        return result;
    }

    /**
     * 使用Grid遍历获取范围内的敌方弹丸（包括导弹）
     *
     * @param center 中心位置
     * @param radius 检查半径
     * @param owner 己方阵营
     * @return 范围内的敌方弹丸列表
     */
    public static List<DamagingProjectileAPI> getEnemyProjectilesInRange(Vector2f center, float radius, int owner) {
        List<DamagingProjectileAPI> result = new ArrayList<>();
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return result;

        float checkSize = radius * 2f;
        java.util.Iterator<Object> iter = engine.getAllObjectGrid().getCheckIterator(
                center, checkSize, checkSize
        );

        while (iter.hasNext()) {
            Object obj = iter.next();
            if (!(obj instanceof DamagingProjectileAPI)) continue;

            DamagingProjectileAPI proj = (DamagingProjectileAPI) obj;
            if (proj.getOwner() == owner) continue;
            if (proj.isExpired() || !engine.isEntityInPlay(proj)) continue;

            float dist = Vector2f.sub(proj.getLocation(), center, null).length();
            if (dist <= radius) {
                result.add(proj);
            }
        }

        return result;
    }

    /**
     * 使用Grid遍历获取范围内的舰船
     *
     * @param center 中心位置
     * @param radius 检查半径
     * @return 范围内的舰船列表
     */
    public static List<ShipAPI> getShipsInRange(Vector2f center, float radius) {
        List<ShipAPI> result = new ArrayList<>();
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return result;

        float checkSize = radius * 2f;
        java.util.Iterator<Object> iter = engine.getShipGrid().getCheckIterator(
                center, checkSize, checkSize
        );

        while (iter.hasNext()) {
            Object obj = iter.next();
            if (!(obj instanceof ShipAPI)) continue;

            ShipAPI ship = (ShipAPI) obj;
            if (ship.isHulk() || !engine.isEntityInPlay(ship)) continue;

            float dist = Vector2f.sub(ship.getLocation(), center, null).length();
            if (dist <= radius) {
                result.add(ship);
            }
        }

        return result;
    }

    /**
     * 使用Grid遍历获取范围内的敌方舰船
     *
     * @param center 中心位置
     * @param radius 检查半径
     * @param owner 己方阵营
     * @return 范围内的敌方舰船列表
     */
    public static List<ShipAPI> getEnemyShipsInRange(Vector2f center, float radius, int owner) {
        List<ShipAPI> result = new ArrayList<>();
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return result;

        float checkSize = radius * 2f;
        java.util.Iterator<Object> iter = engine.getShipGrid().getCheckIterator(
                center, checkSize, checkSize
        );

        while (iter.hasNext()) {
            Object obj = iter.next();
            if (!(obj instanceof ShipAPI)) continue;

            ShipAPI ship = (ShipAPI) obj;
            if (ship.getOwner() == owner) continue;
            if (ship.isHulk() || !engine.isEntityInPlay(ship)) continue;

            float dist = Vector2f.sub(ship.getLocation(), center, null).length();
            if (dist <= radius) {
                result.add(ship);
            }
        }

        return result;
    }

    /**
     * 使用Grid遍历获取范围内的小行星
     *
     * @param center 中心位置
     * @param radius 检查半径
     * @return 范围内的小行星列表
     */
    public static List<CombatEntityAPI> getAsteroidsInRange(Vector2f center, float radius) {
        List<CombatEntityAPI> result = new ArrayList<>();
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return result;

        float checkSize = radius * 2f;
        java.util.Iterator<Object> iter = engine.getAsteroidGrid().getCheckIterator(
                center, checkSize, checkSize
        );

        while (iter.hasNext()) {
            Object obj = iter.next();
            if (!(obj instanceof CombatEntityAPI)) continue;

            CombatEntityAPI asteroid = (CombatEntityAPI) obj;
            if (!engine.isEntityInPlay(asteroid)) continue;

            float dist = Vector2f.sub(asteroid.getLocation(), center, null).length();
            if (dist <= radius) {
                result.add(asteroid);
            }
        }

        return result;
    }

    /**
     * 使用Grid遍历获取矩形区域内的导弹
     * 适用于光束、光剑等线性武器的检测
     *
     * @param center 中心位置
     * @param width 宽度
     * @param height 高度
     * @return 区域内的导弹列表
     */
    public static List<MissileAPI> getMissilesInRect(Vector2f center, float width, float height) {
        List<MissileAPI> result = new ArrayList<>();
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return result;

        java.util.Iterator<Object> iter = engine.getMissileGrid().getCheckIterator(
                center, width, height
        );

        while (iter.hasNext()) {
            Object obj = iter.next();
            if (!(obj instanceof MissileAPI)) continue;

            MissileAPI missile = (MissileAPI) obj;
            if (missile.isExpired() || !engine.isEntityInPlay(missile)) continue;

            result.add(missile);
        }

        return result;
    }

    /**
     * 获取范围内的所有舰船（不包括穿梭机）
     * 兼容SWP_Util的方法名
     *
     * @param location 中心位置
     * @param range 范围
     * @return 舰船列表
     */
    public static List<ShipAPI> getShipsWithinRange(Vector2f location, float range) {
        List<ShipAPI> ships = new ArrayList<>();

        for (ShipAPI tmp : Global.getCombatEngine().getShips()) {
            // 跳过穿梭机
            if (tmp.isShuttlePod()) {
                continue;
            }

            // 检查距离
            if (MathUtils.isWithinRange(tmp, location, range)) {
                ships.add(tmp);
            }
        }

        return ships;
    }

    /**
     * 获取最近的伤害点
     * 用于计算碰撞检测的最近点
     *
     * @param source 源点
     * @param entity 目标实体
     * @return 最近点
     */
    public static Vector2f getNearestPointForDamage(Vector2f source, CombatEntityAPI entity) {
        // 如果是弹丸，直接返回位置
        if (entity instanceof DamagingProjectileAPI) {
            return entity.getLocation();
        }

        // 否则返回边界上的最近点
        return CollisionUtils.getNearestPointOnBounds(source, entity);
    }

    /**
     * 过滤被遮挡的目标
     * 用于区域伤害时排除被护盾或其他物体遮挡的目标
     *
     * @param primaryTarget 主要目标（直接命中的目标）
     * @param originPoint 爆炸原点
     * @param nearbyTargets 附近目标列表（会被修改）
     * @param filterModules 是否过滤模块（如果主目标是母舰，则移除其模块）
     * @param filterShielded 是否过滤被护盾保护的目标
     * @param filterBlocked 是否过滤被其他物体遮挡的目标
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void filterObscuredTargets(CombatEntityAPI primaryTarget, Vector2f originPoint,
                                             List nearbyTargets, boolean filterModules,
                                             boolean filterShielded, boolean filterBlocked) {
        Iterator<CombatEntityAPI> iter = nearbyTargets.iterator();

        while (iter.hasNext()) {
            CombatEntityAPI nearbyTarget = iter.next();

            // 跳过无碰撞的实体
            if (nearbyTarget.getCollisionClass() == CollisionClass.NONE) {
                iter.remove();
                continue;
            }

            // 过滤模块：如果目标是主目标的模块，则移除
            if (filterModules && (nearbyTarget instanceof ShipAPI)) {
                ShipAPI ship = (ShipAPI) nearbyTarget;
                if (ship.getParentStation() == primaryTarget) {
                    iter.remove();
                    continue;
                }
            }

            // 获取目标上最近的伤害点
            Vector2f nearestPoint = getNearestPointForDamage(originPoint, nearbyTarget);

            boolean remove = false;

            // 检查是否被其他目标遮挡
            for (Object otherTarget : nearbyTargets) {
                if (!(otherTarget instanceof CombatEntityAPI)) {
                    continue;
                }
                CombatEntityAPI otherEntity = (CombatEntityAPI) otherTarget;

                // 只检查同阵营的遮挡物，且不是自己
                if ((nearbyTarget.getOwner() != otherEntity.getOwner()) || (otherEntity == nearbyTarget)) {
                    continue;
                }

                // 检查是否被护盾遮挡
                if (filterShielded && (otherEntity.getShield() != null)) {
                    ShieldAPI shield = otherEntity.getShield();
                    if (shield.isWithinArc(nearestPoint) && shield.isOn() &&
                            MathUtils.isWithinRange(nearestPoint, shield.getLocation(), shield.getRadius())) {
                        remove = true;
                        break;
                    }
                }

                // 检查是否被实体本体遮挡
                if (filterBlocked && CollisionUtils.getCollides(originPoint, nearestPoint,
                        otherEntity.getLocation(),
                        otherEntity.getCollisionRadius())) {
                    // 更新边界信息（LazyLib的workaround）
                    BoundsAPI bounds = otherEntity.getExactBounds();
                    if (bounds != null) {
                        bounds.update(otherEntity.getLocation(), otherEntity.getFacing());
                    }

                    // 精确碰撞检测
                    if (CollisionUtils.getCollisionPoint(nearestPoint, originPoint, otherEntity) != null) {
                        remove = true;
                        break;
                    }
                }
            }

            // 如果被遮挡，则移除
            if (remove) {
                iter.remove();
            }
        }
    }

    /**
     * 使用Grid遍历获取矩形区域内的所有弹丸
     * 适用于光束、光剑等线性武器的检测
     *
     * @param center 中心位置
     * @param width 宽度
     * @param height 高度
     * @return 区域内的弹丸列表
     */
    public static List<DamagingProjectileAPI> getAllProjectilesInRect(Vector2f center, float width, float height) {
        List<DamagingProjectileAPI> result = new ArrayList<>();
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return result;

        java.util.Iterator<Object> iter = engine.getAllObjectGrid().getCheckIterator(
                center, width, height
        );

        while (iter.hasNext()) {
            Object obj = iter.next();
            if (!(obj instanceof DamagingProjectileAPI)) continue;

            DamagingProjectileAPI proj = (DamagingProjectileAPI) obj;
            if (proj.isExpired() || !engine.isEntityInPlay(proj)) continue;

            result.add(proj);
        }

        return result;
    }

}