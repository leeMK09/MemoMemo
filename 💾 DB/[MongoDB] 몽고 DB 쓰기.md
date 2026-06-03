## RDB 보다 NoSQL, 특히 MongoDB 의 쓰기가 더 빠를까?

- 무조건 더 빠르다고 할 수 없다
- PostgreSQL 의 경우 batch insert, 적절한 index 설계, partitioning, WAL 튜닝, fsync 정책, connection pool, COPY 등을 잘 활용하면 매우 빠르다
- 반대로 MongoDB 에서도 인덱스가 많으며, document가 크고, trasaction 이 많고, `w:"majotiry"` 와 journaling durability를 강하게 요구하면 느려진다
- 특정 종류의 쓰기 workload에서는 MongoDB 가 RDB보다 빠르게 설계되기 쉬운 경우가 많을 순 있다

**스키마 유연성**

- 스키마 유연성 자체가 write path 의 일부 비용을 줄여주기 때문이다
- RDB는 테이블마다 정해진 컬럼, 타입, 제약조건, foreign key, unique constraint, check constraint 등을 강하게 유지한다
- insert/update 가 들어오면 DB는 해당 row가 테이블 정의에 맞는지, 참조 무결성이 깨지 않는지, unique index를 위반하지 않는지 등을 확인한다
- 이 검증은 데이터 정합성을 보장하는 강력한 장점이지만, 쓰기 시점에는 비용이다
- 반면 MongoDB는 기본적으로 flexible schema model 이다
- 필드가 없어도 되고, document마다 구조가 조금 달라도 된다
- MongoDB도 schema validation을 제공하지만, 기본 사용 방식은 RDB처럼 모든 관계와 타입을 강하게 고정하는 모델은 아니다
- 하지만 여기서 NoSQL은 스키마 검증이 없어서 빠르다라고만 이해하면 위험하다
- 스키마 검증이 약하면 쓰기는 빨라질 수 있지만, 잘못된 데이터가 들어올 가능성도 커진다
- 예를 들어 `userId` 가 어떤 document에서는 ObjectId이고, 어떤 document에서는 string이고, 어떤 document에서는 누락되어 있다면 당장은 insert가 빠를 수 있다
- 하지만 나중에 장애가 났을 때 특정 유저의 이력이 조회되지 않거나, aggregation에서 type mismatch가 발생하거나, 인덱스를 제대로 타지 못해 쿼리가 급격히 느려질 수 있다

**document model joint**

- RDB에서는 정규화를 통해 데이터를 여러 테이블로 나누고, 관계를 foreign key나 join으로 연결한다
- 이 모델은 중복을 줄이고 정합성을 강하게 유지하는 데 유리하다 하지만 쓰기 관점에서는 하나의 비즈니스 이벤트가 여러 테이블 insert/update로 분산될 수 있다
- 예를 들어 `receipts`, `receipt_items`, `stores`, `ocr_results`, `reward_histories` 같은 여러 테이블에 걸쳐 쓰기가 발생할 수 있다
- 반면 MongoDB에서는 조회와 쓰기 패턴에 맞춰 영수증 document 하나에 OCR 결과, item 목록, store snapshot, reward 상태를 함께 넣을 수 있다
- 그러면 하나의 document insert로 끝날 수 있고 이 경우 단일 문서 원자성을 활용할 수 있다
- RDB에서 여러 테이블과 여러 인덱스를 갱신해야 하는 작업을 MongoDB에서는 하나의 document와 필요한 소수의 index 갱신으로 끝낼 수 있다
- 하지만 트레이드 오프도 분명하다 document 안에 중복 데이터를 많이 넣으면 나중에 원본 정보가 바뀌었을 때 여러 document를 갱신해야 한다
- 예를 들어 상점 이름을 receipt document 마다 snapshot 으로 넣었다면, 상점명이 변경되어도 과거 영수증에는 예전 이름이 남아있는다
- 이것이 의도된 snapshot이면 괜찮지만, 항상 최신 상점명을 보여줘야 한다면 데이터 불일치가 된다
- MongoDB 설계에서는 중복을 허용해서 쓰기와 읽기를 빠르게 할 것인가, 정규화해서 정합성을 중앙에서 관리할 것인가를 도메인별로 선택해야 한다

**MongoDB의 write concern을 낮게 잡으면 클라이언트가 빨리 응답을 받을 수 있다**

- `w:1`에서는 Primary가 write를 처리하면 클라이언트에게 성공 응답을 줄 수 있다
- 반면 `w:"majority"`에서는 다수의 data-bearing member가 acknowledge할 때까지 기다리므로 느려질 수 있다
- 여기서 MongoDB가 빠른 것이 아니라, durability/rollback risk를 낮은 latency와 교환한 것 이다
- 운영 장애 관점에서는 이 차이가 중요하다
- 서비스에서 운영 로그처럼 나중에 재처리 가능하고 일부 지연이 허용되는 데이터는 `w:1`로 빠르게 받아도 될 수 있다
- 하지만 포인트 잔액, 결제, 정산, 출금 같은 데이터는 `w:"majority"` 또는 그에 준하는 보상을 고려해야 한다
- 빠른 write만 보고 모든 데이터를 낮은 write concern으로 쓰면 Primary 장애나 network partition 상황에서 성공 응답을 받은 데이터가 나중에 사라진 것처럼 보이는 문제가 발생할 수 있다

**수평 확장과 shard key**

- RDB도 샤딩할 수 있지만, 전통적인 RDB에서는 cross-shard transaction, join, foreign key, global secondary index 같은 문제가 매우 복잡해진다
- MongoDB는 처음부터 document 단위 분산 저장과 shard key 기반 라우팅을 주요 확장 방식으로 제공한다
- 쓰기 요청이 좋은 shard key를 통해 여러 shard에 고르게 분산되면 전체 write 처리량은 증가할 수 있다
- 예를 들어 `receiptId` 나 hash 기반 shard key로 영수증 insert 가 여러 shard에 분산되면 하나의 Primary가 모든 write를 받는 구조보다 확장성이 좋아진다
- 하지만 shard key를 잘못 잡으면 오히려 MongoDB에 더 위험해진다
- 예를 들어 timestamp를 shard key로 잡으면 최신 write가 특정 shard에 몰릴 수 있다
- 특정 userId에 쓰기가 몰리는 서비스에서 userId만 shard key로 잡으면 heavy user 또는 봇 유저가 하나의 shard를 뜨겁게 만들 수 있다
- 장애 대응 관점에서는 MongoDB는 샤당하면 쓰기가 빨라진다가 아닌 write 분산이 좋은 shard key를 선택했을 때만 수평 확장이 유효하다로 이해하자
