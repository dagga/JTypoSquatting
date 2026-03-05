package com.aleph.graymatter.jtyposquatting.ui.renderers;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Manager for flag icons.
 * Generates and caches flag icons from emoji characters to avoid font dependency issues.
 */
public class FlagIconManager {
    
    private static final FlagIconManager INSTANCE = new FlagIconManager();
    private final Map<String, ImageIcon> iconCache = new HashMap<>();
    private final Font emojiFont;
    
    private FlagIconManager() {
        // Try to find a font that supports emoji flags
        Font foundFont = null;
        String[] emojiFontNames = {
            "Segoe UI Emoji",      // Windows
            "Apple Color Emoji",   // macOS
            "Noto Color Emoji",    // Linux with Noto fonts
            "EmojiOne",           // Some Linux systems
            "Twemoji",            // Some systems
            "Symbola",            // Fallback symbol font
            "DejaVu Sans",        // Common Linux font
            "Arial Unicode MS"    // Older Unicode font
        };
        
        for (String fontName : emojiFontNames) {
            Font font = new Font(fontName, Font.PLAIN, 32);
            // Check if font is available
            if (font.getFamily().equalsIgnoreCase(fontName)) {
                foundFont = font;
                break;
            }
        }
        
        // Fallback to default font if no emoji font found
        this.emojiFont = foundFont != null ? foundFont : new Font("Dialog", Font.PLAIN, 32);
        
        // Pre-cache common flags
        preCacheFlags();
    }
    
    public static FlagIconManager getInstance() {
        return INSTANCE;
    }
    
    private void preCacheFlags() {
        // Pre-cache commonly used flags
        getFlagIcon("🇬🇧"); // English
        getFlagIcon("🇫🇷"); // French
        getFlagIcon("🇩🇪"); // German
        getFlagIcon("🇪🇸"); // Spanish
        getFlagIcon("🇮🇹"); // Italian
        getFlagIcon("🇵🇹"); // Portuguese
        getFlagIcon("🇳🇱"); // Dutch
        getFlagIcon("🇷🇺"); // Russian
        getFlagIcon("🇨🇳"); // Chinese
        getFlagIcon("🇯🇵"); // Japanese
        getFlagIcon("🇰🇷"); // Korean
        getFlagIcon("🇸🇦"); // Arabic
        getFlagIcon("🌐");  // Default/Unknown
    }
    
    /**
     * Get or create a flag icon from an emoji character.
     * @param flagEmoji The flag emoji (e.g., "🇬🇧") or language code
     * @return ImageIcon containing the flag
     */
    public ImageIcon getFlagIcon(String flagEmoji) {
        if (flagEmoji == null || flagEmoji.isEmpty()) {
            flagEmoji = "🌐";
        }
        
        // Check cache first
        ImageIcon cached = iconCache.get(flagEmoji);
        if (cached != null) {
            return cached;
        }
        
        // Generate icon from emoji
        ImageIcon icon = createIconFromEmoji(flagEmoji);
        iconCache.put(flagEmoji, icon);
        return icon;
    }
    
    /**
     * Get flag icon for a language name or code.
     * @param language Language name (e.g., "ENGLISH", "FRENCH") or code (e.g., "EN", "FR")
     * @return ImageIcon containing the flag
     */
    public ImageIcon getFlagIconForLanguage(String language) {
        if (language == null || language.isEmpty()) {
            return getFlagIcon("🌐");
        }
        
        String lang = language.toUpperCase().trim();
        String flagEmoji = getFlagEmojiForLanguage(lang);
        return getFlagIcon(flagEmoji);
    }
    
