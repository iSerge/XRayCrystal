package org.xraycrystal;/* JOGL - JOCL in Java. Demo by fifty.
 */

import com.jogamp.opencl.CLCommandQueue;
import com.jogamp.opencl.CLDevice;
import com.jogamp.opencl.gl.*;
import com.jogamp.opencl.*;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLPlatform;
import com.jogamp.opencl.CLProgram;
import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.DebugGL3;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.*;

/** OpenGL texture generated by JOCL */
public class DemoViewer implements GLEventListener
{
    private int     bufferWidth  = 512; // texture size
    private int     bufferHeight = 512;
    private boolean GL_INTEROP   = true; // switch for CL-GL transfers

    private final float lambda = 0.5e-10f;
    private final float R = 1e-7f;
    private final float L = 3e-7f;
    private final float amp = 2f;
    private final int phase = 0;
    private final double w = 0.5;
    private double angle = 0.0;

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

    private JFrame     frame;

    private int        texId;
    private int        programId;
    private int        vbo;
    private CLDevice   device;

    private CLGLContext clContext2; // only used if GL_INTEROP==true
    private CLContext   clContext;  // always used; (clContext2 == clContext) iff GL_INTEROP==true

    private CLKernel       kernel;

    private CLKernel atomTransformKernel;
    private CLKernel initPhaseKernel;
    private CLKernel diffractionKernel;

    private CLCommandQueue commandQueue;

    // only one is used: texBuffer (iff GL_INTEROP==false), or texBuffer2 (iff GL_INTEROP==true)
    private CLGLTexture2d<?>     texBuffer2;
    private CLImage2d<ByteBuffer> texBuffer;

    private float[] atoms = {
//            0f,0f,0f,1f
                2.307707f,    0.000000f,    3.601333f, 1f, //Si
               -0.148293f,    4.253917f,    3.601333f, 1f, //Si
               -1.153853f,    1.998533f,    1.800667f, 1f, //Si
                3.758147f,    1.998533f,    1.800667f, 1f, //Si
                1.302147f,    2.255384f,    0.000000f, 1f, //Si
                1.302147f,    2.255384f,    5.402000f, 1f, //Si
                1.374869f,    1.138348f,    4.244351f, 1f, //O
                3.238727f,    0.621497f,    2.443685f, 1f, //O
                2.754404f,    2.494071f,    0.643018f, 1f, //O
                0.298404f,    1.759845f,    1.157648f, 1f, //O
               -1.081131f,    3.115569f,    2.958315f, 1f, //O
                0.782727f,    3.632419f,    4.758982f, 1f, //O
    };

    private int atomCount = atoms.length / 4;

    private float[] atomsTransMat = {
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
    };

    private CLBuffer<FloatBuffer> transformMatrix;
    private CLBuffer<FloatBuffer> origAtoms;
    private CLBuffer<FloatBuffer> transAtoms;
    private CLBuffer<FloatBuffer> psi;

    private long prevTimeNS = 0;

    /** */
    public DemoViewer () {

        SwingUtilities.invokeLater(this::initUI);

    }

    /** */
    private void initUI() {
        GLCapabilities config = new GLCapabilities(GLProfile.get(GLProfile.GL3));

        GLCanvas canvas = new GLCanvas(config);
        canvas.addGLEventListener(this);

        frame = new JFrame("DemoViewer");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add       (canvas);
        frame.setSize   (bufferWidth, bufferHeight);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }


