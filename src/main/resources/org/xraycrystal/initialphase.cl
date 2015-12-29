typedef float2 complex;
#define PI 3.14159265359f

kernel void prepareLattice(global float3* atoms,  global complex* phase, const unsigned int n, const float lambda){
    size_t i = get_global_id(0);

    const float k = 2.0f * PI / lambda;

    if(i < n){
        float p = k * atoms.z;

        phase[i].x = cos(p);
        phase[i].y = sin(p);
    }
}
