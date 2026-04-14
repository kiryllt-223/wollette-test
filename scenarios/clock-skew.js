/**
 * Scenario 5: App Container Clock Skew (OS Time Drift)
 *
 * Goal: prove that if one application node's OS clock drifts ahead, global
 * limit fairness becomes inconsistent because Redis Lua scripts trust client
 * time (`nowMs`) sent by each app node.
 *
 * Three phases:
 *   Phase 1 (0-30s)  — all app containers on normal time, healthy 200/429 mix
 *   Phase 2 (30-90s) — app-2 clock is skewed externally (+120s)
 *   Phase 3 (90-120s)— app-2 clock is restored (subtract 120s)
 *
 * Run:
 *   docker compose up --build -d
 *
 *   # Terminal 1
 *   k6 run scenarios/clock-skew.js
 *
 *   # Terminal 2 — at ~T=30s, skew app-2 clock forward by 120s
 *   docker compose exec app-2 sh -c "date -u && date -u -s '+120 seconds' && date -u"
 *
 *   # Terminal 2 — at ~T=90s, restore app-2 clock
 *   docker compose exec app-2 sh -c "date -u && date -u -s '-120 seconds' && date -u"
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const NODES = [
    { label: 'app1', url: 'http://localhost:8081' },
    { label: 'app2', url: 'http://localhost:8082' }, // skewed container in phase 2
    { label: 'app3', url: 'http://localhost:8083' },
];

const HOT_USER = 'free-user-clock-skew';

// ── Custom metrics ────────────────────────────────────────────────────────────
const allowedRequests  = new Counter('rl_allowed');
const rejectedRequests = new Counter('rl_rejected');
const serverErrors     = new Counter('rl_server_errors');
const serverErrorRate  = new Rate('rl_server_error_rate');
const rateLimitedRate  = new Rate('rl_rate_limited_rate');
const decisionLatency  = new Trend('rl_decision_latency_ms', true);

const app1Allowed  = new Counter('rl_app1_allowed');
const app1Rejected = new Counter('rl_app1_rejected');
const app2Allowed  = new Counter('rl_app2_allowed');
const app2Rejected = new Counter('rl_app2_rejected');
const app3Allowed  = new Counter('rl_app3_allowed');
const app3Rejected = new Counter('rl_app3_rejected');

export const options = {
    stages: [
        { duration: '30s', target: 30 },  // phase 1: baseline
        { duration: '60s', target: 30 },  // phase 2: apply +120s skew to app-2
        { duration: '30s', target: 30 },  // phase 3: remove skew from app-2
        { duration: '10s', target: 0  },  // ramp down
    ],
    thresholds: {
        rl_server_error_rate: ['rate<0.01'], // no 5xx should leak to clients
        rl_rate_limited_rate: ['rate>0.20'], // limits must still fire overall
        http_req_duration:    ['p(95)<250'],
    },
};

function recordPerNode(nodeLabel, status) {
    if (nodeLabel === 'app1') {
        if (status === 200) app1Allowed.add(1);
        if (status === 429) app1Rejected.add(1);
        return;
    }
    if (nodeLabel === 'app2') {
        if (status === 200) app2Allowed.add(1);
        if (status === 429) app2Rejected.add(1);
        return;
    }
    if (status === 200) app3Allowed.add(1);
    if (status === 429) app3Rejected.add(1);
}

export default function () {
    const node = NODES[__VU % NODES.length];

    group('rate_limit_decision', () => {
        const start = Date.now();
        const res = http.post(
            `${node.url}/request`,
            JSON.stringify({ userId: HOT_USER, endpoint: '/payments' }),
            { headers: { 'Content-Type': 'application/json' }, timeout: '2s' }
        );
        decisionLatency.add(Date.now() - start);

        const isTransportError = res.status === 0;
        const isServerError = !isTransportError && res.status >= 500;
        serverErrorRate.add(isServerError ? true : false);

        check(res, {
            'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
            'not a 5xx':            (r) => r.status < 500,
        });

        if (isServerError) {
            serverErrors.add(1);
            return;
        }

        recordPerNode(node.label, res.status);

        if (res.status === 200) {
            allowedRequests.add(1);
            rateLimitedRate.add(false);
        } else if (res.status === 429) {
            rejectedRequests.add(1);
            rateLimitedRate.add(true);
        }
    });

    sleep(0.05);
}

export function teardown() {
    console.log('\n=== CLOCK SKEW CONSISTENCY CHECK ===');
    console.log('If app-2 was skewed during phase 2, rl_app2_allowed should be inflated');
    console.log('and rl_app2_rejected should be lower than app1/app3 under similar load.');

    NODES.forEach((node) => {
        const health = http.get(`${node.url}/actuator/health`, { timeout: '3s' });
        if (health.status === 200) {
            console.log(`${node.url}: health=UP`);
        } else {
            console.warn(`${node.url}: health=${health.status}`);
        }
    });
}
