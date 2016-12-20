package org.xraycrystal.controls;

import com.jogamp.opencl.*;
import com.jogamp.opencl.gl.CLGLContext;
import com.jogamp.opencl.gl.CLGLTexture2d;
import com.jogamp.opengl.*;
import org.jmol.adapter.smarter.Atom;
import org.jmol.adapter.smarter.AtomSetCollection;
import org.jmol.api.SymmetryInterface;
import org.xraycrystal.util.GlUtils;
import org.xraycrystal.util.Utils;

import javax.vecmath.Point3f;
import java.nio.*;
import java.util.function.Consumer;

public class DiffractionGLListener implements GLEventListener {
    private int     bufferWidth  = 512; // texture size
    private int     bufferHeight = 512;
    private int     atomGroupGlblSize = bufferWidth;
    private final int atomGroupLocalSize = 512;
    private boolean GL_INTEROP   = true; // switch for CL-GL transfers
    private boolean CL_FP64 = false; // true if openCL supports double precision floats

    private float lambda = 0.5f;
    private static final float R = 5e-8f; // photo film is 5 cm away from crystall
    private static final float L = 12e-8f; // photo film is square with 12 cm side

    private double lambda_d = 0.5;
    private static final double R_d = 5e-8; // photo film is 5 cm away from crystall
    private static final double L_d = 12e-8; // photo film is square with 12 cm side

    private boolean useDouble = false;

    private float exposure = 1.0f;

    private float centerX;
    private float centerY;
    private float centerZ;

    private double centerX_d;
    private double centerY_d;
    private double centerZ_d;

    private float[] atoms = {
            0f, 0f, 0f, 1f,
            3f, 0f, 0f, 1f
    };

    private double[] atoms_d = {
            0.0, 0.0, 0.0, 1.0,
            3.0, 0.0, 0.0, 1.0
    };

    private int atomCount = atoms.length / 4;

    private float[] atomsTransMat = {
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
    };

    private double[] atomsTransMat_d = {
            1.0, 0.0, 0.0,
            0.0, 1.0, 0.0,
            0.0, 0.0, 1.0
    };

    private float[] identity_matrix = new float[]{
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
    };
    private float[] projection_matrix = new float[]{
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
    };
    private float[] quadTexcoords = new float[]{
            0.0f,  1.0f, // note the t-coord in (s,t) is mirrored!
            1.0f,  1.0f,
            1.0f,  0.0f,
            0.0f,  0.0f
    };
    private float[] quadCoords  = new float[]{
            0.0f,        0.0f,
            bufferWidth, 0.0f,
            bufferWidth, bufferHeight,
            0.0f,        bufferHeight
    };

    private int texId;
    private int programId;
    private int vbo;
    private CLDevice device;

    private CLGLContext clContext2; // only used if GL_INTEROP==true
    private CLContext   clContext;  // always used; (clContext2 == clContext) iff GL_INTEROP==true

    private CLKernel atomTransformKernel;
    private CLKernel atomTransformKernel_d;
    private CLKernel initPhaseKernel;
    private CLKernel initPhaseKernel_d;
    private CLKernel diffractionKernel;
    private CLKernel diffractionKernel_d;

    private CLCommandQueue commandQueue;

    // only one is used: texBuffer (iff GL_INTEROP==false), or texBuffer2 (iff GL_INTEROP==true)
    private CLGLTexture2d<?> texBuffer2;
    private CLImage2d<ByteBuffer> texBuffer;

    private CLBuffer<FloatBuffer> transformMatrix;
    private CLBuffer<FloatBuffer> origAtoms;
    private CLBuffer<FloatBuffer> transAtoms;
    private CLBuffer<FloatBuffer> psi;

    private CLBuffer<DoubleBuffer> transformMatrix_d;
    private CLBuffer<DoubleBuffer> origAtoms_d;
    private CLBuffer<DoubleBuffer> transAtoms_d;
    private CLBuffer<DoubleBuffer> psi_d;

    private boolean needCalcDiffraction = true;

    private Consumer<Boolean> canUseDouble;

