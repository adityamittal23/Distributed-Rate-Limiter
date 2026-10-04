package com.distributed.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@SpringBootApplication
@RestController
public class DemoBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoBackendApplication.class, args);
    }

    @GetMapping("/api/hello")
    public ResponseEntity<Map<String, Object>> hello(@RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        return ResponseEntity.ok(Map.of(
                "service", "upstream-demo-backend",
                "message", "Hello! Your request was allowed by the rate limiter gateway.",
                "apiKey", apiKey != null ? apiKey : "none",
                "timestamp", System.currentTimeMillis()
        ));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) Map<String, String> body) {
        String username = (body != null && body.containsKey("username")) ? body.get("username") : "anonymous";
        return ResponseEntity.ok(Map.of(
                "status", "authenticated",
                "username", username,
                "message", "Login successful",
                "timestamp", System.currentTimeMillis()
        ));
    }

    @GetMapping("/public/info")
    public ResponseEntity<Map<String, Object>> publicInfo() {
        return ResponseEntity.ok(Map.of(
                "service", "distributed-rate-limiter-demo",
                "status", "healthy",
                "version", "1.0.0"
        ));
    }
}
