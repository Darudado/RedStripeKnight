package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.loading.WeaponGroupSpec;
import com.fs.starfarer.api.loading.WeaponGroupType;


import com.fs.starfarer.combat.entities.Ship;
import com.fs.starfarer.combat.systems.WeaponGroup;

import java.util.EnumSet;

public class RX78_GP03D_ModuleMissileContro_Abandon implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {
    boolean init = false;
    private ShipAPI child = null;
    boolean hasChild = false;

    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || engine.isPaused()) {
            return;
        }
        if (!hasChild || weapon.isDisabled()) {
            return;
        }
        if (child == null || !child.isAlive()) {
            weapon.disable(true);
            return;
        }
        if (!init) {
            //如果挂了此代码的控制武器是安装在装饰武器槽上，需要将武塞入武器组才能被玩家操控
            //也许可以用船插在beforeShipCreat里面为variant直接添加包含特定武器槽id的武器组，但是我没测试
            init(weapon);
            init = true;
        }


    }

    boolean fireCommand = false;

    //仅在模块领取到一次攻击指令后重置指令为不攻击（这种方法只适合1武器对应1模块）
    public boolean getFireCommand() {
        if (fireCommand) {
            fireCommand = false;
            return true;
        }
        return false;
    }

    public void setChildModule(ShipAPI ship) {
        CombatEngineAPI engine = Global.getCombatEngine();

        if (engine == null) return;

        WeaponAPI ownWeapon = null;
        for (WeaponAPI w : ship.getAllWeapons()) {
            if (w.getEffectPlugin() == this) {
                ownWeapon = w;
                break;
            }
        }

        if (ownWeapon == null) return;

        String id = ownWeapon.getId();

        String targetChildId;
        if (id.contains("_Left")) {
            targetChildId = "rs_RX78_GP03_D_Leftmodule";
        } else if (id.contains("_Right")) {
            targetChildId = "rs_RX78_GP03_D_Rightmodule";
        } else {
            return; // 不支持的 ID
        }

        // 遍历战场中所有实体，找匹配 ID 的子舰
        for (ShipAPI potentialChild : engine.getShips()) {
            if (potentialChild.isHulk() || !potentialChild.isAlive()) continue;
            if (potentialChild.getId() != null && potentialChild.getId().equals(targetChildId)) {
                this.child = potentialChild;
                this.hasChild = true;
                return;
            }
        }

        // 如果没找到，也可以监听后续 spawn（可选）
        // 但此处先标记为 hasChild=false，等待下一帧再试？
        this.hasChild = false;
        this.child = null;
    }

    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        engine.removeEntity(projectile);
        fireCommand = true;
    }
//将装饰槽的武器在战斗中加入武器组

    void init(WeaponAPI weapon) {
        if (weapon == null || weapon.getShip() == null || weapon.getShip().isHulk() || weapon.getShip().isShuttlePod() || !weapon.getShip().isAlive()) {
            return;
        }
        Ship ship = (Ship) weapon.getShip();
        if (ship.getWeaponGroupFor(weapon) == null) {
            if (ship.getWeaponGroupsCopy().size() < 7) {
                boolean added = false;
                for (WeaponGroupAPI group : ship.getWeaponGroupsCopy()) {
                    if (!added) {
                        for (WeaponAPI w : group.getWeaponsCopy()) {
                            if (w.getEffectPlugin() instanceof RX78_GP03D_ModuleMissileControl_Left1) {
                                group.addWeaponAPI(weapon);
                                added = true;
                                break;
                            }
                        }
                    } else {
                        break;
                    }
                }
                if (!added) {
                    WeaponGroup g = new WeaponGroup();
                    g.addWeaponAPI(weapon);
                    g.setType(WeaponGroupType.LINKED);
                    ship.addGroup(g);
                }
            } else {
                boolean added = false;
                for (WeaponGroupAPI group : ship.getWeaponGroupsCopy()) {
                    if (!added) {
                        for (WeaponAPI w : group.getWeaponsCopy()) {
                            if (checkHints(w) && w.getType() != WeaponAPI.WeaponType.MISSILE && w.getSlot().isTurret()) {
                                group.addWeaponAPI(weapon);
                                added = true;
                                break;
                            }
                        }
                    } else {
                        break;
                    }
                }
                if (!added) {
                    WeaponGroupSpec group = ship.getVariant().getWeaponGroups().get(6);
                    group.addSlot(weapon.getSlot().getId());
                }
            }
        }
    }

    boolean checkHints(WeaponAPI weapon) {
        EnumSet<WeaponAPI.AIHints> hints = weapon.getSpec().getAIHints();
        return !hints.contains(WeaponAPI.AIHints.PD) && !hints.contains(WeaponAPI.AIHints.PD_ALSO) && !hints.contains(WeaponAPI.AIHints.PD_ONLY)
                && !hints.contains(WeaponAPI.AIHints.STRIKE) && !hints.contains(WeaponAPI.AIHints.DANGEROUS) && !hints.contains(WeaponAPI.AIHints.GUIDED_POOR)
                && !hints.contains(WeaponAPI.AIHints.DO_NOT_AIM) && !hints.contains(WeaponAPI.AIHints.HEATSEEKER);
    }

}