## 주제 : 모든 유저에게 알림 전송 아키텍쳐 설계

- 이벤트, 공지사항, 포인트 지급 등의 알림을 모든 유저 대상으로 전송
- 도메인 : B2C 앱테크 (영끌)
- 대표적인 문제 : fan-out
- 각 단계별로 설계
- 현재 운영중인 아키텍쳐 기반하여 설계 진행
  - 적용하기 쉽도록
  - 백엔드 : node.js (express, nest)
  - elasticache(valkey), bullmq
  - 백엔드는 단순한 EC2 로 운영 중
- 점진적인 아키텍쳐 설계를 통해 기존 아키텍쳐를 변경 혹은 추가 인프라 구성 혹은 기술 스택을 변경해도 됨
  - 예시) EC2 를 ECS 로 구성, bullmq 를 kafka 로 구성, node.js 를 멀티 스레드 기반 백엔드 프레임워크(ex. spring) 으로 구성 등

</br>

이 문서는 실제 운영 도입 전제이고, 처음부터 큐 기반 비동기 처리를 전제로 시작합니다.
단순 구조(1·2단계)는 생략하고, 아래 운영 상황을 반드시 포함해서 설계합니다.

1. 발송 중 외부 푸시 사업자 장애가 발생하는 경우
2. 발송 중간에 마케팅 담당자가 캠페인을 취소하는 경우
3. 일부 유저는 성공했고 일부는 실패한 상태에서 재시도해야 하는 경우
4. 전체 트래픽이 급증할 때 워커만 별도로 스케일아웃해야 하는 경우
5. CPU 80% 제한을 지키면서도 처리량을 최대한 높여야 하는 경우

그리고 이 문서는 "기능 설명"이 아니라 **어떤 주체가 무엇을 저장하고, 어떤 이벤트를 발생시키고, 어떤 조건에서 중단하거나 재시도하는지**까지 연결해서 설명합니다.

## 전제: 이 시스템의 핵심 목표를 다시 정의합니다

이 문제를 정확히 정의하면, 단순히 "푸시를 빨리 많이 보내는 시스템"이 아닙니다.

실제로는 다음 시스템입니다.

마케팅 서버가 하나의 캠페인을 생성하면, 알림 서비스는 그 캠페인의 대상 유저를 분해해서 대량 fan-out 작업으로 전환합니다. 그 다음 워커는 외부 푸시 사업자에게 실제 발송을 수행합니다. 이때 시스템은 CPU 80%를 넘기지 않아야 하고, 성공/실패/취소/재시도 상태를 모두 추적할 수 있어야 하며, 중간 취소가 가능해야 하고, 부분 실패 후 재처리가 가능해야 합니다.

즉, 이 시스템의 본질은 아래와 같습니다.

> **캠페인 상태 관리 시스템 + 대량 비동기 작업 처리 시스템 + 외부 연동 장애 흡수 시스템 + 트래킹 시스템**

## MongoDB 운영 전제 (현재 인프라 반영)

**현재 운영**: EC2 3대 × MongoDB replica set (PSS: Primary + Secondary + Secondary). 샤딩 미도입.

이 환경에서 설계의 분산 안전성과 정합성을 지키려면 아래 규칙을 전부 강제해야 합니다.

### Write/Read Concern (필수)

- **모든 워커 write**: `writeConcern: { w: 'majority', j: true }`
  - Replica 다수가 journal 에 반영된 이후에만 ack → fencing 과 claim 의 진실성 보장
- **Claim / lease / retry-scheduler read**: `readPreference: 'primary'` + `readConcern: 'majority'`
  - Secondary 에서 읽으면 lag 으로 stale version 을 볼 수 있음 → fencing 깨짐
- **Progress reconciler / 분석 쿼리**: `readPreference: 'secondaryPreferred'` + `readConcern: 'majority'`
  - 진행률은 약간 뒤처져도 무방, 대신 primary 부하 분산

### 현재 규모 한계 인지

- EC2 3대 replica set 은 **write 부하가 한 노드(primary) 에 집중**. 발송 TPS 가 수천을 넘어가면 primary write IOPS 가 먼저 병목
- 현실적 1차 목표치: **primary 기준 1,000 ~ 2,000 delivery write/s** (인스턴스 타입 / EBS IOPS 에 따라 가변)
- 이 한계를 넘기 시작하면 **샤드 클러스터로 이행 필요** (아래 "샤딩 로드맵" 참조)

### 샤딩 로드맵 (추후 도입)

현재는 샤딩 미도입이지만 미리 결정해둘 것:

| 샤드 키 후보 | 장점 | 단점 | 평가 |
|------------|------|------|------|
| `campaign_id` | 캠페인 쿼리 localized | **hot shard 참사** — 대형 캠페인 write 가 한 shard 로 몰림 | ✗ |
| `user_id` hashed | write 완벽 분산 | 캠페인 단위 집계 scatter-gather | △ |
| **`{ campaign_id: 1, user_id: 'hashed' }`** (compound) | 캠페인 범위 유지 + chunk 내부 write 분산 | 집계는 여전히 scatter-gather (허용) | **✓ 권장** |

도입 트리거:
- primary write TPS 가 지속적으로 인스턴스 한계의 70% 초과
- `notification_delivery` 컬렉션 크기가 단일 노드 working set 을 넘어섬
- 캠페인 1회당 대상자가 500만 이상 규모로 증가

### Change Streams 운영 전제

- Replica set 이므로 사용 가능 ✓
- Consumer 는 `resumeToken` 을 별도 컬렉션에 주기적으로 flush (crash 시 중복 최소화)
- Change Stream 은 **oplog 보유 기간** 에 종속 — oplog window 가 consumer downtime 보다 짧으면 유실. oplog size 충분히 (권장 48h+) 확보

### 트랜잭션 정책

- Multi-document transaction 은 **replica set 에서 동작하나 write latency 2~3배 증가**
- 원칙: **"트랜잭션 없이 설계 가능한 경로를 1순위"**. 대부분의 경우 `findOneAndUpdate` 원자 연산 + Change Streams 로 대체 가능
- 트랜잭션이 꼭 필요한 지점만 국소적으로 사용 (예: outbox 가 불가피한 이종 도메인 연계)

## 앱테크(영끌) 도메인 특화 전제

일반적인 "모든 유저 알림" 설계에서 추가로 얹어야 할 도메인 요구사항입니다.
이 레이어가 없으면 기술 설계가 아무리 좋아도 운영에서 민원·이탈·회계 이슈가 바로 터집니다.

### 1) 포인트 지급과 알림의 분리

포인트 지급은 `exactly once`, 알림은 `at least once (best effort)` 입니다. 요구 레벨 자체가 다릅니다.

- 포인트 지급 트랜잭션과 알림 발송을 **한 트랜잭션에 묶으면 안 됩니다.**
- **MongoDB 환경에서는 Change Streams 를 1순위 로 권장** — 포인트 지급 컬렉션(`user_point`) 의 변경을 watch 하는 별도 consumer 가 알림 캠페인을 생성. outbox 테이블/컬렉션 자체가 불필요해짐.
  - 장점: 추가 write 없음 / resume token 으로 at-least-once / Kafka Connect MongoDB Source 로 직접 Kafka 연동 가능
  - 주의: replica set 필수 (현재 EC2 3대 replica set 으로 충족), consumer 측에서 resume token 영구 저장 필요
- Change Streams 로 대체하기 어려운 케이스(예: 비즈니스 이벤트가 다른 도메인 서비스 API 호출까지 요구) 에서만 **outbox 컬렉션 + multi-document transaction** 사용.
- "알림이 실패해도 포인트 지급은 이미 끝나 있어야 한다"가 원칙입니다.

### 2) 유저 단위 글로벌 Frequency Cap (Reserve → Commit/Release 2단계, **모든 연산 원자**)

`campaign_id:user_id` 단위 idempotency는 **한 캠페인 내부 중복만** 막습니다.
캠페인 A(이벤트), B(포인트 적립), C(출석)가 동시에 돌면 한 유저는 푸시 3개를 동시에 받습니다. 앱테크는 이 순간 이탈률이 급등합니다.

**중요 1**: cap 을 단순 `INCR + EXPIRE` 로 발송 직전에 차감하면 transient failure / 취소 상황에서 cap 만 소모되고 실제 발송은 안 된 상태가 누적됩니다.
**중요 2**: "확정 카운트 + reserve 카운트 합 비교 → reserve 생성" 을 분리해서 처리하면 동시 워커가 모두 통과해 cap 을 초과합니다. **반드시 단일 원자 연산** 으로 처리해야 합니다.

→ **Redis Lua script 기반 reserve / commit / release 3단계 + 모든 cap 연산 원자화** 필수.

