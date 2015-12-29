#define PI 3.14159265359f

kernel void makeImage(global float* data, write_only image2d_t img, const float amp, const bool phase){
    int2 pos = (int2)(get_global_id(0), get_global_id(0));
    float v = data(pos.y*get_global_size(0) + pos.x);

    float4 color;

    if(phase){
        color = (float4)(sin(v), sin(v + 2*PI/3), sin(v + 4*PI/3), 1.0f);
        color = color*color;
    } else {
        color = (float4)(amp*v, amp*v, amp*v, 1.0f);
    }

    drite_imagef(img, pos, color);
}