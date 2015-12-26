package org.xraycrystal.controls;

import org.jmol.adapter.smarter.SmarterJmolAdapter;
import org.jmol.api.JmolAdapter;
import org.jmol.api.JmolViewer;
import org.jmol.viewer.Viewer;

import javax.swing.*;
import java.awt.*;

public class JmolPanel extends JPanel {
    private static final long serialVersionUID = -3661941083797644242L;
    JmolViewer viewer;
    JmolAdapter adapter;

    public JmolPanel() {
        adapter = new SmarterJmolAdapter();
        viewer = Viewer.allocateViewer(this, adapter, null, null, null, null, null, null);
        viewer.setShowMeasurements(false);
    }

    public JmolViewer getViewer() {
        return viewer;
    }

    public void executeCmd(String rasmolScript) {
        viewer.evalString(rasmolScript);
    }

    public void readFile(String file) {
        viewer.evalString("load \"" + file + "\" {1 1 1};");
    }

    @Override
    public Dimension getPreferredSize() {
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
