package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MissileRenderDataAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

public class HiddenMissles extends BaseHullMod {
    public HiddenMissles() {
    }

    public void advanceInCombat(ShipAPI ship, float amount) {
        for(WeaponAPI w : ship.getAllWeapons()) {
            if (w.getSlot().isHardpoint() && !w.getSlot().isStationModule() && !w.getSlot().isDecorative() && Math.abs(w.getSlot().getArc()) <= 10.0F) {
                if (w.getSprite() != null) {
                    w.getSprite().setSize(0.0F, 0.0F);
                }

                if (w.getBarrelSpriteAPI() != null) {
                    w.getBarrelSpriteAPI().setSize(0.0F, 0.0F);
                }

                if (w.getMissileRenderData() != null && !w.getMissileRenderData().isEmpty()) {
                    for(MissileRenderDataAPI data : w.getMissileRenderData()) {
                        data.getSprite().setSize(0.0F, 0.0F);
                    }
                }

                if (w.getGlowSpriteAPI() != null) {
                    w.getGlowSpriteAPI().setSize(0.0F, 0.0F);
                }

                if (w.getUnderSpriteAPI() != null) {
                    w.getUnderSpriteAPI().setSize(0.0F, 0.0F);
                }

                w.ensureClonedSpec();
                ship.getLargeHardpointCover().setSize(0.0F, 0.0F);
                ship.getMediumHardpointCover().setSize(0.0F, 0.0F);
                ship.getSmallHardpointCover().setSize(0.0F, 0.0F);
            }
        }

    }
}