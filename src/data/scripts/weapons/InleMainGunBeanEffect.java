package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * 光束武器增强特效插件 - 旋转电弧生成器
 * 实现BeamEffectPlugin接口：游戏会在光束武器每帧刷新时自动调用该插件的advance方法
 * 核心功能：
 * 1. 在光束开火时，以武器发射点为圆心生成4个顺时针旋转的点位
 * 2. 从旋转点位朝武器瞄准方向持续发射紫色等离子电弧
 * 3. 电弧具备完整的敌方碰撞检测，可击中舰船/战机/导弹，穿透护盾造成能量伤害
 * 4. 电弧击中目标后触发链式闪电，递归弹射到周围敌方目标，造成连环伤害
 * 5. 附带全套视觉特效（光晕、粒子、光源）和音效，参数支持距离衰减
 */
public class InleMainGunBeanEffect implements BeamEffectPlugin {

    // ============= 【旋转点位相关常量】- 可直接修改自定义旋转效果 =============
    private static final float ROTATION_DISTANCE = 75f;      // 旋转半径（像素）：点位离发射点的距离，越大电弧越远
    private static final float ROTATION_SPEED = -3f;        // 旋转速度（度/0.05秒）：负数=顺时针旋转，正数=逆时针，绝对值越大越快
    private static final float TIME_INTERVAL = 0.1f;        // 旋转角度更新间隔（秒）：固定0.05秒更新一次角度，避免帧速影响旋转速度
    private static final float PROJECTILE_SPAWN_INTERVAL = 0.1f; // 电弧生成间隔（秒）：持续开火时，每0.05秒生成一次电弧，越小越密集

    // ============= 【电弧核心相关常量】- 可直接修改自定义电弧属性 =============
    private static final String LIGHTNING_SOUND_ID = "mote_attractor_impact_emp_arc"; // 电弧击中音效ID（游戏原生音效）
    private static final float SLOP_RANGE = 15F;            // 电弧超射程冗余距离：防止目标贴脸时无法击中
    private static final EnumSet<CollisionClass> ALLOWED_COLLISIONS; // 电弧可击中的实体类型枚举
    private static final Vector2f ZERO;                      // 零向量：复用对象，避免频繁new Vector2f造成内存浪费
    private static final float INITIAL_DAMAGE = 45F;      // 电弧基础伤害值（能量伤害）
    private static final float INITIAL_EMP = 25F;           // 电弧基础EMP值（电磁瘫痪，此处设0）
    private static final int INITIAL_ARC_COUNT = 1;          // 单次击中生成的初始链式电弧数量
    private static final int MAX_CHAIN_COUNT = 1;            // 链式闪电最大递归传导次数（2次=弹射3个目标）
    private static final float DAMAGE_ENHANCE_RATE = 3f;   // 链式闪电伤害增强倍率：每弹射一次，伤害提升20%
    private static final float MIN_DAMAGE_THRESHOLD = 1f;    // 链式闪电最低伤害阈值：低于该值停止传导
    private static final float CHAIN_SEARCH_RANGE = 1000f;    // 链式闪电目标搜索范围（像素）
    private static final float CHAIN_ARC_THICKNESS = 25f;    // 链式电弧的基础厚度
    private static final Random RANDOM = new Random();       // 随机数对象：用于链式闪电随机选择目标

    // ============= 【运行时状态变量】- 记录插件实时运行状态，请勿随意修改 =============
    private float currentRotation = 0f;                      // 当前累计旋转角度（核心变量，驱动4个点位旋转）
    private float timeSinceLastRotation = 0f;                // 距离上次更新旋转角度的累计时间
    private float timeSinceLastArcSpawn = 0f;                // 距离上次生成电弧的累计时间
    private boolean weaponWasFiring = false;                 // 上一帧武器是否开火：用于判断「刚开火」和「刚停火」的状态切换
    private boolean hasGeneratedOnFire = false;              // 本轮开火周期是否已生成电弧：防止单次开火重复生成

    private boolean wasZero = true;
    private final IntervalUtil fireInterval = new IntervalUtil(0.25f, 1.75f);
    boolean runOnce = false;


