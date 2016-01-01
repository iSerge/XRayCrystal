#define PI 3.14159265359f

kernel void prepareLattice(global float4* aIn,  global float4* aOut, global float* matrix, const unsigned int n){
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

    const float k = 2.0f * PI / lambda;

    if(i < n){
        float p = k * atoms[i].x;

        phase[i].x = cos(p);
        phase[i].y = sin(p);
    }
}

kernel void diffraction(global float4* atoms,  global float2* psi, write_only image2d_t img, const unsigned int n,
                        const float lambda, const float R, const float L, const float amp, const bool phase)
{
    int2 pos = (int2)(get_global_id(0), get_global_id(0));
//    size_t x = get_global_id(0);
//    size_t y = get_global_id(1);

    const float width = (float)get_global_size(0);
    const float height = (float)get_global_size(1);

    const float k = 2.0f * PI / lambda;

    float2 I = (float2)(0.0f, 0.0f);

    for(size_t i = 0; i < n; ++i){
        float3 atom = (atoms[i].x, atoms[i].y, atoms[i].z);
        float3 r = fma((float3)(L,L,R),
                       (float3)((float)pos.x/width - 0.5f, (float)pos.y/height - 0.5f, 1.0f),
                       -atom);
//        float3 r = (fma(L, ((float)x/width - 0.5f), -atoms[i].x), fma(L, ((float)y/height - 0.5f), -atoms[i].y, R - atoms[i].z);
        float phase = k*length(r);
        float cos = cos(phase);
        float sin = sin(phase);
        I.x +=  psi[i].x*cos - psi[i].y*sin;
        I.y +=  psi[i].y*cos + psi[i].x*sin;
    }

    float v;

    if(phase){
        v = atan(I.y/I.x);
    } else {
        float2 A = I/n;
        v = dot(A,A);
    }

    float4 color;

    if(phase){
        color = (float4)(sin(v), sin(v + 2*PI/3), sin(v + 4*PI/3), 1.0f);
            color = color*color;
    } else {
        color = (float4)(amp*v, amp*v, amp*v, 1.0f);
    }

    write_imagef(img, pos, color);
}
