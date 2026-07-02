package data.hullmods.Tr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.BoundsAPI;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.FluxTrackerAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.*;

import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class Tr_SpecializedArmor extends BaseHullMod {
    // 静态常量映射（所有实例共享，只读）
    private static final Map<WeaponAPI.WeaponSize, String> PROJECTILE_SOUNDS = new HashMap<>();
    private static final Map<WeaponAPI.WeaponSize, String> BEAM_SOUNDS = new HashMap<>();
    private static final Map<WeaponAPI.WeaponSize, Vector2f> PUFF_SIZE = new HashMap<>();
    private static final Map<WeaponAPI.WeaponSize, Vector2f> SPARK_SIZE = new HashMap<>();

    // 静态常量数组，用于粒子特效（避免每帧创建）
    private static final float[] DENOMINATOR = {75.0F, 100.0F, 125.0F, 150.0F, 175.0F};
    private static final float[] DENOMINATOR2 = {25.0F, 30.0F, 35.0F, 40.0F, 45.0F};

    // 静态常量数值
    private static final float HE_KINETIC_DAMAGE_REDUCTION = 0.45F;
    private static final float ENERGY_DAMAGE_REDUCTION = 0.1F;
    private static final float BEAM_DAMAGE_REDUCTION = 0.25F;
    private static final float EMP_DAMAGE_NEGATION = 0.4F;
    private static final float HEALTH_FLUX_CONVERSION = 0.9F;

    // 实例状态（每个船独立，但原设计存在多船共享问题，此处保持原结构，仅优化容器类型）
    private Set<DamagingProjectileAPI> tracked_beams = new HashSet<>();
    private Set<DamagingProjectileAPI> reflected_beams = new HashSet<>();
    private List<DamagingProjectileAPI> remove_tracked = new ArrayList<>(); // 临时存储待移除项

    // 静态初始化块，填充只读映射
    static {
        PROJECTILE_SOUNDS.put(WeaponSize.SMALL, "rs_ricochet_projectile_small");
        PROJECTILE_SOUNDS.put(WeaponSize.MEDIUM, "rs_ricochet_projectile_medium");
        PROJECTILE_SOUNDS.put(WeaponSize.LARGE, "rs_ricochet_projectile_large");

        BEAM_SOUNDS.put(WeaponSize.SMALL, "rs_ricochet_energy_small");
        BEAM_SOUNDS.put(WeaponSize.MEDIUM, "rs_ricochet_energy_medium");
        BEAM_SOUNDS.put(WeaponSize.LARGE, "rs_ricochet_energy_large");

        PUFF_SIZE.put(WeaponSize.SMALL, new Vector2f(15.0F, 15.0F));
        PUFF_SIZE.put(WeaponSize.MEDIUM, new Vector2f(35.0F, 35.0F));
        PUFF_SIZE.put(WeaponSize.LARGE, new Vector2f(55.0F, 55.0F));

        SPARK_SIZE.put(WeaponSize.SMALL, new Vector2f(1.0F, 1.5F));
        SPARK_SIZE.put(WeaponSize.MEDIUM, new Vector2f(1.5F, 2.0F));
        SPARK_SIZE.put(WeaponSize.LARGE, new Vector2f(2.0F, 2.5F));
    }

    public Tr_SpecializedArmor() {
        // 构造函数现在只保留必要的初始化（实例状态已在声明时初始化，无需额外操作）
        // 原本加载的 smoke_sprite 已被移除（从未使用）
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship != null) {
            // 收集新投射物（使用 HashSet 的快速 contains）
            for (DamagingProjectileAPI beams : CombatUtils.getProjectilesWithinRange(ship.getLocation(), ship.getCollisionRadius())) {
                WeaponAPI weapon_source = beams.getWeapon();
                if ((weapon_source == null || weapon_source.getShip() != ship) && !beams.didDamage() && shouldBeMarked(beams) && !tracked_beams.contains(beams)) {
                    tracked_beams.add(beams);
                }
            }
        }

        // 缓存舰船边界线段，避免每投射物重复获取
        List<BoundsAPI.SegmentAPI> segments = null;
        if (ship != null && ship.getExactBounds() != null) {
            segments = ship.getExactBounds().getSegments();
        }

        for (DamagingProjectileAPI tracked : tracked_beams) {
            Vector2f p1 = tracked.getLocation();
            Vector2f p2 = getVectorBy(p1, tracked.getFacing(), 20.0F);
            Vector2f collision_point = null;
            BoundsAPI.SegmentAPI intersected_segment = null;

            // 仅在存在边界线段时进行碰撞检测
            if (segments != null) {
                for (BoundsAPI.SegmentAPI segment : segments) {
                    Vector2f[] collisionPoints = new Vector2f[]{CollisionUtils.getCollisionPoint(
                            p1, p2, segment.getP1(), segment.getP2()
                    )};
                    collision_point = collisionPoints[0];
                    if (collision_point != null) {
                        intersected_segment = segment;
                        break;
                    }
                }
            }

            if (collision_point != null && !tracked.getCustomData().containsKey("reflected") && tracked.getOwner() != ship.getOwner() && tracked.getOwner()  >= 0 ) {
                // 计算入射角度并决定是否反射
                p1 = intersected_segment.getP1();
                p2 = intersected_segment.getP2();
                float segmentLength = MathUtils.getDistance(p1, p2);

                if (segmentLength > 0.001f) {
                    boolean shouldReflect = calculateAndCheckReflection(tracked, intersected_segment);
                    if (shouldReflect) {
                        Vector2f directional = VectorUtils.getDirectionalVector(intersected_segment.getP1(), intersected_segment.getP2());
                        float __x = directional.x;
                        float __y = directional.y;
                        double _x = (double) (-__y) / Math.sqrt(__x * __x + __y * __y);
                        double _y = (double) __x / Math.sqrt(__x * __x + __y * __y);
                        Vector2f n = new Vector2f((float) _x, (float) _y);
                        Vector2f vel = tracked.getVelocity();
                        double scalar = (double) -2.0F * dot(n, vel);
                        Vector2f scalar_n = new Vector2f((float) (_x * scalar), (float) (_y * scalar));
                        vel.setX((vel.x + scalar_n.x) / 2.0F);
                        vel.setY((vel.y + scalar_n.y) / 2.0F);
                        float angleVariance = MathUtils.getRandomNumberInRange(-5f, 5f);
                        tracked.setFacing(VectorUtils.getFacing(vel) + angleVariance);
                        tracked.setCustomData("collision_status", tracked.getCollisionClass());
                        tracked.setCollisionClass(CollisionClass.NONE);
                        tracked.setCustomData("collision_point", collision_point);
                        tracked.setCustomData("has_been_reflected", true);
                        reflected_beams.add(tracked);

                        WeaponAPI.WeaponSize weapon_size = WeaponSize.MEDIUM;
                        if (tracked.getWeapon() != null) {
                            weapon_size = tracked.getWeapon().getSize();
                        }

                        // 播放音效（已注释）
                        //SoundPlayerAPI sound_player = Global.getSoundPlayer();
                        //if (tracked.getDamageType() != DamageType.ENERGY && tracked.getDamageType() != DamageType.OTHER) {
                        //    sound_player.playSound(PROJECTILE_SOUNDS.get(weapon_size), 1.0F, 1.0F, collision_point, vel);
                        //} else if (tracked.getDamageType() == DamageType.ENERGY) {
                        //    sound_player.playSound(BEAM_SOUNDS.get(weapon_size), 1.0F, 1.0F, collision_point, vel);
                        //}

                        drawSfx(tracked, collision_point, vel);
                        healthConversionv(tracked, ship);
                    } else {
                        // 不反射，但可能仍然需要处理伤害转换
                        if (tracked.getDamageTarget() == ship && tracked.didDamage()) {
                            healthConversionv(tracked, ship);
                            remove_tracked.add(tracked);
                        }
                    }
                }
            } else {
                if (tracked.getDamageTarget() == ship && tracked.didDamage()) {
                    if (ship != null) {
                        healthConversionv(tracked, ship);
                    }
                    remove_tracked.add(tracked);
                }
                remove_tracked.add(tracked);
            }
        }

        // 清理跟踪列表（使用 removeAll 一次性移除）
        remove_tracked.forEach(tracked_beams::remove);
        remove_tracked.clear();

        // 处理已反射的投射物
        for (DamagingProjectileAPI reflected : reflected_beams) {
            tracked_beams.remove(reflected);

            Vector2f collision_point = (Vector2f)reflected.getCustomData().get("collision_point");
            float distance = MathUtils.getDistance(collision_point, reflected.getLocation());
            if (distance > 150.0F) {
                reflected.setCollisionClass((CollisionClass)reflected.getCustomData().get("collision_status"));
                reflected.setCustomData("reflected", true);
            }
        }

        removeIrrelevantProjectiles();
        removeIrrelevantProjectilesv2();
    }

    private boolean calculateAndCheckReflection(DamagingProjectileAPI proj, BoundsAPI.SegmentAPI segment) {
        Vector2f segmentVector = new Vector2f(
                segment.getP2().x - segment.getP1().x,
                segment.getP2().y - segment.getP1().y
        );

        float segmentLength = (float)Math.sqrt(segmentVector.x * segmentVector.x + segmentVector.y * segmentVector.y);
        if (segmentLength < 0.001f) {
            return false;
        }

        Vector2f segmentNormal = new Vector2f(-segmentVector.y, segmentVector.x);

        float normalLength = (float)Math.sqrt(segmentNormal.x * segmentNormal.x + segmentNormal.y * segmentNormal.y);
        if (normalLength > 0.001f) {
            segmentNormal.x /= normalLength;
            segmentNormal.y /= normalLength;
        } else {
            return false;
        }

        Vector2f projectileDirection = new Vector2f(proj.getVelocity());

        float projSpeed = (float)Math.sqrt(projectileDirection.x * projectileDirection.x + projectileDirection.y * projectileDirection.y);
        if (projSpeed < 0.001f) {
            return false;
        }

        projectileDirection.x /= projSpeed;
        projectileDirection.y /= projSpeed;

        float dotProduct = segmentNormal.x * projectileDirection.x + segmentNormal.y * projectileDirection.y;

        dotProduct = Math.max(-1.0f, Math.min(1.0f, dotProduct));
        float incidentAngle = (float)Math.toDegrees(Math.acos(Math.abs(dotProduct)));

        incidentAngle = Math.min(90.0f, Math.max(0.0f, incidentAngle));

        float reflectionChance = getReflectionChanceByAngle(incidentAngle);

        float randomValue = MathUtils.getRandomNumberInRange(0.0f, 1.0f);

        return randomValue <= reflectionChance;
    }

    private float getReflectionChanceByAngle(float angle) {
        if (angle <= 20.0f) {
            return 0f;
        } else if (angle <= 45.0f) {
            return 0.3f;
        } else if (angle <= 65.0f) {
            return 0.65f;
        } else if (angle <= 80.0f) {
            return 0.85f;
        } else {
            return 1f;
        }
    }

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getHighExplosiveDamageTakenMult().modifyMult(id, 1.0F - HE_KINETIC_DAMAGE_REDUCTION);
        stats.getKineticDamageTakenMult().modifyMult(id, 1.0F - HE_KINETIC_DAMAGE_REDUCTION);
        stats.getEnergyDamageTakenMult().modifyMult(id, 1.0F - ENERGY_DAMAGE_REDUCTION);
        stats.getBeamDamageTakenMult().modifyMult(id, 1.0F - BEAM_DAMAGE_REDUCTION);
        stats.getEmpDamageTakenMult().modifyMult(id, 1.0F - EMP_DAMAGE_NEGATION);
    }

    private void healthConversionv(DamagingProjectileAPI proj, ShipAPI ship) {
        float soft = proj.getBaseDamageAmount();
        soft = soft * HEALTH_FLUX_CONVERSION;
        FluxTrackerAPI flux_tracker = ship.getFluxTracker();
        flux_tracker.increaseFlux(soft, false);
    }

    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) {
            return "" + (int)(HE_KINETIC_DAMAGE_REDUCTION * 100.0F) + "%";
        } else if (index == 1) {
            return "" + (int)(ENERGY_DAMAGE_REDUCTION * 100.0F) + "%";
        } else if (index == 2) {
            return "" + (int)(BEAM_DAMAGE_REDUCTION * 100.0F) + "%";
        } else {
            return null;
        }
    }

    private void drawSfx(DamagingProjectileAPI proj, Vector2f point, Vector2f vel) {
        WeaponAPI.WeaponSize weapon_size = WeaponSize.MEDIUM;
        if (proj.getWeapon() != null) {
            weapon_size = proj.getWeapon().getSize();
        }

        float clone_x = vel.x;
        float clone_y = vel.y;
        CombatEngineAPI engine = Global.getCombatEngine();
        int smoke_number = MathUtils.getRandomNumberInRange(1, 3);

        for(int i = 0; i < smoke_number; ++i) {
            Vector2f range = PUFF_SIZE.get(weapon_size);
            float size = MathUtils.getRandomNumberInRange(range.x, range.y);
            float opacity = MathUtils.getRandomNumberInRange(0.05F, 0.1F);
            float duration = MathUtils.getRandomNumberInRange(0.1F, 0.3F);
            int denomination = MathUtils.getRandomNumberInRange(0, 4);
            Vector2f vel_clone = new Vector2f((float)(denomination * 5) + clone_x / DENOMINATOR[denomination], (float)(denomination * 5) + clone_y / DENOMINATOR[denomination]);
            engine.addSmokeParticle(point, vel_clone, size, opacity, duration, Color.darkGray);
        }

        int fire_number = MathUtils.getRandomNumberInRange(5, 10);

        for(int i = 0; i < fire_number; ++i) {
            Vector2f range = SPARK_SIZE.get(weapon_size);
            float size = MathUtils.getRandomNumberInRange(range.x, range.y);
            float opacity = MathUtils.getRandomNumberInRange(0.3F, 0.6F);
            float duration = MathUtils.getRandomNumberInRange(0.3F, 0.7F);
            int denomination = MathUtils.getRandomNumberInRange(0, 4);
            Vector2f vel_clone = new Vector2f(clone_x / DENOMINATOR2[denomination], clone_y / DENOMINATOR2[denomination]);
            engine.addHitParticle(point, vel_clone, size, opacity, duration, Color.YELLOW);
        }
    }

    private static double dot(Vector2f a, Vector2f b) {
        return a.x * b.x + a.y * b.y;
    }

    private void removeIrrelevantProjectiles() {
        CombatEngineAPI engine = Global.getCombatEngine();
        // 使用迭代器直接移除，避免创建临时列表
        tracked_beams.removeIf(tracked -> !engine.isEntityInPlay(tracked) || !shouldBeMarked(tracked));
    }

    private void removeIrrelevantProjectilesv2() {
        CombatEngineAPI engine = Global.getCombatEngine();
        reflected_beams.removeIf(reflected -> !engine.isEntityInPlay(reflected) || !shouldBeMarked(reflected));
    }

    private Boolean shouldBeMarked(DamagingProjectileAPI proj) {
        Map<String, Object> custom_data = proj.getCustomData();
        return !custom_data.containsKey("reflected");
    }

    private Vector2f getVectorBy(Vector2f point, float angle, float distance) {
        float processed_angle = (float)((double)angle * Math.PI / 180.0);
        float posX = (float)((double)distance * Math.cos(processed_angle) + (double)point.x);
        float posY = (float)((double)distance * Math.sin(processed_angle) + (double)point.y);
        return new Vector2f(posX, posY);
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        Color green = new Color(55, 245, 65, 255);
        Color warning = new Color(199, 78, 78, 155);
        Color flavor = new Color(110, 110, 110, 255);

        tooltip.addSectionHeading("Detailed description", Alignment.MID, 10.0F);
        tooltip.addPara("Reflective armor provides superior survivability even when the hull armor is completely depleted because the armor plates are built integrally with the hull structure.\n\n• Reflection probability depends on %s and %s of the projectile, up to %s. Reflected damage is reduced by %s, and %s of the base damage is converted to soft flux.", 10.0F, Misc.getHighlightColor(), "incident angle", "Weapon type", "100%", "50%", "90%");

        tooltip.addPara("Kinetic/High Explosive Damage Reduction: %s • Energy Weapon Damage Reduction: %s • Beam Damage Reduction: %s • EMP Resistance Boost: %s", 10.0F, green,
                Misc.getRoundedValue(HE_KINETIC_DAMAGE_REDUCTION * 100.0F) + "%",
                Misc.getRoundedValue(ENERGY_DAMAGE_REDUCTION * 100.0F) + "%",
                Misc.getRoundedValue(BEAM_DAMAGE_REDUCTION * 100.0F) + "%",
                Misc.getRoundedValue(EMP_DAMAGE_NEGATION * 100.0F) + "%");

        tooltip.addPara("The relationship between reflection probability and incident angle:" +
                "≤20°: 0% reflection" +
                "20°-45°: 30% reflective" +
                "45°-65°: 65% reflective" +
                "65°-80°: 85% reflective" +
                "≥80°: 100% reflective", 10.0F, Misc.getHighlightColor(), "");

        tooltip.addSectionHeading("warn", Misc.getHighlightColor(), warning, Alignment.MID, 10.0F);
        TooltipMakerAPI incompat_text = tooltip.beginImageWithText("graphics/icons/tooltip/hullmod_incompatible.png", 40.0F);
        incompat_text.addPara("This armor plate %s and %s weapons provide poor protection and do not provide complete protection against %s.", 10.0F, Misc.getNegativeHighlightColor(),
         "anti-beam weapons", "High explosive type", "Missiles and other explosive ordnance");
        //tooltip.addImageWithText(10.0F);
        //tooltip.addPara("%s", 10.0F, flavor, "这次攻击被弹开了！我们无法击穿他们的装甲。这到底是什么材料做的？！").italicize();
        //tooltip.addPara("%s", 2.0F, flavor, "         —— 一名前海盗幸存者在遭遇基哈尔级机动兵器后的叙述。");
    }
}