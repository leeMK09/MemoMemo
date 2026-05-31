# 레플리카 셋 동기화는 어떻게 하는가?

- MongoDB replica set은 하나의 Primary와 여러 Secondary가 같은 데이터셋을 유지하도록 구성된 묶음이다
- 애플리케이션의 일반적인 write는 Primary로 들어간다
- Primary는 write를 자기 데이터에 적용하고, 그 변경 내용을 oplog라는 capped collection 성격의 복제 로그에 기록한다
- Secondary는 Primary 또는 다른 Secondary를 sync source로 삼아서 oplog entry를 가져오고, 그 entry를 자기 데이터셋에 재적용한다
- Secondary 는 데이터셋을 복제할 때 Change Stream API를 구독해서 복제하는 것이 아닌 oplog를 가져와서 적용한다
    - Change Stream은 애플리케이션이 변경 이벤트를 받고 싶다고 할때 사용하는 API이다
    - Change Stream은 수동으로 oplog를 tailing하는 복잡성과 위험 없이 실시간 변경에 접근하게 해주는 기능이고, MongoDB는 change stream 이벤트 생성을 위해 oplog 정보를 사용한다

**복제 흐름**

- Primary가 어떤 document를 update하면 MongoBD는 그 변경을 데이터 파일에 반영할 준비를 하고, 동시에 oplog에 이런 작업이 발생했다는 항목을 남긴다
- 이 oplog entry는 단순한 SQL 문자열 같은 것이 아니라 Secondary가 재적용했을 때 같은 결과를 만들 수 있도록 구성된 idempotent operation 이다
    - 즉 oplog의 각 작업은 idempotent하며, 한 번 적용하든 여러 번 적용하든 대상 데이터셋에 같은 결과를 만들어야 한다
- 이때 문제는 네트워크가 끊겼다가 다시 붙거나 Secondary가 특정 oplog entry를 적용했는지 확신하기 어려운 경계 상황이 생길 수 있다
- 이때 동일한 oplog entry가 재시도되더라도 데이터가 두 번 증가하거나 두 번 삭제되는 식으로 망가지면 복제가 불가능하다
- 그래서 oplog entry는 Secondary가 안전하게 재적용할 수 있는 형태이여야 한다
    - 예를 들어 단순히 count를 +1 해라 라는 의미만 남기면 재적용 시 중복 증가 위험이 있으며 실제 복제 로그는 operation timestamp, term, namespace, operation type, object 정보 등을 통해 순서와 적용 대상을 판단할 수 있게 구성된다

## Secondary가 새로 추가되거나 데이터가 너무 오래되어 따라잡을 수 없는 경우

- 이 경우에는 initial sync가 필요하다
- initial sync는 단순히 현재 파일을 통째로 복사하고 끝나는 것이 아니다
- 새 Secondary는 sync source에서 전체 데이터를 복제해오고 그 사이 Primary에서는 계속 write 가 발생한다
- 따라서 initial sync가 시작된 시점 이후 발생한 변경을 놓치지 않으려면 데이터 복사 이후 그동안 쌓인 oplog를 다시 적용해야 한다
    - data synchronization이 initial sync와 ongoing replication 두 가지 형태로 나뉘며, initial sync 중에도 oplog를 사용해 데이터셋을 현재 replica set 상태에 맞춘다

## replication lag

- 운영에서 자주 발생하는 문제는 replication lag 이다
- Primary에는 write가 계속 들어오는데 Secondary가 oplog를 가져오거나 적용하는 속도가 느리면 Secondary는 점점 뒤쳐진다
- replication lag는 Primary의 operation과 Secondary가 oplog에서 해당 operation을 적용하는 것 사이의 지연이며, 과도한 lag는 빠른 Primary 승격을 어렵게 하고 분산 읽기의 일관성을 떨어뜨릴 수 있다
- 해당 lag 가 생기는 원인은 다양하다
    - Secondary의 디스크 IOPS가 부족할 수 있다
    - 인덱스가 많은 컬렉션에 write가 몰리면 Secondary도 oplog 적용 과정에서 인덱스를 함께 갱신해야 하므로 느릴 수 있다
    - Primary와 Secondary 사이 네트워크 대역폭이나 RTT가 나쁠 수 있다
    - Secondary에서 무거운 Read를 많이 수행해서 WriedTiger cache 와 디스크를 읽기 쿼리가 점유하고 있을 수도 있다
    - 또는 Primary가 순간적으로 대량 write를 받아 oplog가 빠르게 증가했는데 Secondary가 그 속도를 따라가지 못할 수도 있다
- 더 위험한 상황은 Secondary가 너무 오래 뒤처져서 oplog window 밖으로 밀려나는 경우이다
    - oplog는 무한히 보관되지 않는다
    - oplog가 오래된 항목을 덮어쓰기 시작했는데, 어떤 Secondary가 아직 그 항목을 가져가지 못했다면 그 Secondary는 더 이상 incremental하게 따라잡을 수 없다
    - 이 상태를 stale member라고 볼 수 있고, MongoDB 문서에도 member가 너무 뒤처져 Primary가 아직 복제하지 못한 oplog entry를 overwrite하면 stale 상태가 되며, 이 경우 데이터를 제거하고 initial sync를 다시 수행하는 방식 등으로 resync해야 한다

