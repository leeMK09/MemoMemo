## SQS 모드

- SQS 의 대표 모드는 Standard Queue 와 FIFO Queue 이다
- Standard Queue 는 높은 처리량과 단순한 부하 분산에 적합하다
- 메시지 순서를 엄격하게 보장하지 않고, 중복 전달이 발생할 수 있다는 전제에서 설계한다
- 그래서 이미지 처리, 알림 발송, 로그 후처리, 이메일 발송, 검색 인덱싱처럼 순서가 아주 중요하지 않거나 idempotent 하게 만들 수 있는 작업에 맞는다
- FIFO Queue 는 순서가 중요한 작업에 적합하다
- 같은 `MessageGroupId` 안에서는 순서가 유지되고, `MessageDeduplicationId` 를 통해 5분 deduplication window 안의 중복 발행을 줄일 수 있다
- 하지만 순서를 보장하는 만큼 message group 설계를 잘못하면 처리량이 쉽게 막힌다
- 특히 하나의 group 에 메시지가 몰리면 그 group 은 직렬 처리되므로 hot partition 처럼 병목이 된다
- 정산, 포인트, 재고 차감, 사용자별 상태 전이처럼 같은 엔티티에 대한 이벤트 순서가 중요한 경우에는 FIFO Queue 를 검토할 수 있다
- 하지만 모든 정합성을 FIFO 에만 맡기면 안되고 FIFO 는 메시지 전달 순서를 도와주는 장치이지, DB transaction isolation, unique constraint, optimistic lock, idempotency table 등을 대체하지는 않는다

</br>

## Pull 방식, Push 방식

- Pull 방식은 consumer 가 broker 또는 queue 에 직접 요청해서 메시지를 가져가는 방식이다
- SQS 의 기본 방식이 pull 이다
  - Worker 가 `ReceiveMessage` 를 호출하고, 메시지를 받으면 처리하고, 처리 후 `DeleteMessage` 를 호출한다
- Push 방식은 broker 또는 event source 가 consumer 를 호출하는 방식이다
- 예를 들어 SNS 가 HTTP endpoint 로 메시지를 보내거나, EventBridge 가 target 을 호출하거나, Lambda event source mapping 이 SQS 를 polling 한 뒤 Lambda 를 호출하는 방식은 애플리케이션 입장에서는 push 처럼 보인다
- 다만 중요한 점은 SQS 자체가 일반적인 HTTP 서버 endpoint 로 직접 push 하는 서비스가 아니라는 점이다
- Lambda 와 붙이면 Lambda 서비스가 대신 SQS 를 polling 하고 함수를 호출해주기 때문에 사용자는 push 처럼 느끼게 된다
- AWS 문서도 Lambda event source mapping 이 SQS 를 polling 하고 Lambda 함수를 동기 호출한다고 설명한다
- 직접 서버를 운영하는 경우에는 pull 방식이 제어권에 맞는다
- Worker 수, concurrency, retry, backpressure, gracefull shutdown, visibility extension 을 직접 세밀하게 제어할 수 있다
- 반면 운영 코드가 늘어나긴 한다, Lambda 방식은 운영 부담이 적고 자동 scale 이 쉽다
  - 하지만 Lambda timeout, cold start, batch 실패, partial batch response, reserved concurrency, downstream DB connection 폭증 같은 제약을 신경써야 한다

</br>

## SQS 를 push 처럼 사용한다면

- SQS 만 단독으로 두면 consumer 가 pull 해야 한다
- Push 처럼 만들고 싶다면 중간에 호출 주체를 붙여야 한다
- 가장 흔한 방식은 SQS > Lambda 이다, 이 경우 Lambda 서비스가 SQS 를 polling 하며 메시지가 있으면 Lambda 함수를 호출하고, 함수가 성공하면 메시지를 삭제한다
- 사용자는 worker 서버를 운영하지 않아도 되며 실패하면 visibility timeout 이후 재시도되고, 여러 번 실패하면 DLQ 로 보낼 수 있다
- 다만 batch 실패 시 전체 batch 가 재처리 되는 문제가 있으므로 partial batch response 를 켜는 것이 중요하다
- 이벤트 fan-out 이 필요하면 SNS > SQS 구조를 쓴다
- Producer 는 SNS topic 에 publish 하고, 여러 SQS queue 가 topic 을 구독한다
- 그러면 결제 이벤트 하나를 발행했을 때, 포인트 queue, 알림 queue, 분석 queue 가 각각 독립적으로 메시지를 받을 수 있다
- 이 구조는 Kafka 의 publish-subscribe 모델과 비슷하다 다만 Kafka 처럼 consumer group offset 을 broker 에 보관하면서 같은 topic 을 여러 consumer group 이 읽는 구조와는 다르다
- SNS 가 각 SQS queue 에 메시지를 복사해주고, 각 queue 가 독립적인 backlog 와 retry/DLQ 를 갖는 구조이다
- 스케줄링이나 SaaS 이벤트 라우팅이 필요하면 EventBridge > SQS/Lambda 를 사용한다
- EventBridge 는 규칙 기반 라우팅, 스케줄, SaaS integration 에 강하고, SQS 는 안정적인 버퍼와 재처리에 강하다
- 실무에서는 EventBridge 가 이벤트 라우터, SQS 가 내구성 있는 작업 대기열, Lambda/ECS worker 가 처리기 역할을 맡는 조합이 많다

</br>

## SQS 와 Kafka 의 차이점

