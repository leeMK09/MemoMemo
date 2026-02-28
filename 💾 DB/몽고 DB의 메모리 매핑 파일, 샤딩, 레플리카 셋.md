## 메모리 매핑 파일, mmap (OS 관점)

- 메모리 매핑은 운영체제가 제공하는 기능이며 애플리케이션이 파일을 `read()` / `write()` 로 직접 복사해서 다루는 대신 파일의 특정 구간을 프로세스의 가상 메모리 주소 공간에 매핑해두고 애플리케이션이 그 주소를 메모리처럼 읽고 쓰게 만드는 방식

**동작 방식**

- 애플리케이션이 어떤 페이지(예시: 4KB)를 처음 읽었을 때 그 페이지가 물리 메모리에 없으면 CPU 가 페이지 폴드(page fault)를 일으키고 운영체제가 디스크에서 그 페이지를 읽어 페이지 캐시에 올린 뒤 매핑된 메모리 주소에 그 페이지를 연결
    - 즉 파일 읽기가 메모리 접근처럼 보이지만 실제로는 OS 가 뒤에서 디스크 I/O를 수행
- 애플리케이션이 매핑된 메모리에 값을 쓴 것처럼 보이더라도 그 변경은 즉시 디스크로 내려가지 않고 운영체제가 해당 페이지를 더티 페이지(dirty page)로 표시한 뒤 적절한 시점에 백그라운드로 디스크에 플러시한다
    - 쓰기도 곧바로 디스크 쓰기가 아닌 메모리 수정 + 나중에 flush 로 동작
- 페이지 캐시는 운영체제 전역 자원이므로 같은 호스트에서 다른 프로세스가 메모리를 많이 사용하면 사용하던 페이지 캐시가 밀려날 수 있다
    - 그 결과 같은 쿼리를 다시 실행해도 페이지가 다시 디스크에서 읽히면서 지연이 커질 수 있다
- 결국 메모리 압박, 스왑, 페이지 폴트 패턴, 디스크 특성이 성능에 영향

</br>

## MongoDB 에서 메모리 매핑

**MMAPv1 시절**

- MMAPv1 엔진 (구 버전 스토리지 엔진)은 데이터 파일을 메모리에 매핑해서 접근하는 모델이 강했고 이 때문에 아래 문제들이 운영에서 중요해짐
    - 데이터가 커지면 프로세스의 가상 주소 공간이 커지고 실제로는 OS 페이지 캐시가 많은 부분을 커버한다
    - 순간적인 메모리 압박으로 페이지 캐시가 밀리면, 날라가면 랜덤 I/O 가 급증하면서 응답 지연이 발생할 수 있다
    - 특정 패턴에서 페이지 폴트 폭주나 스왑이 발생하면 데이터베이스가 멈춘 것 처럼 보일 수 있다
- 즉 MMAPv1 에서는 OS 가 캐시를 책임지는 구조가 강했고 운영자는 OS 메모리 정책과 스왑 정책을 사실상 데이터베이스 성능 정책처럼 다뤄야 했다

**WiredTiger**

- WiredTiger (현 스토리지 엔진) 는 내부적으로 자체 캐시(WiredTiger cache)를 가지고 그 위에서 B-Tree 계열 구조를 사용해 데이터를 관리
- 이때 운영체제도 여전히 파일을 다루므로 페이지 캐시가 관여하지만 성능의 핵심은 아래 흐름으로 동작한다
    - 클라이언트가 데이터를 읽으면 MongoDB 는 WiredTiger 캐시에서 먼저 페이지를 찾고 없으면 디스크에서 읽어 캐시에 올린다
    - 클라이언트가 데이터를 쓰면 WiredTiger 는 메모리(캐시)에서 페이지를 수정하고 이 변경은 즉시 디스크 데이터파일에 반영되지 않는다
    - 변경된 데이터는 주기적으로 체크포인트를 통해 일관된 시점의 디스크 상태로 내려가며 동시에 장애 복구를 위해 저널(journal)에 재실행 가능한 변경 기록이 기록된다
- 결국 읽기 성능은 캐시에 얼마나 데이터가 남아있는가로 좌우된다
- 쓰기 성능은 저널 기록 + 체크포인트 I/O + 압축/페이지 스플릿의 조합으로 좌우된다

**장/단점**

- 장점
    - OS 페이지 캐시와 엔진 캐시가 결합하면서 반복 접근 데이터를 매우 빠르게 처리할 수 있고 파일 I/O 를 단순화하는 구조가 성능과 구현을 동시에 유리하게 만들 수 있다
- 단점
    - 메모리 압박 상황에서 페이지 교체가 급격히 발생하면 지연이 급증
    - WiredTiger 에서는 캐시와 디스크 I/O 가 균형을 읽으면 체크포인트나 컴팩션 영향으로 지연이 될 수 있다

</br>

## Replica Set

