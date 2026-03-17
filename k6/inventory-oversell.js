/**
 * 場景：庫存超賣防護 + 最終一致性驗證
 *
 * 流程：
 *   1. setup()：用 http.batch() 並發建立 100 筆訂單（同一商品，庫存 5）
 *   2. 等待 Saga 補償透過 RabbitMQ 完成（startTime: 8s）
 *   3. verify_consistency：查詢每筆訂單最終狀態
 *   4. 斷言：CREATED ≤ 5，CREATED + CANCELLED = 總訂單數
 *
 * 執行：k6 run k6/inventory-oversell.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const ORDER_URL = __ENV.ORDER_URL || 'http://localhost:8082';
const STOCK_LIMIT = parseInt(__ENV.STOCK_LIMIT || '5');
const ORDER_COUNT = parseInt(__ENV.ORDER_COUNT || '100');
// Saga 補償需要走 RabbitMQ，預留 8 秒；若補償較慢可調高
const SAGA_WAIT_SECONDS = parseInt(__ENV.SAGA_WAIT || '8');

export const options = {
  scenarios: {
    verify_consistency: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 1,
      startTime: `${SAGA_WAIT_SECONDS}s`,
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<1000'],
    'http_req_failed': ['rate<0.01'],
  },
};

// setup() 在所有 scenario 開始前執行，http.batch() 模擬並發建立
export function setup() {
  const requests = Array.from({ length: ORDER_COUNT }, () => ({
    method: 'POST',
    url: `${ORDER_URL}/internal/v1/orders`,
    body: JSON.stringify({
      userId: `oversell-user-${uuidv4()}`,
      productName: '超賣測試商品',
      quantity: 1,
    }),
    params: {
      headers: {
        'Content-Type': 'application/json',
        'X-Idempotency-Key': uuidv4(),
      },
    },
  }));

  console.log(`[setup] 並發送出 ${ORDER_COUNT} 筆訂單請求...`);
  const responses = http.batch(requests);

  const orderIds = responses
    .filter((r) => r.status === 201)
    .map((r) => JSON.parse(r.body).data.id);

  console.log(`[setup] 建立成功：${orderIds.length} 筆，等待 ${SAGA_WAIT_SECONDS}s Saga 補償...`);
  return { orderIds };
}

// 主場景：Saga 補償完成後查詢每筆訂單最終狀態
export default function (data) {
  const { orderIds } = data;
  let created = 0;
  let cancelled = 0;
  let other = 0;

  for (const id of orderIds) {
    const res = http.get(`${ORDER_URL}/internal/v1/orders/${id}`);
    if (res.status !== 200) {
      other++;
      continue;
    }
    const status = JSON.parse(res.body).data?.status;
    if (status === 'CREATED') created++;
    else if (status === 'CANCELLED' || status === 'SAGA_CANCELLED') cancelled++;
    else other++;

    sleep(0.02);
  }

  console.log(`\n=== 最終一致性驗證結果 ===`);
  console.log(`總訂單數：${orderIds.length}`);
  console.log(`CREATED（庫存扣成功）：${created} 筆`);
  console.log(`CANCELLED（Saga 補償）：${cancelled} 筆`);
  console.log(`其他狀態：${other} 筆`);
  console.log(`庫存上限：${STOCK_LIMIT}`);

  check(null, {
    [`實際成功 ≤ 庫存上限 ${STOCK_LIMIT}`]: () => created <= STOCK_LIMIT,
    ['CREATED + CANCELLED = 總訂單數（無漏補償）']: () => created + cancelled === orderIds.length,
    ['無異常狀態']: () => other === 0,
  });
}
