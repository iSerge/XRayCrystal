package org.xraycrystal;

import org.jmol.adapter.smarter.AtomSetCollection;
import org.jmol.adapter.smarter.AtomSetCollectionReader;
import org.jmol.adapter.smarter.SmarterJmolAdapter;
import org.jmol.api.JmolAdapter;
import org.jmol.api.JmolSimpleViewer;
import org.jmol.api.JmolViewer;
import org.jmol.util.Logger;
import org.jmol.viewer.Viewer;
import org.openscience.jmol.app.jmolpanel.JmolPanel;

import javax.swing.*;
import javax.vecmath.Point3f;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
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
            JFileChooser fc = new JFileChooser();
            fc.setMultiSelectionEnabled(false);
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);

            int retval = fc.showOpenDialog(mainFrame);
            if(JFileChooser.APPROVE_OPTION == retval){
                jmol.readFile(fc.getSelectedFile().getAbsolutePath());
            }
        });

        mainFrame.setVisible(true);
        jmol.loadFileFromResource("/Au-Gold.cif");
    }

    static class JmolPanel extends JPanel{
        private static final long serialVersionUID = -3661941083797644242L;
        JmolViewer viewer;
        JmolAdapter adapter;
        JmolPanel() {
            adapter = new SmarterJmolAdapter();
            viewer = Viewer.allocateViewer(this, adapter, null, null, null, null, null, null);
        }

        public JmolSimpleViewer getViewer() {
            return viewer;
        }

        public void executeCmd(String rasmolScript){
            viewer.evalString(rasmolScript);
        }

        public void readFile(String file){
            viewer.evalString("load \"" + file + "\" { 555 555 -1 } ");
        }

        public void loadFileFromResource(String name){
            BufferedReader reader = new BufferedReader(new InputStreamReader(Main.class.getResourceAsStream(name)));

            Map<String, Object> htParams = new HashMap<>();
            htParams.put("spaceGroupIndex", -1);
            htParams.put("lattice", new Point3f(1.0f, 1.0f, 1.0f));
            htParams.put("packed", true);

            AtomSetCollectionReader fileReader = (AtomSetCollectionReader) adapter.getAtomSetCollectionReader(name, null, reader, htParams);

            Object result =  adapter.getAtomSetCollection(fileReader);
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
