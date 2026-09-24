# SQS Visibility Timeout 과 안전한 DLQ Redrive

## Visibility Timeout은 처리 시간 제한이 아닌 임대 시간

- Consumer가 `ReceiveMessage`를 호출했다고 해서 메시지가 큐에서 제거되는 것은 아니다
- SQS는 Consumer에게 `Handler`을 발급하고, Visibility Timeout 동안 다른 Consumer에게 메시지를 숨긴다
- Consumer가 처리를 완료한 뒤 `DeleteMessage`에 성공해야 메시지가 최종 삭제된다
- 따라서 다음 상황에서는 같은 메시지가 다시 노출된다
    - Consumer가 메시지를 수신
    - DB 저장이나 외부 요청 처리는 성공한다
    - `DeleteMessage`전에 프로세스가 종료된다
    - Visibility Timeout이 만료된다
    - 다른 Consumer가 같은 메시지를 다시 받는다
- SQS의 at-least-once 전달 특성상 중복 수신은 예외 상황이 아니라 시스템이 정상적으로 제공하는 복구 방식이다
- 그러므로 Consumer의 멱등성은 선택적인 최적화가 아니라 필수 조건이다

**Visiblity Timeout 설정 방식의 장단점**

- 장점은 구현이 단순하며 Heartbeat API 호출과 타이머 관리가 필요없다, 대부분의 정상 작업이 Timeout 안에 끝난다면 동시 중복 처리 가능성이 작아진다
- 단점은 메시지를 받는 직후 Worker가 죽어도 Visiblity Time 동안은 복구되지 않는다
    - FIFO 큐에서는 같은 `MessageGroupId`의 다음 메시지도 함께 대기할 수 있다
    - 정상 처리시간의 편차가 커지면 다시 Timeout을 늘려야 한다
    - Timeout을 길게 잡아도 중복 처리 가능성이 사라지는 것은 아니다
- 적합한 조건은 도메인마다 다르고 사용하는 패턴마다 다르지만 일반적인 조건들이 있다
    - 수 분 정도의 장애 복구 지연을 허용할 수 있는 구조이거나 처리량이 낮고 후속 메시지 블로킹의 영향이 적다면 SQS 구조를 고민해볼만 하다

**OCR 처리 SQS 사례**

- Worker에서는 기존 900초 Timeout을 300초로 줄이고, 처리 중 60초마다 `ChangeMessageVisibility`를 호출하도록 구현했다고 가정하자
- 이 구조의 장점
    - 정상적으로 동작하는 Worker는 필요한 만큼 처리 시간을 연장할 수 있다
    - Worker가 죽으면 Heartbeat도 중단되므로 300초 후 다른 Worker가 복구할 수 있다
    - 고정 Timeout을 최악의 처리시간에 맞출 필요가 없다
    - 장시간 처리와 빠른 장애 복구를 동시에 만족시킬 수 있다
- 단점
    - Heartbeat 자체가 SQS API 호출을 증가시킨다
    - 작업 완료, 예외, 프로세스 종료 시 타이머를 반드시 정리해야 한다
    - Heartbeat 실패와 본 작업 실패를 구분해야 한다
    - Event Loop 지연이나 네트워크 장애가 심하면 살아 있는 Worker의 임대가 만료될 수 있다
- SQS 도입의 적합한 조건
    - OCR, 이미지 처리, 외부 API처럼 실행시간 편차가 큰 경우
    - Worker가 죽었을 때 수 분 안에 복구해야 한다
    - 중복 처리를 막아야 하지만 장시간 고정 잠금은 피하고 싶다
- 해당 구조의 작업 분할, 체크포인트 구조
    - 작업 흐름: 이미지 수신 -> OCR 수행 -> 검증 -> 성공 처리
    - 각 작업 흐름에 맞게 분할 및 체크포인트를 구성함으로써 각 단계의 Timeout을 독립적으로 관리할 수 있다
        - 실패한 단계부터 재시작할 수 있고 어느 단계에서 병목이 발생하는지 관측하기 쉽다, 단계별 확장과 DLQ 분리가 가능하다
    - 그러나 중간 상태를 저장해야 하며, 단계 사이의 메시지 전달과 DB 상태 정합성을 다뤄야 한다
        - 전체 처리 흐름이 복잡해지고 메시지 순서와 보상 트랜잭션 설계가 필요하다
    - 추가적으로 개선이 필요하다면 아래 작업 분할을 다시 검토할 필요가 있다
        - 단일 작업 시간이 수십 분 이상으로 증가한 경우
        - 단계별 재처리 요구가 빈번해진 경우
        - OCR은 성공했지만 성공 처리만 실패하는 사례가 반복되는 경우
        - 하나의 DLQ에 서로 다른 실패 원인이 섞여 운영이 어려울 수 있음

</br>

## SQS DLQ는 DLQ 목록 조회 자체가 상태 변경이다

- SQS에는 메시지를 건드리지 않고 조회하는 peek API를 제공하지 않는다
- DLQ 관리 화면에서 메시지 목록을 보여주기 위해 `ReceiveMessage`를 호출하면, 그 메시지는 Visibility Timeout 동안 다른 조회와 Consumer에게 보이지 않게 된다
    - DLQ에 28건이 존재할때 운영자가 목록을 조회, 그중 5건을 Redrive시 나머지 23건이 화면에서 사라짐
    - 메시지가 삭제된 것이 아니라 목록 조회 시 설정된 Visibility Timeout 때문에 보이지 않게 된 것이다
