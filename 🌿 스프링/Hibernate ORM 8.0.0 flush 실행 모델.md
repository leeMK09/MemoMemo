## Hibernate ORM 8.0.0.Beta2 flush 실행 모델의 교체

- 아직 개발 버전이며 정식 릴리즈 전에 호환되지 않게 바뀔 수 있다고 명시됨
- [8.0.0.Beta2 릴리즈 노트](https://github.com/hibernate/hibernate-orm/releases/tag/8.0.0.Beta2)

### Hibernate 6.4 의 flush

- Hibernate 6.4 에서는 대략 다음 경로로 flush가 진행된다
    1. 트랜잭션 commit 또는 명시적 flush
    2. `DefaultFlushEventListener`
    3. `AbstractFlushingEventListener.flushEverythingToExecutions()`
    4. 엔티티 dirty checking + 컬렉션 변화 감지
    5. `EntityInsertAction / EntityUpdateAction / CollectionUpdateAction` 생성
    6. `ActionQueue`에 적재
    7. `AbstractFlushingEventListener.performExecutions()`
    8. `ActionQueue.prepareActions()`
    9. `ActionQueue.executeActions()`
    10. JDBC batching 및 SQL 실행
- `ActionQueue`는 애플리케이션에서 `persist()`, `remove()`를 호출한 순서대로 SQL을 그대로 실행하지 않는다
- 6.4에서는 내부 `OrderedActions` 순서에 따라 대략 다음과 같이 처리된다

**OrderedActions 순서**

1. orphan collection 제거
2. orphan entity 제거
3. entity INSERT
4. entity UPDATE
5. collection queued operation
6. collection DELETE
7. collection UPDATE
8. collection INSERT
9. entity DELETE

- 이 순서는 FK 위반 가능성을 낮추기 위한 전역적인 휴리스틱이다
- `hibernate.order_inserts` 와 `hibernate.order_updates`를 사용하면 같은 종류 안에서 다시 정렬해 JDBC 배치 적중률을 높일 수 있지만, 실제 데이터베이스 스키마의 모든 FK/유니크 관계를 완전하게 모델링하는 것은 아니다
- 그러므로 다음과 같은 복잡한 변경에서는 문제가 생길 수 있다
    - A가 B를 참조
    - B가 C를 참조
    - C의 유니크 키를 기존 행에서 새 행으로 이동
    - 컬렉션에서는 기존 연결을 삭제하고 새 연결을 삽입
- 전역 순서만으로는 어떤 UPDATE가 먼저 실행돼야 유니크 키가 비는지, 어떤 INSERT 전에 어떤 FK가 준비돼야 하는지 까지 정확하게 판단하기 어렵다
- 그래서 예상하지 못한 FK 위반, 유니크 키 충돌 또는 배치 분절이 생길 수 있다

</br>

### Hibernate 8 의 graph-based flushing

- Hibernate 8은 dirty checking 자체를 없애는 것이 아니라, dirty checking 이후 만들어진 작업을 SQL로 실행하는 계획 단계에 그래프를 도입
    - dirty checking 결과
    - Entity / Collection 작업을 저수준 FlushOperation으로 분해
    - 테이블, FK, 유니크 키, 보조 테이블 관계로 의존성 그래프 생성
    - FlushPlanner가 실행 가능한 FlushPlan 생성
    - PlanStep 단위로 JDBC batching 및 SQL 실행
    - 사이클이 있으면 필요한 보정 UPDATE 실행

**중심 역할**

- `GraphBasedActionQueue` : 기존 `ActionQueue` 역할을 대체
    - [GraphBasedActionQueue](https://github.com/hibernate/hibernate-orm/blob/8.0.0.Beta2/hibernate-core/src/main/java/org/hibernate/action/queue/internal/GraphBasedActionQueue.java)
- `FlushCoordinatior` : 작업 분해, 그래프 작성, 계획 및 실행을 조율
    - [FlushCoordinator](https://github.com/hibernate/hibernate-orm/blob/8.0.0.Beta2/hibernate-core/src/main/java/org/hibernate/action/queue/internal/FlushCoordinator.java)
- `Decomposer` : `EntityInsertAction` 같은 고수준 작업을 테이블별 `FlushOperation`으로 분해한다
- `StandardGraphBuilder` : FK/유니크 제약조건을 의존성 간선으로 만듦
- `StandardFlushPlanner` : 실행 순서와 배치 가능한 작업을 `FlushPlan`으로 만듦
- `CycleBreakPatcher` : 순환 참조 때문에 한 번에 INSERT할 수 없는 경우 보정 작업을 만듦

**graph-based flushing**

- 모든 flush가 무조건 비싼 그래프 탐색을 거치는 것은 아니다
- `FlushCoordinator`에는 작업들이 서로 독립적일때 그래프 생성을 생략하고 직접 실행 계획을 만드는 fast path가 있다
- 복잡한 객체 그래프의 정확성을 높이되, 단순한 단일 엔티티 UPDATE까지 항상 그래프 비용을 지불하지 않도록 한 설계
    - [Hibernate 8](https://docs.hibernate.org/orm/8.0/whats-new/)
- 가장 직접적인 변화는 같은 서비스 코드를 실행해도 SQL 순서가 달라질 수 있다는 점이다
- 이것은 대체로 제약조건 위반 감소와 배치 효율 향상으로 이어질 수 있지만, 운영 관점에서는 다음과 같은 회귀 가능성이 생길 수 있다
- 기존에는 `EntityUpdateAction`끼리 같은 순서로 실행되면서 데드락이 우연히 회피됐는데, 새 계획에서는 테이블과 FK 관계에 따라 다른 순서로 락을 획득할 수 있다
- 반대로 현재 자주 발생하는 데드락이 줄어들 수도 있다
- 따라서 단순히 기능 테스트만 통과한다고 안전하다 볼 순 없고, 동일 데이터에 대한 동시 변경 테스트가 필요하다
- 또한 Hibernate 이벤트 리스너나 `Interceptor`, 컬렉션 post-event가 전체 flush 안에서 언제 호출되는가에 의존하는 코드가 있다면 순서가 달라질 수 있다
- Hibernate 8은 컬렉션 이벤트 완료 시점을 실제 의미 단위의 mutation 완료에 맞추도록 변경했고, 서로 관련 없는 컬렉션 간의 전역 이벤트 순서는 보장하지 않는다고 명시한다
- 문제가 발생할 경우 임시로 다음 설정을 사용해 기존 큐로 되돌릴 수 있다

```yaml
spring:
    jpa:
        properties:
            hibernate.flush.queue.type: legacy
```

**확인해야할 사항들**

- 현재 H2 테스트만으로는 MySQL 과 같은 실제 운영 DB 의 FK / 유니크 키 / 락 획득 순서 / 데드락 특성을 재현하기 어렵다
- Hibernate 8을 실제로 검토하는 시점에는 Testcontainers 기반 MySQL 통합 테스트를 별도로 두는 것이 좋다
- 특히 다음 시나리오를 기록해 두면 비교하기 용이하다
    - 하나의 트랜잭션에서 부모/자식 엔티티를 동시에 생성/수정/삭제하는 경우
    - `orphanRemoval=true` 컬렉션에서 기존 자식을 제거하고 새 자식을 추가하는 경우
    - 동일한 유니크 값을 기존 엔티티에서 제거한 뒤 다른 엔티티에 할당하는 경우
    - 서로 같은 주문/재고 행을 다른 순서로 갱신하는 동시 트랜잭션
    - `IDENTITY` 식별자를 사용하는 엔티티와 연관 엔티티를 함께 저장하는 경우
    - `@Version` 이 있는 엔티티와 컬렉션을 동시에 변경하는 경우
- 비교 대상은 SQL 개수만이 아니라 SQL 실행 순서, JDBC batch 실행 횟수, flush 소요 시간, 데드락 및 lock wait timeout 발생률이다
