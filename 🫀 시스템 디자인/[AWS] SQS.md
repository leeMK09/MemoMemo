## SQS

- SQS 는 서버안의 큐 개념이 아닌 AWS 가 운영하는 분산 메시지 보관소의 개념이다
- 애플리케이션은 메시지를 SQS 에 넣고, 워커는 SQS 에서 메시지를 가져와 처리한 뒤, 처리가 끝났다는 의미로 직접 삭제한다
  - 이때 SQS 는 메시지를 소비자에게 전달했다고 바로 제거하지 않는다
  - 메시지를 잠시 안 보이게 만들고, 소비자가 삭제하면 최종 제거한다
  - 잠시 안 보이게 하는 시간이 바로 `Visibility Timeout`
    - AWS 공식 문서 기준으로 기본 `Visibility Timeout` 은 30초이다

</br>

## 전달이 아닌 대여 후 삭제

- SQS 를 처음 사용하면 착각하는 부분이 `consumer` 가 메시지를 받으면 메시지가 큐에서 사라진다고 생각하는 것 이다
- `Producer` 가 `SendMessage` 를 호출하면 SQS 는 메시지를 내부 저장소에 기록한다
- `Consumer` 가 `ReceiveMessage` 를 호출하면 SQS 는 메시지를 `consumer` 에게 응답으로 내려주지만, 그 순간 메시지를 삭제하지 않는다
- 대신 해당 메시지에 `ReceiptHanlde` 이라는 임시 처리권을 발급하고, 메시지를 일정 시간 동안 다른 `consumer` 에게 보이지 않게 만든다
- `Consumer` 가 비즈니스 로직을 정상 처리한 뒤 `DeleteMessage` 를 호출하면서 이 `ReceiptHanlde` 을 넘기면 그때 SQS 가 메시지를 최종 삭제한다
- 이 구조 덕분에 SQS 는 기본적으로 `at-least-once delivery` 즉 최소 한번은 전달한다에 가깝게 동작한다
  - AWS 가 메시지를 절대 중복 전달하지 않는다고 보장하는 구조가 아닌 장애나 timeout, 네트워크 문제, consumer 처리 실패가 있으면 같은 메시지가 다시 전달될 수 있게 설계되어 있다
- 그래서 SQS 를 사용하는 애플리케이션은 반드시 `idempotent` 즉 같은 메시지가 두 번 처리되어도 데이터가 망가지지 않도록 멱등성을 지키도록 만들어야 한다

**예시: 포인트 지급 SQS**

- consumer 가 메시지를 받고 DB 에 포인트 적립을 성공시킨 직후 프로세스가 죽을 수 있다
- 이 경우 consumer 는 `DeleteMessage` 를 호출하지 못했기 때문에 `Visibility Timeout` 이 지나면 같은 메시지가 다시 나온다
- 이때 단순히 `user.point += 1000` 을 다시 실행하면 중복 지급이 되며 실무에서는 `point_event_id` 같은 유니크 키를 DB 에 저장하고, 이미 처리한 이벤트라면 다시 포인트를 지급하지 않도록 막는다

</br>

## Visibility Timeout 은 처리 중인 메시지를 잠시 숨기는 임대 시간

- `Visibility Timeout` 은 consumer 가 메시지를 받은 뒤, 해당 메시지가 다른 consumer 에게 다시 보이지 않도록 숨겨지는 시간이다
- 중요한 점은 `Visibility Timeout` 이 처리 보장 시간이 아니라는 것 이다
  - SQS 가 30초 안에 반드시 처리하라고 강제하는 것이 아닌 30초 동안은 다른 consumer 에게 이 메시지를 안 보여주겠다는 의미이다
- Consumer 가 30초 안에 처리를 끝내고 `DeleteMessage` 를 호출하면 메시지는 삭제된다
  - 30초 안에 처리를 끝내지 못하거나, 처리 도중 죽거나, 네트워크 문제로 삭제 요청을 보내지 못하면 메시지는 다시 visible 상태가 된다
