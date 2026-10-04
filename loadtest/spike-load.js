import http from 'k6/http';
import { check } from 'k6';

// Sudden traffic spike scenario to test burst absorption
export const options = {
  stages: [
    { duration: '10s', target: 500 },   // Warm-up
    { duration: '10s', target: 5000 },  // Immediate spike
    { duration: '30s', target: 5000 },  // Sustained spike
    { duration: '10s', target: 500 },   // Cool-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<25'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:80';

export default function () {
  const userId = Math.floor(Math.random() * 20); // 20 users hitting heavily
  const res = http.get(`${BASE_URL}/api/hello`, {
    headers: {
      'X-API-Key': `spike-client-${userId}`,
      'X-User-Tier': 'free',
    },
  });

  check(res, {
    'valid response status': (r) => r.status === 200 || r.status === 429,
  });
}
