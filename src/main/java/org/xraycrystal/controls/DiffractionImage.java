package org.xraycrystal.controls;

import javax.swing.*;
import javax.vecmath.Point3f;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import org.jetbrains.annotations.NotNull;

public class DiffractionImage extends JPanel {
    @NotNull private BufferedImage img;
    @NotNull private Dimension size;

    public DiffractionImage(@NotNull Dimension size){
        this.size = size;
        img = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics g = img.getGraphics();
        g.setColor(Color.black);
        g.fillRect(0,0, size.width, size.height);
        g.dispose();
    }

    @Override
    public Dimension getPreferredSize() {
        return size;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(img, 0, 0, null);
    }

    public void drawDiffraction(List<Point3f> atoms){
        BufferedImage img  = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);

        int count = atoms.size();

        final float lambda = 0.5e-10f;
        final float R = 1e-7f;
        final float L = 3e-7f;

        final float k = 2.0f*(float)Math.PI/lambda;
        final Point3f K = new Point3f(0.0f, 0.0f, 1.0f);
        K.scale(k);

        float[][] psi = new float[count][2];

        for(int i = 0; i < count; ++i){
            Point3f a = atoms.get(i);
            float phase = K.x*a.x + K.y*a.y + K.z*a.z;
            psi[i][0] = (float)Math.cos(phase);
            psi[i][1] = (float)Math.sin(phase);
        }

        System.out.println("Starting main diffraction loop");

        for(int x = 0; x < size.width; ++x){
            for(int y = 0; y < size.height; ++y){
                float[] I = {0.0f, 0,0f};
                for(int i = 0; i < count; ++i){
                    Point3f a = atoms.get(i);
                    float rx = L*(((float)x)/((float)size.width) - 0.5f) - a.x;
                    float ry = L*(((float)y)/((float)size.height) - 0.5f) - a.y;
                    float rz = R - a.z;
                    float phase = k*(float)Math.sqrt(rx*rx + ry*ry + rz*rz);
                    float cos = (float)Math.cos(phase);
                    float sin = (float)Math.sin(phase);
                    I[0] +=  psi[i][0]*cos - psi[i][1]*sin;
                    I[1] +=  psi[i][1]*cos + psi[i][0]*sin;
                }
                I[0] /= count;
                I[1] /= count;

                float A = I[0]*I[0]+I[1]*I[1];
                A = A > 1.0f ? 1.0f : A;
                int c = (int)(255.0*(1.0 - A));
//                int c = (int)(255.0*A);
                img.setRGB(x, y, 0xff000000 | c<<16 | c<<8 | c);
            }
        }
        System.out.println("Diffraction finished");

        this.img = img;
        repaint();
    }
}
