## MongoDB 격리 수준

- MongoDB 는 전통적인 SQL 수준(READ COMMITTED, REPEATABLE READ 등)을 그대로 채택하지 않고 동작 특성이 부분적으로 READ COMMITTED 에 가깝도록 설계된 모델을 사용한다
- 일반적인 관계형 데이터베이스에서 말하는 READ COMMITTED 격리 수준은 각 SELCT 문이 실행되는 시점에 이미 커밋된 데이터만 읽도록 보장한다
- MongoDB 는 read concern 과 write concern 이라는 독자적인 개념을 통해 읽기와 쓰기의 가시성 안정성을 정의한다

**read concern**

- MongoDB 에서 기본적으로 사용되는 read concern 은 `local` 이다
- 이 설정 하에서 MongoDB는 현재 노드(Primary 또는 Secondary) 에 로컬로 커밋된 데이터를 읽도록 허용한다, 중요한 점은 해당 커밋이 트랜잭션 커밋이 아닌 해당 노드 메모리와 스토리지에 반영된 상태를 의미한다는 점이다
- 이로 인해 다음과 같은 동작 특성을 나타낸다
    - 하나의 클라이언트가 데이터를 조회할 때 아직 커밋되지 않은 다른 연산의 중간 상태는 절대로 읽지 않는다
- MongoDB 는 statement (문) 단위로 현재 노드에 반영된 최신 데이터를 읽는다

**명시적인 트랜잭션 사용시**

- MongoDB 가 명시적 트랜잭션(`session.startTransaction()`)을 사용할 때 snapshot 기반 일관성 모델을 사용한다
    - 트랜잭션 시작 시점의 일관된 스냅샷을 기준으로 읽는다
    - 트랜잭션이 끝날 때까지 같은 쿼리는 같은 결과를 본다
    - 즉 스냅숏 격리(Snapshot Isolation)가 적용된다
- MongoDB 에서 멀티 도큐먼트 트랜잭션을 사용하지 않는 일반 CRUD 연산은 트랜잭션이라는 논리적 단위 자체가 존재하지 않는다
- MongoDB 의 트랜잭션 격리 모델은 Replica Set + 멀티 도큐먼트 트랜잭션 + snapshot read concern 이 결합될 때 수행된다 → Snapshot Isolation

</br>

## Snapshot Isolation

- 하나의 트랜잭션이 시작되는 시점에 존재하던 데이터베이스의 상태를 스냅숏으로 고정하고 트랜잭션이 끝날 때까지 항상 동일한 시점의 데이터만 읽도록 보장하는 격리 방식
    - 트랜잭션 시작 시점이 기준
    - 그 이후 다른 트랜잭션이 커밋한 변경은 보이지 않는다
    - 트랜잭션 안에서는 같은 쿼리를 여러 번 실행해도 항상 같은 결과를 본다
- 즉 각 트랜잭션은 자기만의 과거 시점 데이터베이스를 보고 일한다 라고 이해하면 된다

**MongoDB 의 Snapshot Isolation**

- MongoDB(WiredTiger 스토리지 엔진)은 다음과 같은 구조를 가진다
    - 하나의 문서에 대해 여러 버전(version)을 유지
    - 각 쓰기 작업은 이전 버전을 덮어쓰는 것이 아닌 새로운 버전을 생성
- 이후 트랜잭션 시작 (`session.startTransaction()`) 시 `read timestamp` 가 고정된다
    - 트랜잭션이 시작되면 MongoDB 는 내부적으로 다음을 기록한다
    - 해당 트랜잭션이 어떤 시점의 커밋된 데이터까지 볼 수 있는지 → 즉 해당 트랜잭션의 가시성 경계를 기록
    - 해당 경계 이후에 생성된 데이터 버전은 커밋되었든 아니든 해당 트랜잭션에서는 절대 보이지 않는다
