package data.scripts.skills;

import com.fs.starfarer.api.characters.AfterShipCreationSkillEffect;
import com.fs.starfarer.api.characters.DescriptionSkillEffect;
import com.fs.starfarer.api.characters.LevelBasedEffect;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class RS_PolarizedArmor {
    public static float EFFECTIVE_ARMOR_BONUS = 50.0F;
    public static float EMP_BONUS_PERCENT = 50.0F;
    public static float VENT_RATE_BONUS = 25.0F;
    public static float NON_SHIELD_FLUX_LEVEL = 50.0F;

    public RS_PolarizedArmor() {
    }

    public static class ArmorLevel0 implements DescriptionSkillEffect {
        public ArmorLevel0() {
        }

        public String getString() {
            return "*Ships without a shield or a phase cloak are treated as always having " + (int) NON_SHIELD_FLUX_LEVEL + "% hard flux.";
        }

        public Color[] getHighlightColors() {
            Color h;
            h = Misc.getDarkHighlightColor();
            return new Color[]{h};
        }

        public String[] getHighlights() {
            return new String[]{(int) NON_SHIELD_FLUX_LEVEL + "%"};
        }

        public Color getTextColor() {
            return null;
        }
    }

    public static class ArmorLevel1 implements ShipSkillEffect {
        public ArmorLevel1() {
        }

        public void apply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id, float level) {
            stats.getMaxArmorDamageReduction().modifyFlat(id, 0.1F);
        }

        public void unapply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id) {
            stats.getMaxArmorDamageReduction().unmodify(id);
        }

        public String getEffectDescription(float level) {
            return "最大装甲减伤从 85% 上升至 95%";
        }

        public String getEffectPerLevelDescription() {
            return null;
        }

        public LevelBasedEffect.ScopeDescription getScopeDescription() {
            return ScopeDescription.PILOTED_SHIP;
        }
    }

    public static class ArmorLevel2 implements AfterShipCreationSkillEffect {
        public ArmorLevel2() {
        }

        public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
            ship.addListener(new RSPolarizedArmorEffectMod(ship, id));
        }

        public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {
            MutableShipStatsAPI stats = ship.getMutableStats();
            ship.removeListenerOfClass(RSPolarizedArmorEffectMod.class);
            stats.getEffectiveArmorBonus().unmodify(id);
            stats.getEmpDamageTakenMult().unmodify(id);
        }

        public void apply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id, float level) {
        }

        public void unapply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id) {
        }

        public String getEffectDescription(float level) {
            return "基于舰船的辐能比例，可以计算最高 +" + (int) EFFECTIVE_ARMOR_BONUS + "% 的额外装甲减伤";
        }

        public String getEffectPerLevelDescription() {
            return null;
        }

        public LevelBasedEffect.ScopeDescription getScopeDescription() {
            return LevelBasedEffect.ScopeDescription.PILOTED_SHIP;
        }
    }

    public static class ArmorLevel3 implements ShipSkillEffect {
        public ArmorLevel3() {
        }

        public void apply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id, float level) {
        }

        public void unapply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id) {
        }

        public String getEffectDescription(float level) {
            return "舰船受到的EMP伤害最高减免" + Math.round(EMP_BONUS_PERCENT) + "%, 基于舰船目前的辐能比例";
        }

        public String getEffectPerLevelDescription() {
            return null;
        }

        public LevelBasedEffect.ScopeDescription getScopeDescription() {
            return ScopeDescription.PILOTED_SHIP;
        }
    }

    public static class ArmorLevel4 implements ShipSkillEffect {
        public ArmorLevel4() {
        }

        public void apply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id, float level) {
            stats.getVentRateMult().modifyPercent(id, VENT_RATE_BONUS);
        }

        public void unapply(MutableShipStatsAPI stats, ShipAPI.HullSize hullSize, String id) {
            stats.getVentRateMult().unmodify(id);
        }

        public String getEffectDescription(float level) {
            return "+" + (int) VENT_RATE_BONUS + "% 的强制排散速率";
        }

        public String getEffectPerLevelDescription() {
            return null;
        }

        public LevelBasedEffect.ScopeDescription getScopeDescription() {
            return ScopeDescription.PILOTED_SHIP;
        }
    }

    public static class RSPolarizedArmorEffectMod implements DamageTakenModifier, AdvanceableListener {
        protected ShipAPI ship;
        protected String id;

        public RSPolarizedArmorEffectMod(ShipAPI ship, String id) {
            this.ship = ship;
            this.id = id;
        }

        public void advance(float amount) {
            MutableShipStatsAPI stats = this.ship.getMutableStats();
            float fluxLevel = this.ship.getFluxLevel();
            if (this.ship.getShield() == null && !this.ship.getHullSpec().isPhase() && (this.ship.getPhaseCloak() == null || !this.ship.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.PHASE))) {
                fluxLevel = NON_SHIELD_FLUX_LEVEL * 0.01F;
            }

            float armorBonus = EFFECTIVE_ARMOR_BONUS * fluxLevel;
            float empBonus = EMP_BONUS_PERCENT * fluxLevel;
            stats.getEffectiveArmorBonus().modifyPercent(this.id, armorBonus);
            stats.getEmpDamageTakenMult().modifyMult(this.id, 1.0F - empBonus * 0.01F);
            Color c = this.ship.getSpriteAPI().getAverageColor();
            c = Misc.setAlpha(c, 127);
            float b = 0.0F;
            if (fluxLevel > 0.75F) {
                b = (fluxLevel - 0.75F) / 0.25F;
            }

            if (b > 0.0F) {
                this.ship.setJitter(this, c, 1.0F * fluxLevel * b, 1, 0.0F);
            }

        }

        public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
            return null;
        }
    }
}
