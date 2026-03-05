package com.aleph.graymatter.jtyposquatting.util;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

public class JSonUtils {

    private static final JSONParser jsonP = new JSONParser();

    /**
     * When keys and values have to be swapped and keys are not needed
     * "key" : "String1, String2",
     * or
     * "key": "Sting1",
     */
    public static JSONObject KeysValuesSwap(final JSONObject joIn) {
        JSONObject joOut;
        joOut = new JSONObject();

        var keyInList = joIn.keySet();
        Iterator iterator = keyInList.iterator();

        String keyIn;
        while (iterator.hasNext()) {
            keyIn = iterator.next().toString();
            String values = (String) joIn.get(keyIn);
            StringTokenizer tokenizer = new StringTokenizer(values, ",");
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken().strip();
                Object put = joOut.put(token, keyIn);
            }
        }

        return joOut;
    }
}
