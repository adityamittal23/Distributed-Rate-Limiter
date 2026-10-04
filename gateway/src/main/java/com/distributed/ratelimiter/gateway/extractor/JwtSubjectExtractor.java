package com.distributed.ratelimiter.gateway.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Extracts JWT subject (sub) claim from Authorization Bearer token.
 */
public class JwtSubjectExtractor implements KeyExtractor {

    private static final Logger log = LoggerFactory.getLogger(JwtSubjectExtractor.class);
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String extractKey(HttpServletRequest request) {
        String authHeader = request.getHeader(HEADER_AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return "anonymous-jwt";
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        String[] parts = token.split("\\.");
        if (parts.length >= 2) {
            try {
                // Decode JWT payload (part 1)
                byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
                String payloadJson = new String(decodedBytes, StandardCharsets.UTF_8);
                JsonNode jsonNode = objectMapper.readTree(payloadJson);
                if (jsonNode.has("sub")) {
                    return jsonNode.get("sub").asText();
                } else if (jsonNode.has("username")) {
                    return jsonNode.get("username").asText();
                }
            } catch (Exception e) {
                log.debug("Failed to decode JWT payload: {}", e.getMessage());
            }
        }

        // Return token directly or fallback
        return token.isEmpty() ? "anonymous-jwt" : token;
    }
}
