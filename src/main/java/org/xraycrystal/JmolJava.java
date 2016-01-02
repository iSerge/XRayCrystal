package org.xraycrystal;

import org.jmol.api.*;
import org.jmol.constant.EnumCallback;
import org.xraycrystal.controls.DiffractionImage;
import org.xraycrystal.controls.JmolPanel;

import javax.swing.*;
import javax.vecmath.Point3f;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.*;

public class JmolJava {
    public static int IMAGE_DIM = 512;

    DiffractionImage image;

    public static void main(String[] args) throws Exception {
        JmolJava jmolJava = new JmolJava();

        SwingUtilities.invokeLater(jmolJava::createPanel);
    }

    private void createPanel(){
        JFrame mainFrame = new JFrame();
        mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JmolPanel jmol = new JmolPanel();

        image = new DiffractionImage(new Dimension(IMAGE_DIM, IMAGE_DIM));

        GridBagLayout layout = new GridBagLayout();
        mainFrame.setLayout(layout);
        GridBagConstraints c = new GridBagConstraints();

        c.insets.set(6,6,6,6);

        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 2;
        mainFrame.add(image, c);

        c.gridx = 1;
        c.gridheight = 1;
        mainFrame.add(jmol,c);

        c.gridy = 1;
        JButton loadBtn = new JButton("Read file...");
        mainFrame.add(loadBtn, c);

        mainFrame.pack();
        mainFrame.setResizable(false);
        mainFrame.setLocationRelativeTo(null);

        loadBtn.addActionListener( a -> {
            JFileChooser fc = new JFileChooser(new File("."));
            fc.setMultiSelectionEnabled(false);
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);

            int retval = fc.showOpenDialog(mainFrame);
            if(JFileChooser.APPROVE_OPTION == retval){
                String file = fc.getSelectedFile().getAbsolutePath();
                jmol.readFile(file);
            }
        });

        jmol.getViewer().setJmolCallbackListener(new JmolCallbackListener() {
            @Override
            public void setCallbackFunction(String callbackType, String callbackFunction) {
            }

            @Override
            public void notifyCallback(EnumCallback message, Object[] data) {
                if(EnumCallback.CLICK == message && data.length > 0 && data[1] instanceof Integer) {
                    if ((Integer) data[4] < 0) {
                        System.out.println("Orientation changed");
                    }
                } else if(EnumCallback.LOADSTRUCT == message){
                    JmolViewer viewer = jmol.getViewer();
                    int atomCount = viewer.getAtomCount();
                    float[] atoms = new float[atomCount*4];
                    for(int i = 0; i < atomCount; ++i){
                        Point3f a = new Point3f(viewer.getAtomPoint3f(i));
                        a.scale(1e-10f);
                        atoms[i*4] = a.x;
                        atoms[i*4+1] = a.y;
                        atoms[i*4+2] = a.z;
                        atoms[i*4+3] = 1.0f;
                    }

                    image.drawDiffraction(atoms);
                } else {
                    System.out.println("CallbackNotify: " + message);
                }
            }

            @Override
            public boolean notifyEnabled(EnumCallback type) {
                return EnumCallback.CLICK == type || EnumCallback.LOADSTRUCT == type;
            }
        });

        mainFrame.setVisible(true);
    }

}
