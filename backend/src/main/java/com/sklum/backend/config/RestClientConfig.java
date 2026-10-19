package com.sklum.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * One blocking RestClient (Spring 6.1+) pointed at the mock-backends service. Blocking is fine
 * here — Java 25 virtual threads (enabled in application.yml) give cheap concurrency for the
 * per-request fan-out without reactive code, per the "Concurrency model" decision in CLAUDE.md.
 *
 * Connect/read timeouts are the first line of defence — Resilience4j's circuit breakers (see
 * the client classes) are the second: once a backend is slow or failing often enough, the
 * breaker trips and callers fail fast with a fallback instead of queuing up behind a dying
 * dependency. This is the concrete implementation of the business case's "delivery promise
 * needs a degraded-state fallback" risk.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient mockBackendsClient(@Value("${mocks.base-url}") String baseUrl,
                                          @Value("${mocks.connect-timeout-ms}") long connectTimeoutMs,
                                          @Value("${mocks.read-timeout-ms}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) connectTimeoutMs);
        factory.setReadTimeout((int) readTimeoutMs);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
