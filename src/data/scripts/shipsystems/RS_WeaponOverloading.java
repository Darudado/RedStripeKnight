package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RS_WeaponOverloading extends BaseShipSystemScript {
    public static final float DAM_BASIC_BONUS = 15f;
    public static final float ROF_BASIC_BONUS = 15f;
    public static final float DAM_ADV_BONUS = 25f;
    public static final float ROF_ADV_BONUS = 25f;
    public static final float EN_BASIC_DEC = 25f;

    // 电弧攻击相关常量
    private static final float ARC_INTERVAL = 0.25f;     // 电弧发射间隔（秒）
    private static final float ARC_DAMAGE = 100f;       // 电弧伤害
    private static final float ARC_EMP_DAMAGE = 50f;   // 电弧EMP伤害
    private static final float ARC_RANGE = 750f;       // 电弧射程
    private static final Color ARC_COLOR = new Color(125, 0, 155, 255); // 电弧颜色
    private static final String ARC_SOUND = "tachyon_lance_emp_impact"; // 电弧音效

    private float arcTimer = 0f;
    private final Random random = new Random();

    private final Object STATUSKEY1 = new Object();
    private final Object STATUSKEY2 = new Object();
    private final Object STATUSKEY3 = new Object();
    private final Object STATUSKEY4 = new Object();
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {

        if (stats.getEntity() instanceof ShipAPI ship) {
            if (ship.getVariant().hasHullMod("PolariphaseDrive")) {
                stats.getMaxSpeed().modifyFlat(id , 25f);
                stats.getTimeMult().modifyPercent(id , 50f);

                stats.getEnergyWeaponDamageMult().modifyPercent(id, DAM_BASIC_BONUS/2);
                stats.getBallisticWeaponDamageMult().modifyPercent(id, DAM_BASIC_BONUS/2);
                stats.getEnergyRoFMult().modifyPercent(id, ROF_BASIC_BONUS/2);
                stats.getBallisticRoFMult().modifyPercent(id, ROF_BASIC_BONUS/2);
                stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1 - EN_BASIC_DEC / 150);
                stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1 - EN_BASIC_DEC / 150);

                if (Global.getCombatEngine().getPlayerShip() == ship) {
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            this.STATUSKEY1,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Non-missile weapon damage increased by 8%",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            this.STATUSKEY2,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Non-missile weapon fire rate increased by 8%",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            this.STATUSKEY3,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Flux cost of non-missile weapons reduced by 15%",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            this.STATUSKEY4,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "The speed increases by 25 knots and the time flow rate changes",
                            false
                    );
                }

            }else if(ship.getVariant().hasHullMod("PhaseDefenseUnit")){
                stats.getEnergyWeaponDamageMult().modifyPercent(id, DAM_BASIC_BONUS/2);
                stats.getBallisticWeaponDamageMult().modifyPercent(id, DAM_BASIC_BONUS/2);
                stats.getEnergyRoFMult().modifyPercent(id, ROF_BASIC_BONUS/2);
                stats.getBallisticRoFMult().modifyPercent(id, ROF_BASIC_BONUS/2);
                stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1 - EN_BASIC_DEC / 150);
                stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1 - EN_BASIC_DEC / 150);
                stats.getShieldUpkeepMult().modifyMult(id , 0.75f);
                stats.getShieldDamageTakenMult().modifyMult(id , 0.75f);
                stats.getHardFluxDissipationFraction().modifyPercent(id ,10f);


                    // 获取时间增量
                    CombatEngineAPI engine = com.fs.starfarer.api.Global.getCombatEngine();
                    if (engine != null && engine.isPaused()) return;

                    float amount = 0;
                    if (com.fs.starfarer.api.Global.getCombatEngine() != null) {
                        amount = com.fs.starfarer.api.Global.getCombatEngine().getElapsedInLastFrame();
                    }
                    arcTimer += amount;

                    // 每隔ARC_INTERVAL秒触发一次电弧攻击
                    if (arcTimer >= ARC_INTERVAL) {
                        arcTimer = 0f;

                        // 获取发射位置
                        List<Vector2f> launchPoints = getLaunchPoints(ship);
                        if (!launchPoints.isEmpty()) {
                            // 寻找目标
                            List<CombatEntityAPI> targets = findTargets(ship);

                            // 发射电弧（最多两道）
                            int arcCount = Math.min(2, launchPoints.size());
                            for (int i = 0; i < arcCount; i++) {
                                if (i < targets.size()) {
                                    Vector2f launchPoint = launchPoints.get(i);
                                    CombatEntityAPI target = targets.get(i);

                                    launchArc(ship, launchPoint, target, engine);
                                }
                            }
                        }

                        if (Global.getCombatEngine().getPlayerShip() == ship) {
                            Global.getCombatEngine().maintainStatusForPlayerShip(
                                    this.STATUSKEY1,
                                    ship.getSystem().getSpecAPI().getIconSpriteName(),
                                    ship.getSystem().getDisplayName(),
                                    "Non-missile weapon damage increased by 8%",
                                    false
                            );
                            Global.getCombatEngine().maintainStatusForPlayerShip(
                                    this.STATUSKEY2,
                                    ship.getSystem().getSpecAPI().getIconSpriteName(),
                                    ship.getSystem().getDisplayName(),
                                    "Non-missile weapon fire rate increased by 8%",
                                    false
                            );
                            Global.getCombatEngine().maintainStatusForPlayerShip(
                                    this.STATUSKEY3,
                                    ship.getSystem().getSpecAPI().getIconSpriteName(),
                                    ship.getSystem().getDisplayName(),
                                    "Flux cost of non-missile weapons reduced by 15%",
                                    false
                            );
                            Global.getCombatEngine().maintainStatusForPlayerShip(
                                    this.STATUSKEY4,
                                    ship.getSystem().getSpecAPI().getIconSpriteName(),
                                    ship.getSystem().getDisplayName(),
                                    "Shield effectiveness increased",
                                    false
                            );
                        }
                    }
            }else if(ship.getVariant().hasHullMod("WeaponOverLoad")){
                stats.getEnergyWeaponDamageMult().modifyPercent(id, DAM_ADV_BONUS);
                stats.getBallisticWeaponDamageMult().modifyPercent(id, DAM_ADV_BONUS);
                stats.getEnergyRoFMult().modifyPercent(id, ROF_ADV_BONUS);
                stats.getBallisticRoFMult().modifyPercent(id, ROF_ADV_BONUS);
                stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1 - EN_BASIC_DEC / 50);
                stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1 - EN_BASIC_DEC / 50);

                if (Global.getCombatEngine().getPlayerShip() == ship) {
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            this.STATUSKEY1,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Non-missile weapon damage increased by 25%",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            this.STATUSKEY2,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Non-missile weapon fire rate increased by 25%",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            this.STATUSKEY3,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Flux cost of non-missile weapons reduced by 50%",
                            false
                    );
                }
            }else {
                stats.getEnergyWeaponDamageMult().modifyPercent(id, DAM_BASIC_BONUS);
                stats.getBallisticWeaponDamageMult().modifyPercent(id, DAM_BASIC_BONUS);
                stats.getEnergyRoFMult().modifyPercent(id, ROF_BASIC_BONUS);
                stats.getBallisticRoFMult().modifyPercent(id, ROF_BASIC_BONUS);
                stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1 - EN_BASIC_DEC / 100);
                stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1 - EN_BASIC_DEC / 100);

                if (Global.getCombatEngine().getPlayerShip() == ship) {
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            this.STATUSKEY1,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Non-missile weapon damage increased by 15%",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            this.STATUSKEY2,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "The rate of fire of non-missile weapons is increased by 15%",
                            false
                    );
                    Global.getCombatEngine().maintainStatusForPlayerShip(
                            this.STATUSKEY3,
                            ship.getSystem().getSpecAPI().getIconSpriteName(),
                            ship.getSystem().getDisplayName(),
                            "Flux cost of non-missile weapons reduced by 25%",
                            false
                    );
                }
            }
        }
    }


    private List<Vector2f> getLaunchPoints(ShipAPI ship) {
        List<Vector2f> points = new ArrayList<>();

        // 1. 首先查找SYSTEM类型的武器槽位
        for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
            if (slot.getWeaponType() == WeaponAPI.WeaponType.SYSTEM) {
                // 检查槽位是否被占用（内置武器）
                String weaponId = ship.getVariant().getWeaponId(slot.getId());
                if (weaponId != null && !weaponId.isEmpty()) {
                    points.add(slot.computePosition(ship));
                }
            }
        }

        // 2. 如果没有找到SYSTEM槽位，则查找所有内置武器槽位
        if (points.isEmpty()) {
            for (String slotId : ship.getVariant().getFittedWeaponSlots()) {
                if (ship.getVariant().getWeaponId(slotId) != null) {
                    WeaponSlotAPI slot = ship.getHullSpec().getWeaponSlotAPI(slotId);
                    if (slot != null) {
                        points.add(slot.computePosition(ship));
                    }
                }
            }
        }

        return points;
    }

    /**
     * 寻找目标（优先打击导弹与战机）
     */
    private List<CombatEntityAPI> findTargets(ShipAPI ship) {
        List<CombatEntityAPI> missiles = new ArrayList<>();
        List<CombatEntityAPI> fighters = new ArrayList<>();
        List<CombatEntityAPI> ships = new ArrayList<>();

        // 获取战斗引擎
        CombatEngineAPI engine = com.fs.starfarer.api.Global.getCombatEngine();

        // 修改：使用 getShips() 而不是 getEntities()
        List<ShipAPI> allShips = engine.getShips();
        for (ShipAPI targetShip : allShips) {
            // 检查是否为敌方目标且不是自己
            if (targetShip.getOwner() != ship.getOwner() && targetShip.getOwner() != 100) {
                float distance = Misc.getDistance(ship.getLocation(), targetShip.getLocation());
                if (distance <= ARC_RANGE) {
                    // 检查是否存活
                    if (targetShip.isAlive()) {
                        if (targetShip.isFighter() || targetShip.isDrone()) {
                            fighters.add(targetShip);
                        } else {
                            ships.add(targetShip);
                        }
                    }
                }
            }
        }

        // 修改：使用 getMissiles() 获取导弹
        List<MissileAPI> allMissiles = engine.getMissiles();
        for (MissileAPI missile : allMissiles) {
            // 检查是否为敌方目标
            if (missile.getOwner() != ship.getOwner() && missile.getOwner() != 100) {
                float distance = Misc.getDistance(ship.getLocation(), missile.getLocation());
                if (distance <= ARC_RANGE) {
                    // 检查导弹是否存活
                    if (engine.isMissileAlive(missile)) {
                        missiles.add(missile);
                    }
                }
            }
        }

        // 合并列表：优先导弹，然后是战机，最后是舰船
        List<CombatEntityAPI> allTargets = new ArrayList<>();
        allTargets.addAll(missiles);
        allTargets.addAll(fighters);
        allTargets.addAll(ships);

        // 随机选择目标（但保持优先级）
        List<CombatEntityAPI> selectedTargets = new ArrayList<>();
        if (!allTargets.isEmpty()) {
            // 至少选择一个目标
            int index = random.nextInt(allTargets.size());
            selectedTargets.add(allTargets.get(index));

            // 如果有多个目标，尝试选择第二个（与第一个不同）
            if (allTargets.size() > 1) {
                int secondIndex = index;
                while (secondIndex == index && allTargets.size() > 1) {
                    secondIndex = random.nextInt(allTargets.size());
                }
                selectedTargets.add(allTargets.get(secondIndex));
            }
        }

        return selectedTargets;
    }

    /**
     * 发射电弧
     */
    private void launchArc(ShipAPI source, Vector2f launchPoint, CombatEntityAPI target, CombatEngineAPI engine) {
        if (source == null || target == null) {
            return;
        }

        // 修改：根据实体类型检查是否存活
        boolean targetAlive;
        if (target instanceof ShipAPI) {
            targetAlive = ((ShipAPI) target).isAlive();
        } else if (target instanceof MissileAPI) {
            targetAlive = engine.isMissileAlive((MissileAPI) target);
        } else {
            // 对于其他类型的实体，检查是否过期
            targetAlive = !target.isExpired();
        }

        if (!source.isAlive() || !targetAlive) {
            return;
        }

        // 创建电弧
        engine.spawnEmpArc(
                source,                    // 来源
                launchPoint,               // 起点
                source,                    // 起点实体
                target,                    // 目标
                DamageType.ENERGY,         // 伤害类型
                ARC_DAMAGE,                // 伤害
                ARC_EMP_DAMAGE,            // EMP伤害
                ARC_RANGE,                 // 最大范围
                ARC_SOUND,                 // 音效
                25f,                       // 电弧厚度
                ARC_COLOR,                 // 电弧颜色
                Color.WHITE                // 淡出颜色
        );

        // 添加视觉特效
        engine.addHitParticle(
                launchPoint,
                new Vector2f(),
                30f,                       // 尺寸
                1.0f,                      // 亮度
                0.3f,                      // 持续时间
                new Color(225, 100, 255, 200)
        );
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        // 移除所有状态修改
        stats.getEnergyWeaponDamageMult().unmodify(id);
        stats.getBallisticWeaponDamageMult().unmodify(id);
        stats.getEnergyRoFMult().unmodify(id);
        stats.getBallisticRoFMult().unmodify(id);
        stats.getEnergyWeaponFluxCostMod().unmodify(id);
        stats.getBallisticWeaponFluxCostMod().unmodify(id);
        stats.getMaxSpeed().unmodify(id);
        stats.getTimeMult().unmodify(id);
        stats.getShieldUpkeepMult().unmodify(id);
        stats.getShieldDamageTakenMult().unmodify(id);
        stats.getHardFluxDissipationFraction().unmodify(id);

        // 重置电弧计时器
        arcTimer = 0f;
    }
}