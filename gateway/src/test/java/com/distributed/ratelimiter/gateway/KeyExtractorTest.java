package com.distributed.ratelimiter.gateway;

import com.distributed.ratelimiter.gateway.config.KeyType;
import com.distributed.ratelimiter.gateway.extractor.CompositeKeyExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class KeyExtractorTest {

    private final CompositeKeyExtractor extractor = new CompositeKeyExtractor();

    @Test
    @DisplayName("Should extract API key from X-API-Key header")
    void testApiKeyExtraction() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "sk_test_12345");

        String key = extractor.extract(request, KeyType.API_KEY);
        assertThat(key).isEqualTo("sk_test_12345");
    }

    @Test
    @DisplayName("Should extract client IP honoring X-Forwarded-For first entry")
    void testClientIpExtractionWithProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");

        String key = extractor.extract(request, KeyType.IP);
        assertThat(key).isEqualTo("203.0.113.195");
    }

    @Test
    @DisplayName("Should fallback to remote address when no proxy headers exist")
    void testClientIpExtractionRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.50");

        String key = extractor.extract(request, KeyType.IP);
        assertThat(key).isEqualTo("192.168.1.50");
    }

    @Test
    @DisplayName("Should extract subject claim from JWT bearer token")
    void testJwtSubjectExtraction() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // Valid Base64URL encoded payload: {"sub":"user-uuid-999","name":"Alice"}
        // header: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9
        // payload: eyJzdWIiOiJ1c2VyLXV1aWQtOTk5IiwibmFtZSI6IkFsaWNlIn0
        String mockJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLXV1aWQtOTk5IiwibmFtZSI6IkFsaWNlIn0.signature";
        request.addHeader("Authorization", "Bearer " + mockJwt);

        String key = extractor.extract(request, KeyType.JWT);
        assertThat(key).isEqualTo("user-uuid-999");
    }
}
