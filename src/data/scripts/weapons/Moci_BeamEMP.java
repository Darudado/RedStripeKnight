package data.scripts.weapons;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.EmpArcEntityAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/**
 * EMP离子光束武器效果
 */
public class Moci_BeamEMP implements BeamEffectPlugin {
    private final IntervalUtil fireInterval = new IntervalUtil(0.2F, 0.3F);
    IntervalUtil sparkInterval = new IntervalUtil(0.05f, 0.1f);   //电弧生成逻辑初始化
    public static final float maxSpread = 20;
    private static final Color ARC_COLOR1 = new Color(20, 102, 255, 255);
    private static final Color ARC_COLOR2 = new Color(115, 115, 255, 255);

    // ========== 效果参数配置 ==========
    
    /** 视觉效果电弧间隔控制 (0.2-0.25秒随机间隔) */
    private final IntervalUtil arcIntervalUtil = new IntervalUtil(0.2f, 0.25f);
    
    /** 光束亮度阈值 - 只有达到此亮度才触发EMP效果 */
    private static final float EMP_BRIGHTNESS_THRESHOLD = 0.9f;
    
    /** 视觉效果亮度阈值 - 达到此亮度开始生成装饰电弧 */
    private static final float VISUAL_BRIGHTNESS_THRESHOLD = 0.9f;
    
    /** EMP伤害倍数 - 相对于光束幅能伤害 */
    private static final float EMP_DAMAGE_MULT = 0.5f;
    
    /** 实体伤害倍数 - 相对于光束基础伤害 */
    private static final float PHYSICAL_DAMAGE_MULT = 0.25f;
    
    /** 扩散电弧伤害倍数 - 相对于武器基础伤害 */
    private static final float SPREAD_DAMAGE_MULT = 0.1f;
    
    /** 扩散电弧EMP倍数 - 相对于武器幅能伤害 */
    private static final float SPREAD_EMP_MULT = 0.25f;
    
    /** 护盾穿透基础阈值 - 硬辐能水平需超过此值才有穿透可能 */
    private static final float PIERCE_BASE_THRESHOLD = 0.5f;
    
    /** 扩散电弧范围 */
    private static final float SPREAD_RANGE = 250f;
    
    /** 扩散电弧数量 */
    private static final int SPREAD_ARC_COUNT = 5;
    
    // ========== 颜色配置 ==========
    
    /** 电弧边缘颜色 */
    private static final Color ARC_FRINGE_COLOR = new Color(20, 102, 255, 255);
    
    /** 电弧核心颜色 */
    private static final Color ARC_CORE_COLOR = new Color(211, 211, 255, 255);
    
    // ========== 状态跟踪变量 ==========
    
    /** 标记上一帧DPS持续时间是否为零 - 用于处理时间加速情况 */
    private boolean wasZero = true;
    
    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        // 基础检查：引擎暂停时不处理
        if (engine.isPaused()) return;
        CombatEntityAPI target = beam.getDamageTarget();


        if (target instanceof ShipAPI && beam.getBrightness() >= 1.0F) {
            float dur = beam.getDamage().getDpsDuration();
            if (!this.wasZero) {
                dur = 0.0F;
            }

            this.wasZero = beam.getDamage().getDpsDuration() <= 0.0F;
            this.fireInterval.advance(dur);
            if (this.fireInterval.intervalElapsed()) {
                ShipAPI ship = (ShipAPI)target;
                boolean hitShield = target.getShield() != null && target.getShield().isWithinArc(beam.getTo());
                float pierceChance = ((ShipAPI)target).getHardFluxLevel() - 0.1F;
                pierceChance *= ship.getMutableStats().getDynamic().getValue("shield_pierced_mult");
                boolean piercedShield = hitShield && (float)Math.random() < pierceChance;
                if (!hitShield || piercedShield) {
                    Vector2f point = beam.getRayEndPrevFrame();
                    float emp = beam.getDamage().getFluxComponent() * 0.5F;
                    float dam = beam.getDamage().getDamage() * 0.25F;
                    engine.spawnEmpArcPierceShields(beam.getSource(), point, beam.getDamageTarget(), beam.getDamageTarget(), DamageType.ENERGY, dam, emp, 100000.0F, "tachyon_lance_emp_impact", beam.getWidth() + 5.0F, beam.getFringeColor(), beam.getCoreColor());
                }
            }
        }





