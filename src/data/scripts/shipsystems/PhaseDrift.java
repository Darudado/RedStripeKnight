package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.utils.RSUtil;
import org.boxutil.base.BaseControlData;
import org.boxutil.base.api.InstanceDataAPI;
import org.boxutil.base.api.RenderDataAPI;
import org.boxutil.config.BoxConfigs;
import org.boxutil.define.BoxEnum;
import org.boxutil.manager.CombatRenderingManager;
import org.boxutil.units.standard.attribute.Instance2Data;
import org.boxutil.units.standard.entity.SpriteEntity;
import org.boxutil.util.CalculateUtil;
import org.boxutil.util.TransformUtil;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Matrix2f;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.*;
import java.util.List;

public class PhaseDrift extends BaseShipSystemScript {
    public static final Map<ShipAPI.HullSize, Float> SPEED_FALLOFF_PER_SEC = new HashMap<>();

    public final static String ID = "Phase_Drift";
    private final static String _DATA = ID + "_DATA";
    private final static String WEAPON_OVERLOAD_DATA = ID + "_WEAPON_OVERLOAD_DATA";
    private final static String PHASE_DEFENSE_RESTORE_FLAG = ID + "_PHASE_DEFENSE_RESTORED";

    private final static Color _COLOR = new Color(175, 85, 90, 120);
    private final static Color _COLOR_CORE = new Color(255, 255 ,255, 180);
    private final static float _EFFECT_CD = 0.2f;

    private final static float DRONE_DISTANCE_THRESHOLD = 75f; // 每航行75单位距离生成一个无人机
    private final static float DRONE_DAMAGE = 400f; // 固定伤害400
    private final static float DRONE_DURATION = 2f;

    private boolean A = true;
    private final IntervalUtil Timer = new IntervalUtil(1.5f, 1.5f);


    public static Map mag = new HashMap();
    static {
        mag.put(ShipAPI.HullSize.FIGHTER, 550f);
        mag.put(ShipAPI.HullSize.FRIGATE, 700f);
        mag.put(ShipAPI.HullSize.DESTROYER, 850f);
        mag.put(ShipAPI.HullSize.CRUISER, 1000f);
        mag.put(ShipAPI.HullSize.CAPITAL_SHIP, 1200f);
    }

    // 静态初始化块 - 添加这个
    static {
        SPEED_FALLOFF_PER_SEC.put(ShipAPI.HullSize.FRIGATE, 1.0f);
        SPEED_FALLOFF_PER_SEC.put(ShipAPI.HullSize.DESTROYER, 1.1f);
        SPEED_FALLOFF_PER_SEC.put(ShipAPI.HullSize.CRUISER, 1.3f);
        SPEED_FALLOFF_PER_SEC.put(ShipAPI.HullSize.CAPITAL_SHIP, 1.5f);
        // 如果需要，也可以添加其他船体大小
        SPEED_FALLOFF_PER_SEC.put(ShipAPI.HullSize.FIGHTER, 0.9f);
    }

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (stats == null || !(stats.getEntity() instanceof ShipAPI ship)) return;
        if (!ship.isAlive()) return;


