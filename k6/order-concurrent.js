/**
 * 場景：並發建立訂單
 *
 * 場景一（idempotency）：冪等性驗證
 *   - 1 VU 依序用同一個 X-Idempotency-Key 打 10 次
 *   - 預期：第一次 201，後續皆命中快取回傳相同訂單 ID
 *
 * 場景二（distributed_lock）：分散式鎖驗證
 *   - 50 VU 同時打同一個 userId（不同 idempotency key）
 *   - 預期：部分成功（201），其餘被鎖擋回 409 — 這是正確行為，代表鎖在保護資料
 *   - 驗證重點：服務不崩潰、沒有 500、成功的訂單資料完整
 *
 * 執行：k6 run k6/order-concurrent.js
 */

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import exec from 'k6/execution';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.ORDER_URL || 'http://localhost:8082';

export const options = {
  scenarios: {
    // 場景一：冪等性 — 1 VU 依序打 10 次，同一個 key
    idempotency: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 10,
      maxDuration: '30s',
      tags: { scenario: 'idempotency' },
    },
    // 場景二：分散式鎖 — 50 VU 同時打同一個 userId
    distributed_lock: {
      executor: 'shared-iterations',
      vus: 50,
      iterations: 50,
      maxDuration: '30s',
      startTime: '35s',
      tags: { scenario: 'distributed_lock' },
    },
  },
  thresholds: {
    'http_req_duration{scenario:idempotency}': ['p(95)<500'],
    'http_req_duration{scenario:distributed_lock}': ['p(95)<500'],
    // 冪等場景：全部必須成功（依序打，不會有鎖衝突）
    'http_req_failed{scenario:idempotency}': ['rate<0.01'],
    // 分散式鎖場景：409 是預期行為（鎖衝突），只要沒有 500 就算通過
    // k6 預設把非 2xx 都算 failed，所以這裡放寬至 100%，真正驗證靠 checks
    'http_req_failed{scenario:distributed_lock}': ['rate<=1.0'],
  },
};

// 冪等測試用的 key — 所有 iteration 共用
const IDEMPOTENCY_KEY = uuidv4();
let firstOrderId = null;

export default function () {
  const scenario = exec.scenario.name;

  if (scenario === 'idempotency') {
    group('冪等性測試（同一 key 打 10 次）', () => {
      const res = http.post(
        `${BASE_URL}/internal/v1/orders`,
        JSON.stringify({
          userId: 'idem-test-user',
          productName: '冪等測試商品',
          quantity: 1,
        }),
        {
          headers: {
            'Content-Type': 'application/json',
            'X-Idempotency-Key': IDEMPOTENCY_KEY,
          },
        }
      );

      const body = JSON.parse(res.body);
      const iteration = exec.scenario.iterationInTest;

      if (iteration === 0) {
        // 第一次：應建立訂單
        firstOrderId = body.data?.id;
        check(res, {
          '第一次請求 201 建立成功': (r) => r.status === 201,
          '回應有訂單 id': () => !!firstOrderId,
        });
      } else {
        // 後續：命中冪等快取，回傳相同訂單
        check(res, {
          '重複請求 200 或 201（命中快取）': (r) => r.status === 200 || r.status === 201,
          '回傳相同訂單 id': () => body.data?.id === firstOrderId,
        });
      }
    });
    sleep(0.05);
  }

  if (scenario === 'distributed_lock') {
    group('分散式鎖測試（50 VU 同一 userId）', () => {
      const res = http.post(
        `${BASE_URL}/internal/v1/orders`,
        JSON.stringify({
          userId: 'lock-test-user',
          productName: '分散式鎖測試商品',
          quantity: 1,
        }),
        {
          headers: {
            'Content-Type': 'application/json',
            'X-Idempotency-Key': uuidv4(),
          },
        }
      );

      // 201 = 成功建立；409 ORDER_LOCK_CONFLICT = 鎖正常運作，皆為預期行為
      check(res, {
        '201 成功 或 409 鎖衝突（皆為預期）': (r) => r.status === 201 || r.status === 409,
        '無 500 系統錯誤': (r) => r.status !== 500,
      });

      if (res.status === 201) {
        const body = JSON.parse(res.body);
        check(res, {
          '成功訂單包含完整資料': () =>
            body.success === true && !!body.data?.id && !!body.data?.userId,
        });
      }
    });
  }
}
