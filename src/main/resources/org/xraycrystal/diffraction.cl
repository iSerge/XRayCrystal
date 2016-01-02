#define  PI 3.14159265359f
#define _2PI 6.28318530718f

kernel void prepareLattice(global float4* aIn,  global float4* aOut, global float* matrix, const unsigned int n,
                            const float dx, const float dy, const float dz)
{
    size_t i = get_global_id(0);

    if(i < n){
        aOut[i].x = aIn[i].x*matrix[0] + aIn[i].y*matrix[1] + aIn[i].z*matrix[2];
        aOut[i].y = aIn[i].x*matrix[3] + aIn[i].y*matrix[4] + aIn[i].z*matrix[5];
        aOut[i].z = aIn[i].x*matrix[6] + aIn[i].y*matrix[7] + aIn[i].z*matrix[8];
        aOut[i].s3 = aIn[i].s3;
    }
}

kernel void initPhase(global float4* atoms,  global float2* phase, const unsigned int n, const float lambda){
    size_t i = get_global_id(0);

    const float k = _2PI / lambda;

    if(i < n){
        float p = k * atoms[i].z;

        phase[i].x = cos(p)*atoms[i].s3;
        phase[i].y = sin(p)*atoms[i].s3;
    }
}

kernel void diffraction(global float4* atoms,  global float2* psi, write_only image2d_t img, const unsigned int n,
                        const float lambda, const float R, const float L, const float amp, const bool phase)
{
    int2 pos = (int2)(get_global_id(0), get_global_id(1));
    float x = (float)get_global_id(0);
    float y = (float)get_global_id(1);

    const float width = (float)get_global_size(0);
    const float height = (float)get_global_size(1);

    const float k = _2PI / lambda;

    float2 I = (float2)(0.0f, 0.0f);

    float Lx = L*(x/width - 0.5f);
    float Ly = L*(y/height - 0.5f);

    for(size_t i = 0; i < n; ++i){
        float3 atom = (float3)(atoms[i].x, atoms[i].y, atoms[i].z);
        float r = distance((float3)(Lx,Ly,R), atom);
        float phase = fmod(k*r, _2PI);

        float c;
        float s = sincos(phase, &c);
        I.s0 +=  psi[i].x*c - psi[i].y*s;
        I.s1 +=  psi[i].y*c + psi[i].x*s;
    }
    I /= n;

    float v;

    if(phase){
        v = atan(I.y/I.x);
    } else {
        v = amp*(I.x*I.x + I.y*I.y);
        v = 1.0f - clamp(v, 0.0f, 1.0f);
    }

    float4 color;

    if(phase){
        color = (float4)(sin(v), sin(v + _2PI/3), sin(v + 4*PI/3), clamp(sqrt(I.x*I.x + I.y*I.y), 0.0f, 1.0f));
        color = color*color;
    } else {
        color = (float4)(v, v, v, 1.0f);
    }

    write_imagef(img, pos, color);
}
