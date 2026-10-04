package com.distributed.ratelimiter.gateway.extractor;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Extracts API key from request headers or query parameters.
 */
public class ApiKeyExtractor implements KeyExtractor {

    private static final String HEADER_API_KEY = "X-API-Key";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String PARAM_API_KEY = "apiKey";

    @Override
    public String extractKey(HttpServletRequest request) {
        // 1. Check X-API-Key header
        String apiKey = request.getHeader(HEADER_API_KEY);
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }

        // 2. Check Authorization header
        String authHeader = request.getHeader(HEADER_AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("ApiKey ")) {
            return authHeader.substring(7).trim();
        }

        // 3. Fall back to query param
        String param = request.getParameter(PARAM_API_KEY);
        if (param != null && !param.isBlank()) {
            return param.trim();
        }

        return "anonymous";
    }
}
