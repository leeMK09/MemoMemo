## WiredTiger 의 MVCC

- WiredTiger의 MVCC는 하나의 key/document에 대해 여러 시점의 value 버전을 유지하고, 각 읽기 작업이 자기 transaction snapshot 또는 read timestamp 기준으로 보여도 되는 버전만 선택해서 읽도록 만드는 동시성 제어 방식이다
- WiredTiger는 MVCC를 사용하며, operation 시작 시점에 point-in-time-snapshot을 제공한다
- 이 snapshot은 메모리상의 데이터에 대해 일관된 view를 제공하고, WiredTiger는 checkpoint를 만들때도 특정 snapshot의 데이터를 여러 data file에 일관되게 기록한다
- 핵심은 읽기 작업이 쓰기를 막지 않는다는 것 이다
- RDB에서 흔히 lock 기반으로 생각하면, 어떤 writer가 document를 수정하는 동안 reader가 기다릴 것 같지만, MVCC에서는 reader가 굳이 최신 값을 기다리지 않는다
- reader는 내가 읽기 시작한 시점에 보였어야 하는 과거 버전을 찾아 읽는다
- 그래서 writer는 새 버전을 만들고, reader는 자기 snapshot 기준으로 적절한 옛 버전을 고른다
- 다만 lock이 아예 없다는 의미는 아니며 MongoDB 문서에 따르면 WiredTiger는 write operation에서 document level concurrency control을 사용하고, 대부분의 read/write operation에서 optimistic concurrency control을 사용한다
- Global, database, collection 레벨에서는 intent lock을 사용하며, storage engine이 두 작업의 충돌을 감지하면 하나의 작업이 write conflict를 겪고 MongoDB가 해당 작업을 재시도할 수 있다
- 즉 WiredTiger는 큰 범위의 lock으로 모든 작업을 순서대로 세우는 방식이 아닌 가능한 한 여러 작업을 동시에 진행시키고 실제 같은 document/version을 동시에 바꾸려는 순간에 충돌을 감지하는 방식이다

</br>

### WiredTiger 에서 버전은 어디에 존재하는가?

- MVCC를 이해할 때 가장 먼저 봐야 하는 것은 여러 버전이 실제로 어디에 저장되느냐이다
- WiredTiger에서 어떤 key의 버전은 크게 세 위치에 존재할 수 있다
- 첫 번째는 in-memory update chain이다
    - WiredTiger는 어떤 key에 대한 최신 변경들을 메모리 안에서 linked list 형태로 들고 있을 수 있다
    - WiredTiger 구조의 문서에 따르면 key를 읽을 때 WiredTiger는 먼저 그 key의 in-memory update들을 순회하고, 이 in-memory update들은 최신 update가 head에 있는 singly linked list, 즉 update chain으로 구성한다
- 두 번째는 disk image에 있는 버전이다
    - update chain에서 현재 reader에게 보이는 버전을 못 찾으면 WiredTiger는 disk page에 기록된 버전을 본다
    - 이 disk image는 이전 reconciliation 과정에서 디스크에 쓰기로 선택된 버전이다
- 세 번째는 history store 이다
    - reader가 아주 오래된 snapshot을 읽고 있는데, 그 reader에게 필요한 옛 버전이 현재 메모리 update chain에 없고 disk image에도 없을 수 있다
    - 이때 WiredTiger는 history store를 조회한다
    - snapshot history가 `WiredTigerHS.wt` 파일에 유지되며 history window를 늘리면 오래된 수정 값들을 더 오래 유지해야 하므로 disk 사용량이 증가한다

> 정리: 하나의 key/document에 대한 버전 탐색 순서
>
> 1. 메모리 update chain
>
> - new update → older update → older update ...
>
> 2. disk image
>
> - 마지막 reconciliation/checkpoint 과정에서 disk page에 남은 버전
>
> 3. history store
>
> - 오래된 snapshot reader를 위해 별도로 보관된 과거 버전

**예시: point 누적**

