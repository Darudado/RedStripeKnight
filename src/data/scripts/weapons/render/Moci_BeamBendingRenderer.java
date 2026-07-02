package data.scripts.weapons.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 黑洞引力光束弯曲渲染器
 * 通过直接修改 BeamAPI.getTo() 返回的引用来实现光束弯曲效果
 * 
 * 核心发现：getTo() 返回的是私有字段的直接引用，可以直接修改！
 */
public class Moci_BeamBendingRenderer implements EveryFrameCombatPlugin {
    
    private static final Logger LOG = Global.getLogger(Moci_BeamBendingRenderer.class);
    private static Moci_BeamBendingRenderer instance;
    
    // 黑洞信息
    private final Map<Object, BlackHoleData> blackHoles = new HashMap<>();
    
    // 光束原始坐标缓存（用于恢复）
    private final Map<BeamAPI, Vector2f> originalEndpoints = new HashMap<>();
    
    // 光束伤害乘数（避免瞬间膨胀）
    private static final float BEAM_DAMAGE_MULTIPLIER = 0.05f; // 光束伤害只计入5%
    
    // 调试日志计数器（每60帧输出一次）
    private int debugFrameCounter = 0;
    private static final int DEBUG_LOG_INTERVAL = 60;
    
    // 曲线细分段数
    private static final int CURVE_SEGMENTS = 20;
    
    // 被弯曲的光束（需要隐藏原始渲染）
    private final Map<BeamAPI, Boolean> bentBeams = new HashMap<>();
    
    public Moci_BeamBendingRenderer() {
        instance = this;
        LOG.info("[BEAM_BENDING] Initialized beam bending renderer!");
    }
    
    public static Moci_BeamBendingRenderer getInstance() {
        return instance;
    }
    
    /**
     * 注册黑洞
     */
    public void registerBlackHole(Object id, Vector2f center, float radius) {
        BlackHoleData data = blackHoles.get(id);
        if (data == null) {
            data = new BlackHoleData();
            blackHoles.put(id, data);
            LOG.info("[BEAM_BENDING] New black hole registered! id=" + id);
        }
        data.center.set(center);
        data.radius = radius;
        data.lastFrame = Global.getCombatEngine().getTotalElapsedTime(false);
        // 移除每帧更新日志，避免日志洪水
    }
    
    /**
     * 移除黑洞
     */
    public void unregisterBlackHole(Object id) {
        blackHoles.remove(id);
    }
    
    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;
        
        // 调试日志：每60帧输出一次
        debugFrameCounter++;
        if (debugFrameCounter >= DEBUG_LOG_INTERVAL && !blackHoles.isEmpty()) {
            LOG.info("[BEAM_BENDING] Status check: blackHoles=" + blackHoles.size() + ", beams=" + engine.getBeams().size());
            debugFrameCounter = 0;
        }
        