        if (ship.getVariant().hasHullMod("PolariphaseDrive")){
            stats.getTimeMult().modifyMult(id, 8.0F);
            stats.getHullDamageTakenMult().modifyMult(id, 75.0F);
            stats.getArmorDamageTakenMult().modifyMult(id, 75.0F);
            stats.getShieldDamageTakenMult().modifyMult(id, 75.0F);
            stats.getEmpDamageTakenMult().modifyMult(id, 75.0F);
            stats.getMaxTurnRate().modifyMult(id, 2.0F);
            stats.getTurnAcceleration().modifyMult(id, 3.5F);
            stats.getMaxSpeed().modifyMult(id, 3.5F);
            stats.getAcceleration().modifyPercent(id, 600.0F);
            stats.getDeceleration().modifyPercent(id, 600.0F);
            //if (ship == Global.getCombatEngine().getPlayerShip()) {
                //Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / 8.0F);
                //}
            Data data = (Data) ship.getCustomData().get(_DATA);
            if (data == null) {
                data = new Data(ship);
                ship.setCustomData(_DATA, data);
            }
            TransformUtil.createSimpleRotateMatrix(MathUtils.clampAngle(ship.getFacing()), data.facingRotate);
            if (!data.toggle) {
                data.toggle = true;
                TransformUtil.createSimpleRotateMatrix(VectorUtils.getFacing(ship.getVelocity()), data.velRotate);
                if (BoxConfigs.isShaderEnable()) {
                    byte particleNum = (byte) data.flareSlots.size();
                    SpriteEntity flare = new SpriteEntity();
                    List<InstanceDataAPI> flareList = new ArrayList<>(particleNum);
                    Instance2Data instance;
                    Vector2f curr = new Vector2f();
                    for (Vector2f flareSlot : data.flareSlots) {
                        instance = new Instance2Data();
                        curr.set(flareSlot);
                        Matrix2f.transform(data.facingRotate, curr, curr);
                        instance.setLocation(curr);
                        instance.setScale(MathUtils.getRandomNumberInRange(0.8f, 1.1f), MathUtils.getRandomNumberInRange(0.8f, 1.0f));
                        instance.setTimer(0.0f, 1.0f, 0.8f);
                        flareList.add(instance);
                    }
                    flare.getMaterialData().setEmissive(Global.getSettings().getSprite("graphics/fx/particlealpha64linear.png"));
                    flare.getMaterialData().setColor(new Color(0, 0, 0, 0));
                    flare.getMaterialData().setEmissiveColor(_COLOR);
                    flare.getMaterialData().setEmissiveColorAlpha(0.9f);
                    flare.getMaterialData().setAlphaToEmissive(0.0f);
                    flare.setBaseSizePerTiles(320.0f, 20.0f);
                    flare.setLocation(ship.getLocation());
                    flare.setControlData(data);
                    flare.setAdditiveBlend();
                    flare.setInstanceData(flareList, 0.0f, 1.0f, 0.0f);
                    flare.setInstanceDataRefreshAllFromCurrentIndex();
                    flare.submitInstanceData();
                    flare.callRefreshInstanceData(true);
                    flare.setRenderingCount(data.flareSlots.size());
                    flare.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
                    CombatRenderingManager.addEntity(BoxEnum.ENTITY_SPRITE, flare);
                }
            }

            float frameTime = Global.getCombatEngine().getElapsedInLastFrame();
            if (data.effectCD >= _EFFECT_CD) {
                data.effectCD -= _EFFECT_CD;
                Vector2f currPStart = new Vector2f(), currPEnd = new Vector2f(), currOffset = new Vector2f();
                float rand, yOffset;
                for (Vector2f arcSlot : data.arcSlots) {
                    currPStart.set(arcSlot);
                    Matrix2f.transform(data.facingRotate, currPStart, currPStart);
                    Vector2f.add(currPStart, ship.getLocation(), currPStart);
                    rand = (float) Math.random();
                    currOffset.x = -CalculateUtil.mix(100.0f, 0.0f, rand);
                    yOffset = CalculateUtil.mix(0.0f, 60.0f, rand);
                    if ((float) Math.random() <= 0.5f) yOffset = -yOffset;
                    currOffset.y = yOffset;
                    Matrix2f.transform(data.velRotate, currOffset, currOffset);
                    currPEnd.set(currPStart);
                    Vector2f.add(currPEnd, currOffset, currPEnd);
                    // will use new method in next version of BoxUtil for emp arc rendering
                    Global.getCombatEngine().spawnEmpArcVisual(currPStart, null, currPEnd, null, MathUtils.getRandomNumberInRange(24.0f, 48.0f), _COLOR, _COLOR_CORE);
                }

                DamagingExplosionSpec spec = new DamagingExplosionSpec(0.1f, ship.getCollisionRadius(), ship.getCollisionRadius(), 0.00001f, 0.0f, CollisionClass.PROJECTILE_NO_FF, CollisionClass.PROJECTILE_FIGHTER, 0.0f, 0.0f, 0.0f, 0, Color.BLACK, Color.BLACK);
                spec.setShowGraphic(false);
                spec.setDamageType(DamageType.ENERGY);
                spec.setEffect(new ExplosionEffect(ship, frameTime));
                Global.getCombatEngine().spawnDamagingExplosion(spec, ship, ship.getLocation());
            }
            data.effectCD += frameTime;
        }else if (ship.getVariant().hasHullMod("WeaponOverLoad")){
            stats.getTimeMult().modifyMult(id, 4.0F);
            stats.getMaxTurnRate().modifyMult(id, 1.5F);
            stats.getTurnAcceleration().modifyMult(id, 2.0F);
            stats.getMaxSpeed().modifyMult(id, 2.5F);
            stats.getAcceleration().modifyPercent(id, 350.0F);
            stats.getDeceleration().modifyPercent(id, 350.0F);

            // 获取或创建WeaponOverLoad数据
            WeaponOverLoadData woData = (WeaponOverLoadData) ship.getCustomData().get(WEAPON_OVERLOAD_DATA);
            if (woData == null) {
                woData = new WeaponOverLoadData(ship);
                ship.setCustomData(WEAPON_OVERLOAD_DATA, woData);
            }

            // 更新位置并计算移动距离
            Vector2f currentPos = ship.getLocation();
            if (woData.lastPosition != null) {
                float distanceMoved = MathUtils.getDistance(currentPos, woData.lastPosition);
                woData.distanceTraveled += distanceMoved;

                // 检查是否达到生成无人机的距离阈值
                if (woData.distanceTraveled >= DRONE_DISTANCE_THRESHOLD) {
                    generateLaserDrone(ship);
                    woData.distanceTraveled = 0f; // 重置距离计数器
                }
            }
            woData.lastPosition = new Vector2f(currentPos);
        }else if (ship.getVariant().hasHullMod("PhaseDefenseUnit")) {

            // PhaseDefenseUnit的其他效果（如果需要）
            stats.getTimeMult().modifyMult(id, 4.0F);
            stats.getMaxTurnRate().modifyMult(id, 1.2F);
            stats.getTurnAcceleration().modifyMult(id, 1.5F);
            stats.getMaxSpeed().modifyMult(id, 4.5F);
            stats.getAcceleration().modifyPercent(id, 250.0F);
            stats.getDeceleration().modifyPercent(id, 250.0F);

            stats.getFluxDissipation().modifyPercent(id, 700.0F);

        }else {

            stats.getTimeMult().modifyMult(id, 5.0F);
            stats.getHullDamageTakenMult().modifyMult(id, 75.0F);
            stats.getArmorDamageTakenMult().modifyMult(id, 75.0F);
            stats.getShieldDamageTakenMult().modifyMult(id, 75.0F);
            stats.getEmpDamageTakenMult().modifyMult(id, 75.0F);
            stats.getMaxTurnRate().modifyMult(id, 2.0F);
            stats.getTurnAcceleration().modifyMult(id, 3.5F);
            stats.getMaxSpeed().modifyMult(id, 2.5F);
            stats.getAcceleration().modifyPercent(id, 400.0F);
            stats.getDeceleration().modifyPercent(id, 400.0F);
        }

