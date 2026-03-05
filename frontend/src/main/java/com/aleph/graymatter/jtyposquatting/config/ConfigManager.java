package com.aleph.graymatter.jtyposquatting.config;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Manages application configuration and internationalization
 */
public class ConfigManager {
    private static final ConfigManager instance = new ConfigManager();
    
    private final ResourceBundle messages;
    private final ResourceBundle config;
    
    private ConfigManager() {
        // Load messages bundle (supports i18n)
        this.messages = ResourceBundle.getBundle("messages", Locale.getDefault());
        
        // Load config bundle
        this.config = ResourceBundle.getBundle("config");
    }
    
    public static ConfigManager getInstance() {
        return instance;
    }
    
    /**
     * Get localized message
     */
    public String getMessage(String key) {
        try {
            return messages.getString(key);
        } catch (Exception e) {
            return "!" + key + "!";
        }
    }
    
    /**
     * Get localized message with format arguments
     */
    public String getMessage(String key, Object... args) {
        try {
            return String.format(messages.getString(key), args);
        } catch (Exception e) {
            return "!" + key + "!";
        }
    }
    
    /**
     * Get configuration value as string
     */
    public String getConfig(String key) {
        try {
            return config.getString(key);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get configuration value as integer
     */
    public int getIntConfig(String key, int defaultValue) {
        try {
            return Integer.parseInt(config.getString(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Get configuration value as long
     */
    public long getLongConfig(String key, long defaultValue) {
        try {
            return Long.parseLong(config.getString(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Get configuration value as boolean
     */
    public boolean getBooleanConfig(String key, boolean defaultValue) {
        try {
            return Boolean.parseBoolean(config.getString(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * Set locale for internationalization
     */
    public void setLocale(Locale locale) {
        ResourceBundle.clearCache();
    }
    
    /**
     * Get current locale
     */
    public Locale getLocale() {
        return Locale.getDefault();
    }
}
