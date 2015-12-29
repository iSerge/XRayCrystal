kernel void prepareLattice(global float3* aIn,  global float3* aOut, global float* matrix, const unsigned int n){
    size_t i = get_global_id(0);

    if(i < n){
        aOut[i].x = aIn[i].x*matrix[0] + aIn[i].y*matrix[1] + aIn[i].z*matrix[2];
        aOut[i].y = aIn[i].x*matrix[3] + aIn[i].y*matrix[4] + aIn[i].z*matrix[5];
        aOut[i].z = aIn[i].x*matrix[6] + aIn[i].y*matrix[7] + aIn[i].z*matrix[8];
    }
}
