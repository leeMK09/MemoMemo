import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

// ===== 고정 파라미터 =====
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TPS      = Number(__ENV.TPS || 1200);   // 목표 TPS = 1200
const DURATION = 60;                           // 60초 고정
// 러너와 동일한 값으로!
const HOT_QTY  = Number(__ENV.HOT_QTY || 5400);
const COLD_QTY = Number(__ENV.COLD_QTY || 3000);

// VU 풀은 여유있게(환경에 맞게 조절)
const PRE_VUS  = Number(__ENV.PRE_VUS || 1400);
const MAX_VUS  = Number(__ENV.MAX_VUS || 2800);

// HOT: 1..5, COLD: 6..20
const hotIds  = Array.from({ length: 5 }, (_, i) => i + 1);
const coldIds = Array.from({ length: 15 }, (_, i) => i + 6);

// 스케줄 = 재고 총합(정확히 72,000)
const SCHEDULE = new SharedArray('schedule', () => {
  const list = [];
  hotIds.forEach(id  => { for (let i = 0; i < HOT_QTY;  i++) list.push(id); });
  coldIds.forEach(id => { for (let i = 0; i < COLD_QTY; i++) list.push(id); });

  // 순서 섞기(경합 유도; 정합성엔 영향 없음)
  for (let i = list.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [list[i], list[j]] = [list[j], list[i]];
  }
  return list;
});

const TOTAL = SCHEDULE.length;
const EXPECTED = TPS * DURATION;

// 사전 검증: 총합이 안 맞으면 즉시 중단
if (TOTAL !== EXPECTED) {
  throw new Error(`[CONFIG ERROR] TOTAL(${TOTAL}) != TPS(${TPS}) * ${DURATION}s (${EXPECTED}). `
    + `HOT_QTY(5개) + COLD_QTY(15개)의 합이 정확히 ${EXPECTED}이 되게 맞춰주세요.`);
}

export const options = {
  scenarios: {
    tps1200_for_60s: {
      executor: 'constant-arrival-rate',
      rate: TPS,                 // 초당 1200개
      timeUnit: '1s',
      duration: `${DURATION}s`,  // 60초 고정
      preAllocatedVUs: PRE_VUS,
      maxVUs: MAX_VUS,
      exec: 'place',
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],     // 실패율 < 1%
    http_req_duration: ['p(95)<1000'],  // p95 < 1s (필요시 조정)
  },
};

const ok = new Counter('ok');
const fail = new Counter('fail');

export function place() {
  const idx = exec.scenario.iterationInTest;
  if (idx >= TOTAL) return; // schedule 초과 틱 방어

  const productId = SCHEDULE[idx];
  const res = http.post(
    `${BASE_URL}/orders/create`,
    JSON.stringify({ inventoryId: productId, quantity: 1 }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  (res && res.status >= 200 && res.status < 400) ? ok.add(1) : fail.add(1);
}
