package com.aleph.graymatter.jtyposquatting.abuse;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.github.bucket4j.Bandwidth;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Limits the request rate on the API, per authenticated user or, failing that, per client IP.
 * Buckets are kept in a bounded in-memory cache (per instance).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final int requestsPerMinute;
    private final Counter rejections;
    private final Cache<String, Bucket> buckets = CacheBuilder.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    public RateLimitFilter(int requestsPerMinute, MeterRegistry meters) {
        this.requestsPerMinute = requestsPerMinute;
        this.rejections = meters.counter("jtypo.quota.rejections", "type", "rate");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ConsumptionProbe probe = bucketFor(clientKey(request)).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }
        rejections.increment();
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Too many requests, retry in " + retryAfterSeconds + " s\"}");
    }

    private Bucket bucketFor(String key) {
        try {
            return buckets.get(key, () -> Bucket.builder()
                    .addLimit(Bandwidth.builder()
                            .capacity(requestsPerMinute)
                            .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                            .build())
                    .build());
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    static String clientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "user:" + authentication.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
