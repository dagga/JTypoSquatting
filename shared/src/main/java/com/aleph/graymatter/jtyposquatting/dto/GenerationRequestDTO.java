package com.aleph.graymatter.jtyposquatting.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * Request body of {@code POST /api/scans}: the domain whose typo variants are generated and checked.
 */
public class GenerationRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 6117886166997374644L;

    private String baseDomain;
    /** Ignore cached checks and check every domain again */
    private boolean forceRefresh;

    public GenerationRequestDTO() {
    }

    public GenerationRequestDTO(String baseDomain, boolean forceRefresh) {
        this.baseDomain = baseDomain;
        this.forceRefresh = forceRefresh;
    }

    public String getBaseDomain() {
        return baseDomain;
    }

    public void setBaseDomain(String baseDomain) {
        this.baseDomain = baseDomain;
    }

    public boolean isForceRefresh() {
        return forceRefresh;
    }

    public void setForceRefresh(boolean forceRefresh) {
        this.forceRefresh = forceRefresh;
    }

    @Override
    public String toString() {
        return "GenerationRequestDTO{baseDomain='" + baseDomain + "', forceRefresh=" + forceRefresh + '}';
    }
}
