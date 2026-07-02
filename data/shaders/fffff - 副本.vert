#version 430

layout(location = 0) in vec2 aUv;
layout(location = 1) in vec2 aPos; // -0.5 ~ 0.5

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

uniform vec2 aTexScale;
uniform mat4 aVpMat;
uniform mat4 aModelMat;

out vec2 vUv;
out vec2 vPos;

void main() {
    vUv = aUv * aTexScale;
    vPos = aPos;
    gl_Position = aVpMat * aModelMat * vec4(aPos, 0.0, 1.0);
}