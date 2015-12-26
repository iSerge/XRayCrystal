package org.xraycrystal;

import org.jmol.adapter.smarter.AtomSetCollection;
import org.jmol.adapter.smarter.AtomSetCollectionReader;
import org.jmol.adapter.smarter.SmarterJmolAdapter;
import org.jmol.api.*;
import org.jmol.constant.EnumCallback;
import org.jmol.viewer.Viewer;

import javax.swing.*;
import javax.vecmath.Point3f;
import java.awt.*;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(Main::createPanel);
    }

    private static void createPanel(){
        JFrame mainFrame = new JFrame();
        mainFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JmolPanel jmol = new JmolPanel();

        GridBagLayout layout = new GridBagLayout();
        mainFrame.setLayout(layout);
        GridBagConstraints c = new GridBagConstraints();

        mainFrame.add(jmol,c);

        JButton loadBtn = new JButton("Read file...");
        mainFrame.add(loadBtn, c);

        mainFrame.pack();
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
                    System.out.println("Got " + viewer.getAtomCount() + " atoms");
                    viewer.getUnscaledTransformMatrix();
                    for(int i = 0; i < Math.min(3,viewer.getAtomCount()); ++i){
                        System.out.print(viewer.getAtomName(i));
                        Point3f a = viewer.getAtomPoint3f(i);
                        System.out.println(String.format("  %f, %f, %f", a.x, a.y, a.x));
                    }
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
        //jmol.loadFileFromResource("/Quartz.cif");
    }

    static class JmolPanel extends JPanel{
        private static final long serialVersionUID = -3661941083797644242L;
        JmolViewer viewer;
        JmolAdapter adapter;
        JmolPanel() {
            adapter = new SmarterJmolAdapter();
            viewer = Viewer.allocateViewer(this, adapter, null, null, null, null, null, null);
            viewer.setShowMeasurements(false);
        }

        public JmolViewer getViewer() {
            return viewer;
        }

        public void executeCmd(String rasmolScript){
            viewer.evalString(rasmolScript);
        }

        public void readFile(String file){
            viewer.evalString("load \"" + file + "\" { 555 555 -1 };");
        }

        public AtomSetCollection loadFile(String name){
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(name)));

                Map<String, Object> htParams = new HashMap<>();
                htParams.put("spaceGroupIndex", -1);
                htParams.put("lattice", new Point3f(1.0f, 1.0f, 1.0f));
                htParams.put("packed", true);

                AtomSetCollectionReader fileReader = (AtomSetCollectionReader) adapter.getAtomSetCollectionReader(name, null, reader, htParams);

                Object result =  adapter.getAtomSetCollection(fileReader);
                if(result instanceof AtomSetCollection){
                    return (AtomSetCollection) result;
                } else if (result instanceof String) {
                    throw new IOError(new Error((String)result));
                } else {
                    throw new AssertionError("Unhandled read result type");
                }
            } catch (FileNotFoundException e){
                throw new IOError(e);
            }
        }

        @Override
        public Dimension getPreferredSize(){
            return new Dimension(250, 200);
        }

        final Dimension currentSize = new Dimension();
        final Rectangle rectClip = new Rectangle();

        public void paint(Graphics g) {
            getSize(currentSize);
            g.getClipBounds(rectClip);
            viewer.renderScreenImage(g, currentSize, rectClip);
        }
    }
}