- 해결 방안 혹은 우회 방안
    - 목록 조회에서 획득한 Handler와 만료 시각을 관리
    - 운영자가 화면을 보고 있는 동안 Heartbeat로 Visibility 연장
    - UI에 처리권 만료 상태를 표시
    - 만료된 Handler로 액션을 시도하면 원인을 명확히 안내
    - 장기적으로 SQS를 직접 조회하는 화면 대신 별도의 DLQ 인덱스나 운영용 Projection을 고려

</br>

## Redrive는 원자적인 연산이 아니다

- 일반적인 Redrive는 다음 두 단계로 동작한다
    1. 원본 큐에 `SendMessage`
    2. DLQ에서 `DeleteMessage`
- 두 작업은 하나의 트랜잭션이 아니며 다음처럼 부분 성공이 가능하다
    - SendMessage는 성공했지만 DeleteMessage가 실패하며 API 응답이 실패, 운영자가 다시 Redrive시 원본 큐에 작업이 두 번 전달된다
- 이 상태를 일반 실패로만 표현하면 운영자는 아무것도 처리되지 않았다고 판단하여 다시 Redrive 할 수 있다
- 따라서 Redrive 결과를 다음처럼 구분할 필요가 있다
    - 예시) `NOT_SENT` / `SENT_AND_DELETED` / `SENT_BUT_NOT_DELETED` ...

### Redrive 멱등성을 보장하는 아키텍쳐

**SQS FIFO Deduplication만 사용**

- 원본 메시지 ID를 기반으로 고정된 값을 사용한다
    - `redrive:{originalMessageId}`
- 장점
    - 별도 저장소가 필요없고 SQS가 중복 전송을 자동으로 제거, 구현비용이 작다
- 단점
    - FIFO의 deduplication window 밖에서는 같은 메시지가 다시 처리될 수 있고 Standard Queue에서는 사용할 수 없다
    - 애플리케이션의 최종 데이터 변경까지 보장하지는 않는다
- 예시) 현재 시각을 포함한다
    - `redrive-{originalMessageId}-{Date.now()}`
    - 매 클릭마다 새로운 ID가 만들어지기 때문에 SQS 입장에서는 전부 다른 메시지로 보게된다
    - 결과적으로 FIFO 중복 제거 기능을 애플리케이션 코드가 무력화한 방식

**분산 멱등성 락**

- Redis를 통한 멱등성 락
    - 예시 키: `dlq_redrive_lock:{originalMessageId}`
- 장점
    - 여러 컨테이너 인스턴스의 동시 요청을 하나로 제한할 수 있고 TTL을 적용하면 영구 잠금을 피할 수 있다
    - 빠르고 구현 비용이 비교적 적다
- 단점
    - Redis 같은 외부 의존성이 생기며 TTL이 너무 짧으면 느린 작업 중 다른 요청이 다시 진입할 수 있다
    - TTL이 너무 길다면 실패한 작업의 재시도가 지연될 수 있다, 락 획득과 SQS 전송 사이에 프로세스가 죽는 문제는 별도로 다뤄야 한다
- Redis 같은 외부 자원 장애시 in-memory 폴백을 사용하지 않는 방향을 생각해볼 수 있다
- 이 경우 가용성보다 정확성이 중요하므로 빠른 실패가 적절할 수 있다

**DB Redrive Ledger**

- Redrive 요청을 DB에 기록하고 `originalMessageId`에 unique index를 건다

```json
{
    "originalMessageId": "...",
    "status": "PROCESSING",
    "sentMessageId": "...",
    "requestedBy": "...",
    "requestedAt": "...",
    "completedAt": "..."
}
```

- 장점
    - 재처리 이력을 영구적으로 감시할 수 있으며 부분 성공 상태를 표현할 수 있다
    - FIFO의 5분 중복 제거 기간에 의존하지 않게되며 운영자, 처리시각, 결과 등의 감사처리 또한 가능해진다
- 단점
    - 스키마와 상태 전이 로직이 추가되며 중간 상태에서 프로세스가 죽었을 때 복구 정책이 필요하다
    - 데이터 정리와 보존 정책이 필요하며 SQS 전송과 DB 상태 변경은 여전히 하나의 트랜잭션이 아니므로 Outbox 패턴까지 고려해야한다
- 재처리 작업이 감사 이력이나 금전적 책임이 중요하다면 Redis 락보단 DB Ledger가 적합할 것 같다

**하류 도메인의 멱등성**

- 성공 처리 서비스가 `domainId` 혹은 `eventId` 를 unique key로 저장하고, 이미 처리된 이벤트라면 성공을 생략한다
- 장점
    - 상류의 어떤 실수에도 최종 중복 처리를 막을 수 있다
    - SQS 중복 수신과 수동 Redrive 중복을 모두 방어한다
    - 메시지 인프라와 무관하게 도메인 정합성을 보장한다
- 단점
    - 중복 외부 API 호출로 인해 비용이 발생할 수 있다
    - 도메인별로 멱등키를 정의해야한다
    - 이미 발생한 부수효과 자체는 막을 수 없다
- 가장 안전한 구성은 하나를 선택하는 것이 아니라 계층적으로 방어하는 것이다

```text
고정 Deduplication ID
  + Redis 또는 DB 기반 Redrive 멱등성
  + 하류 도메인 멱등성
```
