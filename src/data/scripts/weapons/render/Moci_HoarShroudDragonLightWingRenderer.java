package data.scripts.weapons.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.dark.shaders.util.ShaderLib;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 霜岚光翼的原设计风格渲染器。
 *
 * 设计来源：
 * - 结构上尽量保留 docs/光翼特效.java 的原始思路：
 *   1. 维护一个待渲染的 TrailEntity 列表
 *   2. 先把拖尾画进离屏 FBO
 *   3. 再把 FBO 贴回世界坐标
 *   4. 分成 under / up 两层做叠加
 *
 * 这份文件和控制器的关系：
 * - Moci_HoarShroudDragonLightWingAnimationController：
 *   只负责装饰武器翻帧动画
 * - Moci_HoarShroudDragonLightWingEffectController：
 *   只负责把本渲染器挂进战斗引擎
 * - Moci_HoarShroudDragonLightWingCombinedController：
 *   作为 .wpn 实际入口，同时驱动上面两个控制器
 * - 本文件：
 *   负责“光翼拖尾具体长什么样、怎么画、画到哪里”
 *
 * 如何直接使用本渲染器：
 * 1. 你手里需要有一个 WeaponAPI，通常是 DECORATIVE 武器
 * 2. 这个武器最好配置了 turretOffsets / hardpointOffsets，作为拖尾锚点
 * 3. 战斗中调用：
 *    engine.addLayeredRenderingPlugin(new Moci_HoarShroudDragonLightWingRenderer(weapon));
 *
 * 适配贴图分两类：
 * 1. “拖尾笔刷纹理”
 *    - graphics/fx/trail_flame.png
 *    - graphics/fx/beam_rough2_core.png
 *    这两张是渲染器内部拿来刷拖尾形状的，不是机体本身的装饰贴图
 * 2. “机体装饰武器贴图”
 *    - 例如 Moci_HoarShroudDragon_lightwing.wpn 里的 turretSprite / hardpointSprite
 *    - 例如肩灯 .wpn 里的 turretSprite / hardpointSprite
 *    这些负责机体本身展开动画，不负责拖尾
 *
 * 如果你要给别的机体复用，最关键的是三件事：
 * - 槽位 id：决定走哪套参数
 * - 开火点 offsets：决定拖尾从哪里长出来、朝哪个方向延伸
 * - getEffectLevel() 的驱动来源：当前默认取 ship system effectLevel
 */
public class Moci_HoarShroudDragonLightWingRenderer extends BaseCombatLayeredRenderingPlugin {
    private static final Logger LOG = Logger.getLogger(Moci_HoarShroudDragonLightWingRenderer.class);

    /**
     * 主光翼槽位 id。
     *
     * 如果你复用到别的船，可以改成你的装饰武器槽位名称。
     */
    private static final String SLOT_LIGHTWING = "LIGHTWING";
    /**
     * 左肩灯槽位 id。
     */
    private static final String SLOT_SHOULDER_LIGHT_L = "SHOULDER_LIGHT_L";
    /**
     * 右肩灯槽位 id。
     */
    private static final String SLOT_SHOULDER_LIGHT_R = "SHOULDER_LIGHT_R";

    /**
     * 拖尾被切成多少段来绘制。
     *
     * 调大：
     * - 轮廓更细腻
     * - 但 GL 提交次数更多，性能更高
     *
     * 调小：
     * - 性能更轻
     * - 但拖尾边缘和收缩过渡会更粗糙
     */
    private static final int SEGMENT = 100;
    /**
     * 拖尾起始段宽度倍率。
     *
     * 含义：
     * - 越接近 1，根部越粗
     * - 越小，根部越尖
     */
    private static final float WIDTH_START = 0.3f;
    /**
     * 拖尾末端宽度倍率。
     *
     * 含义：
     * - 越大，尾端保留得越宽，视觉更“羽翼”
     * - 越小，尾端收得更尖
     */
    private static final float WIDTH_END = 0.8f;

    /**
     * 主光翼核心颜色。
     *
     * 这是靠近中心、偏亮的那层颜色。
     */
    private static final Color LIGHTWING_CORE = new Color(255, 245, 220, 255);
    /**
     * 主光翼拖尾颜色。
     *
     * 这是往尾端过渡时的主色。
     */
    private static final Color LIGHTWING_TRAIL = new Color(255, 182, 77, 220);
    /**
     * 肩灯核心颜色。
     */
    private static final Color SHOULDER_CORE = new Color(255, 255, 255, 220);
    /**
     * 肩灯拖尾颜色。
     */
    private static final Color SHOULDER_TRAIL = new Color(90, 170, 255, 180);

