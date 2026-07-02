package data.shade;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.dark.shaders.util.ShaderLib;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.BufferUtils;
import java.nio.FloatBuffer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

import data.hullmods.RS_Moci_GNShield_Script;

/**
 * 高性能GN护盾渲染器 - 基于着色器技术
 * 结合光环src的性能优势和GN护盾的特色效果
 */
public class Moci_GNShieldRendering_Shader extends BaseCombatLayeredRenderingPlugin {

    public static final String RENDER_KEY = "Moci_GNShieldRendering_Shader";
    private final CombatEngineAPI engine = Global.getCombatEngine();

    // 着色器系统
    private int shader = 0;
    private int[] uniform;
    private boolean shaderInitialized = false;
    private Moci_ShaderManager shaderManager;
    
    // 着色器状态优化
    private static int currentShader = -1; // 当前绑定的着色器，避免重复绑定
    private static boolean shaderStateInitialized = false; // 纹理单元是否已初始化
    
    // 性能统计（可选）
    private static int shaderBindCount = 0; // 着色器绑定次数统计
    private static int uniformUpdateCount = 0; // uniform更新次数统计

    // GPU优化：uniform缓存系统，减少GPU-CPU通信
    private float cachedType = -999f;           // uniform[2] 缓存
    private float cachedSpreadLevel = -999f;    // uniform[3].x/y 缓存
    private float cachedMaxSize = -999f;        // uniform[3].z 缓存
    private float cachedCombinedAlpha = -999f;  // uniform[3].w 缓存
    private float cachedFluxLevel = -999f;      // uniform[5] 缓存
    // uniform[4] (GN护盾颜色) 是固定值，不需要缓存
    
    // 浮点数比较的精度阈值
    private static final float UNIFORM_EPSILON = 0.0001f;

    // GPU优化：VBO系统，预计算顶点数据
    private int shieldQuadVBO = -1;         // 顶点缓冲对象ID
    private boolean vboInitialized = false; // VBO初始化标志
    private static final int VERTICES_PER_QUAD = 4;    // 每个四边形4个顶点
    private static final int FLOATS_PER_VERTEX = 4;    // 每个顶点4个float (x, y, u, v)
    
    // 着色器文件路径
    private static final String SHADER_VERT_PATH = "data/shaders/Moci_GNShield.vert";
    private static final String SHADER_FRAG_PATH = "data/shaders/Moci_GNShield.frag";
    private static final String SHADER_KEY = "Moci_GNShield";

    // GN护盾扩散效果系统
    private final Map<ShipAPI, Float> shieldSpreadElapsed = new HashMap<>();
    private static final float SPREAD_CYCLE_TIME = 10f; // GN护盾扩散周期：6秒完整循环（扩散+回收）

    public Moci_GNShieldRendering_Shader() {
        super();
        initializeShader();
    }

    private void initializeShader() {
        try {
            // 获取着色器管理器实例
            shaderManager = Moci_ShaderManager.getInstance();
            
            if (!shaderManager.isOpenGL20Supported()) {
                return;
            }
            
            // 通过着色器管理器创建着色器
            shader = shaderManager.createShader(SHADER_VERT_PATH, SHADER_FRAG_PATH, SHADER_KEY);
            
            if (shader != 0 && shaderManager.isShaderValid(SHADER_KEY)) {
                GL20.glUseProgram(shader);
                uniform = new int[] {
                    GL20.glGetUniformLocation(shader, "shieldTex"),
                    GL20.glGetUniformLocation(shader, "fxTex"), 
                    GL20.glGetUniformLocation(shader, "type"),
                    GL20.glGetUniformLocation(shader, "state"),
                    GL20.glGetUniformLocation(shader, "gnColor"),
                    GL20.glGetUniformLocation(shader, "fluxLevel")
                };
                
                // 验证uniform位置
                boolean uniformsValid = true;
                for (int i = 0; i < uniform.length; i++) {
                    if (uniform[i] == -1) {
                        uniformsValid = false;
                    }
                }
                
                if (uniformsValid) {
                    // 纹理单元设置将在首次绑定时进行（延迟初始化优化）
                    GL20.glUseProgram(0);
                    
                    shaderInitialized = true;
                    
                    // 着色器预热：触发首次绑定以优化后续性能
                    warmupShader();
                } else {
                    shaderInitialized = false;
                }
            } else {
                shaderInitialized = false;
            }
            
            // 输出着色器管理器统计信息
            shaderManager.printShaderStats();
            
        } catch (Exception e) {
            shaderInitialized = false;
        }
    }

