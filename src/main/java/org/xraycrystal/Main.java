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
import java.util.ArrayList;
import java.util.List;

public class Main {
    DiffractionImage diffraction;

    public static void main(String[] args) throws Exception {
        Main main = new Main();

        SwingUtilities.invokeLater(main::createPanel);
    }

    private void createPanel(){
        JFrame mainFrame = new JFrame();
        mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JmolPanel jmol = new JmolPanel();
        diffraction = new DiffractionImage(new Dimension(300, 300));

        GridBagLayout layout = new GridBagLayout();
        mainFrame.setLayout(layout);
        GridBagConstraints c = new GridBagConstraints();

        c.insets.set(6,6,6,6);

        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 2;
        mainFrame.add(diffraction, c);

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
                    List<Point3f> atoms = new ArrayList<>(atomCount);
                    for(int i = 0; i < atomCount; ++i){
                        Point3f a = new Point3f(viewer.getAtomPoint3f(i));
                        a.scale(1e-10f);
                        atoms.add(a);
                    }

                    SwingUtilities.invokeLater(() -> {
                        diffraction.drawDiffraction(atoms);
                    });

//                    Thread t = new Thread(() -> {
//                        diffraction.drawDiffraction(atoms);
//                    });
//                    t.setName("Diffraction computer");
//                    t.run();
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