    /**
     * 绑定舰船。
     * 当前默认从 weapon.getShip() 获取。
     */
    private final ShipAPI ship;
    /**
     * 绑定武器。
     * 拖尾锚点、朝向、fire point 数量都来自它。
     */
    private final WeaponAPI weapon;
    /**
     * 拖尾上层使用的主纹理。
     *
     * 如果你要改风格，优先换这张。
     */
    private final SpriteAPI texture1 = Global.getSettings().getSprite("graphics/fx/trail_flame.png");
    /**
     * 拖尾底层/黑边使用的辅助纹理。
     *
     * 这张更多决定粗糙感和边缘气质。
     */
    private final SpriteAPI texture2 = Global.getSettings().getSprite("graphics/fx/beam_rough2_core.png");
    /**
     * 当前武器对应的所有拖尾实体。
     *
     * 一个武器如果定义了多个 fire point，这里就会生成多个拖尾实体。
     */
    private final List<TrailEntity> entities = new ArrayList<TrailEntity>();

    private CombatEngineAPI engine;
    /**
     * 置 true 后插件会自然过期。
     */
    private boolean shouldExpire = false;
    /**
     * 离屏缓冲。
     *
     * 保留原始设计思路：先画到 FBO，再把 FBO 贴回世界。
     */
    private BufferWithData bufferWithData = null;

    public Moci_HoarShroudDragonLightWingRenderer(WeaponAPI weapon) {
        this.weapon = weapon;
        this.ship = weapon != null ? weapon.getShip() : null;
        this.layer = CombatEngineLayers.ABOVE_SHIPS_LAYER;
        this.engine = Global.getCombatEngine();
        initializeTrailEntities();
    }

    @Override
    public float getRenderRadius() {
        return Float.MAX_VALUE;
    }

    @Override
    public boolean isExpired() {
        return shouldExpire;
    }

    @Override
    public void advance(float amount) {
        if (shouldPause()) {
            return;
        }

        if (ship == null || !ship.isAlive() || ship.isHulk()) {
            expireAndCleanup();
            return;
        }

        float effectLevel = getEffectLevel();
        for (TrailEntity entity : entities) {
            entity.textureRun -= amount * (0.1f + 0.2f * effectLevel);
            if (entity.textureRun <= -1000f) {
                entity.textureRun += 1000f;
            }
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer != this.layer) {
            return;
        }
        if (shouldPause()) {
            return;
        }
        if (entities.isEmpty()) {
            return;
        }

        float effectLevel = getEffectLevel();
        if (effectLevel <= 0f) {
            return;
        }

        if (!ensureBufferReady()) {
            expireAndCleanup();
            return;
        }

        renderUnderTrail(effectLevel);
        renderUpTrail(effectLevel);
    }

    private void initializeTrailEntities() {
        if (weapon == null || ship == null || weapon.getSlot() == null) {
            return;
        }

        SlotConfig config = getSlotConfig(weapon.getSlot().getId());
        if (config == null) {
            return;
        }

        int firePointCount = getFirePointCount();
        for (int i = 0; i < firePointCount; i++) {
            TrailEntity entity = new TrailEntity();
            entity.ship = ship;
            entity.weapon = weapon;
            entity.firePointIndex = i;
            entity.length = config.length;
            entity.width = config.width;
            entity.coreColor = config.coreColor;
            entity.trailColor = config.trailColor;
            entities.add(entity);
        }
    }

    private boolean shouldPause() {
        if (engine == null) {
            engine = Global.getCombatEngine();
        }
        if (engine == null) {
            return true;
        }
        if (engine.isPaused()) {
            return true;
        }
        if (ship == null || weapon == null) {
            return true;
        }
        if (!engine.isEntityInPlay(ship)) {
            return true;
        }
        return false;
    }

