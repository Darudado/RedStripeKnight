package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;
import java.awt.Color;
import java.util.*;

public class PhaseDefenseUnit extends BaseHullMod {
    // 防御场常量参数
    private static final float MIN_ABSORB_CHANCE = 0.15f;
    private static final float MAX_ABSORB_CHANCE = 0.6f;
    private static final float FIELD_RADIUS_FACTOR = 1.5f;
    private static final float FLUX_PER_DAMAGE = 1.25f;
    private static final float PARTICLE_SIZE = 4.0f;
    private static final float PARTICLE_DURATION = 0.4f;
    private static final Color PARTICLE_COLOR = new Color(175, 15, 15, 175);

    private static final Set<String> BLOCKED_OTHER = new HashSet<>();
    private static final Set<String> BLOCKED_OMNI = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER_PLAYER_ONLY = new HashSet<>();
    private static final String ERROR = "IncompatibleHullmodWarning";
    private float check = 0.0F;

    // 静态存储：舰船ID -> 最后吸收数据
    private static final Map<String, AbsorptionData> lastAbsorptionData = new HashMap<>();

    // 静态内部类存储吸收数据
    public static class AbsorptionData {
        public final float damage;
        public static float fluxToAdd; // 注意：这里应该是非静态的

        public AbsorptionData(float damage, float fluxToAdd) {
            this.damage = damage;
            AbsorptionData.fluxToAdd = fluxToAdd;
        }
    }

    // 提供给其他类访问的静态方法
    public static AbsorptionData getLastAbsorptionData(String shipId) {
        return lastAbsorptionData.get(shipId);
    }