- 읽기 시 항상 스냅숏 기준 필터링 수행
    - 트랜잭션 내부에서 문서를 읽을 때 해당 문서의 여러 버전 중에서 트랜잭션의 read timestamp 이전에 커밋된 버전만 선택하고 나머지는 전부 무시한다
    - 이 때문에 같은 문서를 여러 번 읽어도 결과가 동일하고 다른 트랜잭션의 변경으로 결과가 바뀌지 않는다

> 예를 들어 서비스에 한 유저는 동시에 활성 쿠폰을 최대 1개만 가질 수 있다는 규칙이 있다고 가정
> 쿠폰이 별도 document로 저장되어 있고, 두 transaction이 동시에 실행된다
>
> - T1: user-1의 활성 쿠폰이 있는지 조회 → 없음
> - T2: user-1의 활성 쿠폰이 있는지 조회 → 없음
> - T1: coupon-A를 active로 insert
> - T2: coupon-B를 active로 insert
> - 둘다 서로 다른 document를 insert하므로 write conflict가 안 날 수 있음
> - 결과: user-1에게 active coupon이 2개 생김

- 각 transaction은 자기 snapshot 기준으로 활성 쿠폰이 없었다는 판단을 했다
- 하지만 전체 결과는 비즈니스 규칙을 깨뜨린다
    - 이것이 snapshot isolation의 대표적인 write skew 이다
- 이 문제를 해결하려면 transaction만 믿으면 안된다
- MongoDB 에서는 `userId + active` 같은 unique index 설계, 상태 문서 하나에 조건부 update를 거는 방식, 또는 유저별 쿠폰 상태를 하나의 document 안에 모으는 모델링을 고려해야 한다
- 즉 MongoDB 에서 consistency 를 지키려면 MVCC 격리 수준과 데이터 모델링을 같이 설계해야 한다

**Snapshot Isolation 을 트랜잭션에서만 제공하는 이유**

간단하게 정의하자면 Snapshot Isolation 은 비용이 발생한다

1. 오래된 버전 유지
    - 트랜잭션이 오래 실행되면 과거 버전을 삭제할 수 없음
2. 동시성 제약이 생김
    - Snapshot Isolation 은 읽기에는 강하지만 쓰기 충돌이 발생할 수 있다
    - MongoDB 는 이를 감지하면 트랜잭션을 롤백시킨다
    - 트랜잭션은 재시도 비용을 전제로 한 기능
3. 분산 환경에서 더 비싸다
    - replica set 전체에서 동일한 스냅샷 시점을 유지해야 하며 커밋 시 다수 노드의 합의가 필요
    - 그래서 standalone MongoDB 에서는 트랜잭션을 지원하지 않는다

</br>

## 몽고 DB 의 Cache, Flush, Dirty Page (WiredTiger 스토리지 엔진 기준)

- 쓰기 성능을 확보하기 위해 변경사항을 먼저 캐시에 반영
- 이후 Flush 와 Checkpoint 를 통해 디스크 일관성과 복구 기준점을 확정한다
- 즉 `Cache` → `Dirty Page` → `Flush` → `Checkpoint` → `WAL` 흐름순으로 동작

**동작 메커니즘**

- 애플리케이션이 `insert`, `update`, `delete` 를 요청하면 엔진은 우선 메모리 캐시의 페이지를 수정하고 해당 시점의 페이지는 디스크와 불일치하므로 Dirty Page 가 된다
- 이후 Dirty 비율, 주기, 시스템 상태 같은 조건이 충족되면 백그라운드 쓰기 작업이 Dirty Page 를 디스크 파일로 내리고 기록이 완료된 페이지는 Clean 상태로 전환된다
- 이 과정이 Flush 이다
- Checkpoint 는 Flush 보다 목적이 넓다
- Checkpoint 는 단순히 일부 Dirty Page 를 내리는 것이 아닌 특정 시점의 데이터 상태를 복구 기준점으로 사용 가능한 일관된 상태로 확정한다
- 그래서 장애 복구 시에는 Checkpoint 시점의 디스크 상태를 기준으로 이후 WAL 로그를 재적용해 정합성을 맞춘다

**Flush 와 Checkpoint 차이점**

