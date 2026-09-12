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
