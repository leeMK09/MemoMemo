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
