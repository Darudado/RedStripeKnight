package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import data.subsystems.YukikazeLockedTarget;
import org.magiclib.subsystems.MagicSubsystemsManager;

public class YukikazeTargeting extends BaseHullMod{
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id){
        try {
            YukikazeLockedTarget Subsystem = new YukikazeLockedTarget(ship , ship.getSystem());
            // 注册到MagicLib子系统管理器
            MagicSubsystemsManager.addSubsystemToShip(ship, Subsystem);
        } catch (Exception e) {
            // 记录错误日志，便于调试
            Global.getLogger(this.getClass()).error("Failed to add subsystem:" + e.getMessage(), e);
        }


    }
}