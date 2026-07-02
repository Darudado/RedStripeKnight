package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;

/**
 * 弹丸命中效果：
 * - 如果目标舰船有护盾且实际护盾效率（幅能/伤害） ≤ 0.8，增加20%护盾受到的伤害，持续4秒。
 * - 如果护盾效率 ≤ 0.6，则增伤效果强化至40%，持续4秒。
 * - 4秒内再次命中会重置持续时间（无论命中时护盾效率是否变化）。
 */
public class DveEffect implements OnHitEffectPlugin {

    // 视觉效果参数（保留自参考代码）
    private static final Color NEBULA_COLOR = new Color(65, 150, 215, 225);
    private static final Color EXPLOSION_COLOR = new Color(60, 145, 225, 175);
    private static final float NEBULA_SIZE = 10.0F * (0.75F + (float) Math.random() * 0.5F);

    /**
     * 内部脚本：管理单艘舰船的护盾易伤效果。
     * 附加到引擎中，每帧减少剩余时间，到期后移除效果。
     */
    private static class ShieldDebuffScript implements EveryFrameCombatPlugin {

        private final ShipAPI ship;
        private float timeRemaining = 4f;
        private float currentMultiplier;
        private CombatEngineAPI engine;

        private static final String MOD_ID = "cr_dve_shield_buff";

        public ShieldDebuffScript(ShipAPI ship, float multiplier) {
            this.ship = ship;
            this.currentMultiplier = multiplier;
            applyModifier();
        }

        private void applyModifier() {
            ship.getMutableStats().getShieldDamageTakenMult().modifyMult(MOD_ID, currentMultiplier);
        }

        private void removeModifier() {
            ship.getMutableStats().getShieldDamageTakenMult().unmodifyMult(MOD_ID);
        }

        /**
         * 重置持续时间（不改变倍数）
         */
        public void refresh() {
            timeRemaining = 4f;
        }

        /**
         * 重置持续时间，并可更新倍数（用于首次触发，后续命中不再调用）
         * @param newMultiplier 新倍数（若与当前不同则更新）
         */
        public void reset(float newMultiplier) {
            timeRemaining = 4f;
            if (Math.abs(currentMultiplier - newMultiplier) > 0.01f) {
                removeModifier();
                currentMultiplier = newMultiplier;
                applyModifier();
            }
        }

        @Override
        public void init(CombatEngineAPI engine) {
            this.engine = engine;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            // 检查舰船是否失效（被摧毁或脱离战斗）
            if (ship == null || !ship.isAlive() || ship.getOwner() == -1 || engine == null) {
                cleanupAndRemove();
                return;
            }

            timeRemaining -= amount;
            if (timeRemaining <= 0f) {
                cleanupAndRemove();
            }
        }

        private void cleanupAndRemove() {
            removeModifier();
            // 从舰船的自定义数据中移除自身引用
            if (ship != null) {
                ship.setCustomData(MOD_ID, null);
            }
            if (engine != null) {
                engine.removePlugin(this);
            }
        }

        @Override
        public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {}

        @Override
        public void renderInWorldCoords(ViewportAPI viewport) {}

        @Override
        public void renderInUICoords(ViewportAPI viewport) {}
    }

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target,
                      Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult,
                      CombatEngineAPI engine) {

        // 1. 视觉效果（保留参考代码中的爆炸和星云）
        Vector2f v_target = new Vector2f(target.getVelocity());
        engine.addNebulaParticle(point, v_target, NEBULA_SIZE, 20.0F, 0.15F, 0.3F, 0.8F, NEBULA_COLOR, true);
        engine.spawnExplosion(point, v_target, EXPLOSION_COLOR, NEBULA_SIZE * 3.0F, 0.6F);

        // 2. 护盾易伤效果
        if (!(target instanceof ShipAPI ship)) return;          // 只对舰船生效
        if (ship.getShield() == null) return;                  // 无护盾的舰船不触发

        // 检查舰船是否已有此debuff脚本（通过自定义数据存储）
        ShieldDebuffScript script = (ShieldDebuffScript) ship.getCustomData().get(ShieldDebuffScript.MOD_ID);
        if (script != null) {
            // 已有脚本，直接重置持续时间（不改变倍数，无论当前护盾效率如何）
            script.refresh();
            return;
        }

        // 没有脚本，判断是否需要触发新debuff
        // 获取实际护盾效率（幅能/伤害），此时目标尚未受到此debuff影响
        float actualEfficiency = ship.getMutableStats().getShieldDamageTakenMult().getModifiedValue();

        if (actualEfficiency > 0.8f) return;                    // 效率高于0.8不触发

        // 计算增伤倍数：效率≤0.6时1.4倍（+40%），否则1.2倍（+20%）
        float multiplier = (actualEfficiency <= 0.6f) ? 1.4f : 1.2f;

        // 创建新脚本并通过引擎添加
        script = new ShieldDebuffScript(ship, multiplier);
        engine.addPlugin(script);
        ship.setCustomData(ShieldDebuffScript.MOD_ID, script);
    }
}