**reserved 모델은 aggregate counter 가 아니라 예약 단위 ZSET 사용.** 단일 counter + TTL 구조는 (a) 한 예약의 TTL 만료가 살아있는 다른 예약까지 날리고, (b) 이미 만료된 reserved 에 DECR 치면 음수가 되는 구조적 결함이 있음. ZSET member = `reservation_id`, score = 만료 epoch ms 로 두면 예약별 독립 수명이 보장됩니다.

#### Reserve (Lua, 원자)

```lua
-- KEYS[1] = freq:user:{uid}:hour:{yyyymmddhh}:confirmed   (counter)
-- KEYS[2] = freq:user:{uid}:hour:{yyyymmddhh}:reserved    (ZSET)
-- ARGV[1] = cap              ARGV[2] = now_ms
-- ARGV[3] = reserve_ttl_sec  ARGV[4] = reservation_id
-- ARGV[5] = window_ttl_sec

-- lazy GC: 만료된 예약 제거 (다른 예약은 영향 없음)
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', '(' .. ARGV[2])

local confirmed = tonumber(redis.call('GET', KEYS[1]) or '0')
local reserved  = tonumber(redis.call('ZCARD', KEYS[2]))
if confirmed + reserved >= tonumber(ARGV[1]) then
  return 0   -- DENIED
end
local expire_at = tonumber(ARGV[2]) + tonumber(ARGV[3]) * 1000
redis.call('ZADD', KEYS[2], expire_at, ARGV[4])
redis.call('EXPIRE', KEYS[2], ARGV[5])
redis.call('EXPIRE', KEYS[1], ARGV[5])
return 1     -- GRANTED
```

#### Commit (Lua, provider accepted)

```lua
-- ARGV[1] = reservation_id, ARGV[2] = window_ttl
redis.call('INCR', KEYS[1])
redis.call('EXPIRE', KEYS[1], ARGV[2])
redis.call('ZREM', KEYS[2], ARGV[1])   -- 없으면 no-op (만료된 상태여도 안전)
return 1
```

#### Release (transient fail / 취소 / timeout)

```
ZREM freq:...:reserved  <reservation_id>   -- 없으면 no-op, 음수 없음
```

#### Orphan 회수
워커 크래시로 release 도 못 한 경우 → 해당 `reservation_id` 의 score(만료 epoch) 가 지나면 다음 Reserve 호출의 `ZREMRANGEBYSCORE` 로 **해당 예약만** 청소. 다른 예약은 영향 없음.

`reservation_id` = `{campaign_id}:{user_id}:{attempt_no}` (idempotent 재시도 안전).

#### 흐름 정리

```
[Worker] ──Lua RESERVE──▶ Redis ──denied──▶ delivery=SKIPPED_FREQUENCY_CAP
                            │
                          granted
                            ▼
                       Provider 호출
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
            accept    transient fail   crash
              │             │             │
       Lua COMMIT      RELEASE      (TTL 30s 후
       (confirmed↑      (reserved↓    자동 회수)
        reserved↓)        only)
```

기본값(스터디 합의 전 제안):
- `cap` = 유저당 1시간 최대 3건 (마케팅 팀 협의 필요)
- `reserve TTL` = 30초
- `confirmed TTL` = 1시간 (cap 윈도우와 동일)
- 캠페인 우선순위에 따라 cap 초과 시 **낮은 우선순위는 skip / delayed**

**불변식**: `confirmed_count == 실제 provider accepted 수`. drift = 0.

### 3) 점진적 발송 (Ramp-up)

모든 유저에게 동시 발송 = 앱 동시 접속 폭증 = **자사 API 서버 자체가 먼저 죽습니다.**
앱테크는 특히 "푸시 → 출석 체크 → DB write 폭증" 연쇄가 큽니다.

- 발송 속도를 **token bucket 기반 글로벌 throttler** 로 제한
- 예: "100만 유저를 10분에 걸쳐 분산"처럼 ramp-up 파라미터를 캠페인 설정에 포함

### 4) 예약 발송 · Quiet Hours

- 유저별 알림 수신 시간대 (예: 22:00 ~ 08:00 야간 차단)
- 마케팅 성 캠페인은 quiet hours 에 자동 skip 또는 다음날로 연기
- "밤 11시 포인트 지급 푸시" 민원을 설계 단계에서 원천 차단

### 5) Invalid Token 수집/정리 루프

FCM/APNS 가 돌려주는 invalid token 응답을 **user_device_token 테이블에 역반영**하는 consumer가 반드시 필요합니다.
안 하면 실패율이 계속 누적되고, DLQ 적재량이 자연 증가합니다.

### 6) 채널별 특성 구분

- **Push** : 외부 provider 호출, 실패율·rate limit 존재
- **In-app** : "발송"이 아니라 **유저 조회 시 노출**. DB 쓰기만 하면 끝 (별도 아키텍처)
- **SMS** : 건당 비용 → 중복 발송 = 직접적 회계 손실 → idempotency 는 **엔지니어링 이슈가 아니라 회계 이슈**
- **Email** : 반송(bounce) 관리가 추가로 필요

이 문서의 core flow는 **Push** 기준이고, In-app은 별도 플로우로 분리합니다.

## 먼저 결론부터

이 요구사항이라면 현실적으로는 아래 두 가지 노선 중 하나입니다.

**첫 번째 노선: BullMQ + Redis Cluster 기반**
구현이 비교적 단순하고 Node.js 생태계와 잘 맞습니다. 중간 취소, 재시도, DLQ, 워커 스케일아웃도 충분히 구현 가능합니다. 다만 메시지 스트림 보관, 소비자 독립성, 장기 재처리, 복수 시스템의 독립 소비에는 Kafka보다 약합니다.

**두 번째 노선: Kafka + 별도 Job/State DB 기반**
메시지 영속성, 오프셋 기반 재처리, 다중 소비자, 장기 보관, 추적용 이벤트 스트림 구성에 매우 강합니다. 반면 "개별 job 취소", "즉시 dequeue", "세밀한 job 제어"는 Kafka가 큐처럼 직접 해주는 게 아니므로, 애플리케이션 레벨 상태머신을 반드시 같이 설계해야 합니다.

실제 알림 서비스 도입 전제라면 아래처럼 단계적으로 가는 것을 추천합니다.

- 처음 운영 도입은 BullMQ 기반으로 빠르게 구축
- 마케팅 트래킹, 장기 이벤트 보관, 독립 소비, 데이터 플랫폼 연계가 커지면 Kafka 이벤트 스트림 추가
- 아주 큰 규모가 되면 최종적으로는 **Command는 Queue, Event는 Kafka** 로 분리

즉 **발송 명령 처리 = BullMQ / 내부 Job Queue**, **발송 결과 이벤트 = Kafka** 구조가 가장 현실적입니다.

### Kafka 도입 판단 trigger (구체적 기준)

"분석 요구가 커질 때" 같은 추상적 표현 대신, 팀이 의사결정 할 수 있게 구체화합니다.

- 결과 이벤트를 독립 소비할 주체가 **3개 이상** 생길 때 (분석팀 / CS / 포인트 시스템 / 실시간 대시보드 등)
- 장기 재처리 요구가 **30일 이상** 생길 때
- DAU가 **100만** 을 돌파해 운영 DB 에 분석 쿼리를 더 이상 때릴 수 없을 때
- 위 중 하나라도 해당되면 Kafka 도입 검토, 두 개 이상이면 도입 확정

---

## 0단계: 대상 유저 스냅샷 (Fan-out 의 앞단)

기존 설계가 `chunk로 자른다` 부터 시작하는데, **앱테크에서 "모든 유저 대상" 쿼리 자체가 가장 큰 비용**입니다.
수백만 유저를 대상으로 `SELECT ... WHERE segment=...` 를 OLTP 에 직접 때릴 수 없습니다. fan-out 중에 유저가 탈퇴하거나 세그먼트가 변경되는 문제도 있습니다.

### 해결: 캠페인 생성 시점에 대상 유저 스냅샷을 만듭니다

- `notification_campaign` 레코드를 만들 때 **`campaign_target_snapshot` 테이블**에 대상 `user_id` 를 모두 덤프합니다.
- 대량 insert 는 OLTP 가 아닌 **read replica → 배치 export → snapshot 테이블 적재** 흐름으로 뺍니다. 또는 세그먼트 조건이 복잡하면 데이터 웨어하우스에서 쿼리 후 S3 로 export, 스냅샷 테이블에 import 하는 방식도 가능합니다.
- 이후 chunk 분할은 **스냅샷 테이블을 기준으로만** 자릅니다.
- 캠페인 상태가 `TARGET_BUILDING` → `TARGET_READY` → `RUNNING` 으로 분리됩니다.

### Snapshot Consistency Policy (명시)

스냅샷이 무엇을 고정하고 무엇을 런타임 최신값으로 가져갈지 정책으로 못 박습니다. 이걸 명시 안 하면 사후 "왜 당시 대상자와 실제 수신자가 다르냐"는 질문에 답할 수 없습니다.

