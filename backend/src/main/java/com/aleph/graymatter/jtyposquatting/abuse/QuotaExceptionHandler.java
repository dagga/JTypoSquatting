package com.aleph.graymatter.jtyposquatting.abuse;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.util.Map;

/**
 * Maps quota violations to HTTP 429 with a Retry-After header.
 * The response is written directly so that it also works for SSE requests (Accept: text/event-stream).
 */
@RestControllerAdvice
public class QuotaExceptionHandler {

    private final Gson gson = new Gson();

    @ExceptionHandler(QuotaExceededException.class)
    public void handleQuotaExceeded(QuotaExceededException e, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(Math.max(1, e.getRetryAfter().toSeconds())));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(gson.toJson(Map.of("error", e.getMessage())));
    }
}
