#version 150

uniform sampler2D DiffuseSampler;
uniform vec3 Tint;
uniform float Strength;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    vec3 graded = source.rgb * Tint;
    // A restrained color grade keeps block and GUI colors recognizable.
    fragColor = vec4(mix(source.rgb, clamp(graded, 0.0, 1.0), clamp(Strength, 0.0, 1.0)), source.a);
}
