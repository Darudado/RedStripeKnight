package data.scripts.weapons;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import data.scripts.weapons.render.Moci_RS_EngineRender;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipEngineControllerAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

public class RS_MechShoulderEngineAnimationController_Main implements EveryFrameWeaponEffectPlugin {
    private boolean initialized = false;
    private WeaponAPI wingWeapon = null;       // 关联的翼部武器
    private WeaponAPI shoulderWeapon = null;   // 关联的肩部武器
    private String slotId;                     // 当前武器的槽位ID

    // 当前武器创建的所有火焰渲染器
    private List<Moci_RS_EngineRender> renders = new ArrayList<>();

    // 引擎渲染参数
    private final Color engineColor = new Color(43, 149, 255, 195);
    public static float ENGINE_LENGTH_MULT = 1.75f;
    public static float ENGINE_WIDTH_MULT  = 1.75f;
    public static float GLOW_SIZE_MULT     = 0.75f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused()) {
            return;
        }

        ShipAPI ship = weapon.getShip();

        // === 初始化：绑定关联武器、创建火焰渲染器 ===
        if (!initialized) {
            slotId = weapon.getSlot().getId();

            // 需要火焰渲染的槽位（包括新增加的 SHOULDERe_A）
            boolean needsFlame = slotId.equals("SHOULDERe_LW") || slotId.equals("SHOULDERe_RW")
                    || slotId.equals("SHOULDERe_L")  || slotId.equals("SHOULDERe_R")
                    || slotId.equals("SHOULDERe_A")|| slotId.equals("subENGINE");

            if (needsFlame) {
                // 1. 根据槽位查找关联武器（用于角度跟随），SHOULDERe_A 不跟随任何武器
                if (slotId.equals("SHOULDERe_LW") || slotId.equals("SHOULDERe_RW")) {
                    String wingSlotId = slotId.equals("SHOULDERe_LW") ? "WING_L" : "WING_R";
                    for (WeaponAPI w : ship.getAllWeapons()) {
                        if (w.getSlot().getId().equals(wingSlotId)) {
                            wingWeapon = w;
                            break;
                        }
                    }
                } else if (slotId.equals("SHOULDERe_L") || slotId.equals("SHOULDERe_R")) {
                    String shoulderSlotId = slotId.equals("SHOULDERe_L") ? "SHOULDER_L" : "SHOULDER_R";
                    for (WeaponAPI w : ship.getAllWeapons()) {
                        if (w.getSlot().getId().equals(shoulderSlotId)) {
                            shoulderWeapon = w;
                            break;
                        }
                    }
                }
                // SHOULDERe_A 无需查找任何跟随武器

                // 2. 取一个引擎作为模板（用于颜色等信息）
                if (ship.getEngineController() != null && ship.getEngineController().getShipEngines() != null) {
                    ShipEngineControllerAPI.ShipEngineAPI dummyEngine = null;
                    for (ShipEngineControllerAPI.ShipEngineAPI eng : ship.getEngineController().getShipEngines()) {
                        if (eng != null) {
                            dummyEngine = eng;
                            break;
                        }
                    }

                    if (dummyEngine != null) {
                        // 加载精灵
                        SpriteAPI glowSprite = Global.getSettings().getSprite("fx", "engineflame32-orig");
                        SpriteAPI flameSprite = Global.getSettings().getSprite("fx", "engineglow32_crystal");
                        SpriteAPI cricleSprite = Global.getSettings().getSprite("fx", "MBSlowSprite");

                        // 获取当前武器的开火点信息
                        List<Vector2f> firePoints = weapon.getSpec().getTurretFireOffsets();
                        int firePointCount = (firePoints != null && !firePoints.isEmpty()) ? firePoints.size() : 1;
                        List<Float> angleOffsets = weapon.getSpec().getTurretAngleOffsets();

                        // 确定颜色
                        Color engineColorToUse = engineColor;
                        if (dummyEngine.getEngineColor() != null) {
                            engineColorToUse = new Color(
                                    dummyEngine.getEngineColor().getRed(),
                                    dummyEngine.getEngineColor().getGreen(),
                                    dummyEngine.getEngineColor().getBlue(),
                                    engineColor.getAlpha()
                            );
                        }

                        // 为每个开火点创建一个火焰渲染器
                        for (int i = 0; i < firePointCount; i++) {
                            Moci_RS_EngineRender render = new Moci_RS_EngineRender(
                                    ship, dummyEngine, weapon,
                                    glowSprite, flameSprite, cricleSprite,
                                    CombatEngineLayers.ABOVE_SHIPS_LAYER,
                                    CombatEngineLayers.ABOVE_SHIPS_LAYER,
                                    true
                            );
                            render.setFirePointIndex(i);
                            render.setActive(false);      // 初始关闭，由每帧逻辑控制
                            render.setGlowSizeMult(GLOW_SIZE_MULT);
                            render.setColorOverride(engineColorToUse);
                            render.setLengthMult(ENGINE_LENGTH_MULT);
                            render.setWidthMult(ENGINE_WIDTH_MULT);

                            // 如果开火点有独立角度则固定火焰方向
                            if (angleOffsets != null && i < angleOffsets.size()) {
                                Float angle = angleOffsets.get(i);
                                if (angle != null && Math.abs(angle) > 0.001f) {
                                    render.setFixedFacing(angle);
                                }
                            }
                            engine.addLayeredRenderingPlugin(render);
                            renders.add(render);   // 加入列表以便后续控制
                        }
                    }
                }
            }

            initialized = true;
        }

        // 飞船死亡后不再处理
        if (!ship.isAlive()) {
            return;
        }

        // === 每帧逻辑：角度跟随（SHOULDERe_A 无跟随目标，此处不会改变角度） ===
        if (wingWeapon != null) {
            weapon.setCurrAngle(wingWeapon.getCurrAngle());
        }
        if (shoulderWeapon != null) {
            weapon.setCurrAngle(shoulderWeapon.getCurrAngle());
        }

        // === 每帧逻辑：根据操控状态激活/关闭火焰 ===
        updateFlameActive(ship);
    }

    /**
     * 根据当前武器的槽位和飞船引擎操控状态，决定火焰是否显示。
     * 规则：
     * - SHOULDERe_S:  减速 或 倒退
     * - SHOULDERe_R:  左平移 或 左转
     * - SHOULDERe_L:  右平移 或 右转
     * - SHOULDERe_RW: 加速 或 左转 或 左平移
     * - SHOULDERe_LW: 加速 或 右转 或 右平移
     * - SHOULDERe_A:  仅向前加速
     */
    private void updateFlameActive(ShipAPI ship) {
        if (renders.isEmpty()) {
            return;
        }

        ShipEngineControllerAPI ctrl = ship.getEngineController();
        if (ctrl == null) {
            return;
        }

        boolean active = false;

        switch (slotId) {
            case "SHOULDERe_S":
                active = ctrl.isDecelerating() || ctrl.isAcceleratingBackwards();
                break;
            case "SHOULDERe_R":
                active = ctrl.isStrafingLeft() || ctrl.isTurningLeft();
                break;
            case "SHOULDERe_L":
                active = ctrl.isStrafingRight() || ctrl.isTurningRight();
                break;
            case "SHOULDERe_RW":
                active = ctrl.isAccelerating() || ctrl.isTurningLeft() || ctrl.isStrafingLeft();
                break;
            case "SHOULDERe_LW":
                active = ctrl.isAccelerating() || ctrl.isTurningRight() || ctrl.isStrafingRight();
                break;
            case "SHOULDERe_A", "subENGINE":
                active = ctrl.isAccelerating();   // 仅向前加速时激活
                break;
            default:
                break;
        }

        // 统一设置所有渲染器的激活状态
        for (Moci_RS_EngineRender r : renders) {
            r.setActive(active);
        }
    }
}