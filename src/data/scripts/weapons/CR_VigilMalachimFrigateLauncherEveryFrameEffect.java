package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.ai.Launchai;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;
import java.util.function.Function;

public class CR_VigilMalachimFrigateLauncherEveryFrameEffect implements EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {

    // 状态跟踪数据结构
    private static class ProjectileState {
        final float fireTime;
        final Vector2f parentVelocity;
        final int owner;

        ProjectileState(float fireTime, Vector2f parentVelocity, int owner) {
            this.fireTime = fireTime;
            this.parentVelocity = parentVelocity;
            this.owner = owner;
        }
    }

    // 使用LinkedHashMap保持处理顺序
    private final Map<DamagingProjectileAPI, ProjectileState> delayedProjectiles =
            new LinkedHashMap<>(32, 0.75f, false);

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused()) return;

        float currentTime = engine.getTotalElapsedTime(true);
        Iterator<Map.Entry<DamagingProjectileAPI, ProjectileState>> iter =
                delayedProjectiles.entrySet().iterator();

        while (iter.hasNext()) {
            Map.Entry<DamagingProjectileAPI, ProjectileState> entry = iter.next();
            DamagingProjectileAPI proj = entry.getKey();
            ProjectileState state = entry.getValue();

            // 计算经过时间
            float elapsed = currentTime - state.fireTime;

            if (elapsed >= 2f) {
                // 触发效果
                spawnShipWithTransition(proj, state, engine);

                // 清理实体
                engine.removeEntity(proj);
                iter.remove();
            } else {

            }
        }
    }

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        // 立即记录发射状态
        delayedProjectiles.put(projectile, new ProjectileState(
                engine.getTotalElapsedTime(true),
                new Vector2f(weapon.getShip().getVelocity()),
                projectile.getOwner()
        ));


    }

    private void spawnShipWithTransition(DamagingProjectileAPI proj,
                                         ProjectileState state, CombatEngineAPI engine) {
        // 播放过渡动画
        playTransitionAnimation(proj.getLocation(), engine);

        // 生成舰船实体
        ShipAPI ship = engine.getFleetManager(state.owner).spawnShipOrWing(
                "cr_vigimalachim_variant",
                proj.getLocation(),
                proj.getFacing()
        );

        // 设置组合速度
        Vector2f combinedVel = Vector2f.add(
                proj.getVelocity(),
                state.parentVelocity,
                null
        );
        ship.getVelocity().set(combinedVel);

        // 应用AI
        ship.setShipAI(new Launchai(ship));
    }

    private void playTransitionAnimation(Vector2f location, CombatEngineAPI engine) {
        // 修正动画参数
        String[] frames = {"VigilMalachim_dec", "VigilMalachim"};
        Color[] colors = {new Color(255, 200, 50, 150), new Color(255, 100, 50, 200)};

        for (int i = 0; i < frames.length; i++) {
            engine.addHitParticle(
                    location,
                    new Vector2f(),
                    80f * (i + 1),
                    1f,
                    0.5f,
                    colors[i]
                    // 使用实际的sprite ID
            );
        }
    }

