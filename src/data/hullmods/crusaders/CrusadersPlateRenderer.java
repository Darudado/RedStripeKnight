package data.hullmods.crusaders;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * Programmed for 百里连山.
 * @author luoxb
 */
public class CrusadersPlateRenderer extends BaseCombatLayeredRenderingPlugin {
	private final static String FX_SPRITE = "graphics/fx/crusaders_shieldTexture.png";
	private final static Color COLOR = new Color(205, 58, 58, 170);
	final static float DEFAULT_RIPPLE_SIZE = 0.5f;
	private final static float MAX_BAND_WIDTH = 0.9f;
	private final static float BASE_BAND_WIDTH = 0.2f;
	private final static float MAX_GLOW_RADIUS_FRAC = 0.75f;
	private final static float BASE_GLOW_RADIUS_FRAC = 0.05f;
	private final static float GLOW_TIME_MULT = 0.75f;
	private final static float RIPPLE_WEIGHT = 0.75f;
	private final static float INTENSITY = 1f;
	private final static float FX_DENSITY = 2f;
	private final static float GAMMA = 1f;
	private final static float SIZE_MULT = 1f;
	final static float DURATION = 0.75f;
	final static float BEAM_HIT_INTERVAL = 0.5f;
	
	private final static int MAX_HIT_DATA_CNT = 64; // less than or equals 64
	final static int HIT_DATA_SIZE = MAX_HIT_DATA_CNT * 4;

	private SpriteAPI fxSprite;
	
	private int programID;
	private int vao;
	private int vpMat, modelMat;
	private int fxTex, shipTex;
	private int ratio, texScale;
	private int hitData, hitCnt;
	private int currTime;
	
	private boolean initialized = false;
	
	public record Request (float[] hitData, int aHitCnt, Matrix4f modelMatrix) {}
	public record Batch (SpriteAPI sprite, List<Request> requests) {}
	private final Map<String, Batch> batches = new HashMap<>();
	
	private final FloatBuffer hitDataBuffer = BufferUtils.createFloatBuffer(HIT_DATA_SIZE);
	private final FloatBuffer mat4Buffer = BufferUtils.createFloatBuffer(16);
	
	private final Matrix4f cachedVpMat = new Matrix4f(); 
	
    public CrusadersPlateRenderer() {
        this.layer = CombatEngineLayers.ABOVE_SHIPS_LAYER;
    }
    
    public void initResources() {
    	this.programID = CrusadersCore.programID;
        
        this.fxTex = GL20.glGetUniformLocation(programID, "aFxTex");
        this.shipTex = GL20.glGetUniformLocation(programID, "aMaskTex");
        this.vpMat = GL20.glGetUniformLocation(programID, "aVpMat");
        this.modelMat = GL20.glGetUniformLocation(programID, "aModelMat");
        this.ratio = GL20.glGetUniformLocation(programID, "aHWRatio");
        this.texScale = GL20.glGetUniformLocation(programID, "aTexScale");
        this.hitData = GL20.glGetUniformLocation(programID, "aHitData");
        this.hitCnt = GL20.glGetUniformLocation(programID, "aHitCnt");
        this.currTime = GL20.glGetUniformLocation(programID, "aCurrTime");
        
        this.fxSprite = Global.getSettings().getSprite(FX_SPRITE);
        
        vao = GL30.glGenVertexArrays();
        
    	int prevVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
	    int prevVBO = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
	    
	    GL30.glBindVertexArray(vao);
	    
	    int index = GL31.glGetUniformBlockIndex(programID, "ubo");
		GL31.glUniformBlockBinding(programID, index, 0);
		int size = GL31.glGetActiveUniformBlocki(programID, index, GL31.GL_UNIFORM_BLOCK_DATA_SIZE);
	    
//		vec4 aCol; 
//	    float aMaxBandWidth;  
//	    float aBaseBandWidth; 
//	    float aMaxGlowRadiusFrac;
//	    float aBaseGlowRadiusFrac; 
//	    float aGlowTimeMult;
//	    float aRippleWeight;
//	    float aIntensity; 
//	    float aDuration; 
//		float aFxDensity;
//	    float aGamma;
		
		ByteBuffer buffer = BufferUtils.createByteBuffer(size);
		buffer.putFloat(COLOR.getRed()/255f);
		buffer.putFloat(COLOR.getGreen()/255f);
		buffer.putFloat(COLOR.getBlue()/255f);
		buffer.putFloat(COLOR.getAlpha()/255f);
		buffer.putFloat(MAX_BAND_WIDTH);
		buffer.putFloat(BASE_BAND_WIDTH);
		buffer.putFloat(MAX_GLOW_RADIUS_FRAC);
		buffer.putFloat(BASE_GLOW_RADIUS_FRAC);
		buffer.putFloat(GLOW_TIME_MULT);
		buffer.putFloat(RIPPLE_WEIGHT);
		buffer.putFloat(INTENSITY);
		buffer.putFloat(DURATION);
		buffer.putFloat(FX_DENSITY);
		buffer.putFloat(GAMMA);
		buffer.putFloat(0f);
		buffer.putFloat(0f);
		buffer.flip();
		
		int ubo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
		GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, buffer, GL15.GL_STATIC_READ);
		GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, 0, ubo);
	    
		float unit = 0.5f * SIZE_MULT;
	    float[] mesh = {
	    		//aUv,    aPos
			     0f, 1f, -unit,  unit,
			     0f, 0f, -unit, -unit,
			     1f, 1f,  unit,  unit,
			     1f, 0f,  unit, -unit,
			};
	    FloatBuffer meshBuf = BufferUtils.createFloatBuffer(16).put(mesh).flip();
	    int meshVbo = GL15.glGenBuffers();
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, meshVbo);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, meshBuf, GL15.GL_STATIC_DRAW);
		
		int stride = 4 * 4;
		
		GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0);
		GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 8);
		
		GL20.glEnableVertexAttribArray(0);
		GL20.glEnableVertexAttribArray(1);
		
		GL30.glBindVertexArray(prevVAO);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevVBO);
    }
    
    @Override
    public void init(CombatEntityAPI entity) {
    	batches.clear();
    }
    
    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
    	if (batches.isEmpty()) return;
    	
