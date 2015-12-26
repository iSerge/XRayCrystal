package org.xraycrystal;

import org.jmol.adapter.smarter.AtomSetCollection;
import org.jmol.adapter.smarter.AtomSetCollectionReader;
import org.jmol.adapter.smarter.SmarterJmolAdapter;
import org.jmol.util.Logger;

import javax.vecmath.Point3f;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        String fileName = "/Quartz.cif";
        BufferedReader reader = new BufferedReader(new InputStreamReader(Main.class.getResourceAsStream(fileName)));
        Map<String, Object> htParams = new HashMap<>();
        htParams.put("spaceGroupIndex", -1);
        htParams.put("lattice", new Point3f(1.0f, 1.0f, 1.0f));

        SmarterJmolAdapter adapter = new SmarterJmolAdapter();
        AtomSetCollectionReader fileReader2 = (AtomSetCollectionReader) adapter.getAtomSetCollectionReader(fileName, null, reader, htParams);

        Object result =  adapter.getAtomSetCollection(fileReader2);
        if(result instanceof String) {
            System.out.println("Error: " + result);
        } else {
            AtomSetCollection atoms = (AtomSetCollection)result;
        }

    }
}
