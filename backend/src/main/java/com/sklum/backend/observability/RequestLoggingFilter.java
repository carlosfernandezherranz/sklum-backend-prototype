package com.sklum.backend.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured JSON request logs — the foundation most ops tooling reads from (Fig. 4's
 * Observability component). Kept as a hand-rolled filter rather than a logging-library
 * JSON encoder, so the exact shape is visible in one place instead of config.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("com.sklum.backend.access");
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long latencyMs = System.currentTimeMillis() - start;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ts", Instant.now().toString());
            entry.put("method", request.getMethod());
            entry.put("path", request.getRequestURI());
            entry.put("status", response.getStatus());
            entry.put("latencyMs", latencyMs);
            try {
                log.info(mapper.writeValueAsString(entry));
            } catch (Exception ignored) {
                // never let logging break the request
            }
        }
    }
}
