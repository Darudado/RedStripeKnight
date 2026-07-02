#version 430 core

// Const 
layout (std140, binding = 0) uniform ubo {
    vec4 aCol; 
    float aMaxBandWidth;  
    float aBaseBandWidth; 
    float aMaxGlowRadiusFrac;
    float aBaseGlowRadiusFrac; 
    float aGlowTimeMult;
    float aRippleWeight;
    float aIntensity; 
    float aDuration; 
    float aFxDensity;
    float aGamma;
    float _pad1;
    float _pad2;
};

// Per Frame
uniform sampler2D aFxTex;
uniform float aCurrTime;

// Per Batch
uniform sampler2D aMaskTex;
uniform float aHWRatio;  // H / W
uniform vec2 aTexScale;

// Per Ship
// x,y = Scaled Position
// z   = Time Stamp
// w   = Radius
uniform vec4 aHitData[64]; 
uniform int aHitCnt;

in vec2 vUv;
in vec2 vPos; 
out vec4 FragColor;

float getIntensity() {
    float totalIntensity = 0.0;
    vec2 scaledPos = vec2(vPos.x, vPos.y * aHWRatio);
    
    for (int i = 0; i < 64; i++) {
        if (i >= aHitCnt) break;

        vec2 rawHitPos = aHitData[i].xy;
        float stamp = aHitData[i].z;
        float maxRadius = aHitData[i].w; 
        float elapsed = aCurrTime - stamp;

        float frac = elapsed / aDuration;
        if (frac < 0.0 || frac > 1.0) continue;

        vec2 scaledHit = vec2(rawHitPos.x, rawHitPos.y * aHWRatio);
        
        float dist = distance(scaledPos, scaledHit);

        // Glow
        float glowRadius = maxRadius * (aBaseGlowRadiusFrac + (aMaxGlowRadiusFrac - aBaseGlowRadiusFrac) * frac); 
        float glow = 1.0 - smoothstep(0.0, glowRadius, dist);
        glow *= smoothstep(aGlowTimeMult, 0.0, frac); 

        // Ripple
        float rippleRadius = maxRadius * frac; 
        float rippleWidth = mix(aBaseBandWidth, aMaxBandWidth, frac);
        
        float distToRing = abs(dist - rippleRadius);
        float ripple = 1.0 - smoothstep(0.0, rippleWidth*0.5, distToRing);
        ripple *= 1.0 - smoothstep(0.0, 1.0, frac);

        // Accumulate
        totalIntensity += (glow * (1.0-aRippleWeight) + ripple * aRippleWeight);
    }
    
    return totalIntensity * aIntensity;
}

void main() {
    float maskAlpha = texture(aMaskTex, vUv).a;
    if (maskAlpha < 0.01) discard; 

    vec2 orginalUv = vUv / aTexScale;
    orginalUv.y *= aHWRatio;
    vec4 fx = texture(aFxTex, orginalUv * aFxDensity); 

    float intensity = getIntensity(); 
    
    vec3 finalRGB = pow(aCol.rgb * fx.rgb * intensity, vec3(aGamma));
    float finalAlpha = maskAlpha * clamp(intensity, 0.0, 1.0);
    FragColor = vec4(finalRGB, finalAlpha * aCol.a);
}