    // 静态代码块：初始化枚举和常量对象，只执行一次
    static {
        // 初始化电弧允许击中的实体类型：战机、友方导弹、敌方导弹、舰船（核心配置，不要加友方舰船）
        ALLOWED_COLLISIONS = EnumSet.of(
                CollisionClass.FIGHTER,
                CollisionClass.MISSILE_FF,
                CollisionClass.MISSILE_NO_FF,
                CollisionClass.SHIP
        );
        ZERO = new Vector2f(); // 初始化零向量
    }

    /**
     * 插件核心回调方法 - 每帧执行（游戏原生回调）
     * 游戏引擎会在光束武器存在的每一帧调用该方法，是整个插件的逻辑入口
     * @param amount 帧时间增量（秒）：每帧的耗时，用于累计时间、平滑更新
     * @param engine 战斗引擎核心对象：所有战斗相关操作的入口（生成特效、施加伤害、获取实体等）
     * @param beam 当前挂载该插件的光束武器实例
     */
    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        // 游戏暂停时直接返回，不执行任何逻辑，避免状态异常
        if (engine.isPaused()) {
            return;
        }

        WeaponAPI weapon = beam.getWeapon();
        // 获取光束所属的武器对象，为空则直接返回（异常防护）
        if (weapon == null) return;

        float range = beam.getBrightness() * 800f;
        int alpha_value1 = Math.round(beam.getBrightness() * 50f);

        if (alpha_value1 > 255); {
            alpha_value1 = 255;
        }

        MagicRender.battlespace(Global.getSettings().getSprite("graphics/starscape/star2.png"), beam.getFrom(), new Vector2f(),
                new Vector2f(50f, 25f),
                new Vector2f(range * 2f, range),
                weapon.getCurrAngle() - 90f,
                0f,
                new Color(225,110,250, alpha_value1),
                true,
                0.07f,
                0f,
                0.14f);

        if (beam.getBrightness() >= 1f) {
            float dur = beam.getDamage().getDpsDuration();
            // needed because when the ship is in fast-time, dpsDuration will not be reset every frame as it should be
            if (!wasZero) dur = 0;
            wasZero = beam.getDamage().getDpsDuration() <= 0;
            fireInterval.advance(dur);

            //boolean hitShield = target.getShield() != null && target.getShield().isWithinArc(beam.getTo());

            if (!runOnce) {
                runOnce = true;
                Global.getSoundPlayer().playSound("LEA_fire", 1.1f, 1.5f, beam.getFrom(), new Vector2f());
                engine.spawnProjectile(weapon.getShip(), weapon, "rs_Tr_6_Inle_maingun_proj", beam.getFrom(), weapon.getCurrAngle(), new Vector2f());
                if (weapon.getChargeLevel() >= 0.5f) {
                    beam.setWidth(beam.getWidth() * (-1f + (weapon.getChargeLevel() * 2.5f)));
                }
            }
        } else {
            runOnce = false;
        }

        // 判断当前武器是否处于开火状态
        boolean weaponIsFiring = weapon.isFiring();

        // 核心逻辑1：无论武器是否开火，都持续更新旋转点位的角度（旋转不停）
        updateRotationPoints(amount);

        // 核心逻辑2：武器开火时，生成旋转点位的电弧
        if (weaponIsFiring) {
            // 刚切换到开火状态：重置电弧生成的状态变量，避免残留状态影响
            if (!weaponWasFiring) {
                hasGeneratedOnFire = false;
                timeSinceLastArcSpawn = 0f;
            }

            // 累计电弧生成的间隔时间
            timeSinceLastArcSpawn += amount;

            // 首次开火：立即生成一次电弧
            if (!hasGeneratedOnFire) {
                spawnRotatingPointArcs(engine, weapon);
                hasGeneratedOnFire = true;
            }

            // 持续开火：达到间隔时间则循环生成电弧，形成持续弹幕效果
            if (timeSinceLastArcSpawn >= PROJECTILE_SPAWN_INTERVAL) {
                timeSinceLastArcSpawn = 0f;
                spawnRotatingPointArcs(engine, weapon);
            }
        }
        // 武器刚停止开火：重置状态变量，为下一次开火做准备
        else if (weaponWasFiring) {
            hasGeneratedOnFire = false;
            timeSinceLastArcSpawn = 0f;
        }