    public DiffractionGLListener(Consumer<Boolean> canUseDouble){
        this.canUseDouble = canUseDouble;
    }

    public boolean isDoublePrecisionAvailable(){
        return CL_FP64;
    }

    public boolean getUseDouble() {
        return useDouble;
    }

    public void setUseDouble(boolean useDouble){
        this.useDouble = useDouble;
        needCalcDiffraction = true;
    }

    public float getLambda() {
        return lambda;
    }

    public void setLambda(float lambda) {
        this.lambda = lambda;
        this.lambda_d = lambda;

        if(null != initPhaseKernel) {
            initPhaseKernel.setArg(3, lambda);
        }
        if(CL_FP64 && null != initPhaseKernel_d) {
            initPhaseKernel_d.setArg(3, lambda_d);
        }

        if(null != diffractionKernel) {
            diffractionKernel.setArg(4, lambda);
        }
        if(CL_FP64 && null != diffractionKernel_d) {
            diffractionKernel_d.setArg(4, lambda_d);
        }

        needCalcDiffraction = true;
    }

    public float getExposure() {
        return exposure;
    }

    public void setExposure(float exposure) {
        this.exposure = exposure;
    }

    @Override
    public void init (GLAutoDrawable drawable) {

        if (clContext == null) {
            CLPlatform platform = CLPlatform.getDefault();
            device   = platform.getMaxFlopsDevice(CLDevice.Type.GPU);
            // default device used; switched inside the NVidia driver (if you have both)
            if (null == device) {
                throw new RuntimeException("couldn't find any CL device");
            }

            System.out.println(platform.getName() + " platform: " + device.getName());
            if (GL_INTEROP) {
                // create OpenCL context before creating any OpenGL objects
                clContext2 = CLGLContext.create(drawable.getContext(), device);
                clContext  = clContext2;
            } else {
                clContext  = CLContext.create(device);
            }

            CL_FP64 = device.isExtensionAvailable("cl_khr_fp64") || device.isExtensionAvailable("cl_amd_fp64");

            canUseDouble.accept(CL_FP64);

            // checking device capabilities
            System.out.println("Local mem size: " +device.getLocalMemSize());
            System.out.println("Local mem type: " +device.getLocalMemType());
            System.out.println("Max compute units: " +device.getMaxComputeUnits());
            System.out.println("Max work group size: " +device.getMaxWorkGroupSize());

            // enable GL error checking using the composable pipeline
            drawable.setGL(new DebugGL3(drawable.getGL().getGL3()));

            // OpenGL initialization
            GL3 gl = drawable.getGL().getGL3();
            gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            gl.glClearDepth(1.0f);
            gl.glDisable(GL2.GL_DEPTH_TEST);
            gl.glEnable(GL2.GL_BLEND);
            gl.glBlendEquationSeparate(GL2.GL_FUNC_ADD, GL2.GL_FUNC_ADD);
            gl.glBlendFuncSeparate(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA, GL2.GL_ONE, GL2.GL_ZERO);
            gl.glPixelStorei(GL2.GL_UNPACK_ALIGNMENT, 1);

            initShader(drawable);
            gl.glUseProgram(programId);
            System.out.println(String.format("%d/%d/%d", getLocation(gl, "M"), getLocation(gl, "P"), getLocation(gl, "tex")));

            // generate data buffers for texcoords and coords
            int idArray[] = new int[2];
            gl.glGenBuffers(2, IntBuffer.wrap(idArray));

            // Create the vertex array object
            int vboArray[] = new int[1];
            gl.glGenVertexArrays(1, IntBuffer.wrap(vboArray));
            vbo = vboArray[0];
            gl.glBindVertexArray(vbo);

            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, idArray[0]);
            ByteBuffer texData = GlUtils.clone(quadTexcoords);
            gl.glBufferData(GL2.GL_ARRAY_BUFFER, quadTexcoords.length*4, texData, GL2.GL_STATIC_DRAW);
            gl.glVertexAttribPointer(getAttribLocation(gl, "texcoord"), 2, GL2.GL_FLOAT, false, 0, 0);
            gl.glEnableVertexAttribArray(getAttribLocation(gl, "texcoord"));

            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, idArray[1]);
            ByteBuffer coordData = GlUtils.clone(quadCoords);
            gl.glBufferData(GL2.GL_ARRAY_BUFFER, quadCoords.length*4, coordData, GL2.GL_STATIC_DRAW);
            gl.glVertexAttribPointer    (getAttribLocation(gl, "vertex"),   2, GL2.GL_FLOAT, false, 0, 0);
            gl.glEnableVertexAttribArray(getAttribLocation(gl, "vertex"));
            gl.glBindVertexArray(0);