- 일반적인 고가용성 및 데이터 복제 매커니즘으로 MongoDB 에서도 확장성이 핵심 키워드이므로 수행가능
- Replica Set 구성 요소
    - Primary 는 쓰기를 받아들이는 유일한 노드
    - Secondary 는 Primary 의 변경을 따라가며 복제본을 유지
    - 필요하면 Arbiter 라는 투표만 하는 노드를 두기도 함

**동작 원리**

- Primary 에서 발생한 모든 쓰기 작업은 변경 로그 형태로 순서대로 기록
    - MongoDB 에서는 이를 보통 oplog (operation log) 라는 개념으로 이해함
- 이를 Secondary가 따라가서 적용
- 데이터 파일을 통째로 복제하는 것이 아닌 변경 연산을 시간 순서대로 전파하는 방식
- Secondary 는 다음을 반복
    - Primary 의 oplog 를 일정 지점부터 읽기
    - 아직 적용하지 않은 연산을 로컬에 재생
    - 적용 지점이 Primary 를 따라잡을때까지 반복

**선출(election) 과 장애조치(failover)**

- Primary 가 장애로 보이거나 네트워크 분리로 인해 다수 노드가 Primary 를 볼 수 없게 된다면 Secondary 들이 투표를 통해 새로운 Primary 를 선출
- 여기서 중요한 안전 조건은 보통 다수결(majority) 기반
- 즉 전체 멤버 중 과반수가 합의할 수 있는 쪽이 Primary 가 됨

**Read Preference 와 Write Concern**

- Replica Set 을 이해할 때는 읽기/쓰기 보장 수준을 같이 이해해야 한다
- **Write Concern** : 해당 쓰기를 성공으로 간주하기 전에 몇 노드에 반영되었다고 확인할 것인가를 정한다
    - majority 를 사용하면 과반수에 반영된 변경만 성공으로 간주하므로 장애 시 데이터 손실 가능성이 줄어든다
- **Read Preference** : 읽기를 Primary 에서 할지 Secondary 에서 할지를 정한다
    - Secondary 에서 읽으면 읽기 분산이 되지만 복제 지연이 있으면 최신 값이 아닐 수 있다

**장/단점**

- 장점
    - 노드 장애 시 자동 복구로 서비스 지속성이 높아짐
    - Secondary 를 읽기용으로 활용하면 읽기 확장도 일부 가능
- 단점
    - 네트워크 분리나 지연이 있을 때 선출과 롤백 같은 복잡한 상황이 발생
    - 어느 데이터가 최신인가를 도메인에서 엄격하게 요구하면 Read Preference 설계가 까다로워짐
    - Write Concern 을 높이면 쓰기 지연이 증가할 수 있음

</br>

## Sharding

- 수평 확장(Scale-out) 메터니즘
- 데이터가 너무 커서 한 대의 서버(하나의 Replica Set) 에 담기 어렵거나 쓰기/읽기 트래픽이 몰려 단일 Primary 가 병목 될때 데이터를 여러 샤드(shard)로 나누어 저장
- 핵심 구성 요소
    - Shard 는 실제 데이터를 저장하는 단위이며 보통 각 샤드가 Replica Set 이다, 즉 샤딩은 거의 항상 레플리카 셋과 함께 간다
    - mongos 는 라우터 프로세스이며 클라이언트는 보통 mongos 에 접속
    - Config Server 는 메타데이터 저장소이며 어떤 데이터 범위가 어떤 샤드에 있는지 같은 샤딩 맵을 저장

**샤드 키**

- MongoDB 샤딩은 기본적으로 어떤 기준으로 데이터를 나눌지에 대해 샤드키로 결정
- 샤드 키는 단순한 인덱스가 아닌 분산 저장의 파티션 기준이기 때문에 여러 고려사항이 필요함
    - 데이터가 샤드 간 고르게 분산
    - 특정 샤드에 쓰기가 몰리는 핫스팟 문제
    - 쿼리가 특정 샤드만 요청하는지 아니면 모든 샤드를 요청하는지 (Scatter-Gather)
    - 재밸런싱 비용이 얼마나 드는지

**청크(chunk) 와 밸런서(balancer)**

- MongoDB 는 샤드 키의 값 범위를 일정 구간으로 나누어 chunk 라는 단위로 관리
- 특정 샤드에 chunk 가 과도하게 몰리면 밸런서가 chunk 를 다른 샤드로 옮겨서 분산을 맞춤
- 중요한 점은 샤딩은 단순히 처음에 나눠 저장하는 것이 아닌 운영 중에서도 지속적으로 분산 균형을 유지하려고 움직이는 시스템이라는 점
- 따라서 밸런싱이 활발히 일어나는 환경에서는 백그라운 데이터 이동이 I/O 를 소모할 수 있다

**쿼리 라우팅의 동작 원리**

- 클라이언트가 mongos 에 질의를 보내면 mongos 는 config server 의 메타데이터를 보고 다음을 결정
    - 이 쿼리가 샤드 키 조건을 포함해서 특정 샤드만 대상으로 하는지
    - 샤드 키 조건이 없거나 범위가 넓어서 여러 샤드를 대상으로 하는지
