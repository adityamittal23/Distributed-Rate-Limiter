import http from 'k6/http';
import { check } from 'k6';

// Single abusive hot-key stress test
export const options = {
  scenarios: {
    hot_key_hammer: {
      executor: 'constant-vus',
      vus: 50,
      duration: '30s',
    },
  },
  thresholds: {
    // Fast-drop local cache overhead should be under 2ms
    http_req_duration: ['p(99)<5', 'p(50)<1'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:80';

export default function () {
  // All 50 concurrent virtual users share the exact same abusive API key!
  const res = http.get(`${BASE_URL}/api/hello`, {
    headers: {
      'X-API-Key': 'abusive-bot-crawler',
      'X-User-Tier': 'free',
    },
  });

  check(res, {
    'handled gracefully': (r) => r.status === 200 || r.status === 429,
  });
}