            int[] texArray = new int[1];
            // Create the texture object
            gl.glGenTextures(1, texArray, 0);
            texId    = texArray[0];
            gl.glActiveTexture(GL2.GL_TEXTURE0);
            gl.glBindTexture  (GL2.GL_TEXTURE_2D, texId);
            gl.glUniform1i    (getLocation(gl, "tex"), 0);
            // texture filter
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
            // texture wrap mode
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S,
                    GL2.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T,
                    GL2.GL_CLAMP_TO_EDGE);
            // set image size only
            gl.glTexImage2D   (GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA32F, bufferWidth, bufferHeight, 0,
                    GL2.GL_RGBA, GL2.GL_FLOAT, null);
            gl.glBindTexture  (GL2.GL_TEXTURE_2D, 0);
            // initialize OpenCL, creating a context for the given GL object
            initCL(gl);
        }
    }

    private void initShader (GLAutoDrawable d)
    {
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 3 graphics context

        int vertID = gl.glCreateShader(GL2.GL_VERTEX_SHADER);
        int fragID = gl.glCreateShader(GL2.GL_FRAGMENT_SHADER);

        String sourceVS = Utils.readResource("/org/xraycrystal/vertex.shader");
        String sourceFS = Utils.readResource("/org/xraycrystal/fragment.shader");

        String[] vs = { sourceVS };
        String[] fs = { sourceFS };

        gl.glShaderSource(vertID, 1, vs, null, 0);
        gl.glShaderSource(fragID, 1, fs, null, 0);

        // compile the shader
        gl.glCompileShader(vertID);
        gl.glCompileShader(fragID);

        // check for errors
        GlUtils.printShaderInfoLog(d, vertID);
        GlUtils.printShaderInfoLog(d, fragID);

        // create program and attach shaders
        programId = gl.glCreateProgram();
        gl.glAttachShader(programId, vertID);
        gl.glAttachShader(programId, fragID);

        // "out_color" is a user-provided OUT variable of the fragment shader.
        // Its output is bound to the first color buffer in the framebuffer
        gl.glBindFragDataLocation(programId, 0, "out_color");

        // link the program
        gl.glLinkProgram(programId);
        // output error messages
        GlUtils.printProgramInfoLog(d, programId);
    }

    private void initCL (GL3 gl)
    {
        // ensure pipeline is clean before doing cl work
        gl.glFinish();

        String diffractionProgram = Utils.readResource("/org/xraycrystal/diffraction.cl");
        CLProgram diffractionProg = clContext.createProgram(diffractionProgram);
        diffractionProg.build();
        System.out.println(diffractionProg.getBuildStatus());
        System.out.println(diffractionProg.isExecutable());
        System.out.println(diffractionProg.getBuildLog());

        CLProgram diffractionProg_d = null;
        if(CL_FP64) {
            String diffractionProgram_d = Utils.readResource("/org/xraycrystal/diffraction_d.cl");
            diffractionProg_d = clContext.createProgram(diffractionProgram_d);
            diffractionProg_d.build();
            System.out.println(diffractionProg_d.getBuildStatus());
            System.out.println(diffractionProg_d.isExecutable());
            System.out.println(diffractionProg_d.getBuildLog());
        }

        commandQueue = device.createCommandQueue();

        if(CL_FP64){
            setAtoms(atoms_d);

            transformMatrix_d = clContext.createDoubleBuffer(9, CLMemory.Mem.READ_ONLY);
            transformMatrix_d.getBuffer().put(atomsTransMat_d).rewind();
            commandQueue.putWriteBuffer(transformMatrix_d, false);
        }

        setAtoms(atoms);

        transformMatrix = clContext.createFloatBuffer(9, CLMemory.Mem.READ_ONLY);
        transformMatrix.getBuffer().put(atomsTransMat).rewind();
        commandQueue.putWriteBuffer(transformMatrix, false);

        commandQueue.finish();

        if(CL_FP64){
            atomTransformKernel_d = diffractionProg_d.createCLKernel("prepareLattice_d")
                    .putArg(origAtoms_d)
                    .putArg(transAtoms_d)
                    .putArg(transformMatrix_d)
                    .putArg(atomCount)
                    .putArg(centerX_d)
                    .putArg(centerY_d)
                    .putArg(centerZ_d)
                    .rewind();

            initPhaseKernel_d = diffractionProg_d.createCLKernel("initPhase_d")
                    .putArg(transAtoms_d)
                    .putArg(psi_d)
                    .putArg(atomCount)
                    .putArg(lambda_d * 1e-10)
                    .rewind();
        }

        atomTransformKernel = diffractionProg.createCLKernel("prepareLattice")
                .putArg(origAtoms)
                .putArg(transAtoms)
                .putArg(transformMatrix)
                .putArg(atomCount)
                .putArg(centerX)
                .putArg(centerY)
                .putArg(centerZ)
                .rewind();

        initPhaseKernel = diffractionProg.createCLKernel("initPhase")
                .putArg(transAtoms)
                .putArg(psi)
                .putArg(atomCount)
                .putArg(lambda * 1e-10f)
                .rewind();


        if (GL_INTEROP) {
            texBuffer2 = clContext2.createFromGLTexture2d(GL2.GL_TEXTURE_2D,
                    texId, 0, CLBuffer.Mem.WRITE_ONLY);
            System.out.println("cl buffer type:        " + texBuffer2.getGLObjectType());
            System.out.println("shared with gl buffer: " + texBuffer2.getGLObjectID());

            if(CL_FP64){
                diffractionKernel_d = diffractionProg_d.createCLKernel("diffraction_d")
                        .putArg(transAtoms_d)
                        .putArg(psi_d)
                        .putArg(texBuffer2)
                        .putArg(atomCount)
                        .putArg(lambda_d * 1e-10)
                        .putArg(R_d)
                        .putArg(L_d)
                        .rewind();
            }

            diffractionKernel = diffractionProg.createCLKernel("diffraction")
                    .putArg(transAtoms)
                    .putArg(psi)
                    .putArg(texBuffer2)
                    .putArg(atomCount)
                    .putArg(lambda * 1e-10f)
                    .putArg(R)
                    .putArg(L)
                    .rewind();

        } else {
            // Create an empty OpenCL buffer
            ByteBuffer buffer = ByteBuffer.allocateDirect(bufferWidth*bufferHeight*4); buffer.order(ByteOrder.nativeOrder());
            texBuffer = clContext.createImage2d(buffer, bufferWidth, bufferHeight,
                    new CLImageFormat(CLImageFormat.ChannelOrder.RGBA, CLImageFormat.ChannelType.UNORM_INT8),
                    CLBuffer.Mem.WRITE_ONLY);

            System.out.println(String.format("texture-size=%d", texBuffer.getBuffer().capacity()));

            if(CL_FP64){
                diffractionKernel_d = diffractionProg_d.createCLKernel("diffraction_d")
                        .putArg(transAtoms_d)
                        .putArg(psi_d)
                        .putArg(texBuffer)
                        .putArg(atomCount)
                        .putArg(lambda_d * 1e-10)
                        .putArg(R_d)
                        .putArg(L_d)
                        .rewind();
            }

            diffractionKernel = diffractionProg.createCLKernel("diffraction")
                    .putArg(transAtoms)
                    .putArg(psi)
                    .putArg(texBuffer)
                    .putArg(atomCount)
                    .putArg(lambda * 1e-10f)
                    .putArg(R)
                    .putArg(L)
                    .rewind();
        }

        System.out.println("cl initialised");
    }

    @Override
    public void display (GLAutoDrawable drawable)
    {
        GL3 gl = drawable.getGL().getGL3();

        if(needCalcDiffraction) {
            computeCL(gl);
        }

        gl.glClear (GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        float tx = (bufferWidth+0.0f) /(bufferWidth-0.0f);
        float ty = (0.0f+bufferHeight)/(0.0f-bufferHeight);
        float tz = (0.0f+1.0f)  /(1.0f-0.0f);
        projection_matrix[ 0] =  2.0f/(bufferWidth-0.0f);
        projection_matrix[ 5] =  2.0f/(0.0f-bufferHeight);
        projection_matrix[10] = -2.0f/(1.0f-0.0f);
        projection_matrix[12] = -tx;
        projection_matrix[13] = -ty;
        projection_matrix[14] = -tz;

        gl.glUseProgram      (programId);
        gl.glUniformMatrix4fv(getLocation(gl, "M"),  1, false, FloatBuffer.wrap(identity_matrix));
        gl.glUniformMatrix4fv(getLocation(gl, "P"),  1, false, FloatBuffer.wrap(projection_matrix));
        gl.glUniform1f(getLocation(gl,"exposure"), exposure);
        gl.glActiveTexture   (GL2.GL_TEXTURE0);
        gl.glBindTexture     (GL2.GL_TEXTURE_2D, texId);
        // draw quad
        gl.glBindVertexArray (vbo);
        gl.glDrawArrays      (GL2.GL_TRIANGLE_FAN, 0, 4);
    }

    private void computeCL (GL3 gl)
    {
        // ensure pipeline is clean before doing cl work
        gl.glFinish();

        if(useDouble){
            transformMatrix_d.getBuffer().put(atomsTransMat_d).rewind();
            commandQueue.putWriteBuffer(transformMatrix_d, false);

            initPhaseKernel_d.setArg(3, lambda_d * 1e-10);
            diffractionKernel_d.setArg(4, lambda_d * 1e-10);
        } else {
            transformMatrix.getBuffer().put(atomsTransMat).rewind();
            commandQueue.putWriteBuffer(transformMatrix, false);

            initPhaseKernel.setArg(3, lambda * 1e-10f);
            diffractionKernel.setArg(4, lambda * 1e-10f);
        }

        if (GL_INTEROP) {
            if(useDouble){
                commandQueue.putAcquireGLObject(texBuffer2)
                        .put1DRangeKernel(atomTransformKernel_d, 0, atomGroupGlblSize, atomGroupLocalSize)
                        .put1DRangeKernel(initPhaseKernel_d, 0, atomGroupGlblSize, atomGroupLocalSize)
                        .put2DRangeKernel(diffractionKernel_d, 0, 0, bufferWidth, bufferHeight, 16, 16)
                        .putReleaseGLObject(texBuffer2)
                        .finish();
            } else {
                commandQueue.putAcquireGLObject(texBuffer2)
                        .put1DRangeKernel(atomTransformKernel, 0, atomGroupGlblSize, atomGroupLocalSize)
                        .put1DRangeKernel(initPhaseKernel, 0, atomGroupGlblSize, atomGroupLocalSize)
                        .put2DRangeKernel(diffractionKernel, 0, 0, bufferWidth, bufferHeight, 16, 16)
                        .putReleaseGLObject(texBuffer2)
                        .finish();
            }

        } else {
            if(useDouble){
                commandQueue.put1DRangeKernel(atomTransformKernel_d, 0, atomGroupGlblSize, atomGroupLocalSize)
                        .put1DRangeKernel(initPhaseKernel_d, 0, atomGroupGlblSize, atomGroupLocalSize)
                        .put2DRangeKernel(diffractionKernel_d, 0, 0, bufferWidth, bufferHeight, 16, 16)
                        .putReadImage(texBuffer, true)
                        .finish();
            } else {
                commandQueue.put1DRangeKernel(atomTransformKernel, 0, atomGroupGlblSize, atomGroupLocalSize)
                        .put1DRangeKernel(initPhaseKernel, 0, atomGroupGlblSize, atomGroupLocalSize)
                        .put2DRangeKernel(diffractionKernel, 0, 0, bufferWidth, bufferHeight, 16, 16)
                        .putReadImage(texBuffer, true)
                        .finish();
            }

            // Copy data from the CL image buffer to the GL texture
            gl.glActiveTexture(GL2.GL_TEXTURE0);
            gl.glBindTexture  (GL2.GL_TEXTURE_2D, texId);
            gl.glTexImage2D   (GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA32F, bufferWidth, bufferHeight, 0,
                    GL2.GL_RGBA, GL2.GL_FLOAT, texBuffer.getBuffer().rewind());
        }

        needCalcDiffraction = false;
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int arg1, int arg2, int width, int height)
    {
        GL3 gl = drawable.getGL().getGL3();
        gl.glViewport(0, 0, width, height);
    }

    @Override
    public void dispose(GLAutoDrawable drawable)
    {
        clContext.release();
    }

    private int getLocation (GL3 gl, String name)
    {
        return gl.glGetUniformLocation(programId, name);
    }

    private int getAttribLocation (GL3 gl, String name)
    {
        return gl.glGetAttribLocation(programId, name);
    }

    private void setAtoms(float[] atoms) {
        atomCount = atoms.length / 4;
        System.out.println("Number of atoms: " + atomCount);

        atomGroupGlblSize = atomGroupLocalSize * ((atomCount - 1) / atomGroupLocalSize + 1);

        centerX = 0.0f;
        centerY = 0.0f;
        centerZ = 0.0f;

        for(int i = 0; i < atomCount; ++i){
            atoms[i*4] *= 1e-10;
            atoms[i*4+1] *= 1e-10;
            atoms[i*4+2] *= 1e-10;

            centerX += atoms[i*4];
            centerY += atoms[i*4+1];
            centerZ += atoms[i*4+2];
        }

        centerX /= atomCount;
        centerY /= atomCount;
        centerZ /= atomCount;

        commandQueue.finish();

        if(null != origAtoms){
            origAtoms.release();
        }

        origAtoms = clContext.createFloatBuffer(atomCount*4, CLMemory.Mem.READ_ONLY);
        origAtoms.getBuffer().put(atoms).rewind();
        commandQueue.putWriteBuffer(origAtoms, false);

        if(null != transAtoms){
            transAtoms.release();
        }

        transAtoms = clContext.createFloatBuffer(atomCount*4, CLMemory.Mem.READ_WRITE);

        if(null != psi){
            psi.release();
        }
        psi = clContext.createFloatBuffer(atomCount * 2, CLMemory.Mem.READ_WRITE);


        if(null != atomTransformKernel) {
            atomTransformKernel.setArg(0, origAtoms);
            atomTransformKernel.setArg(1, transAtoms);
            atomTransformKernel.setArg(3, atomCount);
            atomTransformKernel.setArg(4, centerX)
                    .setArg(5, centerY)
                    .setArg(6, centerZ);

        }

        if(null != initPhaseKernel) {
            initPhaseKernel.setArg(0, transAtoms);
            initPhaseKernel.setArg(1, psi);
            initPhaseKernel.setArg(2, atomCount);
        }

        if(null != diffractionKernel) {
            diffractionKernel.setArg(0, transAtoms);
            diffractionKernel.setArg(1, psi);
            diffractionKernel.setArg(3, atomCount);
        }

        needCalcDiffraction = true;
    }

    private void setAtoms(double[] atoms) {
        atomCount = atoms.length / 4;
        System.out.println("Number of atoms: " + atomCount);

        atomGroupGlblSize = atomGroupLocalSize * ((atomCount - 1) / atomGroupLocalSize + 1);

        centerX_d = 0.0f;
        centerY_d = 0.0f;
        centerZ_d = 0.0f;

        for(int i = 0; i < atomCount; ++i){
            atoms[i*4] *= 1e-10;
            atoms[i*4+1] *= 1e-10;
            atoms[i*4+2] *= 1e-10;

            centerX_d += atoms[i*4];
            centerY_d += atoms[i*4+1];
            centerZ_d += atoms[i*4+2];
        }

        centerX_d /= atomCount;
        centerY_d /= atomCount;
        centerZ_d /= atomCount;

        commandQueue.finish();

        if(null != origAtoms_d){
            origAtoms_d.release();
        }

        origAtoms_d = clContext.createDoubleBuffer(atomCount*4, CLMemory.Mem.READ_ONLY);
        origAtoms_d.getBuffer().put(atoms).rewind();
        commandQueue.putWriteBuffer(origAtoms_d, false);

        if(null != transAtoms_d){
            transAtoms_d.release();
        }

        transAtoms_d = clContext.createDoubleBuffer(atomCount*4, CLMemory.Mem.READ_WRITE);

        if(null != psi_d){
            psi_d.release();
        }
        psi_d = clContext.createDoubleBuffer(atomCount * 2, CLMemory.Mem.READ_WRITE);


        if(null != atomTransformKernel_d) {
            atomTransformKernel_d.setArg(0, origAtoms_d);
            atomTransformKernel_d.setArg(1, transAtoms_d);
            atomTransformKernel_d.setArg(3, atomCount);
            atomTransformKernel_d.setArg(4, centerX_d)
                    .setArg(5, centerY_d)
                    .setArg(6, centerZ_d);

        }

        if(null != initPhaseKernel_d) {
            initPhaseKernel_d.setArg(0, transAtoms_d);
            initPhaseKernel_d.setArg(1, psi_d);
            initPhaseKernel_d.setArg(2, atomCount);
        }

        if(null != diffractionKernel_d) {
            diffractionKernel_d.setArg(0, transAtoms_d);
            diffractionKernel_d.setArg(1, psi_d);
            diffractionKernel_d.setArg(3, atomCount);
        }

        needCalcDiffraction = true;
    }

    public void setTransformMatrix(float[] atomsTransMat) {
        this.atomsTransMat = atomsTransMat;
        for(int i = 0; i< atomsTransMat.length; ++i){
            atomsTransMat_d[i] = atomsTransMat[i];
        }
        needCalcDiffraction = true;
    }

    public void setAtoms(AtomSetCollection atomsCollection) {
        int atomCount = atomsCollection.getAtomCount();

        if(CL_FP64){
            atoms_d = new double[atomCount * 4];
        }
        atoms = new float[atomCount * 4];

        SymmetryInterface symmetry = atomsCollection.getSymmetry();

        for(int i = 0; i < atomCount; ++i){
            if(null == symmetry || !symmetry.haveUnitCell()){
                Atom atom = atomsCollection.getAtom(i);
                if(CL_FP64){
                    atoms_d[i * 4] = atom.x;
                    atoms_d[i * 4 + 1] = atom.y;
                    atoms_d[i * 4 + 2] = atom.z;
                }
                atoms[i * 4] = atom.x;
                atoms[i * 4 + 1] = atom.y;
                atoms[i * 4 + 2] = atom.z;
            } else {
                Point3f atom = new Point3f(atomsCollection.getAtom(i));
                symmetry.toCartesian(atom, true);

                if(CL_FP64){
                    atoms_d[i * 4] = atom.x;
                    atoms_d[i * 4 + 1] = atom.y;
                    atoms_d[i * 4 + 2] = atom.z;
                }
                atoms[i * 4] = atom.x;
                atoms[i * 4 + 1] = atom.y;
                atoms[i * 4 + 2] = atom.z;
            }

            if(CL_FP64){
                atoms_d[i * 4 + 3] = 1.0;
            }
            atoms[i * 4 + 3] = 1.0f;
        }

            if(CL_FP64){
                setAtoms(atoms_d);
            }
            setAtoms(atoms);
    }
}