        if (!stats.getTimeMult().getPercentMods().containsKey(id)) {
            //Global.getSoundPlayer().playSound("diableavionics_drift", 1.0F, 1.66F, ship.getLocation(), ship.getVelocity());
            ship.setCollisionClass(CollisionClass.NONE);
        }

        Color AFTERIMAGE;
        if(!(ship.getExplosionFlashColorOverride() ==null)){
            AFTERIMAGE = ship.getExplosionFlashColorOverride();
        }else if(!(ship.getOverloadColor() ==null)){
            AFTERIMAGE = ship.getOverloadColor();
        }else{
            AFTERIMAGE =new Color(163, 86, 91, 190);
        }

        ship.addAfterimage(AFTERIMAGE, 0.0F, 0.0F, ship.getVelocity().getX() * (-2f), ship.getVelocity().getY() * (-2f), 3.0F, 0.1f, 0.1f, 0.5f, false, true, false);

//        if (A) {
//            Timer.advance(0.1f);
//            //进行一个残影的拖
//            MagicRender.battlespace(
//                    Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
//                    new Vector2f(ship.getLocation().getX(), ship.getLocation().getY()),
//                    new Vector2f(0, 0),
//                    new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()),
//                    new Vector2f(0, 0),
//                    ship.getFacing() - 90f,
//                    0f,
//                    AFTERIMAGE,
//                    true,
//                    0f,
//                    0f,
//                    0f,
//                    0f,
//                    0.1f,
//                    0.1f,
//                    0.1f,
//                    0.5f,
//                    CombatEngineLayers.BELOW_SHIPS_LAYER);
//
//            if (Timer.intervalElapsed()) {
//                A = false;
//            }
//        }

