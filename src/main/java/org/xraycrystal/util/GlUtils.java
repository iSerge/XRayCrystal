package org.xraycrystal.util;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

public class GlUtils {
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

    public static ByteBuffer clone(int[] data)
    {
        int         len    = data.length;
        ByteBuffer  direct = ByteBuffer.allocateDirect(len*4);
        direct.order(ByteOrder.nativeOrder()); // very important!
        for (int i=0; i<len; ++i) {
            direct.putInt(data[i]);
        }
        direct.rewind();
        return direct;
    }

    private static Map<String, Float> elements = new HashMap<>(115);
    static {
        elements.put("H",  0.46f);
        elements.put("He", 1.22f);
        elements.put("Li", 1.57f);
        elements.put("Be", 1.12f);
        elements.put("B",  0.81f);
        elements.put("C",  0.77f);
        elements.put("N",  0.74f);
        elements.put("O",  0.74f);
        elements.put("F",  0.72f);
        elements.put("Ne", 1.60f);
        elements.put("Na", 1.91f);
        elements.put("Mg", 1.60f);
        elements.put("Al", 1.43f);
        elements.put("Si", 1.18f);
        elements.put("P",  1.10f);
        elements.put("S",  1.04f);
        elements.put("Cl", 0.99f);
        elements.put("Ar", 1.92f);
        elements.put("K",  2.35f);
        elements.put("Ca", 1.97f);
        elements.put("Sc", 1.64f);
        elements.put("Ti", 1.47f);
        elements.put("V",  1.35f);
        elements.put("Cr", 1.29f);
        elements.put("Mn", 1.37f);
        elements.put("Fe", 1.26f);
        elements.put("Co", 1.25f);
        elements.put("Ni", 1.25f);
        elements.put("Cu", 1.28f);
        elements.put("Zn", 1.37f);
        elements.put("Ga", 1.53f);
        elements.put("Ge", 1.22f);
        elements.put("As", 1.21f);
        elements.put("Se", 1.04f);
        elements.put("Br", 1.14f);
        elements.put("Au", 1.44f);
    }

    private static Map<String, float[]> colors = new HashMap<>(115);
    static {
        colors.put("H",  new float[] {255f/255f, 204f/255f, 204f/255f});
        colors.put("He", new float[] {252f/255f, 232f/255f, 206f/255f});
        colors.put("Li", new float[] {134f/255f, 224f/255f, 116f/255f});
        colors.put("Be", new float[] { 94f/255f, 215f/255f, 123f/255f});
        colors.put("B",  new float[] { 31f/255f, 162f/255f,  15f/255f});
        colors.put("C",  new float[] {128f/255f,  73f/255f,  41f/255f});
        colors.put("N",  new float[] {176f/255f, 185f/255f, 230f/255f});
        colors.put("O",  new float[] {254f/255f,   3f/255f,   0f/255f});
        colors.put("F",  new float[] {176f/255f, 185f/255f, 230f/255f});
        colors.put("Ne", new float[] {254f/255f,  55f/255f, 181f/255f});
        colors.put("Na", new float[] {249f/255f, 220f/255f,  60f/255f});
        colors.put("Mg", new float[] {251f/255f, 123f/255f,  21f/255f});
        colors.put("Al", new float[] {129f/255f, 178f/255f, 214f/255f});
        colors.put("Si", new float[] { 27f/255f,  59f/255f, 250f/255f});
        colors.put("P",  new float[] {192f/255f, 156f/255f, 194f/255f});
        colors.put("S",  new float[] {255f/255f, 250f/255f,   0f/255f});
        colors.put("Cl", new float[] { 49f/255f, 252f/255f,   2f/255f});
        colors.put("Ar", new float[] {207f/255f, 254f/255f, 196f/255f});
        colors.put("K",  new float[] {161f/255f,  33f/255f, 246f/255f});
        colors.put("Ca", new float[] { 90f/255f, 150f/255f, 189f/255f});
        colors.put("Sc", new float[] {181f/255f,  99f/255f, 171f/255f});
        colors.put("Ti", new float[] {120f/255f, 202f/255f, 255f/255f});
        colors.put("V",  new float[] {229f/255f,  25f/255f,   0f/255f});
        colors.put("Cr", new float[] {  0f/255f,   0f/255f, 158f/255f});
        colors.put("Mn", new float[] {168f/255f,   8f/255f, 158f/255f});
        colors.put("Fe", new float[] {181f/255f, 113f/255f,   0f/255f});
        colors.put("Co", new float[] {  0f/255f,   0f/255f, 175f/255f});
        colors.put("Ni", new float[] {183f/255f, 187f/255f, 189f/255f});
        colors.put("Cu", new float[] { 34f/255f,  71f/255f, 220f/255f});
        colors.put("Zn", new float[] {143f/255f, 143f/255f, 129f/255f});
        colors.put("Ga", new float[] {158f/255f, 227f/255f, 115f/255f});
        colors.put("Ge", new float[] {126f/255f, 110f/255f, 166f/255f});
        colors.put("As", new float[] {116f/255f, 208f/255f,  87f/255f});
        colors.put("Se", new float[] {154f/255f, 239f/255f,  15f/255f});
        colors.put("Br", new float[] {126f/255f,  49f/255f,   2f/255f});
        colors.put("Au", new float[] {254f/255f, 178f/255f,  56f/255f});
    }

    public static float radiusFromElementName(String element){
        if(elements.containsKey(element)){
            return elements.get(element);
        }else {
            return 150f;
        }
    }

    public static float[] colorFromElementName(String element){
        if(colors.containsKey(element)){
            return colors.get(element);
        }else {
            return new float[] {0.8f, 0.8f, 0.8f};
        }
    }
}
