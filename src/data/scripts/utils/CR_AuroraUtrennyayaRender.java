package data.scripts.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;
import java.util.List;

public class CR_AuroraUtrennyayaRender extends BaseCombatLayeredRenderingPlugin {
    protected SpriteAPI texture = null;
    private final List<Flare> flares = new ArrayList<>();
    protected AuroraParams params;
    private Vector2f holoGenPoint;
    private CombatEntityAPI phantom;
    private float phaseAngle;
    private boolean isExpired = false;
    public CR_AuroraUtrennyayaRender(CombatEntityAPI entity, CombatEntityAPI phantom) {
        this.entity = entity;
        this.phantom = phantom;
    }
    public boolean hasPhantom(){
        return null != phantom;
    }
    public void startRender(CombatEntityAPI phantom) {
        this.phantom = phantom;
    }
    public void endRender() {
        this.flares.clear();
        this.phantom = null;
        cleanup();
        this.isExpired = true;
    }
    public void setReaderLoc(Vector2f loc) {
        this.holoGenPoint = loc;
    }
    private Vector2f getHoloGeneratorPoint(){
        if( null != holoGenPoint){
            return holoGenPoint;
        }
        return entity.getLocation();
    }

    @Override
    public void advance(float amount) {
        if (hasPhantom()) {
            if (!flares.isEmpty()) {
                Flare curr = flares.get(0);
                curr.fader.advance(amount);
                if (curr.fader.isFadedOut()) {
                    flares.remove(0);
                    if (!flares.isEmpty()) {
                        flares.get(0).fader.fadeIn();
                    }
                }
            }else{
                initNewFlareSequence(flares, params);
            }
            phaseAngle = Misc.normalizeAngle(phaseAngle + amount * 180f);
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return super.getActiveLayers();
    }

    @Override
    public float getRenderRadius() {
        return 1.0E8F;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        this.layer = CombatEngineLayers.ABOVE_SHIPS_LAYER;
        this.params = new AuroraParams(
                this.entity,
                10,
                1000f,
                new Color(90, 165, 255, 155),
                0.1f,
                new Color(50, 20, 110, 130),
                new Color(75, 0, 160),
                new Color(100, 30, 110, 190),
                new Color(150, 30, 120, 150),
                new Color(200, 50, 130, 190),
                new Color(250, 70, 150, 240),
                new Color(200, 80, 130, 255),
                new Color(150, 90, 150, 255),
                new Color(127, 0, 255)
        );
        this.texture = Global.getSettings().getSprite("terrain", "aurora");
    }

    @Override
    public boolean isExpired() {
        return this.isExpired;
    }


    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (hasPhantom()) {
            Vector2f location = phantom.getLocation();
            float collisionRadius = entity.getCollisionRadius();
            float phantomFacing = phantom.getFacing();
            Vector2f head = MathUtils.getPointOnCircumference(location, collisionRadius, phantomFacing);
            Vector2f tail = MathUtils.getPointOnCircumference(location, collisionRadius, phantomFacing + 180f);
            Vector2f from = getHoloGeneratorPoint();

            float startAnlge = VectorUtils.getAngle(from, head);
            float endAngle = VectorUtils.getAngle(from, tail);

            float rad = Math.abs(MathUtils.getShortestRotation(startAnlge, endAngle));
            rad = Math.max(rad,15f);

            Flare flare = null;
            if (!flares.isEmpty()) {
                flare = flares.get(0);
            }

            render(params, getHoloGeneratorPoint(), flare, rad, 1f, phaseAngle, viewport.getAlphaMult());
        }
    }