        // 清理过期的黑洞（0.5秒未更新）
        float currentTime = engine.getTotalElapsedTime(false);
        java.util.Iterator<Map.Entry<Object, BlackHoleData>> iterator = blackHoles.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, BlackHoleData> entry = iterator.next();
            boolean expired = currentTime - entry.getValue().lastFrame > 0.5f;
            if (expired) {
                LOG.info("[BEAM_BENDING] Removing expired black hole: " + entry.getKey());
                iterator.remove();
            }
        }
        
        // 步骤1：恢复所有光束到原始状态
        for (Map.Entry<BeamAPI, Vector2f> entry : originalEndpoints.entrySet()) {
            BeamAPI beam = entry.getKey();
            Vector2f originalTo = entry.getValue();
            try {
                // 直接修改 getTo() 返回的引用
                beam.getTo().set(originalTo);
            } catch (Exception e) {
                LOG.error("[BEAM_BENDING] Failed to restore beam endpoint", e);
            }
        }
        originalEndpoints.clear();
        
        if (blackHoles.isEmpty()) return;
        
        // 步骤2：立即计算并应用弯曲
        for (BeamAPI beam : engine.getBeams()) {
            if (beam == null || beam.getWeapon() == null) continue;
            
            Vector2f from = beam.getFrom();
            Vector2f to = beam.getTo();
            if (from == null || to == null) continue;
            
            // 找到影响此光束的黑洞（返回 [黑洞数据, 黑洞ID]）
            Object[] blackHoleInfo = findNearestInfluencingBlackHole(from, to);
            if (blackHoleInfo == null) continue;
            
            BlackHoleData blackHole = (BlackHoleData) blackHoleInfo[0];
            Object blackHoleId = blackHoleInfo[1];
            
            // 保存原始终点
            originalEndpoints.put(beam, new Vector2f(to));
            
            // 计算并应用弯曲
            Vector2f bentEndpoint = calculateBentEndpoint(from, to, blackHole);
            
            // 标记此光束为弯曲状态（将在渲染阶段处理）
            bentBeams.put(beam, true);
            
            // 隐藏原版光束（设置透明）
            try {
                Color originalCore = beam.getCoreColor();
                Color originalFringe = beam.getFringeColor();
                if (originalCore != null) {
                    beam.setCoreColor(new Color(originalCore.getRed(), originalCore.getGreen(), originalCore.getBlue(), 0));
                }
                if (originalFringe != null) {
                    beam.setFringeColor(new Color(originalFringe.getRed(), originalFringe.getGreen(), originalFringe.getBlue(), 0));
                }
            } catch (Exception e) {
                LOG.error("[BEAM_BENDING] Failed to hide original beam", e);
            }
            
            // 修改终点用于伤害判定（直线偏转）
            try {
                to.set(bentEndpoint);
            } catch (Exception e) {
                LOG.error("[BEAM_BENDING] Failed to bend beam endpoint", e);
            }
            
            // 检测光束是否穿过黑洞的事件界限
            if (isBeamCrossingEventHorizon(from, to, blackHole)) {
                // 光束穿过事件界限，吸收其伤害能量
                absorbBeamDamage(beam, blackHoleId, amount);
            }
        }
    }
    
    /**
     * 找到影响此光束的最近黑洞
     * @return [BlackHoleData, Object id] 或 null
     */
    private Object[] findNearestInfluencingBlackHole(Vector2f from, Vector2f to) {
        BlackHoleData nearest = null;
        Object nearestId = null;
        float minDist = Float.MAX_VALUE;
        
        for (Map.Entry<Object, BlackHoleData> entry : blackHoles.entrySet()) {
            BlackHoleData hole = entry.getValue();
            Object id = entry.getKey();
            
            // 计算光束路径到黑洞中心的最短距离
            Vector2f closest = projectPointOnLine(hole.center, from, to);
            float dist = Vector2f.sub(hole.center, closest, null).length();
            
            // 影响范围：黑洞半径的3倍
            float maxRange = hole.radius * 3.0f;
            
            if (dist < maxRange && dist < minDist) {
                minDist = dist;
                nearest = hole;
                nearestId = id;
            }
        }
        
        return nearest != null ? new Object[]{nearest, nearestId} : null;
    }
    
    /**
     * 计算弯曲后的光束终点（简化为直线偏转）
     * 
     * 原理：计算光束应该偏转到哪里
     * - 不是真正的曲线（只有一个 to 点）
     * - 但视觉和伤害效果都会改变
     */
    private Vector2f calculateBentEndpoint(Vector2f from, Vector2f to, BlackHoleData blackHole) {
        Vector2f beamDir = Vector2f.sub(to, from, null);
        float beamLength = beamDir.length();
        if (beamLength < 1f) return new Vector2f(to);
        
        beamDir.normalise();
        
        // 找到光束路径上最接近黑洞的点
        Vector2f closestPoint = projectPointOnLine(blackHole.center, from, to);
        float distanceToBlackHole = Vector2f.sub(blackHole.center, closestPoint, null).length();
        
        // 如果太远，不弯曲
        float maxRange = blackHole.radius * 3.0f;
        if (distanceToBlackHole > maxRange) {
            return new Vector2f(to);
        }
        
        // 计算弯曲强度（距离越近弯曲越强）
        float bendStrength = 1.0f - (distanceToBlackHole / maxRange);
        bendStrength = bendStrength * bendStrength; // 平方衰减
        
        // 计算指向黑洞的方向
        Vector2f toBlackHole = Vector2f.sub(blackHole.center, closestPoint, null);
        if (toBlackHole.length() < 0.001f) {
            return new Vector2f(to);
        }
        toBlackHole.normalise();
        
        // 计算偏转角度
        float maxBendAngle = 60f; // 最大偏转60度
        float bendAngle = maxBendAngle * bendStrength;
        
        // 计算原始光束角度和目标角度
        float originalAngle = (float)Math.toDegrees(Math.atan2(beamDir.y, beamDir.x));
        float targetAngle = (float)Math.toDegrees(Math.atan2(toBlackHole.y, toBlackHole.x));
        
        // 计算最短旋转方向
        float angleDiff = targetAngle - originalAngle;
        while (angleDiff > 180f) angleDiff -= 360f;
        while (angleDiff < -180f) angleDiff += 360f;
        
        // 应用偏转
        float newAngle = originalAngle + angleDiff * (bendAngle / maxBendAngle);
        float newAngleRad = (float)Math.toRadians(newAngle);
        
        // 计算新的终点
        Vector2f newDir = new Vector2f(
            (float)Math.cos(newAngleRad),
            (float)Math.sin(newAngleRad)
        );
        
        return new Vector2f(
            from.x + newDir.x * beamLength,
            from.y + newDir.y * beamLength
        );
    }
    
    /**
     * 检测光束是否穿过黑洞的事件界限
     */
    private boolean isBeamCrossingEventHorizon(Vector2f from, Vector2f to, BlackHoleData blackHole) {
        // 计算光束路径到黑洞中心的最短距离
        Vector2f closest = projectPointOnLine(blackHole.center, from, to);
        float dist = Vector2f.sub(blackHole.center, closest, null).length();
        
        // 如果距离小于事件界限，说明光束穿过了黑洞
        return dist <= blackHole.radius;
    }
    
    /**
     * 吸收光束伤害能量
     */
    private void absorbBeamDamage(BeamAPI beam, Object blackHoleId, float amount) {
        try {
            WeaponAPI weapon = beam.getWeapon();
            if (weapon == null) return;
            
            // 计算光束每帧的伤害输出
            float beamDamagePerSecond = weapon.getDamage().getDamage();
            float beamDamageThisFrame = beamDamagePerSecond * amount;
            
            // 应用光束伤害乘数（避免瞬间膨胀）
            float massToAdd = beamDamageThisFrame * BEAM_DAMAGE_MULTIPLIER;
            
            // 向黑洞添加质量
            if (blackHoleId instanceof DamagingProjectileAPI) {
                Moci_SingularityRenderer.getOrCreate().addMass((DamagingProjectileAPI) blackHoleId, massToAdd);
            }
            
        } catch (Exception e) {
            LOG.error("[BEAM_BENDING] Failed to absorb beam damage", e);
        }
    }
    
    /**
     * 将点投影到线段上
     */
    private Vector2f projectPointOnLine(Vector2f point, Vector2f lineStart, Vector2f lineEnd) {
        Vector2f line = Vector2f.sub(lineEnd, lineStart, null);
        float lineLength = line.lengthSquared();
        
        if (lineLength < 0.001f) {
            return new Vector2f(lineStart);
        }
        
        Vector2f toPoint = Vector2f.sub(point, lineStart, null);
        float projection = (toPoint.x * line.x + toPoint.y * line.y) / lineLength;
        projection = Math.max(0, Math.min(1, projection));
        
        return new Vector2f(
            lineStart.x + line.x * projection,
            lineStart.y + line.y * projection
        );
    }
    
    @Override
    public void init(CombatEngineAPI engine) {
        LOG.info("[BEAM_BENDING] init() called");
    }
    
    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
        // 不需要处理输入
    }
    
    @Override
    public void renderInWorldCoords(ViewportAPI viewport) {
        if (blackHoles.isEmpty()) return;
        
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;
        
        int renderedCount = 0;
        
        // 渲染所有被弯曲的光束
        for (BeamAPI beam : engine.getBeams()) {
            if (beam == null || beam.getWeapon() == null) continue;
            
            // 只渲染被标记为弯曲的光束
            if (!bentBeams.containsKey(beam)) continue;
            
            Vector2f from = beam.getFrom();
            Vector2f to = beam.getTo();
            if (from == null || to == null) continue;
            
            // 找到影响此光束的黑洞
            Object[] blackHoleInfo = findNearestInfluencingBlackHole(from, to);
            if (blackHoleInfo == null) continue;
            
            BlackHoleData blackHole = (BlackHoleData) blackHoleInfo[0];
            
            renderBentBeam(beam, from, to, blackHole, viewport);
            renderedCount++;
        }
        
        if (renderedCount > 0 && debugFrameCounter == 0) {
            LOG.info("[BEAM_BENDING] Rendered " + renderedCount + " bent beams");
        }
        
        // 清空弯曲标记（下一帧重新判断）
        bentBeams.clear();
    }
    
    /**
     * 渲染弯曲的光束（使用贝塞尔曲线）
     */
    private void renderBentBeam(BeamAPI beam, Vector2f from, Vector2f to, BlackHoleData blackHole, ViewportAPI viewport) {
        try {
            // 获取光束属性
            WeaponAPI weapon = beam.getWeapon();
            if (weapon == null) return;
            
            // 使用原始坐标（被修改前的 to）
            Vector2f originalTo = originalEndpoints.get(beam);
            if (originalTo == null) originalTo = to;
            
            // 光束颜色和宽度
            Color coreColor = beam.getCoreColor();
            if (coreColor == null) coreColor = new Color(255, 200, 200, 255);
            Color fringeColor = beam.getFringeColor();
            if (fringeColor == null) fringeColor = new Color(255, 150, 150, 128);
            
            float beamWidth = Math.max(10f, beam.getWidth());
            
            // 计算贝塞尔曲线控制点
            Vector2f controlPoint = calculateBezierControlPoint(from, originalTo, blackHole);
            
            // 生成曲线上的点
            List<Vector2f> curvePoints = generateBezierCurve(from, controlPoint, originalTo, CURVE_SEGMENTS);
            
            // 动态获取光束纹理
            SpriteAPI coreSprite = null;
            SpriteAPI fringeSprite = null;
            String coreTexturePath = null;
            String fringeTexturePath = null;
            
            try {
                if (weapon.getSpec() instanceof com.fs.starfarer.api.loading.BeamWeaponSpecAPI) {
                    com.fs.starfarer.api.loading.BeamWeaponSpecAPI beamSpec = 
                        (com.fs.starfarer.api.loading.BeamWeaponSpecAPI) weapon.getSpec();
                    
                    // 尝试获取纹理路径（通过枚举类型）
                    // 使用MethodHandle获取getTextureType()（不在API接口中，需要通过实现类访问）
                    // 注意：返回类型是混淆后的枚举类，需要使用Enum.class作为泛型
                    Object textureType = null;
                    try {
                        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                        Class<?> implClass = beamSpec.getClass();
                        MethodHandle getTextureTypeHandle = lookup.findVirtual(
                            implClass,
                            "getTextureType",
                            MethodType.methodType(Enum.class)
                        );
                        textureType = getTextureTypeHandle.invoke(beamSpec);
                        if (debugFrameCounter == 0) {
                            LOG.info("[BEAM_BENDING] Got textureType: " + (textureType != null ? textureType.toString() : "null"));
                        }
                    } catch (Throwable ex) {
                        if (debugFrameCounter == 0) {
                            LOG.info("[BEAM_BENDING] Failed to get textureType with Enum: " + ex.getMessage());
                        }
                        // 尝试使用Object作为返回类型
                        try {
                            MethodHandles.Lookup lookup2 = MethodHandles.publicLookup();
                            MethodHandle getTextureTypeHandle2 = lookup2.findVirtual(
                                beamSpec.getClass(),
                                "getTextureType",
                                MethodType.methodType(Object.class)
                            );
                            textureType = getTextureTypeHandle2.invoke(beamSpec);
                            if (debugFrameCounter == 0) {
                                LOG.info("[BEAM_BENDING] Got textureType with Object: " + (textureType != null ? textureType.toString() : "null"));
                            }
                        } catch (Throwable ex2) {
                            if (debugFrameCounter == 0) {
                                LOG.info("[BEAM_BENDING] Failed to get textureType with Object: " + ex2.getMessage());
                            }
                        }
                    }
                    
                    if (textureType != null) {
                        // 使用MethodHandle获取枚举的路径方法（符合规则）
                        try {
                            MethodHandles.Lookup lookup = MethodHandles.lookup();
                            // 获取fringe纹理路径方法
                            MethodHandle getFringeHandle = lookup.findVirtual(
                                textureType.getClass(),
                                "o00000",
                                MethodType.methodType(String.class)
                            );
                            // 获取core纹理路径方法
                            MethodHandle getCoreHandle = lookup.findVirtual(
                                textureType.getClass(),
                                "new",
                                MethodType.methodType(String.class)
                            );
                            fringeTexturePath = (String) getFringeHandle.invoke(textureType);
                            coreTexturePath = (String) getCoreHandle.invoke(textureType);
                            if (debugFrameCounter == 0) {
                                LOG.info("[BEAM_BENDING] Got from enum: core=" + coreTexturePath + " fringe=" + fringeTexturePath);
                            }
                        } catch (Throwable enumEx) {
                            if (debugFrameCounter == 0) {
                                LOG.info("[BEAM_BENDING] Failed to get from enum: " + enumEx.getMessage());
                            }
                        }
                    }
                    
                    // 如果枚举获取失败，尝试直接字符串
                    if (coreTexturePath == null) {
                        coreTexturePath = beamSpec.getCoreTex();
                        if (debugFrameCounter == 0) {
                            LOG.info("[BEAM_BENDING] Direct getCoreTex(): " + coreTexturePath);
                        }
                    }
                    if (fringeTexturePath == null) {
                        fringeTexturePath = beamSpec.getFringeTex();
                        if (debugFrameCounter == 0) {
                            LOG.info("[BEAM_BENDING] Direct getFringeTex(): " + fringeTexturePath);
                        }
                    }
                    
                    // 加载纹理sprite
                    if (coreTexturePath != null && !coreTexturePath.isEmpty()) {
                        coreSprite = Global.getSettings().getSprite(coreTexturePath);
                    }
                    if (fringeTexturePath != null && !fringeTexturePath.isEmpty()) {
                        fringeSprite = Global.getSettings().getSprite(fringeTexturePath);
                    }
                    
                    if (debugFrameCounter == 0) {
                        LOG.info("[BEAM_BENDING] Texture loading: core=" + coreTexturePath + " fringe=" + fringeTexturePath);
                    }
                }
            } catch (Exception e) {
                LOG.error("[BEAM_BENDING] Failed to load beam textures", e);
            }
            
            if (debugFrameCounter == 0) {
                LOG.info("[BEAM_BENDING] Rendering bent beam: width=" + beamWidth + " hasTexture=" + (coreSprite != null));
            }
            
            // 使用纹理或纯色绘制
            if (fringeSprite != null && coreSprite != null) {
                renderCurvedTextureStrip(curvePoints, beamWidth, fringeColor, fringeSprite, 2.5f);
                renderCurvedTextureStrip(curvePoints, beamWidth, coreColor, coreSprite, 1.0f);
            } else {
                // 回退到纯色
                if (debugFrameCounter == 0) {
                    LOG.info("[BEAM_BENDING] Using color strip fallback: fringe=" + fringeColor + " core=" + coreColor + 
                            " points=" + curvePoints.size() + " width=" + beamWidth);
                }
                renderCurvedColorStrip(curvePoints, beamWidth, fringeColor, 2.5f);
                renderCurvedColorStrip(curvePoints, beamWidth, coreColor, 1.0f);
            }
            
        } catch (Exception e) {
            LOG.error("[BEAM_BENDING] Failed to render bent beam", e);
        }
    }
    
    /**
     * 使用OpenGL渲染弯曲的纹理带
     */
    private void renderCurvedTextureStrip(List<Vector2f> curvePoints, float baseWidth, Color color, SpriteAPI sprite, float widthMult) {
        if (curvePoints.size() < 2 || sprite == null) return;
        
        try {
            float width = baseWidth * widthMult;
            
            // 绑定纹理
            sprite.bindTexture();
            
            // 设置OpenGL状态
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            
            // 设置颜色
            GL11.glColor4f(
                color.getRed() / 255f,
                color.getGreen() / 255f,
                color.getBlue() / 255f,
                color.getAlpha() / 255f
            );
            
            // 使用GL_QUAD_STRIP绘制弯曲的纹理带
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            
            float totalLength = 0f;
            for (int i = 0; i < curvePoints.size(); i++) {
                Vector2f point = curvePoints.get(i);
                
                // 计算当前点的切线方向
                Vector2f tangent;
                if (i == 0) {
                    tangent = Vector2f.sub(curvePoints.get(i + 1), point, null);
                } else if (i == curvePoints.size() - 1) {
                    tangent = Vector2f.sub(point, curvePoints.get(i - 1), null);
                } else {
                    Vector2f t1 = Vector2f.sub(point, curvePoints.get(i - 1), null);
                    Vector2f t2 = Vector2f.sub(curvePoints.get(i + 1), point, null);
                    tangent = new Vector2f(
                        (t1.x + t2.x) / 2f,
                        (t1.y + t2.y) / 2f
                    );
                }
                
                tangent.normalise();
                
                // 计算垂直方向
                Vector2f perpendicular = new Vector2f(-tangent.y, tangent.x);
                perpendicular.scale(width / 2f);
                
                // 计算纹理坐标
                if (i > 0) {
                    totalLength += Vector2f.sub(point, curvePoints.get(i - 1), null).length();
                }
                float texCoordV = totalLength / (baseWidth * 4f);
                
                // 添加顶点
                Vector2f left = Vector2f.add(point, perpendicular, null);
                Vector2f right = Vector2f.sub(point, perpendicular, null);
                
                GL11.glTexCoord2f(0f, texCoordV);
                GL11.glVertex2f(left.x, left.y);
                
                GL11.glTexCoord2f(1f, texCoordV);
                GL11.glVertex2f(right.x, right.y);
            }
            
            GL11.glEnd();
            GL11.glPopMatrix();
            
        } catch (Exception e) {
            LOG.error("[BEAM_BENDING] Failed to render curved texture strip", e);
        }
    }
    
    /**
     * 使用OpenGL渲染弯曲的纯色带（不用纹理，更简单高效）
     */
    private void renderCurvedColorStrip(List<Vector2f> curvePoints, float baseWidth, Color color, float widthMult) {
        if (curvePoints.size() < 2) {
            LOG.warn("[BEAM_BENDING] renderCurvedColorStrip: not enough points (" + curvePoints.size() + ")");
            return;
        }
        
        try {
            float width = baseWidth * widthMult;
            
            // 设置OpenGL状态
            GL11.glPushMatrix();
            GL11.glDisable(GL11.GL_TEXTURE_2D);  // 禁用纹理
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            
            // 设置颜色 - 确保alpha不为0
            float alpha = Math.max(0.5f, color.getAlpha() / 255f);
            GL11.glColor4f(
                color.getRed() / 255f,
                color.getGreen() / 255f,
                color.getBlue() / 255f,
                alpha
            );
            
            if (debugFrameCounter == 0) {
                LOG.info("[BEAM_BENDING] Rendering color strip: color=(" + color.getRed() + "," + color.getGreen() + 
                        "," + color.getBlue() + "," + color.getAlpha() + ") width=" + width + " points=" + curvePoints.size());
            }
            
            // 使用GL_QUAD_STRIP绘制弯曲的色带
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            
            for (int i = 0; i < curvePoints.size(); i++) {
                Vector2f point = curvePoints.get(i);
                
                // 计算当前点的切线方向（用于确定垂直方向）
                Vector2f tangent;
                if (i == 0) {
                    // 第一个点：使用到下一个点的方向
                    tangent = Vector2f.sub(curvePoints.get(i + 1), point, null);
                } else if (i == curvePoints.size() - 1) {
                    // 最后一个点：使用从上一个点的方向
                    tangent = Vector2f.sub(point, curvePoints.get(i - 1), null);
                } else {
                    // 中间点：使用平均切线
                    Vector2f t1 = Vector2f.sub(point, curvePoints.get(i - 1), null);
                    Vector2f t2 = Vector2f.sub(curvePoints.get(i + 1), point, null);
                    tangent = new Vector2f(
                        (t1.x + t2.x) / 2f,
                        (t1.y + t2.y) / 2f
                    );
                }
                
                tangent.normalise();
                
                // 计算垂直方向（用于光束宽度）
                Vector2f perpendicular = new Vector2f(-tangent.y, tangent.x);
                perpendicular.scale(width / 2f);
                
                // 添加两个顶点（光束的两侧）
                Vector2f left = Vector2f.add(point, perpendicular, null);
                Vector2f right = Vector2f.sub(point, perpendicular, null);
                
                // 绘制顶点（不需要纹理坐标）
                GL11.glVertex2f(left.x, left.y);
                GL11.glVertex2f(right.x, right.y);
            }
            
            GL11.glEnd();
            GL11.glPopMatrix();
            
        } catch (Exception e) {
            LOG.error("[BEAM_BENDING] Failed to render curved color strip", e);
        }
    }
    
    /**
     * 计算贝塞尔曲线控制点（黑洞引力产生的弯曲）
     */
    private Vector2f calculateBezierControlPoint(Vector2f from, Vector2f to, BlackHoleData blackHole) {
        // 找到光束中点
        Vector2f mid = new Vector2f(
            (from.x + to.x) / 2f,
            (from.y + to.y) / 2f
        );
        
        // 计算中点到黑洞的方向
        Vector2f toBlackHole = Vector2f.sub(blackHole.center, mid, null);
        float distance = toBlackHole.length();
        
        if (distance < 0.001f) return mid;
        
        toBlackHole.normalise();
        
        // 弯曲强度：距离越近弯曲越强
        float maxRange = blackHole.radius * 3.0f;
        float bendStrength = 1.0f - Math.min(1.0f, distance / maxRange);
        bendStrength = bendStrength * bendStrength; // 平方衰减
        
        // 控制点偏移：向黑洞方向偏移
        float offset = blackHole.radius * 2.0f * bendStrength;
        
        return new Vector2f(
            mid.x + toBlackHole.x * offset,
            mid.y + toBlackHole.y * offset
        );
    }
    
    /**
     * 生成二次贝塞尔曲线上的点
     */
    private List<Vector2f> generateBezierCurve(Vector2f p0, Vector2f p1, Vector2f p2, int segments) {
        List<Vector2f> points = new ArrayList<>();
        
        for (int i = 0; i <= segments; i++) {
            float t = (float)i / segments;
            float oneMinusT = 1f - t;
            
            // 二次贝塞尔公式: B(t) = (1-t)²P0 + 2(1-t)tP1 + t²P2
            float x = oneMinusT * oneMinusT * p0.x + 
                     2f * oneMinusT * t * p1.x + 
                     t * t * p2.x;
            float y = oneMinusT * oneMinusT * p0.y + 
                     2f * oneMinusT * t * p1.y + 
                     t * t * p2.y;
            
            points.add(new Vector2f(x, y));
        }
        
        return points;
    }
    
    @Override
    public void renderInUICoords(ViewportAPI viewport) {
        // 不需要渲染
    }
    
    public void cleanup() {
        LOG.info("[BEAM_BENDING] cleanup() called - clearing all data");
        
        // 恢复所有光束
        for (Map.Entry<BeamAPI, Vector2f> entry : originalEndpoints.entrySet()) {
            try {
                entry.getKey().getTo().set(entry.getValue());
            } catch (Exception ignore) {}
        }
        originalEndpoints.clear();
        blackHoles.clear();
    }
    
    // 数据类
    private static class BlackHoleData {
        Vector2f center = new Vector2f();
        float radius;
        float lastFrame;
    }
}

