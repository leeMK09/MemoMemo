## WAIT_TIME_SECONDS

- `WaitTimeSeconds` 는 `ReceiveMessage` 호출에서 사용하는 값이다
- 이 값이 0이면 short polling 이고, 1 ~ 20초면 long polling 이다
  - AWS 공식 문서 기준으로 long polling 은 `ReceiveMessage` 의 wait time 이 0보다 클 때 활성화되고, 최대값은 20초이다
  - Long polling 은 빈 응답과 false empty response 를 줄여 비용과 불필요한 호출을 줄이는데 도움이 된다
- Short polling 은 consumer 가 SQS 에 지금 당장 가져올 메시지가 있는지 물어보고 바로 응답받는 방식이다
- 메시지가 있으면 받고, 없으면 빈 배열을 받는다
  - 이 방식은 지연 시간이 아주 낮을 수 있지만, 메시지가 적은 큐에서는 빈 응답이 많이 발생한다
  - 빈 응답도 API 호출이기 때문에 비용이 들고, consumer 도 불필요하게 루프를 돌게 된다
- Long polling 은 consumer 가 SQS 에 최대 20초 까지 기다릴 테니, 그 안에 메시지가 생기면 응답해줘 라고 요청하는 방식이다
  - 메시지가 바로 있으면 즉시 응답하고, 없으면 기다린다
- 실무에서는 대부분 `WaitTimeSeconds=20` 을 기본 값처럼 사용한다, 특별히 초저지연이 필요한 경우가 아니라면 long polling 이 비용과 효율면에서 유리하다
- Node.js worker 로 예를 들면
  - long polling 을 사용하더라도 이벤트 루프 전체가 멈추는 것은 아니다
  - AWS SDK 가 HTTP 요청을 날린 뒤 소켓 이벤트를 기다리는 동안 Node.js 는 다른 I/O callback 을 처리할 수 있다
  - 반대로 Java 에서 동기 SDK 를 쓰면 해당 worker thread 가 응답을 기다리는 동안 블로킹된다
  - 그래서 Java 에서는 worker thread poll 크기와 long polling 개수를 함께 설계해야 하고, Node.js 에서는 HTTP client 와 max socket 수와 동시 receive loop 개수를 함께 봐야 한다

</br>

## MAX_NUMBER_OF_MESSAGES

- `MaxNumberOfMessages` 는 `ReceiveMessage` 한 번으로 최대 몇 개의 메시지를 받을지 정하는 값이다
- SQS 는 한 번에 최대 10개까지 받을 수 있고 AWS SDK 문서와 API 문서에서도 `ReceiveMessage` 는 최대 10 개의 메시지를 가져온다고 설명한다
- 이 값은 처리량과 실패 범위 사이의 트레이드 오프를 만든다, 1개씩 가져오면 한 메시지 처리 실패가 다른 메시지에 영향을 덜 준다
- 하지만 API 호출 수가 많아지고 처리량이 낮아질 수 있다, 10개씩 가져오면 API 호출 수가 줄고 처리량이 올라가지만, batch 단위로 처리하는 코드에서 중간 하나가 실패했을 때 나머지 메시지까지 재처리될 수 있다
- Lambda 와 SQS 를 연결할 때는 이 문제가 더 중요해진다
  - Lambda event source mapping 은 SQS 를 polling 해서 메시지를 batch 로 받아 Lambda 를 호출한다
  - Lambda 가 batch 전체를 성공 처리하면 메시지를 삭제한다
  - 반대로 함수가 batch 중 하나 때문에 실패하면 기본적으로 batch 전체가 다시 큐로 돌아갈 수 있다
  - AWS 는 이를 보완하기 위해 partial batch reponse 를 지원하며, 실패한 메시지만 다시 큐로 돌릴 수 있다
- 실무에서는 메시지 하나하나가 독립적이고 실패 가능성이 낮으면 batch size 를 10에 가깝게 둔다
- 반대로 외부 API 호출, 결제, 포인트, 정산처럼 실패나 중복 처리 비용이 큰 작업은 batch size 를 작게 잡거나 partial batch response 를 반드시 사용한다

</br>

## CONCURRENCY

- `Concurrency` 는 SQS 메시지 자체의 필드라기보다는 consumer 쪽 실행 전략이다
- 직접 worker 를 운영한다면 concurrency 는 동시에 몇 개의 receive loop 를 돌릴지, 한 번에 받은 batch 를 몇 개의 promise/thread/coroutine 으로 병렬 처리할지, worker pod/task 를 몇 개 띄울지를 의미한다
- Lambda 를 사용한다면 Lambda event source mapping 이 큐를 polling 하고, 메시지를 backlog 에 따라 Lambda 동시 실행 수를 늘린다
- Lambda 문서에 따르면 SQS event source mapping 은 큐를 polling 하고 batch 단위 이벤트로 Lambda 를 동기 호출한다
- Concurrency 를 높이면 처리량은 올라간다
- 하지만 DB connection poll, 외부 API rate limit, Redis connection, CPU, memory, downstream 서비스 부하가 같이 올라간다
- SQS 자체는 메시지를 많이 공급할 수 있어도, consumer 뒤에 있는 DB 가 버티지 못하면 장애가 난다
- 그래서 SQS 기반 워커에서 concurrency 는 큐를 빨리 비우고 싶은 마음으로 정하면 안 되고, downstream 이 안전하게 감당할 수 있는 처리량을 기준으로 정해야 한다
- 예를 들어 알림 발송 worker 가 SQS 메시지를 소비해서 FCM/APNs 를 호출한다면, concurrency 는 서버 CPU 보다 FCM/APNs rate limit, 네트워크 timeout, 실패 재시도 정책에 더 크게 영향을 받는다
- 주문 정산 worker 라면 DB row lock, unique constraint, transaction duration 이 concurrency 의 상한이 된다

