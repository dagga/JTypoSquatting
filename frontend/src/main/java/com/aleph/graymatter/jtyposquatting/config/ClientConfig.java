package com.aleph.graymatter.jtyposquatting.config;

import java.io.IOException;
import java.util.Properties;

/**
 * Client configuration for accessing the REST API
 */
public class ClientConfig {
    private static final String CONFIG_FILE = "client.properties";
    private static final String DEFAULT_API_URL = "http://localhost:8080";
    
    private static String apiUrl;
    
    static {
        loadConfig();
    }
    
    private static void loadConfig() {
        Properties props = new Properties();
        try {
            java.io.InputStream is = ClientConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE);
            if (is != null) {
                try (is) {
                    props.load(is);
                    apiUrl = props.getProperty("api.url", DEFAULT_API_URL);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        apiUrl = DEFAULT_API_URL;
    }
    
    public static String getApiUrl() {
        return apiUrl;
    }
    
    public static void setApiUrl(String url) {
        apiUrl = url;
    }
}