    private float getEffectLevel() {
        if (ship == null || ship.getSystem() == null) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, ship.getSystem().getEffectLevel()));
    }

    private int getFirePointCount() {
        if (weapon == null || weapon.getSpec() == null) {
            return 1;
        }

        int turretCount = weapon.getSpec().getTurretFireOffsets() != null
                ? weapon.getSpec().getTurretFireOffsets().size() : 0;
        int hardpointCount = weapon.getSpec().getHardpointFireOffsets() != null
                ? weapon.getSpec().getHardpointFireOffsets().size() : 0;
        return Math.max(1, Math.max(turretCount, hardpointCount));
    }

    private SlotConfig getSlotConfig(String slotId) {
        if (SLOT_LIGHTWING.equals(slotId)) {
            // 主光翼参数：
            // length: 拖尾长度
            // width : 拖尾基础宽度
            // core/trailColor: 颜色渐变
            return new SlotConfig(240f, 58f, LIGHTWING_CORE, LIGHTWING_TRAIL);
        }
        if (SLOT_SHOULDER_LIGHT_L.equals(slotId) || SLOT_SHOULDER_LIGHT_R.equals(slotId)) {
            // 肩灯参数：
            // 比主光翼更短、更窄，避免抢主体视觉
            return new SlotConfig(170f, 30f, SHOULDER_CORE, SHOULDER_TRAIL);
        }
        return null;
    }

    private boolean ensureBufferReady() {
        if (bufferWithData != null) {
            return true;
        }

        if (texture1 == null || texture2 == null) {
            LOG.warn("Light wing renderer missing source textures.");
            return false;
        }

        bufferWithData = BufferWithData.createBuffer(texture1, texture2);
        return bufferWithData != null;
    }

    private void renderUpTrail(float effectLevel) {
        for (TrailEntity entity : entities) {
            if (getAnchorLocation(entity) == null) {
                continue;
            }

            composeTrailToBuffer(entity, effectLevel, false);
            drawBufferToWorld(entity, true);
        }
    }

    private void renderUnderTrail(float effectLevel) {
        for (TrailEntity entity : entities) {
            if (getAnchorLocation(entity) == null) {
                continue;
            }

            composeTrailToBuffer(entity, effectLevel, true);
            drawBufferToWorld(entity, false);
        }
    }

    private void composeTrailToBuffer(TrailEntity entity, float effectLevel, boolean underTrail) {
        if (bufferWithData == null) {
            return;
        }

        int width = bufferWithData.getWidth();
        int height = bufferWithData.getHeight();
        int textureBufferId = bufferWithData.getTextureBufferId();

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL20.glUseProgram(0);
        bindFramebuffer(textureBufferId);

        GL11.glViewport(0, 0, width, height);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width, 0, height, -2000, 2000);

        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        if (underTrail) {
            renderSingleUnderTrail(entity, width, height, effectLevel);
        } else {
            renderSingleTrail(entity, width, height, effectLevel);
        }

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();

        unbindFramebuffer();
        GL11.glPopAttrib();
    }

    private void drawBufferToWorld(TrailEntity entity, boolean additiveBlend) {
        if (bufferWithData == null) {
            return;
        }

        Vector2f anchor = getAnchorLocation(entity);
        if (anchor == null) {
            return;
        }

        float facing = getWorldFacing(entity);
        int width = bufferWithData.getWidth();
        int height = bufferWithData.getHeight();
        int textureId = bufferWithData.getTextureId();

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();

        GL11.glTranslatef(anchor.x, anchor.y, 0f);
        GL11.glRotatef(facing, 0f, 0f, 1f);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        if (additiveBlend) {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        } else {
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        float halfHeight = height * 0.5f;
        float halfWidth = width * 0.5f;

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 1f);
        GL11.glVertex2f(-halfWidth, halfHeight);
        GL11.glTexCoord2f(1f, 1f);
        GL11.glVertex2f(halfWidth, halfHeight);
        GL11.glTexCoord2f(1f, 0f);
        GL11.glVertex2f(halfWidth, -halfHeight);
        GL11.glTexCoord2f(0f, 0f);
        GL11.glVertex2f(-halfWidth, -halfHeight);
        GL11.glEnd();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();

        GL11.glPopAttrib();
    }

    private void renderSingleTrail(TrailEntity entity, float bufferCenterX, float bufferCenterY, float effectLevel) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        GL11.glPushMatrix();

        float segmentLength = entity.length / SEGMENT;
        GL11.glTranslatef(bufferCenterX / 2f, bufferCenterY / 2f, 0f);
        for (int i = 0; i < SEGMENT; i++) {
            GL11.glTranslatef(segmentLength, 0f, 0f);

            float widthMult;
            if (i <= SEGMENT * 0.2f) {
                widthMult = WIDTH_START + ((1f - WIDTH_START) * smoothstep(0f, SEGMENT * 0.2f, i));
            } else {
                widthMult = 1f - ((1f - WIDTH_END) * smoothstep(SEGMENT * 0.2f, SEGMENT, i));
            }

            float uvLeft = (float) i / SEGMENT + entity.textureRun + entity.random;
            float uvRight = (float) (i + 1) / SEGMENT + entity.textureRun + entity.random;

            texture1.bindTexture();
            texture1.setNormalBlend();

            float halfWidth = entity.width * 0.5f * widthMult;
            float alphaMult;

            float textureOverride = 0.2f;
            uvLeft += textureOverride;
            uvRight += textureOverride;
            Color color = Misc.interpolateColor(entity.coreColor.brighter(), entity.trailColor, (float) i / SEGMENT);
            alphaMult = color.getAlpha()
                    * effectLevel
                    * (1f - smoothstep(-1f, SEGMENT * (0.5f + 0.5f * effectLevel), i));

            GL11.glColor4ub(
                    (byte) color.getRed(),
                    (byte) color.getGreen(),
                    (byte) color.getBlue(),
                    (byte) clampToByte(alphaMult)
            );
            halfWidth *= 0.5f;
            drawSegmentQuad(segmentLength, halfWidth, uvLeft, uvRight);

            halfWidth *= 4f;
            textureOverride = 0.8f;
            uvLeft -= textureOverride;
            uvRight -= textureOverride;
            color = Misc.interpolateColor(entity.coreColor.darker(), Color.BLACK, 0.5f + (float) i / SEGMENT * 0.5f);
            GL11.glColor4ub(
                    (byte) color.getRed(),
                    (byte) color.getGreen(),
                    (byte) color.getBlue(),
                    (byte) clampToByte(alphaMult)
            );
            drawSegmentQuad(segmentLength, halfWidth, uvLeft, uvRight);

            textureOverride = 0.5f;
            uvLeft -= textureOverride;
            uvRight -= textureOverride;
            drawSegmentQuad(segmentLength, halfWidth, uvLeft, uvRight);

            halfWidth /= 2f;
            textureOverride = 0.6f;
            uvLeft -= textureOverride;
            uvRight -= textureOverride;
            color = Misc.interpolateColor(entity.coreColor, Color.WHITE, (float) i / SEGMENT * 0.5f);
            GL11.glColor4ub(
                    (byte) color.getRed(),
                    (byte) color.getGreen(),
                    (byte) color.getBlue(),
                    (byte) clampToByte(alphaMult)
            );
            drawSegmentQuad(segmentLength, halfWidth, uvLeft, uvRight);

            halfWidth /= 2f;
            color = Misc.interpolateColor(entity.trailColor, Color.WHITE, 0.7f - (float) i / SEGMENT * 0.7f);
            GL11.glColor4ub(
                    (byte) color.getRed(),
                    (byte) color.getGreen(),
                    (byte) color.getBlue(),
                    (byte) clampToByte(alphaMult)
            );
            drawSegmentQuad(segmentLength, halfWidth, uvLeft, uvRight);

            textureOverride = 0.4f;
            uvLeft -= textureOverride;
            uvRight -= textureOverride;
            drawSegmentQuad(segmentLength, halfWidth, uvLeft, uvRight);
        }

        GL11.glPopMatrix();
    }

    private void renderSingleUnderTrail(TrailEntity entity, float bufferCenterX, float bufferCenterY, float effectLevel) {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        GL11.glPushMatrix();

        float segmentLength = entity.length / SEGMENT;
        GL11.glTranslatef(bufferCenterX / 2f, bufferCenterY / 2f, 0f);
        for (int i = 0; i < SEGMENT; i++) {
            GL11.glTranslatef(segmentLength, 0f, 0f);

            float widthMult;
            if (i <= SEGMENT * 0.2f) {
                widthMult = WIDTH_START + ((1f - WIDTH_START) * smoothstep(0f, SEGMENT * 0.2f, i));
            } else {
                widthMult = 1f - ((1f - WIDTH_END) * smoothstep(SEGMENT * 0.2f, SEGMENT, i));
            }

            float uvLeft = (float) i / SEGMENT + entity.textureRun + entity.random;
            float uvRight = (float) (i + 1) / SEGMENT + entity.textureRun + entity.random;

            float halfWidth = entity.width * 0.5f * widthMult;
            Color color = Misc.interpolateColor(entity.coreColor, Color.BLACK, 0.8f + 0.1f * i / SEGMENT);
            float alphaMult = color.getAlpha()
                    * effectLevel
                    * (1f - smoothstep(0f, SEGMENT * (0.5f + 0.5f * effectLevel), i));

            texture2.bindTexture();
            texture2.setNormalBlend();
            halfWidth *= 5f;

            GL11.glColor4ub(
                    (byte) color.getRed(),
                    (byte) color.getGreen(),
                    (byte) color.getBlue(),
                    (byte) clampToByte(alphaMult)
            );
            drawSegmentQuad(segmentLength, halfWidth, uvLeft, uvRight);
        }

        GL11.glPopMatrix();
    }

    private void drawSegmentQuad(float segmentLength, float halfWidth, float uvLeft, float uvRight) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(uvLeft, 0f);
        GL11.glVertex3f(0f, -halfWidth, 0f);

        GL11.glTexCoord2f(uvRight, 0f);
        GL11.glVertex3f(segmentLength, -halfWidth, 0f);

        GL11.glTexCoord2f(uvRight, 1f);
        GL11.glVertex3f(segmentLength, halfWidth, 0f);

        GL11.glTexCoord2f(uvLeft, 1f);
        GL11.glVertex3f(0f, halfWidth, 0f);
        GL11.glEnd();
    }

    private Vector2f getAnchorLocation(TrailEntity entity) {
        if (entity == null || entity.weapon == null) {
            return null;
        }
        return entity.weapon.getFirePoint(entity.firePointIndex);
    }

    private float getWorldFacing(TrailEntity entity) {
        if (entity == null || entity.weapon == null) {
            return 0f;
        }

        Vector2f weaponLocation = entity.weapon.getLocation();
        Vector2f firePoint = getAnchorLocation(entity);
        if (weaponLocation == null || firePoint == null) {
            return entity.weapon.getCurrAngle();
        }

        float dx = firePoint.x - weaponLocation.x;
        float dy = firePoint.y - weaponLocation.y;
        if (dx * dx + dy * dy < 0.0001f) {
            return entity.weapon.getCurrAngle();
        }
        return (float) Math.toDegrees(Math.atan2(dy, dx));
    }

    private void bindFramebuffer(int textureBufferId) {
        if (ShaderLib.useBufferCore()) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, textureBufferId);
        } else if (ShaderLib.useBufferARB()) {
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, textureBufferId);
        } else {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, textureBufferId);
        }
    }

    private void unbindFramebuffer() {
        if (ShaderLib.useBufferCore()) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        } else if (ShaderLib.useBufferARB()) {
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, 0);
        } else {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, 0);
        }
    }

    private void expireAndCleanup() {
        shouldExpire = true;
        clearBufferWithData();
    }

    private void clearBufferWithData() {
        if (bufferWithData == null) {
            return;
        }

        int textureId = bufferWithData.getTextureId();
        if (textureId != 0) {
            GL11.glDeleteTextures(textureId);
        }

        int textureBufferId = bufferWithData.getTextureBufferId();
        if (textureBufferId != 0) {
            if (ShaderLib.useBufferCore()) {
                GL30.glDeleteFramebuffers(textureBufferId);
            } else if (ShaderLib.useBufferARB()) {
                ARBFramebufferObject.glDeleteFramebuffers(textureBufferId);
            } else {
                EXTFramebufferObject.glDeleteFramebuffersEXT(textureBufferId);
            }
        }

        bufferWithData = null;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge0 == edge1) {
            return 0f;
        }
        float t = (x - edge0) / (edge1 - edge0);
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static int clampToByte(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    private static final class TrailEntity {
        private ShipAPI ship;
        private WeaponAPI weapon;
        /**
         * 当前拖尾绑定的是武器的第几个 fire point。
         */
        private int firePointIndex;
        /**
         * 拖尾长度。
         *
         * 调大：
         * - 尾迹更长，更有“展开拖曳感”
         * 调小：
         * - 更紧凑，更像喷口或短促光翼
         */
        private float length = 0f;
        /**
         * 拖尾基础宽度。
         *
         * 调大：
         * - 整体更厚、更有翅片感
         * 调小：
         * - 更细、更像光束丝带
         */
        private float width = 0f;
        /**
         * 核心主色。
         */
        private Color coreColor = Color.WHITE;
        /**
         * 向尾端过渡的颜色。
         */
        private Color trailColor = Color.WHITE;
        /**
         * 每条拖尾自己的随机 UV 偏移。
         *
         * 作用：
         * - 避免多条拖尾的纹理滚动完全同步，显得死板
         */
        private float random = (float) Math.random();
        /**
         * 纹理滚动偏移。
         *
         * 每帧都会按 effectLevel 推进，形成“流动中的光翼”。
         */
        private float textureRun = 0f;
    }

    private static final class SlotConfig {
        /**
         * 这一类槽位使用的拖尾长度。
         */
        private final float length;
        /**
         * 这一类槽位使用的拖尾宽度。
         */
        private final float width;
        /**
         * 这一类槽位使用的核心颜色。
         */
        private final Color coreColor;
        /**
         * 这一类槽位使用的尾端颜色。
         */
        private final Color trailColor;

        private SlotConfig(float length, float width, Color coreColor, Color trailColor) {
            this.length = length;
            this.width = width;
            this.coreColor = coreColor;
            this.trailColor = trailColor;
        }
    }

    private static final class BufferWithData {
        /**
         * FBO 宽度额外补边。
         *
         * 原设计思路就是“给很大一圈冗余”，避免拖尾被缓冲边界裁掉。
         *
         * 调大：
         * - 更不容易裁切
         * - 但显存占用更高
         *
         * 调小：
         * - 更省显存
         * - 但长拖尾更容易撞到 FBO 边缘
         */
        private static final int STATIC_WIDTH_EDGE = 2000;
        /**
         * FBO 高度额外补边。
         */
        private static final int STATIC_HEIGHT_EDGE = 2000;

        private final int width;
        private final int height;
        private final int textureId;
        private final int textureBufferId;

        private BufferWithData(int width, int height, int textureId, int textureBufferId) {
            this.width = width;
            this.height = height;
            this.textureId = textureId;
            this.textureBufferId = textureBufferId;
        }

        private static BufferWithData createBuffer(SpriteAPI sprite1, SpriteAPI sprite2) {
            int spriteWidth = 0;
            int spriteHeight = 0;
            if (sprite1 != null) {
                spriteWidth = Math.max(spriteWidth, (int) sprite1.getWidth());
                spriteHeight = Math.max(spriteHeight, (int) sprite1.getHeight());
            }
            if (sprite2 != null) {
                spriteWidth = Math.max(spriteWidth, (int) sprite2.getWidth());
                spriteHeight = Math.max(spriteHeight, (int) sprite2.getHeight());
            }

            int width = Math.max(1, spriteWidth) + STATIC_WIDTH_EDGE;
            int height = Math.max(1, spriteHeight) + STATIC_HEIGHT_EDGE;

            int textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

            int textureBufferId;
            if (ShaderLib.useBufferCore()) {
                textureBufferId = ShaderLib.makeFramebuffer(GL30.GL_COLOR_ATTACHMENT0, textureId, width, height, 0);
            } else if (ShaderLib.useBufferARB()) {
                textureBufferId = ShaderLib.makeFramebuffer(ARBFramebufferObject.GL_COLOR_ATTACHMENT0, textureId, width, height, 0);
            } else {
                textureBufferId = ShaderLib.makeFramebuffer(EXTFramebufferObject.GL_COLOR_ATTACHMENT0_EXT, textureId, width, height, 0);
            }

            if (textureBufferId == 0) {
                if (textureId != 0) {
                    GL11.glDeleteTextures(textureId);
                }
                return null;
            }

            return new BufferWithData(width, height, textureId, textureBufferId);
        }

        private int getWidth() {
            return width;
        }

        private int getHeight() {
            return height;
        }

        private int getTextureId() {
            return textureId;
        }

        private int getTextureBufferId() {
            return textureBufferId;
        }
    }
}