    public static Moci_GNShieldRendering_Shader getInstance() {
        if (Global.getCombatEngine() != null &&
                Global.getCombatEngine().getCustomData().containsKey(RENDER_KEY)) {
            return (Moci_GNShieldRendering_Shader) Global.getCombatEngine().getCustomData().get(RENDER_KEY);
        } else {
            Moci_GNShieldRendering_Shader renderer = new Moci_GNShieldRendering_Shader();
            if (Global.getCombatEngine() != null) {
                Global.getCombatEngine().addLayeredRenderingPlugin(renderer);
                Global.getCombatEngine().getCustomData().put(RENDER_KEY, renderer);
            }
            return renderer;
        }
    }

    @Override
    public void init(CombatEntityAPI entity) {
    }

    @Override
    public void cleanup() {
        // 清理VBO资源，防止内存泄漏
        if (vboInitialized && shieldQuadVBO != -1) {
            GL15.glDeleteBuffers(shieldQuadVBO);
            shieldQuadVBO = -1;
            vboInitialized = false;
        }
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (Global.getCombatEngine().isPaused())
            return;

        // 更新护盾扩散效果
        updateShieldSpread(amount);
    }

    private void updateShieldSpread(float amount) {
        // 为每个有GN护盾的船只维护连续的扩散时间
        for (ShipAPI ship : engine.getShips()) {
            if (RS_Moci_GNShield_Script.hasShield(ship) && ship.getShield() != null && ship.getShield().isOn()) {
                Float elapsedObj = shieldSpreadElapsed.get(ship);
                float elapsed = elapsedObj != null ? elapsedObj : 0f;
                elapsed += amount;
                if (elapsed >= SPREAD_CYCLE_TIME) {
                    elapsed -= SPREAD_CYCLE_TIME;
                }
                shieldSpreadElapsed.put(ship, elapsed);
            }
        }

        // 清理不再需要的数据
        for (ShipAPI ship : new ArrayList<>(shieldSpreadElapsed.keySet())) {
            if (!RS_Moci_GNShield_Script.hasShield(ship) || ship.getShield() == null || !ship.getShield().isOn()) {
                shieldSpreadElapsed.remove(ship);
            }
        }
    }

    private float getShieldSpreadLevel(ShipAPI ship) {
        Float elapsed = shieldSpreadElapsed.get(ship);
        if (elapsed == null) {
            return 0f;
        }
        
        // 简单的扩散等级：直接基于周期时间
        return elapsed / SPREAD_CYCLE_TIME;
    }

    /**
     * 智能着色器绑定，避免重复的状态切换
     */
    private void bindShaderOptimized() {
        if (!shaderInitialized) return;
        
        if (currentShader != shader) {
            GL20.glUseProgram(shader);
            currentShader = shader;
            shaderBindCount++; // 统计绑定次数
            
            // 只在首次绑定时设置纹理单元
            if (!shaderStateInitialized) {
                GL20.glUniform1i(uniform[0], 0); // shieldTex -> GL_TEXTURE0
                GL20.glUniform1i(uniform[1], 1); // fxTex -> GL_TEXTURE1
                shaderStateInitialized = true;
            }
        }
    }
    
    /**
     * 解绑着色器，恢复固定管线
     */
    private static void unbindShader() {
        if (currentShader != 0) {
            GL20.glUseProgram(0);
            currentShader = 0;
        }
    }
    
    /**
     * 着色器预热：初始化时触发首次绑定，优化后续性能
     */
    private void warmupShader() {
        if (!shaderInitialized) return;
        
        try {
            // 触发首次绑定，初始化纹理单元
            bindShaderOptimized();
            
            // 设置一些默认uniform值，让GPU预热着色器
            if (shaderStateInitialized) {
                GL20.glUniform1f(uniform[2], 0.0f); // type
                GL20.glUniform4f(uniform[3], 0.0f, 256.0f, 256.0f, 1.0f); // state
                GL20.glUniform3f(uniform[4], 0.2f, 0.4f, 1.0f); // gnColor
                GL20.glUniform1f(uniform[5], 0.0f); // fluxLevel
            }
            
            // 解绑
            unbindShader();
            
        } catch (Exception e) {
            // Shader warmup failed, continue with fallback
        }
    }

