## keep-alive 의 필요성

- 예를 들어 서울 리전에 있는 Node 서버가 도쿄의 외부 광고 벤더 API를 호출한다고 하자
- Node에는 다음 요청을 보낸다
    - `GET https://vendor.example.com/ads`
- 애플리케이션 코드만 보면 그냥 HTTP GET 하나이다
- 하지만 실제 네트워크에서는 곧바로 GET 요청이 날아가지 않는다
- 먼저 Node가 OS에게 말한다
    - "vendor.example.com 의 443 포트로 연결 하나 만들어줘"
- 그러면 OS의 TCP stack이 상대 서버와 TCP connection을 만든다

```text
Node 서버                                 Vendor
   |                                        |
   | -------- SYN ------------------------> |
   | <------- SYN + ACK ------------------- |
   | -------- ACK ------------------------> |
   |                                        |
```

(TCP 3-way handshake)

- 여기서 TCP connection이라는 건 쉽게 말하면 두 컴퓨터가 앞으로 데이터를 주고받기 위해 만들어 놓은 논리적인 통신 통로이다
- 서울 <-> 도쿄 왕복 지연이 존재하며 약 34ms 의 지연이 생겼다고 가정하자
- 그런데 HTTPS 이므로 여기서 끝이 아닌 TLS handshake 까지 진행되어야 한다

```text
Node 서버                                 Vendor
   |                                        |
   | ---- 지원 TLS 버전/암호화 방식 ------> |
   | <--- 인증서 + TLS 정보 ---------------- |
   | ---- 키 교환 -------------------------> |
   | <--- 암호화 연결 확립 ---------------- |
```

(TLS handshake)

- 실제 TLS 1.3 등에서는 세부 흐름이 다를 수 있지만 중요한 것은 HTTP 데이터를 보내기 전에 추가적인 network round trip 과 cryptographic work 가 필요하다
    - TLS 과정이 약 37ms 지연으로 가정
- 그래서 실제 광고 API 서버가 처리하는 시간이 20ms 라고 해도 요청 전체 시간은 단순히 20ms 가 아니다
    - 34ms (TCP 연결 생성) + 37ms (TLS 연결 생성) + 20ms (Vendor API 처리) = 91ms
- 서버 로직을 아무리 최적화해도 TCP + TLS 71ms 가 계속 고정비용으로 붙고 있다

</br>

### keep-alive 가 무엇을 바꾸는가

- keep-alive 를 사용하면 처음 connection을 만든 이후 요청이 끝나도 바로 닫지 않는다

```text
TCP + TLS connection 생성
        |
        |---- GET /ads
        |<--- response
        |
        |---- GET /ads
        |<--- response
        |
        |---- GET /ads
        |<--- response
```

- 첫 요청은 여전히 비싸지만 두 번째 요청부터는 이미 만들어 놓은 connection을 사용한다
- 그 결과 TCP/TLS connection 지연 비용을 여러 요청에 나눠서 부담하게 된다

</br>

## Node 는 그 connection을 어디에 보관하는가

- 여기서 `https.Agent` 라는 개념이 등장한다
- Agent를 그냥 HTTP 옵션 객체라고 생각하면 된다
- Agent는 좀 더 정확히 말하면 특정 서버로 가는 TCP/TLS socket을 관리하는 connection manager 이다
- 예를 들어 Node가 Vendor API로 HTTP 요청을 하나 보낸다
- 처음에는 Agent 안에 아무 connection도 없다
- 요청이 들어오면 Agent 가 connection을 하나 만든다
- 여기서 `socket`을 통해 애플리케이션 입장에서 TCP connection을 조작하는 OS 객체를 관리한다
- keep-alive 가 꺼져 있다면 Socket 은 종료되고 keep-alive가 켜져 있으면 Agent 는 이 connection 멀쩡하니까 다음 요청에서도 사용하자로 판단하게 된다
- 그래서 Socket 을 free socket pool로 이동시킨다