    private void render(AuroraParams params , Vector2f loc , Flare flare, float rad, float alpha, float phaseAngle, float alphaMult) {
        if (alphaMult <= 0) return;

        float bandWidthInTexture = 256f;
        float innerRadius = params.getInnerRadius();
        float outerRadius = params.getOuterRadius();

        if (outerRadius < innerRadius + 10f) {
            outerRadius = innerRadius + 10f;
        }

        float circ = (float) (Math.PI * 2f * (innerRadius + outerRadius) / 2f);
        float pixelsPerSegment = 50f;
        float segments = Math.round(circ / pixelsPerSegment);
        float startRad = (float) Math.toRadians(0f);
        float endRad = (float) Math.toRadians(rad);
        float spanRad = Math.abs(endRad - startRad);
        float anglePerSegment = spanRad / segments;

        if(null == loc || !MathUtils.isPointWithinCircle(loc,entity.getLocation(),entity.getCollisionRadius())){
            loc = entity.getLocation();
        }
        float targetAnlge = VectorUtils.getAngle(loc, phantom.getLocation());
        targetAnlge = MathUtils.clampAngle(targetAnlge - rad / 2) ;

        float x = loc.x;
        float y = loc.y;

        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0);
        GL11.glRotatef(targetAnlge, 0, 0, 1);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        texture.bindTexture();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        float thickness = (outerRadius - innerRadius);
        float texProgress = 0f;
        float texHeight = texture.getTextureHeight();
        float imageHeight = texture.getHeight();
        float texPerSegment = pixelsPerSegment * texHeight / imageHeight * bandWidthInTexture / thickness;
        float totalTex = Math.max(1f, Math.round(texPerSegment * segments));

        texPerSegment = totalTex / segments;
        float texWidth = texture.getTextureWidth();
        float imageWidth = texture.getWidth();
        float leftTX = texWidth * bandWidthInTexture / imageWidth;
        float rightTX = 2f * texWidth * bandWidthInTexture / imageWidth - 0.001f;

        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (float i = 0; i < segments + 1; i++) {

            float segIndex = i % (int) segments;
            float phaseAngleRad = (float) Math.toRadians(phaseAngle) + (segIndex * anglePerSegment * 10f);
            float angle = (float) Math.toDegrees(segIndex * anglePerSegment);

            float pulseSin = (float) FastTrig.sin(phaseAngleRad);
            float pulseMax = thickness * getShortenMod(flare, angle);
            float pulseAmount = pulseSin * pulseMax;//此次投影的长度

            float pulseInner = pulseAmount * 0.1f * getInnerOffsetMult(flare, angle);
            float thicknessMult = getExtraLengthMult(flare, angle);
            float thicknessFlat = getExtraLengthFlat(flare, angle);

            float theta = anglePerSegment * segIndex;
            float cos = (float) FastTrig.cos(theta);
            float sin = (float) FastTrig.sin(theta);

            float rInner = innerRadius - pulseInner;
            if (rInner < innerRadius * 0.9f) rInner = innerRadius * 0.9f;//最小范围渲染范围界定
            float rOuter = (innerRadius + thickness * thicknessMult - pulseAmount + thicknessFlat);//最大范围界定

            float x1 = cos * rInner;
            float y1 = sin * rInner;
            float x2 = cos * rOuter;
            float y2 = sin * rOuter;

            x2 += (float) (FastTrig.cos(phaseAngleRad) * pixelsPerSegment * 0.33f);
            y2 += (float) (FastTrig.sin(phaseAngleRad) * pixelsPerSegment * 0.33f);


            Color color = getColorForAngle(flare, params.getBaseColor() ,angle);
            GL11.glColor4ub((byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue(), (byte) ((float) color.getAlpha() * alphaMult * alpha));
            GL11.glTexCoord2f(leftTX, texProgress);
            GL11.glVertex2f(x1, y1);
            GL11.glTexCoord2f(rightTX, texProgress);
            GL11.glVertex2f(x2, y2);

            texProgress += texPerSegment;
        }
        GL11.glEnd();
        GL11.glPopMatrix();
    }


    protected void initNewFlareSequence(List<Flare> flares, AuroraParams params) {
        flares.clear();
        int numSmall = 7 - (int) Math.ceil(5f * (float) Math.random());
        Flare large = genLargeFlare(params);//生成大片的，大片的持续时间
        for (int i = 0; i < numSmall; i++) {
            flares.add(genSmallFlare(params, large.direction, large.arc));
        }
        flares.add(large);
        flares.get(0).fader.fadeIn();
    }