    private static class DefenseData {
        Map<DamagingProjectileAPI, Boolean> processedProjectiles = new HashMap<>();
        float currentChance = MIN_ABSORB_CHANCE;
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        return ship.getVariant().getHullMods().contains("CrusadersCore")&&
        !ship.getVariant().getHullMods().contains("WeaponOverLoad") &&
                !ship.getVariant().getHullMods().contains("PolariphaseDrive");
    }

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getFluxDissipation().modifyMult(id, 0.75f);
        stats.getFluxCapacity().modifyMult(id, 1.15f);
        stats.getShieldDamageTakenMult().modifyMult(id, 0.9f);
        stats.getHullDamageTakenMult().modifyMult(id, 0.9f);
        stats.getDynamic().getStat(Stats.SHIELD_PIERCED_MULT).modifyMult(id, 0.75f);
        stats.getEmpDamageTakenMult().modifyMult(id, 0.8f);
        stats.getVentRateMult().modifyMult(id, 1.25f);
        stats.getMaxSpeed().modifyMult(id, 0.6f);
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused() || !ship.isAlive()) return;
        if(ship.getFluxTracker().isVenting()) return;
        if(ship.getFluxTracker().getFluxLevel() >= 1f) return;

        DefenseData data = (DefenseData) ship.getCustomData().get("PhaseDefenseData");
        if (data == null) {
            data = new DefenseData();
            ship.getCustomData().put("PhaseDefenseData", data);
        }
        // 移除了清空processedProjectiles的逻辑

        float fluxLevel = ship.getFluxTracker().getFluxLevel();
        data.currentChance = MAX_ABSORB_CHANCE - (MAX_ABSORB_CHANCE - MIN_ABSORB_CHANCE) * fluxLevel;

        if (engine.getPlayerShip() == ship) {
            engine.maintainStatusForPlayerShip(
                    "PHASE_DEFENSE_STATUS",
                    "graphics/icons/hullsys/fortress_shield.png",
                    "相位防御单元",
                    "吸收概率: " + (int)(data.currentChance * 100) + "%",
                    false
            );
        }

        float shipRadius = ship.getCollisionRadius();
        float fieldRadius = shipRadius * FIELD_RADIUS_FACTOR;
        Vector2f shipLocation = ship.getLocation();

        Iterator<Object> iter = engine.getAllObjectGrid().getCheckIterator(
                shipLocation, fieldRadius * 2f, fieldRadius * 2f);

        while (iter.hasNext()) {
            Object obj = iter.next();
            if (!(obj instanceof DamagingProjectileAPI proj)) continue;

            // 修复1：添加射弹有效性检查
            if (!engine.isEntityInPlay(proj)) continue;

            // 修复2：优化过滤条件
            if (proj.didDamage() ||
                    proj.getOwner() == ship.getOwner() ||
                    data.processedProjectiles.containsKey(proj)) {
                continue;
            }

            if (MathUtils.getDistance(proj, ship) > fieldRadius) continue;

            // 标记为已处理
            data.processedProjectiles.put(proj, true);

            // 修复3：添加射弹存活检查
            if (!engine.isEntityInPlay(proj)) continue;

            if (Math.random() < data.currentChance) {
                absorbProjectile(engine, ship, proj);
            }
        }
    }

    private void absorbProjectile(CombatEngineAPI engine, ShipAPI ship, DamagingProjectileAPI proj) {
        float damage = proj.getDamageAmount();
        float fluxToAdd = damage * FLUX_PER_DAMAGE;

        ship.getFluxTracker().increaseFlux(fluxToAdd, false);
        createAbsorptionEffect(engine, proj.getLocation(), proj.getVelocity());

        // 修复4：添加射弹存在检查
        if (engine.isEntityInPlay(proj)) {
            engine.removeEntity(proj);
        }

        // 存储吸收数据
        lastAbsorptionData.put(ship.getId(), new AbsorptionData(damage, fluxToAdd));
    }

    private void createAbsorptionEffect(CombatEngineAPI engine, Vector2f location, Vector2f velocity) {
        engine.addHitParticle(
                location,
                velocity,
                PARTICLE_SIZE * 3f,
                1.0f,
                PARTICLE_DURATION,
                PARTICLE_COLOR
        );

        for (int i = 0; i < 5; i++) {
            Vector2f particleVel = new Vector2f(
                    velocity.x + (float) Math.random() * 50f - 25f,
                    velocity.y + (float) Math.random() * 50f - 25f
            );
            engine.addHitParticle(
                    location,
                    particleVel,
                    PARTICLE_SIZE * (0.5f + (float) Math.random() * 0.5f),
                    0.8f,
                    PARTICLE_DURATION * 0.7f,
                    PARTICLE_COLOR
            );
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "45%";
        if (index == 1) return "75%";
        if (index == 2) return "25%";
        return null;
    }

    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.getMutableStats();
        if (check > 0.0F && --check < 1.0F) {
            ship.getVariant().removeMod(ERROR);
        }

        for (Set<String> strings : Arrays.asList(BLOCKED_OMNI, BLOCKED_OTHER, BLOCKED_OTHER_PLAYER_ONLY)) {
            checkAndRemoveBlockedMods(ship, strings);
        }

    }

    private void checkAndRemoveBlockedMods(ShipAPI ship, @NotNull Set<String> blockedMods) {
        List<String> shipMods = new ArrayList<>(ship.getVariant().getHullMods());
        for (String mod : shipMods) {
            if (blockedMods.contains(mod)) {
                // 移除冲突船插
                ship.getVariant().removeMod(mod);
                if (!ship.getVariant().hasHullMod(ERROR)) {
                    ship.getVariant().addMod(ERROR);
                }
                // 记录日志
                Global.getLogger(this.getClass()).info(
                        "Removed conflicting hullmod [" + mod + "] from " + ship.getName()
                );
            }
        }
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        float pad = 2f;
        Color h = Misc.getPositiveHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        tooltip.addPara("对舰船进行特定升级的强化套件，每艘舰船只能安装一种特殊强化套件",  opad,h);
        tooltip.addPara("将大部分舰船能源重定向至防护系统。", pad, h);
        tooltip.addSectionHeading("效果", Alignment.MID, opad);
        tooltip.addPara("舰船电网耗散降低 %s  ", pad, bad,  "" + 25 + "%");
        tooltip.addPara("舰船电网容量提升 %s ", pad, h,  "" + 15 + "%");
        tooltip.addPara("船体/护盾所受伤害降低 %s ，护盾被贯穿几率降低 %s ", pad, h, "" + 10 + "%","" + 25 + "%");
        tooltip.addPara("强制排散速率提升 %s ", pad, h, "" + 25 + "%");
        tooltip.addPara("最大战斗航速降低 %s ", pad, bad, "" + 40 + "%");
        tooltip.addPara("如此大刀阔斧地改动是为了适配一种与镀层关联的全新防御机制",  pad, h);
        tooltip.addPara("按住 %s 以查看详细机制", opad, Misc.getHighlightColor(),  "F3" );
        if (Keyboard.isKeyDown(Keyboard.KEY_F3)) {
            tooltip.addPara("吸收护盾覆盖范围 %s 倍内的射弹，转化为电网辐能", pad, h, "" + (FIELD_RADIUS_FACTOR) + "%");
            tooltip.addPara("每发射弹产生的辐能为其伤害的 %s ", pad, bad, "" + FLUX_PER_DAMAGE + "%");
            tooltip.addPara("对确定的单一射弹的吸收概率为 %s - %s 之间，随着辐能上升而下降", pad, h, "" + (int)MIN_ABSORB_CHANCE*100+"%" , "" + (int)MAX_ABSORB_CHANCE *100+"%");
            tooltip.addPara("产生辐能的 %s 用于给镀层充能", pad, h, "" + 0.05 + "%");

        }

        tooltip.addSectionHeading("船插对战术系统的增益", Alignment.MID, opad);

        if(!(ship == null)) {
            if (!(ship.getSystem() == null)) {
                tooltip.addPara("按住 %s 以查看详细信息", opad, h, "F4");
                if (Keyboard.isKeyDown(Keyboard.KEY_F4)) {
                    if (ship.getSystem().getId().equals("CR_PhaseboostDrive")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/active_flare_launcher.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("加速舰船引擎修复并耗散基于目前辐能水平15%的辐能", pad);
                        tooltip.addImageWithText(15f);
                    } else if (ship.getSystem().getId().equals("CR_PhaseVerbJet")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/active_flare_launcher.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("为护盾加压，提供25％减伤效果", pad);
                        tooltip.addImageWithText(15f);
                    } else if (ship.getSystem().getId().equals("CR_PhaseDrift")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/active_flare_launcher.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("提高战术系统持续期间的软辐能散耗速率", pad);
                        tooltip.addImageWithText(15f);
                    } else if (ship.getSystem().getId().equals("RS_WeaponOverloading")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/emp_emitter.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("战术系统持续期间提升盾效并发射点防御电弧", pad);
                        tooltip.addImageWithText(15f);
                    } else if (ship.getSystem().getId().equals("CR_TargetingLink")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/emp_emitter.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("提升战舰与所放飞战机的伤害减免能力", pad);
                        tooltip.addImageWithText(15f);
                    } else if (ship.getSystem().getId().equals("RS_FortressShieldStats")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/emp_emitter.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("较大幅度加压护盾，提升舰船耗散能力并使舰船获得15%硬辐能耗散", pad);
                        tooltip.addImageWithText(15f);
                    }else if (ship.getSystem().getId().equals("CR_PhaseCrossing")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/emp_emitter.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("跳跃后为舰船提供持续3秒的大比例减伤，不可叠加", pad);
                        tooltip.addImageWithText(15f);
                    }else if (ship.getSystem().getId().equals("CR_DamperBurn")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/emp_emitter.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("系统持续期间舰船受到伤害大幅减少", pad);
                        tooltip.addImageWithText(15f);
                    }else if (ship.getSystem().getId().equals("RS_MABurn")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/emp_emitter.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("系统持续期间舰船受到伤害大幅减少", pad);
                        tooltip.addImageWithText(15f);
                    }
                }
            }
        }
    }

    static {
        BLOCKED_OMNI.add("WeaponOverLoad");
        BLOCKED_OMNI.add("PolariphaseDrive");
        //BLOCKED_OMNI.add("targetingunit");


        BLOCKED_OTHER.add("WeaponOverLoad");
        BLOCKED_OTHER.add("PolariphaseDrive");
        //BLOCKED_OTHER.add("targetingunit");

        BLOCKED_OTHER_PLAYER_ONLY.add("WeaponOverLoad");
        BLOCKED_OTHER_PLAYER_ONLY.add("PolariphaseDrive");
        //BLOCKED_OTHER_PLAYER_ONLY.add("targetingunit");
    }
}