```text
Agent

active
  없음

free
  Socket #1
```

- 조금 후 다음 요청이 들어오면 새 TCP connection을 만드는 대신 free pool에서 Socket을 가져온다

</br>

### https.globalAgent?

- Node에는 Agent를 직접 만들지 않아도 기본적으로 사용하는 Agent 가 있다
    - 그게 `https.globalAgent` 이다
- Node 18 환경에서는 기본 Agent 의 keepAlive 가 false 였기 때문에 요청이 끝난 후 connection을 재사용하지 않는 구조이다
- 각 목적지에 대한 실제 socket pool 자체는 origin 기준으로 구분되지만, Agent의 정책은 하나의 공용 정책에 걸려 있다
- 그래서 globalAgent를 수정하면 사용하기 편하다
    - 예를 들어 모든 outbound HTTPS 통신에 keepAlive = true 를 쉽게 적용할 수 있다
- 그런데 운영에서는 위험할 수 있다
- 이유는 외부 시스템마다 특성이 전혀 다르기 때문이다
- 예를 들어 광고 Vendor A는 초당 호출량이 매우 많고 idle timeout이 60초라고 가정하자
- 반면 다른 Vendor B는 하루에 몇 번 호출하지 않고 connection을 10초만 idle해도 서버가 끊는다고 하자
- 둘에게 같은 Agent 설정을 적용하면 어느 한쪽에는 최적이 아니다
- 대신 설계를 바꿔 도메인 요구사항에 따라 connection 설정을 다르게 가져간다

```text
                       Node Worker

            ┌─────────────┴──────────────┐
            │                            │
            │                            │
        광고 Vendor                  다른 Vendor
            │                            │
            v                            v
      VendorAgent A                VendorAgent B

      keepAlive=true               keepAlive=true
      maxSockets=20                maxSockets=5
      maxFreeSockets=10            maxFreeSockets=2
      timeout=...                  timeout=...
```

- 이 설계의 핵심은 단순히 설정을 개별화한 것이 아니다
- 외부 dependency 별로 resource pool 과 failure domain을 분리한 것이다

> `maxSockets`
>
> - keep-alive가 좋다면 connection을 많이 만들면 더 빠를 것 같다
> - 예를 들어 요청이 100개 동시에 들어오면 connection 100개를 만들면 전부 동시에 처리할 수 있다
> - 그런데 그러면 외부 Vendor 입장에서는 갑자기 100개의 동시 요청이 들어온다
> - 그래서 Agent에는 `maxSockets` 라는 제한이 있다
> - 예를 들어 `maxSockets = 10` 이라고 가정하자
> - 현재 Vendor API 응답시간이 100ms 라고 한다면 10개 요청이 동시에 들어오면 Socket 하나 당 하나의 요청이 총 10개 실행된다
> - 그런데 11번째 요청이 이후에 들어온다면 새 connection을 만들고 싶지만 maxSockets 가 10이므로 Agent는 그 요청을 기다리게 된다
> - 따라서 `maxSockets` 는 그냥 connection pool size 가 아니며 실제로는 downstream concurrency limiter 역할을 한다
> - Vendor가 한 번에 받을 수 있는 트래픽을 제한하는 backpressure 장치이기도 하다
> - 이 때문에 값을 너무 크게 잡는 것도 문제고 너무 작게 잡는 것도 문제다
> - 그래서 `maxSockets` 값은 경험적으로 정할 값은 아니며 다음 관계를 보고 정해야 한다
>     - 동시 요청수 = RPS x 응답시간
>     - 예를 들어 Vendor 호출량이 100 RPS 이고 평균 응답시간이 100ms 라면 평균 concurrency는 대략 10 (100 x 0.1초)

</br>

### 워커 단위 설정

