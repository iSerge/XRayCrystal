package org.xraycrystal.controls;

import com.jogamp.opengl.*;
import org.xraycrystal.util.GlUtils;
import org.xraycrystal.util.Utils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public class StructureGLListener implements GLEventListener {
    private int programId;
    private int vertId;
    private int fragId;

    private int lightId;
    private int transId;

    private int vbo;
    private int bufferId;

    private int atomCount = 4;

    private float[] atoms = {
            //  Coordinates  Color              Radius
            -0.45f,  0.45f,  0.8f, 0.0f, 0.0f,  100f,
             0.45f,  0.45f,  0.0f, 0.8f, 0.0f,  50f,
             0.45f, -0.45f,  0.0f, 0.0f, 0.8f,  200f,
            -0.45f, -0.45f,  0.8f, 0.8f, 0.0f,  400f
    };

    private float[] lightDirection = {-0.5f, -0.5f, 1.0f};

    private float[] transformMatrix = {
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

        ByteBuffer data = GlUtils.clone(atoms);
        gl.glBufferData(GL2.GL_ARRAY_BUFFER, atoms.length*4, data, GL2.GL_STATIC_DRAW);

        int posId = gl.glGetAttribLocation(programId, "pos");
        int colorId = gl.glGetAttribLocation(programId, "color");
        int radiusId = gl.glGetAttribLocation(programId, "radius");
        lightId = gl.glGetUniformLocation(programId, "lightDir");
        transId = gl.glGetUniformLocation(programId, "T");

        gl.glVertexAttribPointer(posId, 2, GL2.GL_FLOAT, false, 24/* 6*sizeof(float) */, 0);
        gl.glEnableVertexAttribArray(posId);

        gl.glVertexAttribPointer(colorId, 3, GL2.GL_FLOAT, false, 24/* 6*sizeof(float) */, 8 /* 2*sizeof(float) */);
        gl.glEnableVertexAttribArray(colorId);

        gl.glVertexAttribPointer(radiusId, 2, GL2.GL_FLOAT, false, 24/* 6*sizeof(float) */, 20 /* 2*sizeof(float) */);
        gl.glEnableVertexAttribArray(radiusId);

        gl.glBindVertexArray(0);
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

        gl.glDrawArrays(GL2.GL_POINTS, 0, atomCount);

    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL3 gl = drawable.getGL().getGL3();
        gl.glViewport(0, 0, width, height);
    }

    public void updateTransformMatrix(float[] diffMatrix) {
        float[] deltaMatrix = {
            diffMatrix[0], diffMatrix[1], diffMatrix[2], 0,
            diffMatrix[3], diffMatrix[4], diffMatrix[5], 0,
            diffMatrix[6], diffMatrix[7], diffMatrix[8], 0,
                        0,             0,             0, 1
        };
        transformMatrix = Utils.matMul(deltaMatrix, transformMatrix, 4);
    }
}
