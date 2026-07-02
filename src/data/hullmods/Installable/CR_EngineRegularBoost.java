package data.hullmods.Installable;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicIncompatibleHullmods;

import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

public class CR_EngineRegularBoost extends BaseHullMod {
    private static final String BASIC_BOUN_ID = "cr_allied_critical";
    private static final float BASIC_BOUN = 35f;
    private static final float SMOD_BONUS = 5f;
    private static final Map<ShipAPI.HullSize, Float> SPEED_MAP = new EnumMap<>(ShipAPI.HullSize.class);
    private static final Color ENGINE_COLOR = new Color(255, 255, 255, 255);
    private static final Color CONTRAIL_COLOR = new Color(255, 255, 255, 150);
    private static final float AUTO_TRIGGER_INTERVAL = 10f; // 30秒自动触发间隔

    static {
        SPEED_MAP.put(ShipAPI.HullSize.FRIGATE, 65f);
        SPEED_MAP.put(ShipAPI.HullSize.DESTROYER, 60f);
        SPEED_MAP.put(ShipAPI.HullSize.CRUISER, 55f);
        SPEED_MAP.put(ShipAPI.HullSize.CAPITAL_SHIP, 45f);
    }

    // 内部数据类
    public static class EngineBoostData {
        public enum State {
            IDLE("Ready", "Auto-boost in: %s seconds", 1f),
            CHARGING("Charging", "Boost system charging... %s seconds", 1f),
            ACTIVE("Boost Active", "Boost active! %s seconds remaining", 5f),
            COOLDOWN("Cooldown", "System cooling down... %s seconds", 3f);

            public final String statusTitle;
            public final String statusDesc;
            public final float timeNeededToNextState;

            State(String title, String desc, float time) {
                this.statusTitle = title;
                this.statusDesc = desc;
                this.timeNeededToNextState = time;
            }
        }

        private State state;
        private float elapsed;
        private float chargeProgress;
        private float autoTriggerTimer; // 自动触发计时器

        public EngineBoostData() {
            this.state = State.IDLE;
            this.elapsed = 0f;
            this.chargeProgress = 0f;
            this.autoTriggerTimer = 0f;
        }