- 중복 작업이 동시에 실행될 수 있는 구조이며 이 문제를 막으려면 `Visibility Timeout` 을 실제 처리 시간보다 충분히 길게 잡거나, 처리 중간에 `ChangeMessageVisibility` 로 timeout 을 연장해야 한다
  - 실무에서는 보통 짧은 API 후처리 작업처럼 평균 1초, p99 5초 정도면 `Visibility Timeout` 을 30 ~ 60초로 둔다
  - 이미지 리사이징, 대용량 엑셀 처리, 외부 API 호출처럼 시간이 긴 작업은 p99 처리 시간보다 넉넉하게 잡고, 장시간 작업은 heartbeat 방식으로 주기적으로 `Visibility Timeout` 을 연장한다
  - 다만 timeout 을 너무 길게 잡으면 consumer 가 죽었을 때 재처리가 늦어진다
  - 예를 들어 timeout 을 30분으로 잡으면 worker 가 메시지를 받은 직후 죽어도 그 메시지는 30분 뒤에야 다시 처리된다
- 그래서 `Visibility Timeout` 의 트레이드 오프는 명확하다, 짧게 잡으면 장애 복구는 빠르지만 중복 처리가 늘어나며 길게 잡으면 중복 처리는 줄지만 실패 감지가 늦어진다
  - 처리 시간 p99 보다는 길게, 장애 복구 허용 시간보다는 짧게 잡고 긴 작업은 timeout 연장과 idempotency 를 같이 둔다

</br>

## 블로킹 I/O + 원격 분산 저장소 API

- 애플리케이션과 OS 관점에서 보면 동작 흐름은 명확하다
- Producer 애플리케이션이 AWS SDK 로 `SendMessage` 를 호출하면, SDK 는 HTTP 요청을 만든다
- 이 요청은 사용자 프로세스의 메모리에 있는 JSON 또는 Query API 형태의 payload 에서 시작된다
- 런타임은 TLS 연결을 만들고, OS 커널의 TCP 스택은 데이터를 segment 로 나누어 NIC 로 보낸다
- 서버 입장에서는 이것이 HTTPS API 요청으로 들어오고, SQS 서비스는 인증, 권한 확인, 큐 라우팅, 메시지 저장, 복제 또는 내구성 확보 과정을 거친 뒤 응답한다
- Consumer 도 마찬가지이다
- Consumer 가 `ReceiveMessage` 를 호출하면, 사용자 프로세스는 소켓을 통해 SQS 엔드포인트에 HTTPS 응답을 보낸다
- 이때 `WaitTimeoutSeconds` 가 0이면 SQS 는 즉시 응답하려고 하고 메시지가 없으면 빈 응답이 돌아온다
- `WaitTimeoutSeconds` 가 20이면 SQS 는 최대 20초 동안 메시지가 들어오기를 기다렸다가 응답한다, 이 시간 동안 consumer 프로세스의 관점에서는 HTTP 요청 하나가 pending 상태이고, 런타임에 따라 스레드가 블로킹될 수도 있고, Node.js 처럼 이벤트 루프 기반 런타임에서는 소켓 이벤트를 기다리는 비동기 I/O 상태가 된다
- 즉 SQS 를 OS 레벨에서 이해할 때 중요한 것은 SQS 가 내 프로세스 안의 메모리 큐가 아니라 네트워크를 건너 호출하는 원격 큐라는 점이다
- 그래서 latency, retry, TCP connection reuse, TLS handshake 비용, SDK HTTP client connection pool, NAT Gateway 또는 VPC Endpoint 경로, IAM 인증 비용, CloudWatch metric 지연까지 함께 고려해야한다
- 트래픽이 많으면 애플리케이션의 병목이 CPU 가 아니라 `ReceiveMessage` / `DeleteMessage` API 호출 수, HTTP connection pool, NAT 비용, Lambda concurrency, DB connection pool에서 생길 수 있다
