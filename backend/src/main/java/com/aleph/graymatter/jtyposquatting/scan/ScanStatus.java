package com.aleph.graymatter.jtyposquatting.scan;

/**
 * Lifecycle of a scan job: PENDING → RUNNING → COMPLETED | CANCELLED | FAILED.
 */
public enum ScanStatus {
    PENDING, RUNNING, COMPLETED, CANCELLED, FAILED;

    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }
}
