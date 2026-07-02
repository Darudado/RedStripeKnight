package data.hullmods.crusaders;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

import data.hullmods.WeaponOverLoad;
import data.scripts.util.MagicUI;

import java.awt.Color;

import java.util.*;

import data.scripts.utils.MagicUIHelper;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;

import static data.hullmods.PhaseDefenseUnit.AbsorptionData;

public class CrusadersPlating extends BaseHullMod {
    public static final float ARMOR_RESTORE_DELAY = 7f;
    public static final float HULL_DAMAGE_TAKEN_MULT_WHILE_ACTIVE = 0.85f;
    public static final float ENGINE_AND_WEAPON_DAMAGE_TAKEN_MULT_WHILE_ACTIVE = 0.9f;
    public static final float EMP_DAMAGE_TAKEN_MULT = 0.25f;
    public static final float RECOVERY = 0.025f;
    public static final float THRESHOLD_RECOVERY = 0.4f;
    public static final float LOW_CR_THRESHOLD = 0.4f;
    public static final float OVERLOAD_DEGRADE = 0.05f;
    public static final float FLUX_VENT_RECOVERY_RATIO = 0.04f;
    private static final boolean ShaderLibExists = Global.getSettings().getModManager().isModEnabled("shaderLib");

    private float check = 0.0F;
    public static final Map<HullSize, Float> mag = new HashMap<>();
    private static final Set<String> BLOCKED_OTHER = new HashSet<>();
    private static final Set<String> BLOCKED_OMNI = new HashSet<>();
    private static final Set<String> BLOCKED_OTHER_PLAYER_ONLY = new HashSet<>();
    private static final Map<HullSize, Float> FLUX_CAPACITY = new HashMap<>();
    private static final String ERROR = "IncompatibleHullmodWarning";
    
    static final String PROVIDER_KEY = "RS_crusaders_request_provider";


    public static float ARMOR_MULT = 0.75F;
    public static float ARMOR_BOUNS = 1.4F;
    static {
        mag.put(HullSize.FIGHTER, 1.5f / 20);
        mag.put(HullSize.FRIGATE, 1.5f / 20);
        mag.put(HullSize.DESTROYER, 1.35f / 20);
        mag.put(HullSize.CRUISER, 1.2f / 20);
        mag.put(HullSize.CAPITAL_SHIP, 1.1f / 20);
        FLUX_CAPACITY.put(HullSize.FIGHTER, 1.0f);
        FLUX_CAPACITY.put(HullSize.FRIGATE, 1.0f);
        FLUX_CAPACITY.put(HullSize.DESTROYER, 0.95f);
        FLUX_CAPACITY.put(HullSize.CRUISER, 0.9f);
        FLUX_CAPACITY.put(HullSize.CAPITAL_SHIP, 0.85f);
    }


