#version 150

uniform sampler2D DiffuseSampler;
uniform vec3 Tint;
uniform float Strength;
uniform float Sat;
uniform float Vignette;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    vec3 rgb = source.rgb;
    float luma = dot(rgb, vec3(0.299, 0.587, 0.114));
    rgb = mix(vec3(luma), rgb, Sat);
    vec3 graded = clamp(rgb * Tint, 0.0, 1.0);
    rgb = mix(rgb, graded, clamp(Strength, 0.0, 1.0));
    vec2 d = texCoord - 0.5;
    float v = smoothstep(0.35, 0.95, length(d) * 1.35);
    rgb *= 1.0 - v * clamp(Vignette, 0.0, 1.0);
    fragColor = vec4(rgb, source.a);
}