- 예를 들어 `user:100` 문서의 포인트가 원래 100점이었고, 이후 120점, 150점으로 바뀌었다고 가정해보자
- 최신 reader는 150점을 읽어야 한다
- 하지만 어떤 오래된 transaction은 자신이 시작된 시점의 snapshot 때문에 100점 또는 120점을 읽어야 할 수 있다
- WiredTiger는 이때 key 하나에 대해 최신 값 하나만 덮어쓰기 하지 않고, 여러 버전 중 reader에게 visible한 버전을 골라준다
- 중요한 점은 MongoDB의 document는 결국 WiredTiger 내부에서는 BSON value를 가진 record로 저장되고, WiredTiger는 record/key 단위로 version visibility를 판단한다는 것 이다
- MongoDB 레벨에서는 document를 읽는다고 이해하지만 storage engine 레벨에서는 B-tree page 안의 key/value record에 대해 현재 transaction이 볼 수 있는 update를 찾는다에 가깝다

</br>

### 읽기 요청이 들어오면 WiredTiger는 어떤 기준으로 버전을 고르는가?

- WiredTiger의 MVCC는 단순히 가장 최근 버전을 읽는 것이 아닌 읽기 transaction은 자기 snapshot을 갖고 있고, WiredTiger는 각 update가 이 snapshot에 포함되는지를 판단한다
- WiredTiger transaction 문서에 따르면 transaction이 snapshot을 가지고 있으면 각 read는 update의 transaction id가 snapshot 안에 있는지 확인한다 이후 snapshot 안에 있는 transaction id나 snapshot의 가장 큰 transaction id 보다 큰 transaction id를 가진 update는 reader에게 보이지 않는다
- 즉 어떤 reader가 시작될 때 WiredTiger는 현재 진행 중이던 transaction들과 내가 읽기 시작한 시점 이후에 생긴 transaction들을 구분할 수 있는 snapshot 정보를 잡는다
- reader는 그 snapshot 기준으로 이미 commit되어 있던 변경은 볼 수 있지만, 그 시점에 아직 진행 중이던 transaction의 변경이나 나중에 시작된 transaction의 변경은 볼 수 없다

**예시로 알아보기**

```json
// 예를 들어 transaction id(tx id) 흐름이 아래와 같다고 해보자

T10 : 이미 commit 됨
T11 : reader 시작 시점에 아직 진행 중
T12 : reader 시작 후 시작됨

```

- 이 reader는 T10의 변경은 볼 수 있다 하지만 T11은 reader 가 시작될 때 아직 commit되지 않은 transaction 이었기 때문에, 나중에 commit 되더라도 이 reader의 snapshot 안에서는 보이지 않는다
- T12는 reader보다 나중에 시작된 transaction이므로 당연히 보이지 않는다
- 이것이 snapshot isolation 이며 WiredTiger 문서는 snapshot isolation에서 transaction은 transaction 시작 전에 commit된 record version을 읽고, dirty read와 non-repeatable read는 발생하지 않지만 phantom read는 가능하며, snapshot isolation은 serializable과 같지 않다고 설명한다
- 특히 서로 다른 데이터를 갱신하는 두 transaction이 각자 상대방 시작 전 상태를 읽고 모두 commit되면, 어떤 직렬 실행으로도 설명되지 않는 write skew가 발생할 수 있다
- 즉 MongoDB의 transaction을 사용한다고 해서 모든 동시성 이상 현상이 사라지는 것은 아니다, 특히 두 사람이 동시에 같은 조건을 읽고 서로 다른 document를 insert/update하는 경우에는 unique index나 별도 constraint 설계가 필요할 수 있다

</br>

### timestamp != transaction id

