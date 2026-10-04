import http from 'k6/http';
import { check, sleep } from 'k6';

// 5,000+ req/s multi-instance steady load scenario
export const options = {
  scenarios: {
    steady_traffic: {
      executor: 'ramping-arrival-rate',
      startRate: 500,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 500,
      stages: [
        { duration: '30s', target: 2000 },
        { duration: '1m', target: 5000 },
        { duration: '30s', target: 5000 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    // p99 decision overhead under 20ms target
    http_req_duration: ['p(99)<20', 'p(95)<10', 'p(50)<3'],
    http_req_failed: ['rate<0.01'], // 5xx errors must be under 1%
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:80';

export default function () {
  const userId = Math.floor(Math.random() * 500); // 500 distinct users
  const params = {
    headers: {
      'X-API-Key': `client-key-${userId}`,
      'X-User-Tier': userId % 10 === 0 ? 'pro' : 'free',
      'Content-Type': 'application/json',
    },
  };

  const res = http.get(`${BASE_URL}/api/hello`, params);

  check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    'has rate limit limit header': (r) => r.headers['X-Ratelimit-Limit'] !== undefined,
    'has rate limit remaining header': (r) => r.headers['X-Ratelimit-Remaining'] !== undefined,
    'has rate limit reset header': (r) => r.headers['X-Ratelimit-Reset'] !== undefined,
    '429 response includes Retry-After': (r) => (r.status === 429 ? r.headers['Retry-After'] !== undefined : true),
  });
}
