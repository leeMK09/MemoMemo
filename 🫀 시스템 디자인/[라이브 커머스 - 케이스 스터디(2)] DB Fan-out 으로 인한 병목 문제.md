## [라이브 커머스 - 케이스 스터디(2)] DB Fan-out 으로 인한 병목 문제

- [🫀 [라이브 커머스 - 케이스 스터디(1)] WebSocket 연결 수 폭증으로 인한 성능 문제](https://github.com/leeMK09/MemoMemo/blob/main/%F0%9F%AB%80%20%EC%8B%9C%EC%8A%A4%ED%85%9C%20%EB%94%94%EC%9E%90%EC%9D%B8/WebSocket%20%EC%97%B0%EA%B2%B0%20%EC%88%98%20%ED%8F%AD%EC%A6%9D%EC%9C%BC%EB%A1%9C%20%EC%9D%B8%ED%95%9C%20%EC%84%B1%EB%8A%A5%20%EB%AC%B8%EC%A0%9C.md)
  - 1번 문제 해결에서 나올 문제
    - 방송에서 발생하는 서비스 로직 이벤트를 시청자에게 실시간 공유시
    - SQS → Lambda 에서 DynamoDB 의 특정 샤드에 UPDATE / MODIFY 등의 작업을 수행
      - `shardCount` 기반의 브로드 캐스트
    - 인기 있는 방송이 많아질 경우 DynamoDB 의 병목 현상 발생

### 문제 상황

- 방송에서 발생하는 서비스 로직 이벤트를 시청자에게 실시간 공유시
    - 경매 카운트 다운, 입찰 발생, 채팅 메시지, 좋아요, 상품 가격 업데이트 등
- 인기 방송이 여러개 → 동시 방송 50개 이상 
- 각 방송당 시청자 1만 ~ 5,000 명 (핫한 채널)

### 발생하는 현상 + 문제

- 각 채널에 대한 상태 변경이 자주 업데이트 됨 
- Write 가 폭증 → Lambda 가 처리속도를 따라가지 못함 → Lambda 실행 시간이 길어지거나, 동시 실행 개수가 제한에 도달하면 → Throttling 
- Throttling → 재시도 → 지연증가 → WebSocket 쪽 이벤트도 늦게 전달됨 

</br>

### 해결 전략 1 - reservedConcurrency 를 설정 

- Lambda 동시 실행 한도에 도달하면 Throttling 발생 
- **Lambda 에 `reserved_concurrent_executions` 를 설정해서 → "이 함수는 최소 N개는 항상 쓸 수 있게" 예약해둠**

```yml
functions:
  ddbStreamConsumer:
    handler: src/stream-consumer.handler
    events:
      - stream:
          type: dynamodb
          arn: ...
          batchSize: 100
    reservedConcurrency: 50 # 이 함수는 항상 최대 50개까지 동시 실행을 보장
```

- 이렇게 설정해두면 다른 Lambda 가 폭주해도 **처리는 최소 50개 동시 실행까지는 항상 가능**
- 두 번째 처리는 **Lambda 안의 로직을 가볍게 만드는 것**

</br>

### 해결 전략 2 - Kinesis 를 끼워 넣어서 처리 

```text
DynamoDB (Channel) → Lambda (Stream Consumer) → SQS (Broadcast) → Lambda (to WebSocket) → WebSocket
```

**개선 버전**

```text
DynamoDB (Channel) → Lambda (DB to Kinesis) → Kinesis Data Stream → Lambda (to SQS Fan-out) → SQS (Broadcast) → Lambda (to WebSocket) → WebSocket
```

- 기존에 이벤트를 Consume 하는 Lambda 를 Channel 레코드를 Kinesis 로 forwarding 하는 얇은 관문같은 역할을 수행 
- 진짜 팬아웃/브로드캐스트 준비는 Kinesis consumer 쪽에서 담당하도록 분리 

**DB Streams → Kinesis 로 넘기는 Lambda**

- DynamoDB Streams 레코드들을 `ChannelEvent` 형식으로 정제 
- Kinesis Data Stream 에 기록만 함 → 다른일은 안함 

```javascript
type ChannelEvent = {
    channelId: string;
    eventType: 'LIVE_STARTED' | 'LIVE_ENDED' | 'VIEWER_COUNT_CHANGED' | 'AUCTION_UPDATED';
    version: number;
    occurredAt: string;
    data: Record<string, any>;
};
```

- `ChannelEvent` 객체 정의 

```javascript
import { DynamoDBStreamEvent } from 'aws-lambda';
import { KinesisClient, PutRecordsCommand } from '@aws-sdk/client-kinesis';

const kinesis = new KinesisClient({});

export const ddbStreamToKinesis = async (event: DynamoDBStreamEvent) => {
    const records: ChannelEvent[] = [];

    for (const record of event.Records) {
        if (record.eventName !== 'MODIFY' && record.eventName !== 'INSERT') continue;

        const newImage = record.dynamodb?.NewImage;
        if (!newImage) continue;

        const channelId = newImage.channelId.S!;
        const status = newImage.status.S!;
        const version = Number(newImage.version?.N ?? '0');

        const eventType = mapStatusToEventType(status); // 상태 → 이벤트 매핑 함수

        const channelEvent: ChannelEvent = {
          channelId,
          eventType,
          version,
          occurredAt: new Date().toISOString(),
          data: {
            ...
          },
        };

        records.push(channelEvent);
    }

    if (records.length === 0) return;

    // PutRecords로 1회에 여러 이벤트 batch 전송
    const entries = records.map((evt) => ({
        Data: Buffer.from(JSON.stringify(evt)),
        PartitionKey: evt.channelId, // 채널 단위로 샤딩
    }));

    await kinesis.send(new PutRecordsCommand({
        StreamName: process.env.KINESIS_STREAM!,
        Records: entries,
    }));
};
```

- Kinesis PutRecords 1번으로 끝나게 만들면 Streams 처리 비용, 시간이 상당히 일정하고 짧아짐 

**Kinesis → SQS 팬아웃 Lambda**

- Lambda 는 ChannelEvent → WebSocket broadcast 준비 역할 
- 역할 
    - Kinesis 에서 여러 ChannelEvent 읽기 
    - 같은 채널끼리 모으거나 압축 
    - SQS 메시지를 `shardNo` 기준으로 쪼개서 넣기 

```javascript
import { KinesisStreamEvent } from 'aws-lambda';
import { SQSClient, SendMessageBatchCommand } from '@aws-sdk/client-sqs';

const sqs = new SQSClient({});

export const kinesisToSqsFanout = async (event: KinesisStreamEvent) => {
    const messagesByChannel: Record<string, ChannelEvent[]> = {};

    // Kinesis 레코드 → ChannelEvent 배열로 decode
    for (const record of event.Records) {
        const payload = Buffer.from(record.kinesis.data, 'base64').toString('utf8');
        const evt = JSON.parse(payload) as ChannelEvent;

        if (!messagesByChannel[evt.channelId]) {
            messagesByChannel[evt.channelId] = [];
        }
        messagesByChannel[evt.channelId].push(evt);
    }

    const shardCount = Number(process.env.SHARD_COUNT ?? '10');

    // 채널 별로, shardNo 별로 SQS 메시지를 만들어 보냄
    for (const [channelId, events] of Object.entries(messagesByChannel)) {
        // channelId 기준으로 하나의 "합쳐진 페이로드" 만들 수도 있음 (옵션)
        const payload = {
            channelId,
            events,
        };

        const entries = [];

        for (let shardNo = 0; shardNo < shardCount; shardNo++) {
            entries.push({
                Id: `${channelId}-${shardNo}`,
                MessageBody: JSON.stringify({
                    channelId,
                    shardNo,
                    payload,
                }),
            });
        }

        // SQS Batch 전송
        await sqs.send(new SendMessageBatchCommand({
            QueueUrl: process.env.BROADCAST_QUEUE!,
            Entries: entries,
        }));
    }
};
```

### 정리 

- 문제 
    - Lambda 가 높은 쓰기처리 무거운 로직 동시처리시 DB 부하, Throttling 걸림 
- 1차 해결 
    - Lambda batch size, reserved concurrency 처리 
- 개선안 
    - Lambda 를 얇게 처리 → 단순히 Kinesis 에 Event 를 forwarding 
    - 팬아웃/브로드캐스트는 Kinesis → SQS → WebSocket 로 분리 
 