//    public class VigilMalachimFrigateLauncherSteamRenderer extends BaseCombatLayeredRenderingPlugin {
//        private final CR_VigilMalachimFrigateLauncherData data;
//
//        private int shader = SilentShaderCore.steamProgram;
//        private static boolean inited;
//        private static int timeLoc;
//        private static int progressLoc;
//        private static int dissipationLoc;
//
//        private SilentTimer timer = new SilentTimer(CR_VigilMalachimFrigateLauncherDeckStats.ACCELERATING_DURATION, 0f, 3f);
//
//        public VigilMalachimFrigateLauncherSteamRenderer(CR_VigilMalachimFrigateLauncherData data) {
//            this.data = data;
//        }
//
//        @Override
//        public void advance(float amount) {
//            if (Global.getCombatEngine().isPaused()) return;
//            timer.advance(amount);
//        }
//
//        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
//            if (layer != CombatEngineLayers.CONTRAILS_LAYER) return;
//            if (data.startSlot == null || data.takeOffSlot == null) return;
//
//            Vector2f loc1 = data.startSlot.computePosition(data.source);
//            Vector2f loc2 = data.takeOffSlot.computePosition(data.source);
//
//            float frac = data.elapsed / CR_VigilMalachimFrigateLauncherStats.ACCELERATING_DURATION;
//            frac = CR_VigilMalachimFrigateLauncherStats.ACC_LERP_FUNCTION.apply(frac);
//
//            Vector2f dir = Vector2f.sub(loc2, loc1, new Vector2f());
//            float length = dir.length();
//            if (length < 1f) return;
//            dir.scale(1f / length);
//
//            Vector2f perp = new Vector2f(-dir.y, dir.x);
//
//            float steamWidth = 15f;
//            float sideOffset = 0f;
//            float dissipation = 0f;
//            if (timer.isFadingOut()) {
//                dissipation = 1f - timer.getProgress();
//            }
//
//            GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
//            GL11.glPushMatrix();
//
//            GL20.glUseProgram(shader);
//
//            if (!inited) {
//                inited = true;
//                timeLoc = GL20.glGetUniformLocation(shader, "u_time");
//                progressLoc = GL20.glGetUniformLocation(shader, "u_progress");
//                dissipationLoc = GL20.glGetUniformLocation(shader, "u_dissipation");
//            }
//
//            GL20.glUniform1f(progressLoc, frac);
//            GL20.glUniform1f(dissipationLoc, Math.min(1f, dissipation));
//
//            GL11.glEnable(GL11.GL_BLEND);
//            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
//
//            GL20.glUniform1f(timeLoc, Global.getCombatEngine().getTotalElapsedTime(false));
//            renderSteamQuad(loc1, loc2, perp, sideOffset, steamWidth);
//
//            GL20.glUniform1f(timeLoc, Global.getCombatEngine().getTotalElapsedTime(false) + 10f);
//            renderSteamQuad(loc1, loc2, perp, -sideOffset , -steamWidth);
//
//            GL20.glUseProgram(0);
//            GL11.glPopMatrix();
//            GL11.glPopAttrib();
//        }
//
//        private void renderSteamQuad(Vector2f start, Vector2f end, Vector2f perpDir, float offset, float width) {
//            Vector2f p1 = new Vector2f(start.x + perpDir.x * offset, start.y + perpDir.y * offset);
//            Vector2f p2 = new Vector2f(end.x + perpDir.x * offset, end.y + perpDir.y * offset);
//            Vector2f p3 = new Vector2f(end.x + perpDir.x * (offset + width), end.y + perpDir.y * (offset + width));
//            Vector2f p4 = new Vector2f(start.x + perpDir.x * (offset + width), start.y + perpDir.y * (offset + width));
//
//            GL11.glBegin(GL11.GL_QUADS);
//
//            GL11.glTexCoord2f(0f, 0f);
//            GL11.glVertex2f(p1.x, p1.y);
//
//            GL11.glTexCoord2f(1f, 0f);
//            GL11.glVertex2f(p2.x, p2.y);
//
//            GL11.glTexCoord2f(1f, 1f);
//            GL11.glVertex2f(p3.x, p3.y);
//
//            GL11.glTexCoord2f(0f, 1f);
//            GL11.glVertex2f(p4.x, p4.y);
//
//            GL11.glEnd();
//        }
//
//        @Override
//        public EnumSet<CombatEngineLayers> getActiveLayers() {
//            return EnumSet.of(CombatEngineLayers.CONTRAILS_LAYER);
//        }
//
//        @Override
//        public float getRenderRadius() {
//            return Float.MAX_VALUE;
//        }
//
//        @Override
//        public boolean isExpired() {
//            return timer.isExpired();
//        }
//    }
//
//    public static class CR_VigilMalachimFrigateLauncherData {
//        public ShipAPI source;
//        public WeaponAPI sourceWpn;
//        public WeaponSlotAPI startSlot, takeOffSlot;
//        public String containingVariant;
//        public float elapsed;
//
//        private CR_VigilMalachimFrigateLauncherState state = CR_VigilMalachimFrigateLauncherState.LIFTING;
//
//        public void transferTo(CR_VigilMalachimFrigateLauncherState state) {
//            if (state == this.state) return;
//            if (this.state != null) this.state.transferOut(state, this);
//            if (state != null) state.transferIn(this.state, this);
//            this.state = state;
//        }
//
//        public CR_VigilMalachimFrigateLauncherState getState() {
//            return state;
//        }
//
//    }
//
//    public enum CR_VigilMalachimFrigateLauncherState implements SilentStateLogic<CR_VigilMalachimFrigateLauncherState, CR_VigilMalachimFrigateLauncherData>{
//        NONE {
//
//            public void transferIn(CR_VigilMalachimFrigateLauncherState from, CR_VigilMalachimFrigateLauncherData context) {context.elapsed = 0f;}
//
//
//            public void advance(CR_VigilMalachimFrigateLauncherData context, float amount) {
//                context.elapsed += amount;
//                if (context.elapsed > CR_VigilMalachimFrigateLauncherStats.REFIT_DURATION) context.transferTo(LIFTING);
//            }
//        },
//
//        LIFTING {
//
//            public void transferIn(CR_VigilMalachimFrigateLauncherState from, CR_VigilMalachimFrigateLauncherData context) {context.elapsed = 0f;}
//
//            public void advance(CR_VigilMalachimFrigateLauncherData context, float amount) {
//                context.elapsed += amount;
//                if (context.elapsed > CR_VigilMalachimFrigateLauncherStats.LIFTING_DURATION) context.transferTo(HOLD);
//            }
//        },
//
//        HOLD {
//            public void transferIn(CR_VigilMalachimFrigateLauncherState from, CR_VigilMalachimFrigateLauncherData context) {
//                context.sourceWpn.getAmmoTracker().addOneAmmo();
//            }
//        },
//
//        CHARGING {
//
//            public void transferIn(CR_VigilMalachimFrigateLauncherState from, CR_VigilMalachimFrigateLauncherData context) {context.elapsed = 0f;}
//
//
//            public void advance(CR_VigilMalachimFrigateLauncherData context, float amount) {
//                context.elapsed += amount;
//                if (context.elapsed > CR_VigilMalachimFrigateLauncherStats.CHARGING_DURATION) context.transferTo(ACCELERATING);
//            }
//        },
//
//        ACCELERATING {
//
//            public void transferIn(CR_VigilMalachimFrigateLauncherState from, CR_VigilMalachimFrigateLauncherData context) {
//                context.elapsed = 0f;
//                Global.getCombatEngine().addLayeredRenderingPlugin(new CR_VigilMalachimFrigateLauncherSteamRenderer(context));
//            }
//
//
//            public void advance(CR_VigilMalachimFrigateLauncherData context, float amount) {
//                context.elapsed += amount;
//                if (context.elapsed > CR_VigilMalachimFrigateLauncherStats.ACCELERATING_DURATION) context.transferTo(HAS_TAKEN_OFF);
//            }
//
//
//            public void transferOut(CR_VigilMalachimFrigateLauncherState to, CR_VigilMalachimFrigateLauncherData context) {
//                var manager = Global.getCombatEngine().getFleetManager(context.source.getOwner());
//                manager.setSuppressDeploymentMessages(true);
//                var wing = manager.spawnShipOrWing(
//                        context.containingVariant,
//                        context.takeOffSlot.computePosition(context.source),
//                        context.startSlot.getAngle() + context.source.getFacing(),
//                        CR_VigilMalachimFrigateLauncherStats.TAKEOFF_BURN_DURATION);
//                manager.setSuppressDeploymentMessages(false);
//
//                wing.setCollisionClass(CollisionClass.FIGHTER);
//
//                float x1 = CR_VigilMalachimFrigateLauncherStats.ACC_LERP_FUNCTION.apply(0.99f);
//                float x2 = CR_VigilMalachimFrigateLauncherStats.ACC_LERP_FUNCTION.apply(1f);
//                var loc1 = context.startSlot.getLocation();
//                var loc2 = context.takeOffSlot.getLocation();
//                float d = Misc.getDistance(loc1, loc2);
//                float speed = d * (x2 - x1) / (0.01f * CR_VigilMalachimFrigateLauncherStats.ACCELERATING_DURATION);
//
//                var vel = VectorUtils.rotate(new Vector2f(speed, 0f), context.startSlot.getAngle() + context.source.getFacing());
//                Vector2f.add(vel, context.source.getVelocity(), null);
//
//                wing.addListener(new AdvanceableListener() {
//                    float elapsed = 0f;
//                    @Override
//                    public void advance(float amount) {
//                        elapsed += amount;
//                        wing.getVelocity().set(vel);
//                        if (elapsed > CR_VigilMalachimFrigateLauncherStats.TAKEOFF_BURN_DURATION) {
//                            wing.removeListener(this);
//                        }
//                    }
//                });
//
//                wing.addListener(new HullDamageAboutToBeTakenListener() {
//                    @Override
//                    public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {
//                        if (damageAmount >= wing.getHitpoints()) {
//                            manager.setSuppressDeploymentMessages(true);
//                            manager.removeDeployed(wing, false);
//                            manager.setSuppressDeploymentMessages(false);
//                            context.transferTo(NONE);
//                        }
//                        return false;
//                    }
//                });
//            }
//        },
//
//        HAS_TAKEN_OFF
//    }
//
//    public class CR_VigilMalachimFrigateLauncherStats {
//        public static final float REFIT_DURATION = 3f;
//
//        public static final float LIFTING_DURATION = 1f;
//
//        public static final float CHARGING_DURATION = 0.5f;
//
//        public static final float ACCELERATING_DURATION = 3f;
//
//        public static final float TAKEOFF_BURN_DURATION = 1f;
//
//        public static final Function<Float, Float> ACC_LERP_FUNCTION = t -> (float)Math.pow(t, 4);
//    }
}
