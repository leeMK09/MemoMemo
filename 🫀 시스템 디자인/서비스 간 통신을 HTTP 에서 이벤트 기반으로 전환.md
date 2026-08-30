# 서비스 간 통신을 HTTP에서 이벤트 기반 Pub/Sub 구조로 전환

- 서비스가 서로 완전히 독립적으로 동작한다면 서비스 간 통신에 대해 크게 고민할 필요가 없다
- 하지만 실제 시스템에서는 하나의 도메인에서 발생한 변화가 다른 서비스에도 영향을 미치는 경우가 많다
- 예를 들어 주문을 담당하는 서비스에서 주문이 생성되었을 때 다음과 같이 기능들이 동시에 필요할 수 있다

```text
주문 생성
 ├─ 사용자 서비스에 주문 노출
 ├─ 배송 서비스에 배송 대상 생성
 ├─ 알림 서비스에서 알림 발송
 └─ 데이터 분석 서비스에서 주문 통계 반영
```

- 가장 단순하게 구현한다면 주문 서비스가 각각의 서비스 API를 호출할 수 있다

예시)

```text
Order Service
     │
     ├── HTTP ──> B2C Service
     ├── HTTP ──> Delivery Service
     ├── HTTP ──> Notification Service
     └── HTTP ──> Analytics Service
```

- 서비스가 적을 때는 이 방법이 오히려 가장 단순하다
- HTTP는 요청과 응답의 관계가 명확하고 장애 발생 여부도 즉시 확인할 수 있기 때문이다
- 문제는 하나의 사건에 관심을 가지는 서비스가 많아지기 시작할 때 발생한다

</br>

## 서비스 간 HTTP 통신의 문제

- 주문이 생성되었을 때 B2C 서비스에 해당 주문을 노출해야 한다고 가정한다
- 처음에는 다음 정도의 구조일 수 있다

```text
B2B Service
     │
     │ POST /orders
     ▼
B2C Service
```

- 그런데 시간이 지나면서 주문 생성이라는 사건에 관심을 가지는 서비스가 증가한다

```text
B2B Service
     │
     ├── POST /b2c/orders
     │
     ├── POST /delivery/orders
     │
     ├── POST /notification/orders
     │
     └── POST /analytics/orders
```

- 이때부터 주문 생성이라는 하나의 비즈니스 동작에 다른 서비스들의 존재가 직접적으로 들어오기 시작한다
- 즉 주문 서비스가 다음 사실들을 알고 있어야 한다
    - B2B 서비스가 존재한다.
    - 배송 서비스가 존재한다.
    - 알림 서비스가 존재한다.
    - 분석 서비스가 존재한다.
    - 각 서비스가 어떤 API를 제공한다
    - 각 API의 요청 형식이 무엇이다
    - 각 서비스 장애 시 어떻게 재시도해야 한다
- 결과적으로 서비스가 논리적으로 분리되어 있음에도 실제로는 서로 강하게 연결된다
- 이를 Temporal Coupling 즉 시간적 결합이라고 볼 수도 있다
    - A가 성공하기 위해서는
    - 같은 시점에 B 역시 정상이어야 한다
- 예를 들어 주문 DB 저장은 성공했지만 B2C API가 장애라면 문제가 애매해진다
    1. 주문 DB INSERT 성공
    2. B2C HTTP 요청
    3. B2C 장애
    4. 어떻게 해야하나..?
- 주문 생성 자체를 실패시킬 것 인가?
- 주문은 성공시키고 B2C 반영은 포기할 것인가?
- 재시도한다면 어디서 어떻게 재시도할 것인가?
- 다른 서비스가 추가될수록 문제는 더 복잡해진다

```text
Order 저장       성공
B2C 반영         성공
배송 생성        성공
알림 전송        실패
Analytics 반영   성공
```

- 분산 환경에서는 하나의 DB Transaction으로 이 모든 작업을 묶을 수도 없다
- 결국 서비스 간 HTTP 호출을 계속 늘리는 방식은 서비스 수가 증가할수록 장애 전파와 결합도를 증가시키게 된다

</br>

## Command와 Event를 구분할 필요가 있다

- 이 문제를 해결하기 전에 먼저 중요한 것은 모든 서비스 간 HTTP 호출을 이벤트로 바꿀 필요는 없다는 점이다
- 서비스 간 통신은 크게 두 가지 의미로 구분할 수 있다
    - `Command` : "이 작업을 해줘"
    - `Event` : "이 일이 발생했다"
- 예를 들어 다음은 Command에 가깝다
    - "배송을 시작해라."
    - "결제를 승인해라."
    - "주문을 취소해라."
- 반면 다음은 Event에 가깝다
    - "주문이 생성되었다."
    - "배송이 시작되었다."
    - "배송이 완료되었다."
    - "결제가 완료되었다."
- 차이가 중요하다
- Command는 일반적으로 누가 처리해야 하는지 발행자가 알고 있다

```text
Order Service
     │
     │ StartDeliveryCommand
     ▼
Delivery Service
```

- Event에서는 발행자가 누가 처리할지 알 필요가 없다

```text
Order Service

OrderCreated
     │
     ▼
 Event Broker
     │
     ├── B2C
     ├── Delivery
     ├── Notification
     └── Analytics
```

- Order Service가 알아야 하는 것은 오직 하나다.
    - **"주문이 생성되었다."**
- 누가 이 사건에 관심을 가지는지는 발행자의 관심사가 아니다
- 이러한 구조가 Publish / Subscribe 구조의 중요한 목적 중 하나다

</br>

## SQS 만 사용한다면?