    /** */
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
                clContext  = CLContext  .create(device);
            }

            // enable GL error checking using the composable pipeline
            drawable.setGL(new DebugGL3(drawable.getGL().getGL3()));

            // OpenGL initialization
            GL3 gl = drawable.getGL().getGL3();
            gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            gl.glClearDepth(1.0f);
            gl.glDisable   (GL2.GL_DEPTH_TEST);
            gl.glPixelStorei(GL2.GL_UNPACK_ALIGNMENT, 1);

            initShader     (drawable);
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
            ByteBuffer texData = clone(quadTexcoords);
            gl.glBufferData(GL2.GL_ARRAY_BUFFER, quadTexcoords.length*4, texData, GL2.GL_STATIC_DRAW);
            gl.glVertexAttribPointer    (getAttribLocation(gl, "texcoord"), 2, GL2.GL_FLOAT, false, 0, 0);
            gl.glEnableVertexAttribArray(getAttribLocation(gl, "texcoord"));
            gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, idArray[1]);
            ByteBuffer coordData = clone(quadCoords);
            System.out.println(String.format("%d/%d, %d/%d",
                    getAttribLocation(gl, "vertex"),   coordData.capacity(),
                    getAttribLocation(gl, "texcoord"), texData.capacity()));
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
            gl.glTexImage2D   (GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA, bufferWidth, bufferHeight, 0,
                    GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, null);
            gl.glBindTexture  (GL2.GL_TEXTURE_2D, 0);
            // initialize OpenCL, creating a context for the given GL object
            initCL(gl);

            // start rendering thread
            Animator animator = new Animator(drawable);
            animator.start();

        }
    }

    /** float-array to direct ByteBuffer. */
    private ByteBuffer clone (float[] data)
    {
        int         len    = data.length;
        ByteBuffer  direct = ByteBuffer.allocateDirect(len*4);
        direct.order(ByteOrder.nativeOrder()); // very important!
        for (int i=0; i<len; ++i) {
            direct.putFloat(data[i]);
        }
        direct.rewind();
        return direct;
    }

    /** */
    public void initShader (GLAutoDrawable d)
    {
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 3 graphics context

        int vertID = gl.glCreateShader(GL2.GL_VERTEX_SHADER);
        int fragID = gl.glCreateShader(GL2.GL_FRAGMENT_SHADER);

        String sourceVS = "#version 330\n" +
                "uniform sampler2D tex; \n" +
                "uniform mat4      M, P;\n" +
                "in  vec2 texcoord;     \n" +
                "in  vec2 vertex;       \n" +
                "out vec2 out_texcoord; \n" +
                "void main(void)        \n" +
                "{\n" +
                "   out_texcoord = texcoord;                                \n" +
                "   gl_Position  = P*(M*vec4(vertex.x, vertex.y, 0.0, 1.0));\n" +
                "}";
        String sourceFS = "#version 330\n" +
                "uniform sampler2D tex;  \n" +
                "uniform mat4      M, P; \n" +
                "in  vec2 out_texcoord;  \n" +
                "out vec4 out_color;     \n" +
                "void main (void)        \n" +
                "{                       \n" +
                "   out_color = texture(tex, out_texcoord); \n" +
                "}";

        String[] vs = { sourceVS };
        String[] fs = { sourceFS };

        gl.glShaderSource(vertID, 1, vs, null, 0);
        gl.glShaderSource(fragID, 1, fs, null, 0);

        // compile the shader
        gl.glCompileShader(vertID);
        gl.glCompileShader(fragID);

        // check for errors
        printShaderInfoLog(d, vertID);
        printShaderInfoLog(d, fragID);

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
        printProgramInfoLog(d, programId);
    }

    /** */
    private int getLocation (GL3 gl, String name)
    {
        return gl.glGetUniformLocation(programId, name);
    }
    /** */
    private int getAttribLocation (GL3 gl, String name)
    {
        return gl.glGetAttribLocation(programId, name);
    }
    /** */
    private void printShaderInfoLog(GLAutoDrawable drawable, int obj)
    {
        GL3       gl = drawable.getGL().getGL3(); // get the OpenGL 3 graphics context
        IntBuffer infoLogLengthBuf = IntBuffer.allocate(1);
        int infoLogLength;
        gl.glGetShaderiv(obj, GL2.GL_INFO_LOG_LENGTH, infoLogLengthBuf);
        infoLogLength = infoLogLengthBuf.get(0);
        if (infoLogLength > 0) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(infoLogLength);
            gl.glGetShaderInfoLog(obj, infoLogLength, infoLogLengthBuf, byteBuffer);
            for (byte b:byteBuffer.array()){
                System.err.print((char)b);
            }
        }
    }

    /** */
    private void printProgramInfoLog(GLAutoDrawable drawable, int obj)
    {
        GL3       gl = drawable.getGL().getGL3(); // get the OpenGL 3 graphics context
        IntBuffer infoLogLengthBuf = IntBuffer.allocate(1);
        int infoLogLength;
        gl.glGetProgramiv(obj, GL2.GL_INFO_LOG_LENGTH, infoLogLengthBuf);
        infoLogLength = infoLogLengthBuf.get(0);
        if (infoLogLength > 0) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(infoLogLength);
            gl.glGetProgramInfoLog(obj, infoLogLength, infoLogLengthBuf, byteBuffer);
            for (byte b:byteBuffer.array()){
                System.err.print((char)b);
            }
        }
    }

    /** */
    private void initCL (GL3 gl)
    {
        // ensure pipeline is clean before doing cl work
        gl.glFinish();

        String sourceCL = "__kernel void function (__write_only image2d_t store, unsigned w, unsigned h ) \n" +
                "{ \n" +
                "    unsigned  x = get_global_id(0); \n" +
                "    float     aspect = h/(float)w;  \n" +
                "    int2 ic;      \n" +
                "    ic.s0 = x;    \n" +
                "    float4  color; \n" +
                "    color.s0 = color.s2 = 0.0f; \n" +
                "    color.s1 = 0.5f; \n" +
                "    color.s3 = 1.0f;                     \n" +
                "    // clear image                      \n" +
                "    for (int y=0; y<h; ++y) {           \n" +
                "       ic.s1 = y/aspect;                \n" +
                "       write_imagef(store, ic, color); \n" +
                "    }                                   \n" +
                "    // set red pixel on diagonal (no anti-aliasing) \n" +
                "    color.s0 = 1.0f;        \n" +
                "    ic.s1    = x*aspect;   \n" +
                "    write_imagef(store, ic, color);  \n" +
                "}";
        CLProgram program = clContext.createProgram(sourceCL);
        program.build();
        System.out.println(program.getBuildStatus());
        System.out.println(program.isExecutable());
        System.out.println(program.getBuildLog());

        String diffractionProgram = readResource("/org/xraycrystal/diffraction.cl");
        CLProgram diffractionProg = clContext.createProgram(diffractionProgram);
        diffractionProg.build();
        System.out.println(diffractionProg.getBuildStatus());
        System.out.println(diffractionProg.isExecutable());
        System.out.println(diffractionProg.getBuildLog());

        commandQueue = device.createCommandQueue();

        for(int i = 0; i < atomCount; ++i){
            atoms[i*4] *= 1e-10;
            atoms[i*4+1] *= 1e-10;
            atoms[i*4+2] *= 1e-10;
        }

        origAtoms = clContext.createFloatBuffer(atomCount*4, CLMemory.Mem.READ_ONLY);
        origAtoms.getBuffer().put(atoms).rewind();
        commandQueue.putWriteBuffer(origAtoms,false);

        transformMatrix = clContext.createFloatBuffer(9, CLMemory.Mem.READ_ONLY);
        transformMatrix.getBuffer().put(atomsTransMat).rewind();
        commandQueue.putWriteBuffer(transformMatrix, false);

        transAtoms = clContext.createFloatBuffer(atomCount*4, CLMemory.Mem.READ_WRITE);
        psi = clContext.createFloatBuffer(atomCount*2, CLMemory.Mem.READ_WRITE);

        commandQueue.finish();

        atomTransformKernel = diffractionProg.createCLKernel("prepareLattice")
                .putArg(origAtoms)
                .putArg(transAtoms)
                .putArg(transformMatrix)
                .putArg(atomCount)
                .rewind();

        initPhaseKernel = diffractionProg.createCLKernel("initPhase")
                .putArg(transAtoms)
                .putArg(psi)
                .putArg(atomCount)
                .putArg(lambda)
                .rewind();

        if (GL_INTEROP) {
            texBuffer2 = clContext2.createFromGLTexture2d(GL2.GL_TEXTURE_2D,
                    texId, 0, CLBuffer.Mem.WRITE_ONLY);
            System.out.println("cl buffer type:        " + texBuffer2.getGLObjectType());
            System.out.println("shared with gl buffer: " + texBuffer2.getGLObjectID());

            kernel = program.createCLKernel("function")
                    .putArg(texBuffer2)
                    .putArg(bufferWidth)
                    .putArg(bufferHeight)
                    .rewind();

            diffractionKernel = diffractionProg.createCLKernel("diffraction")
                    .putArg(transAtoms)
                    .putArg(psi)
                    .putArg(texBuffer2)
                    .putArg(atomCount)
                    .putArg(lambda)
                    .putArg(R)
                    .putArg(L)
                    .putArg(amp)
                    .putArg(phase)
                    .rewind();

        } else {
            // Create an empty OpenCL buffer
            ByteBuffer buffer = ByteBuffer.allocateDirect(bufferWidth*bufferHeight*4); buffer.order(ByteOrder.nativeOrder());
            texBuffer = clContext.createImage2d(buffer, bufferWidth, bufferHeight,
                    new CLImageFormat(CLImageFormat.ChannelOrder.RGBA, CLImageFormat.ChannelType.UNSIGNED_INT8),
                    CLBuffer.Mem.WRITE_ONLY);

            System.out.println(String.format("texture-size=%d", texBuffer.getBuffer().capacity()));
            kernel = program.createCLKernel ("function")
                    .putArg(texBuffer)
                    .putArg(bufferWidth)
                    .putArg(bufferHeight)
                    .rewind();

            diffractionKernel = diffractionProg.createCLKernel("diffraction")
                    .putArg(transAtoms)
                    .putArg(psi)
                    .putArg(texBuffer)
                    .putArg(atomCount)
                    .putArg(lambda)
                    .putArg(R)
                    .putArg(L)
                    .putArg(amp)
                    .putArg(phase)
                    .rewind();
        }

        System.out.println("cl initialised");
    }

    /** */
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

        // Update FPS information in main frame title
        long currentTime = System.nanoTime();
        long diff = currentTime - prevTimeNS;
        prevTimeNS = currentTime;
        double fps = (diff / 1e9);
        frame.setTitle("JOCL Test - " +String.format("%.2f", 1.0/fps)+ " fps");
    }

    /** executes OpenCL kernel to generate image for GL texture. */
    private void computeCL (GL3 gl)
    {
        // ensure pipeline is clean before doing cl work
        gl.glFinish();

        if(0 != prevTimeNS){
            long delta = System.nanoTime() - prevTimeNS;
            angle += w * (delta * 1e-9);
            if(2.0 * Math.PI < angle){
                angle -= 2.0*Math.PI;
            }
            float cos = (float)Math.cos(angle);
            float sin = (float)Math.sin(angle);
            atomsTransMat[0] = cos;
            atomsTransMat[2] = -sin;
            atomsTransMat[6] = sin;
            atomsTransMat[8] = cos;
            transformMatrix.getBuffer().put(atomsTransMat).rewind();
            commandQueue.putWriteBuffer(transformMatrix, false);
        }

        if (GL_INTEROP) {
            commandQueue.putAcquireGLObject(texBuffer2)
//                    .put1DRangeKernel  (kernel, 0, bufferWidth, bufferWidth)
                    .put1DRangeKernel(atomTransformKernel, 0, bufferWidth, bufferWidth)
                    .put1DRangeKernel(initPhaseKernel, 0, bufferWidth, bufferWidth)
                    .put2DRangeKernel(diffractionKernel, 0, 0, bufferWidth, bufferHeight, 16, 16)
                    .putReleaseGLObject(texBuffer2)
                    .finish();

        } else {
            //commandQueue.put1DRangeKernel(kernel, 0, bufferWidth, bufferWidth)
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

    /** */
    @Override
    public void reshape(GLAutoDrawable drawable, int arg1, int arg2, int width, int height)
    {
        GL3 gl = drawable.getGL().getGL3();
        gl.glViewport(0, 0, width, height);
    }

    /** */
    @Override
    public void dispose(GLAutoDrawable drawable)
    {
        clContext.release();
    }

    /** */
    public static void main(String[] args)
    {
        GLProfile.initSingleton();

        new DemoViewer();
    }

    @NotNull
    private static String readResource(String fileName)
    {
        try(BufferedReader br = new BufferedReader(
                new InputStreamReader(DemoViewer.class.getResourceAsStream(fileName))))
        {
            StringBuilder sb = new StringBuilder();
            String line;
            while (true)
            {
                line = br.readLine();
                if (line == null)
                {
                    break;
                }
                sb.append(line).append("\n");
            }
            br.close();
            return sb.toString();
        }
        catch (IOException e)
        {
            throw new AssertionError("Can't find resource: " + fileName);
        }
    }

}
