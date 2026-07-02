package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import java.awt.*;

import static data.scripts.utils.RSUtil.Setjitter;
import static data.scripts.utils.RSUtil.moveVec;

public class ViaDivinaLED extends BaseHullMod {
    private final SpriteAPI[] sprites = {
            Global.getSettings().getSprite("misc", "cr_viadivina_LED00"),
            Global.getSettings().getSprite("misc", "cr_viadivina_LED01"),
            Global.getSettings().getSprite("misc", "cr_viadivina_LED02")
    };

    public void advanceInCombat(ShipAPI ship, float effectLevel) {
        if (ship.getSystem() == null) return;

        boolean systemActive = ship.getSystem().isActive();
        if (!systemActive) return;  // 系统未激活时直接返回

        // 修复1：添加边界检查防止数组越界[1,2,4](@ref)
        int num = Math.min((int)(effectLevel * 3f), sprites.length - 1);
        num = Math.max(num, 0);  // 确保索引不小于0

        if (ship.getSystem().isChargeup()) {
            // 修复2：使用安全索引num代替硬编码索引
            Setjitter(sprites[num],
                    moveVec(ship.getLocation(), 0f, 88f, ship.getFacing()),
                    ship.getFacing(),
                    new Color(206, 32, 9, 216),
                    0.2f, 5, 6f);
        }

        // 修复3：添加数组边界检查[2,5](@ref)
        if (effectLevel >= 0f && sprites.length > 1) {
            Setjitter(sprites[1],
                    moveVec(ship.getLocation(), 0f, 0f, ship.getFacing()),
                    ship.getFacing(),
                    new Color(175, 15, 15, 225),
                    0.8f, 3, 2f);
        }

        if (ship.getSystem().isCoolingDown() && sprites.length > 2) {
            // 修复4：添加数组长度检查
            Setjitter(sprites[2],
                    moveVec(ship.getLocation(), 0f, 0f, ship.getFacing()),
                    ship.getFacing(),
                    new Color(175, 15, 15, 115),
                    0.8f, 3, 2f);
        }
    }
}