        public void advance(float amount, ShipAPI ship) {
            if (amount == 0f || ship == null || !ship.isAlive()) return;

            CombatEngineAPI engine = Global.getCombatEngine();
            boolean isPlayerShip = engine.getPlayerShip() == ship;

            this.elapsed += amount;

            switch (state) {
                case IDLE:
                    // 自动触发逻辑：每20秒自动激活
                    autoTriggerTimer += amount;

                    // 添加准备特效（每5秒一次）
                    if (engine.getTotalElapsedTime(false) % 5f < amount) {
                        Vector2f loc = MathUtils.getRandomPointInCircle(
                                ship.getLocation(),
                                ship.getCollisionRadius() * 0.3f
                        );
                        Vector2f vel = new Vector2f(ship.getVelocity());
                        engine.addHitParticle(loc, vel, 5f, 0.8f, 0.2f,
                                new Color(255, 150, 50, 150));
                    }

                    // 检查是否达到自动触发时间
                    if (autoTriggerTimer >= AUTO_TRIGGER_INTERVAL) {
                        this.state = State.CHARGING;
                        this.elapsed = 0f;
                        this.autoTriggerTimer = 0f;
                        // 触发音效
                        Global.getSoundPlayer().playSound(
                                "world_emergency_burn_on",
                                0.8f, 1f,
                                ship.getLocation(),
                                ship.getVelocity()
                        );
                    }
                    break;

                case CHARGING:
                    chargeProgress = Math.min(1f, elapsed / state.timeNeededToNextState);

                    // 添加充电视觉效果
                    if (engine.getTotalElapsedTime(false) % 0.1f < amount) {
                        Vector2f loc = MathUtils.getRandomPointInCircle(
                                ship.getLocation(),
                                ship.getCollisionRadius() * 0.5f
                        );
                        Vector2f vel = new Vector2f(ship.getVelocity());
                        engine.addHitParticle(loc, vel, 10f, 1f, 0.3f, ENGINE_COLOR);
                    }

                    // 充电音效
                    if (elapsed < 0.1f) {
                        Global.getSoundPlayer().playSound(
                                "world_emergency_burn_on",
                                1f, 1f,
                                ship.getLocation(),
                                ship.getVelocity()
                        );
                    }

                    if (elapsed >= state.timeNeededToNextState) {
                        state = State.ACTIVE;
                        elapsed = 0f;
                        Global.getSoundPlayer().playSound(
                                "flux_loop",
                                2f, 1f,
                                ship.getLocation(),
                                ship.getVelocity()
                        );
                    }
                    break;

                case ACTIVE:
                    applyBoostEffects(ship);

                    // 添加激活特效
                    ship.getEngineController().fadeToOtherColor(
                            "cr_engine_boost",
                            ENGINE_COLOR,
                            CONTRAIL_COLOR,
                            1f, 0.8f
                    );


                    if (elapsed >= state.timeNeededToNextState) {
                        state = State.COOLDOWN;
                        elapsed = 0f;
                        removeBoostEffects(ship);
                    }
                    break;

                case COOLDOWN:
                    // 添加冷却特效（每2秒一次）
                    if (engine.getTotalElapsedTime(false) % 2f < amount) {
                        Vector2f loc = MathUtils.getRandomPointInCircle(
                                ship.getLocation(),
                                ship.getCollisionRadius() * 0.4f
                        );
                        Vector2f vel = new Vector2f(ship.getVelocity());
                        engine.addHitParticle(loc, vel, 8f, 0.6f, 0.4f,
                                new Color(100, 100, 255, 100));
                    }

                    if (elapsed >= state.timeNeededToNextState) {
                        state = State.IDLE;
                        elapsed = 0f;
                        chargeProgress = 0f;
                        autoTriggerTimer = 0f;
                    }
                    break;
            }

            // 更新玩家状态显示
            if (isPlayerShip) {
                updatePlayerStatus();
            }
        }

        private void applyBoostEffects(ShipAPI ship) {
            MutableShipStatsAPI stats = ship.getMutableStats();
            float speedBonus = SPEED_MAP.get(ship.getHullSize()) * (float) 1.0;

            // 速度加成
            stats.getMaxSpeed().modifyFlat("cr_engine_boost", speedBonus);
            stats.getAcceleration().modifyPercent("cr_engine_boost", 75f);
            stats.getDeceleration().modifyPercent("cr_engine_boost", 75f);

            // 转向加成（在基础50%之上再增加100%）
            stats.getMaxTurnRate().modifyPercent("cr_engine_boost", 75f);
            stats.getTurnAcceleration().modifyPercent("cr_engine_boost", 75f);

            // 引擎特殊效果
            stats.getEngineDamageTakenMult().modifyMult("cr_engine_boost", 0.5f);
            stats.getFluxDissipation().modifyPercent("cr_engine_boost", 150f);
        }

        private void removeBoostEffects(ShipAPI ship) {
            MutableShipStatsAPI stats = ship.getMutableStats();
            stats.getMaxSpeed().unmodify("cr_engine_boost");
            stats.getAcceleration().unmodify("cr_engine_boost");
            stats.getDeceleration().unmodify("cr_engine_boost");
            stats.getMaxTurnRate().unmodify("cr_engine_boost");
            stats.getTurnAcceleration().unmodify("cr_engine_boost");
            stats.getEngineDamageTakenMult().unmodify("cr_engine_boost");
            stats.getFluxDissipation().unmodify("cr_engine_boost");
        }



        private void updatePlayerStatus() {
            CombatEngineAPI engine = Global.getCombatEngine();
            String description = switch (state) {
                case IDLE -> {
                    float timeUntilNext = AUTO_TRIGGER_INTERVAL - autoTriggerTimer;
                    yield String.format(state.statusDesc, String.format("%.1f", timeUntilNext));
                }
                case CHARGING -> {
                    float chargePercent = chargeProgress * 100f;
                    yield String.format(state.statusDesc, String.format("%.0f%%", chargePercent));
                }
                case ACTIVE, COOLDOWN -> {
                    float remaining = state.timeNeededToNextState - elapsed;
                    yield String.format(state.statusDesc, String.format("%.1f", remaining));
                }
            };

            engine.maintainStatusForPlayerShip(
                    "cr_engine_boost_status",
                    "graphics/icons/hullsys/infernium_injector.png",
                    state.statusTitle,
                    description,
                    state == State.COOLDOWN
            );
        }

