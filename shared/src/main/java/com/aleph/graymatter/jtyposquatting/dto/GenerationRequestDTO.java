package com.aleph.graymatter.jtyposquatting.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Data Transfer Object for domain generation requests.
 * Contains all the parameters needed for generating typo-squatting domains.
 */
public class GenerationRequestDTO implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String baseDomain;
    private List<String> algorithms;
    private boolean includeSubdomains;
    private boolean includeTLDVariations;
    private int maxResults;
    
    public GenerationRequestDTO() {
    }
    
    public GenerationRequestDTO(String baseDomain) {
        this.baseDomain = baseDomain;
        this.includeSubdomains = true;
        this.includeTLDVariations = true;
        this.maxResults = 100;
    }
    
    public String getBaseDomain() {
        return baseDomain;
    }
    
    public void setBaseDomain(String baseDomain) {
        this.baseDomain = baseDomain;
    }
    
    public List<String> getAlgorithms() {
        return algorithms;
    }
    
    public void setAlgorithms(List<String> algorithms) {
        this.algorithms = algorithms;
    }
    
    public boolean isIncludeSubdomains() {
        return includeSubdomains;
    }
    
    public void setIncludeSubdomains(boolean includeSubdomains) {
        this.includeSubdomains = includeSubdomains;
    }
    
    public boolean isIncludeTLDVariations() {
        return includeTLDVariations;
    }
    
    public void setIncludeTLDVariations(boolean includeTLDVariations) {
        this.includeTLDVariations = includeTLDVariations;
    }
    
    public int getMaxResults() {
        return maxResults;
    }
    
    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }
    
    @Override
    public String toString() {
        return "GenerationRequestDTO{" +
                "baseDomain='" + baseDomain + '\'' +
                ", algorithms=" + algorithms +
                ", includeSubdomains=" + includeSubdomains +
                ", includeTLDVariations=" + includeTLDVariations +
                ", maxResults=" + maxResults +
                '}';
    }
}