| 항목 | 정책 | 이유 |
|-----|-----|-----|
| `user_id` (수신자) | **스냅샷 고정** | "누구에게" 의 진실은 캠페인 생성 시점 |
| `device_token` | **런타임 최신** | invalid token 회피, 토큰 교체 빈도 높음 |
| 언어 / locale | **런타임 최신** | 구 버전 payload 미지원 이슈 회피 |
| 앱 버전 호환성 | **런타임 최신** | payload 스키마 호환성 |
| **수신 거부 설정** | **런타임 최신 (강제)** | 법적 의무. 스냅샷 이후 거부한 유저는 즉시 보호 |
| frequency cap 상태 | **런타임** | 발송 직전 reserve 시점 기준 |
| user 활성 상태 (탈퇴/정지) | **런타임 최신** | 탈퇴 유저 발송 차단 |

원칙 한 줄: **"누구에게" 는 스냅샷, "어떻게/지금 보낼 수 있는가" 는 런타임.**

---

## 3단계: 실무 최소 구조 — Queue 기반 비동기 발송 + 상태 DB + 트래킹

이 단계부터가 실제 설계의 시작점입니다. 시스템은 다음처럼 나뉩니다.

- 마케팅 API 서버는 캠페인 생성 요청을 받습니다.
- 알림 서비스의 API 서버는 캠페인 메타데이터를 저장하고 **0단계의 target snapshot** 을 만듭니다.
- 그 다음 스냅샷을 chunk 단위의 발송 job으로 쪼갭니다.
- 각 chunk는 Queue에 들어갑니다.
- Worker는 Queue에서 chunk를 가져와 유저별로 push 발송을 수행합니다.
- 발송 결과는 DB에 저장됩니다.

핵심은 **캠페인 엔티티와 발송 작업 엔티티를 분리**하는 것입니다.

### 데이터 모델 (MongoDB 컬렉션)

- `notification_campaign` — 캠페인 전체
  - `{ _id(campaign_id), title, body, channel, target_filter, status, priority, rampup_config, quiet_hours_policy, created_by, scheduled_at, expires_at, max_delivery_window_sec, retry_budget_per_user, cancel_requested_at, finished_at, progress: { success, failed, retry, skipped, dropped, expired } }`
- `campaign_target_snapshot` — 캠페인 대상 유저 스냅샷 (0단계 산출물)
  - `{ campaign_id, user_id, created_at }` (unique: `{campaign_id, user_id}`)
- `notification_chunk_job` — fan-out 된 chunk 작업
  - `{ _id(chunk_job_id), campaign_id, chunk_index, status, retry_count, lease_owner, lease_expires_at, lease_version, next_retry_at, last_error, created_at, updated_at }`
  - **`lease_version`** = fencing token (4단계 chunk lease fencing 참조)
  - **`next_retry_at`** = chunk 단위 재시도 시각 (chunk 전체 실패로 RETRY_SCHEDULED 된 경우에만 사용. 유저 단위 재시도는 delivery.next_retry_at)
- `notification_delivery` — 유저 단위 발송 결과 (생성 시점은 4단계 claim 패턴 참조)
  - `{ campaign_id, user_id, provider_message_id, status, attempt_no, fail_reason, sent_at, delivered_at, opened_at, next_retry_at, claim_expires_at }`
  - `claim_expires_at` = PROCESSING 단계의 heartbeat (processing-recovery 용)
- `notification_delivery_attempt` — 시도 이력 (4단계에서 추가, Time-series Collection 후보)
  - `{ campaign_id, user_id, attempt_no, status, provider_response, next_retry_at, ts }`

`notification_delivery.status` 값 (FAILED 는 **terminal**) :
`PENDING / PROCESSING / SUCCESS / FAILED / RETRY_SCHEDULED / CANCELLED / SKIPPED_FREQUENCY_CAP / SKIPPED_QUIET_HOURS / SKIPPED_INVALID_TOKEN / SKIPPED_OPT_OUT / DROPPED_OUTAGE / EXPIRED`

### 필수 인덱스 (MongoDB)

```javascript
// delivery
db.notification_delivery.createIndex({ campaign_id: 1, user_id: 1 }, { unique: true });
db.notification_delivery.createIndex({ campaign_id: 1, status: 1 });          // progress aggregate
db.notification_delivery.createIndex({ status: 1, next_retry_at: 1 });         // retry-scheduler
// chunk_job
db.notification_chunk_job.createIndex({ campaign_id: 1, status: 1 });
db.notification_chunk_job.createIndex({ status: 1, lease_expires_at: 1 });     // lease-recovery
// campaign
db.notification_campaign.createIndex({ status: 1, expires_at: 1 });            // expiry-sweeper
// target snapshot
db.campaign_target_snapshot.createIndex({ campaign_id: 1, user_id: 1 }, { unique: true });
db.campaign_target_snapshot.createIndex({ campaign_id: 1, _id: 1 });           // chunk scan
```

이 구조를 쓰는 이유는 **전체 캠페인과 실제 발송 단위를 분리해야 중간 취소, 부분 재시도, chunk 단위 실패 복구, 진행률 집계가 가능해지기** 때문입니다.

### Chunk job 단위 설계 주의점 (BullMQ 함정)

"chunk 500명 = BullMQ job 1개" 로 잡으면 한 job 처리 시간이 길어져 **BullMQ stalled job** 에 걸릴 수 있습니다 (default lock duration 30s).

선택지:
- (a) chunk job 은 "fan-out trigger" 만 하고 **실제 유저 단위 job 으로 다시 잘라 재투입** → 작은 job 이 많아짐, Redis 부하 증가
- (b) lock duration 연장 + `extendLock` 주기적 호출 + 워커가 chunk 내부에서 **small batch (예: 50명)** 단위로 진행 + 진행 상황을 DB 에 저장

실무 추천은 **(b)** 입니다. chunk 하나 = job 하나로 유지하되 내부적으로 50명씩 batch 로 쪼개 진행하고 매 batch 마다 lock extend + 취소 체크 + progress update 를 수행합니다.

### 처리량 감각

10만 유저, chunk 500명 기준 → chunk job 약 200개.
워커 10개 × 내부 chunk 동시 처리 5 × 발송 동시성 20 → 이론상 동시성 1000.
외부 API 왕복 200ms 가정 시 이론 최대 약 5000 TPS.

하지만 JSON 직렬화 / TLS / 재시도 / DB write / logging / metric 까지 들어가면 실제로는 훨씬 낮습니다. 또한 **DB connection pool, Redis connection 이 먼저 고갈되는 경우가 많습니다.**
실무 기준 이 단계 첫 목표치는 **1,000 ~ 2,000 delivery TPS** 정도가 안전합니다.

### Progress 조회 (정합성 규칙 포함)

운영자가 "지금 몇 %?" 를 실시간으로 봐야 합니다. 매번 `COUNT(*)` 를 때리면 DB 가 녹습니다.

**규칙**:

1. **Counter 는 상태 전이 시에만 증가** — 단순 INCR 가 아니라 "유저 delivery 상태가 X → SUCCESS 로 전이될 때만 success_count += 1"
   - MongoDB `updateOne` 이 `matchedCount === 1` 을 반환했을 때만 Redis INCR
   - 재처리로 같은 유저를 다시 처리해도 이미 SUCCESS 면 filter 매칭 실패 → matchedCount 0 → counter 증가 없음
   - **Drift 원천 차단**
2. **Counter 종류** — `pending / processing / success / failed / retry / skipped / dropped / expired`
3. **Reconciliation** — 5분 주기 background job 이 aggregation 으로 DB 기준 값 재계산 후 Redis 덮어쓰기
   ```javascript
   db.notification_delivery.aggregate([
     { $match: { campaign_id } },
     { $group: { _id: '$status', count: { $sum: 1 } } }
   ], { readPreference: 'secondaryPreferred', readConcern: { level: 'majority' } });
   ```
4. **최종 정산** — 캠페인 `COMPLETED / CANCELLED / EXPIRED` 전이 시 마지막 reconciliation 을 1회 실행하고 그 값을 `notification_campaign.progress` subdocument 에 기록
5. **UI 표시** — 진행 중에는 Redis(실시간 근사값), 완료 후에는 campaign.progress (DB final 값). UI 에 "실시간 / 정산" 을 명시

### 이 단계의 트레이드오프

- Queue 메시지는 **"작업 상태의 진실"이 아닙니다.** 진실은 DB. Redis/Kafka 메시지는 "처리 신호"일 뿐.
- 워커가 작업을 가져간 뒤 죽으면 처리 범위가 모호해질 수 있습니다. → **lease 기반 + lease_version fencing + 유저 단위 idempotency** 를 4단계에서 정식 도입.

---

## 4단계: 실패 재시도와 중복 방지까지 포함한 구조

외부 푸시 사업자는 언제든지 실패합니다. 429 / 5xx / timeout / network 단절 / invalid token 등.

먼저 **재시도 가능한 실패** 와 **재시도하면 안 되는 실패** 를 분류합니다.

