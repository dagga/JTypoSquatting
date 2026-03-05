package com.aleph.graymatter.jtyposquatting.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * Data Transfer Object for domain generation requests.
 * Contains all the parameters needed for generating typo-squatting domains.
 */
public class GenerationRequestDTO implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 6117886166997374644L;
    
    private String baseDomain;
    private boolean includeSubdomains;
    private boolean includeTLDVariations;
    private int maxResults;

    @Override
    public String toString() {
        return "GenerationRequestDTO{" +
                "baseDomain='" + baseDomain + '\'' +
                ", includeSubdomains=" + includeSubdomains +
                ", includeTLDVariations=" + includeTLDVariations +
                ", maxResults=" + maxResults +
                '}';
    }
}
