package com.distributed.ratelimiter.gateway.proxy;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.*;

/**
 * Reverse Proxy Controller forwarding allowed requests to the upstream backend service.
 */
@RestController
public class ReverseProxyController {

    private static final Logger log = LoggerFactory.getLogger(ReverseProxyController.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String upstreamUrl;

    public ReverseProxyController(@Value("${gateway.upstream-backend-url:http://localhost:8080}") String upstreamUrl) {
        this.upstreamUrl = upstreamUrl.endsWith("/") ? upstreamUrl.substring(0, upstreamUrl.length() - 1) : upstreamUrl;
    }

    @RequestMapping({"/api/**", "/auth/**", "/public/**"})
    public ResponseEntity<?> proxyRequest(
            HttpServletRequest request,
            RequestEntity<byte[]> requestEntity
    ) throws IOException {
        String path = request.getRequestURI();

        // Prevent proxying internal actuator
        if (path.startsWith("/actuator")) {
            return ResponseEntity.notFound().build();
        }

        String queryString = request.getQueryString();
        String targetUrl = upstreamUrl + path + (queryString != null ? "?" + queryString : "");

        HttpHeaders forwardedHeaders = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!"host".equalsIgnoreCase(name) && !"content-length".equalsIgnoreCase(name)) {
                forwardedHeaders.put(name, Collections.list(request.getHeaders(name)));
            }
        }

        HttpEntity<byte[]> entity = new HttpEntity<>(requestEntity.getBody(), forwardedHeaders);

        try {
            ResponseEntity<byte[]> upstreamResponse = restTemplate.exchange(
                    URI.create(targetUrl),
                    HttpMethod.valueOf(request.getMethod()),
                    entity,
                    byte[].class
            );

            HttpHeaders responseHeaders = new HttpHeaders();
            upstreamResponse.getHeaders().forEach((name, values) -> {
                if (!"transfer-encoding".equalsIgnoreCase(name) && !"connection".equalsIgnoreCase(name)) {
                    responseHeaders.put(name, values);
                }
            });

            return ResponseEntity.status(upstreamResponse.getStatusCode())
                    .headers(responseHeaders)
                    .body(upstreamResponse.getBody());
        } catch (RestClientResponseException ex) {
            HttpHeaders responseHeaders = new HttpHeaders();
            if (ex.getResponseHeaders() != null) {
                ex.getResponseHeaders().forEach((name, values) -> {
                    if (!"transfer-encoding".equalsIgnoreCase(name) && !"connection".equalsIgnoreCase(name)) {
                        responseHeaders.put(name, values);
                    }
                });
            }
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(responseHeaders)
                    .body(ex.getResponseBodyAsByteArray());
        } catch (ResourceAccessException ex) {
            log.warn("Upstream backend at {} is unreachable: {}", targetUrl, ex.getMessage());
            Map<String, Object> error = Map.of(
                    "status", HttpStatus.BAD_GATEWAY.value(),
                    "error", "Bad Gateway",
                    "message", "Upstream backend unreachable: " + upstreamUrl,
                    "timestamp", System.currentTimeMillis()
            );
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
        }
    }
}
