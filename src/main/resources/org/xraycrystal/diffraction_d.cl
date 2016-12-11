#define PI 3.14159265358979323846
#define _2PI 6.28318530717958647692

kernel void prepareLattice_d(global double4* aIn,  global double4* aOut, global double* matrix, const unsigned int n,
                            const double centerX, const double centerY, const double centerZ)
{
    size_t i = get_global_id(0);

    if(i < n){
        aOut[i].x = aIn[i].x*matrix[0] + aIn[i].y*matrix[1] + aIn[i].z*matrix[2] - centerX;
        aOut[i].y = aIn[i].x*matrix[3] + aIn[i].y*matrix[4] + aIn[i].z*matrix[5] - centerY;
        aOut[i].z = aIn[i].x*matrix[6] + aIn[i].y*matrix[7] + aIn[i].z*matrix[8] - centerZ;
        aOut[i].s3 = aIn[i].s3;
    }
}

kernel void initPhase_d(global double4* atoms,  global double2* phase, const unsigned int n, const double lambda){
    size_t i = get_global_id(0);

    const double k = _2PI / lambda;

    if(i < n){
        double p = k * atoms[i].z;

        double co;
        double si = sincos(p, &co);

        phase[i].x = co;
        phase[i].y = si;
    }
}

kernel void diffraction_d(global double4* atoms,  global double2* psi, write_only image2d_t img, const unsigned int n,
                        const double lambda, const double R, const double L)
{
    int2 pos = (int2)(get_global_id(0), get_global_id(1));
    double x = (double)get_global_id(0);
    double y = (double)get_global_id(1);

    const double width = (double)get_global_size(0);
    const double height = (double)get_global_size(1);

    const double k = _2PI / lambda;

    double2 I = (double2)(0.0, 0.0);

    double2 u, t;
    double2 c = (double2)(0.0, 0.0);

    double Lx = L*(x/width - 0.5);
    double Ly = L*(y/height - 0.5);

    for(size_t i = 0; i < n; ++i){
        double3 atom = atoms[i].xyz;
        double r = distance((double3)(Lx,Ly,R), atom);
        double phase = fmod(k*r, _2PI);

        double co;
        double si = sincos(phase, &co);

        double2 j;
        j.x +=  psi[i].x*co - psi[i].y*si;
        j.y +=  psi[i].y*co + psi[i].x*si;

        u = j - c;
        t = I + u;
        c = (t - I) - u;
        I = t;
    }
    I /= n;

    double v;

    v = I.x*I.x + I.y*I.y;

    float4 color;

    color = (float4)(v, v, v, 1.0f);

    write_imagef(img, pos, color);
}
