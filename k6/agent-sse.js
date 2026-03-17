/**
 * 場景：Agent SSE 全流程壓測
 *
 * 驗證目標：
 *   - 並發啟動 Agent run，訂閱 SSE 事件，等到 run-completed
 *   - agent-service 必須以 loadtest profile 啟動（不打真實 LLM）
 *
 * 啟動 agent-service：
 *   -Dspring.profiles.active=loadtest
 *
 * 執行：k6 run k6/agent-sse.js
 *
 * 注意：k6 的 http.get() 不原生支援 SSE 長連線，
 *       這裡用 streaming response 讀取，適合功能驗證。
 *       若需要更精確的 SSE 壓測，可改用 k6 browser 或 xk6-sse extension。
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.AGENT_URL || 'http://localhost:8083';

export const options = {
  scenarios: {
    agent_sse_flow: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 10 },   // 爬升到 10 VU
        { duration: '30s', target: 10 },   // 維持 30 秒
        { duration: '10s', target: 0 },    // 收尾
      ],
    },
  },
  thresholds: {
    // init（POST chat）p95 < 200ms（只是建立 run，應該很快）
    'http_req_duration{name:init}': ['p(95)<200'],
    // SSE 首個事件到達 p95 < 2s（MockChatModel 應該幾乎即時）
    'http_req_duration{name:stream}': ['p(95)<2000'],
    'http_req_failed': ['rate<0.01'],
  },
};

export default function () {
  const userId = `loadtest-user-${__VU}`;

  // Step 1：發送聊天請求，取得 runId
  const initRes = http.post(
    `${BASE_URL}/api/v1/agent/chat`,
    JSON.stringify({ message: '查詢我的訂單', model: 'google' }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': userId,
      },
      tags: { name: 'init' },
    }
  );

  const initOk = check(initRes, {
    'POST /chat 回 202': (r) => r.status === 202,
    '回應有 runId': (r) => {
      const body = JSON.parse(r.body);
      return body.success === true && body.data && body.data.runId;
    },
  });

  if (!initOk) {
    console.error(`[VU ${__VU}] init 失敗：${initRes.status} ${initRes.body}`);
    return;
  }

  const runId = JSON.parse(initRes.body).data.runId;

  // Step 2：訂閱 SSE stream，等待 run-completed 或 run-failed 事件
  // k6 用 responseType: 'text' + timeout 讀取 SSE 內容
  const streamRes = http.get(
    `${BASE_URL}/api/v1/agent/sessions/${runId}/stream`,
    {
      headers: { Accept: 'text/event-stream' },
      timeout: '10s',
      tags: { name: 'stream' },
    }
  );

  check(streamRes, {
    'SSE stream 200': (r) => r.status === 200,
    '收到 run-completed 或 run-failed': (r) =>
      r.body.includes('run-completed') || r.body.includes('run-failed'),
  });

  sleep(0.5);
}
