package com.aleph.graymatter.jtyposquatting.util;

import com.google.gson.JsonObject;
import java.util.StringTokenizer;

public class JSonUtils {

    public static java.util.Map<String, java.util.List<String>> KeysValuesSwapMulti(final JsonObject joIn) {
        java.util.Map<String, java.util.List<String>> mapOut = new java.util.HashMap<>();
        for (String keyIn : joIn.keySet()) {
            String values = joIn.get(keyIn).getAsString();
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
