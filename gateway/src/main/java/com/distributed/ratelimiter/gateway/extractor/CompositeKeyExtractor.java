package com.distributed.ratelimiter.gateway.extractor;

import com.distributed.ratelimiter.gateway.config.KeyType;
import jakarta.servlet.http.HttpServletRequest;

import java.util.EnumMap;
import java.util.Map;

/**
 * Composite key extractor delegating to the appropriate strategy based on KeyType.
 */
public class CompositeKeyExtractor {

    private final Map<KeyType, KeyExtractor> extractors = new EnumMap<>(KeyType.class);

    public CompositeKeyExtractor() {
        extractors.put(KeyType.API_KEY, new ApiKeyExtractor());
        extractors.put(KeyType.IP, new ClientIpExtractor());
        extractors.put(KeyType.JWT, new JwtSubjectExtractor());
    }

    public String extract(HttpServletRequest request, KeyType keyType) {
        KeyExtractor extractor = extractors.getOrDefault(keyType, extractors.get(KeyType.API_KEY));
        return extractor.extractKey(request);
    }
}
