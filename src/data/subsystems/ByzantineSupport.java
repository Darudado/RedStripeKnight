package data.subsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.subsystems.MagicSubsystem;
import org.magiclib.util.MagicRender;
import org.magiclib.util.MagicUI;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ByzantineSupport extends MagicSubsystem {

    // 常量定义
    private final float BASE_RANGE = 2500f;

    // 三个距离区域
    private final float INNER_RANGE = 750f;    // 内圈
    private final float MID_RANGE = 1500f;     // 中圈
    private final float OUTER_RANGE = 2500f;   // 外圈

    // 不同区域的射程增益
    private final float INNER_RANGE_BONUS = 1.3f;  // 内圈+30%
    private final float MID_RANGE_BONUS = 1.2f;    // 中圈+20%
    private final float OUTER_RANGE_BONUS = 1.1f;  // 外圈+10%

    // 波纹特效相关常量
    private final Color WAVE_COLOR = new Color(200, 35, 25, 100);
    private final Color INNER_COLOR = new Color(200, 0, 10, 80);
    private final Color MID_COLOR = new Color(217, 195, 70, 60);
    private final Color OUTER_COLOR = new Color(215, 195, 70, 40);
    private final float WAVE_DURATION = 2.5f;
    private final float WAVE_INTERVAL = 2.5f;

    // 系统特定变量
    private final IntervalUtil waveTimer = new IntervalUtil(WAVE_INTERVAL, WAVE_INTERVAL);
    private final List<MissileAPI> locked = new ArrayList<>();
    private final List<MissileAPI> vulnerable = new ArrayList<>();

    // 存储受影响的友军舰船和其增益效果
    private final Map<ShipAPI, Float> affectedShips = new HashMap<>();
    private final Map<ShipAPI, String> effectKeys = new HashMap<>();

    // 圆形贴图
    private final SpriteAPI circleSprite;

    // 用于存储objectspace渲染的引用，以便在系统结束时清理
    private final List<CombatEntityAPI> renderedCircles = new ArrayList<>();

    public ByzantineSupport(ShipAPI ship) {
        super(ship);
        // 加载圆形贴图
        String CIRCLE_TEXTURE_PATH = "graphics/fx/da_targetingRing.png";
        this.circleSprite = Global.getSettings().getSprite(CIRCLE_TEXTURE_PATH);
    }

    @Override
    public float getBaseActiveDuration() {
        return 10f;
    }

    @Override
    public float getBaseCooldownDuration() {
        return 25f;
    }

    @Override
    public boolean shouldActivateAI(float amount) {
        if (!canActivate()) {
            return false;
        }

        // 检查舰船自身辐能水平
        if (ship.getFluxTracker().getFluxLevel() > 0.7f) return false;

        // 获取2500范围内的友军舰船
        List<ShipAPI> nearbyAllies = AIUtils.getNearbyAllies(ship, BASE_RANGE);

        // 条件1：友军数量超过10
        if (nearbyAllies.size() > 10) {
            return true;
        }

        // 条件2：存在巡洋舰级别以上且辐能容量大于75%的友军
        for (ShipAPI ally : nearbyAllies) {
            if (ally == ship) continue; // 跳过自身

            // 检查舰船级别（巡洋舰或战列舰）
            if (ally.getHullSize() == ShipAPI.HullSize.CRUISER ||
                    ally.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP) {

                // 检查辐能容量是否大于75%
                if (ally.getFluxTracker().getFluxLevel() > 0.75f) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public String getDisplayText() {
        return "united will march";
    }

    @Override
    public String getBriefText() {
        return "Improve the effectiveness of friendly forces";
    }

    @Override
    public float getRange() {
        return BASE_RANGE;
    }

    @Override
    public float getFluxCostFlatOnActivation() {
        return 100f;
    }

    @Override
    public float getFluxCostPercentPerSecondWhileActive() {
        return 0.1f;
    }

    @Override
    public void advance(float amount, boolean isPaused) {
        if (isPaused || !isActive()) return;

        // 更新波纹特效
        waveTimer.advance(amount);
        if (waveTimer.intervalElapsed()) {
            spawnRangeVisuals();
        }

        // 更新受影响的友军舰船
        updateAffectedShips();

        // 更新范围圈的渲染
        updateRangeCircles(amount);
    }

    @Override
    public String getExtraInfoText() {
        // 显示当前影响的舰船数量和范围信息
        int innerCount = 0, midCount = 0, outerCount = 0;

        for (ShipAPI affectedShip : affectedShips.keySet()) {
            if (affectedShip.isAlive() && !affectedShip.isHulk()) {
                Float bonus = affectedShips.get(affectedShip);
                if (bonus == INNER_RANGE_BONUS) innerCount++;
                else if (bonus == MID_RANGE_BONUS) midCount++;
                else if (bonus == OUTER_RANGE_BONUS) outerCount++;
            }
        }

        return String.format("Supporting: %d/%d/%d", innerCount, midCount, outerCount);
    }

    @Override
    public Color getHUDColor() {
        return MagicUI.BLUCOLOR;
    }

    @Override
    public void onActivate() {
        // 系统激活时初始化列表
        locked.clear();
        vulnerable.clear();
        affectedShips.clear();
        effectKeys.clear();
        renderedCircles.clear();

        // 初始波纹效果
        spawnRangeVisuals();

        // 创建范围圈的objectspace渲染
        createRangeCircles();

        // 播放激活音效
        Global.getSoundPlayer().playSound("system_targeting_feed_loop", 1.0f, 0.8f, ship.getLocation(), ship.getVelocity());
    }

    @Override
    public void onFinished() {
        // 系统结束时清理效果
        for (ShipAPI affectedShip : affectedShips.keySet()) {
            removeShipEffects(affectedShip);
        }
        locked.clear();
        vulnerable.clear();
        affectedShips.clear();
        effectKeys.clear();

        // 清理渲染的圆圈
        renderedCircles.clear();
    }

    // === 新增方法：创建范围圈的objectspace渲染 ===

    /**
     * 创建三个范围圈的objectspace渲染
     */
    private void createRangeCircles() {
        float effectLevel = getEffectLevel();
        float alphaMultiplier = effectLevel * 0.7f;

        // 创建内圈
        createSingleCircleObjectspace(
                INNER_RANGE,
                applyAlpha(INNER_COLOR, alphaMultiplier),
                10f
        );

        // 创建中圈
        createSingleCircleObjectspace(
                MID_RANGE,
                applyAlpha(MID_COLOR, alphaMultiplier),
                10f
        );

        // 创建外圈
        createSingleCircleObjectspace(
                OUTER_RANGE,
                applyAlpha(OUTER_COLOR, alphaMultiplier),
                10f
        );
    }

    /**
     * 创建单个范围圈的objectspace渲染
     */
    private void createSingleCircleObjectspace(float radius, Color color, float duration) {
        // 计算圆形直径
        float diameter = radius * 2f;
        Vector2f size = new Vector2f(diameter, diameter);

        // 使用objectspace方法创建跟随舰船的圆圈
        MagicRender.objectspace(
                circleSprite,           // 圆形贴图
                ship,                   // 锚点实体（本舰船）
                new Vector2f(0, 0),     // 偏移量（中心对齐）
                new Vector2f(0, 0),     // 相对速度
                size,                   // 大小
                new Vector2f(0, 0),     // 增长（无增长）
                0f,                     // 角度
                0f,                     // 自旋
                false,                  // 不跟随父物体旋转
                color,                  // 颜色
                false,                  // 不使用相加混合
                0.1f,                   // 淡入时间
                duration - 0.2f,        // 完全显示时间
                0.1f,                   // 淡出时间
                true                   // 在锚点销毁时淡出
                // 渲染层
        );
    }

    /**
     * 更新范围圈的渲染
     */
    private void updateRangeCircles(float amount) {
        // 由于objectspace渲染是持久的，我们不需要每帧更新
        // 但如果有需要动态调整的效果可以在这里处理
    }

    // === 修改后的渲染方法 ===

    @Override
    public void renderWorld(ViewportAPI viewport) {
        if (!isActive()) return;

        // 为受影响的友军舰船添加视觉标记
        renderAffectedShipMarkers(viewport);
    }

    // === 内置工具方法 ===

    /**
     * 生成范围视觉效果（波纹）
     */
    private void spawnRangeVisuals() {
        Vector2f shipLocation = ship.getLocation();

        // 生成三个距离圈的波纹粒子
        spawnWaveParticles(shipLocation, INNER_RANGE, INNER_COLOR, 12);
        spawnWaveParticles(shipLocation, MID_RANGE, MID_COLOR, 24);
        spawnWaveParticles(shipLocation, OUTER_RANGE, OUTER_COLOR, 36);
    }

    /**
     * 生成指定范围的波纹粒子
     */
    private void spawnWaveParticles(Vector2f center, float radius, Color color, int particleCount) {
        CombatEngineAPI engine = Global.getCombatEngine();

        for (int i = 0; i < particleCount; i++) {
            float angle = i * (360f / particleCount);
            Vector2f offset = MathUtils.getPointOnCircumference(null, radius, angle);
            Vector2f location = Vector2f.add(center, offset, null);

            // 计算粒子速度（向外扩散）
            Vector2f velocity = new Vector2f(offset);
            velocity.normalise();
            velocity.scale(radius / WAVE_DURATION * 0.3f);

            // 添加粒子
            engine.addSmoothParticle(
                    location,
                    velocity,
                    3f + (float) Math.random() * 2f,
                    1f,
                    WAVE_DURATION,
                    color
            );
        }

        // 添加核心聚集粒子效果
        addGatherParticles(center, radius, color);
    }

    /**
     * 添加聚集粒子效果
     */
    private void addGatherParticles(Vector2f center, float radius, Color color) {
        CombatEngineAPI engine = Global.getCombatEngine();

        for (int i = 0; i < 8; i++) {
            float angle = (float) (Math.random() * 360f);
            float distance = radius * 0.8f + (float) Math.random() * radius * 0.2f;
            Vector2f spawnLocation = MathUtils.getPointOnCircumference(center, distance, angle);

            // 计算向中心聚集的速度
            Vector2f toCenter = Vector2f.sub(center, spawnLocation, new Vector2f());
            toCenter.normalise();
            toCenter.scale(distance / WAVE_DURATION * 0.5f);

            engine.addSmoothParticle(
                    spawnLocation,
                    toCenter,
                    2f + (float) Math.random(),
                    0.8f,
                    WAVE_DURATION * 0.8f,
                    color
            );
        }
    }

    /**
     * 渲染受影响舰船的标记
     */
    private void renderAffectedShipMarkers(ViewportAPI viewport) {
        for (ShipAPI affectedShip : affectedShips.keySet()) {
            if (affectedShip.isAlive() && !affectedShip.isHulk() &&
                    viewport.isNearViewport(affectedShip.getLocation(), affectedShip.getCollisionRadius() * 2f)) {

                Float bonus = affectedShips.get(affectedShip);
                Color markerColor = getBonusColor(bonus);

                // 在舰船上方渲染标记圆环
                Vector2f markerPos = new Vector2f(affectedShip.getLocation());
                markerPos.y += affectedShip.getCollisionRadius() + 20f;

                // 使用singleframe绘制小型标记（因为这是临时标记）
                renderSingleMarker(markerPos, 15f, markerColor);
            }
        }
    }

    /**
     * 渲染单个标记 - 使用singleframe（因为标记是临时的）
     */
    private void renderSingleMarker(Vector2f center, float radius, Color color) {
        float diameter = radius * 2f;
        Vector2f size = new Vector2f(diameter, diameter);

        MagicRender.singleframe(
                circleSprite,
                center,
                size,
                0f,
                color,
                false,
                CombatEngineLayers.ABOVE_SHIPS_LAYER
        );
    }

    /**
     * 根据增益值获取对应的颜色
     */
    private Color getBonusColor(Float bonus) {
        if (bonus == INNER_RANGE_BONUS) return INNER_COLOR;
        if (bonus == MID_RANGE_BONUS) return MID_COLOR;
        if (bonus == OUTER_RANGE_BONUS) return OUTER_COLOR;
        return WAVE_COLOR;
    }

    /**
     * 调整颜色透明度
     */
    private Color applyAlpha(Color color, float alphaMultiplier) {
        return new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                (int) (color.getAlpha() * alphaMultiplier)
        );
    }

    /**
     * 更新受影响的友军舰船
     */
    private void updateAffectedShips() {
        // 获取范围内的所有友军舰船
        List<ShipAPI> nearbyAllies = AIUtils.getNearbyAllies(ship, OUTER_RANGE);

        // 移除不再在范围内或已摧毁的舰船
        List<ShipAPI> toRemove = new ArrayList<>();
        for (ShipAPI affectedShip : affectedShips.keySet()) {
            if (!affectedShip.isAlive() || affectedShip.isHulk() ||
                    !nearbyAllies.contains(affectedShip) ||
                    MathUtils.getDistance(ship, affectedShip) > OUTER_RANGE) {
                toRemove.add(affectedShip);
            }
        }

        for (ShipAPI shipToRemove : toRemove) {
            removeShipEffects(shipToRemove);
            affectedShips.remove(shipToRemove);
            effectKeys.remove(shipToRemove);
        }

        // 添加或更新范围内的友军舰船
        for (ShipAPI ally : nearbyAllies) {
            if (ally == ship) continue; // 跳过自身

            float distance = MathUtils.getDistance(ship, ally);
            float rangeBonus = getRangeBonusForDistance(distance);

            if (rangeBonus > 1f) { // 只有有增益时才处理
                if (!affectedShips.containsKey(ally) || !affectedShips.get(ally).equals(rangeBonus)) {
                    // 新舰船或增益变化，更新效果
                    if (affectedShips.containsKey(ally)) {
                        removeShipEffects(ally);
                    }
                    applyShipEffects(ally, rangeBonus);
                    affectedShips.put(ally, rangeBonus);
                }
            }
        }
    }

    /**
     * 根据距离获取对应的射程增益
     */
    private float getRangeBonusForDistance(float distance) {
        if (distance <= INNER_RANGE) {
            return INNER_RANGE_BONUS;
        } else if (distance <= MID_RANGE) {
            return MID_RANGE_BONUS;
        } else if (distance <= OUTER_RANGE) {
            return OUTER_RANGE_BONUS;
        }
        return 1f; // 无增益
    }

    /**
     * 对舰船应用射程增益效果
     */
    private void applyShipEffects(ShipAPI targetShip, float rangeBonus) {
        String effectKey = "byzantine_support_" + targetShip.hashCode();
        effectKeys.put(targetShip, effectKey);

        MutableShipStatsAPI stats = targetShip.getMutableStats();

        stats.getMaxSpeed().modifyMult(effectKey, rangeBonus);
        stats.getAcceleration().modifyMult(effectKey, rangeBonus);
        stats.getDeceleration().modifyMult(effectKey, rangeBonus);
        stats.getTurnAcceleration().modifyMult(effectKey, rangeBonus);
        stats.getMaxTurnRate().modifyMult(effectKey, rangeBonus);
        stats.getAutofireAimAccuracy().modifyMult(effectKey, rangeBonus);
        stats.getFighterRefitTimeMult().modifyMult(effectKey, rangeBonus);

        // 添加视觉反馈效果
        addShipActivationEffect(targetShip, rangeBonus);
    }

    /**
     * 添加舰船激活效果
     */
    private void addShipActivationEffect(ShipAPI targetShip, float rangeBonus) {
        CombatEngineAPI engine = Global.getCombatEngine();

        // 添加浮动文字
        engine.addFloatingText(
                targetShip.getLocation(),
                String.format("Speed ​​and maneuverability +%.0f%%", (rangeBonus - 1f) * 100f),
                20f,
                getBonusColor(rangeBonus),
                targetShip,
                1f, 2f
        );

        // 添加粒子效果
        for (int i = 0; i < 8; i++) {
            float angle = i * 45f;
            Vector2f offset = MathUtils.getPointOnCircumference(null, targetShip.getCollisionRadius() * 0.8f, angle);
            Vector2f location = Vector2f.add(targetShip.getLocation(), offset, null);

            Vector2f velocity = new Vector2f(offset);
            velocity.normalise();
            velocity.scale(50f);

            engine.addSmoothParticle(
                    location,
                    velocity,
                    3f,
                    0.8f,
                    1.5f,
                    getBonusColor(rangeBonus)
            );
        }
    }

    /**
     * 移除舰船的增益效果
     */
    private void removeShipEffects(ShipAPI targetShip) {
        String effectKey = effectKeys.get(targetShip);
        if (effectKey != null) {
            MutableShipStatsAPI stats = targetShip.getMutableStats();
            stats.getMaxSpeed().unmodify(effectKey);
            stats.getAcceleration().unmodify(effectKey);
            stats.getDeceleration().unmodify(effectKey);
            stats.getTurnAcceleration().unmodify(effectKey);
            stats.getMaxTurnRate().unmodify(effectKey);
            stats.getAutofireAimAccuracy().unmodify(effectKey);
            stats.getFighterRefitTimeMult().unmodify(effectKey);
        }
    }
}