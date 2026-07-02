package data.hullmods.Tr;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.FighterLaunchBayAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.combat.RiftLanceEffect;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class Tr6_ControllingCore extends BaseHullMod {

    public static Color JITTER_COLOR = new Color(255, 0, 0, 255); // 特效颜色
    // public static Color RIFT_COLOR = RiftCascadeEffect.STANDARD_RIFT_COLOR;
    // //初始裂隙特效颜色
    public static Color RIFT_COLOR = new Color(255, 89, 0, 255); // 裂隙特效颜色
    public static String DATA_KEY = "tr6_controlling_key";

    public static float SPAWN_TIME = 4f; //
    public static float BASE_DELAY = 3f; // 摧毁到生成碎片的时延
    public static float RANDOM_DELAY = 1f; // 在基础时延上正负多少

    // 固定生成的舰船变体ID
    public static String FIXED_SPAWN_VARIANT = "rs_Tr_6_Standerd";

    public static class ShardSpawnerData {
        boolean done = false;
        float delay = BASE_DELAY + (float) Math.random() * RANDOM_DELAY;
    }

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getBreakProb().modifyMult(id, 0f);
    }

    public List<ShipAPI> getDrones(ShipAPI ship) {
        List<ShipAPI> result = new ArrayList<>();
        for (FighterLaunchBayAPI bay : ship.getLaunchBaysCopy()) {
            if (bay.getWing() == null)
                continue;
            result.addAll(bay.getWing().getWingMembers());
        }
        return result;
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (ship.getOriginalOwner() != 0) {
            engine.setCombatNotOverForAtLeast(SPAWN_TIME + 1f);
        }

        for (ShipAPI fighter : getDrones(ship)) {
            if (fighter.getCustomData().get("jkf_zqtd_" + ship.getId()) == null) {
                fighter.setCustomData("jkf_zqtd_" + ship.getId(), true);
            }
        }

        if (!ship.isHulk() || !engine.isEntityInPlay(ship))
            return;

        String key = DATA_KEY + "_" + ship.getId();
        ShardSpawnerData data = (ShardSpawnerData) engine.getCustomData().get(key);
        if (data == null) {
            data = new ShardSpawnerData();
            engine.getCustomData().put(key, data);
            for (ShipAPI fighter : engine.getShips()) {
                if (fighter.isFighter() && fighter.getWing() == null) {
                    if (fighter.getCustomData().get("jkf_zqtd_" + ship.getId()) != null) {
                        engine.applyDamage(fighter, fighter.getLocation(), 1000000f, DamageType.ENERGY, 0, true, false,
                                fighter,
                                false);
                    }
                }
            }
        }

        if (data.done)
            return;

        ship.setHitpoints(ship.getMaxHitpoints());
        ship.getMutableStats().getHullDamageTakenMult().modifyMult("ShardSpawnerInvuln", 0f);
        data.delay -= amount;
        if (data.delay > 0)
            return;

        float dur = SPAWN_TIME;
        float extraDur = 0f;

        // 固定生成角度
        float angle = 0f;

        List<ShardFadeInPlugin> shards = new ArrayList<>();

        // 只生成一艘固定舰船
        ShardFadeInPlugin shard = createShipFadeInPlugin(FIXED_SPAWN_VARIANT, ship, extraDur, dur, angle);
        shards.add(shard);
        Global.getCombatEngine().addPlugin(shard);

        Global.getCombatEngine().addPlugin(createShipFadeOutPlugin(ship, dur + extraDur * 0.5f, shards));
        data.done = true;
    }

    protected EveryFrameCombatPlugin createShipFadeOutPlugin(final ShipAPI ship, final float fadeOutTime,
                                                             final List<ShardFadeInPlugin> shards) {
        return new BaseEveryFrameCombatPlugin() {
            float elapsed = 0f;
            final IntervalUtil interval = new IntervalUtil(0.075f, 0.125f);

            private void pushShipsAway(float amount) {
                // 由于只有一艘船，简化推开逻辑
                for (ShardFadeInPlugin shard : shards) {
                    ShipAPI newShip = shard.ships[0];
                    if (newShip.isFighter())
                        continue;

                    Vector2f dir = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(ship.getLocation(), newShip.getLocation()));
                    float speed = ship.getCollisionRadius() * 0.5f;
                    dir.scale(amount * speed * 0.5f); // 减少推开速度，避免推得太远
                    Vector2f.add(newShip.getLocation(), dir, newShip.getLocation());
                }
            }

            @Override
            public void advance(float amount, List<InputEventAPI> events) {
                if (Global.getCombatEngine().isPaused())
                    return;

                elapsed += amount;

                float progress = elapsed / fadeOutTime;
                if (progress > 1f)
                    progress = 1f;
                ship.setAlphaMult(1f - progress);

                // 轻微推开新船
                pushShipsAway(amount);

                if (progress > 0.5f) {
                    ship.setCollisionClass(CollisionClass.NONE);
                }

                float jitterLevel = progress;
                if (jitterLevel < 0.5f) {
                    jitterLevel *= 2f;
                } else {
                    jitterLevel = (1f - jitterLevel) * 2f;
                }

                float jitterRange = progress;
                // jitterRange = (float) Math.sqrt(jitterRange);
                float maxRangeBonus = 100f;
                float jitterRangeBonus = jitterRange * maxRangeBonus;
                Color c = JITTER_COLOR;
                int alpha = c.getAlpha();
                alpha += (int) (100f * progress);
                if (alpha > 255)
                    alpha = 255;
                c = Misc.setAlpha(c, alpha);

                ship.setJitter(this, c, jitterLevel, 35, 0f, jitterRangeBonus);

                interval.advance(amount);
                if (interval.intervalElapsed() && elapsed < fadeOutTime * 0.75f) {
                    CombatEngineAPI engine = Global.getCombatEngine();
                    c = RiftLanceEffect.getColorForDarkening(RIFT_COLOR);
                    float baseDuration = 2f;
                    Vector2f vel = new Vector2f(ship.getVelocity());
                    float size = ship.getCollisionRadius() * 0.35f;
                    for (int i = 0; i < 3; i++) {
                        Vector2f point = new Vector2f(ship.getLocation());
                        point = Misc.getPointWithinRadiusUniform(point, ship.getCollisionRadius() * 0.5f, Misc.random);
                        float dur = baseDuration + baseDuration * (float) Math.random();
                        Vector2f pt = Misc.getPointWithinRadius(point, size * 0.5f);
                        Vector2f v = Misc.getUnitVectorAtDegreeAngle((float) Math.random() * 360f);
                        v.scale(size + size * (float) Math.random() * 0.5f);
                        v.scale(0.2f);
                        Vector2f.add(vel, v, v);

                        float maxSpeed = size * 1.5f * 0.2f;
                        float minSpeed = size * 1f * 0.2f;
                        float overMin = v.length() - minSpeed;
                        if (overMin > 0) {
                            float durMult = 1f - overMin / (maxSpeed - minSpeed);
                            if (durMult < 0.1f)
                                durMult = 0.1f;
                            dur *= 0.5f + 0.5f * durMult;
                        }
                        engine.addNegativeNebulaParticle(pt, v, size, 2f,
                                0.5f / dur, 0f, dur, c);
                    }
                }

                if (elapsed > fadeOutTime) {
                    ship.setHitpoints(0f);
                    Global.getCombatEngine().removeEntity(ship);
                    ship.setAlphaMult(0f);
                    Global.getCombatEngine().removePlugin(this);
                }
            }
        };
    }

    protected ShardFadeInPlugin createShipFadeInPlugin(final String variantId, final ShipAPI source,
                                                       final float delay, final float fadeInTime, final float angle) {

        return new ShardFadeInPlugin(variantId, source, delay, fadeInTime, angle);
    }

    public static class ShardFadeInPlugin extends BaseEveryFrameCombatPlugin {
        float elapsed = 0f;
        ShipAPI[] ships = null;
        CollisionClass collisionClass;

        String variantId;
        ShipAPI source;
        float delay;
        float fadeInTime;
        float angle;

        public ShardFadeInPlugin(String variantId, ShipAPI source, float delay, float fadeInTime, float angle) {
            this.variantId = variantId;
            this.source = source;
            this.delay = delay;
            this.fadeInTime = fadeInTime;
            this.angle = angle;

        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (Global.getCombatEngine().isPaused())
                return;

            elapsed += amount;
            if (elapsed < delay)
                return;

            CombatEngineAPI engine = Global.getCombatEngine();

            if (ships == null) {
                float facing = source.getFacing() + 15f * ((float) Math.random() - 0.5f);
                // 计算生成位置，在原舰船前方
                Vector2f loc = Misc.getUnitVectorAtDegreeAngle(source.getFacing());
                loc.scale(source.getCollisionRadius() * 0.5f); // 在原舰船前方半个半径位置
                Vector2f.add(loc, source.getLocation(), loc);

                // 添加少量随机偏移，避免重叠
                loc.x += (float) (Math.random() - 0.5) * 50f;
                loc.y += (float) (Math.random() - 0.5) * 50f;

                CombatFleetManagerAPI fleetManager = engine.getFleetManager(source.getOriginalOwner());
                boolean wasSuppressed = fleetManager.isSuppressDeploymentMessages();
                fleetManager.setSuppressDeploymentMessages(true);

                // 获取原舰船船长并创建新船长（用于指挥权移交）
                PersonAPI originalCaptain = source.getOriginalCaptain();
                PersonAPI newCaptain;

                newCaptain = Global.getSettings().createPerson();
                if (originalCaptain != null) {
                    // 复制原船长的属性
                    newCaptain.getStats().setLevel(originalCaptain.getStats().getLevel());
                    newCaptain.setPortraitSprite(originalCaptain.getPortraitSprite());
                    newCaptain.setName(originalCaptain.getName());

                    // 如果是玩家，则转移玩家控制
                    if (source.getCaptain() != null && source.getCaptain().isPlayer()) {
                        // 这里可以添加玩家控制转移逻辑
                        // 在实际游戏中，玩家控制通常是唯一的
                    }
                } else {
                    // 如果没有船长，创建一个默认的
                    newCaptain.setPersonality(Personalities.RECKLESS);
                    newCaptain.getStats().setSkillLevel(Skills.POINT_DEFENSE, 2);
                    newCaptain.getStats().setSkillLevel(Skills.GUNNERY_IMPLANTS, 2);
                    newCaptain.getStats().setSkillLevel(Skills.IMPACT_MITIGATION, 2);
                }

                // 生成新舰船
                ships = new ShipAPI[1];
                ships[0] = engine.getFleetManager(source.getOriginalOwner()).spawnShipOrWing(variantId, loc, facing,
                        0f, newCaptain);

                // 克隆变体并添加限制标签
                ships[0].cloneVariant();
                ships[0].getVariant().addTag(Tags.SHIP_LIMITED_TOOLTIP);

                fleetManager.setSuppressDeploymentMessages(wasSuppressed);
                collisionClass = ships[0].getCollisionClass();

                // 建立舰船映射关系，用于舰队管理
                DeployedFleetMemberAPI sourceMember = fleetManager.getDeployedFleetMemberFromAllEverDeployed(source);
                DeployedFleetMemberAPI deployed = fleetManager.getDeployedFleetMemberFromAllEverDeployed(ships[0]);
                if (sourceMember != null && deployed != null) {
                    Map<DeployedFleetMemberAPI, DeployedFleetMemberAPI> map = fleetManager.getShardToOriginalShipMap();
                    while (map.containsKey(sourceMember)) {
                        sourceMember = map.get(sourceMember);
                    }
                    if (sourceMember != null) {
                        map.put(deployed, sourceMember);
                    }
                }
            }

            float progress = (elapsed - delay) / fadeInTime;
            if (progress > 1f)
                progress = 1f;

            for (ShipAPI ship : ships) {
                ship.setAlphaMult(progress);

                if (progress < 0.5f) {
                    ship.blockCommandForOneFrame(ShipCommand.ACCELERATE);
                    ship.blockCommandForOneFrame(ShipCommand.TURN_LEFT);
                    ship.blockCommandForOneFrame(ShipCommand.TURN_RIGHT);
                    ship.blockCommandForOneFrame(ShipCommand.STRAFE_LEFT);
                    ship.blockCommandForOneFrame(ShipCommand.STRAFE_RIGHT);
                }

                ship.blockCommandForOneFrame(ShipCommand.USE_SYSTEM);
                ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
                ship.blockCommandForOneFrame(ShipCommand.FIRE);
                ship.blockCommandForOneFrame(ShipCommand.PULL_BACK_FIGHTERS);
                ship.blockCommandForOneFrame(ShipCommand.VENT_FLUX);
                ship.setHoldFireOneFrame(true);
                ship.setHoldFire(true);

                ship.setCollisionClass(CollisionClass.NONE);
                ship.getMutableStats().getHullDamageTakenMult().modifyMult("ShardSpawnerInvuln", 0f);
                if (progress < 0.5f) {
                    ship.getVelocity().set(source.getVelocity());
                } else if (progress > 0.75f) {
                    ship.setCollisionClass(collisionClass);
                    ship.getMutableStats().getHullDamageTakenMult().unmodifyMult("ShardSpawnerInvuln");
                }

                float jitterLevel = progress;
                if (jitterLevel < 0.5f) {
                    jitterLevel *= 2f;
                } else {
                    jitterLevel = (1f - jitterLevel) * 2f;
                }

                float jitterRange = 1f - progress;
                float maxRangeBonus = 50f;
                float jitterRangeBonus = jitterRange * maxRangeBonus;
                Color c = JITTER_COLOR;

                ship.setJitter(this, c, jitterLevel, 25, 0f, jitterRangeBonus);
            }

            if (elapsed > fadeInTime) {
                for (ShipAPI ship : ships) {
                    ship.setAlphaMult(1f);
                    ship.setHoldFire(false);
                    ship.setCollisionClass(collisionClass);
                    ship.getMutableStats().getHullDamageTakenMult().unmodifyMult("ShardSpawnerInvuln");
                }
                engine.removePlugin(this);
            }
        }
    }
}