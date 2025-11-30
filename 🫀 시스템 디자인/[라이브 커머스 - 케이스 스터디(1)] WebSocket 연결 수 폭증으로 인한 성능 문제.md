## [라이브 커머스 - 케이스 스터디(1)] WebSocket 연결 수 폭증으로 인한 성능 문제

### 문제

- 라이브 커머스는 "10명 → 갑자기 1,000 명" 으로 급증하는 경우

### 기존 아키텍쳐

- Amazon IVS 사용
  - 라이브 스트림 SDK 제공
- Amazon DynamoDB 사용
  - NoSQL
- Amazon API Gateway
  - WebSocket Gateway

### 발생하는 현상

- DynamoDB 에 Connection ID 가 쌓임
- 여러 람다가 동시에 DynamoDB Scan
  - 성능 저하
- WebSocket Management API 호출 실패 증가

문제 예시)

1. WebSocket `@connect` Lambda

- `connectionId` 를 DynamoDB `Connections` 테이블에 넣음

```json
// Connections 테이블 예시
{
  "connectionId": "abc123",
  "channelId": "live-1",
  "userId": "user-1"
}
```

- 여기서 이야기하는 `channelId` 는?
  - 하나의 방송/라이브 룸을 나타내는 ID
  - Amazon IVS 기준으로는 **IVS 채널(또는 방송 세션)**에 매핑된다고 보면 됨
  - 비즈니스 관점에서는 라이브 방 하나에 해당

2. 방송 상태 변경 이벤트가 오면 (IVS → EventBridge → Lambda)

- Lambda 가 DynamoDB 전체를 Scan
- `channelId == live-1` 인 connection 들을 찾음
- 각각에 대해 `WebSocket Management API postToConnection` 호출

</br>

### 발생할 수 있는 문제

- 시청자 10명일 때는 문제 없음
- 그런데 1개 채널에 10,000 명 붙으면 ?
- **매 이벤트마다 Scan = 테이블 전체**
  - 테이블이 커질수록 기하급수적으로 성능 저하
  - 지연 시간도 길어짐 (Scan 한번에 수천 ms)
- **여러 Lambda 가 동시에 Scan**
  - 5개 이벤트가 동시에 발생 → Lambda 5개가 같은 테이블을 Scan
  - DynamoDB 쓰로틀 → Lambda 재시도 → 추가비용 + 지연
- **WebSocket Management API 호출 실패 증가**

  - 이미 끊어진 `connectionId` 에 `postToConnection` 하면 410
  - 연결이 많을수록 이런 좀비 connection 수가 늘어남
  - 매번 실패 처리 + 예외 로그 + 재시도 비용 발생

- 여기서 이야기하는 `connection` 은?
  - DB 커넥션이 아닌 WebSocket 커넥션을 의미함
  - 앱, 브라우저가 WebSocket Gateway 에 연결
  - API Gateway 가 `connectionId` 를 발급

</br>

## 개선 구조 - SQS fan-out + Query + Application Sharding

- **전체 테이블 Scan 이 아닌 → Application Sharding 을 통해 특정 샤드만 Query**
- **브로드 캐스트 작업을 SQS 로 fan-out**
- **WebSocket 실패시 DynamoDB 에 실패한 connection 삭제**
  - 또는 별도 DLQ 처리

</br>

### DynamoDB 테이블 설계

```json
// Connections 테이블
{
  "pk": "CHAN#live-1#SHARD#0",
  "connectionId": "abc123",
  "channelId": "live-1",
  "userId": "user-1",
  "createdAt": 1234
}
```

- pk (Partition Key)
  - 같은 채널이라도 shard 를 나눠서 한 파티션에 몰리는 것 방지

### shard 값 계산