- 재시도 가능 (transient): timeout, connection reset, 429, 5xx → `RETRY_SCHEDULED`
- 재시도 불가 (permanent): invalid device token, user unsubscribed, malformed payload → 각각 `SKIPPED_INVALID_TOKEN / SKIPPED_OPT_OUT / FAILED`

> **상태 의미 못 박기**: `FAILED` 는 terminal (permanent fail 전용). transient 실패는 절대 직접 `FAILED` 로 가지 않고 **무조건 `RETRY_SCHEDULED` 경유**. budget 소진 시에만 `DROPPED_OUTAGE` 로 종결.

### 발송 hot path — 원자적 Claim 패턴 (MongoDB)

MongoDB 에는 RDBMS 의 `INSERT ... ON CONFLICT DO UPDATE ... RETURNING` 이 직접 대응되지 않습니다. **Mongo replica set 기준 기본 권장 패턴은 Lazy Upsert Claim**. 선 생성(bulkWrite)은 **소규모(≤10만) 캠페인에서만** 예외적으로 사용. 이유: replica set 단일 primary 에서 majority+journal write 가 대량으로 몰리면 primary IOPS 가 먼저 병목이기 때문.

#### 선택 기준

| 대상자 규모 | 권장 패턴 | 이유 |
|------------|----------|------|
| ≤ 10만 | **선 생성 (bulkWrite)** | 진행률 분모 즉시 확정, 운영 UI 단순 |
| > 10만 | **Lazy upsert claim** | 선 생성은 majority+journal write 대량 발생 → primary 병목. lazy 는 실제 발송 유저만 write |

EC2 3대 replica set 환경에서는 **100만 유저 규모에서 선 생성이 primary IOPS 를 수십 분간 점유**합니다. 경계값을 두는 이유.

#### 1-A) 선 생성 패턴 (작은 캠페인)

```javascript
await db.notification_delivery.bulkWrite(
  users.map(u => ({
    updateOne: {
      filter: { campaign_id, user_id: u },
      update: { $setOnInsert: { campaign_id, user_id: u, status: 'PENDING', attempt_no: 0, created_at: new Date() } },
      upsert: true
    }
  })),
  { ordered: false, writeConcern: { w: 'majority', j: true } }
);
// 이후 워커는 1-B 의 find-only claim 사용 (upsert 없음)
```

#### 1-B) Lazy Upsert Claim (큰 캠페인, 기본값)

```javascript
try {
  const claimed = await db.notification_delivery.findOneAndUpdate(
    {
      campaign_id, user_id,
      // 이미 존재하면 PENDING/RETRY_SCHEDULED 만 허용
      // 존재 안 하면 upsert 로 생성 (filter 에 없으니 조건 자동 통과)
      $or: [
        { status: { $exists: false } },
        { status: { $in: ['PENDING', 'RETRY_SCHEDULED'] } }
      ]
    },
    {
      $set: {
        status: 'PROCESSING',
        processing_started_at: new Date(),
        claim_expires_at: new Date(Date.now() + 5*60*1000),  // 5분 heartbeat
        campaign_cancel_requested_at_seen: campaignSnapshot.cancel_requested_at ?? null,
        campaign_expires_at_seen: campaignSnapshot.expires_at
      },
      $setOnInsert: { campaign_id, user_id, created_at: new Date() },
      $inc: { attempt_no: 1 }
    },
    { upsert: true, returnDocument: 'after', writeConcern: { w: 'majority', j: true } }
  );
  if (!claimed.value) return;  // 드물게 발생 (race 이후 상태 확인 실패)
} catch (e) {
  if (e.code === 11000) return;   // DuplicateKey = 이미 terminal → skip
  throw e;
}
```

**왜 $or 두 조건인가**: 신규 유저는 `status` 자체가 없음 → `$exists:false` 로 통과. 기존 유저는 `PENDING/RETRY_SCHEDULED` 만 통과. 이미 terminal 이면 filter 미스 + upsert 시도 → unique key 충돌로 **E11000** 발생 → catch 에서 skip.

#### Campaign Cancel/Expire 원자성 (중요)

Claim 시점에 campaign 상태를 매번 join 으로 읽을 수 없으므로 **campaign 상태 변경을 delivery 로 전파** 합니다.

1. **운영자가 취소 / expires_at 도달** 시 expiry-sweeper 가 즉시 실행:
   ```javascript
   await db.notification_delivery.updateMany(
     { campaign_id, status: { $in: ['PENDING', 'RETRY_SCHEDULED'] } },
     { $set: { status: 'CANCELLED' /* or EXPIRED */, finalized_at: new Date() } },
     { writeConcern: { w: 'majority', j: true } }
   );
   ```
   → 전파 이후 claim filter 가 자동으로 이들을 배제 → **신규 claim 차단이 원자적으로 보장**
2. 이미 `PROCESSING` 상태인 것은 claim 시 memory 에 복사해둔 `campaign_cancel_requested_at_seen / campaign_expires_at_seen` 과 **finalize 직전 campaign 의 현재값을 비교** → 변경 감지 시 provider 호출 취소, `CANCELLED/EXPIRED` 로 finalize
3. 캠페인 상태는 짧은 TTL(1~2초) 로 워커가 로컬 캐시

→ **취소/만료 전파 SLA ≤ 5초** 를 보장하려면: expiry-sweeper 는 주기(1분) 외에 **취소/만료 이벤트 발생 시 on-demand 1회 즉시 실행** 이 API 서버에서 트리거됨. 워커 캐시 TTL = 2초. 최악 5초 이내 전파.

#### 2) Finalize

```javascript
await db.notification_delivery.updateOne(
  { campaign_id, user_id, status: 'PROCESSING' },
  { $set: { status: 'SUCCESS', sent_at: new Date(), provider_message_id }, $unset: { claim_expires_at: '' } },
  { writeConcern: { w: 'majority', j: true } }
);
// matchedCount === 1 일 때만 Redis progress counter INCR (drift 차단)
```

#### 3) Stale PROCESSING 회수 (processing-recovery worker, 신규)

워커가 claim 후 크래시 → `PROCESSING` 인 채로 영구 잔류하는 문제 회피. 별도 background worker 가 주기적으로 회수:

```javascript
// 1분 주기
await db.notification_delivery.updateMany(
  { status: 'PROCESSING', claim_expires_at: { $lt: new Date() } },
  { $set: { status: 'RETRY_SCHEDULED', next_retry_at: new Date() }, $unset: { claim_expires_at: '' } },
  { writeConcern: { w: 'majority', j: true } }
);
```

워커는 small batch 마다 **자신이 claim 한 유저들의 `claim_expires_at` 을 연장** (chunk lock extend 와 동일 주기). stale 인식 시 연장 중단 → 자연스럽게 TTL 만료 → 회수 대상.

규칙 요약:
- **허용 진입 상태는 `PENDING` 과 `RETRY_SCHEDULED`**. terminal 은 claim 불가
- `findOneAndUpdate` 자체가 원자 연산이라 동시 claim 시 단 하나만 성공
- DB 왕복 유저당 ≤ 2회 (claim + finalize)
- `writeConcern: { w: 'majority', j: true }` 필수
- Campaign cancel/expire 는 **sweeper 의 updateMany 로 delivery 에 즉시 전파**, claim 필터에서 자동 배제

### Chunk Lease Fencing (lease_version 기반)

워커 단위 fencing 이 없으면, **느린 워커가 살아있는데 lease-recovery 가 lease 만료로 회수**해 새 워커가 같은 chunk 를 잡는 상황이 생깁니다. 유저 단위 claim 이 중복 발송은 막지만:

- chunk 진행률 counter 이중 증가
- lock extend 경쟁
- chunk complete 처리가 두 번
- oversend 공식이 깨짐

→ **`lease_version` (monotonic fencing token)** 으로 모든 chunk write 를 보호합니다.

#### 1) Claim (워커가 chunk 획득)

```javascript
const claimed = await db.notification_chunk_job.findOneAndUpdate(
  {
    _id: chunk_id,
    status: { $in: ['PENDING', 'RETRY_SCHEDULED'] }
  },
  {
    $set: {
      status: 'PROCESSING',
      lease_owner: worker_id,
      lease_expires_at: new Date(Date.now() + 30_000),
      updated_at: new Date()
    },
    $inc: { lease_version: 1 }
  },
  {
    returnDocument: 'after',
    writeConcern: { w: 'majority', j: true }
  }
);
const myVersion = claimed.value?.lease_version; // null 이면 claim 실패
```

워커는 `myVersion` 을 메모리에 보관하고 **이후 모든 write filter 에 동봉**.

#### 2) Lock extend / complete (모두 version 검증)

