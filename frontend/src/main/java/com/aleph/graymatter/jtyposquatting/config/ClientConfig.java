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
            // Try to load from classpath
            props.load(ClientConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE));
            apiUrl = props.getProperty("api.url", DEFAULT_API_URL);
        } catch (IOException e) {
            // File not found, use default value
            apiUrl = DEFAULT_API_URL;
        }
    }
    
    public static String getApiUrl() {
        return apiUrl;
    }
    
    public static void setApiUrl(String url) {
        apiUrl = url;
    }
}