        public State getState() {
            return state;
        }

    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // 基础加成
        stats.getAcceleration().modifyPercent(BASIC_BOUN_ID, BASIC_BOUN);
        stats.getDeceleration().modifyPercent(BASIC_BOUN_ID, BASIC_BOUN);
        stats.getMaxTurnRate().modifyPercent(BASIC_BOUN_ID, BASIC_BOUN);
        stats.getTurnAcceleration().modifyPercent(BASIC_BOUN_ID, BASIC_BOUN);

        boolean sMod = isSMod(stats);
        if (sMod) {
            stats.getMaxSpeed().modifyFlat(id, SMOD_BONUS);
        }

        // 不兼容检查
        if (stats.getVariant().hasHullMod("unstable_injector")) {
            MagicIncompatibleHullmods.removeHullmodWithWarning(stats.getVariant(), "unstable_injector", "cr_engine_regular_boost");
        }
        if (stats.getVariant().hasHullMod("auxiliarythrusters")) {
            MagicIncompatibleHullmods.removeHullmodWithWarning(stats.getVariant(), "auxiliarythrusters", "cr_engine_regular_boost");
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        // 获取或创建数据
        EngineBoostData data = getOrCreateData(ship);

        // 更新数据
        data.advance(amount, ship);
    }

    private EngineBoostData getOrCreateData(ShipAPI ship) {
        String key = "cr_engine_boost_data_" + ship.getId();
        CombatEngineAPI engine = Global.getCombatEngine();

        EngineBoostData data = (EngineBoostData) engine.getCustomData().get(key);
        if (data == null) {
            data = new EngineBoostData();
            engine.getCustomData().put(key, data);
        }

        return data;
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return String.format("%.0f", BASIC_BOUN) + "%";
        if (index == 1) return String.format("%.0f", SMOD_BONUS);
        return null;
    }

    public String getSModDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return String.format("%.0f", SMOD_BONUS);
        return null;
    }
    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float pads = 10f;
        float pad = 2f;
        // 修改激活系统的说明为自动触发
        tooltip.addPara("经过特殊调整的能源系统会以能量潮汐的方式周期性加强能量输出", Misc.getHighlightColor(), pads);

        tooltip.addSectionHeading("系统调整", Alignment.MID, pads);
        //tooltip.addPara("自动触发间隔: 30秒", Color.GRAY, 3f);
        //tooltip.addPara("充电时间: 1秒", Color.GRAY, 3f);
        //tooltip.addPara("持续时间: 5秒", Color.GRAY, 3f);
        //tooltip.addPara("冷却时间: 3秒", Color.GRAY, 3f);
        tooltip.addPara("引擎潮汐持续期间获得性能加成", pad, Misc.getHighlightColor());
        tooltip.addPara("速度加成: " + SPEED_MAP.get(hullSize) + "节", Misc.getHighlightColor(), pad);
        tooltip.addPara("机动性加成: +75% ", Misc.getHighlightColor(), pad);
        tooltip.addPara("耗散加成: +150% ", Misc.getHighlightColor(), pad);
        tooltip.addPara("完整潮汐周期: 20秒 ", new Color(150, 200, 255, 255), pads);
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        if (ship == null) return false;
        if(ship.getVariant().hasHullMod("CR_ChargingRing")) return false;
        if(ship.getVariant().hasHullMod("CR_StructureUpgrading")) return false;
        if(ship.getVariant().hasHullMod("CR_ShieldOscillating")) return false;
        return ship.getVariant().hasHullMod("CrusadersCore");
    }

    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null) return "船只不存在";
        if (!ship.getVariant().hasHullMod("CrusadersCore")) return "需要十字军核心";
        return null;
    }
}