```javascript
// lock extend (small batch 마다)
const ext = await db.notification_chunk_job.updateOne(
  { _id: chunk_id, lease_version: myVersion },
  { $set: { lease_expires_at: new Date(Date.now() + 30_000) } },
  { writeConcern: { w: 'majority' } }
);
if (ext.matchedCount === 0) {
  // STALE → 즉시 drain 종료, finalize write 포기
}

// complete
await db.notification_chunk_job.updateOne(
  { _id: chunk_id, lease_version: myVersion },
  { $set: { status: 'COMPLETED', finished_at: new Date() } },
  { writeConcern: { w: 'majority', j: true } }
);
```

#### 3) Lease recovery (만료 lease 회수)

```javascript
await db.notification_chunk_job.updateMany(
  { status: 'PROCESSING', lease_expires_at: { $lt: new Date() } },
  {
    $set: { status: 'PENDING', lease_owner: null },
    $inc: { lease_version: 1 }
  },
  { writeConcern: { w: 'majority', j: true } }
);
```

회수와 동시에 version 이 올라가므로 **구버전 워커의 모든 write 가 자동 reject** 됩니다.

#### 4) Retry-scheduler 도 동일 fencing

```javascript
await db.notification_chunk_job.updateMany(
  { status: 'RETRY_SCHEDULED', next_retry_at: { $lte: new Date() } },
  {
    $set: { status: 'PENDING' },
    $inc: { lease_version: 1 }
  },
  { writeConcern: { w: 'majority', j: true } }
);
```

retry-scheduler 가 중복 실행돼도 단일 updateMany 원자 연산이라 한 번만 실질 전환.

#### Stale 인지 동작

워커는 small batch 직전 lock extend 를 시도 → `matchedCount === 0` 이면:
1. 진행 중이던 small batch 의 finalize write 를 **포기** (claim 한 유저들은 다음 워커가 재 claim)
2. metric 에 `stale_worker_aborted` 기록
3. drain 종료

**중요**: claim / extend / recovery 의 모든 read 는 `readPreference: 'primary'` 강제. secondary lag 으로 stale version 을 보면 fencing 이 깨집니다.

### 재시도 정책

"유저 단위 재시도" 와 "chunk 단위 재시도" 를 분리합니다.
chunk 전체를 다시 던지면 이미 성공한 유저에게 중복 발송될 수 있기 때문입니다.

- chunk job 은 **"재처리 트리거"** 역할만 함
- 실제 발송 idempotency 는 위의 **원자적 claim** 으로 보장
- `notification_delivery_attempt` 에 시도 이력을 기록

외부 push 호출에도 **idempotency key** 를 함께 씁니다. provider 가 지원하지 않더라도 내부적으로 `campaign_id:user_id:attempt_no` 를 idempotency key 로 관리.

### 백오프 + 재투입 (Retry Scheduler)

`RETRY_SCHEDULED` 상태가 누군가에 의해 다시 처리 큐로 들어가야 합니다. 이 책임을 명시합니다.

- 옵션 A: **별도 cron worker** (1분 주기) — `SELECT ... WHERE status='RETRY_SCHEDULED' AND next_retry_at <= now() LIMIT N` 후 chunk re-enqueue, **lease_version + 1 fencing 적용**
- 옵션 B: **BullMQ delayed job** — RETRY_SCHEDULED 시점에 next_retry_at 시각으로 delayed job 등록

추천은 **A** (BullMQ delayed job 은 Redis 부담 + 가시성 부족). cron worker 가 DB 기준으로 동작 → 재시도의 진실도 DB 가 가짐.

백오프:
- exponential + jitter
- 1차 30초, 2차 2분, 3차 10분, 4차 30분, 5차 2시간 (max_attempt = 5)
- 단, **campaign expires_at 이 임박하면 더 이상 재시도 안 함** (다음 절)

### Campaign TTL · Outage Shedding (앱테크 핵심)

푸시는 시간 민감도가 큽니다. 출석 독촉이 4시간 뒤 오거나, 이벤트 알림이 종료 후 도착하면 **민원 + CS 폭발**.

#### Campaign TTL

- `notification_campaign.expires_at` 필수 필드
- 기본값: `scheduled_at + 6h` (스터디 합의 전 제안값, 캠페인 카테고리별로 달라야 함)
  - 출석/리마인드 = 1시간
  - 이벤트 시작 = 30분 (이벤트 시작 시각 기준)
  - 일반 공지 = 6시간
  - 포인트 지급 알림 = 24시간 (정확성 우선)
- `expires_at` 도달 시 **모든 미처리 delivery → `EXPIRED`** (background sweeper 가 일괄 전환)

#### Outage Shedding

Circuit breaker open + retry budget 초과 시 retry 누적이 무한히 쌓이면 안 됨.

- `notification_campaign.retry_budget_per_user` (기본값 = max_attempt = 5)
- 워커가 RETRY_SCHEDULED 처리 시 attempt_no >= retry_budget 이면 → `DROPPED_OUTAGE`
- expires_at 임박 (남은 시간 < expected_retry_window) 이면 추가 재시도 포기 → `DROPPED_OUTAGE`
- DLQ 와 구분: DLQ 는 운영자 재처리 대상, DROPPED_OUTAGE 는 **의도적 포기** (운영자가 다시 보내고 싶으면 캠페인을 새로 발급)

### Circuit Breaker (구체적 임계값)

- 범위: **provider × endpoint 단위** (예: FCM `/send`)
- 창 크기: 최근 30초 슬라이딩 윈도우
- Open 조건: 에러율 50% 이상 이고 요청 수 100건 이상
- Half-open: 30초 뒤 5건 probe → 성공 3건 이상이면 close
- Open 상태일 때 워커는 발송 시도를 하지 않고 RETRY_SCHEDULED 로 쌓되, **Outage Shedding 정책에 따라 일정량 이상은 DROPPED_OUTAGE**

### DLQ

재시도 일정 횟수 초과 시 DLQ. BullMQ 면 failed queue, Kafka 면 `notification-send-dlq` 토픽.
DLQ 는 **단순 보관소가 아니라 운영자 재처리 인터페이스의 입력 소스** 입니다.

- 실패 사유별 필터 → 선택 재처리 (예: "외부 사업자 503" 만 재처리, "invalid token" 은 제외)
- DLQ 적재량이 임계치 넘으면 알람
- DROPPED_OUTAGE 는 DLQ 에 안 들어감 (의도적 drop)

### 상태 머신 (공식화)

유저 단위 발송 상태 전이 — **`FAILED` 는 terminal, transient 는 직접 `FAILED` 로 가지 않음**.

```
              ┌─────────────────────────────────────────────┐
              │                                             │
PENDING ──claim──▶ PROCESSING ──provider accepted──▶ SUCCESS (terminal)
              │       │
              │       ├─transient fail──▶ RETRY_SCHEDULED ──cron+fencing──▶ PENDING
              │       │                          │
              │       │                          └─budget초과/expires_at임박─▶ DROPPED_OUTAGE (terminal)
              │       │
              │       ├─permanent fail──▶ FAILED (terminal)
              │       │
              │       └─invalid token / opt-out / quiet ──▶ SKIPPED_* (terminal)
              │
              └─cancel/expire (pre-claim)──▶ CANCELLED / EXPIRED (terminal)
```

Terminal 상태(claim 불가): `SUCCESS / FAILED / CANCELLED / EXPIRED / DROPPED_OUTAGE / SKIPPED_*`
Claim 가능 상태: `PENDING / RETRY_SCHEDULED` (단 2개)

캠페인 상태 전이:

```
DRAFT → TARGET_BUILDING → TARGET_READY → SCHEDULED → RUNNING ─┬─→ COMPLETED
                                                              ├─→ CANCEL_REQUESTED → CANCELLED
                                                              ├─→ EXPIRED
                                                              └─→ FAILED
```

이 전이 규칙을 코드로 고정하고, 허용되지 않는 전이는 런타임에 reject 합니다.

### 이 단계의 트레이드오프

- 원자적 claim 패턴 덕에 MongoDB write 부담은 절반 가까이 줄지만, `notification_delivery` 컬렉션 성장 속도는 동일 → 캠페인별 `expires_at + 30d` 기준 TTL 인덱스 또는 월별 archival 컬렉션 이행 필요. 규모 커지면 compound shard key (campaign_id + user_id hashed) 샤딩
- 상태 종류가 많아지므로 운영 UI 에서 status 필터가 필수
- DROPPED_OUTAGE 가 나오면 마케팅 팀이 "왜 안 갔냐" 질문할 것 → 운영 UI 에 사유와 카운트 노출
- lease_version fencing 으로 분산 안전성은 확보되지만 모든 chunk write 에 version 동봉이 강제됨 → ORM/리포지토리 레이어에서 누락되면 즉시 사고

---

## 5단계: 중간 취소가 가능한 구조

여기서부터는 큐만으로는 안 되고, **캠페인 상태 머신** 이 필요합니다.

이미 큐에 들어간 메시지는 Redis/Kafka 에 퍼져 있고, 일부는 워커가 가져갔고, 일부는 외부 사업자 전송 직전일 수 있습니다.
**"취소"는 단순히 큐에서 메시지를 지우는 행위가 아닙니다.**

