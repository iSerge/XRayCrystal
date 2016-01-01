#version 330 core

uniform sampler2D texture_diffuse;
uniform mat4 P;

in vec4 in_Position;
//in vec4 in_Color;
in vec2 in_TextureCoord;

//out vec4 pass_Color;
out vec2 pass_TextureCoord;

void main(void) {
    gl_Position = P*in_Position;
//    gl_Position = in_Position;

//    pass_Color = in_Color;
    pass_TextureCoord = in_TextureCoord;
}