        sparkInterval.advance(amount);
        if (sparkInterval.intervalElapsed()) {   //准备生成电弧
            Vector2f start = beam.getFrom();
            Vector2f end = beam.getTo();
            //获取光束起始位置
            Vector2f p1 = MathUtils.getRandomPointOnLine(start, end);
            Vector2f p2 = MathUtils.getRandomPointOnLine(start, end);
            float dist1 = Misc.getDistance(p1, start);
            float dist2 = Misc.getDistance(p2, start);
            Vector2f closer = p1;
            Vector2f farer = p2;
            float farerDist = dist1;
            if (dist1 > dist2) {
                closer = p2;
                farer = p1;
                farerDist = dist2;               //确定光束起始位置
            }
            closer = MathUtils.getRandomPointOnCircumference(closer, beam.getWidth());
            farer = MathUtils.getPointOnCircumference(farer, beam.getWidth() * maxSpread * (farerDist / beam.getWeapon().getRange()), (float) ((Math.random() - 0.5f) * (Math.random() * 40 + 160) + beam.getWeapon().getCurrAngle()));
            //     farer = MathUtils.getRandomPointOnCircumference(farer, beam.getWidth() * maxSpread * (farerDist / beam.getWeapon().getRange()));
            engine.spawnEmpArcVisual(closer, beam.getSource(), farer, beam.getSource(), 5, ARC_COLOR1, ARC_COLOR2);
        }

