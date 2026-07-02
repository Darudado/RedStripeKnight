package data.hullmods.Tr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;

/**
 * 模块分离控制系统
 * 功能：双击X键分离指定的模块（不回收）
 * 只分离槽位ID为"CRADLE"的模块
 */
public class Tr_Cradle_Controlling extends BaseHullMod {

    // 常量定义
    private static final String MODULE_ID = "CRADLE"; // 精确匹配的模块槽位ID
    private static final float DOUBLE_CLICK_TIME = 0.3f; // 双击时间阈值（秒）
    private static final float JETTISON_SPEED = 150f; // 分离速度

    // 数据键值
    private static final String DATA_KEY = "ModuleJettisonSystem_data";
    private static final String STATS_KEY = "CRADLE_Module_id";

    private static final float SPEED_PENALTY = -15f; // 最大速度惩罚
    private static final float MOBILITY_BONUS = 0.25f; // 机动性加成（25%）

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive() || ship.isHulk()) {
            return;
        }

        // 只有有模块的船才能分离模块
        if (!ship.isShipWithModules() || ship.getChildModulesCopy().isEmpty()) {
            return;
        }

        // 获取或创建数据对象
        String key = DATA_KEY + "_" + ship.getId();
        ModuleJettisonData data = (ModuleJettisonData) Global.getCombatEngine().getCustomData().get(key);
        if (data == null) {
            data = new ModuleJettisonData();
            Global.getCombatEngine().getCustomData().put(key, data);
        }

        // 处理键盘输入
        handleKeyInput(ship, data);

        // 保存数据
        Global.getCombatEngine().getCustomData().put(key, data);
    }

    /**
     * 处理键盘输入，检测双击X键
     */
    private void handleKeyInput(ShipAPI ship, ModuleJettisonData data) {
        // 检查X键是否按下
        boolean xPressed = Keyboard.isKeyDown(Keyboard.KEY_X);

        // 如果移动键刚刚按下
        if (!data.moveKeyPressed && xPressed) {
            long now = System.currentTimeMillis();
            int inputTime = 300; // 双击时间阈值（毫秒）

            String currentKey = "X";

            // 双击检测：在时间内再次按下相同键
            if (currentKey.equals(data.lastKeyPressed) && now - data.lastKeyTime < inputTime) {
                // 1. 最大速度下调15
                ship.getMutableStats().getMaxSpeed().modifyFlat(STATS_KEY, SPEED_PENALTY);

                // 2. 机动性增加25%
                ship.getMutableStats().getTurnAcceleration().modifyPercent(STATS_KEY, MOBILITY_BONUS * 100f);
                ship.getMutableStats().getMaxTurnRate().modifyPercent(STATS_KEY, MOBILITY_BONUS * 100f);
                ship.getMutableStats().getAcceleration().modifyPercent(STATS_KEY, MOBILITY_BONUS * 100f);
                ship.getMutableStats().getDeceleration().modifyPercent(STATS_KEY, MOBILITY_BONUS * 100f);

                // 执行模块分离
                jettisonModules(ship, data);

                // 播放音效
                Global.getSoundPlayer().playSound("ui_button_press", 1f, 1f, ship.getLocation(), ship.getVelocity());

                // 显示消息
                if (ship == Global.getCombatEngine().getPlayerShip()) {
                    String message = data.jettisonedCount > 0 ?
                            String.format("CRADLE module has been separated - %d modules in total", data.jettisonedCount) :
                            "CRADLE module not found";

                    Global.getCombatEngine().getCombatUI().addMessage(1,
                            new Color(255, 150, 50, 255),
                            message,
                            new Color(255, 255, 255, 255));
                }

                // 重置按键记录
                data.lastKeyPressed = null;
                data.moveKeyPressed = true;
                return;
            } else {
                // 记录新按键和时间
                data.lastKeyPressed = currentKey;
                data.lastKeyTime = now;
            }
            data.moveKeyPressed = true;
        }

        // 如果按键释放
        if (!xPressed) {
            data.moveKeyPressed = false;
        }
    }

    /**
     * 分离所有标记的模块 - 简化版，只分离精确匹配CRADLE的模块
     */
    private void jettisonModules(ShipAPI ship, ModuleJettisonData data) {
        // 获取所有子模块
        List<ShipAPI> childModules = ship.getChildModulesCopy();
        if (childModules.isEmpty()) {
            return;
        }

        // 统计分离的模块数量
        data.jettisonedCount = 0;

        for (ShipAPI module : childModules) {
            // 只分离槽位ID精确等于"CRADLE"的模块
            if (isModuleJettisonable(module)) {
                if (jettisonModule(ship, module)) {
                    data.jettisonedCount++;

                    // 调试信息
                    if (ship == Global.getCombatEngine().getPlayerShip()) {
                        Global.getCombatEngine().getCombatUI().addMessage(0,
                                Color.GREEN,
                                String.format("Detached module: %s",
                                        module.getStationSlot() != null ? module.getStationSlot().getId() : "unknown"),
                                Color.WHITE);
                    }
                }
            }
        }

        // 如果没有找到CRADLE模块，可以添加调试信息
        if (data.jettisonedCount == 0 && ship == Global.getCombatEngine().getPlayerShip()) {
            // 显示当前所有模块的槽位ID用于调试
            StringBuilder moduleInfo = new StringBuilder("Current module slot ID:");
            for (ShipAPI module : childModules) {
                WeaponSlotAPI slot = module.getStationSlot();
                if (slot != null) {
                    moduleInfo.append(slot.getId()).append(", ");
                }
            }
            if (!moduleInfo.isEmpty()) {
                Global.getCombatEngine().getCombatUI().addMessage(0,
                        Color.YELLOW,
                        moduleInfo.toString(),
                        Color.WHITE);
            }
        }
    }

    /**
     * 判断模块是否可以被分离 - 简化版，只检查精确匹配
     */
    private boolean isModuleJettisonable(ShipAPI module) {
        if (module == null || !module.isAlive()) {
            return false;
        }

        WeaponSlotAPI slot = module.getStationSlot();
        if (slot != null) {
            // 精确匹配槽位ID为"CRADLE"
            return slot.getId().equals(MODULE_ID);
        }

        return false;
    }

    /**
     * 分离单个模块
     * @return 是否成功分离
     */
    private boolean jettisonModule(ShipAPI motherShip, ShipAPI module) {
        if (module == null || !module.isAlive() || module.isHulk()) {
            return false;
        }

        // 检查模块是否已经分离
        if (module.getStationSlot() == null) {
            return false;
        }

        try {
            // 1. 保存原始AI和槽位
            final ShipAIPlugin originalAI = module.getShipAI();

            // 2. 解除模块绑定
            module.setCollisionClass(CollisionClass.FIGHTER); // 设置为战斗机碰撞类型
            module.setLayer(CombatEngineLayers.FIGHTERS_LAYER); // 设置到战斗机层

            // 确保模块有克隆的槽位规格
            module.ensureClonedStationSlotSpec();

            // 解除槽位绑定
            module.setStationSlot(null);

            // 移除AI控制
            module.setShipAI(originalAI);

            // 3. 设置模块属性
            // 设置为友方
            module.setOwner(motherShip.getOwner());

            // 设置碰撞半径
            if (module.getCollisionRadius() < 10f) {
                module.setCollisionRadius(10f);
            }

            // 4. 应用分离速度
            applyJettisonVelocity(motherShip, module);

            // 5. 设置武器为自动开火
            enableAutoFire(module);

            // 6. 添加自主控制器
            module.addListener(new JettisonController(module, motherShip));

            // 7. 显示引擎（如果之前隐藏了）
            module.setRenderEngines(true);

            // 8. 视觉特效
            createJettisonEffects(motherShip, module);

            return true;

        } catch (Exception e) {
            // 记录错误但不崩溃
            return false;
        }
    }

    /**
     * 应用分离速度
     */
    private void applyJettisonVelocity(ShipAPI motherShip, ShipAPI module) {
        // 计算分离方向（垂直于母舰表面向外）
        Vector2f moduleLocation = new Vector2f(module.getLocation());
        Vector2f motherLocation = new Vector2f(motherShip.getLocation());

        // 计算模块相对于母舰的方向
        Vector2f dirToModule = Vector2f.sub(moduleLocation, motherLocation, null);
        float distance = dirToModule.length();

        if (distance > 0) {
            // 标准化方向
            dirToModule.scale(1f / distance);

            // 添加垂直于母舰表面的速度
            float motherFacing = motherShip.getFacing();
            Vector2f perpendicularDir = new Vector2f(
                    (float) -Math.sin(Math.toRadians(motherFacing)),
                    (float) Math.cos(Math.toRadians(motherFacing))
            );

            // 确定分离方向（向外）
            float dot = Vector2f.dot(dirToModule, perpendicularDir);
            if (dot < 0) {
                perpendicularDir.scale(-1);
            }

            // 设置分离速度 = 母舰速度 + 分离推力
            Vector2f jettisonVelocity = new Vector2f(
                    motherShip.getVelocity().x + perpendicularDir.x * JETTISON_SPEED,
                    motherShip.getVelocity().y + perpendicularDir.y * JETTISON_SPEED
            );

            module.getVelocity().set(jettisonVelocity);
        }
    }

    /**
     * 启用武器自动开火
     */
    private void enableAutoFire(ShipAPI module) {
        List<WeaponGroupAPI> weaponGroups = module.getWeaponGroupsCopy();
        int groupIndex = 0;

        for (WeaponGroupAPI group : weaponGroups) {
            if (!group.isAutofiring()) {
                // 开启自动开火
                module.giveCommand(ShipCommand.TOGGLE_AUTOFIRE, group, groupIndex);
            }
            groupIndex++;
        }

        // 选择第一个武器组
        if (!weaponGroups.isEmpty()) {
            module.giveCommand(ShipCommand.SELECT_GROUP, 0, 0);
        }
    }

    /**
     * 创建分离特效
     */
    private void createJettisonEffects(ShipAPI motherShip, ShipAPI module) {
        Vector2f moduleLocation = new Vector2f(module.getLocation());
        Vector2f motherLocation = new Vector2f(motherShip.getLocation());

        // 1. 粒子效果
        Global.getCombatEngine().addHitParticle(
                moduleLocation,
                new Vector2f(0, 0),
                50f, // 大小
                1.0f, // 亮度
                0.5f, // 持续时间
                new Color(255, 200, 100, 255) // 颜色
        );

        // 2. 火花效果
        for (int i = 0; i < 10; i++) {
            float angle = (float) (Math.random() * 360f);
            float speed = 50f + (float) Math.random() * 100f;
            Vector2f velocity = new Vector2f(
                    (float) Math.cos(Math.toRadians(angle)) * speed,
                    (float) Math.sin(Math.toRadians(angle)) * speed
            );

            Global.getCombatEngine().addSmokeParticle(
                    moduleLocation,
                    velocity,
                    10f + (float) Math.random() * 20f,
                    0.5f,
                    1.5f + (float) Math.random(),
                    new Color(200, 150, 50, 150)
            );
        }

        // 3. 音效
        Global.getSoundPlayer().playSound("mine_explosion", 0.8f, 0.5f, moduleLocation, module.getVelocity());
    }

    /**
     * 数据类 - 存储模块分离系统的状态
     */
    private static class ModuleJettisonData {
        public String lastKeyPressed = null;
        public long lastKeyTime = 0;
        public boolean moveKeyPressed = false;
        public int jettisonedCount = 0; // 新增：记录已分离的模块数量
    }

    /**
     * 模块控制器（分离后自主行动）
     */
    private static class JettisonController implements AdvanceableListener {
        private final ShipAPI module;
        private final ShipAPI motherShip;
        private boolean isActive = true;

        public JettisonController(ShipAPI module, ShipAPI motherShip) {
            this.module = module;
            this.motherShip = motherShip;
        }

        @Override
        public void advance(float amount) {
            if (!isActive) {
                return;
            }

            // 检查模块和母舰状态
            if (!module.isAlive() || module.isHulk() ||
                    !motherShip.isAlive() || motherShip.isHulk()) {
                cleanup();
                return;
            }

            // 简单的自主行为：攻击最近的敌人
            performCombatBehavior(amount);
        }

        /**
         * 执行战斗行为
         */
        private void performCombatBehavior(float amount) {
            // 寻找最近的敌人
            ShipAPI target = findNearestEnemy();

            if (target != null && target.isAlive()) {
                // 转向目标
                float targetAngle = VectorUtils.getAngle(module.getLocation(), target.getLocation());
                float currentFacing = module.getFacing();
                float angleDiff = MathUtils.getShortestRotation(currentFacing, targetAngle);

                // 转向控制
                float turnRate = module.getMaxTurnRate();
                float maxTurn = turnRate * amount * 0.5f; // 降低转向速度
                float turnAmount = Math.min(Math.abs(angleDiff), maxTurn);

                if (turnAmount > 0.1f) {
                    float turnDirection = Math.signum(angleDiff);
                    float newFacing = currentFacing + (turnAmount * turnDirection);
                    module.setFacing(newFacing);
                }

                // 移动控制
                float distance = MathUtils.getDistance(module.getLocation(), target.getLocation());
                float optimalRange = getOptimalCombatRange();

                if (distance > optimalRange * 1.5f) {
                    // 距离过远，加速接近
                    module.giveCommand(ShipCommand.ACCELERATE, null, 0);
                } else if (distance < optimalRange * 0.5f) {
                    // 距离过近，减速后退
                    module.giveCommand(ShipCommand.ACCELERATE_BACKWARDS, null, 0);
                } else {
                    // 保持距离，侧向移动
                    if (Math.random() > 0.5) {
                        module.giveCommand(ShipCommand.STRAFE_LEFT, null, 0);
                    } else {
                        module.giveCommand(ShipCommand.STRAFE_RIGHT, null, 0);
                    }
                }

                // 尝试使用战术系统
                tryUseShipSystem();
            } else {
                // 没有目标，保持当前速度和方向
                // 可选：返回母舰附近
                patrolNearMotherShip(amount);
            }
        }

        /**
         * 寻找最近的敌人
         */
        private ShipAPI findNearestEnemy() {
            ShipAPI nearest = null;
            float nearestDist = Float.MAX_VALUE;

            for (ShipAPI ship : Global.getCombatEngine().getShips()) {
                if (!ship.isAlive() || ship.isHulk()) {
                    continue;
                }

                // 检查是否为敌人
                if (ship.getOwner() + module.getOwner() == 1) {
                    float dist = MathUtils.getDistance(module.getLocation(), ship.getLocation());
                    if (dist < nearestDist && dist < 2000f) { // 搜索范围限制
                        nearestDist = dist;
                        nearest = ship;
                    }
                }
            }

            return nearest;
        }

        /**
         * 获取最佳战斗距离
         */
        private float getOptimalCombatRange() {
            float optimal = 500f; // 默认值

            // 根据武器计算最佳距离
            for (WeaponAPI weapon : module.getAllWeapons()) {
                if (weapon.getRange() > optimal) {
                    optimal = weapon.getRange();
                }
            }

            return Math.min(optimal, 1000f); // 最大限制
        }

        /**
         * 尝试使用战术系统
         */
        private void tryUseShipSystem() {
            if (module.getSystem() == null) {
                return;
            }

            if (module.getSystem().isActive() || module.getSystem().isCoolingDown()) {
                return;
            }

            if (module.getSystem().getAmmo() <= 0 && module.getSystem().getMaxAmmo() > 0) {
                return;
            }

            // 随机使用系统
            if (Math.random() < 0.01f) { // 每帧1%概率
                module.useSystem();
            }
        }

        /**
         * 在母舰附近巡逻
         */
        private void patrolNearMotherShip(float amount) {
            float distance = MathUtils.getDistance(module.getLocation(), motherShip.getLocation());

            // 如果距离母舰太远，返回附近
            if (distance > 1500f) {
                float targetAngle = VectorUtils.getAngle(module.getLocation(), motherShip.getLocation());
                float currentFacing = module.getFacing();
                float angleDiff = MathUtils.getShortestRotation(currentFacing, targetAngle);

                // 转向母舰
                float turnRate = module.getMaxTurnRate();
                float maxTurn = turnRate * amount * 0.3f;
                float turnAmount = Math.min(Math.abs(angleDiff), maxTurn);

                if (turnAmount > 0.1f) {
                    float turnDirection = Math.signum(angleDiff);
                    float newFacing = currentFacing + (turnAmount * turnDirection);
                    module.setFacing(newFacing);
                }

                // 加速返回
                module.giveCommand(ShipCommand.ACCELERATE, null, 0);
            } else {
                // 在母舰附近随机移动
                if (Math.random() < 0.02f) { // 随机改变方向
                    float randomAngle = (float) (Math.random() * 360f);
                    module.setFacing(randomAngle);
                }

                if (Math.random() < 0.5f) {
                    module.giveCommand(ShipCommand.ACCELERATE, null, 0);
                }
            }
        }

        /**
         * 清理资源
         */
        private void cleanup() {
            if (!isActive) {
                return;
            }

            isActive = false;

            // 如果模块存活，但不重新绑定（不回收）
            if (module.isAlive()) {
                // 保持当前状态，只是移除监听器
                module.removeListener(this);
                // 可以添加模块的自毁逻辑（可选）
                if (motherShip.isHulk() || !motherShip.isAlive()) {
                    module.setHulk(true); // 母舰死亡时模块也自毁
                    module.setHitpoints(0);
                }
            }
        }
    }

    // ==================== HullMod 描述部分 ====================

    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "X";
        if (index == 1) return String.format("%.1f", DOUBLE_CLICK_TIME);
        if (index == 2) return MODULE_ID;
        return null;
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize, ShipAPI ship) {
        return getDescriptionParam(index, hullSize);
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize,
                                          ShipAPI ship, float width, boolean isForModSpec) {
        float pad = 10f;
        float small = 5f;
        Color highlight = Misc.getHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();
        Color good = Misc.getPositiveHighlightColor();

        tooltip.addPara("Function description:", pad, highlight, "Function description");
        tooltip.addPara("• Double-click the X key to detach a marked module during combat", small);
        tooltip.addPara("• Detached modules will fight autonomously and will not return", small);
        tooltip.addPara("• The module will attack nearby enemies and try to stay close to the mothership", small);

        tooltip.addPara("Module marking method:", pad, highlight, "Module marking method");
        tooltip.addPara("Only separate slot id is '" + MODULE_ID + "' module", small);

        tooltip.addPara("Things to note:", pad, bad, "Things to note");
        tooltip.addPara("• Detached modules cannot be recycled", small);
        tooltip.addPara("• Modules cannot be restored after being destroyed", small);
        tooltip.addPara("• Recommended to use this feature in emergency situations", small);

        if (ship != null) {
            // 显示当前可分离的模块数量
            int jettisonableCount = countJettisonableModules(ship);
            Color countColor = jettisonableCount > 0 ? good : bad;

            tooltip.addPara("Current detachable module: %s (Slot ID: %s)", pad, countColor,
                    String.valueOf(jettisonableCount), MODULE_ID);
        }
    }

    /**
     * 统计可分离的模块数量
     */
    private int countJettisonableModules(ShipAPI ship) {
        int count = 0;
        for (ShipAPI module : ship.getChildModulesCopy()) {
            if (isModuleJettisonable(module)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Color getBorderColor() {
        return new Color(255, 150, 50, 255); // 橙色边框
    }

    @Override
    public Color getNameColor() {
        return new Color(255, 200, 100, 255); // 金色名称
    }

    @Override
    public int getDisplaySortOrder() {
        return 1000; // 较高的排序，确保显示在前
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        // 只有有子模块的船才能使用
        return ship != null && !ship.getChildModulesCopy().isEmpty();
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship == null || ship.getChildModulesCopy().isEmpty()) {
            return "The ship has no detachable modules";
        }
        return null;
    }
}