#version 330 core

uniform mat4      M, P;

in  vec2 texcoord;
in  vec2 vertex;

out vec2 out_texcoord;

void main(void){
   out_texcoord = texcoord;
   gl_Position  = P*(M*vec4(vertex.x, vertex.y, 0.0, 1.0));
}
