package data.hullmods.MotherShip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * 每 25 秒通过舰船上的 SYSTEM 类型武器槽位发射一艘舰船。
 * 若不存在任何已装备武器的 SYSTEM 槽位，则从舰船中心发射。
 * 发射的舰船装配由 SHIP_VARIANTS 列表手动配置。
 * 部署点成本自动从装配的舰船数据中获取，无需手动指定。
 */
public class CR_MotherShip1 extends BaseHullMod {

    // ---------- 可配置常量 ----------
    /** 发射间隔（秒） */
    public static final float LAUNCH_INTERVAL = 25f;

    /**
     * 手动配置的发射队列：舰船装配 variant ID 列表。
     * 发射时将按此列表的顺序循环取用。
     */
    private static final List<String> SHIP_VARIANTS = new ArrayList<>();
    static {
        // ===== 在此处添加你的舰船装配 ID =====
        SHIP_VARIANTS.add("cr_light_drone_variant");
        SHIP_VARIANTS.add("cr_heavy_drone_variant");
        SHIP_VARIANTS.add("cr_support_drone_variant");
    }

    // ---------- 船体插件生命周期 ----------
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // 确保每艘舰船只添加一次发射监听器
        if (!ship.hasListenerOfClass(LaunchData.class)) {
            ship.addListener(new LaunchData(ship));
        }
    }

    // ---------- 内部类：绑定到每艘舰船的发射控制器 ----------
    public static class LaunchData implements AdvanceableListener {

        private final ShipAPI ship;
        private float elapsed = 0f;                  // 距离下次发射的时间累计
        private int currentIndex = 0;                // 当前待发射装配的索引

        public LaunchData(ShipAPI ship) {
            this.ship = ship;
            if (SHIP_VARIANTS.isEmpty()) {
                ship.removeListener(this);
            }
        }

        @Override
        public void advance(float amount) {
            if (SHIP_VARIANTS.isEmpty()) return;

            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return;
            if (!ship.isAlive()) {
                ship.removeListener(this);
                return;
            }

            elapsed += amount;

            // 达到发射间隔后尝试发射
            if (elapsed >= LAUNCH_INTERVAL) {
                String variantId = SHIP_VARIANTS.get(currentIndex);
                // 从装配数据自动获取部署点成本
                int cost = (int) Global.getSettings().getVariant(variantId).getHullSpec().getSuppliesToRecover();

                CombatFleetManagerAPI manager = engine.getFleetManager(ship.getOwner());
                int freeCost = manager.getMaxStrength() - manager.getCurrStrength();

                if (freeCost >= cost) {
                    // ---- 确定发射位置：优先使用 SYSTEM 槽位中的武器 ----
                    Vector2f location;
                    float facing;
                    WeaponAPI launcher = getSystemSlotWeapon(ship);
                    if (launcher != null) {
                        location = launcher.getLocation();
                        facing = launcher.getCurrAngle();
                    } else {
                        location = ship.getLocation();
                        facing = ship.getFacing();
                    }

                    // ---- 创建舰队成员 ----
                    FleetMemberAPI member = Global.getFactory().createFleetMember(
                            FleetMemberType.SHIP, variantId);
                    member.updateStats();
                    member.setAlly(ship.isAlly());
                    member.setOwner(ship.getOwner());

                    // 在战役/模拟战中设置正确的指挥官
                    if (engine.isInCampaign() || engine.isInCampaignSim()) {
                        PersonAPI commander = manager.getFleetCommanderPreferPlayer();
                        if (commander != null && commander.getFleet() != null) {
                            member.setFleetCommanderForStats(commander, commander.getFleet().getFleetData());
                        }
                    }

                    // ---- 发射无人机 ----
                    boolean suppress = manager.isSuppressDeploymentMessages();
                    manager.setSuppressDeploymentMessages(true);
                    ShipAPI drone = manager.spawnFleetMember(member, location, facing, 0);
                    drone.setShipTarget(ship.getShipTarget());
                    drone.setAnimatedLaunch();
                    manager.setSuppressDeploymentMessages(suppress);

                    // ---- 发射成功，重置计时并切换到下一个装配 ----
                    elapsed -= LAUNCH_INTERVAL;
                    currentIndex = (currentIndex + 1) % SHIP_VARIANTS.size();
                }
                // 若部署点不足，保留 elapsed，下帧继续尝试，不切换装配
            }
        }

        /**
         * 参考 RS_WeaponOverloading 的 getLaunchPoints 逻辑：
         * 查找舰船上所有已装备武器中，槽位类型为 SYSTEM 的武器作为发射口。
         * 若存在多个，使用第一个找到的；若无，返回 null。
         */
        private WeaponAPI getSystemSlotWeapon(ShipAPI ship) {
            for (WeaponAPI weapon : ship.getAllWeapons()) {
                WeaponSlotAPI slot = weapon.getSlot();
                if (slot != null && slot.getWeaponType() == WeaponAPI.WeaponType.SYSTEM) {
                    return weapon;
                }
            }
            return null;
        }
    }
}