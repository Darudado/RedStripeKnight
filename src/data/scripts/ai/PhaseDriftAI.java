package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipCommand;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

import static data.scripts.utils.RSUtil.BeamweaponFiring;
import static data.scripts.utils.RSUtil.isTreatenedbyProjectile;

public class PhaseDriftAI implements ShipSystemAIScript {
    private ShipAPI ship;
    private ShipSystemAPI system;
    private CombatEngineAPI engine;
    private boolean runOnce = false;
    private boolean SheildRunOnce = false;
    private float checkAgain = 0.25f;
    private float delay = 0f, timer = 0f;
    private boolean SheildDown_flag = false;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine != Global.getCombatEngine()) {
            this.engine = Global.getCombatEngine();
        }
        if (engine.isPaused() || ship.getShipAI() == null) {
            return;
        }

        // 检查舰船是否有护盾
        boolean hasShield = ship.getShield() != null;

        if (!runOnce) {
            runOnce = true;
            delay = 1.0f;
        }

        timer += amount;

        if (SheildDown_flag) {
            // 如果舰船没有护盾，直接跳过护盾相关逻辑
            if (hasShield) {
                if (ship.getShield().isOn()) {
                    ship.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, 0);
                }
                if (ship.getShield().isOff()) {
                    ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK);
                }
            }

            // 没有护盾或者护盾已关闭时，可以使用系统
            if (!SheildRunOnce && AIUtils.canUseSystemThisFrame(ship) &&
                    (!hasShield || (hasShield && ship.getShield().isOff()))) {
                ship.useSystem();
                SheildRunOnce = true;
            }

            if (ship.getSystem().canBeActivated() && SheildRunOnce) {
                SheildDown_flag = false;
                SheildRunOnce = false;
            }
        }

        if (timer > (delay + checkAgain) && !SheildDown_flag) {
            timer = 0;
            checkAgain = 0f;

            if (!system.isActive()
                    && AIUtils.getNearbyEnemies(ship, 2500).isEmpty()
                    && ship.getSystem().getAmmo() > ship.getSystem().getMaxAmmo() - 1) {
                if (AIUtils.canUseSystemThisFrame(ship)) {
                    ship.useSystem();
                    checkAgain = 1.0f;
                }
            } else if (!system.isActive()) {
                boolean Projectile_safe = !isTreatenedbyProjectile(ship, 1000);
                boolean Beam_safe = !BeamweaponFiring(ship, 2000);

                if (AIUtils.canUseSystemThisFrame(ship)) {
                    float hardfluxlevel = ship.getFluxTracker().getHardFlux() / ship.getFluxTracker().getMaxFlux();
                    float currsoftflux = ship.getFluxTracker().getCurrFlux() - ship.getFluxTracker().getHardFlux();

                    if (currsoftflux >= 3000f) {
                        ship.useSystem();
                        checkAgain = 1f;
                    } else if (hardfluxlevel <= 0.8f && hardfluxlevel >= 0.2f && Projectile_safe && Beam_safe) {
                        // 只有在有护盾时才触发关盾时流
                        if (hasShield) {
                            SheildDown_flag = true;
                        } else {
                            // 没有护盾时直接使用时流系统
                            ship.useSystem();
                        }
                        checkAgain = 1f;
                    } else if (hardfluxlevel > 0.8f) {
                        ship.giveCommand(ShipCommand.ACCELERATE_BACKWARDS, null, 0);
                        if (ship.getHitpoints() > ship.getMaxHitpoints() * 0.25 && Beam_safe) {
                            // 只有在有护盾时才可能触发关盾
                            if (hasShield) {
                                SheildDown_flag = true;
                            } else {
                                // 没有护盾时直接使用时流系统撤退
                                ship.useSystem();
                            }
                        }
                        checkAgain = 0f;
                    }
                }
            }
        }
    }
}