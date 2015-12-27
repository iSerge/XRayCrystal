package org.xraycrystal.controls;

import javax.swing.*;
import javax.vecmath.Point3d;
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

        final double lambda = 1e-10;
        final double R = 0.1;
        final double L = 0.1;

        final double k = 2.0*Math.PI/lambda;
        final Point3d K = new Point3d(0, 1.0, 0);
        K.scale(k);

        double[][] psi = new double[count][2];

        for(int i = 0; i < count; ++i){
            Point3f a = atoms.get(i);
            double phase = K.x*a.x + K.y*a.y + K.z*a.z;
            psi[i][0] = Math.cos(phase);
            psi[i][1] = Math.sin(phase);
        }

        System.out.println("Starting main diffraction loop");

        for(int x = 0; x < size.width; ++x){
            for(int y = 0; y < size.height; ++y){
                double[] I = {0.0, 0,0};
                for(int i = 0; i < count; ++i){
                    Point3f a = atoms.get(i);
                    double rx = ((double) (x-size.width))/(2.0*L) - a.x;
                    double ry = R - a.y;
                    double rz = ((double) (y-size.height))/(2.0*L) - a.z;
                    double phase = k*Math.sqrt(rx*rx + ry*ry + rz*rz);
                    double cos = Math.cos(phase);
                    double sin = Math.sin(phase);
                    I[0] +=  psi[i][0]*cos - psi[i][1]*sin;
                    I[1] +=  psi[i][1]*cos + psi[i][0]*sin;
                }
                I[0] /= count;
                I[1] /= count;

                double A = I[0]*I[0]+I[1]*I[1];
//                int c = (int)(255.0*(1.0 - A));
                int c = (int)(255.0*A);
                img.setRGB(x, y, 0xff000000 | c<<16 | c<<8 | c);
            }
        }
        System.out.println("Diffraction finished");

        this.img = img;
        repaint();
    }
}
