package com.aleph.graymatter.jtyposquatting.dto;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.Base64;
import java.util.Map;

/**
 * DTO for domain squatting result
 */
public class DomainResultDTO implements Serializable {
    private String domain;
    private String status;
    private String title;
    private String language;
    private String description;
    private int httpCode;
    
    @SerializedName("screenshotBase64")
    private String screenshotBase64;
    
    @SerializedName(value = "screenshot", alternate = "screenshotBase64")
    private transient byte[] screenshot;
    
    private String homepageText;
    private Map<String, String> httpHeaders;

    public DomainResultDTO() {
    }

    public DomainResultDTO(String domain, String status, String title, String language, String description, int httpCode, byte[] screenshot, String homepageText, Map<String, String> httpHeaders) {
        this.domain = domain;
        this.status = status;
        this.title = title;
        this.language = language;
        this.description = description;
        this.httpCode = httpCode;
        this.screenshot = screenshot;
        this.screenshotBase64 = screenshot != null ? Base64.getEncoder().encodeToString(screenshot) : null;
        this.homepageText = homepageText;
        this.httpHeaders = httpHeaders;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public void setHttpCode(int httpCode) {
        this.httpCode = httpCode;
    }

    public byte[] getScreenshot() {
        // Decode from base64 if screenshot is null but screenshotBase64 is set
        if (screenshot == null && screenshotBase64 != null && !screenshotBase64.isEmpty()) {
            try {
                screenshot = Base64.getDecoder().decode(screenshotBase64);
            } catch (IllegalArgumentException e) {
                screenshot = null;
            }
        }
        return screenshot;
    }

    public void setScreenshot(byte[] screenshot) {
        this.screenshot = screenshot;
        this.screenshotBase64 = screenshot != null ? Base64.getEncoder().encodeToString(screenshot) : null;
    }

    public String getScreenshotBase64() {
        return screenshotBase64;
    }

    public void setScreenshotBase64(String screenshotBase64) {
        this.screenshotBase64 = screenshotBase64;
        this.screenshot = screenshotBase64 != null ? Base64.getDecoder().decode(screenshotBase64) : null;
    }

    public String getHomepageText() {
        return homepageText;
    }

    public void setHomepageText(String homepageText) {
        this.homepageText = homepageText;
    }

    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }

    public void setHttpHeaders(Map<String, String> httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    @Override
    public String toString() {
        return "DomainResultDTO{" +
                "domain='" + domain + '\'' +
                ", status='" + status + '\'' +
                ", title='" + title + '\'' +
                ", language='" + language + '\'' +
                ", description='" + description + '\'' +
                ", httpCode=" + httpCode +
                ", screenshotSize=" + (getScreenshot() != null ? getScreenshot().length : 0) +
                '}';
    }
}
