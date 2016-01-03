package org.xraycrystal.util;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public class GlUtils {
    /** */
    public static void printShaderInfoLog(GLAutoDrawable drawable, int obj)
    {
        GL3 gl = drawable.getGL().getGL3(); // get the OpenGL 3 graphics context
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
    public static void printProgramInfoLog(GLAutoDrawable drawable, int obj)
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

    public static ByteBuffer clone(float[] data)
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
}
