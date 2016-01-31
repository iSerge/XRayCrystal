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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class DiffractionGLListener implements GLEventListener {
    private int     bufferWidth  = 512; // texture size
    private int     bufferHeight = 512;
    private boolean GL_INTEROP   = true; // switch for CL-GL transfers

    private float lambda = 0.5f;
    private static final float R = 5e-8f; // photo film is 5 cm away from crystall
    private static final float L = 12e-8f; // photo film is square with 12 cm side

    private float amp = 1f;

    private int phase = 0;

    private float centerX;
    private float centerY;
    private float centerZ;

    private float[] atoms = {
            0f, 0f, 0f, 1f,
            3f, 0f, 0f, 1f
    };
    private int atomCount = atoms.length / 4;

    private float[] atomsTransMat = {
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
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
    private CLKernel initPhaseKernel;
    private CLKernel diffractionKernel;

    private CLCommandQueue commandQueue;

    // only one is used: texBuffer (iff GL_INTEROP==false), or texBuffer2 (iff GL_INTEROP==true)
    private CLGLTexture2d<?> texBuffer2;
    private CLImage2d<ByteBuffer> texBuffer;

    private CLBuffer<FloatBuffer> transformMatrix;
    private CLBuffer<FloatBuffer> origAtoms;
    private CLBuffer<FloatBuffer> transAtoms;
    private CLBuffer<FloatBuffer> psi;


    public boolean getPhaseShadind() {
        return 0 != phase;
    }

    public void setPhaseShadind(boolean shadePhase){
        phase = shadePhase ? 1 : 0;
        if(null != diffractionKernel){
            diffractionKernel.setArg(8, phase);
        }
    }

    public float getLambda() {
        return lambda;
    }

    public void setLambda(float lambda) {
        this.lambda = lambda;
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

    public void initShader (GLAutoDrawable d)
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

        commandQueue = device.createCommandQueue();

        setAtoms(atoms);

        transformMatrix = clContext.createFloatBuffer(9, CLMemory.Mem.READ_ONLY);
        transformMatrix.getBuffer().put(atomsTransMat).rewind();
        commandQueue.putWriteBuffer(transformMatrix, false);

//        transAtoms = clContext.createFloatBuffer(atomCount * 4, CLMemory.Mem.READ_WRITE);
//        CLBuffer<FloatBuffer> psi = clContext.createFloatBuffer(atomCount * 2, CLMemory.Mem.READ_WRITE);

        commandQueue.finish();

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
                .putArg(lambda*1e-10f)
                .rewind();

        if (GL_INTEROP) {
            texBuffer2 = clContext2.createFromGLTexture2d(GL2.GL_TEXTURE_2D,
                    texId, 0, CLBuffer.Mem.WRITE_ONLY);
            System.out.println("cl buffer type:        " + texBuffer2.getGLObjectType());
            System.out.println("shared with gl buffer: " + texBuffer2.getGLObjectID());

            diffractionKernel = diffractionProg.createCLKernel("diffraction")
                    .putArg(transAtoms)
                    .putArg(psi)
                    .putArg(texBuffer2)
                    .putArg(atomCount)
                    .putArg(lambda*1e-10f)
                    .putArg(R)
                    .putArg(L)
                    .putArg(amp)
                    .putArg(phase)
                    .rewind();

        } else {
            // Create an empty OpenCL buffer
            ByteBuffer buffer = ByteBuffer.allocateDirect(bufferWidth*bufferHeight*4); buffer.order(ByteOrder.nativeOrder());
            texBuffer = clContext.createImage2d(buffer, bufferWidth, bufferHeight,
                    new CLImageFormat(CLImageFormat.ChannelOrder.RGBA, CLImageFormat.ChannelType.UNORM_INT8),
                    CLBuffer.Mem.WRITE_ONLY);

            System.out.println(String.format("texture-size=%d", texBuffer.getBuffer().capacity()));

            diffractionKernel = diffractionProg.createCLKernel("diffraction")
                    .putArg(transAtoms)
                    .putArg(psi)
                    .putArg(texBuffer)
                    .putArg(atomCount)
                    .putArg(lambda*1e-10f)
                    .putArg(R)
                    .putArg(L)
                    .putArg(amp)
                    .putArg(phase)
                    .rewind();
        }

        System.out.println("cl initialised");
    }

    @Override
    public void display (GLAutoDrawable drawable)
    {
        GL3 gl = drawable.getGL().getGL3();

        computeCL(gl);

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

        transformMatrix.getBuffer().put(atomsTransMat).rewind();
        commandQueue.putWriteBuffer(transformMatrix, false);

        initPhaseKernel.setArg(3, lambda*1e-10f);
        diffractionKernel.setArg(4, lambda*1e-10f);

        if (GL_INTEROP) {
            commandQueue.putAcquireGLObject(texBuffer2)
                    .put1DRangeKernel(atomTransformKernel, 0, bufferWidth, bufferWidth)
                    .put1DRangeKernel(initPhaseKernel, 0, bufferWidth, bufferWidth)
                    .put2DRangeKernel(diffractionKernel, 0, 0, bufferWidth, bufferHeight, 16, 16)
                    .putReleaseGLObject(texBuffer2)
                    .finish();

        } else {
            commandQueue.put1DRangeKernel(atomTransformKernel, 0, bufferWidth, bufferWidth)
                    .put1DRangeKernel(initPhaseKernel, 0, bufferWidth, bufferWidth)
                    .put2DRangeKernel(diffractionKernel, 0, 0, bufferWidth, bufferHeight, 16, 16)
                    .putReadImage(texBuffer, true)
                    .finish();

            // Copy data from the CL image buffer to the GL texture
            gl.glActiveTexture(GL2.GL_TEXTURE0);
            gl.glBindTexture  (GL2.GL_TEXTURE_2D, texId);
            gl.glTexImage2D   (GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA8, bufferWidth, bufferHeight, 0,
                    GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, texBuffer.getBuffer().rewind());
        }
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

    private void



    setAtoms(float[] atoms) {
        atomCount = atoms.length / 4;

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
    }

    public void setsetTransformMatrix(float[] atomsTransMat) {
        this.atomsTransMat = atomsTransMat;
    }

    public void setAtoms(AtomSetCollection atomsCollection) {
        int atomCount = atomsCollection.getAtomCount();
        atoms = new float[atomCount*4];

        SymmetryInterface symmetry = atomsCollection.getSymmetry();

        for(int i = 0; i < atomCount; ++i){
            if(null == symmetry || !symmetry.haveUnitCell()){
                Atom atom = atomsCollection.getAtom(i);
                atoms[i*4] = atom.x;
                atoms[i*4 + 1] = atom.y;
                atoms[i*4 + 2] = atom.z;
            } else {
                Point3f atom = new Point3f(atomsCollection.getAtom(i));
                symmetry.toCartesian(atom, true);

                atoms[i*4] = atom.x;
                atoms[i*4 + 1] = atom.y;
                atoms[i*4 + 2] = atom.z;
            }

            atoms[i*4 + 3] = 1.0f;
        }

        setAtoms(atoms);
    }
}
