package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.impl.combat.MineStrikeStatsAIInfoProvider;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.awt.Color;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

public class Support_minestrike extends BaseShipSystemScript implements MineStrikeStatsAIInfoProvider {
    protected static float MINE_RANGE = 1500.0F;
    public static final Color JITTER_COLOR = new Color(255, 155, 255, 75);
    public static final Color JITTER_UNDER_COLOR = new Color(255, 155, 255, 155);

    public static float getRange(ShipAPI ship) {
        return ship == null ? MINE_RANGE : ship.getMutableStats().getSystemRangeBonus().computeEffective(MINE_RANGE);
    }

    public void apply(MutableShipStatsAPI stats, String id, ShipSystemStatsScript.State state, float effectLevel) {
        ShipAPI ship;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI)stats.getEntity();
            float jitterLevel = effectLevel;
            if (state == State.OUT) {
                jitterLevel = effectLevel * effectLevel;
            }

            float maxRangeBonus = 25.0F;
            float jitterRangeBonus = jitterLevel * maxRangeBonus;
            ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, 11, 0.0F, 3.0F + jitterRangeBonus);
            ship.setJitter(this, JITTER_COLOR, jitterLevel, 4, 0.0F, 0.0F + jitterRangeBonus);
            if (state != State.IN) {
                if (effectLevel >= 1.0F) {
                    Vector2f target = ship.getMouseTarget();
                    if (ship.getShipAI() != null && ship.getAIFlags().hasFlag(AIFlags.SYSTEM_TARGET_COORDS)) {
                        target = (Vector2f)ship.getAIFlags().getCustom(AIFlags.SYSTEM_TARGET_COORDS);
                    }

                    if (target != null) {
                        float dist = Misc.getDistance(ship.getLocation(), target);
                        float max = this.getMaxRange(ship) + ship.getCollisionRadius();
                        if (dist > max) {
                            float dir = Misc.getAngleInDegrees(ship.getLocation(), target);
                            target = Misc.getUnitVectorAtDegreeAngle(dir);
                            target.scale(max);
                            Vector2f.add(target, ship.getLocation(), target);
                        }

                        target = this.findClearLocation(target);
                        if (target != null) {
                            this.spawnMine(ship, target);
                        }
                    }
                }
            }

        }
    }

    public void spawnMine(ShipAPI source, Vector2f mineLoc) {
        CombatEngineAPI engine = Global.getCombatEngine();
        Vector2f currLoc = Misc.getPointAtRadius(mineLoc, 30.0F + (float)Math.random() * 30.0F);
        float start = (float)Math.random() * 360.0F;

        for(float angle = start; angle < start + 390.0F; angle += 30.0F) {
            if (angle != start) {
                Vector2f loc = Misc.getUnitVectorAtDegreeAngle(angle);
                loc.scale(50.0F + (float)Math.random() * 30.0F);
                currLoc = Vector2f.add(mineLoc, loc, new Vector2f());
            }

            for(MissileAPI other : Global.getCombatEngine().getMissiles()) {
                if (other.isMine()) {
                    float dist = 0;
                    if (currLoc != null) {
                        dist = Misc.getDistance(currLoc, other.getLocation());
                    }
                    if (dist < other.getCollisionRadius() + 40.0F) {
                        currLoc = null;
                        break;
                    }
                }
            }

            if (currLoc != null) {
                break;
            }
        }

        if (currLoc == null) {
            currLoc = Misc.getPointAtRadius(mineLoc, 30.0F + (float)Math.random() * 30.0F);
        }

        MissileAPI mine = (MissileAPI)engine.spawnProjectile(source, null, "rs_mine", currLoc, (float)Math.random() * 360.0F, null);
        if (source != null) {
            Global.getCombatEngine().applyDamageModifiersToSpawnedProjectileWithNullWeapon(source, WeaponType.MISSILE, false, mine.getDamage());
        }

        float fadeInTime = 0.5F;
        mine.getVelocity().scale(0.0F);
        mine.fadeOutThenIn(fadeInTime);
        Global.getCombatEngine().addPlugin(this.createMissileJitterPlugin(mine, fadeInTime));
        float liveTime = 5.0F;
        mine.setFlightTime(mine.getMaxFlightTime() - liveTime);
        Global.getSoundPlayer().playSound("mine_teleport", 1.0F, 1.0F, mine.getLocation(), mine.getVelocity());
    }

    // 修复后的实现
    protected EveryFrameCombatPlugin createMissileJitterPlugin(final MissileAPI mine, final float fadeInTime) {
        return new EveryFrameCombatPlugin() {
            private float elapsed = 0f;

            @Override
            public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {

            }

            @Override
            public void advance(float amount, List<InputEventAPI> events) {
                CombatEngineAPI engine = Global.getCombatEngine();
                if (engine.isPaused()) return;

                elapsed += amount;

                // 检查导弹是否"存活"（hitpoints > 0 且未过期）
                boolean missileAlive = mine.getHitpoints() > 0 && !mine.isExpired();

                if (elapsed >= fadeInTime || !missileAlive) {
                    engine.removePlugin(this);
                    return;
                }

                // 随时间减弱的抖动效果
                float jitterLevel = 1f - (elapsed / fadeInTime);

                // 导弹只有 setJitter 方法，没有 setJitterUnder
                mine.setJitter(this, Support_minestrike.JITTER_COLOR, jitterLevel, 5, 0f, 3f + 5f * jitterLevel);
            }

            @Override
            public void renderInUICoords(ViewportAPI viewport) {}

            @Override
            public void renderInWorldCoords(ViewportAPI viewport) {}

            @Override
            public void init(CombatEngineAPI engine) {}
        };
    }

    protected float getMaxRange(ShipAPI ship) {
        return this.getMineRange(ship);
    }

    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (system.isOutOfAmmo()) {
            return null;
        } else if (system.getState() != SystemState.IDLE) {
            return null;
        } else {
            Vector2f target = ship.getMouseTarget();
            if (target != null) {
                float dist = Misc.getDistance(ship.getLocation(), target);
                float max = this.getMaxRange(ship) + ship.getCollisionRadius();
                return dist > max ? "超出范围" : "就绪";
            } else {
                return null;
            }
        }
    }

    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        return ship.getMouseTarget() != null;
    }

    private Vector2f findClearLocation(Vector2f dest) {
        if (this.isLocationClear(dest)) {
            return dest;
        } else {
            float incr = 50.0F;
            // 添加泛型声明解决警告
            WeightedRandomPicker<Vector2f> tested = new WeightedRandomPicker<>();

            for(float distIndex = 1.0F; distIndex <= 32.0F; distIndex *= 2.0F) {
                float start = (float)Math.random() * 360.0F;

                for(float angle = start; angle < start + 360.0F; angle += 60.0F) {
                    Vector2f loc = Misc.getUnitVectorAtDegreeAngle(angle);
                    loc.scale(incr * distIndex);
                    Vector2f.add(dest, loc, loc);
                    tested.add(loc);
                    if (this.isLocationClear(loc)) {
                        return loc;
                    }
                }
            }

            if (tested.isEmpty()) {
                return dest;
            } else {
                return tested.pick();
            }
        }
    }

    private boolean isLocationClear(Vector2f loc) {
        for(ShipAPI other : Global.getCombatEngine().getShips()) {
            if (!other.isShuttlePod() && !other.isFighter()) {
                Vector2f otherLoc = other.getShieldCenterEvenIfNoShield();
                float otherR = other.getShieldRadiusEvenIfNoShield();
                if (other.isPiece()) {
                    otherLoc = other.getLocation();
                    otherR = other.getCollisionRadius();
                }

                float dist = Misc.getDistance(loc, otherLoc);
                float checkDist = 75.0F;
                if (other.isFrigate()) {
                    checkDist = 110.0F;
                }

                if (dist < otherR + checkDist) {
                    return false;
                }
            }
        }

        for(CombatEntityAPI other : Global.getCombatEngine().getAsteroids()) {
            float dist = Misc.getDistance(loc, other.getLocation());
            if (dist < other.getCollisionRadius() + 75.0F) {
                return false;
            }
        }

        return true;
    }

    public float getFuseTime() {
        return 3.0F;
    }

    public float getMineRange(ShipAPI ship) {
        return getRange(ship);
    }
}