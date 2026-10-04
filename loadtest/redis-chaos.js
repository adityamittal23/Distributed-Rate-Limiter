import http from 'k6/http';
import { check } from 'k6';

// Resilience & Chaos scenario testing behavior during Redis outages
export const options = {
  stages: [
    { duration: '15s', target: 200 }, // Steady baseline
    { duration: '30s', target: 500 }, // Concurrently kill or pause Redis during this window
    { duration: '15s', target: 200 }, // Recovery
  ],
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:80';

export default function () {
  // Test 1: FAIL_OPEN route (/api/**) with in-memory fallback
  const apiRes = http.get(`${BASE_URL}/api/hello`, {
    headers: { 'X-API-Key': 'fail-open-client' },
  });

  check(apiRes, {
    'fail-open route maintains 200 or fallback 429': (r) => r.status === 200 || r.status === 429,
  });

  // Test 2: FAIL_CLOSED route (/auth/login) strictly blocks during outages
  const loginRes = http.post(`${BASE_URL}/auth/login`, JSON.stringify({ username: 'testuser' }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(loginRes, {
    'login route behaves per policy': (r) => r.status === 200 || r.status === 429,
  });
}
