# Distributed Rate Limiter - System Design Document

## 1. Problem Statement & Objectives
Modern microservice architectures require distributed rate limiting to protect shared backend services against traffic spikes, denial-of-service attempts, abusive API consumers, and noisy neighbors. A centralized rate limiting service must ensure that rate limits are strictly enforced across horizontally scaled gateway replicas while introducing minimal decision latency.

### Primary Objectives:
- **Horizontal Scalability:** Linearly scalable across multiple gateway replicas (5,000+ req/s target).
- **Sub-millisecond Latency:** p99 decision overhead < 20 ms by constraining Redis interaction to a single round-trip atomic Lua script call via `EVALSHA`.
- **Zero Limit Violations:** Atomic updates inside Redis eliminate race conditions inherent to naive `GET`-then-`SET` approaches.
- **Resilience:** Graceful handling of Redis failures via configurable `fail-open` / `fail-closed` policies backed by local in-memory fallback limiters and circuit breakers.
- **Hot-Key Protection:** Local in-memory caching of "blocked until" timestamps prevents abusive keys from overwhelming Redis.

---

## 2. High-Level Architecture

```
                       +-------------------+
                       |    HTTP Clients   |
                       +---------+---------+
                                 |
                                 v
                       +-------------------+
                       |   Nginx (LB / 80) |
                       +----+----+----+----+
                            |    |    |
        +-------------------+    |    +-------------------+
        |                        |                        |
        v                        v                        v
+---------------+        +---------------+        +---------------+
|   Gateway 1   |        |   Gateway 2   |        |   Gateway 3   |
| (Port 8081)   |        | (Port 8082)   |        | (Port 8083)   |
| - Filter      |        | - Filter      |        | - Filter      |
| - Hot Cache   |        | - Hot Cache   |        | - Hot Cache   |
| - CircuitBrkr |        | - CircuitBrkr |        | - CircuitBrkr |
+-------+-------+        +-------+-------+        +-------+-------+
        |                        |                        |
        +------------+-----------+-----------+------------+
                     |                       |
                     v                       v
            +----------------+      +------------------+
            |  Redis 7 (6379)|      | Upstream Backend |
            |  Atomic Lua    |      |   (Port 8080)    |
            +----------------+      +------------------+
                     ^
                     | (Scrapes)
            +----------------+      +------------------+
            |   Prometheus   | ---> | Grafana Dashboard|
            +----------------+      +------------------+
```

---

## 3. Algorithm Selection & Redis Data Models

| Algorithm | Mechanism | Pros | Cons | Redis Data Structure |
|---|---|---|---|---|
| **Token Bucket** | Replenishes tokens over time proportional to elapsed ms. Consumes `cost` tokens on request. | Handles controlled bursts, smooth replenishment, industry standard. | Requires tracking token balance + timestamp. | Hash: `rl:{<id>}:tb:<rule>` (`tokens`, `ts`) |
| **Sliding Window Counter** | Weighted sum: `count = current + prev * (1 - elapsed / window)`. | Memory-efficient (O(1)), highly accurate, zero burst vulnerability. | Approximation (smooth assumption). | Two Keys: `rl:{<id>}:sc:<rule>:<window>` |
| **Sliding Window Log** | Sorted set with timestamps as scores. Drops items `< now - window`. | 100% exact time-slice adherence. | Memory scales linearly O(N) with request limit. | Sorted Set: `rl:{<id>}:sl:<rule>` |
| **Fixed Window** | Simple increment on key bucketed by window start. | Simplest, lowest CPU overhead. | Susceptible to 2x burst across window boundaries. | String integer: `rl:{<id>}:fw:<rule>:<windowStart>` |

### Redis Cluster Slot Tagging
To prevent multi-key cross-slot errors in Redis Cluster mode, keys wrap the client identity in curly braces `{...}`:
`rl:{<identity>}:<algorithm>:<rule_id>`
This guarantees all keys belonging to client `identity` map to the identical hash slot.

---

## 4. Resilience & Graceful Degradation Matrix

```
                      +-----------------------------+
                      | Incoming Rate Limit Request |
                      +--------------+--------------+
                                     |
                                     v
                        [ Check Hot-Key Cache ]
                                     |
                    +----------------+----------------+
                    |                                 |
              [Hit: Blocked]                    [Miss: Proceed]
                    |                                 |
                    v                                 v
          Return HTTP 429 Fast             [ Circuit Breaker Status ]
                                                      |
                                     +----------------+----------------+
                                     |                                 |
                                 [Closed]                         [Open / Fail]
                                     |                                 |
                                     v                                 v
                            [ Call Redis Lua ]              [ Check Failure Policy ]
                                     |                                 |
                        +------------+------------+           +--------+--------+
                        |                         |           |                 |
                    [Allowed]                 [Blocked]   [FAIL_OPEN]     [FAIL_CLOSED]
                        |                         |           |                 |
                        v                         v           v                 v
                Forward Upstream             HTTP 429    Fall Back to     Return 429
                                                         In-Memory Engine (Service Safe)
```

---

## 5. Metrics & Observability Contract
- `rl_allowed_total{rule="...", tier="..."}`: Counter for permitted requests.
- `rl_blocked_total{rule="...", tier="..."}`: Counter for rate-limited (HTTP 429) requests.
- `rl_decision_latency_seconds{algorithm="..."}`: Timer / histogram measuring rate check latency.
- `rl_redis_errors_total{type="..."}`: Counter of Redis communication failures or timeouts.
- `rl_circuit_breaker_state{state="..."}`: Gauge representing Circuit Breaker health (Closed, Open, Half-Open).