    public boolean shouldAddDescriptionToTooltip(HullSize hullSize, ShipAPI ship, boolean isForModSpec) {
        return false;
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        float opad = 10f;
        float pad = 2f;
        Color h = Misc.getHighlightColor();

        tooltip.addPara("由先进的十字军核心的可控外泄能量形成的类装甲镀层，根据船体规模与辐能容量提供 %s 减伤，可见于大部分安装有十字军核心的舰船", opad, h, ship != null ? "" + (Math.round(ship.getMutableStats().getFluxCapacity().getModifiedValue() * mag.get(hullSize))) : "" + Math.round(250f));
        tooltip.addPara("这是一种由纯粹能量构成的完整镀层，呈现了奇特的偏相特征，可减少 %s 武器与引擎收到的伤害，减少 %s 舰体受到的伤害，减少 %s 舰船受到的EMP伤害；所有减伤效果根据当前暂态镀层完整度计算", pad, h, Math.round((1f - ENGINE_AND_WEAPON_DAMAGE_TAKEN_MULT_WHILE_ACTIVE) * 100f) + "%", Math.round((1f - HULL_DAMAGE_TAKEN_MULT_WHILE_ACTIVE) * 100f) + "%", Math.round((1f - EMP_DAMAGE_TAKEN_MULT) * 100f) + "%");
        tooltip.addSectionHeading("机制", Alignment.MID, opad);
        tooltip.addPara("一旦镀层遭到损伤，在不受击的 %s 秒后，镀层将会以 %s 单位每秒的速度恢复", pad, h, "2", "10");
        tooltip.addPara("一旦镀层受到巨量伤害，偏相镀层将会暂时瓦解并且会在回充至 %s 时重启", pad, h, ship != null ? String.valueOf(Math.round(ship.getMutableStats().getFluxCapacity().getModifiedValue() * mag.get(hullSize) * THRESHOLD_RECOVERY)) : Math.round(THRESHOLD_RECOVERY * 100) + "%");
        tooltip.addPara("由于十字军核心的产生的巨量废热，在过载或者主动排散辐能时，镀层只能提供正常情况下一半的效果", pad, h);
        tooltip.addPara("舰船过载产生的巨量不受控能量将会以 %s 单位每秒的速度破坏现有镀层的完整性", pad, h, ship != null ? "" + Math.round(ship.getMutableStats().getFluxCapacity().getModifiedValue() * mag.get(hullSize) * OVERLOAD_DEGRADE) : Math.round(OVERLOAD_DEGRADE * 100) + "%");
        tooltip.addPara("在 %s 战备值以下，镀层将会部分失活，使得其作战效能下降", pad, h, Math.round(40.0F) + "%");
        tooltip.addPara("主动排散时，每排散 %s 单位辐能可恢复 %s 镀层容量", pad, Misc.getHighlightColor(), "25", "1单位");
        tooltip.addPara("由于高能镀层的保护效果，舰船能避免日冕造成的损害", pad, h);
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
               // Global.getLogger(this.getClass()).info("Removed conflicting hullmod [" + mod + "] from " + ship.getName());
            }
        }
    }

    static {
        BLOCKED_OMNI.add("high_scatter_amp");
        BLOCKED_OMNI.add("shield_shunt");
        BLOCKED_OMNI.add("unstable_injector");
        BLOCKED_OMNI.add("augmentedengines");
        BLOCKED_OMNI.add("frontshield");
        BLOCKED_OMNI.add("frontemitter");
        BLOCKED_OMNI.add("extendedshieldemitter");
        BLOCKED_OMNI.add("adaptiveshields");
        BLOCKED_OMNI.add("additional_berthing");


        BLOCKED_OTHER.add("shield_shunt");
        BLOCKED_OTHER.add("unstable_injector");
        BLOCKED_OTHER.add("safetyoverrides");
        BLOCKED_OTHER.add("recovery_shuttles");
        BLOCKED_OTHER.add("additional_berthing");
        BLOCKED_OTHER.add("augmentedengines");
        BLOCKED_OTHER.add("frontshield");
        BLOCKED_OTHER.add("frontemitter");
        BLOCKED_OTHER.add("extendedshieldemitter");
        BLOCKED_OTHER.add("adaptiveshields");

        BLOCKED_OTHER_PLAYER_ONLY.add("converted_hangar");
        BLOCKED_OTHER_PLAYER_ONLY.add("TSC_converted_hangar");
        BLOCKED_OTHER_PLAYER_ONLY.add("shield_shunt");
        BLOCKED_OTHER_PLAYER_ONLY.add("frontshield");
        BLOCKED_OTHER_PLAYER_ONLY.add("frontemitter");
        BLOCKED_OTHER_PLAYER_ONLY.add("extendedshieldemitter");
        BLOCKED_OTHER_PLAYER_ONLY.add("adaptiveshields");
        BLOCKED_OTHER_PLAYER_ONLY.add("unstable_injector");
        BLOCKED_OTHER_PLAYER_ONLY.add("safetyoverrides");
    }



    public static Color getDamagedShieldColor(float currentHealth, float maxHealth) {
        int r = (int) MathUtils.clamp(215 - 45 * (currentHealth / maxHealth - 0.25f), 0f, 255f);
        int g = (int) MathUtils.clamp(25 - 10 * (1f - currentHealth / maxHealth), 0f, 255f);
        int b = (int) MathUtils.clamp(25 - 15 * (1f - currentHealth / maxHealth), 0f, 255f);


        return new Color(r, g, b, 50);
    }

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getDynamic().getStat(Stats.CORONA_EFFECT_MULT).modifyMult(id, 0f);
        stats.getDynamic().getStat(Stats.HULL_DAMAGE_CR_LOSS).modifyMult(id, 0f);
        stats.getEnergyDamageTakenMult().modifyMult(id, 0.85f);

        stats.getEffectiveArmorBonus().modifyMult(id, ARMOR_MULT);
        stats.getMinArmorFraction().modifyMult(id, ARMOR_BOUNS);
        stats.getArmorBonus().modifyMult(id , ARMOR_BOUNS);

    }

    public static void applyArmorGlow(ShipAPI ship, Color color, float intensity, int copies, float jitterDistMin, float jitterDistMax) {
        if (ShaderLibExists) {
            StandardLight light = new StandardLight(new Vector2f(0, 0), ship.getVelocity(), new Vector2f(0, 0), ship);
            light.getColor().set(new Vector3f(color.getRed(), color.getGreen(), color.getBlue()));
            light.setLifetime(intensity * 0.001f);
            light.setSize(ship.getCollisionRadius() * 0.5f);
            light.setIntensity(intensity * 0.05f * (color.getAlpha() / 255f));
            LightShader.addLight(light);

        } else {
            color = new Color(color.getRed(), color.getGreen(), color.getBlue(), 25);
            ship.setJitter(ship, color, intensity * 20f, copies, 0, 0);
        }
        ship.setJitterUnder(ship, color, intensity, copies * 4, jitterDistMin, jitterDistMax);
    }


    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.getMutableStats();
        if (check > 0.0F && --check < 1.0F) {
            ship.getVariant().removeMod(ERROR);
        }

        ship.getHullSpec().getBaseHullId();
        checkAndRemoveBlockedMods(ship, BLOCKED_OMNI);
        checkAndRemoveBlockedMods(ship, BLOCKED_OTHER);
        checkAndRemoveBlockedMods(ship, BLOCKED_OTHER_PLAYER_ONLY);
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        if (!ship.isAlive()) return;
        
        // 添加监听器注册逻辑
        if (!ship.hasListenerOfClass(CrusadersPlatingHitListener.class)) {
            ship.addListener(new CrusadersPlatingHitListener(ship));
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || !engine.isEntityInPlay(ship)) return;

        // 初始化部分（合并两个初始化逻辑）
        if (ship.getCustomData().get("crusaders_fireonce") == null) {
            ship.setCustomData("crusaders_fireonce", true);
            
            ship.setCustomData(PROVIDER_KEY, new CrusadersRequestProvider(ship));
            engine.addPlugin(new BaseEveryFrameCombatPlugin() {
				@Override
				public void advance(float amount, List<InputEventAPI> events) {
					var provider = (CrusadersRequestProvider) ship.getCustomData().get(PROVIDER_KEY);
			        CrusadersPlateRenderer.getInstance().submit(ship, provider.genRequest());
				}
			});
            
            // 初始化护盾角度记录
            ShipHullSpecAPI hullSpec = ship.getHullSpec();
            if (hullSpec != null) {
                ShipHullSpecAPI.ShieldSpecAPI shieldSpec = hullSpec.getShieldSpec();
                if (shieldSpec != null) {
                    float baseShieldArc = shieldSpec.getArc();
                    ship.setCustomData("crusaders_base_shield_arc", baseShieldArc);
                }
            }

            // 初始化镀层系统
            ship.setCustomData("crusaders_currenthpplating",
                    ship.getMutableStats().getFluxCapacity().getModifiedValue() * mag.get(ship.getHullSize()));
            ship.setCustomData("crusaders_maxhpplating",
                    ship.getMutableStats().getFluxCapacity().getModifiedValue() * mag.get(ship.getHullSize()));
            ship.setCustomData("crusaders_recentlydamaged", false);
            ship.setCustomData("crusaders_damagedbybeam", false);
            ship.setCustomData("crusaders_interval", new IntervalUtil(ARMOR_RESTORE_DELAY, ARMOR_RESTORE_DELAY));
            ship.setCustomData("crusaders_shutdown", false);
            ship.setCustomData("crusaders_lastdamagedtime", 0f);
        }
        
        // 护盾角度限制逻辑（保持独立）
        ShieldAPI shield = ship.getShield();
        if (shield != null && shield.isOn()) {
            Object arcData = ship.getCustomData().get("crusaders_base_shield_arc");
            if (arcData instanceof Float) {
                float maxArc = (Float) arcData;
                float currentArc = shield.getArc();

                if (currentArc > maxArc) {
                    ship.getShield().setArc(maxArc);
                }
            }
        }

        // 镀层系统逻辑（保持完整）
        if (ship.getCustomData().get("crusaders_currenthpplating") != null) {
            float currenthp = (Float) ship.getCustomData().get("crusaders_currenthpplating");
            boolean shutdown = (Boolean) ship.getCustomData().get("crusaders_shutdown");
            float maxhp = (Float) ship.getCustomData().get("crusaders_maxhpplating");

            // 新增排散恢复逻辑
            if (ship.getFluxTracker().isVenting()) {
                // 获取当前flux值
                float currentFlux = ship.getFluxTracker().getCurrFlux();

                // 初始化/获取上一帧flux记录
                if (!ship.getCustomData().containsKey("crusaders_last_flux")) {
                    ship.setCustomData("crusaders_last_flux", currentFlux);
                }
                float lastFlux = (Float) ship.getCustomData().get("crusaders_last_flux");

                // 计算实际排散量（考虑软上限）
                float fluxVented = Math.max(lastFlux - currentFlux, 0);

                // 应用镀层恢复
                if (fluxVented > 0) {
                    float recoveryAmount = fluxVented * FLUX_VENT_RECOVERY_RATIO;
                    currenthp = Math.min(currenthp + recoveryAmount, maxhp);
                    ship.setCustomData("crusaders_currenthpplating", currenthp);

                    // 生成可视化反馈
//                    if (engine.getPlayerShip() == ship) {
//                        engine.addFloatingText(
//                                ship.getLocation(),
//                                "镀层恢复 +" + Math.round(recoveryAmount),
//                                20f,
//                                Color.GREEN,
//                                ship,
//                                1f,
//                                0.5f
//                        );
//                    }

                }

                // 更新flux记录
                ship.setCustomData("crusaders_last_flux", currentFlux);
            } else {
                ship.getCustomData().remove("crusaders_last_flux");
            }

            if(ship.getVariant().hasHullMod("PhaseDefenseUnit")){
                float rec_def = AbsorptionData.fluxToAdd *0.0005f;
                if (rec_def > 0) {
                    currenthp = Math.min(currenthp + rec_def, maxhp);
                    ship.setCustomData("crusaders_currenthpplating", currenthp);
                    //if (engine.getPlayerShip() == ship) {
                        //engine.addFloatingText(
                               // ship.getLocation(),
                               // "镀层恢复 +" + Math.round(rec_def),
                               // 20f,
                               // Color.GREEN,
                               // ship,
                               // 1f,
                               // 0.5f
                        //);
                    //}
                }
            }

            if(ship.getVariant().hasHullMod("WeaponOverLoad")) {
                WeaponOverLoad.WeaponFluxData fluxData =
                        (WeaponOverLoad.WeaponFluxData) ship.getCustomData().get(
                                WeaponOverLoad.FLUX_DATA_KEY);

                if (fluxData != null && fluxData.currentFluxToAdd > 0) {
                    float extraflu = fluxData.currentFluxToAdd*0.015f;

                    // 重置数据防止重复使用
                    fluxData.resetFrameData();

                    // 应用镀层恢复
                    currenthp = Math.min(currenthp + extraflu, maxhp);
                    ship.setCustomData("crusaders_currenthpplating", currenthp);

                    // 视觉反馈
                    //if (engine.getPlayerShip() == ship) {
                       //engine.addFloatingText(
                               // ship.getLocation(),
                                //"武器过载恢复 +" + Math.round(extraflu),
                               // 20f,
                                //new Color(100, 255, 255), // 青色
                               // ship,
                               // 1f,
                               // 0.5f
                       // );
                   // }
                }
            }

            float mult = ship.getFluxTracker().isOverloadedOrVenting() ? 0.5f : 1f;
            mult *= Math.min(ship.getCurrentCR() / LOW_CR_THRESHOLD, 1);
            MutableShipStatsAPI stats = ship.getMutableStats();

            if (!shutdown && currenthp <= 0f) {
                applyArmorGlow(ship, Color.red, 0.09f, 1, 0.1f, 0.1f);

                ship.setCustomData("crusaders_shutdown", true);
                ship.setCustomData("crusaders_currenthpplating", 0f);
                ship.getWing();
                 Global.getSoundPlayer().playSound("cr_phaseplatingbreak", 0.7f, 0.5f, ship.getLocation(), ship.getVelocity());
            }

            if (!shutdown && ship.getFluxTracker().isOverloaded()) {
                ship.setCustomData("crusaders_currenthpplating", currenthp - maxhp * OVERLOAD_DEGRADE * amount);
            }

            if ((!(Boolean) ship.getCustomData().get("crusaders_recentlydamaged") || shutdown) && !ship.getFluxTracker().isOverloadedOrVenting()) {
                float hptoadd = Math.min(currenthp + ((RECOVERY) * amount * maxhp), maxhp);
                ship.setCustomData("crusaders_currenthpplating", hptoadd);

                if (shutdown && currenthp >= maxhp * THRESHOLD_RECOVERY) {
                    applyArmorGlow(ship, Color.white, 0.07f, 1, 0.1f, 0.1f);
                    ship.setCustomData("crusaders_shutdown", false);
                    ship.getWing();
                     Global.getSoundPlayer().playSound("cr_phaseplatingactivate", 1, 0.5f, ship.getLocation(), ship.getVelocity());
                }
            }

            ShipAPI playerShip = engine.getPlayerShip();
            if (playerShip != null && playerShip == ship) {
                Vector2f offset = null;
                if (ship.getPhaseCloak() != null && !ship.getHullSpec().isPhase()) {
                    offset = new Vector2f(0, 14);
                }
                if (!shutdown && !ship.getFluxTracker().isOverloaded()) {
                    MagicUIHelper.drawInterfaceStatusBar(ship, currenthp / maxhp, Misc.getPositiveHighlightColor(), Misc.getPositiveHighlightColor(), currenthp / maxhp, "PLATES", Math.round(currenthp), offset);
                } else {
                    MagicUIHelper.drawInterfaceStatusBar(ship, currenthp / maxhp, Misc.getNegativeHighlightColor(), Misc.getNegativeHighlightColor(), currenthp / maxhp, "PLATES", Math.round(currenthp), offset);
                }
            }

            if (!shutdown) {
                stats.getEffectiveArmorBonus().modifyFlat("crusadersNeutroniumPlating", currenthp * mult);
                stats.getHullDamageTakenMult().modifyMult("crusadersNeutroniumPlating", HULL_DAMAGE_TAKEN_MULT_WHILE_ACTIVE * mult);
                //stats.getWeaponDamageTakenMult().modifyMult("crusadersNeutroniumPlating", ENGINE_AND_WEAPON_DAMAGE_TAKEN_MULT_WHILE_ACTIVE * mult);
                stats.getEngineDamageTakenMult().modifyMult("crusadersNeutroniumPlating", ENGINE_AND_WEAPON_DAMAGE_TAKEN_MULT_WHILE_ACTIVE * mult);
                stats.getEmpDamageTakenMult().modifyMult("crusadersNeutroniumPlating", EMP_DAMAGE_TAKEN_MULT * mult);
            } else {
                stats.getEffectiveArmorBonus().unmodifyFlat("crusadersNeutroniumPlating");
                stats.getHullDamageTakenMult().unmodifyMult("crusadersNeutroniumPlating");
                stats.getWeaponDamageTakenMult().unmodifyMult("crusadersNeutroniumPlating");
                stats.getEngineDamageTakenMult().unmodifyMult("crusadersNeutroniumPlating");
                stats.getEmpDamageTakenMult().unmodifyMult("crusadersNeutroniumPlating");
                if (ship.getAIFlags() != null) {
                    ship.getAIFlags().setFlag(ShipwideAIFlags.AIFlags.NEEDS_HELP, amount);
                }
            }

            if (!shutdown && currenthp > 0 && (Boolean) ship.getCustomData().get("crusaders_recentlydamaged")) {
            }

            ShipAPI target = playerShip != null ? playerShip.getShipTarget() : null;
            if (target != null && ship == target) {
                Color color = Misc.getPositiveHighlightColor();
                if (ship.getOwner() != engine.getPlayerShip().getOwner()) {
                    color = Misc.getNegativeHighlightColor();
                    MagicUI.drawHUDStatusBar(engine.getPlayerShip(), currenthp / maxhp, color, color, currenthp / maxhp, "PLATES", "TARGET", true);
                } else {
                    MagicUI.drawHUDStatusBar(engine.getPlayerShip(), currenthp / maxhp, color, color, currenthp / maxhp, "PLATES", "TARGET", true);
                }
            }
        }

        if (ship.getCustomData().get("crusaders_interval") != null) {
            IntervalUtil shipcurrentinterval = (IntervalUtil) ship.getCustomData().get("crusaders_interval");

            if ((Boolean) ship.getCustomData().get("crusaders_recentlydamaged")) {
                shipcurrentinterval.advance(amount);
            }

            if (shipcurrentinterval.intervalElapsed()) {
                ship.setCustomData("crusaders_recentlydamaged", false);
            } else {

                boolean damagedByBeam = (Boolean) ship.getCustomData().get("crusaders_damagedbybeam");
                float lastDamageTime = (Float) ship.getCustomData().get("crusaders_lastdamagedtime");
                float timeSinceDamageTaken = Global.getCombatEngine().getTotalElapsedTime(false) - lastDamageTime;

                if (damagedByBeam) { // Workaround to play beam damage sound loop.
                    if (timeSinceDamageTaken > 0.15f) {
                        ship.setCustomData("crusaders_damagedbybeam", false);
                    } else {
                        Global.getSoundPlayer().playLoop("cr_phaseplatingloop", ship, 1f, 1f, ship.getLocation(), ship.getVelocity());
                    }
                }

                if (ShaderLibExists
                        && timeSinceDamageTaken <= 0.25f) {

                    float currenthp = (Float) ship.getCustomData().get("crusaders_currenthpplating");
                    float maxhp = (Float) ship.getCustomData().get("crusaders_maxhpplating");
                    Color colorDamaged = getDamagedShieldColor(currenthp, maxhp);
                    applyArmorGlow(ship, colorDamaged, 0.02f, 1, 0.1f, 0.1f);
                }
            }
        }
    }

    public boolean isApplicableToShip(ShipAPI ship) {
        // Allows any ship with a crusaders hull id
        String hullId = ship.getHullSpec().getHullId();
        return hullId.startsWith("crusaders_")
                || hullId.startsWith("rs_")
                || hullId.startsWith("vow_");
    }

}
