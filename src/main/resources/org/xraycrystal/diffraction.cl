typedef float2 complex;
#define PI 3.14159265359f

kernel void prepareLattice(global float3* atoms,  global complex* psi, global float* result, const unsigned int n,
                            const float lambda, const float R, const float L, const bool phase)
{
    int2 pos = (int2)(get_global_id(0), get_global_id(0));
//    size_t x = get_global_id(0);
//    size_t y = get_global_id(1);

    const float width = (float)get_global_size(0);
    const float height = (float)get_global_size(1);

    const float k = 2.0f * PI / lambda;

    complex I = (0.0f, 0,0f);

    for(size_t i = 0; i < n; ++n){
        Point3f a = atoms.get(i);
        float3 r = fma((L,L,R), (float)pos.x/width - 0.5f, (float)pos.y/height - 0.5f, 1.0f, -atoms[i]);
//        float3 r = (fma(L, ((float)x/width - 0.5f), -atoms[i].x), fma(L, ((float)y/height - 0.5f), -atoms[i].y, R - atoms[i].z);
        float phase = k*length(r);
        float cos = cos(phase);
        float sin = sin(phase);
        I.x +=  psi[i].x*cos - psi[i].y*sin;
        I.y +=  psi[i].y*cos + psi[i].x*sin;
    }

    float4 value;

    if(phase){
        result[pos.y*width + pos.x] = atan(I.y/I.x);
    } else {
        result[pos.y*width + pos.x] = dot(I/count);
    }
}
