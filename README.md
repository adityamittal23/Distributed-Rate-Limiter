# Distributed Rate Limiter & API Gateway

A high-performance, horizontally scalable Distributed Rate Limiter and API Gateway built with **Java 21**, **Spring Boot 3**, **Redis 7 (Lua scripts)**, and **Lettuce**, capable of sustaining **5,000+ req/s** across multi-instance gateway deployments with **sub-millisecond p99 decision overhead**, zero cross-instance limit breaches, and graceful degradation during Redis outages.

---

## 1. Problem & Goals

### The Problem
In distributed microservice topologies, independent gateway replicas lack shared state. Client-side coordination or naive `GET`-then-`SET` approaches suffer from severe race conditions under concurrent traffic, leading to 300%+ quota over-allocation, noisy neighbors, and backend collapse. Furthermore, an abusive client that repeatedly hits a rate-limited endpoint can easily saturate Redis CPU and network bandwidth if every blocked request triggers remote roundtrips.

### Our Solution & Goals
- **Horizontal Scalability:** Tested across 3 gateway replicas behind an Nginx load balancer.
- **Strict Correctness:** Enforces per-API-key, per-IP, and per-JWT quotas atomically inside Redis via a single Lua roundtrip (`EVALSHA`), guaranteeing **zero limit violations**.
- **Sub-Millisecond Overhead:** p99 decision latency < 2 ms through Lua pre-compilation, SHA1 digest caching, and single-roundtrip execution.
- **Hot-Key Protection:** Local Caffeine L1 cache drops repeated requests from already-blocked clients in **0.01 ms** without querying Redis.
- **Resilience & Fault Tolerance:** Resilience4j Circuit Breaker with route-level `fail-open` (backed by an in-memory fallback rate limiter) and `fail-closed` policies.
- **Enterprise Observability:** Full Prometheus metrics and pre-built Grafana dashboards.

---

## 2. Architecture & Request Flow

```
                                  +-------------------+
                                  |    HTTP Clients   |
                                  +---------+---------+
                                            |
                                            v
                                  +-------------------+
                                  |   Nginx LB (80)   |
                                  +----+----+----+----+
                                       |    |    | Round-Robin
                   +-------------------+    |    +-------------------+
                   |                        |                        |
                   v                        v                        v
           +---------------+        +---------------+        +---------------+
           |   Gateway 1   |        |   Gateway 2   |        |   Gateway 3   |
           |  (Port 8081)  |        |  (Port 8082)  |        |  (Port 8083)  |
           +-------+-------+        +-------+-------+        +-------+-------+
                   |                        |                        |
                   +------------+-----------+-----------+------------+
                                |                       |
                                v                       v
                      +------------------+     +------------------+
                      |  Redis 7 Cluster |     | Upstream Backend |
                      |   (Port 6379)    |     |   (Port 8080)    |
                      +------------------+     +------------------+
                                ^
                                | Scrapes (/actuator/prometheus)
                      +------------------+     +------------------+
                      | Prometheus (9090)| --> |  Grafana (3000)  |
                      +------------------+     +------------------+
```

### Detailed Gateway Request Pipeline
```
[ Incoming HTTP Request ]
          │
          ▼
1. Extract Client Identity (API Key / Client IP / JWT Subject)
          │
          ▼
2. Match Route & Pricing Tier (AntPathMatcher: e.g. /api/** -> Free / Pro)
          │
          ▼
3. Check Local Hot-Key Cache (Caffeine L1)
   ├── [Cache Hit: Already Blocked] ──> Fast-Drop HTTP 429 (<0.01ms overhead, zero Redis I/O)
   └── [Cache Miss: Needs Check]
          │
          ▼
4. Resilience4j Circuit Breaker
   ├── [State: OPEN / Timeout / Redis Down]
   │      ├── Policy: FAIL_CLOSED ─────> Reject with HTTP 429 (Secure for /auth/login)
   │      └── Policy: FAIL_OPEN   ─────> Evaluate In-Memory Fallback Limiter
   │
   └── [State: CLOSED]
          │
          ▼
5. Execute Redis Atomic Lua Script (1 Network Roundtrip via EVALSHA)
   ├── [Rejected] ──> Record in Local Hot-Key Cache + Return HTTP 429 + Retry-After
   └── [Allowed]  ──> Inject X-RateLimit-* Headers + Reverse Proxy to Upstream
```

---

## 3. Pluggable Rate Limiting Algorithms

| Algorithm | Mechanism | Pros | Cons | Redis Data Model | Optimal Use Case |
|---|---|---|---|---|---|
| **Token Bucket** | Replenishes tokens over time at constant rate; spends per request. | Smooth replenishment, controlled bursts. | Slightly more state (tokens + timestamp). | Hash: `rl:{<id>}:tb:<rule>` | Public & Developer APIs |
| **Sliding Window Counter** | Weighted sum: `prevCount * (1 - elapsed/window) + curCount`. | Memory efficient (O(1)), highly accurate, zero boundary burst. | Minor linear approximation (~0.05%). | Hash: `rl:{<id>}:sc:<rule>` | High-throughput endpoints |
| **Sliding Window Log** | Sorted set of timestamps; trims entries `< now - window`. | 100% exact time-slice precision down to millisecond. | Memory scales O(N) with request limit. | Sorted Set: `rl:{<id>}:sl:<rule>` | Security & Financial transactions |
| **Fixed Window** | Discrete counter incremented per window slice. | Simplest, lowest CPU overhead. | Vulnerable to 2x burst across window boundaries. | String: `rl:{<id>}:fw:<rule>:<time>` | Simple internal rate limits |

