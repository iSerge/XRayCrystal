package org.xraycrystal;

import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import org.jmol.adapter.smarter.AtomSetCollection;
import org.jmol.adapter.smarter.AtomSetCollectionReader;
import org.jmol.adapter.smarter.SmarterJmolAdapter;
import org.jmol.api.JmolAdapter;
import org.xraycrystal.controls.DiffractionGLListener;
import org.xraycrystal.controls.StructureGLListener;
import org.xraycrystal.util.Utils;

import javax.swing.*;
import javax.vecmath.Point3f;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.*;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class DiffractionViewer
{
    private float[] atomsTransMat = {
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
    };

    private int oldMouseX = 0;
    private int oldMouseY = 0;

    private GLCanvas diffractionView;
    private GLCanvas structureView;

    private JmolAdapter adapter;

    private JFileChooser fc;
    private String file;

    public DiffractionViewer() {
        SwingUtilities.invokeLater(this::initUI);
    }

    private void initUI() {
        adapter = new SmarterJmolAdapter();

        GLCapabilities config = new GLCapabilities(GLProfile.get(GLProfile.GL4));

        diffractionView = new GLCanvas(config);
        DiffractionGLListener diffractionRenderer = new DiffractionGLListener();
        diffractionView.addGLEventListener(diffractionRenderer);

        JFrame frame = new JFrame("Diffraction viewer");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        GridBagLayout layout = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        frame.setLayout(layout);

        int bufferWidth = 512;
        int bufferHeight = 512;
        diffractionView.setPreferredSize(new Dimension(bufferWidth, bufferHeight));

        config.setSampleBuffers(true);
        config.setNumSamples(4);
        structureView = new GLCanvas(config);
        StructureGLListener structureRenderer = new StructureGLListener();
        structureView.addGLEventListener(structureRenderer);
        structureView.setPreferredSize(new Dimension(256,256));

        JCheckBox phaseCB = new JCheckBox("Show phase");

        phaseCB.setSelected(diffractionRenderer.getPhaseShadind());

        JPanel wlPanel = new JPanel();
        wlPanel.add(new JLabel("Wavelength, \u00c5"));// \u00c5 -> Å
        float lambda = diffractionRenderer.getLambda();
        JTextField wlField = new JTextField(String.valueOf(lambda), 6);
        wlPanel.add(wlField);

        JSlider wlSlider = new JSlider(0, 1000);
        wlSlider.setValue(mkSliderValue(lambda));
        wlSlider.setMajorTickSpacing(333);
        wlSlider.setPaintTicks(true);

        Hashtable<Integer, JLabel> sliderLabels = new Hashtable<>();
        sliderLabels.put(1, new JLabel("0"));
        sliderLabels.put(333, new JLabel("0.1"));
        sliderLabels.put(666, new JLabel("1.0"));
        sliderLabels.put(1000, new JLabel("10"));
        wlSlider.setLabelTable(sliderLabels);
        wlSlider.setPaintLabels(true);

        JPanel cellsNumberPanel = new JPanel();

        cellsNumberPanel.add(new JLabel("Number of unit cells: "));

        JLabel cellsNumberLbl = new JLabel("1");
        cellsNumberPanel.add(cellsNumberLbl);

        JSlider cellsNumberSlider = new JSlider(1, 10);
        cellsNumberSlider.setValue(1);
        cellsNumberSlider.setMajorTickSpacing(5);
        cellsNumberSlider.setMinorTickSpacing(1);

        Hashtable<Integer, JLabel> cellsSliderLabels = new Hashtable<>();
        cellsSliderLabels.put(1, new JLabel("1"));
        cellsSliderLabels.put(5, new JLabel("5"));
        cellsSliderLabels.put(10, new JLabel("10"));
        cellsNumberSlider.setLabelTable(cellsSliderLabels);
        cellsNumberSlider.setPaintLabels(true);

        JButton readFileBtn = new JButton("Open file...");

        float initialExposure = diffractionRenderer.getExposure();
        JLabel exposureLbl = new JLabel(String.format("Exposure: %.2f", initialExposure));

        JSlider exposureSlider = new JSlider(1,1000);
        exposureSlider.setValue(mkSliderValue(initialExposure));
        exposureSlider.setLabelTable(sliderLabels);
        exposureSlider.setPaintLabels(true);
        exposureSlider.setMajorTickSpacing(333);
        exposureSlider.setPaintTicks(true);

        c.insets.set(6,6,3,6);

        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 10;

        frame.add(diffractionView, c);

        c.insets.set(3,6,3,6);

        c.gridx = 0;
        c.gridy = 10;
        c.gridheight = 1;

        frame.add(exposureLbl, c);

        c.gridy = 11;

        c.insets.set(3,6,6,6);

        frame.add(exposureSlider, c);

        c.insets.set(6,6,3,6);

        c.gridx = 1;
        c.gridy = 0;
        c.gridheight = 1;
        frame.add(structureView, c);

        c.insets.set(3,6,3,6);

        c.gridy = 1;
        frame.add(phaseCB, c);

        c.gridy = 2;
        frame.add(wlPanel, c);

        c.gridy = 3;
        frame.add(wlSlider, c);

        c.gridy = 4;
        frame.add(cellsNumberPanel, c);

        c.gridy = 5;
        frame.add(cellsNumberSlider, c);

        c.insets.set(3,6,6,6);

        c.gridy = 6;
        frame.add(readFileBtn, c);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        phaseCB.addItemListener(e -> {
            diffractionRenderer.setPhaseShadind(((JCheckBox)e.getSource()).isSelected());
            diffractionView.display();
        });

        wlField.addActionListener(e -> {
            float l = Float.parseFloat(wlField.getText());
            wlSlider.setValue(mkSliderValue(l));

            diffractionRenderer.setLambda(l);
            diffractionView.display();
        });

        wlSlider.addChangeListener(e ->{
            float l = mkLambda(wlSlider.getValue());
            wlField.setText(String.format("%.5f", l));

            diffractionRenderer.setLambda(l);
            diffractionView.display();
        });

        cellsNumberSlider.addChangeListener(e ->{
            int cellsNumber = cellsNumberSlider.getValue();

            cellsNumberLbl.setText(String.valueOf(cellsNumber));

            if(null != file && !file.isEmpty()){
                AtomSetCollection atoms = loadFile(file, cellsNumber);

                structureRenderer.setAtoms(atoms);
                diffractionRenderer.setAtoms(atoms);

                structureView.display();
                diffractionView.display();
            }
        });

        exposureSlider.addChangeListener( e -> {
            float exposure = mkLambda(exposureSlider.getValue());

            exposureLbl.setText(String.format("Exposure: %.2f", exposure));
            diffractionRenderer.setExposure(exposure);
            diffractionView.display();
        });

        readFileBtn.addActionListener(e -> {
            if(null == fc) {
                fc = new JFileChooser(new File("."));
                fc.setMultiSelectionEnabled(false);
                fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            }

            int retval = fc.showOpenDialog(frame);
            if(JFileChooser.APPROVE_OPTION == retval){
                file = fc.getSelectedFile().getAbsolutePath();
                AtomSetCollection atoms = loadFile(file, cellsNumberSlider.getValue());

                structureRenderer.setAtoms(atoms);
                diffractionRenderer.setAtoms(atoms);

                structureView.display();
                diffractionView.display();
            }

        });

        structureView.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                updateTransMatrix(e);

                diffractionRenderer.setTransformMatrix(atomsTransMat);
                structureRenderer.setTransformMatrix(atomsTransMat);

                structureView.display();
                diffractionView.display();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateMousePos(e);
            }
        });
    }

    private int mkSliderValue(float lambda){
        int sliderValue = 1;
        if(lambda < 0.1f){
            sliderValue = (int)(lambda*3330);
        } else if (0.1f <= lambda && lambda < 1.0f){
            sliderValue = 333 + (int)((lambda-0.1f)*333/0.9f);
        } else if (1.0f <= lambda) {
            sliderValue = 666 + (int)((lambda-1.0f)*334/9);
        }

        return sliderValue;
    }

    private float mkLambda(int sliderValue) {
        float lambda = 0.5f;
        if(sliderValue < 333){
            lambda = sliderValue*0.1f/333;
        } else if (333 <= sliderValue && sliderValue < 666){
            lambda = 0.1f + (sliderValue-333)*0.9f/333;
        } else if (666 <= sliderValue){
            lambda = 1.0f + (float)(sliderValue-666)*9/334;
        }
        return lambda;
    }

    private void updateMousePos(MouseEvent e) {
        oldMouseX = e.getX();
        oldMouseY = e.getY();
    }

    private void updateTransMatrix(MouseEvent e) {
        double w = 0.01;
        float cosX = (float)Math.cos(w*(e.getX() - oldMouseX));
        float sinX = (float)Math.sin(w*(e.getX() - oldMouseX));

        float cosY = (float)Math.cos(w*(e.getY() - oldMouseY));
        float sinY = (float)Math.sin(w*(e.getY() - oldMouseY));

        updateMousePos(e);

        float[] My = {
                cosX, 0, sinX,
                   0, 1,    0,
               -sinX, 0, cosX
        };

        float[] Mx = {
                1,    0,     0,
                0, cosY, -sinY,
                0, sinY,  cosY
        };

        float[] diffMatrix = Utils.matMul( My, Mx, 3);

        atomsTransMat = Utils.matMul(atomsTransMat, diffMatrix, 3);
    }

    public AtomSetCollection loadFile(String name, int cellsNumber){
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(name)));

            Map<String, Object> htParams = new HashMap<>();
            htParams.put("spaceGroupIndex", -1);
            htParams.put("lattice", new Point3f(cellsNumber, cellsNumber, cellsNumber));
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

    public static void main(String[] args)
    {
        GLProfile.initSingleton();

        new DiffractionViewer();
    }

}
