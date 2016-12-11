#define PI   3.14159265359f
#define _2PI 6.28318530718f

kernel void prepareLattice(global float4* aIn,  global float4* aOut, global float* matrix, const unsigned int n,
                            const float centerX, const float centerY, const float centerZ)
{
    size_t i = get_global_id(0);

    if(i < n){
        aOut[i].x = aIn[i].x*matrix[0] + aIn[i].y*matrix[1] + aIn[i].z*matrix[2] - centerX;
        aOut[i].y = aIn[i].x*matrix[3] + aIn[i].y*matrix[4] + aIn[i].z*matrix[5] - centerY;
        aOut[i].z = aIn[i].x*matrix[6] + aIn[i].y*matrix[7] + aIn[i].z*matrix[8] - centerZ;
        aOut[i].s3 = aIn[i].s3;
    }
}

kernel void initPhase(global float4* atoms,  global float2* phase, const unsigned int n, const float lambda){
    size_t i = get_global_id(0);

    const float k = _2PI / lambda;

    if(i < n){
        float p = k * atoms[i].z;

        float co;
        float si = sincos(p, &co);

        phase[i].x = co;
        phase[i].y = si;
    }
}

kernel void diffraction(global float4* atoms,  global float2* psi, write_only image2d_t img, const unsigned int n,
                        const float lambda, const float R, const float L)
{
    int2 pos = (int2)(get_global_id(0), get_global_id(1));
    float x = (float)get_global_id(0);
    float y = (float)get_global_id(1);

    const float width = (float)get_global_size(0);
    const float height = (float)get_global_size(1);

    const float k = _2PI / lambda;

    float2 I = (float2)(0.0f, 0.0f);

    float2 u, t;
    float2 c = (float2)(0.0f, 0.0f);

    float Lx = L*(x/width - 0.5f);
    float Ly = L*(y/height - 0.5f);

    for(size_t i = 0; i < n; ++i){
        float3 atom = atoms[i].xyz;
        float r = distance((float3)(Lx,Ly,R), atom);
        float phase = fmod(k*r, _2PI);

        float co;
        float si = sincos(phase, &co);

        float2 j;
        j.x +=  psi[i].x*co - psi[i].y*si;
        j.y +=  psi[i].y*co + psi[i].x*si;

        u = j - c;
        t = I + u;
        c = (t - I) - u;
        I = t;
    }
    I /= n;

    float v;

    v = I.x*I.x + I.y*I.y;

    float4 color;

    color = (float4)(v, v, v, 1.0f);

    write_imagef(img, pos, color);
}
