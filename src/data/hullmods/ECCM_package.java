package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import data.subsystems.BansheeEccm;
import org.magiclib.subsystems.MagicSubsystemsManager;

public class ECCM_package extends BaseHullMod {
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id){
        try {
            BansheeEccm Subsystem = new BansheeEccm(ship);
            // 注册到MagicLib子系统管理器
            MagicSubsystemsManager.addSubsystemToShip(ship, Subsystem);
        } catch (Exception e) {
            // 记录错误日志，便于调试
            Global.getLogger(this.getClass()).error("Failed to add subsystem:" + e.getMessage(), e);
        }

    }
}