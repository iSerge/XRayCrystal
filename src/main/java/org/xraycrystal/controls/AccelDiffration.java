package org.xraycrystal.controls;

import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLDevice;
import com.jogamp.opencl.CLPlatform;
import com.jogamp.opencl.gl.CLGLContext;
import com.jogamp.opencl.llb.CL;
import com.jogamp.opencl.util.CLDeviceFilters;
import com.jogamp.opencl.util.CLPlatformFilters;
import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.vecmath.Point3f;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;

public class AccelDiffration implements GLEventListener {
    private float rotateT = 0.0f;
    private static final GLU glu = new GLU();

    private static String vsSource =
            "#version 150 core\n" +
//            "uniform mat4 projectionMatrix;\n" +
            "in float coordx;\n" +
            "in float coordy;\n" +
            "void main() {\n" +
//            "  gl_Position = projectionMatrix * vec4(coordx, coordy, 0.0, 1.0);\n" +
            "  gl_Position = vec4(coordx, coordy, 0.0, 1.0);\n" +
            "}\n";

    private static String fsSource =
            "#version 150 core\n" +
            "uniform vec4 solidColor;\n" +
            "out vec4 color;\n" +
            "void main() {\n" +
            "  color = solidColor;\n" +
            "}\n";

    int shaderProg;
    int vShader;
    int fShader;


    @NotNull private GLCanvas canvas;
    private CLContext context;

    private boolean needTransformAtoms = false;
    private boolean needUpdateDiffraction = false;
    private boolean needUpdateImage = false;

    public AccelDiffration(@NotNull GLCanvas canvas){
        this.canvas = canvas;
    }

    private void calculate(){
        Thread worker = new Thread(() -> {
            if(needTransformAtoms){
                // TODO transform atoms to view position

                needTransformAtoms = false;
                needUpdateDiffraction = true;
            }
            if(needUpdateDiffraction){
                // TODO calculate diffraction image

                needUpdateDiffraction = false;
                needUpdateImage = true;
            }
            if(needUpdateImage){
                // TODO convert float image to RGB
                needUpdateImage = false;
            }
            SwingUtilities.invokeLater(canvas::display);
        });
        worker.setName("Diffraction computer");
        worker.start();
    }

    public void setAtoms(List<Point3f> atoms){
        needTransformAtoms = true;
        calculate();
    }

    public void display(GLAutoDrawable gLDrawable) {
        final GL3 gl = gLDrawable.getGL().getGL3();
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();
        gl.glTranslatef(0.0f, 0.0f, -5.0f);

        gl.glRotatef(rotateT, 1.0f, 0.0f, 0.0f);
        gl.glRotatef(rotateT, 0.0f, 1.0f, 0.0f);
        gl.glRotatef(rotateT, 0.0f, 0.0f, 1.0f);
        gl.glRotatef(rotateT, 0.0f, 1.0f, 0.0f);

        gl.glBegin(GL2.GL_TRIANGLES);

        // Front
        gl.glColor3f(0.0f, 1.0f, 1.0f);
        gl.glVertex3f(0.0f, 1.0f, 0.0f);
        gl.glColor3f(0.0f, 0.0f, 1.0f);
        gl.glVertex3f(-1.0f, -1.0f, 1.0f);
        gl.glColor3f(0.0f, 0.0f, 0.0f);
        gl.glVertex3f(1.0f, -1.0f, 1.0f);

        // Right Side Facing Front
        gl.glColor3f(0.0f, 1.0f, 1.0f);
        gl.glVertex3f(0.0f, 1.0f, 0.0f);
        gl.glColor3f(0.0f, 0.0f, 1.0f);
        gl.glVertex3f(1.0f, -1.0f, 1.0f);
        gl.glColor3f(0.0f, 0.0f, 0.0f);
        gl.glVertex3f(0.0f, -1.0f, -1.0f);

        // Left Side Facing Front
        gl.glColor3f(0.0f, 1.0f, 1.0f);
        gl.glVertex3f(0.0f, 1.0f, 0.0f);
        gl.glColor3f(0.0f, 0.0f, 1.0f);
        gl.glVertex3f(0.0f, -1.0f, -1.0f);
        gl.glColor3f(0.0f, 0.0f, 0.0f);
        gl.glVertex3f(-1.0f, -1.0f, 1.0f);

        // Bottom
        gl.glColor3f(0.0f, 0.0f, 0.0f);
        gl.glVertex3f(-1.0f, -1.0f, 1.0f);
        gl.glColor3f(0.1f, 0.1f, 0.1f);
        gl.glVertex3f(1.0f, -1.0f, 1.0f);
        gl.glColor3f(0.2f, 0.2f, 0.2f);
        gl.glVertex3f(0.0f, -1.0f, -1.0f);

        gl.glEnd();

        rotateT += 0.2f;
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
        checkOpenGLerror(gl, "ShaderStatus.glGetProgramiv");

        if (status.get(0) != GL3.GL_TRUE){
            IntBuffer infologLen = IntBuffer.allocate(1);
            gl.glGetShaderiv(shader, GL3.GL_INFO_LOG_LENGTH, infologLen);
            checkOpenGLerror(gl, "ShaderStatus.glGetProgramiv");
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

    private int programStatus(GL3 gl, int program, int param)
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

    public void init(GLAutoDrawable gLDrawable) {
        final GL3 gl = gLDrawable.getGL().getGL3();
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        gl.glClearDepth(1.0f);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LEQUAL);
        gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT, GL.GL_NICEST);

        vShader = gl.glCreateShader(GL3.GL_VERTEX_SHADER);
        fShader = gl.glCreateShader(GL3.GL_FRAGMENT_SHADER);

        IntBuffer length = IntBuffer.allocate(1);
        length.put(0, vsSource.length());

        gl.glShaderSource(vShader, 1, new String[] {vsSource}, length);
        gl.glCompileShader(vShader);
        shaderStatus(gl, vShader, GL3.GL_COMPILE_STATUS);

        length.put(0, fsSource.length());

        gl.glShaderSource(fShader, 1, new String[] {fsSource}, length);
        gl.glCompileShader(fShader);
        shaderStatus(gl, fShader, GL3.GL_COMPILE_STATUS);

        shaderProg = gl.glCreateProgram();
        gl.glAttachShader(shaderProg, vShader);
        gl.glAttachShader(shaderProg, fShader);

        //Initialising OpenCL
        CLDevice[] devices = CLPlatform.getDefault(CLPlatformFilters.glSharing()).listCLDevices(CLDeviceFilters.glSharing());
        for(CLDevice d : devices){
            System.out.println("Got OpenCL device: " + d.getName());
        }

        if(devices.length == 0){
            throw new AssertionError("Failed to get CL/GL sharing device");
        }

        context = CLGLContext.create(gl.getContext(), devices[0]);
        final CL cl =context.getCL();
//        cl.createImage
    }

    public void reshape(GLAutoDrawable gLDrawable, int x,
                        int y, int width, int height) {
    }

    public void dispose(GLAutoDrawable arg0) {
    }
}