</br>

## MessageGroupId

- `MessageGroupId` 는 FIFO Queue 에서 필수이다
- AWS 문서 기준으로 FIFO Queue 에 메시지를 보낼 때 `MessageGroupId` 를 제공하지 않으면 요청이 실패한다
- Standard Queue 에서도 최근에는 fair queue 용도로 `MessageGroupId` 를 사용할 수 있지만, 순서 보장의 핵심 개념은 FIFO 에서 가장 중요하다
- FIFO Queue 에서 SQS 는 같은 `MessageGroupId` 를 가진 메시지를 순서대로 처리되도록 제어한다
  - 예를 들어 `MessageGroupId = user-1` 인 메시지 A, B, C 가 순서대로 들어오면, SQS 는 A 가 consumer 에서 전달된 뒤 A 가 삭제되거나 다시 visible 상태가 되기 전까지 B 를 같은 group 에서 다음 처리 대상으로 넘기지 않는다
  - AWS 문서에서도 메시지가 검색되었지만 삭제되지 않는 경우 visiblity timeout 이 끝날 때까지 invisible 상태로 남고, 같은 message group 의 추가 메시지는 반환되지 않는다고 설명한다
- 이 구조는 순서 보장에는 좋지만 병렬 처리에는 불리하다
- 모든 메시지에 `MessageGroupId = global` 같은 하나의 값을 넣으면 전체 큐가 사실상 단일 worker 처럼 직렬 처리된다
- 반대로 `MessageGroupId = userId` , `orderId` , `accountId` , `surveyId` 처럼 비즈니스 충돌 단위로 나누면, 같은 사용자나 같은 주문에 대해서는 순서를 지키고, 서로 다른 사용자나 주문은 병렬 처리할 수 있다
- 실무 예시 : 포인트 적립
  - 사용자별 포인트 잔액을 순서대로 갱신해야 한다
  - `MessageGroupId = userId` 가 좋다, 그러면 같은 유저의 적립 > 사용 > 취소 이벤트는 순서대로 처리되고, 다른 유저의 이벤트는 병렬 처리된다
  - 반대로 전체 시스템의 모든 포인트 이벤트에 하나의 group id 를 쓰면 정합성은 단순해지지만 처리량이 크게 떨어진다

</br>

## MessageDeduplicationId

- `MessageDeduplicationId` 는 FIFO Queue 에서 중복 메시지 전달을 줄이기 위한 토큰이다
- AWS 문서 기준으로 같은 deduplication id 를 가진 메시지가 5분 deduplication window 안에 여러 번 전송되면 SQS 는 후속 메시지를 성공적으로 받은 것 처럼 응답하지만 consumer 에게 다시 전달하지 않는다
- 이 필드는 절대 중복 방지가 아니다
- 핵심은 5분 window 이다
- 같은 deduplication id 라도 5분이 지나면 다시 들어갈 수 있다
- 또한 deduplication 은 SQS 에 메시지를 넣는 단계의 중복 제거이지, consumer 가 처리하다가 실패해서 같은 메시지가 다시 전달되는 문제를 완전히 없애는 장치가 아니다
- 그러므로 `MessageDeduplicationId` 를 사용하더라도 DB idempotency key 는 별도로 필요하다
- 실무에서는 `MessageDeduplicationId` 에 랜덤 UUID 를 넣으면 중복 제거 효과가 거의 없다
- 같은 비즈니스 이벤트를 재전송할 때 같은 값이 들어가야 의미가 있다
- 예를 들어 주문 생성 이벤트라면 `order-created:{orderId}:{version}` 처럼 만들 수 있고, 포인트 지급 이벤트라면 `point-grant:{eventId}` 처럼 만들 수 있다
- 외부 API callback 을 SQS 에 넣는 경우라면 외부에서 받은 `transactionId` 나 `callbackId` 를 deduplication id 로 쓰는 것이 좋다
- 다만 메시지 body 기반 deduplication 을 켜면 SQS 가 메시지 본문 해시를 기반으로 deduplication id 를 생성할 수 있다
- 하지만 body 에 timestamp, traceId, requestId 처럼 매번 달라지는 필드가 섞이면 같은 비즈니스 이벤트라도 다른 메시지로 인식된다. 그래서 정합성이 중요한 시스템에서는 명시적인 `MessageDeduplicationId` 를 설계하는 편이 안전하다