    protected Flare genLargeFlare(AuroraParams params) {
        Flare flare = new Flare();
        flare.direction =
                Flare.FlareOccurrenceAngle -
                        Flare.FlareOccurrenceArc / 2f +
                        (float) Math.random() * Flare.FlareOccurrenceArc; // 0 ~ 360°
        flare.arc = Flare.FlareArcMin + (Flare.FlareArcMax - Flare.FlareArcMin) * (float) Math.random(); //80° + 30° * random()
        flare.extraLengthFlat = Flare.FlareExtraLengthFlatMin + (Flare.FlareExtraLengthFlatMax - Flare.FlareExtraLengthFlatMin) * (float) Math.random(); //0
        flare.extraLengthMult = Flare.FlareExtraLengthMultMin + (Flare.FlareExtraLengthMultMax - Flare.FlareExtraLengthMultMin) * (float) Math.random(); //1
        flare.shortenFlatMod = Flare.FlareShortenFlatModMin + (Flare.FlareShortenFlatModMax - Flare.FlareShortenFlatModMin) * (float) Math.random(); // 0.8f
        flare.fader = new FaderUtil(
                0,
                Flare.FlareFadeInMin + (float) Math.random(),//淡入1s + 1s * random()
                Flare.FlareFadeOutMin + (Flare.FlareFadeOutMax - Flare.FlareFadeOutMin) * (float) Math.random(),//淡出2s + 3s * random()
                false,
                true);
        flare.direction = Misc.normalizeAngle(flare.direction);

        setColors(flare, params);
        return flare;
    }

    protected Flare genSmallFlare(AuroraParams params, float dir, float arc) {
        Flare flare = new Flare();
        flare.direction = dir - arc / 2f + (float) Math.random() * arc;
        flare.arc = Flare.FlareSmallArcMin + (Flare.FlareSmallArcMax - Flare.FlareSmallArcMin) * (float) Math.random(); //10° + 10° * random()
        flare.extraLengthFlat = Flare.FlareSmallExtraLengthFlatMin + (Flare.FlareSmallExtraLengthFlatMax - Flare.FlareSmallExtraLengthFlatMin) * (float) Math.random();//0
        flare.extraLengthMult = Flare.FlareSmallExtraLengthMultMin + (Flare.FlareSmallExtraLengthMultMax - Flare.FlareSmallExtraLengthMultMin) * (float) Math.random(); //1
        flare.shortenFlatMod = Flare.FlareSmallShortenFlatModMin + (Flare.FlareSmallShortenFlatModMax - Flare.FlareSmallShortenFlatModMin) * (float) Math.random();// 0.8f
        flare.fader = new FaderUtil(
                0,
                Flare.FlareSmallFadeInMin + (Flare.FlareSmallFadeInMax - Flare.FlareSmallFadeInMin) * (float) Math.random(),//淡入0.5s + 0.5s * random()
                Flare.FlareSmallFadeOutMin + (Flare.FlareSmallFadeOutMax - Flare.FlareSmallFadeOutMin) * (float) Math.random(),//淡入0.5s + 0.5s * random()
                false,
                true);
        setColors(flare, params);
        return flare;
    }

    protected void setColors(Flare flare, AuroraParams auroraParams) {
        float colorRangeFraction = flare.arc / Flare.FlareArcMax;
        List<Color> flareColorRange = auroraParams.getAuroraColorRange();
        int totalColors = flareColorRange.size();
        int numColors = Math.max(1, Math.round(colorRangeFraction * totalColors));
        flare.colors.clear();
        ArrayList<Color> colors = new ArrayList<>(flareColorRange);
        Collections.shuffle(colors);
        flare.colors.addAll(colors.subList(0, Math.min(colors.size(), numColors)));
    }

    public float getExtraLengthFlat(Flare curr, float angle) {
        if (curr == null) return 0f;
        if (!Misc.isInArc(curr.direction, curr.arc, angle)) return 0f;
        return curr.extraLengthFlat * (float) Math.sqrt(curr.fader.getBrightness());
    }

    public float getExtraLengthMult(Flare curr, float angle) {
        if(null == curr)return 1f;
        if (!Misc.isInArc(curr.direction, curr.arc, angle)) return 1f;
        return 1f + (curr.extraLengthMult - 1f) * (float) Math.sqrt(curr.fader.getBrightness());
    }

    public float getShortenMod(Flare curr, float angle) {
        if(null == curr)return 0f;
        if (!Misc.isInArc(curr.direction, curr.arc, angle)) return 0f;
        return curr.shortenFlatMod * (float) Math.sqrt(curr.fader.getBrightness());
    }

