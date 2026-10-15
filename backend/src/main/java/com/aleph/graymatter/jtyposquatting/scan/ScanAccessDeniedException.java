package com.aleph.graymatter.jtyposquatting.scan;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ScanAccessDeniedException extends RuntimeException {
    public ScanAccessDeniedException(String scanId) {
        super("Scan belongs to another user: " + scanId);
    }
}
