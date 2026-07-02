package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.*;
import java.util.List;

public class WeaponOverLoad extends BaseHullMod {
    private static final Set<String> BLOCKED_OTHER = new HashSet<>();
    private static final Set<String> BLOCKED_OMNI = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER_PLAYER_ONLY = new HashSet<>();
    private static final String ERROR = "IncompatibleHullmodWarning";
    private float check = 0.0F;

    // 存储光束武器上一帧的开火状态
    private Map<WeaponAPI, Boolean> beamWeaponLastFiringMap = new HashMap<>();
    // 存储非光束武器上一帧的冷却状态
    private Map<WeaponAPI, Float> nonBeamWeaponLastCooldownMap = new HashMap<>();

    public boolean isApplicableToShip(ShipAPI ship) {
        return ship.getVariant().getHullMods().contains("CrusadersCore")&&
                !ship.getVariant().getHullMods().contains("PhaseDefenseUnit") &&
                !ship.getVariant().getHullMods().contains("PolariphaseDrive");
    }

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getFluxDissipation().modifyMult(id, 1.75f);
        stats.getFluxCapacity().modifyMult(id, 0.75f);
        stats.getEnergyWeaponDamageMult().modifyMult(id, 1.3f);
        stats.getEnergyWeaponFluxCostMod().modifyMult(id, 1.15f);
        stats.getBallisticWeaponDamageMult().modifyMult(id, 1.3f);
        stats.getBallisticWeaponFluxCostMod().modifyMult(id, 1.15f);
        stats.getAcceleration().modifyMult(id, 0.75f);
        stats.getDeceleration().modifyMult(id, 0.75f);
        stats.getMaxTurnRate().modifyMult(id, 0.75f);
        stats.getTurnAcceleration().modifyMult(id, 0.75f);
    }

    public static final String FLUX_DATA_KEY = "weapon_overload_flux_data";

    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!Global.getCombatEngine().isPaused() && ship.isAlive()) {
            // 获取或创建数据实例
            WeaponFluxData fluxData = (WeaponFluxData) ship.getCustomData().get(FLUX_DATA_KEY);
            if (fluxData == null) {
                fluxData = new WeaponFluxData();
                ship.getCustomData().put(FLUX_DATA_KEY, fluxData);
            }

            // 重置当前帧数据
            fluxData.resetFrameData();

            List<WeaponAPI> weapons = ship.getUsableWeapons();
            for (WeaponAPI weapon : weapons) {
                float fluxToAdd;

                if (weapon.isBeam()) {
                    fluxToAdd = handleBeamWeapon(ship, weapon);
                } else {
                    fluxToAdd = handleNonBeamWeapon(ship, weapon);
                }

                // 累计当前帧产生的辐能
                if (fluxToAdd > 0) {
                    fluxData.currentFluxToAdd += fluxToAdd;
                    fluxData.totalFluxAdded += fluxToAdd;
                }
            }
        }
    }

    private float handleBeamWeapon(ShipAPI ship, WeaponAPI weapon) {
        // 获取上一帧的开火状态（默认为false）
        boolean lastFiring = beamWeaponLastFiringMap.getOrDefault(weapon, false);
        boolean currentFiring = weapon.isFiring();

        // 更新状态记录
        beamWeaponLastFiringMap.put(weapon, currentFiring);
        if (!lastFiring && currentFiring) {
            float fluxCost = weapon.getFluxCostToFire();
            float fluxToAdd = fluxCost * 0.30f;
            ship.getFluxTracker().increaseFlux(fluxToAdd, true);
            return fluxToAdd; // 返回产生的flux值
        }
        return 0;

    }

    private float handleNonBeamWeapon(ShipAPI ship, WeaponAPI weapon) {
        // 获取上一帧的冷却状态（默认为0）
        float lastCooldown = nonBeamWeaponLastCooldownMap.getOrDefault(weapon, 0f);
        float currentCooldown = weapon.getCooldownRemaining();
        // 更新状态记录
        nonBeamWeaponLastCooldownMap.put(weapon, currentCooldown);

        if (lastCooldown <= 0.001f && currentCooldown > 0.001f) {
            float fluxCost = weapon.getFluxCostToFire();
            float fluxToAdd = fluxCost * 0.4f;
            ship.getFluxTracker().increaseFlux(fluxToAdd, false);
            return fluxToAdd; // 返回产生的flux值
        }
        return 0;
    }

    public static class WeaponFluxData {
        public float currentFluxToAdd;  // 当前帧待转移的辐能量
        public float totalFluxAdded;    // 累计总量（调试用）

        public void resetFrameData() {
            currentFluxToAdd = 0;
        }
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
        Color highlight = Misc.getHighlightColor();
        Color h = Misc.getPositiveHighlightColor();
        Color bad = Misc.getNegativeHighlightColor();

        tooltip.addPara("对舰船进行特定升级的强化套件，每艘舰船只能安装一种特殊强化套件",  opad,h);
        tooltip.addSectionHeading("效果", Alignment.MID, opad);
        tooltip.addPara("将大部分舰船能源重定向至武器系统。", pad, h);
        tooltip.addPara("非导弹武器伤害+ %s  ", pad, h, "" +30 + "%");
        tooltip.addPara("同时所需辐能+ %s ", pad, bad, "" +15 + "%");
        tooltip.addPara("舰船耗散提升 %s ", pad, h,  "" + 75+ "%" );
        tooltip.addPara("舰船辐能容量下降 %s ", pad, bad,  "" + 25+ "%");
        tooltip.addPara("舰船机动能力下降 %s ", pad, bad,  "" + 25+ "%");
        tooltip.addPara("为舰船武器带来额外效果，按住 %s 以查看详细机制", opad, highlight,  "F3" );
        if (Keyboard.isKeyDown(Keyboard.KEY_F3)) {
            tooltip.addPara("武器击发时，额外产生武器所需辐能的 %s 的软辐能作为暂存辐能", pad, bad,"" +40+ "%");
            tooltip.addPara("暂存辐能将占用电网缓慢排散，并根据其总量的 %s 对舰船镀层进行回复", pad, h,  "" + 5+ "%");

        }

        tooltip.addSectionHeading("船插对战术系统的增益", Alignment.MID, opad);
        if((ship == null)){
            tooltip.addPara("未检测到合规舰船及战术系统", pad);
        }
        if(!(ship == null)) {
            if (!(ship.getSystem() == null)) {
                tooltip.addPara("按住 %s 以查看详细信息", opad, h, "F4");
                if (Keyboard.isKeyDown(Keyboard.KEY_F4)) {
                    if (ship.getSystem().getId().equals("CR_PhaseboostDrive")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/ammo_feeder.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("在战术系统使用后1s内加强 50% 的实弹与能量武器伤害", pad);
                        tooltip.addImageWithText(15f);
                    }


                    if (ship.getSystem().getId().equals("CR_PhaseVerbJet")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/ammo_feeder.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("在战术系统开启期间增加 50% 的非导弹武器伤害射速与辐能消耗", pad);
                        tooltip.addImageWithText(15f);
                    }


                    if (ship.getSystem().getId().equals("CR_PhaseDrift")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/ammo_feeder.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("战术系统持续期间每进行75单位位移便生成一架dem无人机攻击敌方单位", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("RS_WeaponOverloading")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/high_energy_focus.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("强化战术系统对武器的强化效果", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("CR_TargetingLink")) {

                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/high_energy_focus.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("大幅强化战舰与所放飞战机的武器伤害及能量消耗", pad);
                        tooltip.addImageWithText(15f);
                    }
                    if (ship.getSystem().getId().equals("RS_FortressShieldStats")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/high_energy_focus.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("中等幅度加压护盾并减少武器辐能消耗，期间舰船可以开火", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("CR_PhaseCrossing")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/high_energy_focus.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("撕裂空间，于舰船跳跃处形成一次大威力爆炸", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("CR_DamperBurn")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/high_energy_focus.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("系统持续期间增加非导弹武器的伤害与射速", pad);
                        tooltip.addImageWithText(15f);
                    }

                    if (ship.getSystem().getId().equals("RS_MABurn")) {
                        TooltipMakerAPI imageText = tooltip.beginImageWithText("graphics/icons/hullsys/high_energy_focus.png", 35f);
                        imageText.addPara("为战术系统附加效果：", pad);
                        imageText.addPara("系统持续期间增加非导弹武器的伤害与射速", pad);
                        tooltip.addImageWithText(15f);
                    }
                }
            }
        }
    }

    static {
        BLOCKED_OMNI.add("PhaseDefenseUnit");
        BLOCKED_OMNI.add("PolariphaseDrive");
        //BLOCKED_OMNI.add("targetingunit");


        BLOCKED_OTHER.add("PhaseDefenseUnit");
        BLOCKED_OTHER.add("PolariphaseDrive");
        //BLOCKED_OTHER.add("targetingunit");

        BLOCKED_OTHER_PLAYER_ONLY.add("PhaseDefenseUnit");
        BLOCKED_OTHER_PLAYER_ONLY.add("PolariphaseDrive");
        //BLOCKED_OTHER_PLAYER_ONLY.add("targetingunit");
    }
}