    public float getInnerOffsetMult(Flare curr, float angle) {
        if(null == curr)return 0f;
        if (!Misc.isInArc(curr.direction, curr.arc, angle)) return 0f;
        return (float) Math.sqrt(curr.fader.getBrightness());
    }
    public Color getColorForAngle(Flare curr, Color baseColor, float angle) {
        if (null == curr) return baseColor;
        if (!Misc.isInArc(curr.direction, curr.arc, angle)) return baseColor;

        angle = Misc.normalizeAngle(angle);

        float arcStart = curr.direction - curr.arc / 2f;
        float arcEnd = curr.direction + curr.arc / 2f;

        angle -= arcStart;
        if (angle < 0) angle += 360f;

        float progress = angle / (arcEnd - arcStart);
        progress = MathUtils.clamp(progress, 0, 1);


        float numColors = curr.colors.size();

        float fractionalIndex = ((numColors - 1f) * progress);
        int colorOne = (int) fractionalIndex;
        int colorTwo = (int) Math.ceil(fractionalIndex);

        float interpProgress = fractionalIndex - (int) fractionalIndex;
        Color one = curr.colors.get(colorOne);
        Color two = curr.colors.get(colorTwo);

        Color result = Misc.interpolateColor(one, two, interpProgress);
        result = Misc.interpolateColor(baseColor, result, curr.fader.getBrightness());

        return result;
    }

    public static class Flare {
        private static final float FlareSmallArcMax = 240;
        private static final float FlareSmallArcMin = 40;
        private static final float FlareArcMax = 180;
        private static final float FlareArcMin = 90;
        private static final float FlareSmallExtraLengthFlatMax = 0;
        private static final float FlareSmallExtraLengthFlatMin = 0;
        private static final float FlareSmallExtraLengthMultMax = 1;
        private static final float FlareSmallExtraLengthMultMin = 1;
        private static final float FlareExtraLengthFlatMax = 0;
        private static final float FlareExtraLengthFlatMin = 0;
        private static final float FlareExtraLengthMultMax = 1;
        private static final float FlareExtraLengthMultMin = 1;
        private static final float FlareSmallFadeInMax = 1f;
        private static final float FlareSmallFadeInMin = 0.5f;
        private static final float FlareSmallFadeOutMax = 1f;
        private static final float FlareSmallFadeOutMin = 0.5f;
        private static final float FlareFadeInMin = 1f;
        private static final float FlareFadeOutMax = 5f;
        private static final float FlareFadeOutMin = 2f;
        private static final float FlareShortenFlatModMax = 0.8f;
        private static final float FlareShortenFlatModMin = 0.8f;
        private static final float FlareSmallShortenFlatModMax = 0.8f;
        private static final float FlareSmallShortenFlatModMin = 0.8f;
        private static final float FlareOccurrenceAngle = 0;
        private static final float FlareOccurrenceArc = 360f;
        public float direction;

        public float arc;
        public float extraLengthMult;
        public float extraLengthFlat;
        public float shortenFlatMod;
        transient public List<Color> colors = new ArrayList<>();
        public FaderUtil fader;

    }

    public static class AuroraParams {
        public Color baseColor;
        transient public List<Color> auroraColorRange = new ArrayList<>();
        public float auroraFrequency;
        public float innerRadius;
        public float outerRadius;
        public CombatEntityAPI relatedEntity;

        public AuroraParams(CombatEntityAPI relatedEntity, float innerRadius, float outerRadius, Color baseColor, float auroraFrequency) {
            this(relatedEntity, innerRadius, outerRadius, baseColor, auroraFrequency, Color.red, Color.orange, Color.yellow, Color.green, Color.blue, new Color(75, 0, 130), new Color(127, 0, 255));
        }
        public AuroraParams(CombatEntityAPI relatedEntity, float innerRadius, float outerRadius, Color baseColor, float auroraFrequency, Color... auroraColors) {
            this.auroraFrequency = auroraFrequency;
            this.baseColor = baseColor;
            this.innerRadius = innerRadius;
            this.outerRadius = outerRadius;
            this.relatedEntity = relatedEntity;
            if (auroraColors != null) {
                Collections.addAll(this.auroraColorRange, auroraColors);
            }
        }

        public Color getBaseColor() {
            return baseColor;
        }

        public List<Color> getAuroraColorRange() {
            return auroraColorRange;
        }

        public float getAuroraFrequency() {
            return auroraFrequency;
        }

        public float getInnerRadius() {
            return innerRadius;
        }

        public float getOuterRadius() {
            return outerRadius;
        }

        public CombatEntityAPI getRelatedEntity() {
            return relatedEntity;
        }



    }


}
