#version 330 core

uniform sampler2D tex;

in  vec2 out_texcoord;

out vec4 out_color;

void main(void) {
    out_color = texture(tex, out_texcoord);
}
