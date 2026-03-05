package com.aleph.graymatter.jtyposquatting.ui.renderers;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.GraphicsEnvironment;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Manager for flag icons.
 * Generates and caches flag icons as colored labels with language codes.
 * This approach avoids font/emoji dependency issues.
 */
public class FlagIconManager {
    
    private static final FlagIconManager INSTANCE = new FlagIconManager();
    private final Map<String, ImageIcon> iconCache = new HashMap<>();
    private final Map<String, Color> languageColors;
    
    private FlagIconManager() {
        // Initialize language colors for visual distinction
        languageColors = new HashMap<>();
        initLanguageColors();
        
        // Pre-cache common flags
        preCacheFlags();
    }
    
    public static FlagIconManager getInstance() {
        return INSTANCE;
    }
    
    private void initLanguageColors() {
        // Assign distinct colors to common languages
        languageColors.put("ENGLISH", new Color(65, 105, 225));    // Royal Blue
        languageColors.put("FRENCH", new Color(0, 38, 148));       // French Blue
        languageColors.put("GERMAN", new Color(221, 0, 0));        // Red
        languageColors.put("SPANISH", new Color(255, 191, 0));     // Yellow
        languageColors.put("ITALIAN", new Color(0, 140, 68));      // Green
        languageColors.put("PORTUGUESE", new Color(0, 102, 0));    // Dark Green
        languageColors.put("DUTCH", new Color(255, 66, 0));        // Orange
        languageColors.put("RUSSIAN", new Color(0, 57, 166));      // Russian Blue
        languageColors.put("CHINESE", new Color(238, 28, 37));     // Chinese Red
        languageColors.put("JAPANESE", new Color(186, 12, 47));    // Japanese Red
        languageColors.put("KOREAN", new Color(0, 56, 168));       // Korean Blue
        languageColors.put("ARABIC", new Color(0, 122, 51));       // Green
        languageColors.put("UNKNOWN", new Color(128, 128, 128));   // Gray
    }
    
    private void preCacheFlags() {
        // Pre-cache commonly used language icons
        getFlagIconForLanguage("ENGLISH");
        getFlagIconForLanguage("FRENCH");
        getFlagIconForLanguage("GERMAN");
        getFlagIconForLanguage("SPANISH");
        getFlagIconForLanguage("ITALIAN");
        getFlagIconForLanguage("UNKNOWN");
    }
    
    /**
     * Get or create a flag icon from an emoji character.
     * @param flagEmoji The flag emoji (not used in this implementation)
     * @return ImageIcon containing the flag
     */
    public ImageIcon getFlagIcon(String flagEmoji) {
        // For backward compatibility, return default icon
        return getFlagIconForLanguage("UNKNOWN");
    }
    
    /**
     * Get flag icon for a language name or code.
     * Creates a colored rectangle with the language code.
     * @param language Language name (e.g., "ENGLISH", "FRENCH") or code (e.g., "EN", "FR")
     * @return ImageIcon containing the flag
     */
    public ImageIcon getFlagIconForLanguage(String language) {
        if (language == null || language.isEmpty()) {
            language = "UNKNOWN";
        }
        
        String langKey = language.toUpperCase().trim();
        
        // Check cache first
        ImageIcon cached = iconCache.get(langKey);
        if (cached != null) {
            return cached;
        }
        
        // Generate icon
        ImageIcon icon = createLanguageIcon(langKey);
        iconCache.put(langKey, icon);
        return icon;
    }
    
    /**
     * Create an icon showing the language code with a colored background.
     */
    private ImageIcon createLanguageIcon(String language) {
        int width = 45;
        int height = 26;
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        // Enable anti-aliasing
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        
        // Get color for this language
        Color langColor = languageColors.get(language);
        if (langColor == null) {
            langColor = languageColors.get("UNKNOWN");
        }
        
        // Draw rounded rectangle background
        g2d.setColor(langColor);
        g2d.fillRoundRect(2, 2, width - 4, height - 4, 6, 6);
        
        // Draw border
        g2d.setColor(langColor.darker());
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(2, 2, width - 4, height - 4, 6, 6);
        
        // Get language code (first 2 letters)
        String code = language.length() >= 2 ? language.substring(0, 2).toUpperCase() : language;
        
        // Draw language code in white
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 14));
        
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(code);
        int textHeight = fm.getHeight();
        int x = (width - textWidth) / 2;
        int y = (height - textHeight) / 2 + fm.getAscent();
        
        g2d.drawString(code, x, y);
        g2d.dispose();
        
        return new ImageIcon(image);
    }
    
    /**
     * Clear the icon cache (useful for memory management).
     */
    public void clearCache() {
        iconCache.clear();
    }
}
