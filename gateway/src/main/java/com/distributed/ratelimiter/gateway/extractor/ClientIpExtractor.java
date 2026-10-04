package com.distributed.ratelimiter.gateway.extractor;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Safely extracts client IP address, honoring X-Forwarded-For and proxy headers.
 */
public class ClientIpExtractor implements KeyExtractor {

    private static final String[] IP_HEADER_CANDIDATES = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_CLIENT_IP"
    };

    @Override
    public String extractKey(HttpServletRequest request) {
        for (String header : IP_HEADER_CANDIDATES) {
            String ipList = request.getHeader(header);
            if (ipList != null && !ipList.isBlank() && !"unknown".equalsIgnoreCase(ipList)) {
                // In X-Forwarded-For, the first IP is the original client IP
                String[] ips = ipList.split(",");
                String clientIp = ips[0].trim();
                if (isValidIp(clientIp)) {
                    return clientIp;
                }
            }
        }

        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr.trim() : "127.0.0.1";
    }

    private boolean isValidIp(String ip) {
        return ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip);
    }
}
