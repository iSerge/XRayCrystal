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
        g.setColor(Color.white);
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
        final double R = 0.01;
        final double L = 0.01;

        final double k = 2.0*Math.PI/lambda;
        final Point3d K = new Point3d(0, 1.0, 0);
        K.scale(k);

        double[] psi = new double[count];

        for(int i = 0; i < count; ++i){
            Point3f a = atoms.get(i);
            psi[i] = Math.acos(Math.cos(K.x*a.x + K.y*a.y + K.z*a.z));
        }

        System.out.println("Starting main diffraction loop");

        for(int x = 0; x < size.width; ++x){
            for(int y = 0; y < size.height; ++y){
                double I = 0.0;
                for(int i = 0; i < count; ++i){
                    Point3f a = atoms.get(i);
                    double rx = ((double) (x-size.width))/(2.0*L) - a.x;
                    double ry = R - a.y;
                    double rz = ((double) (y-size.height))/(2.0*L) - a.z;
                    I += Math.cos(k*Math.sqrt(rx*rx + ry*ry + rz*rz) + psi[i]);
                }
                double A = I/count;
                int c = (int)(255.0*(1.0 - A*A));
                img.setRGB(x, y, 0xff000000 | c<<16 | c<<8 | c);
            }
        }
        System.out.println("Diffraction finished");

        this.img = img;
        repaint();
    }
}
