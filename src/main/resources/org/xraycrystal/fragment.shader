#version 330 core

uniform sampler2D tex;

in  vec2 out_texcoord;

out vec4 out_color;

void main(void) {
    const float gamma = 2.2;
    vec4 hdrColor = texture(tex, out_texcoord);

    // Reinhard tone mapping
    vec3 mapped = hdrColor.rgb / (hdrColor.rgb + 1.0);
    // Gamma correction
    mapped = pow(mapped, vec3(1.0 / gamma));

    out_color = vec4(vec3(mapped), hdrColor.a);
}