정확히는:

- 캠페인 취소 = **새로운 처리 시작을 막는 것**
- 이미 lease 를 잡은 작업은 **다음 체크포인트에서 중단**
- 이미 외부 사업자 전송 완료된 메시지는 **되돌릴 수 없음**

> 즉, 취소는 **"전체 되돌리기"가 아니라 "추가 진행 중단"** 입니다.

### 구현

1. `notification_campaign.status` 를 `CANCEL_REQUESTED` 로 변경
2. 스케줄러가 아직 시작하지 않은 chunk job 들을 `CANCELLED` 로 변경
3. 워커는 chunk 처리 중간중간 캠페인 상태를 확인
   - chunk 시작 시 1회
   - 내부 batch (예: 50명) 처리 직전마다 1회
   - 가능하면 외부 push 요청 직전 1회 (claim 의 WHERE 절에 캠페인 status 조건 포함)

취소 전파 지연은 조금 있지만, 이미 큐에 들어간 작업을 중앙에서 강제 제거하지 않아도 안정적으로 멈출 수 있습니다.

### 취소 후 초과 발송 상한 (수치)

운영 합의용으로 worst-case oversend 를 수치로 박아둡니다.

```
worst_case_oversend ≤ worker_count × in_flight_chunks_per_worker × small_batch_size
```

현재 권장 기본값 기준:
- worker = 10
- in-flight chunks per worker = 5
- small batch = 50

→ **worst case = 10 × 5 × 50 = 2,500 건** 의 추가 발송이 취소 요청 시각 이후 발생할 수 있음.

조절 노브:
- small batch 50 → 10 으로 낮추면 worst case **500 건** 까지 축소 (단, lock extend / 취소 체크 빈도 5배 증가)
- 마케팅 팀과 사전 합의: "취소 후 최대 2,500 건은 추가 발송될 수 있음" 을 운영 UI 와 정책 문서에 명시

> **fencing 보장**: lease_version 덕에 lease-recovery 가 끼어들어도 stale 워커의 추가 발송은 차단되므로, oversend 공식이 정확히 유지됩니다.

### Kafka / BullMQ 에서의 취소

- **Kafka**: 큐에서 개별 메시지를 빼내 삭제하는 구조가 아님. "취소 = 메시지 제거" 가 아니라 "취소 = 소비자가 무효 처리"
- **BullMQ**: 대기 중 job 삭제는 쉬움. 다만 이미 active 상태 job 은 Redis 에서 지운다고 사라지지 않음. 결국 워커의 **cooperative cancellation** 이 핵심

### 운영 UI 에 반드시 표시

- "취소 요청 시간 이후 추가 발송 중단"
- 취소 요청 전 이미 provider 로 나간 건은 취소되지 않음
- **"취소 직전 in-flight 건은 최대 2,500건까지 추가 발송될 수 있음"** 을 표시
- 운영자가 이 점을 이해하고 누를 수 있어야 민원이 안 생김

---

## 6단계: CPU 80% 제한을 진짜로 지키는 구조

워커를 무한히 늘려도 병목은 아래 네 군데 중 하나에서 생깁니다.

1. 워커 프로세스 CPU
2. Redis/Kafka broker 처리량
3. DB write 처리량
4. 외부 push provider rate limit

### 워커 내부 동시성을 CPU 기준으로 조절

Node.js 는 단순 CPU load average 만 보면 늦습니다. `eventLoopUtilization` 또는 event loop lag 을 같이 봐야 합니다.

- CPU ≤ 65% && event loop lag 낮음 → 내부 동시성 조금 올림
- CPU ~ 75% → 유지
- CPU ≥ 80% 또는 event loop lag 기준 초과 → 동시성 낮춤
- provider 429 증가 → 동시성 추가 하향

즉 워커 처리 속도는 **CPU + event loop lag + provider 응답 + 큐 적체량** 을 함께 보고 결정합니다.

### 워커 오토스케일링 (ECS)

서비스를 분리합니다.

- `notification-api-service` — 고정 수 운영
- `notification-worker-service` — queue backlog + CPU 기반 scale out/in

정책 예시:

- backlog ≥ 10,000 && 워커 평균 CPU < 70% → scale out
- backlog 매우 낮음 && 워커 CPU ≤ 30% → scale in

**Scale in 은 성급하게 하면 안 됩니다.** active lease 가 끝날 때까지 기다려야 하므로 **drain mode** 가 필요합니다.

- 드레인 워커는 새 lease 를 잡지 않음
- 현재 진행 중인 small batch 만 마무리 후 종료
- 강제 종료돼도 lease timeout 뒤 다른 워커가 lease_version + 1 로 인계

### 병목 이동 관점

10 task 로 확장하면 수천 TPS, 더 가면 수만 TPS 도 가능. 다만 그 시점에는 **외부 provider rate limit 과 DB write IOPS 가 더 먼저 병목** 이 됩니다.

- DB 가 병목 → 이벤트 저장을 Kafka / S3 / OLAP 파이프라인으로 우회
- provider 가 병목 → provider 별 rate limiter
- `notification_delivery` 테이블이 수천만 row 쌓이면 → 파티셔닝 (campaign_id 기준) + 아카이빙

**즉 이 단계부터는 "워커 증설" 이 아니라 "병목 이전" 이 핵심입니다.**

---

## 7단계: 인프라 구조까지 포함한 실전 설계

Node.js 기반이고 실제 도입 전제라면 AWS 기준으로 아래 구성을 추천합니다.

### API 계층

- 외부 요청 → ALB → `notification-api` ECS 서비스
- 캠페인 생성 / 취소 요청 / 진행률 조회 / DLQ 재처리 / 발송 결과 조회
- 최소 2 task 이상을 서로 다른 AZ 에 배치

### 상태 저장소

- 캠페인 / chunk job / delivery 상태 = **MongoDB replica set** (EC2 3대, 운영 진실 데이터)
- 모든 write: `writeConcern: { w: 'majority', j: true }`
- fencing / claim / retry-scheduler read: `readPreference: 'primary'` + `readConcern: 'majority'`
- 분석/집계 read: `readPreference: 'secondaryPreferred'` (primary 보호)
- oplog window ≥ 48h 유지 (Change Streams consumer downtime 대비)
- Redis / Kafka 는 신호 전달용, 진실 저장소가 아님
- 샤딩은 현재 미도입. primary write TPS 가 인스턴스 한계 70% 초과 시점에 compound shard key `{campaign_id: 1, user_id: 'hashed'}` 로 이행

### Queue 계층

**BullMQ 노선**
- ElastiCache for Redis (Valkey), multi-AZ
- AOF on + 스냅샷 백업
- "명령 Queue" 만 Redis, 결과 이벤트는 별도 저장소 / Kafka 로

**Kafka 노선**
- MSK 또는 자체 Kafka cluster
- Topic: `notification-command`, `notification-result`, `notification-dlq`
- 반드시 MongoDB 상태 DB 와 병행

### Worker 계층

`notification-worker` ECS 서비스 (오토스케일링 대상).

각 워커 task 가 수행하는 발송 hot path:

```
1. 큐에서 chunk job fetch
2. DB 에 chunk lease 획득 → my_version 메모리 보관
3. 캠페인 상태 + expires_at 확인 (RUNNING / now() < expires_at)
4. snapshot 테이블에서 small batch (50명) 로드
5. 각 유저에 대해:
   a. 런타임 체크: device_token 유효 / opt_out / quiet_hours / 활성 상태
   b. Frequency cap RESERVE (Lua script, 원자)
   c. Ramp-up throttler 대기
   d. 원자적 claim (findOneAndUpdate + lazy upsert, filter: status ∈ {PENDING, RETRY_SCHEDULED} 또는 미존재)
      → campaign 캐시 값(cancel_requested_at, expires_at) 을 claim 문서에 *_seen 으로 snap
      → DuplicateKey/실패 시 reserve RELEASE 후 skip
   e. Provider 호출 직전: campaign 캐시 재확인 (cancel_requested_at/expires_at 변화 시 provider 호출 생략, CANCELLED/EXPIRED 로 finalize)
   f. Provider adapter 호출
   g. 결과 처리:
      - SUCCESS  → delivery UPDATE + Lua COMMIT (cap) + counter INCR + Kafka publish
      - TRANSIENT FAIL → delivery UPDATE(RETRY_SCHEDULED) + cap RELEASE
      - PERMANENT FAIL → delivery UPDATE(FAILED) + cap RELEASE + DLQ
      - INVALID TOKEN → delivery UPDATE(SKIPPED_INVALID_TOKEN) + invalid-token 이벤트 발행 + cap RELEASE
6. small batch 종료 시:
   ├─ chunk lock extend (filter: lease_version = my_version)
   │  └─ matchedCount = 0 → STALE 인지: 진행 포기, drain 종료 (남은 PROCESSING 은 processing-recovery 가 회수)
   ├─ 진행 중 유저들의 delivery.claim_expires_at 연장 (processing-recovery 보호)
   ├─ 취소 상태 재확인
   └─ progress reconcile
7. chunk 완료 시: chunk_job UPDATE(COMPLETED, WHERE lease_version = my_version) + lease 반납
```