- WiredTiger MVCC를 제대로 이해하려면 transaction id (tx id) 와 timestamp를 구분해야 한다
- tx id 는 WiredTiger 내부에서 동시 transaction의 visibility를 판단하는 데 사용된다
- 어떤 update가 reader가 시작될 때 이미 commit된 것인지, 아직 진행 중이던 것인지, reader 이후에 생긴 것인지 판단하는 데 관여한다
- 반면 timestamp는 MongoDB가 WiredTiger에게 넘겨주는 논리적 시간에 가깝다
- WiredTiger timestamp 문서에 따르면 timestamp는 wall-clock time과 반드시 같지 않은 application time이며 WiredTiger는 timestamp를 64 bit unsigned integer로 다루고, 값 자체를 실제 시계 시간으로 해석하지 않는다
- WiredTiger 문서는 각 transaction에 read timestamp와 commit timestamp가 있으며, read timestamp는 read operation에 사용되는 application time이고 commit timestamp는 write operation에 사용되는 application time이 라고 설명한다
- 각 database item의 value 변경은 timestamp와 연결되고, value는 어떤 시작 시간부터 다음 overwrite/remove 전까지의 time window를 갖는다
- 즉 tx id 는 실행 시점의 동시성 판단에 가깝고 timestamp는 MongoDB 전체 복제/복구/majority commit/read concern 관점에서 이 변경이 어느 논리적 시점의 변경인가를 표현하는 데 가깝다
- 그래서 버전 선택은 개념적으로 아래와 같다

> reader 가 어떤 key를 읽는다
>
> 1. 최신 update부터 update chain을 따라간다
> 2. 각 update에 대해 transaction id 기준으로 visible한지 본다
> 3. timestamp가 있는 경우 read timestamp 이하인지 본다
> 4. 두 조건을 모두 만족하는 첫 번째 버전을 반환한다
> 5. 메모리 update chain에서 못 찾으면 disk image를 본다
> 6. 그래도 못 찾으면 history store를 본다

- 이 판단 때문에 MongoDB는 같은 document에 대해 여러 시점의 reader가 동시에 존재할 수 있다
- 최신 reader는 최신 commit timestamp의 값을 읽고, 오래된 snapshot reader는 history store에 보간된 옛 값을 읽는다

</br>

### update chain은 실제로 어떤 식으로 생기는가

**예시: 하나의 사용자 document가 있다고 가정**

```json
{
    "_id": "user-1",
    "point": 100
}
```

```json
// 처음에는 disk image에 point 100이 있다고 가정
disk image: user-1 → point=100
```

```json
// 이후 T20 transaction이 point를 120으로 바꾼다
// WiredTiger는 기존 disk image를 즉시 덮어써서 없애는 방식이 아니라 메모리 안에 새로운 update를 붙인다
update chain:
    [point=120, txn=T20, commitTs=20] → null

disk image:
    [point=100]
```

```json
// 그다음 T30이 point를 150으로 바꾸면 update chain의 head에 최신 update가 붙는다
update chain:
    [point=150, txn=T30, commitTs=30]
        → [point=120, txn=T20, commitTs=20]
        → null

disk image:
    [point=100]
```

- 이 상태에서 read timestamp 25로 읽는 transaction이 들어온다면?
- WiredTiger는 먼저 head인 point 150을 본다
- commit timestamp가 30이므로 read timestamp 25보다 미래이다
- 따라서 이 reader에게는 보이면 안된다 그 다음 point 120을 본다
- commit timestamp가 20이므로 read timestamp 25이하이다, transaction id 기준으로도 visible하다면 이 reader는 point 120을 읽는다
- 반대로 read timestamp 35인 reader는 point 150을 읽는다
- 이처럼 같은 시점에 서로 다른 reader가 서로 다른 값을 읽는 것이 MVCC의 핵심이다
- 여기서 중요한 운영 포인트는 오래된 reader가 계속 살아 있으면, WiredTiger는 그 reader가 필요로 할 수 있는 과거 버전을 함부로 버릴 수 없다
- 그래서 오래 열린 transaction 오래 도는 aggregation, 오래 유지되는 cursor는 history store와 cache에 압력을 준다
    - large, long-running transaction이 cache pressure를 만들 수 있고, transaction이 끝나기 전까지 해당 상태가 in-memory에 유지되어 evict될 수 없으며, cache 가 trigger 이상으로 올라가면 application thread가 eviction에 동원되어 latency가 증가할 수 있다