- 예를 들어 워커 당 `maxSockets` 가 10 ~ 20이라고 했을때, Node cluster가 worker 8개라면 Agent가 worker마다 따로 존재한다
    - Worker1 -> 최대 20, Worker2 -> 최대 20
    - 그러면 실제 프로세스 전체에서는 20 x 8 = 160개의 connection이 가능하다
- 즉 개발자가 코드에서 보는 숫자는 `maxSockets = 20` 이지만 Vendor에서 보는 숫자는 160일 수 있다
- 그래서 connection pool 의 capacity는 항상 프로세스 단위가 아니라 fleet 전체 단위로 계산해야 한다
    - 이건 DB connection pool 에서도 동일하다
    - 예를 들어 HikariCP를 pod 당 20으로 잡았는데 pod가 50개라면 DB 입장에서는 1,000 connection이다

> `maxFreeSocket`
>
> - 이제 요청이 끝났다고 해보자
> - 활성 connection 20개가 모두 free 상태가 되었다
> - keep-alive의 논리만 생각하면 20개를 다 저장해도 된다
> - 하지만 만약 새벽 시간대라서 몇 시간 동안 호출이 거의 없다면? 20개의 TCP connection을 계속 붙들고 있는 것은 별 의미가 없다
> - 그래서 `maxFreeSockets` 는 현재 아무 요청도 처리하지 않는 idle socket을 몇 개까지 pool에 남겨둘지 결정한다
> - 이 값 역시 트레이드 오프이다, 너무 작으면 트래픽이 조금만 다시 올라와도 connection을 새로 만들어야 한다

> `peer`
>
> - keep-alive connection에는 Node만 관여하는게 아니다
> - connection은 두 당사자가 공유하는 상태다
>     - Node <---> Vendor
> - Node 입장에서는 Vendor가 `peer` 이고 Vendor 입장에서는 Node 가 `peer` 이다
> - 그런데 현실에서는 실제 `peer` 가 Vendor application이 아닐 수도 있다
> - 앞 단에 Node -> Internet -> AWS ALB -> Nginx -> Vendor API Server 라면 Node 가 유지하고 있는 TCP connection은 Vendor application까지 직접 이어지는 것이 아니라 중간 Load Balancer에서 종료될 수도 있다
> - 그래서 connection idle timeout을 조사할때 "Vendor Node 서버 keep-alive timeout이 몇초인가?" 만 보면 안된다

</br>

## keep-alive 에서 가장 까다로운 문제 - stale socket

- Node Agent의 free pool에 connection 하나가 있다고 하자
    - Node Agent free socket: Socket A
- 마지막 요청 이후 55초 동안 사용하지 않았다
- Vendor Load Balancer의 idle timeout이 60초라고 하자
- 시간이 흘러 Vendor Load Balancer가 60초 동안 아무 데이터가 없다는 걸로 판단 후 connection을 끊는다
- 그런데 네트워크에서 connection close event가 Node까지 전달되는 과정과 Node가 새로운 요청을 받아 Agent에서 socket을 꺼내는 과정이 정확히 동시에 겹칠 수 있다

```text
시간 T

Vendor
"Socket A 종료"

        ↓ FIN/RST 전송


거의 동시에


Node
새 요청 발생

Agent
"어? free socket A 있네."

GET 요청을 Socket A에 쓰기 시작
```

- Node 입장에서는 socket을 선택하는 바로 그 순간까지만 해도 usable하다고 생각했다
- 하지만 실제 peer는 connection을 종료한 상태이다
- 그래서 write를 하면 상대방이 RST를 보내고 Node에는 `ECONNRESET` 이 올라온다
- 이것이 바로 `stale socket race` 이다
- 핵심은 Node가 오래된 socket을 보관해서 버그가 발생했다 처럼 단순한 것이 아니라 분산 시스템이 두 endpoint가 connection 상태를 바라보는 시점이 정확히 동기화될 수 없기 때문에 생기는 race condition이다
- 그래서 100% 완전히 제거하기 어렵고 발생 확률을 낮추고 안전하게 복구하는 방향으로 설계한다

