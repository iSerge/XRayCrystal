#if defined(cl_khr_fp64)  // Khronos extension available?
#pragma OPENCL EXTENSION cl_khr_fp64 : enable
#define DOUBLE_SUPPORT_AVAILABLE
#elif defined(cl_amd_fp64)  // AMD extension available?
#pragma OPENCL EXTENSION cl_amd_fp64 : enable
#define DOUBLE_SUPPORT_AVAILABLE
#endif

#if defined(DOUBLE_SUPPORT_AVAILABLE)

// double
typedef double real_t;
typedef double2 real2_t;
typedef double3 real3_t;
typedef double4 real4_t;
typedef double8 real8_t;
typedef double16 real16_t;

#define PI 3.14159265358979323846
#define _0 0.0
#define _0_5 0.5
#define _1 1.0
#define _2 2.0
#define _3 3.0
#define _4 4.0

#else

// float
typedef float real_t;
typedef float2 real2_t;
typedef float3 real3_t;
typedef float4 real4_t;
typedef float8 real8_t;
typedef float16 real16_t;

#define PI 3.14159265359f
#define _0 0.0f
#define _0_5 0.5f
#define _1 1.0f
#define _2 2.0f
#define _3 3.0f
#define _4 4.0f

#endif

kernel void prepareLattice(global real4_t* aIn,  global real4_t* aOut, global real_t* matrix, const unsigned int n,
                            const real_t centerX, const real_t centerY, const real_t centerZ)
{
    size_t i = get_global_id(0);

    if(i < n){
        aOut[i].x = aIn[i].x*matrix[0] + aIn[i].y*matrix[1] + aIn[i].z*matrix[2] - centerX;
        aOut[i].y = aIn[i].x*matrix[3] + aIn[i].y*matrix[4] + aIn[i].z*matrix[5] - centerY;
        aOut[i].z = aIn[i].x*matrix[6] + aIn[i].y*matrix[7] + aIn[i].z*matrix[8] - centerZ;
        aOut[i].s3 = aIn[i].s3;
    }
}

kernel void initPhase(global real4_t* atoms,  global real2_t* phase, const unsigned int n, const real_t lambda){
    size_t i = get_global_id(0);

    const real_t k = _2*PI / lambda;

    if(i < n){
        real_t p = k * atoms[i].z;

        phase[i].x = cos(p)*atoms[i].s3;
        phase[i].y = sin(p)*atoms[i].s3;
    }
}

kernel void diffraction(global real4_t* atoms,  global real2_t* psi, write_only image2d_t img, const unsigned int n,
                        const real_t lambda, const real_t R, const real_t L, const int phase)
{
    int2 pos = (int2)(get_global_id(0), get_global_id(1));
    real_t x = (real_t)get_global_id(0);
    real_t y = (real_t)get_global_id(1);

    const real_t width = (real_t)get_global_size(0);
    const real_t height = (real_t)get_global_size(1);

    const real_t k = _2*PI / lambda;

    real2_t I = (real2_t)(_0, _0);

    real2_t u, t;
    real2_t c = (real2_t)(_0, _0);

    real_t Lx = L*(x/width - _0_5);
    real_t Ly = L*(y/height - _0_5);

    for(size_t i = 0; i < n; ++i){
        real3_t atom = atoms[i].xyz;
        real_t r = distance((real3_t)(Lx,Ly,R), atom);
        real_t phase = fmod(k*r, _2*PI);

        real_t co;
        real_t si = sincos(phase, &co);

        real2_t j;
        j.s0 +=  psi[i].x*co - psi[i].y*si;
        j.s1 +=  psi[i].y*co + psi[i].x*si;

        u = j - c;
        t = I + u;
        c = (t - I) - u;
        I = t;
    }
    I /= n;

    real_t v;

    if(phase){
        v = atan(I.y/I.x);
    } else {
        v = I.x*I.x + I.y*I.y;
        //v = 1.0f - clamp(v, _0, _0);
    }

    float4 color;

    if(phase){
        color = (float4)(sin(v), sin(v + _2*PI/_3), sin(v + _4*PI/_3), sqrt(I.x*I.x + I.y*I.y));
        color = color*color;
    } else {
        color = (float4)(v, v, v, 1.0f);
    }

    write_imagef(img, pos, color);
}
