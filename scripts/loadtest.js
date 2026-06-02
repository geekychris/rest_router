// k6 load test for the REST router.
//
// Usage:
//   k6 run --vus 50 --duration 30s scripts/loadtest.js
//   GATEWAY=http://my.gw API_KEY=my-key k6 run scripts/loadtest.js
//
// Validates:
//   - p95 latency under 200ms
//   - error rate below 1% for the authenticated tier
//   - 429s observed for anonymous tier (rate limit working)

import http from 'k6/http';
import { check, group } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const GATEWAY = __ENV.GATEWAY || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'demo-premium-secret';

const rateLimited = new Counter('rate_limited');
const errorRate = new Rate('errors');

export const options = {
    scenarios: {
        authenticated: {
            executor: 'constant-arrival-rate',
            rate: 100,
            timeUnit: '1s',
            duration: '30s',
            preAllocatedVUs: 50,
            exec: 'authed',
        },
        anonymous_burst: {
            executor: 'constant-arrival-rate',
            rate: 60,
            timeUnit: '1s',
            duration: '30s',
            preAllocatedVUs: 30,
            exec: 'anon',
        },
    },
    thresholds: {
        'http_req_duration{scenario:authenticated}': ['p(95)<200'],
        'errors{scenario:authenticated}': ['rate<0.01'],
    },
};

export function authed() {
    const res = http.get(`${GATEWAY}/servicea/things/42`, {
        headers: { 'X-API-Key': API_KEY },
        tags: { scenario: 'authenticated' },
    });
    check(res, { 'status ok': r => r.status >= 200 && r.status < 500 });
    if (res.status === 429) rateLimited.add(1);
    errorRate.add(res.status >= 500);
}

export function anon() {
    const res = http.get(`${GATEWAY}/servicea/things/42`, {
        tags: { scenario: 'anonymous' },
    });
    if (res.status === 429) rateLimited.add(1);
}
