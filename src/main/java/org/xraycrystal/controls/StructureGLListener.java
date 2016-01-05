package org.xraycrystal.controls;

import com.jogamp.opengl.*;
import org.xraycrystal.util.GlUtils;
import org.xraycrystal.util.Utils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class StructureGLListener implements GLEventListener {
    public static final int ATOM_DESCR_LEN = 7;
    private int programId;
    private int vertId;
    private int fragId;

    private int lightId;
    private int transId;
    private int modelMatId;
    private int projMatId;

    private int vbo;
    private int bufferId = -1;

    private float[] atoms = {
         //  Coordinates            Color              Radius
             0.00f, 0.00f, 0.00f,   0.8f, 0.8f, 0.8f,  100f,
             3.00f, 0.00f, 0.00f,   0.8f, 0.8f, 0.8f,  100f,
    };

    private int atomCount = atoms.length / ATOM_DESCR_LEN;

    private float[] lightDirection = {-0.5f, -0.5f, 1.0f};

    private float[] modelMatrix = {
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
    };

    private float[] transformMatrix = {
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
    };

    private float[] projectionMatrix = {
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
    };

    @Override
    public void init(GLAutoDrawable drawable) {
        drawable.setGL(new DebugGL3(drawable.getGL().getGL3()));

        GL3 gl = drawable.getGL().getGL3();

        gl.glClearColor(0.2f, 0.2f, 0.2f, 1.0f);
        gl.glClearDepth(1.0f);

        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDepthFunc(GL2.GL_LEQUAL);

        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendEquationSeparate(GL2.GL_FUNC_ADD, GL2.GL_FUNC_ADD);
        gl.glBlendFuncSeparate(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA, GL2.GL_ONE, GL2.GL_ZERO);

        gl.glEnable(GL4.GL_PROGRAM_POINT_SIZE);

        gl.glPixelStorei(GL2.GL_UNPACK_ALIGNMENT, 1);

        initShader(drawable);

        initBuffers(drawable);
    }

    private void initBuffers(GLAutoDrawable drawable) {
        GL3 gl = drawable.getGL().getGL3();

        int[] bufferIds = new int[1];
        gl.glGenBuffers(1, bufferIds, 0);
        bufferId = bufferIds[0];

        int[] vboArray = new int[1];
        gl.glGenVertexArrays(1, vboArray, 0);
        vbo = vboArray[0];

        gl.glBindVertexArray(vbo);
        gl.glBindBuffer(GL2.GL_ARRAY_BUFFER, bufferId);

        setAtoms(atoms ,drawable);

        int posId = gl.glGetAttribLocation(programId, "pos");
        int colorId = gl.glGetAttribLocation(programId, "color");
        int radiusId = gl.glGetAttribLocation(programId, "radius");
        lightId = gl.glGetUniformLocation(programId, "lightDir");
        transId = gl.glGetUniformLocation(programId, "T");
        modelMatId = gl.glGetUniformLocation(programId, "M");
        projMatId = gl.glGetUniformLocation(programId, "P");

        gl.glVertexAttribPointer(posId, 3, GL2.GL_FLOAT, false, 28/* ATOM_DESCR_LEN*sizeof(float) */, 0);
        gl.glEnableVertexAttribArray(posId);

        gl.glVertexAttribPointer(colorId, 3, GL2.GL_FLOAT, false, 28/* ATOM_DESCR_LEN*sizeof(float) */, 12 /* 3*sizeof(float) */);
        gl.glEnableVertexAttribArray(colorId);

        gl.glVertexAttribPointer(radiusId, 1, GL2.GL_FLOAT, false, 28/* ATOM_DESCR_LEN*sizeof(float) */, 24 /* 6*sizeof(float) */);
        gl.glEnableVertexAttribArray(radiusId);

        gl.glBindVertexArray(0);
    }

    private void setAtoms(float[] atoms, GLAutoDrawable drawable) {
        GL3 gl = drawable.getGL().getGL3();

        atomCount = atoms.length / ATOM_DESCR_LEN;

        float[] center = {0f, 0f, 0f};
        float[] min = {1e20f, 1e20f, 1e20f};
        float[] max = {-1e20f, -1e20f, -1e20f};

        for(int i = 0; i < atomCount; ++i){
            for(int j = 0; j < 3; ++j) {
                float coord = atoms[i * ATOM_DESCR_LEN + j];
                center[j] += coord;
                min[i] = Math.min(min[i], coord);
                max[i] = Math.max(max[i], coord);
            }
        }

        float maxDelta = 1.4142f * Math.max(max[0]-min[0], Math.max(max[1]-min[1], max[2]-min[2]));

        for(int i = 0; i < atomCount; ++i){
            atoms[i * ATOM_DESCR_LEN + 6] /= Math.sqrt(maxDelta);
        }

        projectionMatrix[15] = maxDelta;

        modelMatrix[12] = -center[0]/atomCount;
        modelMatrix[13] = -center[1]/atomCount;
        modelMatrix[14] = -center[2]/atomCount;

        ByteBuffer data = GlUtils.clone(atoms);
        gl.glBufferData(GL2.GL_ARRAY_BUFFER, atoms.length*4, data, GL2.GL_STATIC_DRAW);
    }

    public void initShader (GLAutoDrawable d)
    {
        GL3 gl = d.getGL().getGL3(); // get the OpenGL 3 graphics context

        vertId = gl.glCreateShader(GL2.GL_VERTEX_SHADER);
        fragId = gl.glCreateShader(GL2.GL_FRAGMENT_SHADER);

        String sourceVS = Utils.readResource("/org/xraycrystal/struct.vertex");
        String sourceFS = Utils.readResource("/org/xraycrystal/struct.fragment");

        String[] vs = { sourceVS };
        String[] fs = { sourceFS };

        gl.glShaderSource(vertId, 1, vs, null, 0);
        gl.glShaderSource(fragId, 1, fs, null, 0);

        // compile the shader
        gl.glCompileShader(vertId);
        gl.glCompileShader(fragId);

        GlUtils.printShaderInfoLog(d, vertId);
        GlUtils.printShaderInfoLog(d, fragId);


        // create program and attach shaders
        programId = gl.glCreateProgram();
        gl.glAttachShader(programId, vertId);
        gl.glAttachShader(programId, fragId);

        // "out_color" is a user-provided OUT variable of the fragment shader.
        // Its output is bound to the first color buffer in the framebuffer
        gl.glBindFragDataLocation(programId, 0, "outColor");

        // link the program
        gl.glLinkProgram(programId);
        GlUtils.printProgramInfoLog(d, programId);
    }


    @Override
    public void dispose(GLAutoDrawable drawable) {
        GL3 gl = drawable.getGL().getGL3();

        gl.glBindVertexArray(0);
        gl.glUseProgram(0);

        gl.glDetachShader(programId, vertId);
        gl.glDetachShader(programId, fragId);

        gl.glDeleteShader(vertId);
        gl.glDeleteShader(fragId);
        gl.glDeleteProgram(programId);

        gl.glDeleteVertexArrays(1, new int[] {vbo}, 0);
        gl.glDeleteBuffers(1, new int[] {bufferId}, 0);
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL3 gl = drawable.getGL().getGL3();

        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

        gl.glUseProgram(programId);
        gl.glBindVertexArray(vbo);

        gl.glUniform3fv(lightId, 1, lightDirection, 0);

        gl.glUniformMatrix4fv(transId, 1, false, FloatBuffer.wrap(transformMatrix));

        gl.glUniformMatrix4fv(modelMatId, 1, false, FloatBuffer.wrap(modelMatrix));
        gl.glUniformMatrix4fv(projMatId, 1, false, FloatBuffer.wrap(projectionMatrix));

        gl.glDrawArrays(GL2.GL_POINTS, 0, atomCount);
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL3 gl = drawable.getGL().getGL3();
        gl.glViewport(0, 0, width, height);
    }

    public void setTransformMatrix(float[] mat){
        transformMatrix[0] = mat[0];
        transformMatrix[1] = mat[1];
        transformMatrix[2] = mat[2];
        transformMatrix[3] = 0;
        transformMatrix[4] = mat[3];
        transformMatrix[5] = mat[4];
        transformMatrix[6] = mat[5];
        transformMatrix[7] = 0;
        transformMatrix[8] = mat[6];
        transformMatrix[9] = mat[7];
        transformMatrix[10] = mat[8];
        transformMatrix[11] = 0;
    }
}