- Flush 는 주로 캐시 압력을 낮추고 Dirty Page 를 줄이는 운영 동작이다
- 반면 Checkpoint 는 복구 기준점을 만드는 일관성 동작이다
- 두 기능 모두 디스크 기록을 포함하지만 Flush 는 성능/메모리 관리 성격이 강하고 Checkpoint 는 내구성/복구 경계 확정 성격이 강하다
- 즉 Flush 는 캐시를 정리하는 쓰기 배출, Checkpoint 는 복구 가능한 일관성 시점을 확정하는 작업

**실패 시나리오 관점**

- 장애가 Checkpoint 직전에 발생하면 최근 변경분 중 일부는 데이터 파일에 없을 수 있지만 WAL 이 남아있으면 복구 과정에서 재적용된다
- 다만 I/O 병목이나 Checkpoint 지연이 큰 환경에서는 복구 시간이 길어질 수 있으므로 Dirty Page 누적과 디스크 쓰기 지연을 운영 지표로 함께 관리해야 한다

</br>

### checkpoint 와 MVCC

- MVCC는 메모리에서 여러 버전을 관리한다
- 하지만 데이터베이스는 결국 디스크에 영속성 데이터를 남겨야 한다
- WiredTiger는 checkpoint를 통해 특정 시점의 일관된 snapshot을 data file에 기록한다
- MongoDB 문서에 따르면 WiredTiger는 데이터를 disk에 쓸 때 snapshot의 모든 데이터를 여러 data file에 일관되게 기록하고, durable해진 데이터는 checkpoint 역할을 한다
- checkpoint는 마지막 checkpoint까지 data file에 일관된 상태임을 보장하며, MongoDB는 기본적으로 WiredTiger checkpoint를 60초 간격으로 생성한다
- 새 checkpoint를 쓰는 동안 이전 checkpoint는 여전히 유효하고, 새로운 checkpoint는 WiredTiger metadata table이 원자적 업데이트되어 새로운 checkpoint를 참조하게 될 때 영구적인 접근이 가능하게 된다
- 즉 WiredTiger는 매 write마다 data file을 완전히 최신 상태로 동기화하지 않고 매 write 마다 모든 관련 page를 fsync 하면 매우 느리기 때문이다
- 대신 메모리와 journal을 사용하고, 주기적으로 checkpoint를 만들어 여기까지는 data file 만으로도 일관된 복구 지점이다는 기준점을 만든다
- 이 구조에서 MVCC가 중요한 이유는 checkpoint 역시 아무 값이나 마구 쓰면 안되기 때문이다
- checkpoint는 일관된 snapshot이어야 하며 어떤 key는 T100까지 반영하고 다른 key는 T90까지만 반영했는데 둘 사이의 transaction 관계가 깨져 있다면 복구 후 데이터가 논리적으로 깨질 수 있다
- 그래서 WiredTiger는 snapshot과 timestamp, stable timestamp 같은 개념을 통해 checkpoint에 들어갈 수 있는 안정된 버전과 아직 임시 데이터의 버전을 구분한다
- 이것이 MongoDB replication rollback과도 연관된다
- Primary 였던 노드가 다른 노드들과 네트워크가 끊긴 상태에서 write를 받았는데 그 write가 majority commited 되지 못했고, 이후 다른 노드가 새 Primary가 되면 기존 Primary의 일부 변경은 rollback되어야 할 수 있다
- WiredTiger의 timestamp, stable timestamp, oplog, checkpoint, journal은 이 복구/rollback 흐름의 기반이 된다

</br>

### journal 과 MVCC

