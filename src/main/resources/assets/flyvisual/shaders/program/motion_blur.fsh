#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D PrevSampler;

uniform float Amount;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 c = texture(DiffuseSampler, texCoord);
    vec4 p = texture(PrevSampler, texCoord);
    // p.a = 0 у пустого буфера (после сброса): тогда смешивать не с чем, кадр остаётся как есть
    float w = clamp(Amount, 0.0, 0.98) * p.a;
    fragColor = vec4(mix(c.rgb, p.rgb, w), 1.0);
}
