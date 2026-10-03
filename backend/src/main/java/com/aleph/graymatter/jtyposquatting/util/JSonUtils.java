package com.aleph.graymatter.jtyposquatting.util;

import org.json.simple.JSONObject;
import java.util.StringTokenizer;

public class JSonUtils {

    public static java.util.Map<String, java.util.List<String>> KeysValuesSwapMulti(final JSONObject joIn) {
        java.util.Map<String, java.util.List<String>> mapOut = new java.util.HashMap<>();
        for (Object keyObj : joIn.keySet()) {
            String keyIn = keyObj.toString();
            String values = (String) joIn.get(keyIn);
            if (values != null) {
                StringTokenizer tokenizer = new StringTokenizer(values, ",");
                while (tokenizer.hasMoreTokens()) {
                    String token = tokenizer.nextToken().strip();
                    mapOut.computeIfAbsent(token, k -> new java.util.ArrayList<>()).add(keyIn);
                }
            }
        }
        return mapOut;
    }
}
