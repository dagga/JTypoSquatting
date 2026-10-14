package com.aleph.graymatter.jtyposquatting.security;

import java.net.UnknownHostException;

/**
 * Thrown when the analysis engine is asked to connect to a forbidden destination.
 * Extends {@link UnknownHostException} so that HTTP clients treat it as a resolution failure.
 */
public class BlockedDestinationException extends UnknownHostException {
    public BlockedDestinationException(String message) {
        super(message);
    }
}