- MVCC가 누가 어떤 버전을 볼 수 있는가를 해결한다면, journal은 장애 후 어떤 변경을 복구할 수 있는가를 해결한다
- MongoDB 문서에 따르면 WiredTiger는 checkpoint와 함께 write-ahead log, 즉 journal을 사용해 durability를 보장한다
- journal은 checkpoint 사이의 모든 data modification을 보존하고 MongoDB가 checkpoint 사이에 종료되면 마지막 checkpoint 이후 변경을 journal로 replay한다
- MongoDB 프로세스가 write를 처리할 때 변경은 먼저 WiredTiger cache에 반영된다
- 그다음 durability가 필요한 변경은 journal record로 만들어지며 이 journal write는 운영체제의 page cache를 거쳐 storage device로 내려간다
- 단순히 `write()` 시스템콜이 반환되었다고 해서 물리 NAND flash에 영구 반영되었다고 단정할 수는 없다
- 커널 page cache, 파일 시스템 journal, block layer, storage controller cache, NVMe/SATA device cache가 사이에 있기 때문이다
- 그래서 DB는 fsync 또는 fdatasync 계열 flush, storage barrier, write ordering을 통해 로그가 먼저 durable해진 뒤 page data가 나중에 checkpoint로 내려가도 복구 가능하다는 전제를 세운다
- 정리하면 journal은 MVCC 의 버전 선택 규칙 자체는 아니지만 MVCC가 만든 변경들이 장애 후에도 commit 된 상태로 복구될 수 있게 만드는 내구성 계층이다

</br>

### eviction, reconciliation, checkpoint 와 MVCC

- WiredTiger는 B-tree 기반 저장 구조를 사용한다
- 데이터와 인덱스 page는 WiredTiger cache에 올라오고, update가 발생하면 page와 update chain이 메모리에서 바뀐다
- 하지만 cache는 무한하지 않으므로 언젠가 page를 내보내야 한다
    - 이 과정을 eviction이라고 한다
- MongoDB의 WiredTiger 문서는 cache 사용량이 target보다 낮으면 eviction이 발생하지 않고, target 이상 trigger 미만이면 internal thread가 eviction을 수행하며, trigger 이상이면 application thread가 자기 작업을 멈추고 eviction에 동원될 수 있다고 설명한다
- 또한 large, long-running transaction은 cache pressure 상황에서 application thread가 eviction에 동원되어 latency가 증가하는 문제와 연결된다고 설명한다
- Eviction이 단순히 메모리 page를 버리는 것이라면 쉬울 것 같지만 MVCC 때문에 복잡해진다
- 어떤 page에 여러 key가 있고 각 key마다 여러 버전이 있을 수 있다
- Eviction/reconciliation 과정은 이 page를 disk에 어떤 형태로 기록할 것인가, 어떤 최신 버전을 disk image로 만들 것인가, 어떤 오래된 버전을 history store로 옮길 것인가, 아직 active reader가 필요로 하는 버전을 버리면 안 되는가를 판단해야 한다
- 예를 들어 어떤 page 안에 `user-1`, `user-2`, `user-3`이 있고 각 user document가 여러 번 갱신되었다고 가정하자
- 최신 reader에게는 최신 값만 필요하지만, 오래된 transaction이 아직 read timestamp 100으로 열려 있으면 timestamp 100 기준의 값도 필요하다
- 이때 eviction은 최신 값만 disk에 쓰고 옛 버전은 버릴 수 없다, 옛 버전은 history store에 남겨야 할 수 있다
    - 이 작업이 많아질수록 eviction 비용이 증가하게 된다
- 운영에서 이 구조는 매우 중요하다, 오래 걸리는 transaction이나 cursor가 많으면 `oldest timestamp` 또는 pinned timestamp가 앞으로 가지 못한다
- WiredTiger timestamp 문서는 pinned timestamp가 oldest timestamp와 현재 실행 중인 transaction들의 read timestamp 중 최소값이며, 오래된 데이터를 drop하거나 garbage collect할때 실제 lower bound로 사용된다고 설명한다
- 즉 오래된 reader 하나가 나는 아직 timestamp 100을 보고 있다 라고 붙잡고 있으면, WiredTiger는 timestamp 100 이후의 과거 버전들을 함부로 정리하지 못한다
- 이 상태에서 write가 계속 들어오면 history store와 cache가 커지고, eviction이 어려워지고, 결국 application thread stall로 이어질 수 있다
