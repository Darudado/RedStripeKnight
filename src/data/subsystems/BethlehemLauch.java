package data.subsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.magiclib.subsystems.MagicSubsystem;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import java.util.*;

public class BethlehemLauch extends MagicSubsystem {

    private static final float RECALL_RANGE = 1000f;
    private static final float DAMAGE_TAKEN_WHEN_SPEEDING = 0.1f; // 改为0.1f
    private static final float MAX_BUFF_TIME = 4f; // 改为4f
    private static final float RECALL_TIME = 1f; // 改为1f
    private static final float RECALL_CHECK_INTERVAL = 0.2f; // 改为0.2f

    // 状态管理
    private final IntervalUtil recallInterval;
    private final List<CombatEffect> activeEffects;
    private final List<CombatEffect> effectsToAdd;
    private final List<CombatEffect> effectsToRemove;
    private final Map<ShipAPI, String> fighterToBuffIdMap;
    private final Map<String, DamagingProjectileAPI> buffIdToProjectileMap;
    private final Set<ShipAPI> recalledFightersInCurrentCycle; // 新增：记录本次系统使用中已召回的飞机

    public BethlehemLauch(ShipAPI ship) {
        super(ship);
        this.recallInterval = new IntervalUtil(RECALL_CHECK_INTERVAL, RECALL_CHECK_INTERVAL);
        this.activeEffects = new ArrayList<>();
        this.effectsToAdd = new ArrayList<>();
        this.effectsToRemove = new ArrayList<>();
        this.fighterToBuffIdMap = new HashMap<>();
        this.buffIdToProjectileMap = new HashMap<>();
        this.recalledFightersInCurrentCycle = new HashSet<>(); // 初始化已召回飞机集合
    }

    private boolean isValidWing(FighterWingAPI wing) {
        if (wing == null) return false;
        if (wing.getSpec().getRange() < 2000f) return false;
        if (wing.getSpec().isBomber()) return false; // 添加轰炸机检查
        return true;
    }

