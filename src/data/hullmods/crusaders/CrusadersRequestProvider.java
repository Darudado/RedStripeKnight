package data.hullmods.crusaders;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

import data.hullmods.crusaders.CrusadersPlateRenderer.Request;

/**
 * Programmed for 百里连山.
 * @author luoxb
 */
class CrusadersRequestProvider {
    private final ShipAPI ship;
    private final SpriteAPI sprite;
    
    private final float[] hitDatas = new float[CrusadersPlateRenderer.HIT_DATA_SIZE];
    private int tail = 0, head = 0; 
    
    private final Map<BeamAPI, Float> beamDelay = new HashMap<>();
    
    private final float[] tempData = new float[CrusadersPlateRenderer.HIT_DATA_SIZE];
    private final Matrix4f tempModel = new Matrix4f();
    
    CrusadersRequestProvider(ShipAPI ship) {
        this.ship = ship;
        this.sprite = ship.getSpriteAPI();
    }
    
    public void putPoint(Vector2f pointInWorld, float radius, boolean radiusInPx,@Nullable BeamAPI source) {
    	float time = Global.getCombatEngine().getTotalElapsedTime(false);
    	
    	if (source != null) {
    		float stamp = beamDelay.computeIfAbsent(source, b->Global.getCombatEngine().getTotalElapsedTime(false));
    		if (time - stamp > CrusadersPlateRenderer.BEAM_HIT_INTERVAL) beamDelay.put(source, time);
    		else return;
    	}
    	
        Vector2f offset = Vector2f.sub(pointInWorld, ship.getLocation(), null);
        float angle = -(ship.getFacing() - 90f); 
        VectorUtils.rotate(offset, angle, offset); 
        
        float width = sprite.getWidth();
        float height = sprite.getHeight(); 
        float u = offset.x / width;
        float v = offset.y / height; 
        float r = radiusInPx ? (radius / width) : radius;
        
        putVec4(u, v, time, r);
    }
    
    private void putVec4(float x, float y, float stamp, float radius) {
        int nextHead = (head + 4) % hitDatas.length;
        if (nextHead == tail) 
            tail = (tail + 4) % hitDatas.length;
        
        hitDatas[head] = x;
        hitDatas[(head + 1) % hitDatas.length] = y;
        hitDatas[(head + 2) % hitDatas.length] = stamp;
        hitDatas[(head + 3) % hitDatas.length] = radius;
        
        head = nextHead;
    }
    
    public Request genRequest() {
        int pin = 0; 
        
        float currTime = Global.getCombatEngine().getTotalElapsedTime(false);
        float duration = CrusadersPlateRenderer.DURATION; 
        
        int current = tail;
        
        while (current != head) {
            float stamp = hitDatas[(current + 2) % hitDatas.length];
            if (currTime - stamp > duration) {
                tail = (tail + 4) % hitDatas.length;
                current = tail;
                continue;
            }
            
            tempData[pin++] = hitDatas[current];
            tempData[pin++] = hitDatas[(current+1) % hitDatas.length];
            tempData[pin++] = hitDatas[(current+2) % hitDatas.length];
            tempData[pin++] = hitDatas[(current+3) % hitDatas.length];
            
            current = (current+4) % hitDatas.length;
        }
        
        if (pin == 0) return null;
        
        CrusadersPlateRenderer.genModelMatrix(ship, tempModel); 
        return new Request(tempData, pin/4, tempModel);
    }
}