        // ========== 主要EMP效果处理 ==========
        handleEMPEffects(engine, beam, target);
        // ========== 视觉效果处理 ==========
        handleVisualEffects(amount, beam);


    }
    
    /**
     * 处理主要的EMP效果逻辑
     * 包括护盾穿透判断和EMP电弧生成
     */
    private void handleEMPEffects(CombatEngineAPI engine, BeamAPI beam, CombatEntityAPI target) {
        // 只对舰船目标生效，且光束亮度需足够高
        if (!(target instanceof ShipAPI targetShip) || beam.getBrightness() < EMP_BRIGHTNESS_THRESHOLD) {
            return;
        }

        // 获取并处理DPS持续时间
        float duration = beam.getDamage().getDpsDuration();
        
        // 处理时间加速情况：当游戏处于快进时，dpsDuration可能不会每帧重置
        if (!wasZero) {
            duration = 0f; // 强制重置为0，确保计时正确
        }
        wasZero = beam.getDamage().getDpsDuration() <= 0;
        
        // 更新EMP触发计时器
        fireInterval.advance(duration);
        
        // 检查是否到达触发间隔
        if (!fireInterval.intervalElapsed()) {
            return;
        }
        
        // ========== 护盾穿透计算 ==========
        boolean hitShield = isHittingShield(targetShip, beam);
        boolean piercedShield = false;
        
        if (hitShield) {
            // 计算护盾穿透概率
            float pierceChance = calculateShieldPierceChance(targetShip);
            piercedShield = Math.random() < pierceChance;
        }
        
        // ========== EMP电弧生成 ==========
        if (!hitShield || piercedShield) {
            // 生成主要EMP电弧
            generateEMPArc(engine, beam);
            
            // 生成扩散电弧
            generateSpreadArcs(engine, beam);
        }
    }
    
    /**
     * 检查光束是否命中目标护盾
     */
    private boolean isHittingShield(ShipAPI target, BeamAPI beam) {
        return target.getShield() != null && target.getShield().isWithinArc(beam.getTo());
    }
    
    /**
     * 计算护盾穿透概率
     * 基于目标的硬辐能水平和护盾穿透加成
     */
    private float calculateShieldPierceChance(ShipAPI target) {
        // 基础穿透概率 = 硬辐能水平 - 基础阈值
        float baseChance = target.getHardFluxLevel() - PIERCE_BASE_THRESHOLD;
        
        // 应用舰船的护盾穿透倍数加成
        float pierceMultiplier = target.getMutableStats().getDynamic().getValue(Stats.SHIELD_PIERCED_MULT);
        
        return baseChance * pierceMultiplier;
    }
    
    /**
     * 生成EMP电弧效果
     * 对目标造成EMP和少量实体伤害
     */
    private void generateEMPArc(CombatEngineAPI engine, BeamAPI beam) {
        Vector2f impactPoint = beam.getRayEndPrevFrame();
        
        // 计算EMP伤害值（基于光束的幅能伤害分量）
        float empDamage = beam.getDamage().getFluxComponent() * EMP_DAMAGE_MULT;
        
        // 计算实体伤害值（基于光束的基础伤害）
        float physicalDamage = beam.getDamage().getDamage() * PHYSICAL_DAMAGE_MULT;
        
        // 生成EMP电弧
        engine.spawnEmpArc(
            beam.getSource(),        // 电弧来源（发射舰船）
            impactPoint,             // 电弧起始点
            beam.getDamageTarget(),  // 电弧目标实体
            beam.getDamageTarget(),  // 电弧视觉目标
            DamageType.ENERGY,       // 伤害类型
            physicalDamage,          // 实体伤害
            empDamage,               // EMP伤害
            100000f,                 // 最大射程
            "tachyon_lance_emp_impact", // 音效ID
            beam.getWidth() * 0.5f,    // 电弧宽度
            ARC_FRINGE_COLOR,        // 电弧边缘颜色
            ARC_CORE_COLOR           // 电弧核心颜色
        );
    }
    
    /**
     * 生成扩散电弧效果
     * 从命中点向周围目标扩散电弧
     */
    private void generateSpreadArcs(CombatEngineAPI engine, BeamAPI beam) {
        Vector2f impactPoint = beam.getRayEndPrevFrame();
        
        // 获取扩散范围内的所有有效目标
        List<CombatEntityAPI> validTargets = getValidSpreadTargets(impactPoint, beam);
        
        // 计算扩散电弧的伤害值
        float spreadDamage = beam.getDamage().getDamage() * SPREAD_DAMAGE_MULT;
        float spreadEMP = beam.getDamage().getFluxComponent() * SPREAD_EMP_MULT;
        
        // 生成指定数量的扩散电弧
        for (int i = 0; i < SPREAD_ARC_COUNT; i++) {
            CombatEntityAPI arcTarget;
            
            // 如果有有效目标，随机选择一个并移除避免重复攻击；否则创建随机点目标
            if (!validTargets.isEmpty()) {
                int randomIndex = MathUtils.getRandomNumberInRange(0, validTargets.size() - 1);
                arcTarget = validTargets.get(randomIndex);
                validTargets.remove(randomIndex); // 移除已选择的目标，避免重复攻击
            } else {
                // 创建随机点进行纯视觉效果
                Vector2f randomPoint = MathUtils.getRandomPointInCircle(impactPoint, SPREAD_RANGE);
                arcTarget = new SimpleEntity(randomPoint);
            }
            
            // 生成扩散电弧
            engine.spawnEmpArc(
                beam.getSource(),        // 电弧来源
                impactPoint,             // 电弧起始点（主命中点）
                beam.getSource(),        // 起始绑定实体
                arcTarget,               // 电弧目标
                DamageType.ENERGY,       // 伤害类型
                spreadDamage,            // 实体伤害
                spreadEMP,               // EMP伤害
                100000f,                 // 最大射程
                "tachyon_lance_emp_impact", // 音效ID
                beam.getWidth() * 0.15f,                      // 电弧宽度
                ARC_FRINGE_COLOR,        // 电弧边缘颜色
                ARC_CORE_COLOR           // 电弧核心颜色
            );
        }
    }
    
    /**
     * 获取扩散范围内的有效目标
     * 只攻击敌方目标，避免友伤
     * 使用Grid遍历优化性能
     */
    private List<CombatEntityAPI> getValidSpreadTargets(Vector2f centerPoint, BeamAPI beam) {
        List<CombatEntityAPI> validTargets = new ArrayList<>();
        
        // 使用Grid遍历优化：获取范围内的所有对象
        CombatEngineAPI engine = Global.getCombatEngine();
        Iterator<Object> objectIter = engine.getAllObjectGrid().getCheckIterator(
            centerPoint,        // 检查中心点
            SPREAD_RANGE * 2f,  // 检查宽度
            SPREAD_RANGE * 2f   // 检查高度
        );
        
        // 遍历Grid返回的对象
        while (objectIter.hasNext()) {
            Object obj = objectIter.next();
            
            // 类型筛选：只考虑CombatEntityAPI
            if (!(obj instanceof CombatEntityAPI entity)) {
                continue;
            }

            // 只考虑舰船和导弹
            if (!(entity instanceof ShipAPI || entity instanceof MissileAPI)) {
                continue;
            }
            
            // 跳过友军，只攻击敌方目标
            if (entity.getOwner() == beam.getSource().getOwner()) {
                continue;
            }
            
            // 排除无碰撞的实体
            if (entity.getCollisionClass().equals(CollisionClass.NONE)) {
                continue;
            }
            
            // 精确距离检查：Grid遍历返回的是矩形区域，我们需要圆形范围
            float distance = MathUtils.getDistance(centerPoint, entity.getLocation());
            if (distance > SPREAD_RANGE) {
                continue;
            }
            
            validTargets.add(entity);
        }
        
        return validTargets;
    }
    
    /**
     * 处理视觉效果
     * 在光束路径上生成装饰性电弧
     */
    private void handleVisualEffects(float amount, BeamAPI beam) {
        // 只在光束亮度足够时生成视觉效果
        if (beam.getBrightness() <= VISUAL_BRIGHTNESS_THRESHOLD) {
            return;
        }
        
        arcIntervalUtil.advance(amount);
        
        if (arcIntervalUtil.intervalElapsed()) {
            createVisualArc(beam, beam.getWidth());
        }
    }
    
    /**
     * 创建视觉装饰电弧效果
     * 生成2条从发射点到目标点的完整电弧，增强视觉效果
     * 
     * @param beam 光束实例
     * @param arcWidth 电弧宽度参考值
     */
    private void createVisualArc(BeamAPI beam, float arcWidth) {
        Random random = new Random();
        
        // 生成2条电弧，从发射点直接到目标点
        for (int i = 0; i < 2; i++) {
            // 为每条电弧添加随机偏移，让它们看起来更自然
            Vector2f startPoint = Misc.getPointWithinRadiusUniform(
                beam.getFrom(),
                0f,                         // 最小偏移
                0f,            // 最大偏移
                random
            );
            
            Vector2f endPoint = Misc.getPointWithinRadiusUniform(
                beam.getRayEndPrevFrame(),
                0f,                         // 最小偏移
                0f,            // 最大偏移
                random
            );
            
            // 生成视觉电弧
            EmpArcEntityAPI visualArc = Global.getCombatEngine().spawnEmpArcVisual(
                startPoint,                   // 起始点
                beam.getSource(),             // 起始实体
                endPoint,                     // 结束点
                beam.getSource(),             // 结束实体
                // 电弧宽度：基础宽度 + 随机变化
                beam.getWidth() * 0.4f + (beam.getWidth() * 0.2f * random.nextFloat()),
                ARC_FRINGE_COLOR,             // 边缘颜色
                ARC_CORE_COLOR                // 核心颜色
            );
            
            // 设置单次闪烁模式，让电弧看起来更像放电效果
            visualArc.setSingleFlickerMode();
        }
    }
} 