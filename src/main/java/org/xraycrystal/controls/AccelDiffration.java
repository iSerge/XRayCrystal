package org.xraycrystal.controls;

import com.jogamp.opencl.*;
import com.jogamp.opencl.gl.CLGLContext;
import com.jogamp.opencl.gl.CLGLTexture2d;
import com.jogamp.opencl.llb.CL;
import com.jogamp.opencl.util.CLDeviceFilters;
import com.jogamp.opencl.util.CLPlatformFilters;
import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import org.jetbrains.annotations.NotNull;
import org.xraycrystal.Main;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class AccelDiffration implements GLEventListener {
    private final float lambda = 0.5e-10f;
    private final float R = 1e-7f;
    final float L = 3e-7f;
    final float amp = 1.0f;

    private float[] projection_matrix = new float[]{
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
    };


    int shaderProg;
    int vShader;
    int fShader;
    int patchVAO;
    int vertexData;
    int positionAttr;
    int textureCoordAttr;
    int textureId;

    private CLCommandQueue commandQueue;

    CLKernel prepareLatticeKernel;
    CLKernel initPhaseKernel;
    CLKernel diffractionKernel;

    int atomGlobalWorkSize;
    int atomLocalWorkSize;

    int diffractionGlobalSize;
    int diffractionLocalSize;

    CLGLTexture2d<?> img;
    int atomCount;
    CLBuffer<FloatBuffer> transformMatrix;
    CLBuffer<FloatBuffer> origAtoms;
    CLBuffer<FloatBuffer> transAtoms;
    CLBuffer<FloatBuffer> psi;


    @NotNull private GLCanvas canvas;
    private CLGLContext context;
    private CLDevice device;

    private boolean needTransformAtoms = false;
    private boolean needUpdateDiffraction = false;

    public AccelDiffration(@NotNull GLCanvas canvas){
        this.canvas = canvas;
    }

    private void calculate(GL3 gl){
        gl.glFinish();

        long time = System.nanoTime();

        commandQueue.putAcquireGLObject(img);

        if(needTransformAtoms){
            commandQueue.put1DRangeKernel(prepareLatticeKernel, 0, atomGlobalWorkSize, atomLocalWorkSize);
            needTransformAtoms = false;
            needUpdateDiffraction = true;
        }
        if(needUpdateDiffraction){
            commandQueue.put1DRangeKernel(initPhaseKernel, 0, atomGlobalWorkSize, atomLocalWorkSize);
            commandQueue.put2DRangeKernel(diffractionKernel, 0, 0,
                    diffractionGlobalSize, diffractionGlobalSize,
                    diffractionLocalSize, diffractionLocalSize);
            needUpdateDiffraction = false;
        }

        commandQueue.putReleaseGLObject(img);

        commandQueue.finish();

        System.out.println(String.format("Finished diffraction calc in %f seconds",
                (float)(System.nanoTime() - time)*1e-9f));
    }

    public void setAtoms(float[] atoms){
        if(atoms.length%4 != 0){
            throw new AssertionError("Atoms array length must be multiple of 4");
        }

        clLoadAtoms(atoms);

        needTransformAtoms = true;

        canvas.display();
    }

    private void clLoadAtoms(float[] atoms) {
        atomCount = atoms.length / 4;

        if(null != origAtoms){
            origAtoms.release();
        }
        origAtoms = context.createFloatBuffer(atoms.length, CLMemory.Mem.READ_ONLY);
        origAtoms.getBuffer().put(atoms);
        commandQueue.putWriteBuffer(origAtoms, false);

        if(null != transAtoms){
            transAtoms.release();
        }

        transAtoms = context.createFloatBuffer(atoms.length, CLMemory.Mem.READ_WRITE);

        if(null != psi){
            psi.release();
        }
        psi = context.createFloatBuffer(atomCount*2, CLMemory.Mem.READ_WRITE);

        prepareLatticeKernel.setArg(0, origAtoms);
        prepareLatticeKernel.setArg(1, transAtoms);
        prepareLatticeKernel.setArg(3, atomCount);

        initPhaseKernel.setArg(0, transAtoms);
        initPhaseKernel.setArg(1, psi);
        initPhaseKernel.setArg(2, atomCount);

        diffractionKernel.setArg(0, transAtoms);
        diffractionKernel.setArg(1, psi);
        diffractionKernel.setArg(3, atomCount);

        atomLocalWorkSize = Math.min(device.getMaxWorkGroupSize(), 256);
        atomGlobalWorkSize = roundUp(atomLocalWorkSize, atomCount);

        commandQueue.finish();
    }

    public void display(GLAutoDrawable gLDrawable) {
        final GL3 gl = gLDrawable.getGL().getGL3();

        calculate(gl);

        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

        gl.glUseProgram(shaderProg);

        gl.glActiveTexture(GL3.GL_TEXTURE0);
        gl.glBindTexture(GL3.GL_TEXTURE_2D, textureId);

        gl.glBindVertexArray(patchVAO);

        int projectionLocation = gl.glGetUniformLocation(shaderProg, "P");
        gl.glUniformMatrix4fv(projectionLocation, 1, false, FloatBuffer.wrap(projection_matrix));

        int samplerLocation = gl.glGetUniformLocation(shaderProg, "texture_diffuse");
        gl.glUniform1i(samplerLocation, 0);


        gl.glDrawArrays(GL2.GL_TRIANGLE_FAN, 0, 4);

        gl.glUseProgram(0);
    }

    public void init(GLAutoDrawable gLDrawable) {
        gLDrawable.setGL(new DebugGL3(gLDrawable.getGL().getGL3()));

        final GL3 gl = gLDrawable.getGL().getGL3();
        gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        gl.glClearDepth(1.0f);
        gl.glDisable   (GL2.GL_DEPTH_TEST);
        gl.glPixelStorei(GL2.GL_UNPACK_ALIGNMENT, 1);

        vShader = gl.glCreateShader(GL3.GL_VERTEX_SHADER);
        fShader = gl.glCreateShader(GL3.GL_FRAGMENT_SHADER);

        String vsSource = readResource("/org/xraycrystal/vertex.shader");
        IntBuffer length = IntBuffer.allocate(1);
        length.put(0, vsSource.length());

        gl.glShaderSource(vShader, 1, new String[] {vsSource}, length);
        gl.glCompileShader(vShader);
        shaderStatus(gl, vShader, GL3.GL_COMPILE_STATUS);

        String fsSource = readResource("/org/xraycrystal/fragment.shader");
        length.put(0, fsSource.length());

        gl.glShaderSource(fShader, 1, new String[] {fsSource}, length);
        gl.glCompileShader(fShader);
        shaderStatus(gl, fShader, GL3.GL_COMPILE_STATUS);

        shaderProg = gl.glCreateProgram();
        gl.glAttachShader(shaderProg, vShader);
        gl.glAttachShader(shaderProg, fShader);

        gl.glLinkProgram(shaderProg);
        programStatus(gl, shaderProg, GL3.GL_LINK_STATUS);
        gl.glValidateProgram(shaderProg);
        programStatus(gl, shaderProg, GL3.GL_VALIDATE_STATUS);

        gl.glUseProgram(shaderProg);

        positionAttr = gl.glGetAttribLocation(shaderProg, "in_Position");
        textureCoordAttr = gl.glGetAttribLocation(shaderProg, "in_TextureCoord");

        IntBuffer vaoId = IntBuffer.allocate(1);
        gl.glGenVertexArrays(1,vaoId);
        patchVAO = vaoId.get(0);

        IntBuffer vboIds = IntBuffer.allocate(2);
        gl.glGenBuffers(1, vboIds);
        vertexData = vboIds.get(0);

        gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, vertexData);
        float d = Main.IMAGE_DIM;
        FloatBuffer vData = FloatBuffer.wrap(new float[] {
                0f, 0f, 0f, 1f, 0f, 0f,
                d,  0f, 0f, 1f, 1f, 0f,
                d,   d, 0f, 1f, 1f, 1f,
                0f,  d, 0f, 1f, 0f, 1f
        });
        gl.glBufferData(GL3.GL_ARRAY_BUFFER, vData.capacity()*4, vData, GL.GL_STATIC_DRAW);

        gl.glBindVertexArray(patchVAO);

        gl.glVertexAttribPointer(positionAttr, 4, GL3.GL_FLOAT, false, 4, 0);
        gl.glEnableVertexAttribArray(positionAttr);
        gl.glVertexAttribPointer(textureCoordAttr, 4, GL3.GL_FLOAT, false, 4, 4);
        gl.glEnableVertexAttribArray(textureCoordAttr);

        float tx = (diffractionGlobalSize+0.0f) /(diffractionGlobalSize-0.0f);
        float ty = (0.0f+diffractionGlobalSize)/(0.0f-diffractionGlobalSize);
        float tz = (0.0f+1.0f)  /(1.0f-0.0f);
        projection_matrix[ 0] =  2.0f/(diffractionGlobalSize-0.0f);
        projection_matrix[ 5] =  2.0f/(0.0f-diffractionGlobalSize);
        projection_matrix[10] = -2.0f/(1.0f-0.0f);
        projection_matrix[12] = -tx;
        projection_matrix[13] = -ty;
        projection_matrix[14] = -tz;

        int projectionLocation = gl.glGetUniformLocation(shaderProg, "P");
        gl.glUniformMatrix4fv(projectionLocation, 1, false, FloatBuffer.wrap(projection_matrix));


//        gl.glBindBuffer(GL3.GL_ARRAY_BUFFER, 0);
        gl.glBindVertexArray(0);

        //Initialising OpenCL
        CLDevice[] devices = CLPlatform.getDefault(CLPlatformFilters.glSharing()).listCLDevices(CLDeviceFilters.glSharing());
        for(CLDevice dev : devices){
            System.out.println("Got OpenCL device: " + dev.getName());
        }

        if(devices.length == 0){
            throw new AssertionError("Failed to get CL/GL sharing device");
        }

        context = CLGLContext.create(gl.getContext(), devices);

        device = context.getMaxFlopsDevice();
        System.out.println("Selected OpenCL device: " + device.getName());

        commandQueue = device.createCommandQueue();

        System.out.println("Device max workgroup size is: " + device.getMaxWorkGroupSize());

        diffractionLocalSize = Math.min((int)Math.sqrt((float)device.getMaxWorkGroupSize()), 16);
        diffractionGlobalSize = roundUp(diffractionLocalSize, Main.IMAGE_DIM);

        String diffractionProgram = readResource("/org/xraycrystal/diffraction.cl");

        CLProgram prog = context.createProgram(diffractionProgram);
        prog.build();

        prepareLatticeKernel = prog.createCLKernel("prepareLattice");
        initPhaseKernel = prog.createCLKernel("initPhase");
        diffractionKernel = prog.createCLKernel("diffraction");

        transformMatrix = context.createFloatBuffer(9, CLMemory.Mem.READ_ONLY);
        transformMatrix.getBuffer().put(new float[] {1f, 0f, 0f,
                                                     0f, 1f, 0f,
                                                     0f, 0f, 1f});
        commandQueue.putWriteBuffer(transformMatrix, true);

        System.out.println("kernel workgroup size: " + prepareLatticeKernel.getWorkGroupSize(device));
        prepareLatticeKernel.setArg(2, transformMatrix);

        initPhaseKernel.setArg(3, lambda);

        int[] texArray = new int[1];
        gl.glGenTextures(1, texArray, 0);
        textureId = texArray[0];

        gl.glActiveTexture(GL2.GL_TEXTURE0);
        gl.glBindTexture  (GL2.GL_TEXTURE_2D, textureId);
        gl.glUniform1i    (gl.glGetUniformLocation(shaderProg, "texture_diffuse"), 0);

        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
        // texture wrap mode
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S,
                GL2.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T,
                GL2.GL_CLAMP_TO_EDGE);
        // set image size only
        gl.glTexImage2D   (GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA, diffractionGlobalSize, diffractionGlobalSize, 0,
                GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, null);
        gl.glBindTexture  (GL2.GL_TEXTURE_2D, 0);

        gl.glUseProgram(0);

        img = context.createFromGLTexture2d(GL3.GL_TEXTURE_2D, textureId, 0, CL.CL_MEM_WRITE_ONLY);

        diffractionKernel.setArg(2, img);
        diffractionKernel.setArg(4, lambda);
        diffractionKernel.setArg(5, R);
        diffractionKernel.setArg(6, L);
        diffractionKernel.setArg(7, amp);
        diffractionKernel.setArg(8, 0);

        clLoadAtoms(new float[] {0f, 0f, 0f, 1f});
    }

    public void reshape(GLAutoDrawable gLDrawable, int x,
                        int y, int width, int height) {
    }

    public void dispose(GLAutoDrawable arg0) {
        GL3 gl = arg0.getGL().getGL3();

        context.release();

        if(0 != shaderProg){
            gl.glDeleteProgram(shaderProg);
            gl.glDeleteShader(vShader);
            gl.glDeleteShader(fShader);
        }
    }

    private static String getGLErrorString(int error){
        switch(error){
            case GL3.GL_NO_ERROR:
                return "GL_NO_ERROR";
            case GL3.GL_INVALID_ENUM:
                return "GL_INVALID_ENUM";
            case GL3.GL_INVALID_VALUE:
                return "GL_INVALID_VALUE";
            case GL3.GL_INVALID_OPERATION:
                return "GL_INVALID_OPERATION";
            case GL3.GL_OUT_OF_MEMORY:
                return "GL_OUT_OF_MEMORY";
            case GL3.GL_INVALID_FRAMEBUFFER_OPERATION:
                return "GL_INVALID_FRAMEBUFFER_OPERATION";
            case GL3.GL_STACK_OVERFLOW:
                return "GL_STACK_OVERFLOW";
            case GL3.GL_STACK_UNDERFLOW:
                return "GL_STACK_UNDERFLOW";
            case GL2.GL_TABLE_TOO_LARGE:
                return "GL_TABLE_TOO_LARGE";
            default:
                return String.format("(ERROR: Unknown Error Enum - %d)", error);
        }
    }

    private static void checkOpenGLerror(GL3 gl, String loc) {
        int errCode = gl.glGetError();
        if(errCode != GL3.GL_NO_ERROR){
            System.err.println(String.format("%s: OpenGl error! - %s\n",loc, getGLErrorString(errCode)));
        }
    }


    private static int shaderStatus(GL3 gl, int shader, int param)
    {
        IntBuffer status = IntBuffer.allocate(1);

        gl.glGetShaderiv(shader, param, status);
        checkOpenGLerror(gl, "ShaderStatus.glGetShaderiv");

        if (status.get(0) != GL3.GL_TRUE){
            IntBuffer infologLen = IntBuffer.allocate(1);
            gl.glGetShaderiv(shader, GL3.GL_INFO_LOG_LENGTH, infologLen);
            checkOpenGLerror(gl, "ShaderStatus.glGetShaderiv");
            if(infologLen.get(0) > 1){
                ByteBuffer infoLog = ByteBuffer.allocate(infologLen.get(0));
                IntBuffer charsWritten = IntBuffer.allocate(1);
                gl.glGetShaderInfoLog(shader, infologLen.get(0), charsWritten, infoLog);
                checkOpenGLerror(gl, "ShaderStatus.glGetShaderInfoLog");
                System.err.println("ShaderStatus::glGetShaderInfoLog");
                System.err.println(String.format("Shader program: %s", new String(infoLog.array(), 0, charsWritten.get(0))));
            }
        }

        return status.get(0);
    }

    private static int programStatus(GL3 gl, int program, int param)
    {
        IntBuffer status = IntBuffer.allocate(1);

        gl.glGetProgramiv(program, param, status);
        checkOpenGLerror(gl, "ProgramStatus.glGetProgramiv");

        if (status.get(0) != GL3.GL_TRUE){
            IntBuffer infologLen = IntBuffer.allocate(1);
            gl.glGetProgramiv(program, GL3.GL_INFO_LOG_LENGTH, infologLen);
            checkOpenGLerror(gl, "ProgramStatus.glGetProgramiv");
            if(infologLen.get(0) > 1){
                ByteBuffer infoLog = ByteBuffer.allocate(infologLen.get(0));
                IntBuffer charsWritten = IntBuffer.allocate(1);
                gl.glGetProgramInfoLog(program, infologLen.get(0), charsWritten, infoLog);
                checkOpenGLerror(gl, "ProgramStatus.glGetProgramInfoLog");
                System.err.println("ProgramStatus::glGetShaderInfoLog");
                System.err.println(String.format("Program: %s", new String(infoLog.array(), 0, charsWritten.get(0))));
            }
        }

        return status.get(0);
    }


    @NotNull
    private String readResource(String fileName)
    {
        try(BufferedReader br = new BufferedReader(
                new InputStreamReader(AccelDiffration.class.getResourceAsStream(fileName))))
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

    private static int roundUp(int groupSize, int globalSize) {
        int r = globalSize % groupSize;
        if (r == 0) {
            return globalSize;
        } else {
            return globalSize + groupSize - r;
        }
    }
}