- 샤드 키 조건이 명확하면 타겟 라우팅이 되어 해당 샤드만 접근하므로 매우 효율적
- 반대로 샤드 키 조건이 없으면 mongos 가 모든 샤드에 질의를 던지고 결과를 합치는 방식이 되어 클러스터가 커질수록 비용이 커짐

**장/단점**

- 장점
    - 저장 용량과 처리량을 서버 여러 대로 분산시킬 수 있어서 단일 서버 한계를 넘어설 수 있다는 점
- 단점
    - 샤드 키 설계시 고려할 점(핫스팟, 스캐터-개더 등)이 있음
    - 데이터 이동과 메타데이터 관리가 어려움
    - 트랜잭션이나 조인 성격의 작업은 샤드간 경계를 넘으며 비용이 커짐

</br>

## Change Stream

- Oplog 는 Primary 에서 일어나는 모든 변경을 기록한다
    - Oplog 를 통해서 어떤 도큐먼트가 생성/수정/삭제되었는지를 실시간으로 감지할 수 있다
    - 해당 이벤트를 기반으로 다른 서비스에서 추가 작업을 수행할 수 있다
- 몽고DB 는 이를 고수준 API로 감싸 Change Stream 이라는 기능을 제공한다 → Change Stream 은 Oplog 위에 올라간 고수준 API 이며 형태는 특정 컬렉션/DB/전체 클러스터에 대한 변경 스트림을 읽는 커서 역할
    - 이를 통해 별도의 Kafka, Redis Pub/Sub 같은 메시지 큐를 사용하지 않고도 DB 레벨에서 카프카/레디스 Pub/Sub 느낌의 이벤트 스트림을 제공받을 수 있다
    - 이를 이용해서 서비스 간 느슨한 결합을 만들 수 있다

**중요한 점**

- Change Stream 은 몽고 DB Wire Protocol (= 드라이버 프로토콜) 기반이다
- 공식 API 는 `db.collection.watch()` 같은 드라이버/셸/SDK 메소드로 노출된다

```javascript
const changeStream = db
    .collection("test")
    .watch([{ $match: { operationType: "insert" } }]);

for await (const change of changeStream) {
    console.log("변경 감지", change);
}
```

- `watch()`
    - 내부적으로는 레플리카셋의 Oplog 를 tailing 하는 tailable cursor 를 연다
    - 계속해서 새로운 이벤트를 push/pull 하는 스트림을 유지한다
    - 각 이벤트마다 resume token 을 제공해서 끊겼다가 다시 이어 받을 수 있게 한다

**예시: BigQuery 가 Change Stream 을 구독**

- BigQuery 는 자체적으로 몽고 DB 프로토콜을 이해해서 Change Stream 을 직접 여는 것이 아니다
- 몽고 DB 쪽에 Change Stream 을 읽는 중간 서비스/커넥터가 존재함
- 해당 서비스가 읽은 이벤트를 Pub/Sub, Kafka, GCS, Cloud Storage 파일, Dataflow 등으로 흘려보냄
- BigQuery 는 해당 데이터를 스트리밍 insert, Batch(파일/스토리지) 로드 또는 Dataflow/Connector 를 통해 ingest
- 형태로 간접적으로 소비한다
- 예시 1) 몽고 DB → Change Stream → 커스텀 ETL 서비스 → BigQuery
    - Node/Java 같은 앱이 `watch()` 로 Change Stream 을 연다
    - 이벤트를 JSON 으로 변환해서 BigQuery 에 `INSERT` (Stream API) 혹은 GCS 파일로 로깅
    - BigQuery 는 해당 데이터를 테이블로 반영
- 예시 2) 몽고 DB Kafka Source Connector → Kafka → BigQuery Sink
    - 몽고 DB Source Connector 가 Change Stream 을 읽어서 Kafka 토픽에 이벤트를 push
    - BigQuery Sink (예: Kafka Connect BigQuery, Dataflow 템플릿)가 토픽을 읽어서 테이블에 적재
- 예시 3) Atlas 공식 연동 (Atlas → GCP 생태계 → BigQuery)
    - 몽고 DB Atlas 에서 GCP 연동 기능/커넥터 사용
    - 내부적으로는 Change Stream + ETL 파이프라인으로 BigQuery 에 동기화
- 예시 4) 몽고 DB → Change Stream → SSE → ETL 서비스 → BigQuery
    - Node/Java 같은 앱이 `watch()` 로 Change Stream 을 연다
    - 이벤트를 계속 읽고 해당 서비스가 받은 Change Stream 이벤트를 SSE(Server-Sent Events), WebSocket, chunked HTTP Response 등의 형태로 외부 HTTP 클라이언트에게 전송
    - ETL 서비스는 해당 HTTP 스트림을 받아서 BigQuery 에 쓰는 역할을 수행
- 즉 Mongo 프로토콜을 이해하는 소비자가 반드시 중간에 끼어있어야 한다
