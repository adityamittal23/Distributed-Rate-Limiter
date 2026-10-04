package com.distributed.ratelimiter.gateway.extractor;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Strategy interface to extract client identity from HTTP requests.
 */
public interface KeyExtractor {

    /**
     * Extracts an identity string representing the client.
     *
     * @param request Incoming HTTP servlet request.
     * @return Unique identity string (e.g. IP address, API key, JWT sub).
     */
    String extractKey(HttpServletRequest request);
}