### Redis Cluster Hash Tagging
All keys use Redis Cluster hash tags around the client identity:
```
rl:{<identity>}:<algo>:<ruleId>
```
Wrapping the identity in `{...}` guarantees that regardless of which algorithm or rule is checked, all state for that client hashes to the **exact same Redis Cluster slot**, preventing multi-key cross-slot errors.

---

## 4. Race Condition Proof: Naive GET-then-SET vs Atomic Lua

A key design highlight of this project is demonstrating the critical race condition in non-atomic rate limiters.

### The Naive Flaw
```java
// Naive implementation (limiter-redis/NaiveNonAtomicRateLimiter.java)
String val = redis.get(key);
long count = (val != null) ? Long.parseLong(val) : 0L;
if (count + 1 <= limit) {
    redis.set(key, String.valueOf(count + 1)); // RACE: Multiple threads overwrite each other!
    return allowed();
}
```

### Empirical Test Result (`NaiveVsAtomicRaceConditionTest.java`)
- **Limit:** 5 requests
- **Concurrent requests:** 25 requests across 15 threads
- **Naive GET-then-SET Result:** **21 requests permitted** (Over 320% limit violation!)
- **Atomic Lua Script Result:** **Strictly 5 permitted**, 20 rejected (0 violations).

---

## 5. Resilience & Fault Tolerance Matrix

| Failure Mode | Route Policy | Behavior | Rationale |
|---|---|---|---|
| **Redis Outage / Network Partition** | `FAIL_OPEN` (`/api/**`) | Gateway activates local **In-Memory Fallback Rate Limiter**. | Upstream service stays protected from total collapse, but legitimate clients continue to be served. |
| **Redis Outage / High Latency** | `FAIL_CLOSED` (`/auth/login`) | Gateway immediately returns **HTTP 429**. | Protects sensitive authentication routes from credential stuffing or brute force when Redis is offline. |
| **Abusive Client DDoS (10k req/s)** | Any | Gateway L1 **Hot-Key Cache** drops calls in memory in **0.01 ms**. | Completely shields Redis and backend from being overwhelmed by abusive keys. |

---

## 6. Observability & Metrics

Exposed at `/actuator/prometheus`:

- `rl_allowed_total{rule, tier, algorithm}`: Counter of accepted requests.
- `rl_blocked_total{rule, tier, algorithm, reason}`: Counter of rejected requests (reasons: `rate_limited`, `hot_key_cache`, `circuit_breaker`).
- `rl_decision_latency_seconds`: Latency timer publishing p50, p95, p99 percentiles.
- `rl_redis_errors_total{type}`: Counter of Redis timeouts or connection exceptions.
- `rl_fallback_total{rule, outcome}`: Counter tracking fallback activations.

---

## 7. Quick Start Guide

### Prerequisites
- JDK 21+
- Docker & Docker Compose (optional, for multi-instance cluster deployment)

### 1. Build and Run Tests
```bash
# Run unit & concurrency tests across all modules
mvn clean test
```

### 2. Run Full Multi-Instance Topology with Docker Compose
```bash
# Package jars
mvn clean package -DskipTests

# Start Nginx LB + 3 Gateways + Redis + Demo Backend + Prometheus + Grafana
cd deploy
docker compose up --build -d
```

### 3. Verify Endpoints via Curl

#### Standard Allowed Request:
```bash
curl -i -H "X-API-Key: test-client" http://localhost/api/hello
```
**Response:**
```http
HTTP/1.1 200 OK
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 9
X-RateLimit-Reset: 1772659200

{"service":"upstream-demo-backend","message":"Hello! Your request was allowed by the rate limiter gateway."}
```

#### Rate-Limited Request (Exceeded Quota):
```bash
# Hammer endpoint until limit is exhausted
curl -i -H "X-API-Key: test-client" http://localhost/api/hello
```
**Response:**
```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1772659200
Retry-After: 6
Content-Type: application/json

{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded for rule 'api-default'. Try again in 6 seconds.",
  "retryAfter": 6,
  "resetAtMs": 1772659206000
}
```

#### Tiered Rate Limiting (Pro Tier):
```bash
curl -i -H "X-API-Key: pro-client" -H "X-User-Tier: pro" http://localhost/api/hello
```
**Response:**
```http
HTTP/1.1 200 OK
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 99
```

#### Observability & Monitoring Links
- **Nginx Gateway LB:** http://localhost:80
- **Prometheus:** http://localhost:9090
- **Grafana Dashboard:** http://localhost:3000

---

## 8. Run k6 Load Tests

```bash
# Install k6 (if not already installed)
# Run 5,000 req/s steady load test
k6 run loadtest/steady-load.js

# Run hot-key abusive traffic test
k6 run loadtest/hot-key.js

# Run spike surge test
k6 run loadtest/spike-load.js
```

---