SIGTERM 수신 시 → drain mode (새 lease 안 잡고 현재 batch 만 마무리, version 검증 통과 시에만 finalize).

### 외부 Push Provider 계층

- Provider adapter 계층으로 분리 (FCM / APNS / SMS / Email)
- Provider 별 rate limit, error code, retry 조건, 비용 특성 상이
- 공통 인터페이스 `ProviderAdapter` 를 두고 구현체 분리
- **Invalid token consumer** 가 별도로 돌면서 user_device_token 테이블 정리

### 트래킹/분석 계층

운영 DB 에 분석 쿼리 직접 때리지 않음.

- 발송 결과 이벤트 → Kafka / Kinesis → 분석 파이프라인
- 실시간성 필요하면 ClickHouse / BigQuery / Redshift
- 간단 시작은 MongoDB secondary read + 배치 적재도 가능 (단, secondary lag 으로 정산값과는 약간 차이)

운영 DB = **정확한 상태 관리**, 분석 계층 = **대량 집계**.

### Background Workers (별도)

워커는 발송용만 있는 게 아닙니다. 다음 background worker 들이 같이 돌아야 합니다.

| Worker | 주기 | 역할 | Fencing |
|--------|-----|-----|---------|
| `retry-scheduler` | 1분 | delivery 의 `next_retry_at` 도달분을 `RETRY_SCHEDULED → PENDING` 전환 (유저 단위 재시도) + chunk 의 next_retry_at 도달분을 PENDING 전환 | delivery: filter 기반 updateMany / chunk: `lease_version + 1` |
| `expiry-sweeper` | 1분 | 취소/만료 캠페인의 `PENDING/RETRY_SCHEDULED` delivery 를 **즉시** `CANCELLED/EXPIRED` 로 전파 + `notification_campaign.status` 도 전이 | 조건부 updateMany, terminal status 보호 |
| `processing-recovery` | 1분 | `claim_expires_at` 지난 `PROCESSING` delivery 를 `RETRY_SCHEDULED` 로 회수 (워커 크래시 대응) | filter 기반 updateMany |
| `progress-reconciler` | 5분 | DB 기준 aggregate 재계산 후 Redis counter 덮어쓰기 | 단순 GET → SET 덮어쓰기 |
| `lease-recovery` | 1분 | lease_expires_at 지난 chunk_job 을 PENDING 으로 회수 | `lease_version + 1` (stale worker write reject) |
| `invalid-token-consumer` | 상시 | Kafka 이벤트 → user_device_token 정리 | 일반 idempotent UPSERT |
| `change-stream-relay` | 상시 | point/event 등 도메인 컬렉션 Change Streams watch → notification campaign 생성 | resume token 영구 저장 |

이들은 `notification-worker` 와 별도의 ECS 서비스(또는 EventBridge + Lambda)로 분리하는 게 안전합니다.

### 모니터링

최소 지표:
- 큐 backlog 길이 / chunk 처리 시간 / delivery TPS
- provider 별 성공률 / 429 / 5xx / latency
- retry 대기 수 / DLQ 적재량 / **DROPPED_OUTAGE 건수** / **EXPIRED 건수**
- 워커 CPU / memory / **event loop lag**
- DB write latency / Redis·Kafka broker 지표
- Frequency cap reserve/commit/release 비율 / quiet hours skip 건수
- **Progress counter 와 DB aggregate 의 drift 크기** (reconciliation 결과 로그)
- **Stale worker write rejection 건수** (lease_version 미스매치로 0 row 반환된 case) — 정상치는 매우 낮음, 급증 시 lease TTL / 워커 latency 점검 신호

> CPU 80% 제한을 지키려면 CPU 만 보지 말고 **event loop lag 를 반드시 같이** 봐야 합니다.
> Node.js 는 CPU 가 아직 80% 안 찍혀도 이벤트 루프가 밀리면 이미 과부하 초기 상태일 수 있습니다.

---

## 장애 시나리오 (end-to-end 흡수 동작)

### 시나리오 1: 외부 provider 가 503 을 계속 반환

1. 워커가 발송 실패 감지
2. 에러 분류기가 transient failure 로 분류
3. `delivery.status = RETRY_SCHEDULED`, cap RELEASE (Lua DECR reserved)
4. **Circuit breaker 가 open** 으로 전환 → 이후 발송 시도 자체가 스킵되고 큐잉만
5. 30초 뒤 half-open probe → 복구되면 close
6. 복구가 늦으면 **expires_at 임박 또는 retry_budget 초과 → DROPPED_OUTAGE** (무한 누적 방지)

### 시나리오 2: 워커 task 가 chunk 처리 중 죽음 (혹은 lease-recovery 가 끼어듦)

1. `lease_expires_at` 만료 → `lease-recovery` worker 가 chunk 를 PENDING 으로 회수, **lease_version + 1**
2. 다른 워커가 chunk 재 claim → 새 my_version 획득
3. **만약 원래 워커가 살아 돌아와 finalize 시도하면**: `WHERE lease_version = old_version` 조건에 걸려 0 row 반환 → stale 인지하고 즉시 종료, finalize write 포기
4. 새 워커가 small batch 진행 → 원자적 claim 의 WHERE 조건 (`status IN ('PENDING','RETRY_SCHEDULED')`) 덕에 이미 SUCCESS 인 유저는 자동 skip
5. 미처리 유저만 재발송, counter drift / oversend 없음

### 시나리오 3: 마케팅 담당자가 취소를 누름

1. API 가 `notification_campaign.status = CANCEL_REQUESTED`
2. 아직 시작하지 않은 chunk job → `CANCELLED`
3. 실행 중 워커는 **다음 small batch 체크포인트**에서 멈춤 (worst case 2,500 건 추가 발송)
4. 이미 provider 로 나간 건은 취소되지 않음 (UI 에 명시)

### 시나리오 4: Scale in 시 active 작업 보호

1. Scale in 전 워커가 drain 모드로 전환
2. 새 lease 를 잡지 않고 현재 batch 만 종료, `WHERE lease_version = my_version` 조건으로 finalize
3. 강제 종료되더라도 lease timeout 후 lease-recovery 가 `lease_version + 1` 로 인계 → 강제 종료된 워커가 늦게 finalize 시도해도 reject

### 시나리오 5: 한 유저에게 동시 캠페인 3개가 몰림

1. 각 캠페인의 워커가 발송 직전 Redis Lua **RESERVE** (단일 원자 연산)
2. 3번째 캠페인의 reserve 가 cap 초과 → script 가 denied 반환 → SKIPPED_FREQUENCY_CAP, reserved key 증가 없음
3. 1·2번 캠페인은 provider accepted 후 Lua **COMMIT** (confirmed↑, reserved↓)
4. 만약 1번 캠페인이 transient fail → RELEASE (reserved DECR), cap 복원, 재시도 시 다시 reserve 시도
5. cap 이 실제 발송 수와 정확히 일치 (drift 없음, race 없음)

### 시나리오 6: 포인트 지급 직후 알림

1. 포인트 지급 트랜잭션 + outbox 레코드 insert (한 트랜잭션)
2. Outbox relay 가 outbox 를 읽고 알림 캠페인 생성
3. 알림이 실패해도 포인트 지급은 이미 커밋됨

### 시나리오 7: Provider 가 6시간 동안 죽음 (장기 장애)

1. Circuit breaker open 유지
2. 2시간 동안은 RETRY_SCHEDULED 가 계속 누적
3. expiry-sweeper / 워커 claim 시 expires_at 검증으로 시간 민감 캠페인부터 EXPIRED 전환
4. retry_budget(=5) 초과한 delivery 는 DROPPED_OUTAGE
5. 6시간 후 복구 시점에 살아있는 캠페인만 정상 재시도, 죽은 캠페인은 운영자가 새 캠페인으로 재발급
6. **"2시간 전 캠페인이 6시간 후 일제히 발송되는" 사고 차단**

### 시나리오 8: Retry-scheduler 와 워커가 동시에 같은 chunk 를 건드림

1. retry-scheduler 가 `RETRY_SCHEDULED → PENDING` 전환 시 lease_version + 1
2. 그 직전 RETRY_SCHEDULED 를 보고 있던 stale 워커가 같은 chunk 에 finalize 시도 → version 조건 fail, 0 row 반환
3. 새 워커가 PENDING 을 다시 claim, lease_version + 1
4. 동일 chunk 의 진행률 / complete 처리는 항상 한 워커만 성공

---

## 점진적 고도화 요약

