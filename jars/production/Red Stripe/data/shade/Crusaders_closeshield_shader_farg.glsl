#version 430 core

uniform sampler2D crusaders_texture1;
uniform sampler2D crusaders_textureship;
uniform vec4 crusaders_texturecolor;
uniform vec2 crusaders_scale;


uniform float crusaders_commenmaxalphamult;
uniform float crusaders_hittedmaxalphamult;

uniform float crusaders_maxrange;
uniform float crusaders_innerrange;

uniform float crusaders_points_maxtime;

uniform float crusaders_scaneffectbool;
uniform float crusaders_scaneffectahphamult;
uniform float crusaders_scaneffectmaxrange;
uniform float crusaders_scaneffectminrange;

in vec2 crusaders_fargUV;
out vec4 FragColor;

uniform float crusaders_baseIntensity;

layout(std430, binding = 1) buffer SSBO_data {float floatList[];};
uniform float crusaders_points_count;
void main() {
    vec2 scaledTexCoord = crusaders_fargUV * crusaders_scale * 2.0;
    vec4 v1 = texture(crusaders_texture1, scaledTexCoord);
    vec4 v2 = texture(crusaders_textureship, crusaders_fargUV);
    vec4 result = vec4(v1.xyx, v1.w  * v2.w);

    // 混合基础强度与受击强度
    float totalIntensity = crusaders_timelevel + crusaders_baseIntensity;

    // 颜色叠加逻辑
    vec3 finalColor = mix(result.xyz, crusaders_texturecolor.xyz, totalIntensity);
    result = vec4(finalColor, result.w);

    if(result.w != 0.0) {
        result = mix(vec4(result.xyz, result.w), vec4(crusaders_texturecolor.xyz, result.w), 0.6);
        result.w = result.w * crusaders_commenmaxalphamult;
        //result.xyz = vec3((result.xyz - 0.5) * 1.3 + 0.5);
        //if(result.w > crusaders_maxalpha) {result.w = result.w * 0.3;}
        if(crusaders_scaneffectbool > 0.0 && crusaders_scaneffectbool < 1.0 + crusaders_scaneffectmaxrange){// && crusaders_fargUV.y < crusaders_scaneffectbool + 0.1
            float dis = abs(crusaders_fargUV.y - crusaders_scaneffectbool);
            if(dis < crusaders_scaneffectmaxrange){
                float alpha = smoothstep(crusaders_scaneffectminrange, crusaders_scaneffectmaxrange, dis);
                if(result.w > crusaders_commenmaxalphamult * 0.35) {result.w = result.w + (crusaders_scaneffectahphamult * (1.0 - alpha));}
            }
        }

        if(crusaders_points_count > 0.0) {
            for(int i = 0; i < crusaders_points_count; i+=4) {
                vec2 point = vec2(floatList[i],floatList[i + 1]);
                float time = floatList[i + 2];
                float power = floatList[i + 3];

                float dis = distance(crusaders_fargUV,point);
                if(dis < crusaders_maxrange * power){
                    float alpha = smoothstep(crusaders_innerrange * power, crusaders_maxrange * power, dis);
                    if(result.w > crusaders_commenmaxalphamult * 0.35){result.w = result.w  + (crusaders_hittedmaxalphamult * (1.0 - alpha) * (time / crusaders_points_maxtime));}
                }
            }
        }
    }
    FragColor = result;
}



