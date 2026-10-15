package com.aleph.graymatter.jtyposquatting.scan;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ScanNotFoundException extends RuntimeException {
    public ScanNotFoundException(String scanId) {
        super("Scan not found: " + scanId);
    }
}