    /**
     * Map language codes/names to flag emojis.
     */
    private String getFlagEmojiForLanguage(String lang) {
        return switch (lang) {
            // European Languages
            case "ENGLISH", "EN" -> "🇬🇧";
            case "FRENCH", "FR" -> "🇫🇷";
            case "GERMAN", "DE" -> "🇩🇪";
            case "SPANISH", "ES" -> "🇪🇸";
            case "ITALIAN", "IT" -> "🇮🇹";
            case "PORTUGUESE", "PT" -> "🇵🇹";
            case "DUTCH", "NL" -> "🇳🇱";
            case "RUSSIAN", "RU" -> "🇷🇺";
            case "POLISH", "PL" -> "🇵🇱";
            case "TURKISH", "TR" -> "🇹🇷";
            case "SWEDISH", "SV" -> "🇸🇪";
            case "NORWEGIAN", "NO", "NORWEGIAN_N", "NORWEGIAN_B" -> "🇳🇴";
            case "DANISH", "DA" -> "🇩🇰";
            case "FINNISH", "FI" -> "🇫🇮";
            case "CZECH", "CS" -> "🇨🇿";
            case "GREEK", "EL" -> "🇬🇷";
            case "ROMANIAN", "RO" -> "🇷🇴";
            case "HUNGARIAN", "HU" -> "🇭🇺";
            case "UKRAINIAN", "UK" -> "🇺🇦";
            case "BULGARIAN", "BG" -> "🇧🇬";
            case "CROATIAN", "HR" -> "🇭🇷";
            case "SERBIAN", "SR" -> "🇷🇸";
            case "SLOVAK", "SK" -> "🇸🇰";
            case "SLOVENIAN", "SL" -> "🇸🇮";
            case "LITHUANIAN", "LT" -> "🇱🇹";
            case "LATVIAN", "LV" -> "🇱🇻";
            case "ESTONIAN", "ET" -> "🇪🇪";
            case "ALBANIAN", "SQ" -> "🇦🇱";
            case "MACEDONIAN", "MK" -> "🇲🇰";
            case "BOSNIAN", "BS" -> "🇧🇦";
            
            // Asian Languages
            case "CHINESE", "ZH" -> "🇨🇳";
            case "JAPANESE", "JA" -> "🇯🇵";
            case "KOREAN", "KO" -> "🇰🇷";
            case "ARABIC", "AR" -> "🇸🇦";
            case "HINDI", "HI" -> "🇮🇳";
            case "THAI", "TH" -> "🇹🇭";
            case "VIETNAMESE", "VI" -> "🇻🇳";
            case "INDONESIAN", "ID" -> "🇮🇩";
            case "MALAY", "MS" -> "🇲🇾";
            case "TAGALOG", "TL" -> "🇵🇭";
            
            // Other Languages
            case "HEBREW", "HE" -> "🇮🇱";
            case "PERSIAN", "FA" -> "🇮🇷";
            case "URDU", "UR" -> "🇵🇰";
            case "BENGALI", "BN" -> "🇧🇩";
            case "TAMIL", "TA" -> "🇮🇳";
            case "TELUGU", "TE" -> "🇮🇳";
            case "MARATHI", "MR" -> "🇮🇳";
            case "GUJARATI", "GU" -> "🇮🇳";
            case "KANNADA", "KN" -> "🇮🇳";
            case "MALAYALAM", "ML" -> "🇮🇳";
            case "PUNJABI", "PA" -> "🇮🇳";
            case "SWAHILI", "SW" -> "🇰🇪";
            case "AFRIKAANS", "AF" -> "🇿🇦";
            case "ZULU", "ZU" -> "🇿🇦";
            case "XIOSA", "XH" -> "🇿🇦";
            
            // Default
            default -> "🌐";
        };
    }
    
    /**
     * Create an ImageIcon from an emoji character by rendering it to a BufferedImage.
     */
    private ImageIcon createIconFromEmoji(String emoji) {
        int size = 32;
        
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        // Enable anti-aliasing for smoother rendering
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        
        // Clear background
        g2d.setColor(new Color(0, 0, 0, 0));
        g2d.fillRect(0, 0, size, size);
        
        // Set font and color
        g2d.setFont(emojiFont);
        g2d.setColor(Color.BLACK);
        
        // Center the emoji
        FontMetrics fm = g2d.getFontMetrics();
        int x = (size - fm.charWidth(emoji.charAt(0))) / 2;
        int y = ((size - fm.getHeight()) / 2) + fm.getAscent();
        
        // Draw the emoji
        g2d.drawString(emoji, x, y);
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