    // 获取发射点位置
    private Vector2f findLaunchPoint() {
        for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
            if (slot.getId().contains("LAUNCH")) {
                return slot.computePosition(ship); // 使用computePosition方法
            }
        }
        return new Vector2f(ship.getLocation());
    }

    @Override
    public float getBaseActiveDuration() {
        return 4f;
    }

    @Override
    public float getBaseCooldownDuration() {
        return 10f;
    }

    @Override
    public String getDisplayText() {
        return "Fighter ejection system";
    }

    @Override
    public String getStateText() {
        if (getState() == State.IN || getState() == State.ACTIVE) {
            return "Recalling (" + activeEffects.size() + ")";
        }
        return super.getStateText();
    }

    @Override
    public void onActivate() {
        super.onActivate();
        recallInterval.forceIntervalElapsed();
        recalledFightersInCurrentCycle.clear(); // 系统激活时清空已召回记录
    }

    @Override
    public void advance(float amount, boolean isPaused) {
        super.advance(amount, isPaused);
        if (isPaused) return;

        // 首先处理待添加和待移除的效果
        if (!effectsToAdd.isEmpty() || !effectsToRemove.isEmpty()) {
            processPendingEffects();
        }

        // 更新所有效果
        updateEffects(amount);

        if (getState() == State.ACTIVE || getState() == State.IN) {
            recallInterval.advance(amount);

            if (recallInterval.intervalElapsed()) {
                // 查找需要召回的战机
                for (FighterLaunchBayAPI bay : ship.getLaunchBaysCopy()) {
                    if (bay == null) continue;

                    FighterWingAPI wing = bay.getWing();
                    if (wing == null || !isValidWing(wing)) continue;

                    for (ShipAPI fighter : wing.getWingMembers()) {
                        if (fighter == null || !fighter.isAlive() || fighter.isHulk()) continue;
                        if (isFighterInRecall(fighter)) continue;

                        // 新增判断：检查这架飞机是否在本次系统使用中已经被召回过
                        if (recalledFightersInCurrentCycle.contains(fighter)) {
                            continue; // 如果已经召回过，跳过这架飞机
                        }

                        float distance = MathUtils.getDistance(ship.getLocation(), fighter.getLocation());
                        if (distance > RECALL_RANGE) continue;

                        // 开始召回这架战机
                        startRecall(fighter);
                        break; // 一次只召回一架
                    }
                }
            }
        }
    }

    // 处理待添加和待移除的效果
    private void processPendingEffects() {
        if (!effectsToAdd.isEmpty()) {
            activeEffects.addAll(effectsToAdd);
            effectsToAdd.clear();
        }

        if (!effectsToRemove.isEmpty()) {
            activeEffects.removeAll(effectsToRemove);
            effectsToRemove.clear();
        }
    }

    private boolean isFighterInRecall(ShipAPI fighter) {
        for (CombatEffect effect : activeEffects) {
            if (effect instanceof RecallEffect && effect.fighter == fighter) {
                return true;
            }
        }
        return fighter.getCustomData().containsKey("BethlehemLaunch_RecallFighter");
    }

    private void startRecall(ShipAPI fighter) {
        fighter.setCustomData("BethlehemLaunch_RecallFighter", true);
        fighter.getWing().getSourceShip().setPullBackFighters(true);

        // 新增：将这架飞机添加到已召回记录中
        recalledFightersInCurrentCycle.add(fighter);

        // 播放音效
        // Global.getSoundPlayer().playSound("aEP_EMP_pike_fire", 0.5f, 1f, fighter.getLocation(), Misc.ZERO);

        RecallEffect effect = new RecallEffect(fighter, RECALL_TIME);
        effectsToAdd.add(effect);
    }

    private void updateEffects(float amount) {
        Iterator<CombatEffect> iterator = activeEffects.iterator();
        while (iterator.hasNext()) {
            CombatEffect effect = iterator.next();
            effect.advance(amount);

            if (effect.shouldEnd || effect.isFinished()) {
                if (effect instanceof RecallEffect) {
                    completeRecall((RecallEffect) effect);
                } else if (effect instanceof SpeedBuffEffect) {
                    completeSpeedBuff((SpeedBuffEffect) effect);
                }
                iterator.remove();
            }
        }
    }

    private void completeRecall(RecallEffect effect) {
        ShipAPI fighter = effect.fighter;
        if (fighter != null && fighter.isAlive()) {
            fighter.removeCustomData("BethlehemLaunch_RecallFighter");
            fighter.setPhased(false);
            fighter.setExtraAlphaMult(1f);

            // 检查是否应该应用加速buff
            if ((getState() == State.ACTIVE || getState() == State.IN) && ship.isAlive() && !ship.isHulk()) {
                applySpeedBuff(fighter);
            }
        }
    }

    private void completeSpeedBuff(SpeedBuffEffect effect) {
        ShipAPI fighter = effect.fighter;
        if (fighter != null && fighter.isAlive()) {
            String buffId = effect.buffId;

            if (buffId != null) {
                fighter.getMutableStats().getMaxSpeed().unmodify(buffId);
                fighter.getMutableStats().getArmorDamageTakenMult().unmodify(buffId);
                fighter.getMutableStats().getHullDamageTakenMult().unmodify(buffId);
                fighter.getMutableStats().getMaxTurnRate().unmodify(buffId);
            }

            if (fighterToBuffIdMap.containsKey(fighter)) {
                fighterToBuffIdMap.remove(fighter);
            }

            if (buffId != null && buffIdToProjectileMap.containsKey(buffId)) {
                DamagingProjectileAPI proj = buffIdToProjectileMap.get(buffId);
                if (proj != null && Global.getCombatEngine().isEntityInPlay(proj)) {
                    Global.getCombatEngine().removeEntity(proj);
                }
                buffIdToProjectileMap.remove(buffId);
            }

            fighter.removeCustomData("BethlehemLaunch_RecallFighter");
            fighter.setPhased(false);
            fighter.setExtraAlphaMult(1f);

            if (fighter.getWing() != null && fighter.getWing().getSourceShip() != null) {
                fighter.getWing().getSourceShip().setPullBackFighters(false);
            }

            if (fighter.getShipAI() != null) {
                fighter.getShipAI().cancelCurrentManeuver();
                fighter.getShipAI().forceCircumstanceEvaluation();
            }
        }
    }

    private void applySpeedBuff(ShipAPI fighter) {
        String buffId = "BethlehemLaunch_SpeedBuff_" + System.currentTimeMillis();
        fighterToBuffIdMap.put(fighter, buffId);

        fighter.getMutableStats().getMaxSpeed().modifyFlat(buffId, 600f);
        fighter.getMutableStats().getArmorDamageTakenMult().modifyMult(buffId, DAMAGE_TAKEN_WHEN_SPEEDING);
        fighter.getMutableStats().getHullDamageTakenMult().modifyMult(buffId, DAMAGE_TAKEN_WHEN_SPEEDING);
        fighter.getMutableStats().getMaxTurnRate().modifyMult(buffId, 0f);

        Vector2f launchPoint = findLaunchPoint();

        DamagingProjectileAPI proj = (DamagingProjectileAPI) Global.getCombatEngine().spawnProjectile(
                ship, null, "rs_bethlehem_main",
                launchPoint, ship.getFacing(), null);

        Vector2f projloc = proj.getLocation();
        Vector2f projvol = proj.getVelocity();
        float projfacing = proj.getFacing();

        buffIdToProjectileMap.put(buffId, proj);

        fighter.getLocation().set(projloc);
        fighter.setFacing(projfacing);
        fighter.getVelocity().set(projvol);

        SpeedBuffEffect effect = new SpeedBuffEffect(fighter, buffId, proj, MAX_BUFF_TIME);
        effectsToAdd.add(effect);

        fighter.giveCommand(ShipCommand.ACCELERATE, null, 0);
        fighter.blockCommandForOneFrame(ShipCommand.FIRE); // 阻止开火
        fighter.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS); // 阻止后退
        fighter.blockCommandForOneFrame(ShipCommand.STRAFE_RIGHT); // 阻止右移
        fighter.blockCommandForOneFrame(ShipCommand.STRAFE_LEFT); // 阻止左移
        fighter.blockCommandForOneFrame(ShipCommand.DECELERATE); // 阻止减速
    }

    @Override
    public void onFinished() {
        super.onFinished();
        cancelAllEffects();
    }

    @Override
    public void onShipDeath() {
        super.onShipDeath();
        cancelAllEffects();
    }

    private void cancelAllEffects() {
        for (CombatEffect effect : activeEffects) {
            if (effect instanceof RecallEffect recallEffect) {
                if (recallEffect.fighter != null && recallEffect.fighter.isAlive()) {
                    recallEffect.fighter.removeCustomData("BethlehemLaunch_RecallFighter");
                    recallEffect.fighter.setPhased(false);
                    recallEffect.fighter.setExtraAlphaMult(1f);
                }
            } else if (effect instanceof SpeedBuffEffect speedEffect) {
                completeSpeedBuff(speedEffect);
            }
        }
        activeEffects.clear();
        effectsToAdd.clear();
        effectsToRemove.clear();
        recalledFightersInCurrentCycle.clear(); // 清空已召回记录

        for (DamagingProjectileAPI proj : buffIdToProjectileMap.values()) {
            if (proj != null && Global.getCombatEngine().isEntityInPlay(proj)) {
                Global.getCombatEngine().removeEntity(proj);
            }
        }
        buffIdToProjectileMap.clear();
        fighterToBuffIdMap.clear();
    }

    @Override
    public boolean shouldActivateAI(float amount) {
        if (getState() != State.READY) return false;

        for (FighterLaunchBayAPI bay : ship.getLaunchBaysCopy()) {
            if (bay == null) continue;

            FighterWingAPI wing = bay.getWing();
            if (wing == null || !isValidWing(wing)) continue;

            for (ShipAPI fighter : wing.getWingMembers()) {
                if (fighter == null || !fighter.isAlive() || fighter.isHulk()) continue;
                if (isFighterInRecall(fighter)) continue;

                // 新增判断：检查这架飞机是否在之前的系统使用中已经被召回过
                if (recalledFightersInCurrentCycle.contains(fighter)) {
                    continue; // 如果已经召回过，跳过这架飞机
                }

                float distance = MathUtils.getDistance(ship.getLocation(), fighter.getLocation());
                if (distance <= RECALL_RANGE) {
                    return true;
                }
            }
        }

        return false;
    }

    // 战斗效果基类
    private abstract static class CombatEffect {
        protected ShipAPI fighter;
        protected float duration;
        protected float elapsed;
        protected boolean shouldEnd = false;

        public CombatEffect(ShipAPI fighter, float duration) {
            this.fighter = fighter;
            this.duration = duration;
            this.elapsed = 0f;
        }

        public void advance(float amount) {
            elapsed += amount;
        }

        public boolean isFinished() {
            return elapsed >= duration || fighter == null || !fighter.isAlive();
        }

        public float getProgress() {
            return Math.min(elapsed / duration, 1f);
        }
    }

    // 召回效果
    private class RecallEffect extends CombatEffect {
        public RecallEffect(ShipAPI fighter, float duration) {
            super(fighter, duration);
        }

        @Override
        public void advance(float amount) {
            super.advance(amount);

            // 检查系统状态，如果系统不再激活，则结束召回
            if (getState() != State.ACTIVE && getState() != State.IN) {
                shouldEnd = true;
                return;
            }

            if (fighter != null && fighter.isAlive()) {
                fighter.setPhased(true);
                float alpha = 1f - getProgress() * 0.5f;
                fighter.setExtraAlphaMult(alpha);

                float jitterRange = 5f + getProgress() * fighter.getCollisionRadius();
                fighter.setJitter("RecallDevice", new Color(100, 150, 255, 100),
                        getProgress(), 10, 0f, jitterRange);
            }
        }
    }

    // 加速效果
    private class SpeedBuffEffect extends CombatEffect {
        private final String buffId;
        private final DamagingProjectileAPI guideProjectile;

        public SpeedBuffEffect(ShipAPI fighter, String buffId, DamagingProjectileAPI guideProjectile, float duration) {
            super(fighter, duration);
            this.buffId = buffId;
            this.guideProjectile = guideProjectile;
        }

        @Override
        public void advance(float amount) {
            super.advance(amount);

            // 检查系统状态，如果系统不再激活，则结束加速
            if (getState() != State.ACTIVE && getState() != State.IN) {
                shouldEnd = true;
                return;
            }

            if (fighter != null && fighter.isAlive() && Global.getCombatEngine().isEntityInPlay(guideProjectile)) {
                fighter.setFacing(guideProjectile.getFacing());
                fighter.getVelocity().set(guideProjectile.getVelocity());
                fighter.getLocation().set(guideProjectile.getLocation());

                fighter.setJitter(buffId, Color.RED, 0.5f, 3, 0f, 5f);

                // 强制战机加速并禁止其他操作
                fighter.giveCommand(ShipCommand.ACCELERATE, null, 0);
                fighter.blockCommandForOneFrame(ShipCommand.FIRE);
                fighter.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS);
                fighter.blockCommandForOneFrame(ShipCommand.STRAFE_RIGHT);
                fighter.blockCommandForOneFrame(ShipCommand.STRAFE_LEFT);
                fighter.blockCommandForOneFrame(ShipCommand.DECELERATE);
            } else {
                shouldEnd = true;
            }
        }
    }
}