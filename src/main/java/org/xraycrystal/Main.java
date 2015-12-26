package org.xraycrystal;

import org.jmol.adapter.smarter.AtomSetCollection;
import org.jmol.adapter.smarter.AtomSetCollectionReader;
import org.jmol.adapter.smarter.SmarterJmolAdapter;
import org.jmol.util.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        Logger.debugging = true;

        String fileName = "/Au-Gold.cif";
        String fileName2 = "/Quartz.cif";
        BufferedReader reader = new BufferedReader(new InputStreamReader(Main.class.getResourceAsStream(fileName)));
        BufferedReader reader2 = new BufferedReader(new InputStreamReader(Main.class.getResourceAsStream(fileName2)));
        Map<String, Object> htParams = new HashMap<>();
        htParams.put("spaceGroupIndex", -1);

        SmarterJmolAdapter adapter = new SmarterJmolAdapter();
        AtomSetCollectionReader fileReader = (AtomSetCollectionReader) adapter.getAtomSetCollectionReader(fileName, null, reader, htParams);
        AtomSetCollectionReader fileReader2 = (AtomSetCollectionReader) adapter.getAtomSetCollectionReader(fileName2, null, reader2, htParams);

        Object result =  adapter.getAtomSetCollection(fileReader);
        Object result2 =  adapter.getAtomSetCollection(fileReader2);
        if(result instanceof String) {
            System.out.println("Error: " + result);
        } else {
            AtomSetCollection atoms = (AtomSetCollection)result;
        }

    }
}
