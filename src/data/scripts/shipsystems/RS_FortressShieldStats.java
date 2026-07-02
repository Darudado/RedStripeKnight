package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class RS_FortressShieldStats extends BaseShipSystemScript {
    public static float DAMAGE_MULT = 0.85f;

    private RS_shieldDamageTakenListener listener = null;

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel){
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship != null) {
            if (listener == null) {
                listener = new RS_shieldDamageTakenListener(ship);
                ship.addListener(listener);
            }
            if (ship.getVariant().hasHullMod("WeaponOverLoad")) {
                stats.getShieldDamageTakenMult().modifyMult(id, 1f - (DAMAGE_MULT/5) * effectLevel);
                stats.getShieldUpkeepMult().modifyMult(id , 0.25f);
                stats.getBallisticWeaponFluxCostMod().modifyMult(id , 0.5f);
                stats.getEnergyWeaponFluxCostMod().modifyMult(id , 0.5f);
            } else if (ship.getVariant().hasHullMod("PolariphaseDrive")) {
                stats.getShieldDamageTakenMult().modifyMult(id, 1f - (DAMAGE_MULT/3) * effectLevel);
                stats.getTimeMult().modifyMult(id ,1.75f);
                for (WeaponAPI weapons : ship.getAllWeapons()) {
                    weapons.setForceNoFireOneFrame(true);
                }
            } else if (ship.getVariant().hasHullMod("PhaseDefenseUnit")) {
                stats.getShieldDamageTakenMult().modifyMult(id, 1f - DAMAGE_MULT/2 * effectLevel);
                stats.getShieldUpkeepMult().modifyMult(id , 0.5f);
                stats.getFluxDissipation().modifyPercent(id, 50.0F);
                stats.getHardFluxDissipationFraction().modifyPercent(id, 15.0F);
                for (WeaponAPI weapons : ship.getAllWeapons()) {
                    weapons.setForceNoFireOneFrame(true);
                }
            } else {
                for (WeaponAPI weapons : ship.getAllWeapons()) {
                    weapons.setForceNoFireOneFrame(true);
                }
                stats.getShieldDamageTakenMult().modifyMult(id, 1f - DAMAGE_MULT * effectLevel);
                stats.getShieldUpkeepMult().modifyMult(id, 0f);
                stats.getMaxSpeed().modifyFlat(id , 15f);
            }
        }
    }

    public void unapply(MutableShipStatsAPI stats, String id){
        stats.getShieldDamageTakenMult().unmodify(id);
        stats.getShieldUpkeepMult().unmodify(id);
        stats.getMaxSpeed().unmodify(id);

        stats.getEnergyWeaponDamageMult().unmodify(id);
        stats.getBallisticWeaponDamageMult().unmodify(id);
        stats.getEnergyWeaponFluxCostMod().unmodify(id);
        stats.getBallisticWeaponFluxCostMod().unmodify(id);

        stats.getTimeMult().unmodify(id);

        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship != null) {
            ship.removeListenerOfClass(RS_shieldDamageTakenListener.class);
        }

    }

    public static class RS_shieldDamageTakenListener implements DamageTakenModifier {
        protected final ShipAPI tship;
        public RS_shieldDamageTakenListener(ShipAPI tship)
        {
            this.tship = tship;
        }

        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
            if (damage !=null && damage.getStats() !=null && damage.getStats().getEntity() !=null && shieldHit) {
                CombatEngineAPI engine = Global.getCombatEngine();
                Vector2f goldenPoint = MathUtils.getRandomPointInCircle(tship.getLocation(), tship.getShield().getRadius() - 20f);
                engine.addSmoothParticle(goldenPoint, new Vector2f(), MathUtils.getRandomNumberInRange(8f, 20f), 1f, MathUtils.getRandomNumberInRange(0.4f, 1f), new Color(175, 15, 15, 150));
                if ((float) Math.random() > 0.75f) {
                    engine.spawnEmpArc(tship, goldenPoint, null, new SimpleEntity(point), DamageType.ENERGY, 1500f, 1500f, 10000f, null, 0.2f, new Color(170, 35, 35, 255), new Color(175, 15, 15, 255));
                }
            }
            return null;
        }
    }
}