package data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Pair;

import org.boxutil.base.BaseRenderData;
import org.boxutil.units.standard.entity.FlareEntity;
import org.boxutil.units.standard.entity.TrailEntity;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.*;

/**
 * 战斗插件，管理延迟动作和自定义渲染实体（如电弧、光晕）。
 */
public class CombatPlugin extends BaseEveryFrameCombatPlugin {
    private float currentTime;
    private CombatEngineAPI engine;
    public static final String customDataKey = "rs_CombatPlugin";
    private final Queue<ActionItem> actionList = new PriorityQueue<>();

    private final LocalData localData = new LocalData();

    // 存储所有需要渲染的实体（TrailEntity, FlareEntity 等均继承自 BaseRenderData）
    private final List<BaseRenderData> activeRenderables = new ArrayList<>();

    public CombatPlugin() {
    }

    public interface Action {
        void perform();
    }

    public static final class LocalData {
        final List<WeaponAPI> DisruptField = new ArrayList<>(50);
    }

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        currentTime = 0f;
        engine.getCustomData().put(customDataKey, this);
        Global.getCombatEngine().getCustomData().put(customDataKey, new LocalData());
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || engine.isPaused()) return;

        // 执行延迟动作
        ActionItem firstItem;
        while ((firstItem = actionList.peek()) != null && firstItem.timeToPerform <= currentTime) {
            actionList.poll();
            firstItem.action.perform();
        }
        currentTime += amount;

        // 更新所有渲染实体的计时器，并移除已过期的实体
        Iterator<BaseRenderData> iter = activeRenderables.iterator();
        while (iter.hasNext()) {
            BaseRenderData renderable = iter.next();
            renderable.advanceGlobalTimer(amount, engine.isPaused());
            if (renderable.isGlobalTimerOver()) {
                renderable.delete(); // 释放 OpenGL 资源
                iter.remove();
            }
        }
    }

    public void renderInWorldCoords(float amount, List<InputEventAPI> events) {
        // 在世界坐标系中绘制所有活跃实体
        for (BaseRenderData renderable : activeRenderables) {
            renderable.glDraw();
        }
    }

    public static void addDisrput(WeaponAPI weapon) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;
        CombatPlugin plugin = getInstance();
        if (plugin == null) return;
        plugin.localData.DisruptField.add(weapon);
    }

    /**
     * 将渲染实体添加到管理队列中，引擎将在每一帧自动绘制。
     */
    public void addRenderable(BaseRenderData renderable) {
        activeRenderables.add(renderable);
    }

    // ------------------------------------------------------------------------
    // 电弧生成方法
    // ------------------------------------------------------------------------

    /**
     * 生成一条带有抖动效果的电弧，并返回对应的 TrailEntity 和 FlareEntity 对。
     * 创建的实体将自动添加到当前插件的渲染队列中。
     *
     * @param offset      偏移位置（可为 null）
     * @param width       电弧宽度
     * @param start       起点（世界坐标或相对偏移后的坐标）
     * @param end         终点（世界坐标或相对偏移后的坐标）
     * @param fringe      电弧边缘颜色（通常为亮色）
     * @param core        电弧核心颜色（可为 null，默认白色）
     * @param jitterPower 抖动强度
     * @param full        完全显示持续时间（秒）
     * @param fadeOut     淡出持续时间（秒）
     * @return 包含电弧轨迹和终点光晕的 Pair
     */
    public static Pair<TrailEntity, FlareEntity> RS_BOX_spawnEmpArcVisual(
            @Nullable Vector2f offset,
            float width,
            Vector2f start,
            Vector2f end,
            Color fringe,
            @Nullable Color core,
            float jitterPower,
            float full,
            float fadeOut
    ) {
        // 1. 计算绝对坐标
        Vector2f absStart = (offset != null) ? Vector2f.add(offset, start, new Vector2f()) : new Vector2f(start);
        Vector2f absEnd = (offset != null) ? Vector2f.add(offset, end, new Vector2f()) : new Vector2f(end);

        // 2. 生成抖动节点
        List<Vector2f> nodes = generateJitteredLine(absStart, absEnd, jitterPower);

        // 3. 创建并配置 TrailEntity（电弧轨迹）
        TrailEntity trail = new TrailEntity();
        trail.setNodes(nodes);
        Color coreColor = (core != null) ? core : Color.WHITE;
        trail.setStartColor(coreColor);
        trail.setEndColor(coreColor);
        trail.setStartEmissive(fringe);
        trail.setEndEmissive(fringe);
        trail.setStartWidth(width);
        trail.setEndWidth(width);
        trail.setJitterPower(0f); // 节点已手动抖动，此处设为0避免重复
        trail.setStripLineMode(true); // 必须启用 Strip Line 模式才能显示为连续线条
        trail.setGlobalTimer(0f, full, fadeOut);
        trail.setAdditiveBlend(); // 使用叠加混合以获得发光效果

        // 设置纹理（如果需要）—— 请根据实际 BoxUtil API 调整
        // 若 setBaseTexture 不存在，可注释或使用其他纹理设置方法
         //MaterialData material = trail.getMaterialData();
        //if (material != null) {
            //material.setBaseTexture("graphics/fx/emp_arc.png"); // 示例路径
        //}

        // 提交节点数据到 GPU
        byte result = trail.submitNodes();
        if (result != 0) {
            Global.getLogger(CombatPlugin.class).warn("TrailEntity submitNodes failed: " + result);
        }

        // 4. 创建并配置 FlareEntity（终点光晕）
        FlareEntity flare = new FlareEntity();
        flare.setLocation(absEnd);
        flare.setSize(width * 2f, width * 2f);
        flare.setFringeColor(fringe);
        flare.setCoreColor(coreColor);
        flare.setSmooth(); // 平滑边缘
        flare.setGlowPower(1f);
        flare.setGlobalTimer(0f, full, fadeOut);
        flare.setAdditiveBlend(); // 同样使用叠加混合

        // 5. 将实体添加到管理插件中
        CombatPlugin plugin = (CombatPlugin) Global.getCombatEngine().getCustomData().get(customDataKey);
        if (plugin != null) {
            plugin.addRenderable(trail);
            plugin.addRenderable(flare);
        } else {
            Global.getLogger(CombatPlugin.class).warn("CombatPlugin instance not found, arc not added");
        }

        return new Pair<>(trail, flare);
    }

    /**
     * 根据起点、终点和抖动强度生成一条带有随机偏移的折线。
     */
    private static List<Vector2f> generateJitteredLine(Vector2f start, Vector2f end, float jitterPower) {
        List<Vector2f> nodes = new ArrayList<>();
        float dist = Vector2f.sub(end, start, new Vector2f()).length();
        // 根据距离和抖动强度决定分段数（至少2段）
        int segments = Math.max(2, (int) (dist / 15f * (1 + jitterPower)) + 6);
        Random rand = new Random();

        nodes.add(new Vector2f(start)); // 起点

        // 生成中间点
        for (int i = 1; i < segments; i++) {
            float t = (float) i / segments;
            // 线性插值基础位置
            float x = start.x + (end.x - start.x) * t;
            float y = start.y + (end.y - start.y) * t;
            Vector2f base = new Vector2f(x, y);

            // 计算垂直方向（用于抖动）
            Vector2f dir = new Vector2f(end.x - start.x, end.y - start.y);
            dir.normalise(); // 单位方向向量
            Vector2f perp = new Vector2f(-dir.y, dir.x); // 垂直向量

            // 随机偏移量：抖动幅度与 jitterPower 和距离成正比
            float offsetMagnitude = (rand.nextFloat() * 2 - 1) * jitterPower * dist * 0.15f;
            Vector2f offset = new Vector2f(perp.x * offsetMagnitude, perp.y * offsetMagnitude);
            Vector2f.add(base, offset, base);

            nodes.add(base);
        }

        nodes.add(new Vector2f(end)); // 终点
        return nodes;
    }

    // ------------------------------------------------------------------------
    // 延时任务系统（保持原有功能）
    // ------------------------------------------------------------------------

    public static void queueAction(Action action, float delay) {
        CombatPlugin instance = getInstance();
        if (instance != null) {
            instance.actionList.add(new ActionItem(action, instance.currentTime + delay));
        }
    }

    public static CombatPlugin getInstance() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) {
            return null;
        }
        return (CombatPlugin) engine.getCustomData().get(customDataKey);
    }

    public static class ActionItem implements Comparable<ActionItem> {
        Action action;
        float timeToPerform;

        public ActionItem(Action action, float timeToPerform) {
            this.action = action;
            this.timeToPerform = timeToPerform;
        }

        @Override
        public int compareTo(ActionItem other) {
            return Float.compare(timeToPerform, other.timeToPerform);
        }
    }
}