//    	var logger = Global.getLogger(getClass());
//    	var stamp = System.nanoTime();
//    	logger.info("Render begin.");
    	
    	if (!initialized) {
    		initialized = true;
    		initResources();
    	}
    	
    	GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
    	GL11.glEnable(GL11.GL_BLEND);
    	GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    	GL20.glUseProgram(programID);
    	
    	GL30.glBindVertexArray(vao);
		putPerFrameData(viewport);
		for (var entry : batches.entrySet()) {
    		Batch batch = entry.getValue();
    		putPerBatchData(batch);
    		for (var request: batch.requests) {
    			putPerShipData(request);
    			GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
    		}
    		batch.requests.clear();
    	}
    	GL30.glBindVertexArray(0);
    	
    	GL13.glActiveTexture(GL13.GL_TEXTURE1);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    	GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		
		GL20.glUseProgram(0);
		GL11.glPopAttrib();
		
//		logger.info("Render end. Time cost: "+(System.nanoTime()-stamp)/1000f + "mius");
//		logger.info("Time cost in percent: "+(System.nanoTime()-stamp)/10000000f/Global.getCombatEngine().getElapsedInLastFrame() + "%");
    }
    
    private void putPerFrameData(ViewportAPI vp) {
    	GL13.glActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, fxSprite.getTextureId());
		GL20.glUniform1i(fxTex, 0);
		
		GL20.glUniform1f(currTime, Global.getCombatEngine().getTotalElapsedTime(false));
		GL20.glUniformMatrix4(vpMat, false, genViewportMatrix(vp, cachedVpMat));
		GL13.glActiveTexture(GL13.GL_TEXTURE1);
    }
    
    private void putPerBatchData(Batch batch) {
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, batch.sprite.getTextureId());
		GL20.glUniform1i(shipTex, 1);
		
		GL20.glUniform1f(ratio, batch.sprite.getHeight()/batch.sprite.getWidth());
		GL20.glUniform2f(texScale, batch.sprite.getTexWidth(), batch.sprite.getTexHeight());
    }
    
    private void putPerShipData(Request request) {
    	hitDataBuffer.clear();
    	hitDataBuffer.put(request.hitData);
    	hitDataBuffer.flip();
    	GL20.glUniform4(hitData, hitDataBuffer);
    	
    	mat4Buffer.clear();
		request.modelMatrix.store(mat4Buffer); 
		mat4Buffer.flip(); 
		GL20.glUniformMatrix4(modelMat, false, mat4Buffer);
		
		GL20.glUniform1i(hitCnt, request.hitData.length/4);
    }
    
    public void submit(ShipAPI ship, @Nullable Request request) {
    	if (request == null) return;
    	var spec = ship.getHullSpec();
    	batches.computeIfAbsent(spec.getHullId(), 
    					id -> new Batch(Global.getSettings().getSprite(spec.getSpriteName()), new ArrayList<>())
    			).requests.add(request);
    }
    
    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER);}
    
    @Override
    public float getRenderRadius() {return Float.MAX_VALUE;}
    
    @Override
    public boolean isExpired() {return false;} 
    
    
    
    private static final FloatBuffer cachedVpBuffer = BufferUtils.createFloatBuffer(16);
    private static final CrusadersPlateRenderer instance = new CrusadersPlateRenderer();
    
    public static CrusadersPlateRenderer getInstance() {return instance;}

    private static FloatBuffer genViewportMatrix(ViewportAPI viewport, Matrix4f proj) {
        float llx = viewport.getLLX();
        float lly = viewport.getLLY();
        float width = viewport.getVisibleWidth();
        float height = viewport.getVisibleHeight();

        // 2/w  0    0   -(r+l)/(r-l)
        // 0    2/h  0   -(t+b)/(t-b)
        // 0    0   -2/(f-n) ...
        // 0    0    0    1

        if (proj == null) proj = new Matrix4f();
        proj.setIdentity();

        proj.m00 = 2f / width;
        proj.m11 = 2f / height;
        proj.m22 = -1f; 
        
        proj.m30 = - (2f * llx + width) / width;
        proj.m31 = - (2f * lly + height) / height;
        proj.m32 = 0f;
        
        cachedVpBuffer.clear();
        proj.store(cachedVpBuffer);
        cachedVpBuffer.flip();
        return cachedVpBuffer;
    }
    
    public static Matrix4f genModelMatrix(ShipAPI ship, Matrix4f model) {
        SpriteAPI sprite = ship.getSpriteAPI();
        float x = ship.getLocation().getX();
        float y = ship.getLocation().getY();
        float facing = ship.getFacing(); 
        float w = sprite.getWidth();
        float h = sprite.getHeight();
        float ox = sprite.getCenterX();
        float oy = sprite.getCenterY();
        Vector2f offset = new Vector2f(w/2f - ox, h/2f - oy);
        VectorUtils.rotate(offset, facing-90f);
        x += offset.x;
        y += offset.y;
        
        if (model == null) model = new Matrix4f();
        model.setIdentity();

        model.translate(new Vector3f(x, y, 0f));

        float radians = (float) Math.toRadians(facing - 90f);
        model.rotate(radians, new Vector3f(0f, 0f, 1f));

        model.scale(new Vector3f(w, h, 1f));
        return model;
    }
}