```javascript
function shardKey(channelId: string, connectionId: string, shardCount: number) {
  const hash = simpleHash(connectionId);
  const shardNo = hash % shardCount; // 모듈러 처리
  return `CHAN#${channelId}#SHARD#${shardNo}`;
}
```

- DynamoDB 테이블은 1개 → `Connections` 테이블
- 시청자 한 명이 접속하게 되면 해시 함수 + 모듈러 연산을 통해 Partition Key 를 할당
- 이후 connection 정보 + 할당받은 Partition Key 로 DynamoDB 저장

### 이후 방송 상태 변경 → 1차 Fan-out Lamdba

- IVS → EventBridge → Lambda
- 아래 람다의 역할
  - 받은 이벤트를 기준으로
  - 해당 채널의 모든 shard 에 대해 **SQS 메시지**를 발행

```javascript
const sqs = new SQSClient({});

export const handle = async (event: any) => {
  const channelId = event.detail.channelId;
  const payload = {
    type: "LIVE_STARTED",
    channelId,
    startedAt: Date.now(),
  };

  const shardCount = Number(process.env.SHARD_COUNT ?? "10");
  const entries = [];

  for (let shardNo = 0; shardNo < shardCount; shardNo++) {
    entries.push({
      Id: `${chennelId}-${shardNo}`,
      MessageBody: JSON.stringify({
        channelId,
        shardNo,
        payload,
      }),
    });
  }

  await sqs.send(
    new SendMessageBatchCommand({
      QueueUrl: process.env.BROADCAST_QUEUE,
      Entries: entries,
    })
  );
};
```

- 이벤트를 받으면 해당 채널에 할당된 샤드에게 이벤트를 발행한다
- 즉 shard 수가 10이면 한 이벤트 당 SQS 메시지 10개 발행

</br>

### SQS → Lambda

- 실제 WebSocket 전송
- Lambda 내에서는 자신의 Partition Key 에 해당하는 부분에만 쿼리를 진행
  - 자신이 할당된 shard 내에만 쿼리

```javascript
const dbClient = new DynamoDBClient({});
const apiGateway = new ApiGatewayManagementApi({ endpoint: process.env.WS_ENDPOINT });

export const broadcastToWebSocket = async (sqsEvent: any) => {
    const connections: { pk: string }[] = [];

    for (const record of sqsEvent.Records) {
        const { channelId, shardNo, payload } = JSON.parse(record.body);
        const shardPk = `CHAN#${channelId}#SHARD#${shardNo}`;

        const response = await dbClient.send(new QueryCommand({
            TableName: process.env.CONNECTION_TABLE,
            KeyConditionExpression: "pk = :pk",
            ExpressionAttributeValues: { ":pk": { S: shardPk } },
            Limit: 100,
        });

        await apiGateway.postToConnection({
            ConnectionId: connectionId,
            Data: JSON.stringify(payload)
        });
    }
}
```

- SQS 기반 fan-out
  - shard 개수만큼만 Lambda 실행
  - 이벤트 빈도가 늘어나도 SQS 버퍼를 통해 처리

</br>

### 고려 사항

- 샤드 개수
  - 한 방송에 동시 최대 몇명을 생각해야 한다
  - 샤드 개수는 한 방송안에 connection 을 몇 덩어리로 나눌까를 의미하는 숫자
  - 잘 나가는 방송은 당연히 샤드 개수를 늘려야 한다
  - Lambda 의 타임아웃, WebSocket Management API 호출 속도, 네트워크 제한을 고려해야 함
  - 하나의 이벤트가 처리하는데 허용하는 지연 시간 → SLA 허용 범위내
    - 방송 시작됨 이벤트가 모든 시청자에게 1초 이내로 전달되면 된다 등
- 샤드 수를 나중에 변경하고 싶다면? → 리쉐이딩 전략
  - 버전 개념을 추가해야 한다
  ```json
  pk = CHAN#{channelId}#{version}#SHARD#{shardNo}
  ```
  - 처음에는 `V1` 에 샤드 개수 4개로 시작
  - 16 샤드로 늘리고 싶다면 → 신규 접속자는 `V2` 에 샤드 개수 16 으로 저장
    - `V1 shard 4` + `V2 shard 16` → 총 20개 SQS 메시지 발행
    - **두 버전을 같이 지원하는 과도기를 둔다**
