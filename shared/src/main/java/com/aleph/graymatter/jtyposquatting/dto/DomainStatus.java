package com.aleph.graymatter.jtyposquatting.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DomainStatus {
    ACTIVE("Active"),
    DEAD("Dead"),
    UNREACHABLE("Unreachable"),
    SUSPICIOUS("Suspicious"),
    SAFE("Safe"),
    TESTING("Testing..."),
    UNKNOWN("Unknown");

    private final String label;

    DomainStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static DomainStatus fromString(String text) {
        if (text == null) return UNKNOWN;
        for (DomainStatus status : DomainStatus.values()) {
            if (status.label.equalsIgnoreCase(text) || status.name().equalsIgnoreCase(text)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