        // 更新上一帧的开火状态，用于下一帧的状态切换判断
        weaponWasFiring = weaponIsFiring;
    }

    // ============= 【核心业务方法 - 模块化拆分，职责单一】 =============

    /**
     * 更新旋转点位的角度：核心旋转逻辑，保证旋转速度恒定不受帧速影响
     * @param amount 帧时间增量
     */
    private void updateRotationPoints(float amount) {
        // 累计时间，达到固定间隔才更新角度
        timeSinceLastRotation += amount;
        if (timeSinceLastRotation >= TIME_INTERVAL) {
            // 累加旋转角度，实现持续旋转
            currentRotation += ROTATION_SPEED;
            // 角度归一化：保持在0-360度范围内，防止数值溢出
            if (currentRotation >= 360f) {
                currentRotation -= 360f;
            }
            // 重置累计时间，开始下一轮计时
            timeSinceLastRotation = 0f;
        }
    }

    /**
     * 核心方法：在4个旋转点位上生成电弧，是旋转点位到电弧发射的桥梁方法
     * @param engine 战斗引擎
     * @param weapon 电弧的源武器
     */
    private void spawnRotatingPointArcs(CombatEngineAPI engine, WeaponAPI weapon) {
        // 获取武器所属的舰船，舰船死亡/不存在则返回
        ShipAPI ship = weapon.getShip();
        if (ship == null || !ship.isAlive()) return;

        // 获取武器的真实发射点（炮口），而非武器的安装位置，精准定位电弧起点
        Vector2f firePoint = getWeaponFirePoint(weapon);
        if (firePoint == null) return;

        // 获取武器当前的瞄准朝向，电弧将沿该方向发射
        float weaponFacing = weapon.getCurrAngle();

        // 定义4个固定的基础角度：0°,90°,180°,270° → 形成正十字形的4个点位
        float[] baseAngles = {0f, 72f, 144f, 216f, 288f};

        // 遍历4个基础角度，为每个角度计算旋转后的实时点位，并生成电弧
        for (float baseAngle : baseAngles) {
            float currentAngle = baseAngle + currentRotation; // 基础角度+累计旋转角度=实时旋转角度
            Vector2f pointLocation = calculateOffsetLocation(firePoint, weaponFacing + currentAngle); // 计算实时点位坐标
            fireLightningArc(engine, ship, weapon, pointLocation, weaponFacing); // 从该点位发射电弧
        }
    }

    /**
     * 精准获取武器的炮口发射点坐标（核心工具方法）
     * 优先取硬点/炮塔的发射偏移量，转换为世界坐标，而非直接用武器位置，保证电弧从炮口射出
     * @param weapon 源武器
     * @return 炮口的绝对世界坐标
     */
    private Vector2f getWeaponFirePoint(WeaponAPI weapon) {
        // 优先获取硬点武器的发射偏移（固定炮）
        if (!weapon.getSpec().getHardpointFireOffsets().isEmpty()) {
            Vector2f fireOffset = new Vector2f(weapon.getSpec().getHardpointFireOffsets().get(0));
            Vector2f result = new Vector2f();
            // 偏移量按武器朝向旋转后，叠加到武器位置 → 得到真实炮口坐标
            Vector2f.add(weapon.getLocation(), Misc.rotateAroundOrigin(fireOffset, weapon.getCurrAngle()), result);
            return result;
        }
        // 其次获取炮塔武器的发射偏移（可旋转炮）
        else if (!weapon.getSpec().getTurretFireOffsets().isEmpty()) {
            Vector2f fireOffset = new Vector2f(weapon.getSpec().getTurretFireOffsets().get(0));
            Vector2f result = new Vector2f();
            Vector2f.add(weapon.getLocation(), Misc.rotateAroundOrigin(fireOffset, weapon.getCurrAngle()), result);
            return result;
        }
        // 兜底：无定义发射点则使用武器本身的位置
        else {
            return weapon.getLocation();
        }
    }

    /**
     * 计算指定基础位置的偏移坐标（极坐标转笛卡尔坐标）
     * @param baseLocation 基础坐标（炮口）
     * @param angle 偏移角度（旋转后的角度）
     * @return 偏移后的绝对坐标
     */
    private Vector2f calculateOffsetLocation(Vector2f baseLocation, float angle) {
        // 获取指定角度的单位向量 → 缩放为旋转半径 → 叠加到基础坐标
        Vector2f offset = Misc.getUnitVectorAtDegreeAngle(angle);
        offset.scale(ROTATION_DISTANCE);
        return Vector2f.add(baseLocation, offset, null);
    }

    /**
     * 【核心核心方法】发射等离子电弧/闪电链，包含完整的：碰撞检测、目标筛选、伤害计算、视觉特效、链式闪电触发
     * 是整个插件的核心实现，所有电弧相关的逻辑都集中在这里
     * @param engine 战斗引擎
     * @param ship 电弧发射方舰船
     * @param weapon 源武器
     * @param spawnLocation 电弧的起点（旋转点位）
     * @param facing 电弧的发射朝向（武器瞄准方向）
     */
    private void fireLightningArc(CombatEngineAPI engine, ShipAPI ship, WeaponAPI weapon,
                                  Vector2f spawnLocation, float facing) {
        CombatEntityAPI target = null; // 电弧最终击中的有效目标，初始为空
        // 计算电弧的虚拟射线终点：武器射程+冗余距离，沿发射朝向延伸
        Vector2f endpoint = new Vector2f(weapon.getRange() + SLOP_RANGE, 0.0F);
        VectorUtils.rotate(endpoint, facing, endpoint); // 按朝向旋转射线
        Vector2f.add(spawnLocation, endpoint, endpoint); // 偏移到电弧起点
        Vector2f point = new Vector2f(endpoint); // 实际碰撞点，初始为射线终点
        Vector2f visualPoint = point; // 电弧视觉特效的显示点，与碰撞点一致

        // 第一步：筛选电弧射程内的所有实体
        List<CombatEntityAPI> entitiesToCheck = CombatUtils.getEntitiesWithinRange(spawnLocation, weapon.getRange() + SLOP_RANGE);
        Iterator<CombatEntityAPI> iter = entitiesToCheck.iterator();

        // 第二步：过滤无效实体，只保留可被电弧击中的敌方目标
        while (iter.hasNext()) {
            CombatEntityAPI entity = iter.next();
            if (entity == ship) iter.remove(); // 排除自身舰船
            else if (!ALLOWED_COLLISIONS.contains(entity.getCollisionClass())) iter.remove(); // 排除不允许的碰撞类型
            else if (entity.getOwner() == ship.getOwner()) iter.remove(); // 排除友方单位（核心！防止误伤）
        }

        // 第三步：按距离排序，优先检测离电弧起点更近的目标（符合物理逻辑）
        entitiesToCheck.sort((o1, o2) -> {
            float d1 = MathUtils.getDistance(spawnLocation, o1.getLocation());
            float d2 = MathUtils.getDistance(spawnLocation, o2.getLocation());
            return Float.compare(d1, d2);
        });

        // 第四步：逐目标进行精准碰撞检测，找到第一个被电弧击中的有效目标
        for (CombatEntityAPI entity : entitiesToCheck) {
            if (CollisionUtils.getCollides(spawnLocation, endpoint, entity.getLocation(), entity.getCollisionRadius())) {
                // 情况1：电弧起点直接在目标碰撞圈内（极近距离）
                if (CollisionUtils.isPointWithinCollisionCircle(spawnLocation, entity) && CollisionUtils.isPointWithinBounds(spawnLocation, entity)) {
                    point = spawnLocation;
                    visualPoint = spawnLocation;
                    // 微调视觉点，避免特效与目标重叠
                    Vector2f extra = new Vector2f(25.0F, 0.0F);
                    VectorUtils.rotate(extra, facing, extra);
                    Vector2f.add(visualPoint, extra, visualPoint);
                    target = entity;
                    break;
                }

                // 情况2：目标是舰船，优先检测护盾碰撞，再检测船体碰撞（护盾优先级最高）
                if (entity instanceof ShipAPI targetShip) {
                    if (targetShip.getShield() != null && targetShip.getShield().isOn()) {
                        Vector2f collision = getCollisionRayCircle(spawnLocation, endpoint,
                                targetShip.getShield().getLocation(), targetShip.getShield().getRadius());
                        // 碰撞点在护盾的激活弧内才有效（护盾有射击死角）
                        if (collision != null && Misc.isInArc(targetShip.getShield().getFacing(),
                                targetShip.getShield().getActiveArc(), targetShip.getShield().getLocation(), collision)) {
                            point = collision;
                            visualPoint = collision;
                            target = entity;
                            break;
                        }
                    }

                    // 检测船体边界碰撞
                    BoundsAPI bounds = entity.getExactBounds();
                    if (bounds != null) bounds.update(entity.getLocation(), entity.getFacing());
                    Vector2f collision = CollisionUtils.getCollisionPoint(spawnLocation, endpoint, entity);
                    if (collision != null) {
                        point = collision;
                        visualPoint = collision;
                        target = entity;
                        break;
                    }
                }
                // 情况3：目标是战机/导弹等非舰船实体，直接检测圆形碰撞
                else {
                    Vector2f collision = getCollisionRayCircle(spawnLocation, endpoint,
                            entity.getLocation(), entity.getCollisionRadius());
                    if (collision != null) {
                        point = collision;
                        visualPoint = collision;
                        target = entity;
                        break;
                    }
                }
            }
        }

        // 第五步：碰撞点距离校验，防止超出射程，保证参数合法性
        Vector2f difference = Vector2f.sub(point, spawnLocation, new Vector2f());
        float length = difference.length();
        if (length >= weapon.getRange() + SLOP_RANGE) {
            difference.scale((weapon.getRange() + SLOP_RANGE) / length);
            Vector2f.add(spawnLocation, difference, point);
        }

        // 第六步：计算距离衰减系数，越远的电弧伤害/特效越弱，符合视觉和战斗逻辑
        float distance = MathUtils.getDistance(spawnLocation, point);
        if (distance < 0.1F) distance = 10.0F; // 避免距离为0导致衰减异常
        float atten = 1.0F; // 衰减系数，1=无衰减
        if (distance > weapon.getRange()) {
            atten = 1.0F - (distance - weapon.getRange()) / 400.0F; // 超射程后线性衰减
        }
        atten = Math.max(0, atten); // 防止衰减系数为负数

        // 第七步：生成电弧的光影特效（DarkShader），提升视觉质感
        float thickness = 30.0F * atten;
        float coreWidth = thickness * 0.65F;
        int brightness = (int) (90.0F * atten);
        StandardLight light = new StandardLight(spawnLocation, point, ZERO, ZERO, null);
        light.setIntensity(0.6F * atten);
        light.setSize(80.0F * atten);
        light.setColor(125, 0, 155);
        light.fadeOut(0.35F); // 0.35秒淡出，过渡自然
        LightShader.addLight(light);

        // 第八步：生成紫色电弧的视觉特效（游戏原生EMP电弧）
        EmpArcEntityAPI.EmpArcParams arcParams = new EmpArcEntityAPI.EmpArcParams();
        arcParams.zigZagReductionFactor = 0F; // 电弧平滑无锯齿
        arcParams.maxZigZagMult = 0.0F;
        arcParams.glowSizeMult = 1.5F; // 光晕放大
        arcParams.flickerRateMult = 0.75F; // 降低闪烁频率
        arcParams.segmentLengthMult = 4.0F; // 电弧段延长
        arcParams.fadeOutDist = 150.0F;
        arcParams.minFadeOutMult = 5.0F;

        EmpArcEntityAPI arc = engine.spawnEmpArcVisual(spawnLocation, ship, visualPoint, target, thickness,
                new Color(225, 100, 255, clamp255(brightness)), // 电弧边缘色：淡紫
                new Color(125, 0, 155, clamp255(brightness)),    // 电弧核心色：深紫
                arcParams);
        if (arc != null) {
            arc.setCoreWidthOverride(coreWidth);
            arc.setSingleFlickerMode();
            arc.setRenderGlowAtStart(true);
            arc.setWarping(0F);
        }

        // 第九步：电弧击中目标后的逻辑处理：粒子特效、伤害、音效、链式闪电
        if (target != null) {
            // 生成15个击中火花粒子，视觉反馈拉满
            for (int i = 0; i < 15; ++i) {
                Vector2f vel = new Vector2f(MathUtils.getRandomNumberInRange(300.0F, 800.0F), 0.0F);
                VectorUtils.rotate(vel, facing + 180.0F + MathUtils.getRandomNumberInRange(-90.0F, 90.0F), vel);
                Color sparkColor = new Color(MathUtils.getRandomNumberInRange(125, 225),
                        MathUtils.getRandomNumberInRange(75, 125), 165);
                engine.addHitParticle(point, vel, 10.0F, 1.0F, 0.25F, sparkColor);
            }

            // 计算衰减后的实际伤害和EMP，施加到目标身上
            float damage = INITIAL_DAMAGE * atten;
            float emp = INITIAL_EMP * atten;
            engine.applyDamage(null, target, point, damage, DamageType.ENERGY, emp,
                    false, false, ship, true);

            // 非舰船目标添加击中光晕特效
            if (!(target instanceof ShipAPI)) {
                float hitGlowSize = Misc.getHitGlowSize(100.0F, INITIAL_DAMAGE, DamageType.HIGH_EXPLOSIVE,
                        0.0F, 0.0F, damage, emp);
                engine.addHitParticle(point, target.getVelocity(), hitGlowSize, 1.0F, new Color(125, 0, 155, 90));
            }

            // 播放电弧击中音效，音量随衰减系数降低
            Global.getSoundPlayer().playSound(LIGHTNING_SOUND_ID, 1.0F, 0.55F * atten, visualPoint, ZERO);

            // 核心：触发链式闪电效果，电弧开始弹射！
            startChainLightning(engine, ship, target, point, damage, emp, weapon.getRange());
        }
    }

    // ===================== 【链式闪电核心逻辑 - 递归实现】 =====================

    /**
     * 启动链式闪电的初始弹射：从首次击中的目标开始，生成第一道链式电弧
     * @param engine 战斗引擎
     * @param source 发射方舰船
     * @param initialTarget 首次击中的目标
     * @param hitPoint 击中坐标
     * @param baseDamage 衰减后的基础伤害
     * @param baseEmp 衰减后的基础EMP
     * @param weaponRange 武器射程
     */
    private void startChainLightning(CombatEngineAPI engine, ShipAPI source, CombatEntityAPI initialTarget,
                                     Vector2f hitPoint, float baseDamage, float baseEmp, float weaponRange) {
        // 搜索击中点位周围的有效目标
        List<CombatEntityAPI> nearbyTargets = findChainTargets(hitPoint, source);
        // 过滤掉初始目标，避免电弧弹射回自身
        List<CombatEntityAPI> otherTargets = filterTargets(nearbyTargets, initialTarget);

        // 生成指定数量的初始链式电弧
        for (int i = 0; i < INITIAL_ARC_COUNT; i++) {
            CombatEntityAPI nextTarget = selectRandomTarget(otherTargets);
            if (nextTarget != null && nextTarget != initialTarget) {
                spawnChainArc(engine, source, hitPoint, nextTarget, baseDamage, baseEmp, weaponRange, 0);
                // 递归启动链式传导，开始下一轮弹射
                startChainReaction(engine, source, nextTarget, baseDamage, baseEmp, weaponRange, 1);
            }
        }
    }

    /**
     * 链式闪电的递归传导核心方法（核心递归逻辑）
     * 电弧从当前目标弹射到下一个目标，伤害递增，直到达到最大次数或最低伤害阈值
     * @param chainCount 当前传导次数，作为递归终止条件
     */
    private void startChainReaction(CombatEngineAPI engine, ShipAPI source, CombatEntityAPI currentTarget,
                                    float currentDamage, float currentEmp, float weaponRange, int chainCount) {
        // 递归终止条件1：达到最大传导次数，停止弹射
        if (chainCount >= MAX_CHAIN_COUNT) {
            return;
        }

        // 计算增强后的伤害和EMP，链式闪电越弹伤害越高
        float enhancedDamage = currentDamage * DAMAGE_ENHANCE_RATE;
        float enhancedEmp = currentEmp * DAMAGE_ENHANCE_RATE;

        // 递归终止条件2：伤害低于阈值，停止弹射（避免无限递归）
        if (enhancedDamage < MIN_DAMAGE_THRESHOLD) {
            return;
        }

        // 判断当前目标是否已被击毁，处理目标消失的情况
        if (engine.isEntityInPlay(currentTarget)) {
            if (currentTarget instanceof ShipAPI) {
                ((ShipAPI) currentTarget).isHulk();
            }
        }
        Vector2f chainStartLocation = currentTarget.getLocation();

        // 搜索当前目标周围的有效目标
        List<CombatEntityAPI> nearbyTargets = findChainTargets(chainStartLocation, source);
        List<CombatEntityAPI> availableTargets = filterTargets(nearbyTargets, currentTarget);

        // 有有效目标则继续弹射
        if (!availableTargets.isEmpty()) {
            CombatEntityAPI nextTarget = selectRandomTarget(availableTargets);
            if (nextTarget != null && nextTarget != currentTarget) {
                spawnChainArc(engine, source, chainStartLocation, nextTarget, enhancedDamage, enhancedEmp, weaponRange, chainCount);
                // 递归调用，传导次数+1
                startChainReaction(engine, source, nextTarget, enhancedDamage, enhancedEmp, weaponRange, chainCount + 1);
            }
        }
    }

    /**
     * 过滤目标列表：移除指定的排除目标
     */
    private List<CombatEntityAPI> filterTargets(List<CombatEntityAPI> targets, CombatEntityAPI excludeTarget) {
        List<CombatEntityAPI> filteredTargets = new ArrayList<>();
        for (CombatEntityAPI target : targets) {
            if (target != excludeTarget) {
                filteredTargets.add(target);
            }
        }
        return filteredTargets;
    }

    /**
     * 从目标列表中随机选择一个目标，让链式闪电的弹射更具随机性
     */
    private CombatEntityAPI selectRandomTarget(List<CombatEntityAPI> targets) {
        if (targets.isEmpty()) return null;
        int randomIndex = RANDOM.nextInt(targets.size());
        return targets.get(randomIndex);
    }

    /**
     * 生成链式闪电的电弧特效，并施加伤害
     */
    private void spawnChainArc(CombatEngineAPI engine, ShipAPI source, Vector2f start,
                               CombatEntityAPI target, float damage, float emp, float weaponRange, int chainCount) {
        EmpArcEntityAPI.EmpArcParams params = new EmpArcEntityAPI.EmpArcParams();
        params.zigZagReductionFactor = 0F;
        params.maxZigZagMult = 0.0F;
        params.glowSizeMult = 1.5F;
        params.flickerRateMult = 0.75F;
        params.segmentLengthMult = 4.0F;
        params.fadeOutDist = 150.0F;
        params.minFadeOutMult = 5.0F;

        // 链式电弧厚度随传导次数递减，视觉上有层次感
        float thickness = CHAIN_ARC_THICKNESS * (1f - (chainCount * 0.2f));
        if (thickness < 5f) thickness = 5f;

        // 生成链式电弧并施加伤害
        EmpArcEntityAPI arc = engine.spawnEmpArc(source, start, source, target,
                DamageType.KINETIC, damage, emp, weaponRange * 2f, LIGHTNING_SOUND_ID,
                thickness, new Color(225, 100, 255, 90),
                new Color(225, 100, 255, 150), params);
        if (arc != null) {
            arc.setCoreWidthOverride(thickness * 0.65f);
            arc.setSingleFlickerMode();
            arc.setRenderGlowAtStart(true);
            arc.setWarping(0F);
        }
    }

    /**
     * 搜索指定位置周围的所有有效链式闪电目标
     */
    private List<CombatEntityAPI> findChainTargets(Vector2f location, ShipAPI source) {
        List<CombatEntityAPI> nearbyEntities = CombatUtils.getEntitiesWithinRange(location, CHAIN_SEARCH_RANGE);
        List<CombatEntityAPI> validTargets = new ArrayList<>();
        for (CombatEntityAPI entity : nearbyEntities) {
            if (isValidChainTarget(entity, source)) {
                validTargets.add(entity);
            }
        }
        return validTargets;
    }

    /**
     * 判断一个实体是否是有效的链式闪电目标（核心过滤规则）
     */
    private boolean isValidChainTarget(CombatEntityAPI entity, ShipAPI source) {
        if (entity.getOwner() == source.getOwner()) return false; // 排除友方
        if (entity.getCollisionClass() == CollisionClass.NONE) return false; // 排除无碰撞体积的实体
        if (entity instanceof MissileAPI) return true; // 导弹是有效目标
        if (entity instanceof ShipAPI ship) {
            if (ship.isHulk()) return false; // 排除舰船残骸
            return entity.getCollisionClass() == CollisionClass.FIGHTER ||
                    entity.getCollisionClass() == CollisionClass.MISSILE_FF ||
                    entity.getCollisionClass() == CollisionClass.MISSILE_NO_FF ||
                    entity.getCollisionClass() == CollisionClass.SHIP;
        }
        return false;
    }

    // ===================== 【通用工具方法】- 纯工具，无业务逻辑 =====================

    /**
     * 高精度射线与圆形的碰撞检测算法
     * 用于检测电弧与护盾/导弹/战机的圆形碰撞，返回碰撞点坐标
     */
    private Vector2f getCollisionRayCircle(Vector2f start, Vector2f end, Vector2f circle, float radius) {
        float x1 = start.x - circle.x;
        float x2 = end.x - circle.x;
        float y1 = start.y - circle.y;
        float y2 = end.y - circle.y;

        float dx = x2 - x1;
        float dy = y2 - y1;
        float dr_sqrd = dx * dx + dy * dy;
        float D = x1 * y2 - x2 * y1;
        float delta = radius * radius * dr_sqrd - D * D;

        if (delta < 0.0F) return null; // 无碰撞
        else if (delta > 0.0F) { // 两个碰撞点
            float x_sub = Math.signum(dy) * dx * (float) Math.sqrt(delta);
            float x_a = (D * dy + x_sub) / dr_sqrd;
            float x_b = (D * dy - x_sub) / dr_sqrd;

            float y_sub = Math.abs(dy) * (float) Math.sqrt(delta);
            float y_a = (-D * dx + y_sub) / dr_sqrd;
            float y_b = (-D * dx - y_sub) / dr_sqrd;

            float dax = x_a - x1;
            float dbx = x_b - x1;
            float day = y_a - y1;
            float dby = y_b - y1;
            float dist_a_sqrt = dax * dax + day * day;
            float dist_b_sqrt = dbx * dbx + dby * dby;

            return dist_a_sqrt < dist_b_sqrt
                    ? new Vector2f(x_a + circle.x, y_a + circle.y)
                    : new Vector2f(x_b + circle.x, y_b + circle.y);
        } else { // 相切，一个碰撞点
            float x = D * dy / dr_sqrd;
            float y = -D * dx / dr_sqrd;
            return new Vector2f(x + circle.x, y + circle.y);
        }
    }

    /**
     * 颜色值钳位工具方法：确保颜色的Alpha值在0-255之间，避免出现无效颜色值导致特效异常
     */
    private int clamp255(int x) {
        return Math.max(0, Math.min(255, x));
    }
}