> 장애 시나리오
>
> - Primary가 초당 5천 건의 영수증 insert를 받고 있고, 각 영수증 document에는 OCR 결과와 긴 문자열 필드가 포함되어 있다
> - Secondary는 같은 oplog를 가져오지만 디스크가 느리고, 동시에 분석팀에서 Secondary에 무거운 aggregation query를 날리고 있다 이때 Secondary의 WiredTiger cache는 읽기와 쓰기 적용이 경쟁하게 되고, 디스크는 인덱스 갱신과 대량 read를 동시에 처리하게 된다
> - 그러면 Secondary의 apply 속도가 떨어지고 replication lag 가 커진다
> - 만약 이 상태에서 Primary가 죽으면, lag가 큰 Secondary는 최신 데이터를 충분히 갖고 있지 않기 때문에 Primary로 승격되더라도 일부 write가 rollback될 수 있거나, majority write concern을 사용하지 않은 write는 새 Primary에 존재하지 않을 수 있다
> - 여기서 write concern이 중요하다
> - `w:1`은 Primary가 자기 기준으로 write를 처리하면 성공 응답을 줄 수 있어서 latency가 낮다 하지만 Primary가 write를 받고 Secondary들이 충분히 복제하기 전에 장애가 나면 그 write는 rollback될 수 있다
> - 반대로 `w:"majority"`는 다수의 data-bearing member가 write를 acknowledge할 때까지 기다리므로 latency가 증가하지만, Primary 장애 시 rollback 가능성을 낮춘다
> - 즉 더 많은 member가 acknowledge할수록 Primary 장애 시 written data 가 rollback될 가능성이 낮아지지만, 높은 write concern은 클라이언트가 기다려야 하므로 latency를 증가시킬 수 있다
> - 읽기에서도 read preference와 read concern을 같이 확인해야 한다
> - 애플리케이션이 Secondary에서 읽도록 설정하면 Primary 부하를 줄일 수 있지만, replication lag 때문에 방금 쓴 데이터를 못 읽을 수 있다
> - 영수증 포인트 지급 서비스의 경우 사용자는 영수증을 올리고 포인트가 바로 반영되길 기대하는 화면이라면 Secondary read는 위험할 수 있다
> - 반대로 관리자 통계, 추천 후보, 대시보드처럼 몇 초 지연이 허용되는 데이터라면 Secondary read로 Primary 부하를 줄일 수 있다
> - 즉 replica set 은 단순히 HA만 제공하는 것이 아니라 consistency 와 latency 사이의 선택지를 제공한다

**Change Stream**

- Change Stream은 majority commit 과 관련이 있다
- MongoDB 문서에 따르면 `Mongo.watch()` 는 majority of data-bearing members에 persisted된 데이터 변경만 알린다
- 또한 replica set에 arbiter가 있고 data-bearing member가 충분하지 않아 operation이 majority commited될 수 없는 경우 change stream이 열려 있어도 알림을 보내지 않을 수 있다
- 이 말은 outbox + Change Stream 구조에서 장애 대응 포인트가 명확하다는 의미이다
- Change Stream worker가 죽었다가 재시작할 때 resume token을 안전하게 저장해야 한다
- worker가 이벤트를 외부 시스템에 발행한 뒤 resume token을 저장하기 전에 죽으면 같은 이벤트가 다시 발행할 수 있다
- 반대로 resume token을 먼저 저장하고 외부 발행전에 죽으면 이벤트를 잃을 수 있다
- 그래서 outbox event 자체에 `eventId`, `status`, `publishedAt`, `retryCount`, `lastError` 같은 필드를 두고 외부 발행은 idempotent하게 설계해야 한다
- Change Stream은 단순히 이벤트를 감지하는 통로이며 외부 시스템까지 exactly-once delivery를 자동 보장하지 않는다

**운영 관점에서 봐야하는 replica set 장애 포인트**

- 운영 관점에서 replica set 장애를 줄이려면 몇 가지 기준을 반드시 봐야한다
- 첫째, `rs.status()` 나 `db.getReplicationInfo()`로 replication lag, oplog window, member state를 확인해야 한다
- MongoDB 문서에서도 `db.getReplicationInfo()`가 oplog에 poll한 데이터를 사용해 replica set 상태 정보를 반환하며 replication 진단에 사용할 수 있다
- 둘째, oplog size와 최소 보존 시간을 write volume에 맞게 설정해야 한다
    - MongoDB는 `replSetResizeOplog`로 oplog 크기 또는 minimum retention period를 조정할 수 있지만, 줄일 경우 오래된 entry가 truncate되어 클라이언트가 읽던 oplog entry가 사라지는 등 문제가 생길 수 있다
- 셋째, Secondary에 무거운 분석 쿼리를 직접 날리는 구조는 조심해야 한다
    - 분석 workload가 replica apply 를 방해하면 HA 목적으로 둔 Secondary가 오히려 장애 시점에 쓸모없어질 수 있다

**정리**

- Replica Set에서 복제 DB가 리더 DB의 Change Stream 이벤트를 구독하는 방식이 아닌 oplog 복제를 통해 적용한다
- Change Stream 은 애플리케이션이 변경 이벤트를 구독하기 위한 API이고 내부적으로 oplog 정보를 사용한다
- 즉 복제 → oplog 기반, 애플리케이션 이벤트 감지 → Change Stream 기반 으로 구분해야 한다