- 이미 SQS 를 사용하고 있다면 가장 먼저 생각할 수 있는 방법은 하나의 Queue에 이벤트를 넣는 것 이다

```text
Order Service
       │
       ▼
      SQS
       │
 ┌─────┼─────┐
 ▼     ▼     ▼
B2C Delivery Notification
```

- 하지만 이 구조는 우리가 원하는 Pub/Sub 구조가 아니다
- SQS는 기본적으로 Queue이며 여러 Consumer가 하나의 Queue를 소비하면 메시지를 나눠 가진다

```text
Queue

Message A
Message B
Message C

    │
 ┌──┼──┐
 ▼  ▼  ▼

C1  C2  C3

----------

C1 → Message A
C2 → Message B
C3 → Message C
```

- Message A 를 C1, C2, C3가 모두 받는 것이 아닌 동일 Queue의 여러 Consumer는 보통 경쟁 소비자 Competing Consumer 관계가 된다
- Kafka의 Consumer Group과 비교하면 동일한 SQS Queue를 여러 Worker가 소비하는 모습이 하나의 Consumer Group과 어느 정도 비슷하다
- 따라서 다음 요구사항에는 하나의 SQS만으로 부족하다
- **하나의 이벤트를 여러 서비스가 각각 독립적으로 처리해야 한다**

</br>

### 방법 1. 서비스마다 직접 SQS로 발행하기

```text
               ┌──> B2C Queue
               │
Order Service ─┼──> Delivery Queue
               │
               └──> Notification Queue
```

- 위 구조처럼 각 서비스에 맞는 SQS 를 직접 발행한다면 독립적으로 처리할 순 있다
- 하지만 이 경우 기존 HTTP 방식과 본질적으로 비슷한 문제가 다시 발생한다
- Order Service 코드가 다음 Queue들의 존재를 모두 알고 있기 때문이다
    - `publish(b2cQueue)`
    - `publish(deliveryQueue)`
    - `publish(notificationQueue)`
- 새로운 Consumer가 추가될 때마다 Producer를 수정해야 한다
- 메시징 시스템을 도입했지만 서비스 간 논리적인 결합도는 크게 줄어들지 않은 것이다
- 따라서 한 단계의 메시지 라우터가 필요하다

</br>

### SNS + SQS Fan-out 구조

- AWS 에서는 대표적으로 SNS 와 SQS 를 조합해 이러한 구조를 만들 수 있다

```text
                   ┌──> B2C SQS
                   │
                   ├──> Delivery SQS
Order Service ──> SNS
                   ├──> Notification SQS
                   │
                   └──> Analytics SQS
```

- 여기서 역할을 나누어보면 아래와 같다
    - SNS
        - = Publish / Subscribe
        - = 하나의 메시지를 여러 Subscriber에게 전달
    - SQS
        - = Queue
        - = 메시지를 저장하고 Consumer가 안정적으로 처리할 수 있게 함
- AWS 에서도 SNS -> 여러 SQS로 메시지를 복제하는 구조를 대표적인 Fan-out 패턴으로 설명한다
- SNS 는 메시지를 여러 Subscriber에게 push 하고, SQS는 이를 보관한 뒤 각 Consumer가 독립적으로 처리하도록 한다
- 따라서 실제 구조는 다음처럼 설명된다

```text
                Publish
Order Service ───────────> Order Event Topic
                                  │
               ┌──────────────────┼──────────────────┐
               │                  │                  │
               ▼                  ▼                  ▼
           B2C Queue        Delivery Queue      Analytics Queue
               │                  │                  │
            Worker             Worker             Worker
```

- 중요한 점은 SNS Consumer Group 이 존재하는 것이 아니라 각 관심사별 SQS 가 독립적인 Subscription 역할을 한다는 것 이다
- 그리고 하나의 SQS 안에서 여러 Consumer 인스턴스를 실행한다

```text
SNS Topic
   │
   ▼

B2C Queue
   │
 ┌─┼───────────┐
 │ │           │
 ▼ ▼           ▼
B2C Worker1  Worker2  Worker3


-----------

Kafka

Topic
 ├─ Consumer Group A
 ├─ Consumer Group B
 └─ Consumer Group C


SNS + SQS

SNS Topic
 ├─ SQS A
 ├─ SQS B
 └─ SQS C
```

- 완전히 동일한 구현은 아니지만 Pub/Sub 관점에서는 비슷한 역할을 구성할 수 있다

</br>

**왜 SNS 만 사용하지 않고 SNS + SQS 를 사용하는가?**

- SNS 또한 직접 HTTP Endpoint 를 Subscriber로 등록할 수 있다

```text
SNS
 ├── HTTP → B2C
 ├── HTTP → Delivery
 └── HTTP → Notification
```

- 하지만 이 경우 Consumer 가 장애 상태라면 다시 전달 문제가 중요해진다
- 반면 SQS 를 사이에 두면 Producer 와 Consumer 의 실행 시점을 분리할 수 있다
    - SNS -> SQS 구조인 경우 Consumer 가 장애 발생시 메시지는 SQS 에 남아있음
    - 이후 Consumer 가 다시 복구되면 메시지 처리 가능
- 즉 SQS 가 버퍼 역할과 장애 격리 역할을 해준다
- SNS 는 Push 기반이고 SQS 는 Consumer 가 polling 하는 Queue 기반 서비스이다
- 두 서비스를 결합하면 이벤트를 여러 Consumer 에게 fan-out 하면서 동시에 각 Consumer가 나중에 처리할 수 있도록 메시지를 보존할 수 있다