> `scheduling: 'lifo'`
>
> - free pool에 socket 이 세 개 있다고 하자
> - Vendor idle timeout은 60초이다
> - FIFO라면 가장 오래된 pool에 들어간 소켓부터 사용할 가능성이 있다
> - LIFO라면 가장 최근에 사용된 소켓을 먼저 꺼낸다
> - 즉 LIFO 는 단순 자료구조 선택 문제가 아니라 connection freeshness를 활용하는 전략이다
> - 트래픽이 낮을수록 이 차이가 의미가 있다
> - 요청이 계속 들어오는 고트래픽 환경에서는 socket들이 자주 사용되므로 free socket이 오래 묵지 않는다
> - 하지만 간헐적인 외부 API 라면 오래된 socket을 잡을 가능성이 커진다

> `Agent timeout < peer idle timeout`
>
> - Vendor가 예를 들어 idle connection을 60초 후 끊는다고 하자
> - Node가 60초보다 오래 connection을 보관하면 언젠가는 이 경계에 들어간다
> - 그러면 race가 생길 수 있다
> - 그래서 Node가 더 먼저 포기하게 만든다
> - 이렇게 하면 대부분의 경우 우리 쪽이 connection을 먼저 폐기하므로 Vendor가 먼저 죽인 stale socket을 잡을 가능성이 낮아진다
> - Node 18 core `https.Agent`에서 `timeout` 이라는 옵션은 흔히 사람들이 생각하는 "free pool에 들어간 socket만 50초 뒤 삭제" 라는 아주 순순한 free socket TTL과 정확히 같지는 않다
> - socket inactivity timeout에 가까우므로 설정 방법에 따라 실제 요청 중인 socket 동작에도 영향을 줄 수 있다
> - 그래서 운영에서는 다음 개념을 분리해서 사고하는 것이 좋다
>     - TCP connection을 만드는 데 기다릴 시간
>     - HTTP 응답을 기다릴 시간
>     - 아무 데이터가 오지 않는 socket inactivity 시간
>     - pool에서 idle connection을 보관할 시간
>     - Agent queue에서 socket을 기다리는 시간
> - 사실 이것들은 전부 다른 timeout이다
> - 하나의 `timeout: 5000`으로 다 해결하려 하면 나중에 장애 원인 분석이 굉장히 어려워진다

</br>

## keep-alive 구조의 장점

```text
Vendor A 호출
    |
    v
Vendor A 전용 Agent
    |
    ├─ keepAlive
    ├─ maxSockets
    ├─ maxFreeSockets
    ├─ timeout policy
    └─ LIFO
```

- 가장 눈에 띄는 점은 물론 latency 이다
- 서울 <-> 다른 리전 호출에서 TCP + TLS 71ms가 매번 붙던 상황이라면 재사용되는 요청에서는 이 비용을 크게줄일 수 있다
- 그런데 운영 관점에서는 latency 못지않게 중요한 장점이 하나 더 있다
- Vendor마다 독립적인 resource budget을 갖게 된다

```text
광고 Vendor
maxSockets 20

결제 Vendor
maxSockets 5
```

- 예를 들어 위 구조라면 광고 Vendor가 느려져도 광고 connection pool만 영향을 받는다
- 다만 같은 Agent를 사용하는 endpoint끼리는 여전히 서로 영향을 줄 수도 있다
- 만약 두 endpoint가 하나의 Agent를 공유한다고 가정하자
- `/impression` 시 장애가 발생하여 모든 socket을 점유하면 두 endpoint 의 요청들은 Agent queue에서 기다리게 된다
- 그래서 필요하다면 Vendor 별 분리를 넘어 기능별/criticality별 Agent 분리도 가능하다
    - (= bulkhead 패턴과 비슷함)
- 즉 한 구역의 장애가 다른 구역으로 번지지 않게 resource pool을 격리하는 것이다
