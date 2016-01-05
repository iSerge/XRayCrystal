package org.xraycrystal.util;

import org.jetbrains.annotations.NotNull;
import org.xraycrystal.DiffractionViewer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Utils {
    @NotNull
    public static String readResource(String fileName)
    {
        InputStream input = DiffractionViewer.class.getResourceAsStream(fileName);
        if(null == input){
            throw new AssertionError("Can't find resource: " + fileName);
        }
        try(BufferedReader br = new BufferedReader(
                new InputStreamReader(input)))
        {
            StringBuilder sb = new StringBuilder();
            String line;
            while (true)
            {
                line = br.readLine();
                if (line == null)
                {
                    break;
                }
                sb.append(line).append("\n");
            }
            br.close();
            return sb.toString();
        }
        catch (IOException e)
        {
            throw new AssertionError("Can't read resource: " + fileName);
        }
    }

    public static float[] matMul(float[] a, float[] b, int n){
        float[] result = new float[n*n];

        for(int i = 0; i<n; ++i){
            for(int j = 0; j<n; ++j){
                float r = 0.0f;
                for(int k = 0; k<n; ++k){
                    r += a[i*n+k]*b[k*n+j];
                }
                result[i*n+j] = r;
            }
        }

        return result;
    }
}