| 단계 | 핵심 추가 | 트레이드오프 |
|-----|----------|-------------|
| 0 | Target snapshot + Snapshot Consistency Policy | 스냅샷 저장 비용 vs OLTP 부하 회피 |
| 3 | Queue + DB + Worker 분리 + Progress 정합성 규칙 + lease_version 컬럼 | 구현 쉬움 / 이벤트 스트림 분석은 약함 |
| 4 | 원자적 Claim (FAILED terminal) + Chunk Lease Fencing + Retry/Idempotency + DLQ + Circuit Breaker + Campaign TTL + Outage Shedding | 상태 종류 급증 / 모든 chunk write 에 version 동봉 강제 |
| 5 | Cancellation + Lease + Drain + Oversend 상한 수치화 | 워커가 상태 기반 실행 엔진이 됨 |
| 6 | Worker Auto Scaling + Dynamic Concurrency | 내부 동시성 제어까지 필요 |
| 7 | Command(BullMQ) / Event(Kafka) 분리 + Background Workers (fencing 포함) | 인프라 복잡도 증가 |

---

## 최종 추천 구조 (도입안)

```
[Marketing API]
     │
     ▼
[ALB] → [notification-api ECS]
                 │
                 ├─ 1) MongoDB: campaign insert (expires_at, retry_budget 포함)
                 │    └─ 대상자 ≤ 10만 시에만 delivery PENDING 선 생성 (bulkWrite),
                 │       그 외 기본은 워커의 lazy upsert claim
                 ├─ 2) Target snapshot 생성 (async)
                 └─ 3) BullMQ: chunk command enqueue
                          │
                          ▼
                 [notification-worker ECS]  ← auto scale
                     │
                     ├─ chunk lease (MongoDB findOneAndUpdate) → my_version 보관
                     ├─ small batch loop:
                     │    ├─ runtime check (token/optout/quiet)
                     │    ├─ frequency cap RESERVE (Lua, 원자)
                     │    ├─ ramp-up throttler
                     │    ├─ atomic claim (findOneAndUpdate,
                     │    │                 filter: status ∈ {PENDING,RETRY_SCHEDULED},
                     │    │                 campaign RUNNING + expires_at 사전 검증)
                     │    ├─ provider adapter → FCM/APNS/SMS
                     │    └─ finalize: COMMIT/RELEASE (Lua) + counter + Kafka publish
                     └─ lock extend (WHERE lease_version=my_version)
                          └─ 0 row → stale, drain 종료
                                              │
                                              ▼
                            [Kafka: notification-result]
                                              │
                                              ├─ invalid-token consumer
                                              ├─ analytics → ClickHouse
                                              └─ ops dashboard

[Background ECS services]
  ├─ retry-scheduler   (1m, lease_version + 1)
  ├─ expiry-sweeper    (1m + 취소/만료 이벤트 시 on-demand 즉시 실행)
  ├─ progress-reconciler (5m)
  └─ lease-recovery    (1m, lease_version + 1)
```

**역할 분리**

- **제어** = BullMQ
- **진실** = MongoDB replica set (w:majority, j:true)
- **분산 안전성** = lease_version fencing
- **정합성** = Lua script (cap) + 원자적 claim (delivery) + reconciliation (progress)
- **분석** = Kafka
- **확장** = worker ECS 서비스 단독
- **시간 관리** = expiry-sweeper + retry-scheduler

---

## 스터디 실행 로드맵 (Week 단위)

발표·설계로 끝나지 않고 **팀이 월요일부터 뭘 짤지** 를 정리합니다.

### Week 1 — 3단계 로컬 재현 (MongoDB replica set 1개로 로컬 구성)
- 목표: 1만 유저 로컬에서 단일 BullMQ + MongoDB (single-node replica set, PSS 모사) 로 전체 fan-out 동작 확인
- 산출물: `notification-api`, `notification-worker` 최소 구현, target snapshot 생성 로직, Snapshot Consistency Policy 코드 반영, chunk_job 에 `lease_version` / delivery 에 `claim_expires_at` 필드 포함
- 측정: 1만 유저 발송 소요 시간, 평균 TPS, 실패율

### Week 2 — 4단계 (lazy upsert claim / lease fencing / 재시도 / DLQ / Circuit Breaker / Campaign TTL)
- **기본 패턴**: `findOneAndUpdate` lazy upsert claim + E11000 catch. 소규모(≤10만) 테스트에서만 bulkWrite PENDING 선 생성 경로 추가로 검증
- 동시성 부하 테스트로 race 검증 (w:majority 강제)
- **`lease_version` fencing 구현 + lease-recovery 가 살아있는 워커를 회수하는 시나리오 강제 재현**
- Invalid token / 429 / 5xx 를 mock provider 로 주입
- expires_at + DROPPED_OUTAGE 동작 검증 (강제 장기 장애 시나리오)
- 측정: retry 후 최종 성공률, DLQ 적재율, DROPPED_OUTAGE 건수, **유저당 DB 왕복 수 (목표: claim 1 + finalize 1 = 2회 이하)**, **stale worker write rejection 건수 (정상 처리 시 0)**

### Week 3 — 부하 테스트 (k6)
- mock provider latency 150ms 고정
- 10만 유저 / 100만 유저 시나리오
- 측정: TPS 한계, CPU / event loop lag / DB write IOPS 중 **어느 것이 먼저 병목** 인지 기록
- Progress reconciler 의 drift 측정

### Week 4 — 5단계 (취소 / drain / oversend 측정)
- 발송 중간 취소 E2E 테스트
- Worker SIGTERM drain 테스트
- **실측 oversend 가 worst-case 공식과 얼마나 차이 나는지** 기록
- **drain 중 lease_version 검증으로 stale finalize 가 reject 되는지 확인**

### Week 5 — 6단계 (동적 concurrency + auto scaling)
- Event loop lag 기반 concurrency 자동 조절 구현
- ECS staging 에서 auto scaling 검증

### Week 6 — 앱테크 도메인 레이어
- Frequency cap **Lua script reserve/commit/release** 구현 + 동시성 1000 부하로 **cap drift 0 검증**
- quiet hours / ramp-up / outbox
- 실제 포인트 지급 시나리오와 연결

### Week 7 — 7단계 (Kafka 결과 이벤트 + Background Workers)
- `notification-result` 토픽
- Invalid token consumer
- 4종 background worker 분리 배포 (fencing 적용)
- 간단 분석 적재

### 스터디 공통 측정 목표

- **목표 TPS**: 2,000 delivery/s (mock provider 150ms 기준)
- **CPU 상한**: 워커 task p95 CPU ≤ 80%
- **Event loop lag**: p95 ≤ 50ms
- **DLQ 적재율**: 전체 발송의 ≤ 0.1%
- **재시도 후 최종 성공률**: ≥ 99.5% (invalid token 제외)
- **DB 왕복 수 (유저당)**: ≤ 2회 (claim + finalize)
- **Frequency cap drift**: 0 (confirmed_count == 실제 provider accepted 수)
- **Progress counter drift**: reconciliation 직전 ≤ 0.5%
- **Stale worker write rejection**: 정상 운영 시 0, 장애 주입 시 fencing 으로 100% reject 검증

---

## 스터디에서 던질 결정 질문

1. **중간 취소를 어디까지 보장할 것인가** — worst-case oversend 2,500건이 마케팅 팀에 수용 가능한가? small batch 크기를 더 줄일 것인가?
2. **재시도는 유저 단위인가, chunk 단위인가** — 원자적 claim 채택 시 자동으로 유저 단위로 정리됨
3. **CPU 80% 를 무엇으로 측정할 것인가** — CPU only vs CPU + event loop lag + 429
4. **BullMQ 만으로 갈지, Kafka 를 언제 도입할지** — 운영 복잡도 vs 데이터 활용도
5. **트래킹의 진실 소스는 무엇인가** — 운영 DB (정산) vs Redis counter (실시간) vs Kafka 이벤트 (분석)
6. **포인트 지급과 알림의 결합 강도** — outbox 패턴을 어느 시점에 도입할지
7. **Frequency cap 정책** — 유저당 시간당 N건 (마케팅 팀 협의)
8. **Ramp-up 기본값** — 100만 유저 발송 시 몇 분에 걸쳐 분산할지
9. **Campaign TTL 기본값** — 카테고리별 expires_at (출석 1h / 이벤트 30m / 공지 6h / 포인트 24h) 합의
10. **DROPPED_OUTAGE 처리 정책** — 운영자가 자동 알림 받고 새 캠페인으로 재발급할지, 묵시적 종료로 둘지
11. **FAILED terminal 정책** — permanent fail 만 FAILED 로 두는 방향이 맞는가? 운영자가 FAILED 도 직접 재처리하고 싶은 케이스가 있는가? (있으면 별도 admin re-enqueue API 필요)
12. **lease_version 누락 방어** — ORM/리포지토리 레이어에서 chunk write 시 version 조건 누락을 컴파일 타임 또는 lint 로 강제할 방법