- 이 부분이 장애 포인트에서 자주 보이는 갑자기 MongoDB가 느려졌다의 원인이 될 수 있다
    - 단순히 CPU가 높아서가 아닌 오래 열린 snapshot이 과거 버전을 pinning하고 그 결과 WiredTiger cache가 비워지지 못하고, 결국 application thread가 자기 요청 처리 대신 eviction 작업에 끌려 들어가면서 응답 시간이 튀는 구조가 된다

### history store는 왜 필요한가?

- update chain이 계속 길어지면 메모리를 너무 많이 먹는다
- 그렇다고 오래된 버전을 바로 버리면 오래된 snapshot reader가 읽을 값이 사라진다
- 이 사이를 해결하기 위해 WiredTiger는 오래된 버전을 history store로 옮길 수 있다
- MongoDB 문서에 따르면 MongoDB는 snapshot history를 `WiredTigerHS.wt`파일에 유지한다
- `minSnapshotHistoryWindowInSeconds` 값을 늘리면 서버가 지정된 시간 동안 오래된 수정 값을 유지해야 하므로 disk 사용량이 증가한다
- 쉽게 이야기해서 history store는 오래된 snapshot reader를 위한 과거 버전의 창고이다
- 최신 버전은 update chain 이나 disk image에 남고, 오래된 버전은 history store로 이동할 수 있다
- 하지만 history store는 공짜가 아니다
- 오래된 snapshot이 많거나 update가 많은 workload에서는 history store가 커진다
- history store가 커지면 디스크 사용량이 늘고, 오래된 read가 많아질수록 history store 조회 비용이 생긴다
- 또한 checkpoint, eviction, reconciliation이 더 복잡해진다
- 예를 들어 `users` 의 point를 자주 갱신한다고 가정한다면 동시에 어떤 관리자 화면이 오래 걸리는 aggregation을 실행할때 오래된 snapshot을 붙잡고 있는다
- 그 사이 point update가 계속 발생하면 WiredTiger는 최신 point만 유지할 수 없고, 관리자 aggregation이 볼 수 있어야 하는 과거 버전들을 유지해야 한다
- 이 과거 버전들이 history store에 쌓이면 디스크와 cache에 부담이 생기게 된다

### writer는 기존 값을 어떻게 바꾸는가?

- MVCC 에서 writer는 개념적으로 기존 값을 직접 파괴하지 않고 새 버전을 만든다
- 물론 실제 물리 저장 구조에서는 B-tree page, memory buffer, reconciliation, checkpoint 과정이 얽혀 있으므로 항상 새 파일 위치에 append만 한다처럼 단순화할 수 없다
- 그러나 visibility 관점에서는 writer가 새 update record를 만들고, reader는 자기 snapshot 기준으로 기존 버전 또는 새 버전 중 하나를 선택한다
- MongoDB 레벨에서 사용자가 `updateOne({ _id: "user-1", }, { $inc: { point: 10 }})` 를 호출하면, MongoDB는 query predicate를 만족하는 document를 찾고, update modifier를 적용한 새 BSON 상태를 만들고, WiredTiger storage transaction 안에서 해당 record의 update를 생성한다
- 이 update는 즉시 모든 reader에게 보이는 것이 아니라, transaction commit 상태와 timestamp visibility 조건을 통과해야 보인다
- 만약 두 writer 가 같은 document를 동시에 바꾼다면 WiredTiger는 optimistic concurrency control을 사용하기 때문에 두 writer를 처음부터 큰 락으로 잡아두지 않는다
- 둘 다 진행할 수 있지만, 같은 document/version을 수정하려는 충돌이 감지되면 하나가 write conflict를 겪는다
- MongoDB 문서는 storage engine이 두 작업 사이의 conflict 를 감지하면 하나의 작업이 write conflict를 겪고 MongoDB가 해당 작업을 투명하게 재시도한다
- MongoDB가 내부적으로 재시도할 수 있는 범위가 있지만, transaction 전체나 애플리케이션 레벨의 부작용까지 자동으로 exactly-once으로 만들어주지는 않는다
