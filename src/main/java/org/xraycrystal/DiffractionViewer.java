package org.xraycrystal;

import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import org.xraycrystal.controls.DifractionGLListener;
import org.xraycrystal.controls.StructureGLListener;
import org.xraycrystal.util.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Hashtable;

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

    public DiffractionViewer() {

        SwingUtilities.invokeLater(this::initUI);

    }

    private void initUI() {
        GLCapabilities config = new GLCapabilities(GLProfile.get(GLProfile.GL4));

        diffractionView = new GLCanvas(config);
        DifractionGLListener diffractionRenderer = new DifractionGLListener();
        diffractionView.addGLEventListener(diffractionRenderer);

        JFrame frame = new JFrame("DemoViewer");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        GridBagLayout layout = new GridBagLayout();
        GridBagConstraints c = new GridBagConstraints();
        frame.setLayout(layout);

        int bufferWidth = 512;
        int bufferHeight = 512;
        diffractionView.setPreferredSize(new Dimension(bufferWidth, bufferHeight));

        structureView = new GLCanvas(config);
        StructureGLListener structureRenderer = new StructureGLListener();
        structureView.addGLEventListener(structureRenderer);
        structureView.setPreferredSize(new Dimension(256,256));

        JCheckBox phaseCB = new JCheckBox("Show phase");

        phaseCB.setSelected(diffractionRenderer.getPhaseShadind());
        phaseCB.addItemListener(e -> {
            diffractionRenderer.setPhaseShadind(((JCheckBox)e.getSource()).isSelected());
            diffractionView.display();
        });

        JPanel wlPanel = new JPanel();
        wlPanel.add(new JLabel("Wavelength, Å"));
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

        c.insets.set(6,6,6,6);

        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 10;

        frame.add(diffractionView, c);

        c.gridx = 1;
        c.gridy = 0;
        c.gridheight = 1;
        frame.add(structureView, c);

        c.gridy = 1;
        frame.add(phaseCB, c);

        c.gridy = 2;
        frame.add(wlPanel, c);

        c.gridy = 3;
        frame.add(wlSlider, c);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        structureView.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                updateTransMatrix(e);

                diffractionRenderer.setsetTransformMatrix(atomsTransMat);
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

        atomsTransMat = Utils.matMul(diffMatrix, atomsTransMat, 3);
    }

    public static void main(String[] args)
    {
        GLProfile.initSingleton();

        new DiffractionViewer();
    }

}
