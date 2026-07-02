package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class RS_TargetingLinkRender extends BaseCombatLayeredRenderingPlugin {

    public static final Color CONTRAIL = new Color(187, 213, 255, 155);
    protected static final float BASEVEL = 50f;
    protected final SpriteAPI sprite;
    protected final float WIDTH = 12f;
    private List<RenderData> datas = new ArrayList<>();
    public static final String _KEY = "RS_RENDER_KEY";

    public RS_TargetingLinkRender() {
        sprite = Global.getSettings().getSprite("graphics/fx/Moci_linebeamcore.png");//填入光束材质
        layer = CombatEngineLayers.BELOW_SHIPS_LAYER;
    }

    public static RS_TargetingLinkRender getInstance(){
        if(Global.getCombatEngine().getCustomData()!=null&&Global.getCombatEngine().getCustomData().containsKey(_KEY)){
            return (RS_TargetingLinkRender) Global.getCombatEngine().getCustomData().get(_KEY);
        }else{
            RS_TargetingLinkRender r = new RS_TargetingLinkRender();
            Global.getCombatEngine().addLayeredRenderingPlugin(r);
            Global.getCombatEngine().getCustomData().put(_KEY,r);
            return r;
        }
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(layer);
    }

    @Override
    public float getRenderRadius() {
        return 10000f;
    }

    public void advance(float amount) {

        List<RenderData> toRemove = new ArrayList<>();
        for (RenderData data : datas) {
            data.elapsed += amount;
            data.textStart += amount * BASEVEL;
            if (data.elapsed > data.in + data.full + data.out) toRemove.add(data);
        }
        datas.removeAll(toRemove);
        toRemove.clear();
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (!datas.isEmpty()&&layer ==this.layer) {
            for (RenderData data : datas) {
                GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                GL11.glPushMatrix();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                sprite.bindTexture();
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glBegin(GL11.GL_QUAD_STRIP);
                float textLength = sprite.getWidth();
                float textProgress = data.textStart;
                float level = 1f;
                if (data.elapsed < data.in) {
                    level = 0.5f + 0.5f * data.elapsed / data.in;
                }
                if (data.elapsed > data.in + data.full) {
                    level = 1f - 0.5f * (data.elapsed - data.in - data.full);
                }
                float linkAngle = VectorUtils.getAngle(data.from, data.to);
                float width = WIDTH*level;
                float length = MathUtils.getDistance(data.from,data.to);
                Vector2f v0 = MathUtils.getPointOnCircumference(data.from, width, linkAngle + 90f);
                Vector2f v1 = MathUtils.getPointOnCircumference(data.from, width, linkAngle - 90f);
                GL11.glColor4ub((byte) data.core.getRed(), (byte) data.core.getGreen(), (byte) data.core.getBlue(), (byte) ((int)(data.core.getAlpha()*level)));
                GL11.glTexCoord2f(textProgress, 0f);
                GL11.glVertex2f(v0.x, v0.y);
                GL11.glTexCoord2f(textProgress, 1f);
                GL11.glVertex2f(v1.x, v1.y);
                Vector2f v2 = MathUtils.getPointOnCircumference(data.to, width, linkAngle + 90f);
                Vector2f v3 = MathUtils.getPointOnCircumference(data.to, width, linkAngle - 90f);
                textProgress += length / textLength;
                GL11.glTexCoord2f(textProgress, 0f);
                GL11.glVertex2f(v2.x, v2.y);
                GL11.glTexCoord2f(textProgress, 1f);
                GL11.glVertex2f(v3.x, v3.y);
                GL11.glEnd();
                GL11.glPopMatrix();
                GL11.glPopAttrib();
            }
        }
    }

    public void addRenderData(Vector2f from, Vector2f to, float in, float full, float out, Color core){
        datas.add(new RenderData(from,to,in,full,out,core));
    }

    public static class RenderData {
        public Vector2f from, to;
        public float in, full, out;
        public Color core;
        public float textStart;
        public float elapsed = 0;

        public RenderData(Vector2f from, Vector2f to, float in, float full, float out, Color core) {
            this.from = from;
            this.to = to;
            this.in = in;
            this.full = full;
            this.out = out;
            this.core = core;
        }
    }
}