    private float calculateTotalShipSize(ShipAPI ship) {
        // 计算包含模块的总尺寸
        float maxSize = Math.max(ship.getSpriteAPI().getHeight(), ship.getSpriteAPI().getWidth());

        if (ship.getChildModulesCopy() != null) {
            for (ShipAPI module : ship.getChildModulesCopy()) {
                if (module.getSpriteAPI() != null) {
                    float moduleSize = Math.max(module.getSpriteAPI().getHeight(), module.getSpriteAPI().getWidth());
                    float distance = (float) Math.sqrt(
                            Math.pow(module.getLocation().x - ship.getLocation().x, 2) +
                                    Math.pow(module.getLocation().y - ship.getLocation().y, 2));
                    maxSize = Math.max(maxSize, (distance + moduleSize) * 1.2f);
                }
            }
        }

        return maxSize;
    }

    private boolean shouldRenderWeapon(WeaponAPI weapon) {
        // 优化的武器过滤逻辑
        if (weapon.getSprite() == null)
            return false;
        if (weapon.isDisabled())
            return false;
        
        // 只渲染可见的硬点和炮塔武器
        return !weapon.getSlot().isHidden() && 
               (weapon.getSlot().isBuiltIn() || weapon.getSlot().isDecorative());
    }

    /**
     * GPU优化：按纹理ID分组武器，减少纹理绑定次数
     * 注意：保持Java7兼容性，不使用diamond运算符
     */
    private Map<Integer, ArrayList<WeaponAPI>> groupWeaponsByTexture(ShipAPI ship) {
        Map<Integer, ArrayList<WeaponAPI>> weaponsByTexture = new HashMap<Integer, ArrayList<WeaponAPI>>();
        
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.getSprite() != null && shouldRenderWeapon(weapon)) {
                int textureId = weapon.getSprite().getTextureId();
                
                ArrayList<WeaponAPI> weaponList = weaponsByTexture.get(textureId);
                if (weaponList == null) {
                    weaponList = new ArrayList<WeaponAPI>();
                    weaponsByTexture.put(textureId, weaponList);
                }
                weaponList.add(weapon);
            }
        }
        
        return weaponsByTexture;
    }

    /**
     * GPU优化：按纹理ID分组模块，减少纹理绑定次数
     * 注意：保持Java7兼容性，不使用diamond运算符
     */
    private Map<Integer, ArrayList<ShipAPI>> groupModulesByTexture(ShipAPI ship) {
        Map<Integer, ArrayList<ShipAPI>> modulesByTexture = new HashMap<Integer, ArrayList<ShipAPI>>();
        
        if (ship.getChildModulesCopy() != null) {
            for (ShipAPI module : ship.getChildModulesCopy()) {
                if (module.getSpriteAPI() != null) {
                    int textureId = module.getSpriteAPI().getTextureId();
                    
                    ArrayList<ShipAPI> moduleList = modulesByTexture.get(textureId);
                    if (moduleList == null) {
                        moduleList = new ArrayList<ShipAPI>();
                        modulesByTexture.put(textureId, moduleList);
                    }
                    moduleList.add(module);
                }
            }
        }
        
        return modulesByTexture;
    }

    /**
     * GPU优化：智能uniform设置，只在值真正改变时才发送到GPU
     */
    private void setUniform1fCached(int location, float value, float cachedValue) {
        if (Math.abs(value - cachedValue) > UNIFORM_EPSILON) {
            GL20.glUniform1f(location, value);
            uniformUpdateCount++; // 统计uniform更新次数
            // 注意：调用方需要自己更新缓存值
        }
    }

    private void setUniform4fCached(int location, float x, float y, float z, float w, 
                                   float cachedX, float cachedY, float cachedZ, float cachedW) {
        if (Math.abs(x - cachedX) > UNIFORM_EPSILON || 
            Math.abs(y - cachedY) > UNIFORM_EPSILON ||
            Math.abs(z - cachedZ) > UNIFORM_EPSILON ||
            Math.abs(w - cachedW) > UNIFORM_EPSILON) {
            GL20.glUniform4f(location, x, y, z, w);
            uniformUpdateCount++; // 统计uniform更新次数
            // 注意：调用方需要自己更新缓存值
        }
    }

    /**
     * GPU优化：初始化VBO，创建标准化的护盾四边形
     */
    private void initShieldQuadVBO() {
        if (vboInitialized) return;
        
        try {
            // 创建标准化的四边形顶点数据 (位置 + 纹理坐标)
            // 格式：每个顶点 = (x, y, u, v)
            float[] quadVertices = {
                // 左下角
                -0.5f, -0.5f,   0.0f, 0.0f,
                // 左上角
                -0.5f,  0.5f,   0.0f, 1.0f,
                // 右上角
                 0.5f,  0.5f,   1.0f, 1.0f,
                // 右下角
                 0.5f, -0.5f,   1.0f, 0.0f
            };
            
            // 创建FloatBuffer
            FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(quadVertices.length);
            vertexBuffer.put(quadVertices);
            vertexBuffer.flip();
            
            // 生成并绑定VBO
            shieldQuadVBO = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, shieldQuadVBO);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0); // 解绑
            
            vboInitialized = true;
            
        } catch (Exception e) {
            vboInitialized = false;
        }
    }

    /**
     * GPU优化：使用VBO渲染护盾四边形
     */
    private void renderShieldQuadVBO(float scaleX, float scaleY) {
        if (!vboInitialized) {
            initShieldQuadVBO();
            if (!vboInitialized) return; // 初始化失败，回退到立即模式
        }
        
        // 绑定VBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, shieldQuadVBO);
        
        // 启用顶点数组
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        
        // 设置顶点指针 (stride = 4 floats per vertex)
        GL11.glVertexPointer(2, GL11.GL_FLOAT, FLOATS_PER_VERTEX * 4, 0);              // 位置 (x, y)
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, FLOATS_PER_VERTEX * 4, 2 * 4);       // 纹理坐标 (u, v)
        
        // 应用缩放
        GL11.glScalef(scaleX, scaleY, 1.0f);
        
        // 绘制四边形 (4个顶点)
        GL11.glDrawArrays(GL11.GL_QUADS, 0, VERTICES_PER_QUAD);
        
        // 禁用顶点数组
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        
        // 解绑VBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }



    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
    }

    @Override
    public float getRenderRadius() {
        return 1000000f;
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer == CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER) {
            // 智能着色器绑定优化
            bindShaderOptimized();
            
            for (ShipAPI ship : engine.getShips()) {
                if (RS_Moci_GNShield_Script.hasShield(ship) &&
                        ShaderLib.isOnScreen(ship.getLocation(), 2f * ship.getCollisionRadius()) &&
                        !ship.isStationModule()) {
                    processShield(ship);
                }
            }
            
            // 解绑着色器（在所有渲染完成后）
            unbindShader();
        }
    }

    protected void processShield(ShipAPI ship) {
        if (ship.getShield() == null || !ship.getShield().isOn()) {
            return;
        }

        // 获取贴图
        SpriteAPI shield = Global.getSettings().getSprite("fx", "Moci_GNShield");
        SpriteAPI hitFx = Global.getSettings().getSprite("fx", "Moci_GNShield_fog");

        // 计算渲染尺寸
        float max = Math.max(ship.getSpriteAPI().getHeight(), ship.getSpriteAPI().getWidth());
        max = Math.max(512f, max);  // 最小尺寸
        max *= 1.1f;                // 的缩放系数
        Vector2f size = new Vector2f(max, max);
        Vector2f uv = new Vector2f(1.0f, 1.0f);

        // 计算透明度和效果参数
        float combinedShieldAlphaBase = ship.getAlphaMult() * ship.getExtraAlphaMult() * ship.getExtraAlphaMult2();
        float combinedShieldAlpha = combinedShieldAlphaBase * 1.0f; // 恢复简单透明度
        float spreadLevel = getShieldSpreadLevel(ship);
        float fluxLevel = ship.getFluxLevel();

        // OpenGL状态设置
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        // 第一步：绘制船只轮廓到模板缓冲
        GL11.glColorMask(false, false, false, false);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 255);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.2f);

        if (shaderInitialized) {
            // GPU优化：缓存uniform设置 - 模板渲染模式
            setUniform1fCached(uniform[2], 3.0f, cachedType);
            if (Math.abs(3.0f - cachedType) > UNIFORM_EPSILON) {
                cachedType = 3.0f;
            }
        }

        // 渲染主舰船
        SpriteAPI ss = ship.getSpriteAPI();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ss.getTextureId());
        ss.renderAtCenter(ship.getLocation().x, ship.getLocation().y);

        // GPU优化：批处理渲染模块（按纹理分组）
        Map<Integer, ArrayList<ShipAPI>> modulesByTexture = groupModulesByTexture(ship);
        for (Map.Entry<Integer, ArrayList<ShipAPI>> entry : modulesByTexture.entrySet()) {
            // 每个纹理只绑定一次
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, entry.getKey());
            
            // 批量渲染所有使用此纹理的模块
            for (ShipAPI module : entry.getValue()) {
                SpriteAPI moduleSpr = module.getSpriteAPI();
                moduleSpr.renderAtCenter(module.getLocation().x, module.getLocation().y);
            }
        }

        // GPU优化：批处理渲染武器（按纹理分组）
        Map<Integer, ArrayList<WeaponAPI>> weaponsByTexture = groupWeaponsByTexture(ship);
        for (Map.Entry<Integer, ArrayList<WeaponAPI>> entry : weaponsByTexture.entrySet()) {
            // 每个纹理只绑定一次
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, entry.getKey());
            
            // 批量渲染所有使用此纹理的武器
            for (WeaponAPI weapon : entry.getValue()) {
                SpriteAPI weaponSpr = weapon.getSprite();
                weaponSpr.renderAtCenter(weapon.getLocation().x, weapon.getLocation().y);
            }
        }

        // 第二步：在船只轮廓内渲染护盾
        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 255);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glColorMask(true, true, true, true);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.0f);
        GL11.glDisable(GL11.GL_ALPHA_TEST);

        // 渲染护盾效果
        GL11.glPushMatrix();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, shield.getTextureId());
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, hitFx.getTextureId());

        GL11.glTranslatef(ship.getLocation().x, ship.getLocation().y, 0.0f);
        GL11.glRotatef(ship.getFacing(), 0.0f, 0.0f, 1.0f);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // 设置着色器参数（GN护盾定制版）- GPU优化版本
        if (shaderInitialized) {
            // type = 0: 普通扩散模式 - 缓存优化
            setUniform1fCached(uniform[2], 0.0f, cachedType);
            if (Math.abs(0.0f - cachedType) > UNIFORM_EPSILON) {
                cachedType = 0.0f;
            }
            
            // state: (spreadLevel, actualSize, effectiveSize, combinedAlpha) - 智能双尺寸处理
            float actualSize = Math.max(ship.getSpriteAPI().getHeight(), ship.getSpriteAPI().getWidth());
            // 确保着色器涟漪效果的最小可见性：最小128像素用于带宽计算
            float effectiveSize = Math.max(128f, actualSize);
            setUniform4fCached(uniform[3], spreadLevel, actualSize, effectiveSize, combinedShieldAlpha,
                             cachedSpreadLevel, cachedSpreadLevel, cachedMaxSize, cachedCombinedAlpha);
            if (Math.abs(spreadLevel - cachedSpreadLevel) > UNIFORM_EPSILON ||
                Math.abs(actualSize - cachedMaxSize) > UNIFORM_EPSILON ||
                Math.abs(combinedShieldAlpha - cachedCombinedAlpha) > UNIFORM_EPSILON) {
                cachedSpreadLevel = spreadLevel;
                cachedMaxSize = actualSize;
                cachedCombinedAlpha = combinedShieldAlpha;
            }
            
            // GN护盾颜色（基础蓝色）- 固定值，每次都设置（但GPU驱动会优化）
            GL20.glUniform3f(uniform[4], 0.2f, 0.4f, 1.0f);
            
            // 幅能等级（用于颜色混合）- 缓存优化
            setUniform1fCached(uniform[5], fluxLevel, cachedFluxLevel);
            if (Math.abs(fluxLevel - cachedFluxLevel) > UNIFORM_EPSILON) {
                cachedFluxLevel = fluxLevel;
            }
            

        } else {
            // 如果着色器未初始化，设置基础颜色（蓝色到红色渐变）
            float r = 0.2f + fluxLevel * 0.8f;
            float g = 0.4f - fluxLevel * 0.1f; 
            float b = 1.0f - fluxLevel * 0.8f;
            GL11.glColor4f(r, g, b, combinedShieldAlpha);
        }

        // GPU优化：使用VBO渲染护盾四边形（替代立即模式）
        if (!vboInitialized) {
            initShieldQuadVBO(); // 尝试初始化VBO
        }
        
        if (vboInitialized) {
            // VBO渲染：预计算顶点，GPU缓存，高性能
            renderShieldQuadVBO(size.x, size.y);
        } else {
            // 回退到立即模式（VBO初始化失败时）
            float halfX = size.x * 0.5f;
            float halfY = size.y * 0.5f;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0f, 0.0f);
            GL11.glVertex2f(-halfX, -halfY);
            GL11.glTexCoord2f(0.0f, uv.y);
            GL11.glVertex2f(-halfX, halfY);
            GL11.glTexCoord2f(uv.x, uv.y);
            GL11.glVertex2f(halfX, halfY);
            GL11.glTexCoord2f(uv.x, 0.0f);
            GL11.glVertex2f(halfX, -halfY);
            GL11.glEnd();
        }

        GL11.glPopMatrix();

        // 渲染击中效果
        renderHitEffects(ship, shield, hitFx, combinedShieldAlphaBase);

        // 清理OpenGL状态
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 255);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopAttrib();
    }

    private void renderHitEffects(ShipAPI ship, SpriteAPI shield, SpriteAPI hitFx, float combinedShieldAlphaBase) {
        // 获取击中效果数据
        RS_Moci_GNShield_Script script = RS_Moci_GNShield_Script.getInstance(ship);
        if (script == null) {
            return;
        }
        
        if (script.getHitDatas().isEmpty()) {
            // 调试：检查是否有击中数据
            return;
        }
        
        // 击中特效开始渲染

        GL11.glPushMatrix();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, shield.getTextureId());
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, hitFx.getTextureId());
        
        GL11.glTranslatef(ship.getLocation().x, ship.getLocation().y, 0.0f);
        GL11.glRotatef(ship.getFacing(), 0.0f, 0.0f, 1.0f);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (RS_Moci_GNShield_Script.Moci_GNShieldHitData hit : script.getHitDatas()) {
            if (shaderInitialized) {
                // type = 1: 击中效果模式
                GL20.glUniform1f(uniform[2], 1.0f + combinedShieldAlphaBase);
                
                // state: (life, size, relX, relY)
                Vector2f rel = hit.getRel();
                GL20.glUniform4f(uniform[3], hit.getLevel(), hit.getSize(), 
                    rel.x / 256.0f, rel.y / 256.0f);
                
                // 击中效果也需要设置颜色和幅能参数
                GL20.glUniform3f(uniform[4], 0.2f, 0.4f, 1.0f);
                GL20.glUniform1f(uniform[5], ship.getFluxLevel());
            }

            Vector2f size = new Vector2f(hit.getSize(), hit.getSize());
            Vector2f relPos = hit.getRel();

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0f, 0.0f);
            GL11.glVertex2f(-size.x + relPos.x, -size.y + relPos.y);
            GL11.glTexCoord2f(0.0f, 1.0f);
            GL11.glVertex2f(-size.x + relPos.x, size.y + relPos.y);
            GL11.glTexCoord2f(1.0f, 1.0f);
            GL11.glVertex2f(size.x + relPos.x, size.y + relPos.y);
            GL11.glTexCoord2f(1.0f, 0.0f);
            GL11.glVertex2f(size.x + relPos.x, -size.y + relPos.y);
            GL11.glEnd();
        }

        GL11.glPopMatrix();
    }

    // 着色器创建方法已移至Moci_ShaderManager

    public boolean isShaderInitialized() {
        return shaderInitialized;
    }
    
    /**
     * 获取性能统计报告（可用于调试）
     */
    public static String getPerformanceStats() {
        return String.format("Shader Performance - Binds: %d, Uniform Updates: %d", 
            shaderBindCount, uniformUpdateCount);
    }
    
    /**
     * 重置性能统计
     */
    public static void resetPerformanceStats() {
        shaderBindCount = 0;
        uniformUpdateCount = 0;
    }
}