- Kafka 에서는 topic 에 메시지가 append 되고, consumer group 마다 offset 을 따로 가진다, 그래서 같은 topic 을 A consumer group 은 알림 용도로 읽고, B consumer group 은 분석 용도로 읽고, C consumer group 은 정산 용도로 읽을 수 있다
- 각 group 은 자기 offset 을 독립적으로 관리한다
- SQS 는 큐 하나에 여러 consumer 가 붙으면, 그 consumer 들은 같은 메시지를 나눠 처리하는 competing consumer 관계가 된다
- 즉 consumer A 가 메시지를 받아 삭제하면 consumer B 는 그 메시지를 받지 못한다
- 그래서 SQS queue 하나는 보통 하나의 작업 목적을 위한 부하 분산 큐로 봐야 한다
- Kafka 처럼 하나의 이벤트를 여러 도메인에서 각각 처리하고 싶다면 SQS queue 하나에 consumer 를 여러 개 붙이는 것이 아닌 SNS topic 또는 EventBridge bus 를 앞에 두고 목적별 SQS queue 를 여러 개 둔다
  - 예를 들어 `OrderCreated` 이벤트가 발생하면 SNS topic 에 publish 하고 `order-created-point-queue`, `order-created-notification-queue`, `order-created-analytics-queue` 가 각각 구독한다, 그러면 각 도메인은 자기 큐의 backlog, retry, DLQ 를 독립적으로 관리할 수 있다

</br>

## 레플리카 셋 구성은 SQS 사용자가 직접 하는 개념이 아니다

- MongoDB 나 Kafka 를 직접 운영할 때는 replica set, broker replica factor, ISR 같은 구성을 사용자가 직접 설계한다
- 하지만 SQS 는 완전관리형 서비스이기 때문에 사용자가 SQS replica set 을 몇 개로 하겠다라고 설정하지 않는다
- 대신 사용자는 애플리케이션 레벨에서 안전성을 보완해야 한다
- 정합성이 중요하면 SQS replica set 을 직접 구성하는 것이 아니라, DB 에 idempotency table 을 두고, 메시지 처리 결과를 transaction 으로 기록하고, 실패 메시지는 DLQ 로 보내고, DLQ redrive 정책과 운영 알림을 둔다
- 더 강한 복구가 필요하면 outbox pattern 을 사용해서 DB transaction 과 메시지 발행의 간극을 줄인다

</br>

## 부하 분산 용도의 SQS

- SQS 가 가장 잘하는 일 중 하나는 부하 분산이다
- API 서버가 요청을 받자마자 무거운 작업을 처리하지 않고 SQS 에 메시지만 넣으면, API 서버는 빠르게 응답할 수 있다
- 이후 worker 가 SQS 에서 메시지를 가져와 처리한다
- 예를 들어 영수증 이미지 OCR, 대량 알림 발송, 이메일 발송, 추천 데이터 갱신, 외부 API 연동 같은 작업은 API 요청 흐름에서 직접 처리하면 사용자가 오래 기다리고, 트래픽이 몰릴 때 API 서버가 같이 무너진다
- 이때 SQS 를 중간에 두면 API 서버는 producer 가 되고 worker 는 consumer 가 된다, 큐에 쌓인 메시지 수가 늘어나면 worker ECS task 수를 늘리고, 줄어들면 worker 수를 줄인다
- 이 구조의 핵심 지표는 `ApproximateNumberOfMessageVisible`, `ApproximateAgeOfOldestMessage`, worker 처리 성공률, DLQ 메시지 수이다
- 메시지 수만 보고 scale out 하면 오래된 메시지가 방치될 수 있고, age 만 보면 순간 burst 에 과민하게 반응할 수 있다
- 실무에서는 visible message count 와 oldest age 를 함께 보고, downstream DB 나 외부 API 가 버틸 수 있는 상한을 둔 상태에서 autoscaling 을 건다

</br>

## SQS 의 단점

- SQS 의 첫 번째 트레이드 오프는 중복 처리이다
  - Standard Queue 는 중복 전달 가능성을 전제로 설계해야 하고, FIFO Queue 도 consumer 처리 실패로 인한 재전달 가능성은 남아있다
  - 이를 보완하려면 메시지마다 business idempotency key 를 넣고, DB unique constraint 나 Redis SETNX, DynamoDB conditional write 로 처리 이력을 남겨야 한다
- 두 번째 트레이드 오프는 순서와 처리량의 충돌이다
  - FIFO Queue 에서 순서를 강하게 잡을수록 병렬성이 떨어진다, 이를 보완하려면 `MessageGroupId` 를 너무 넓게 잡지 말고, 충돌이 발생하는 비즈니스 단위로 쪼개야 한다
  - 사용자별 순서가 필요하면 userId 로 group 을 나누고, 주문별 순서가 필요하면 orderId 로 group 을 나누는 식이다
- 세 번째 트레이드 오프는 push 기반 실시간 이벤트 브로커가 아니라는 점이다
  - SQS 는 기본적으로 consumer 가 polling 한다
  - Long polling 으로 비용과 빈 응답을 줄일 수 있지만, WebSocket 처럼 즉시 push 되는 구조는 아니다, 실시간 사용자 알림이라면 SQS 는 내부 작업 큐로 쓰고, 실제 사용자 push 는 WebSocket 서버, SNS mobile push, FCM/APNs 같은 채널로 분리하는 편이 좋다
- 네 번째 트레이드 오프는 메시지 조회와 재처리 운영이 생각보다 어렵다는 것이다
  - SQS 는 Kafka 처럼 과거 offset 을 원하는 시점으로 되돌려 replay 하는 용도에 강하지 않다
  - DLQ 로 실패 메시지를 모을 수는 있지만, 장기간 이벤트 로그와 재처리 플랫폼이 필요하면 S3 event log, Kafka, Kinesis, EventBridge archive/replay 같은 대안을 함께 검토해야 한다
