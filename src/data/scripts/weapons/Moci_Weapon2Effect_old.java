package data.scripts.weapons;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.DamagingExplosionSpec;
import com.fs.starfarer.api.util.Misc;

import static data.scripts.weapons.Moci_MechProjDecoWeaponEffect.syncFirePoint;

/**
 * 打桩机
 */
// 继承自Moci_MechProjDecoWeaponEffect
public class Moci_Weapon2Effect_old implements EveryFrameWeaponEffectPlugin {
    private boolean init = false;
    private static final float EXPLODE_DELAY = 2; //桩爆炸延迟
    WeaponAPI source;
    private static final String OTHER_WEAPON_ID = "Moci_Weapon1"; // 互斥武器ID
    
    // 静态标志，指示是否在拳击动作中，供其他动画组件检查
    public static boolean isPunching = false;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused()) {
            return;
        }

        // 特殊手臂的独立逻辑
        if (!init) {
            init = true;
            weapon.getShip().addListener(new Moci_EffectScript(weapon));
            ShipAPI ship = weapon.getShip();
            String slotId = weapon.getSlot().getId();
            //通过武器槽添加后缀寻找关联武器
            String sourceId = slotId + "_SOURCE";
            for (WeaponAPI w : ship.getAllWeapons()) {
                if (sourceId.equals(w.getSlot().getId())) {
                    source = w;
                    break;
                }
            }
            //关联武器槽安装了武器的情况下
            if (source != null) {
                //计算装饰武器和真武器之间的坐标差
                Vector2f diff = new Vector2f();
                Vector2f.sub(weapon.getSlot().getLocation(), source.getSlot().getLocation(), diff);
                //取装饰武器的炮塔模式第一个开火点坐标
                Vector2f firePoint = weapon.getSpec().getTurretFireOffsets().get(0);
                syncFirePoint(source, diff, firePoint);
                Color color = new Color(0, 0, 0, 0);
                if (source.getSprite() != null) {
                    source.getSprite().setColor(color);
                }
                if (source.getBarrelSpriteAPI() != null) {
                    source.getBarrelSpriteAPI().setColor(color);
                }
                if (source.getUnderSpriteAPI() != null) {
                    source.getUnderSpriteAPI().setColor(color);
                }
                if (source.getGlowSpriteAPI() != null) {
                    source.getGlowSpriteAPI().setColor(color);
                }
            }
        }
        if (source != null) {
            weapon.setCurrAngle(source.getCurrAngle());
        }
    }

    public static class Moci_EffectScript implements AdvanceableListener {
        final ShipAPI ship;
        final WeaponAPI weapon;
        CollisionClass collision;
        int chargeUP = 6; //抬手蓄力帧
        int fire = 10; //发射帧
        float timer = 0;
        boolean fired = false;
        ShipAPI target = null;
        private static final float ENGINE_GLOW_MULT = 2f; // 引擎光效倍率
        private Moci_MechSystemFollowerAnimationWeapon wingPlugin = null;
        private punchAnimController controller = null; // 动画控制器

        private Moci_EffectScript(WeaponAPI weapon) {
            this.ship = weapon.getShip();
            this.weapon = weapon;
            this.collision = ship.getCollisionClass();
            for(WeaponAPI w : ship.getAllWeapons()) {
                if (w.getSlot().getId().equals("WING_R")) {
                    this.wingPlugin = (Moci_MechSystemFollowerAnimationWeapon) w.getEffectPlugin();
                }
            }

            // // 初始化动画控制器
             Set<Object[]> weapons = new HashSet<>();
             WeaponAPI body = null;
             for (WeaponAPI w : ship.getAllWeapons()) {
                 if (w.getSlot().getId().equals("BODY")) {
                     body = w;
                 }
                 // 检查是否是需要联动的武器槽
                 if (punchAnimController.LINKED_WEAPON_SLOTS.contains(w.getSlot().getId())) {
                     if (w.getSprite() == null) {
                         continue;
                     }
                 float cx = w.getSprite().getCenterX();
                 float cy = w.getSprite().getCenterY();
                 Object[] obj = new Object[]{w, cx, cy};
                 weapons.add(obj);
                 }
                 controller = new punchAnimController(weapons, body, ship);
                 ship.addListener(controller);
             }
        }

        // 修改IDLE状态
        protected State state = State.IDLE;

        enum State {
            IDLE(0),
            CHARGE_UP(5), //抬手冲刺时间
            PUNCH(0),
            DE_CHARGE(0);

            private final float duration;

            State(float duration) {
                this.duration = duration;
            }
        }

        /**
         * 外部调用，激活拳击效果
         */
        public void setFire() {
            if (state == State.IDLE) {
                state = State.CHARGE_UP;
                timer = 0;
                fired = false;
                target = null;
                if(wingPlugin!=null){
                    wingPlugin.setExtraActive(true);
                }
            }
        }

        @Override
        public void advance(float amount) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) {
                return;
            }

            if (weapon.getAnimation() != null) {
                weapon.getAnimation().pause();
            }
            if (state != State.IDLE) {
                // 禁用互斥武器
                for (WeaponAPI w : weapon.getShip().getAllWeapons()) {
                    if (w.getId().equals(OTHER_WEAPON_ID)) {
                        w.setForceNoFireOneFrame(true);
                    }
                }
            }

            if (state == State.CHARGE_UP) {
                // 改变碰撞类型为战机
                if (collision == null) {
                    collision = ship.getCollisionClass();
                    ship.setCollisionClass(CollisionClass.FIGHTER);
                }

                //持续冲刺
                ship.blockCommandForOneFrame(ShipCommand.DECELERATE);
                ship.blockCommandForOneFrame(ShipCommand.ACCELERATE_BACKWARDS);
                timer += amount;

                // 增强引擎视觉效果
                timer += amount;

                //抬手动画
                if (weapon.getAnimation().getFrame() < chargeUP) {
                    weapon.getAnimation().play();
                    ship.getMutableStats().getMaxSpeed().modifyFlat("Moci_Weapon2", 300);
                    ship.getMutableStats().getAcceleration().modifyFlat("Moci_Weapon2", 10000);
                    ship.giveCommand(ShipCommand.ACCELERATE, null, 0);
                    ship.getEngineController().extendFlame(
                            this,
                            ENGINE_GLOW_MULT,  // 长度倍率
                            ENGINE_GLOW_MULT, // 宽度倍率
                            ENGINE_GLOW_MULT // 亮度倍率
                    );
                    
                    // 在抬手阶段也检测碰撞，允许提前触发打桩效果
                    ShipAPI target = AIUtils.getNearestEnemy(ship);
                    if (target != null) {
                        if (CollisionUtils.isPointWithinBounds(weapon.getFirePoint(0), target)) {
                            // 直接转到PUNCH状态，并设置target
                            this.target = target;
                            
                            // 设置帧到fire帧，确保打桩动作正确触发
                            weapon.getAnimation().setFrame(fire);
                            
                            // 清除加速效果
                            ship.getMutableStats().getMaxSpeed().unmodify("Moci_Weapon2");
                            ship.getMutableStats().getAcceleration().unmodify("Moci_Weapon2");
                            // 直接设置速度为默认最大速度
                            float defaultMaxSpeed = ship.getMaxSpeed();
                            if (ship.getVelocity().length() > defaultMaxSpeed) {
                                Vector2f velocity = ship.getVelocity();
                                velocity.normalise();
                                velocity.scale(defaultMaxSpeed);
                                ship.getVelocity().set(velocity);
                            }
                            if (collision != null) {
                                ship.setCollisionClass(collision);
                                collision = null;
                            }
                            state = State.PUNCH;
                            
                            // 立即执行打桩效果
                            executePunchEffect(engine, target);
                        }
                    }
                } else {
                    ShipAPI target = AIUtils.getNearestEnemy(ship);
                    if (target != null) {
                        if (CollisionUtils.isPointWithinBounds(weapon.getFirePoint(0), target)) {
                            ship.getMutableStats().getMaxSpeed().unmodify("Moci_Weapon2");
                            ship.getMutableStats().getAcceleration().unmodify("Moci_Weapon2");
                            // 直接设置速度为默认最大速度
                            float defaultMaxSpeed = ship.getMaxSpeed();
                            if (ship.getVelocity().length() > defaultMaxSpeed) {
                                Vector2f velocity = ship.getVelocity();
                                velocity.normalise();
                                velocity.scale(defaultMaxSpeed);
                                ship.getVelocity().set(velocity);
                            }
                            if (collision != null) {
                                ship.setCollisionClass(collision);
                                collision = null;
                            }
                            state = State.PUNCH;
                            this.target = target;
                        }
                    }
                    float ang = ship.getFacing();
                    float v_ang = VectorUtils.getFacing(ship.getVelocity());
                    float a = normalizeAngle(ang - v_ang);
                }
                if (timer > State.CHARGE_UP.duration) {
                    ship.getMutableStats().getMaxSpeed().unmodify("Moci_Weapon2");
                    ship.getMutableStats().getAcceleration().unmodify("Moci_Weapon2");
                    // 直接设置速度为默认最大速度
                    float defaultMaxSpeed = ship.getMaxSpeed();
                    if (ship.getVelocity().length() > defaultMaxSpeed) {
                        Vector2f velocity = ship.getVelocity();
                        velocity.normalise();
                        velocity.scale(defaultMaxSpeed);
                        ship.getVelocity().set(velocity);
                    }
                    if (collision != null) {
                        ship.setCollisionClass(collision);
                        collision = null;
                    }
                    state = State.DE_CHARGE;
                    timer = 0;
                    if(wingPlugin!=null){
                        wingPlugin.setExtraActive(false);
                    }
                }
            }
            //反向播放收回手臂
            if (state == State.DE_CHARGE) {
                int current = weapon.getAnimation().getFrame();
                if (current == 0) {
                    state = State.IDLE;
                } else {
                    timer += amount * weapon.getAnimation().getFrameRate();
                    while (timer >= 1) {
                        timer -= 1;
                        current -= 1;
                        if (current == 0) {
                            break;
                        }
                    }
                    weapon.getAnimation().setFrame(current);
                }

                // 取消速度修正
                ship.getMutableStats().getMaxSpeed().unmodifyFlat("Moci_Weapon2");
                ship.getMutableStats().getAcceleration().unmodify("Moci_Weapon2");
                // 直接设置速度为默认最大速度
                float defaultMaxSpeed = ship.getMaxSpeed();
                if (ship.getVelocity().length() > defaultMaxSpeed) {
                    Vector2f velocity = ship.getVelocity();
                    velocity.normalise();
                    velocity.scale(defaultMaxSpeed);
                    ship.getVelocity().set(velocity);
                }
                if (collision != null) {
                    ship.setCollisionClass(collision);
                    collision = null;
                }
            }
            if (state == State.PUNCH) {
                weapon.getAnimation().play();
                int current = weapon.getAnimation().getFrame();
                if (current == fire && !fired) {
                    fired = true;
                    executePunchEffect(engine, target);
                }

                if (current == weapon.getAnimation().getNumFrames() - 1 || current < chargeUP) {
                    weapon.getAnimation().setFrame(0);
                    state = State.IDLE;
                    if(wingPlugin!=null){
                        wingPlugin.setExtraActive(false);
                    }
                }
            }
        }
        
        // 抽取打桩效果到单独的方法
        private void executePunchEffect(CombatEngineAPI engine, ShipAPI target) {
            if (target == null) return;
            
            // 计算发射角度和位置
            float angle = 180 + weapon.getCurrAngle();
            Vector2f point2 = MathUtils.getPoint(weapon.getFirePoint(0), 45, angle);

            // 触发动画控制器
            if (controller != null) {
                controller.setTarget(-30, 0.1f, 0.1f);
            }

            //打桩
            Vector2f point = weapon.getFirePoint(0);
            //释放3000动能伤害
            engine.applyDamage(target, point, 3000, DamageType.KINETIC, 1000, true, false, ship);
            //插桩
            Global.getCombatEngine().addLayeredRenderingPlugin(new anchorEffect(target, point, weapon.getCurrAngle(), ship, weapon));
            //可选 刷点粒子之类的效果
            Global.getSoundPlayer().playSound("Moci_Weapon2Sound", 1f, 1f, point, new Vector2f());
            // 生成烟雾粒子效果
            for(int i=0; i<20; i++) {
                Vector2f vel = MathUtils.getRandomPointInCone(new Vector2f(), 100, angle-20, angle+20);
                vel.scale((float)Math.random());
                Vector2f.add(vel, ship.getVelocity(), vel);
                float grey = MathUtils.getRandomNumberInRange(0.5f, 0.75f);
                engine.addSmokeParticle(
                        MathUtils.getRandomPointInCircle(point, 5),
                        vel,
                        MathUtils.getRandomNumberInRange(5, 20),
                        MathUtils.getRandomNumberInRange(0.25f, 0.75f),
                        MathUtils.getRandomNumberInRange(0.25f, 1f),
                        new Color(grey,grey,grey,MathUtils.getRandomNumberInRange(0.25f, 0.75f))
                );
            }

            // 生成碎片粒子效果
            for(int i=0; i<10; i++) {
                Vector2f vel = MathUtils.getRandomPointInCone(new Vector2f(), 250, angle-20, angle+20);
                Vector2f.add(vel, ship.getVelocity(), vel);
                engine.addHitParticle(
                        point,
                        vel,
                        MathUtils.getRandomNumberInRange(2, 4),
                        1,
                        MathUtils.getRandomNumberInRange(0.05f, 0.25f),
                        new Color(255,125,50)
                );
            }

            // 生成闪光粒子效果
            Vector2f vel = MathUtils.getRandomPointInCone(new Vector2f(), 100, angle-20, angle+20);
            vel.scale((float)Math.random());
            Vector2f.add(vel, ship.getVelocity(), vel);
            engine.addHitParticle(
                    point,
                    vel,
                    100,
                    0.5f,
                    0.5f,
                    new Color(255,20,10)
            );
            engine.addHitParticle(
                    point,
                    vel,
                    80,
                    0.75f,
                    0.15f,
                    new Color(255,200,50)
            );
            engine.addHitParticle(
                    point,
                    vel,
                    50,
                    1,
                    0.05f,
                    new Color(255, 234, 212)
            );
        }
    }

    private static class anchorEffect extends BaseCombatLayeredRenderingPlugin {
        final ShipAPI ship;
        final Vector2f offset;
        final SpriteAPI sprite;
        final float facing;
        Vector2f location;
        final ShipAPI source;
        final WeaponAPI weapon;
        private int explosionCount = 0; // 记录已执行的爆炸次数
        private float nextExplosionTime = 0f; // 下一次爆炸的时间
        private float lastBeepTime = 0f; // 上次播放音效的时间
        private static final float BEEP_INTERVAL = 1f; // 音效播放间隔

        private anchorEffect(ShipAPI ship, Vector2f location, float facing, ShipAPI source, WeaponAPI weapon) {
            this.ship = ship;
            this.source = source;
            this.weapon = weapon;
            this.offset = LocationToOffSet(ship, location);
            this.sprite = Global.getSettings().getSprite("graphics/missiles/Moci_pile.png");
            this.facing = normalizeAngle(facing - ship.getFacing());
            this.location = new Vector2f(location);
            this.layer = CombatEngineLayers.ABOVE_SHIPS_LAYER;
            this.nextExplosionTime = EXPLODE_DELAY; // 第一次爆炸在延迟后执行
        }

        @Override
        public float getRenderRadius() {
            return 30; //比桩子的图大一点就行 自己调整
        }

        float timer = 0;

        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            if (layer == this.layer) {
                sprite.setAngle(facing + ship.getFacing() - 90);
                // 在倒计时期间添加红色闪烁效果
                if (timer < EXPLODE_DELAY) {
                    float flashIntensity = (float) Math.sin(timer * 10f) * 0.5f + 0.5f; // 0到1之间的闪烁
                    sprite.setColor(new Color(1f, 1f - flashIntensity, 1f - flashIntensity, 1f));
                } else {
                    sprite.setColor(Color.WHITE);
                }
                sprite.renderAtCenter(location.x, location.y);
            }
        }

        @Override
        public void advance(float amount) {
            if (isExpired) {
                return;
            }
            //更新坐标
            this.location.set(OffSetToLocation(ship, offset));
            this.entity.getLocation().set(location);
            timer += amount;

            // 在倒计时期间播放音效
            if (timer < EXPLODE_DELAY) {
                if (timer - lastBeepTime >= BEEP_INTERVAL) {
                    Global.getSoundPlayer().playSound("Moci_Beep", 1f, 0.7f, location, new Vector2f());
                    lastBeepTime = timer;
                }
            }

            // 执行连续爆炸
            if (timer >= nextExplosionTime && explosionCount < 6) {
                float f = normalizeAngle(facing + ship.getFacing());
                
                // 创建爆炸效果
                DamagingExplosionSpec spec = new DamagingExplosionSpec(
                        0.1f,  //爆炸持续时间
                        75, //伤害半径
                        50, //核心伤害半径
                        4000/6, //最大伤害（原来的1/6）
                        2000/6, //最小伤害（原来的1/6）
                        CollisionClass.MISSILE_FF,//碰撞类型
                        CollisionClass.MISSILE_FF,//对战机碰撞类型
                        3,//粒子尺寸
                        10, //粒子尺寸随机数
                        0.12f, //粒子持续时间
                        25,   //粒子数量
                        new Color(255, 79, 15, 255), //粒子颜色
                        new Color(255, 227, 185, 100) //爆炸颜色
                );
                Global.getCombatEngine().spawnDamagingExplosion(spec, source, location);

                // // 生成闪光效果
                // if(MagicRender.screenCheck(0.2f, location)) {
                //     Global.getCombatEngine().addSmoothParticle(
                //             location,
                //             new Vector2f(),
                //             50,
                //             0.12f,
                //             0.12f,
                //             Color.WHITE
                //     );
                // }

                // 施加推力（原来的1/6）
                float force = 200/6f;
                forceRotate(1, f, force, null, location, ship, true, 10);

                explosionCount++;
                nextExplosionTime += 0.25f; // 设置下一次爆炸的时间

                // 如果已经执行了6次爆炸，标记为过期
                if (explosionCount >= 6) {
                    isExpired = true;
                }
            }
        }

        private boolean isExpired = false;

        @Override
        public boolean isExpired() {
            return isExpired;
        }
    }

    public static Vector2f OffSetToLocation(CombatEntityAPI entity, Vector2f offset) {
        Vector2f loc = new Vector2f(offset);
        VectorUtils.rotate(loc, entity.getFacing(), loc);
        Vector2f.add(loc, entity.getLocation(), loc);
        return loc;
    }

    public static Vector2f LocationToOffSet(CombatEntityAPI entity, Vector2f loc) {
        Vector2f offset = new Vector2f(loc);
        Vector2f.sub(offset, entity.getLocation(), offset);
        VectorUtils.rotate(offset, -entity.getFacing(), offset);
        return offset;
    }

    public static float normalizeAngle(float ang) {
        //       ang%=360;
        while ((ang > 180f || ang < -180f)) {
            if (ang > 180f) {
                ang -= 360f;
            }
            if (ang < -180f) {
                ang += 360f;
            }
        }
        return ang;
    }

    /**
     * 施加外力
     *
     * @param amount      如果是持续性的施加力，输入每帧的持续时间来减小力的单帧施加效果
     * @param facing      力的朝向
     * @param force       力的大小
     * @param Dirvel      与力同向的长度为1的单位向量
     * @param loc         施力点
     * @param targetShip  施力对象
     * @param effectSpeed 当为true的时候，不仅让对象转动，还将部分动能直接用于改变目标的速度
     * @param rotateSpeed 1倍重量的力施加在半径切线上导致的旋转速度 参考值90
     */
    public static void forceRotate(float amount, float facing, float force, Vector2f Dirvel, Vector2f loc, ShipAPI targetShip, boolean effectSpeed, float rotateSpeed) {
        ShipAPI ship_ = targetShip;
        float r = ship_.getCollisionRadius();
        if (targetShip.getParentStation() != null) {
            ship_ = targetShip.getParentStation();
            r += Misc.getDistance(targetShip.getLocation(), ship_.getLocation());
        }

        // 根据目标质量计算力衰减
        float mass = ship_.getMass();
        float massFactor = 1f;
        if (mass > 1000f) {
            // 质量在1000-3000之间线性衰减
            massFactor = Math.max(0f, 1f - (mass - 1000f) / 3000f);
            force *= massFactor;
        }

        float f = VectorUtils.getAngle(ship_.getLocation(), loc);
        float d = Misc.getDistance(ship_.getLocation(), loc);
        //  r *= 1.2f;//为了减少对旋转的影响，半径被放大一些
        if (d > r) {
            d = r;
        }
        float dr = d / r; //施力点占半径的比例

        float speed_ang = normalizeAngle(facing - f); //速度方向与半径线的夹角
        float Rotate_percent = Math.abs(speed_ang / 90); //能量转化为旋转的比例
        if (Rotate_percent > 1) {
            Rotate_percent = 2 - Rotate_percent;
        }
        //   Rotate_percent *= Rotate_percent;
        Rotate_percent *= dr; //根据力臂就减少转向的实际施加量
        float forcePercent = 1 - Rotate_percent;
        if (speed_ang < 0) {
            Rotate_percent = -Rotate_percent;
        }
        Rotate_percent *= force / ship_.getMass();
        //设置1倍重量的力作用于碰撞半径切线上会导致4秒/圈的转圈
        float rotate = Rotate_percent * rotateSpeed;
        if (Math.abs(ship_.getAngularVelocity()) < Math.abs(rotate)) {
            ship_.setAngularVelocity(rotate * amount + ship_.getAngularVelocity());
        }
        if (effectSpeed) {
            if (Dirvel == null) {
                Dirvel = new Vector2f(1, 0);
                VectorUtils.rotate(Dirvel, facing);
            }
            Dirvel.scale(forcePercent * force / ship_.getMass());
            forceSpeed(Dirvel, ship_);
        }
    }

    /**
     * 向量叠加
     *
     * @param vel      叠加的速度
     * @param launcher 飞船
     */
    public static void forceSpeed(Vector2f vel, ShipAPI launcher) {
        Vector2f shipV = launcher.getVelocity();
        Vector2f.add(shipV, vel, shipV);
    }

    // 新增的获取效果脚本实例的方法
    public static Moci_EffectScript getEffectScriptInstance(ShipAPI ship) {
        if (ship == null) return null;
        if (ship.hasListenerOfClass(Moci_EffectScript.class)) {
            return ship.getListeners(Moci_EffectScript.class).get(0);
        }
        return null;
    }

    /**
     * 动画控制器类
     * 用于控制多个武器的联动动画，包括身体旋转和武器位置调整
     */
    private static class punchAnimController implements AdvanceableListener {
        final Set<Object[]> weapons;  // 需要联动的武器集合，每个元素包含[武器实例, 原始中心点X, 原始中心点Y]
        final WeaponAPI body;         // 身体部分，作为旋转的中心点
        final ShipAPI ship;           // 所属舰船

        // 需要联动的武器槽位ID列表
        private static final Set<String> LINKED_WEAPON_SLOTS = new HashSet<>(Arrays.asList(
            "HEAD",    // 头部
            "BODY",    // 躯干
            "WING_R",    // 右翼
            "SHOULDER_R", // 右肩
            "ARM_L",     // 左臂
            "SHOULDER_L", // 左肩
            "WING_L",    // 左翼
            "WING_B"     // 背部翼
        ));
        
        // 通过角度旋转的部位
        private static final Set<String> ROTATING_PARTS = new HashSet<>(Arrays.asList(
            "BODY",     // 躯干
            "WING_B"    // 背部翼
        ));
        
        // 左侧部件
        private static final Set<String> LEFT_PARTS = new HashSet<>(Arrays.asList(
            "ARM_L",     // 左臂
            "SHOULDER_L", // 左肩
            "WING_L"     // 左翼
        ));
        
        // 右侧部件
        private static final Set<String> RIGHT_PARTS = new HashSet<>(Arrays.asList(
            "SHOULDER_R", // 右肩
            "WING_R"      // 右翼
        ));
        
        // 需要特殊处理的头部（既旋转又偏移中心点）
        private static final String HEAD_SLOT = "HEAD";
        
        // 原始船体尺寸
        private float originalShipSpriteWidth = 0f;
        private float originalShipSpriteHeight = 0f;
        private boolean hasStoredOriginalSize = false;

        private punchAnimController(Set<Object[]> weapons, WeaponAPI body, ShipAPI ship) {
            this.weapons = weapons;
            this.body = body;
            this.ship = ship;
        }

        float punchTargetAngle = 0;
        float currentAngle = 0;
        float timer = 0;
        float actionTime = 0;
        float waitWhenArrive = 0;
        private STATE state = STATE.IDLE;

        enum STATE {
            IDLE,
            PUNCH,
            WAIT,
            BACK
        }

        public void setTarget(float angle, float actionTime, float waitWhenArrive) {
            if (state != STATE.IDLE) { //一拳打完之前不准打第二拳
                return;
            }
            punchTargetAngle = angle;
            this.actionTime = actionTime;
            this.waitWhenArrive = waitWhenArrive;
            this.state = STATE.PUNCH;
            timer = 0;
            
            // 设置静态标志
            Moci_Weapon2Effect_old.isPunching = true;
            
            // 存储并隐藏船体贴图
            if (!hasStoredOriginalSize && ship.getSpriteAPI() != null) {
                originalShipSpriteWidth = ship.getSpriteAPI().getWidth();
                originalShipSpriteHeight = ship.getSpriteAPI().getHeight();
                hasStoredOriginalSize = true;
                ship.getSpriteAPI().setSize(0f, 0f);
            }
        }

        @Override
        public void advance(float amount) {
            if (state == STATE.PUNCH) {
                timer += amount;
                float progress = Math.min(1, timer / actionTime);
                // 注意这里取反了角度，使旋转方向正确
                currentAngle = Misc.interpolate(0, -punchTargetAngle, progress);
                if (progress == 1) {
                    state = STATE.WAIT;
                    timer = 0;
                }
            } else if (state == STATE.WAIT) {
                timer += amount;
                if (timer >= waitWhenArrive) {
                    state = STATE.BACK;
                    timer = 0;
                }
            } else if (state == STATE.BACK) { //这里返程用的是和出拳一样的比例计算方式 也可以换成以固定速率回正到0
                timer += amount;
                float progress = Math.min(1, timer / actionTime);
                // 注意这里取反了角度，使旋转方向正确
                currentAngle = Misc.interpolate(-punchTargetAngle, 0, progress);
                if (progress == 1) {
                    state = STATE.IDLE;
                    timer = 0;
                    
                    // 重置静态标志
                    Moci_Weapon2Effect_old.isPunching = false;
                    
                    // 还原船体贴图尺寸
                    if (hasStoredOriginalSize && ship.getSpriteAPI() != null) {
                        ship.getSpriteAPI().setSize(originalShipSpriteWidth, originalShipSpriteHeight);
                    }
                }
            }

            Vector2f center = new Vector2f();
            if (body != null) {
                center.set(body.getSlot().getLocation());
            }

            for (Object[] obj : weapons) {
                WeaponAPI weapon = (WeaponAPI) obj[0];
                String slotId = weapon.getSlot().getId();
                
                // 对于身体和后翼，直接设置旋转角度
                if (ROTATING_PARTS.contains(slotId)) {
                    weapon.setCurrAngle(currentAngle + ship.getFacing());
                    continue;
                }
                
                // 头部不作特殊处理，让它回到原始状态
                if (HEAD_SLOT.equals(slotId)) {
                    continue;
                }
                
                // 获取武器槽位置
                Vector2f offset = new Vector2f(weapon.getSlot().getLocation());
                // 获取武器槽相对于body/飞船中心的位置
                Vector2f rotate = Vector2f.sub(offset, center, null);
                // 计算旋转角度 - 左右两侧使用相反的角度
                float angleToUse = currentAngle;
                if (LEFT_PARTS.contains(slotId)) {
                    angleToUse = -currentAngle; // 左侧部件旋转方向相反
                } else if (RIGHT_PARTS.contains(slotId)) {
                    angleToUse = -currentAngle; // 右侧部件旋转方向相反
                }
                // 计算旋转
                VectorUtils.rotate(rotate, angleToUse);
                // 叠加body
                Vector2f.add(rotate, center, rotate);
                // 计算偏移量
                Vector2f.sub(rotate, offset, rotate);
                float cx = (float) obj[1] + rotate.y;
                float cy = (float) obj[2] + rotate.x;
                weapon.getSprite().setCenterX(cx);
                weapon.getSprite().setCenterY(cy);
            }
        }
    }
}