        if (state == State.OUT) {
            // 计算减速修正
            float decelMult = Math.max(0.5F, Math.min(2.0F, stats.getDeceleration().getModifiedValue() / stats.getDeceleration().getBaseValue()));
            float adjFalloffPerSec = SPEED_FALLOFF_PER_SEC.get(ship.getHullSize()) * (float) Math.pow(decelMult, 0.5F);
            float maxDecelPenalty = 1.0F / decelMult;
            float amount = Global.getCombatEngine().getElapsedInLastFrame();

            // 修改机动性状态
            stats.getMaxTurnRate().unmodify(id);
            stats.getDeceleration().modifyPercent(id, 400.0F + (maxDecelPenalty + 1.0F) * 100 *effectLevel);
            stats.getTurnAcceleration().modifyPercent(id, 50.0F * effectLevel);


            // 速度衰减逻辑 (防止超速)
            if (amount > 0.0F) {
                Vector2f velocity = ship.getVelocity();
                float currentSpeed = velocity.length();
                float maxSpeed = stats.getMaxSpeed().getModifiedValue();

                if (currentSpeed > maxSpeed) {
                    float overspeedRatio = (currentSpeed - maxSpeed) / maxSpeed *2;
                    float dynamicFalloff = MathUtils.clamp(adjFalloffPerSec + overspeedRatio * 0.2f, 0.8f, 1.5f);
                    float falloffFactor = (float) Math.pow(dynamicFalloff, amount * stats.getTimeMult().getModifiedValue());

                    // 应用速度衰减
                    Vector2f newVel = new Vector2f(velocity);
                    newVel.scale(falloffFactor);

                    // 速度下限保护
                    float newSpeed = newVel.length();
                    if (newSpeed < maxSpeed) {
                        newVel.normalise().scale(maxSpeed);
                    }
                    velocity.set(newVel);
                }
            }

        }
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getTimeMult().unmodifyMult(id);
        stats.getHullDamageTakenMult().unmodify(id);
        stats.getArmorDamageTakenMult().unmodify(id);
        stats.getShieldDamageTakenMult().unmodify(id);
        stats.getEmpDamageTakenMult().unmodify(id);
        stats.getMaxTurnRate().unmodifyMult(id);
        stats.getTurnAcceleration().unmodifyMult(id);
        stats.getAcceleration().unmodifyMult(id);
        stats.getMaxSpeed().unmodifyMult(id);
        stats.getDeceleration().unmodify(id);
        stats.getFluxDissipation().unmodify(id);
        Global.getCombatEngine().getTimeMult().unmodify(id);
        ShipAPI ship;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            if( ship.getVariant().hasHullMod("Yukikaze")){
                ship.setCollisionClass(CollisionClass.FIGHTER);
            }else {
                ship.setCollisionClass(CollisionClass.SHIP);
            }
        }

        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            if (ship.getVariant().hasHullMod("WeaponOverLoad")) {
                ship.setCustomData(WEAPON_OVERLOAD_DATA, null);
            }
            if (ship.getVariant().hasHullMod("PhaseDefenseUnit")) {
                ship.setCustomData(PHASE_DEFENSE_RESTORE_FLAG, null);
            }
        }

    }

    private final static class Data extends BaseControlData {
        public final ShipAPI ship;
        public final List<Vector2f> arcSlots = new ArrayList<>();
        public final List<Vector2f> flareSlots = new ArrayList<>();
        public Matrix2f velRotate = new Matrix2f();
        public Matrix2f facingRotate = new Matrix2f();
        public float effectCD = 0.0f;
        public boolean toggle = false;

        public Data(ShipAPI ship) {
            this.ship = ship;
            for (WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
                if (slot == null) continue;
                if (slot.getSlotSize() == WeaponAPI.WeaponSize.SMALL) {
                    if (slot.isTurret()) this.arcSlots.add(slot.getLocation()); else if (slot.isHardpoint()) this.flareSlots.add(slot.getLocation());
                }
            }
        }

        public void controlAdvance(@NotNull RenderDataAPI renderEntity, float amount) {
            if (!this.ship.getSystem().isActive()) TransformUtil.createSimpleRotateMatrix(MathUtils.clampAngle(this.ship.getFacing()), this.facingRotate);
            SpriteEntity flare = (SpriteEntity) renderEntity;
            Instance2Data curr;
            Vector2f currP = new Vector2f();
            for (byte i = 0; i < (byte) this.flareSlots.size(); ++i) {
                curr = (Instance2Data) flare.getInstanceData().get(i);
                currP.set(this.flareSlots.get(i));
                Matrix2f.transform(this.facingRotate, currP, currP);
                curr.setLocation(currP);
                curr.setScale(MathUtils.getRandomNumberInRange(0.8f, 1.1f), MathUtils.getRandomNumberInRange(0.8f, 1.0f));
            }
            if (this.ship.getSystem().isCoolingDown()) flare.getMaterialData().setEmissiveColorAlpha(this.ship.getSystem().getCooldownRemaining() / this.ship.getSystem().getSpecAPI().getCooldown(this.ship.getMutableStats()) * 0.6f);
            flare.submitInstanceData();
            flare.callRefreshInstanceData(true);
            flare.setLocation(this.ship.getLocation());
        }

        public boolean controlAlphaBasedTimer(@NotNull RenderDataAPI renderEntity) {
            return false;
        }

        public boolean controlRemoveBasedTimer(@NotNull RenderDataAPI renderEntity) {
            return false;
        }

        public boolean controlIsDone(@NotNull RenderDataAPI renderEntity) {
            return this.ship == null || !this.ship.isAlive() || (!this.toggle && !this.ship.getSystem().isCoolingDown());
        }
    }

    private record ExplosionEffect(ShipAPI source, float amount) implements OnHitEffectPlugin {

        public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
            engine.applyDamage(target, point, 15000.0f * this.amount, DamageType.ENERGY, 30000.0f * this.amount, false, false, this.source, false);
            }
        }

    private final static class WeaponOverLoadData {
        public final ShipAPI ship;
        public Vector2f lastPosition;
        public float distanceTraveled;

        public WeaponOverLoadData(ShipAPI ship) {
            this.ship = ship;
            this.lastPosition = new Vector2f(ship.getLocation());
            this.distanceTraveled = 0f;
        }
    }

    private void generateLaserDrone(ShipAPI ship) {
        if (Global.getCombatEngine() == null || !ship.isAlive()) return;

        // 寻找目标
        ShipAPI target = ship.getShipTarget();
        if (target == null) {
            // 使用AIUtils寻找最近的敌人
            if (ship.getOwner() == 0) {
                target = AIUtils.getNearestEnemy(ship);
            } else {
                target = AIUtils.getNearestAlly(ship);
            }
        }

        if (target == null) return;

        // 计算最大武器射程
//        float maxWeaponRange = calculateMaxWeaponRange(ship);
//        if (maxWeaponRange <= 0) maxWeaponRange = 1200f; // 默认射程

        float maxWeaponRange = mag.size();

        ShipAPI drone = RSUtil.addDroneattack(Global.getCombatEngine(),ship,"rs_eurychoros_payload"
                    ,ship.getLocation(),ship.getShieldRadiusEvenIfNoShield(),target.getLocation(),1f);

        // 设置无人机属性
        setupLaserDrone(drone, ship, maxWeaponRange);

    }

    private float calculateMaxWeaponRange(ShipAPI ship) {
        float maxRange = 0f;
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.getType() != WeaponAPI.WeaponType.MISSILE &&
                    weapon.getType() != WeaponAPI.WeaponType.SYSTEM) {
                maxRange = Math.max(maxRange, weapon.getRange());
            }
        }
        return maxRange;
    }

    private void setupLaserDrone(ShipAPI drone, ShipAPI parentShip, float maxRange) {
        // 添加监听器使伤害固定为400并强制硬通量（参考nonona代码）
        drone.addListener(new WeaponOverLoadDroneListener(drone));

        // 继承母舰的部分属性（参考nonona代码）
        drone.getMutableStats().getEnergyWeaponDamageMult().applyMods(
                parentShip.getMutableStats().getEnergyWeaponDamageMult());
        drone.getMutableStats().getBeamWeaponDamageMult().applyMods(
                parentShip.getMutableStats().getBeamWeaponDamageMult());

        // 设置射程为最大武器射程
//        drone.getMutableStats().getEnergyWeaponRangeBonus().modifyFlat(
//                "weapon_overload_drone", maxRange);
//        drone.getMutableStats().getBeamWeaponRangeBonus().modifyFlat(
//                "weapon_overload_drone", maxRange);

        // 设置伤害修正，确保基础伤害为200
        // 这里假设无人机的基础伤害是100，需要2倍修正来达到200
        // 实际值可能需要根据武器配置调整
        drone.getMutableStats().getEnergyWeaponDamageMult().modifyMult(
                "weapon_overload_damage", 2.0f);
        drone.getMutableStats().getBeamWeaponDamageMult().modifyMult(
                "weapon_overload_damage", 2.0f);

        // 设置无人机外观（可选）
        drone.setWeaponGlow(0.5f, new Color(255, 100, 100, 150),
                EnumSet.of(WeaponAPI.WeaponType.ENERGY));
    }

    public static class WeaponOverLoadDroneListener implements DamageDealtModifier, AdvanceableListener {
        private final ShipAPI drone;
        private float lifetime = 0f;

        public WeaponOverLoadDroneListener(ShipAPI drone) {
            this.drone = drone;
        }

        @Override
        public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
            // 强制所有伤害为硬通量伤害
            damage.setForceHardFlux(false);

            // 确保伤害值为400
            String id = "weapon_overload_drone_listener";
            damage.getModifier().modifyMult(id, DRONE_DAMAGE / damage.getDamage());

            return null;
        }

        @Override
        public void advance(float amount) {
            if (drone == null || !drone.isAlive()) return;

            lifetime += amount;
            if (lifetime >= DRONE_DURATION) {
                // 无人机时间到，销毁
                drone.setHitpoints(0);
                Global.getCombatEngine().applyDamage(drone, drone.getLocation(),
                        drone.getMaxHitpoints() * 2f, DamageType.ENERGY, 0f, true, false, null);
            }
        }
    }

}