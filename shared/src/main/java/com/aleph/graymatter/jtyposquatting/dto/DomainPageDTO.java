package com.aleph.graymatter.jtyposquatting.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class DomainPageDTO implements Serializable {
    private String domain;
    private String htmlContent;
    private String textContent;
    private String homepageText; // New field for homepage text extract
    private String title;
    private String metaDescription;
    private String metaKeywords;
    private String metaAuthor;
    private String metaOgTitle;
    private String metaOgDescription;
    private String detectedLanguage;
    private long timestamp;
    private int httpCode;
    private Map<String, String> httpHeaders;
    private byte[] screenshot;

    public DomainPageDTO() {
    }

    public DomainPageDTO(String domain) {
        this.domain = domain;
        this.timestamp = System.currentTimeMillis();
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getHtmlContent() {
        return htmlContent;
    }

    public void setHtmlContent(String htmlContent) {
        this.htmlContent = htmlContent;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    public String getHomepageText() {
        return homepageText;
    }

    public void setHomepageText(String homepageText) {
        this.homepageText = homepageText;
    }

    public String getMetaDescription() {
        return metaDescription;
    }

    public void setMetaDescription(String metaDescription) {
        this.metaDescription = metaDescription;
    }

    public String getMetaKeywords() {
        return metaKeywords;
    }

    public void setMetaKeywords(String metaKeywords) {
        this.metaKeywords = metaKeywords;
    }

    public String getMetaAuthor() {
        return metaAuthor;
    }

    public void setMetaAuthor(String metaAuthor) {
        this.metaAuthor = metaAuthor;
    }

    public String getMetaOgTitle() {
        return metaOgTitle;
    }

    public void setMetaOgTitle(String metaOgTitle) {
        this.metaOgTitle = metaOgTitle;
    }

    public String getMetaOgDescription() {
        return metaOgDescription;
    }

    public void setMetaOgDescription(String metaOgDescription) {
        this.metaOgDescription = metaOgDescription;
    }

    public String getDetectedLanguage() {
        return detectedLanguage;
    }

    public void setDetectedLanguage(String detectedLanguage) {
        this.detectedLanguage = detectedLanguage;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public void setHttpCode(int httpCode) {
        this.httpCode = httpCode;
    }

    public Map<String, String> getHttpHeaders() {
        if (httpHeaders == null) {
            httpHeaders = new HashMap<>();
        }
        return httpHeaders;
    }

    public void setHttpHeaders(Map<String, String> httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    public void addHttpHeader(String key, String value) {
        if (httpHeaders == null) {
            httpHeaders = new HashMap<>();
        }
        httpHeaders.put(key, value);
    }

    public byte[] getScreenshot() {
        return screenshot;
    }

    public void setScreenshot(byte[] screenshot) {
        this.screenshot = screenshot;
    }
}
