# MongoDB/WiredTiger 의 intent lock

- **intent lock**은 실제로 데이터를 읽거나 쓰기 위해 잡는 최종 잠금이라기보단 상위 리소스 아래의 더 작은 리소스에서 읽기 또는 쓰기를 할 예정이라는 상위 계층에 표시하는 잠금이다
- MongoDB 공식 문서 또한 intent lock을 intent shared 또는 intent exclusive로 해당 리소스보다 더 세밀한 단위에서 읽거나 쓰기 위해 concurrency control을 사용할 것임을 나타내는 lock 이라고 설명한다
- MongoDB에서 이 개념이 필요한 이유는 MongoDB가 전역 인스턴스, 데이터베이스, 컬렉션 그리고 WiredTiger 내부의 document 수준까지 여러 계층의 리소스를 동시에 다루기 때문이다
    - MongoDB는 multi-granularity locking을 사용하여 global, database, collection 수준에서 잠금을 잡을 수 있고, 컬렉션보다 더 낮은 수준의 동시성 제어는 스토리지 엔진이 구현한다
    - WiredTiger는 document-level concurrency control을 제공한다

</br>

## intent lock이 필요한 이유

```text
mongod 프로세스 전체 (Global)
    → Database: test-db
        → Collection: receipts
            → Document: receipt-1
            → Document: receipt-2
            → Document: receipt-3
```

- 어떤 API 요청이 `receipts` 컬렉션 안의 `receipt-1` 문서 하나만 수정한다고 가정
- WiredTiger는 문서 수준에서 충돌을 제어할 수 있으므로, 이론적으로는 `receipt-1`만 보호하면 된다
- 그런데 동시에 다른 작업이 `test-db` 데이터베이스 전체를 `drop` 하려고 하거나, `receipts` 컬렉션에 대해 `index build`, `rename`, `collection drop` 같은 구조 변경 작업을 하려고 한다면 문제가 생긴다
- 문서 하나를 수정하는 작업은 아주 작은 리소스를 건드리지만 drop collection은 그 상위 리소스 전체를 건드린다
- 이때 상위 리소스 입장에서는 자신의 아래 어딘가에서 누군가 읽거나 쓰는 중인지를 알아야 한다
- 그런데 상위 리소스가 매번 하위 document 수백만 개를 전부 확인 후 누가 document lock을 잡고 있는지를 확인하는 비용은 너무 크다
- 그래서 MongoDB는 문서 하나를 수정하려는 작업이 실제 document를 수정하기 전에 상위 계층인 global, database, collection에 자신은 아래쪽에서 쓰기를 할 예정이다 라는 표시를 남긴다
- 이것이 `IX` 즉 `intent Exclusive lock` 이다
- 반대로 어떤 조회 작업이 컬렉션 안의 document들을 읽으려면, 상위 계층에 자신은 아래쪽에서 읽기를 할 예정이다 라는 표시를 남긴다
- 이것이 `IS` 즉 `intent Shared lock` 이다
- 즉 `intent lock` 의 목적은 상위 리소스를 직접 독점하려는 것이 아닌 상위 리소스에게 하위 리소스 사용 계획을 알려서 큰 범위의 잠금과 작은 범위의 잠금이 안전하게 공존하도록 만드는 것 이다

</br>

## S, X, IS, IX 구분

- MongoDB 문서에서는 `shared lock`, `exclusive lock` 외에도 `intent shared`, `intent exclusive` 가 있다고 설명한다
- `Shared lock` 은 읽기 작업을 위한 잠금이고, `Exclusive lock` 은 쓰기 작업을 위한 잠금이다
- `intent Shared` 와 `intent Exclusive` 는 더 세밀한 수준에서 읽거나 쓰려는 의도를 나타내기 위해 상위 수준에 잡는 잠금이다
- `S` 와 `X` 는 해당 리소스 자체를 실제로 읽거나 쓰기 위해 보호하는 잠금에 가깝다
- 반면 `IS` 와 `IX`는 해당 리소스 아래쪽의 더 작은 리소스에게 읽거나 쓰기를 할 예정이라는 표시이다
- 예를 들어 컬렉션 자체를 읽는 작업이 있다고 가정
- 이 작업은 컬렉션 자체에 `S` 를 잡을 수 있다
- 그러면 다른 작업이 같은 컬렉션을 drop 하거나 exclusive하게 변경하는 것을 막을 수 있다
- 반대로 특정 document 하나를 수정하는 작업은 컬렉션 전체에 `X`를 잡으면 너무 비효율적이다
- 해당 document 하나만 바꾸는데 컬렉션 전체를 막으면 다른 document에 대한 쓰기까지 모두 막히기 때문이다
- 그래서 이 작업은 상위 리소스에는 `IX`를 표시하고 실제 document 수준의 충돌 제어는 WiredTiger 가 처리한다

```text
S  = 이 리소스를 공유 모드로 실제 읽는다
X  = 이 리소스를 배타 모드로 실제 쓴다
IS = 이 리소스 아래의 더 작은 리소스를 읽을 예정이다
IX = 이 리소스 아래의 더 작은 리소스를 쓸 예정이다
```

- 다만 MongoDB/WiredTiger 조합에서는 document 수준에서 MongoDB lock manager 가 전통적인 `S/X document lock`을 직접 관리한다기보다는 컬렉션 이하의 실제 document-level 충돌 제어를 WiredTiger가 MVCC 와 optimistic concurrency control로 처리한다고 이해하는 것이 좋다
- MongoDB 문서 또한 WiredTiger 가 대부분의 read/write 작업에 optimistic concurrency control을 사용하고, MongoDB가 global/database/collection 수준에서 intent lock을 사용한다고 설명한다

</br>

## 문서 하나를 update할 때 실제 잠금 흐름

```javascript
db.receipts.updateOne({ _id: "receipt-1" }, { $set: { state: "COMPLETED" } });
```

- 예를 들어 애플리케이션이 위 쿼리를 실행한다고 가정하자
- 이 작업은 `receipts` 컬렉션 자체를 독점할 필요가 없다
- `receipt-1` 문서 하나만 바꾸면 된다, 이때 MongoDB 서버 계층은 먼저 상위 리소스에 intent lock을 잡는다

```text
Global     : IX
Database   : IX
Collection : IX
Document   : WiredTiger가 document-level concurrency control로 실제 충돌 제어
```

- 여기서 Global에 `IX`를 잡는 이유는 이 mongod 인스턴스 아래의 어떤 데이터베이스/컬렉션에서 쓰기를 할 예정이다 라는 표시이다
- Database에 `IX` 를 잡는 이유는 이 database 아래의 어떤 collection에서 쓰기를 할 예정이다 라는 표시이다
- Collection에 `IX` 를 잡는 이유는 이 collection 아래의 어떤 document에서 쓰기를 할 예정이다 라는 표시이다
- 이 표시가 있으면 동시에 누군가 `receipts` 컬렉션 자체를 drop하거나 exclusive 하게 변경하려고 할 때 MongoDB lock manager가 충돌을 감지할 수 있다
- `dropCollection` 같은 작업은 컬렉션 자체에 `X` 가 필요할 수 있는데, 이미 그 컬렉션에 `IX`가 잡혀 있으면 하위에서 쓰기 작업이 진행 중이므로 지금 컬렉션 전체를 독점하면 안된다고 판단할 수 있다
- MongoDB 공식 문서도 예시로 컬렉션을 쓰기 위해 `X` 모드로 lock하려면 해당 데이터베이스 lock 과 global lock을 `IX` 모드로 잡아야 한다고 설명한다
- 또한 하나의 database는 `IS` 와 `IX` 를 동시에 가질 수 있지만, `X` lock은 다른 어떤 모드와도 공존할 수 없다고 설명한다
- 여기서 중요한 포인트는 일반적인 document update가 collection에 `IX`를 잡는다고 해서 컬렉션 전체에 대한 다른 document update가 막히는 것은 아니다
- `IX`끼리는 공존할 수 있다
- 그래서 여러 요청이 같은 collection 안의 서로 다른 document를 동시에 update할 수 있다
- 실제 같은 document 를 동시에 바꾸려는 충돌은 WiredTiger가 optimistic concurrency control로 감지한다
- 즉 `IX` 는 컬렉션 자체를 막는 쓰기 잠금이 아니고 `IX` 는 컬렉션 아래 어딘가에서 쓰기할 예정이라는 의도 표시이다

</br>

## 조회할 때는 어떻게 되는가?

```javascript
db.receipts.find({ userId: "user-1" });
```

- 이 조회는 document들을 읽는다
- 이때 MongoDB는 상위 리소스에 `IS` 를 잡는다
- 개념적으로는 다음과 같다

```text
Global   : IS
Database : IS
Collection: IS
Document : WiredTiger snapshot/MVCC로 visible version 선택
```

- 이 조회 작업은 컬렉션 전체를 exclusive하게 바꾸려는 작업과는 충돌해야 한다
- 이유는 읽는 도중 컬렉션이 drop 되거나 rename되면 안 되기 때문이다
- 하지만 다른 일반적인 document update와는 공존할 수 있어야 한다
- MongoDB와 WiredTiger 는 MVCC를 사용하기 때문에 reader는 writer가 만든 최신 버전을 기다리지 않고 자기 snapshot 기준으로 볼 수 있는 버전을 읽을 수 있다
- 그래서 `IS`와 `IX`는 함께 존재할 수 있다
- 한 작업은 아래에서 읽을 예정이고 다른 작업은 아래에서 쓸 예정이다
- 이 둘이 상위 resource 수준에서 공존할 수 있어야 MongoDB가 동시에 읽기와 쓰기를 처리할 수 있다
- 단 collection 전체에 `X`가 필요한 작업은 다르다
- `X`는 다른 모든 모드와 공존할 수 없다
- MongoDB 문서도 exclusive lock은 다른 어떤 lock mode와도 공존할 수 없고 shared lock은 intent shared와만 공존할 수 있다고 설명한다

**호환성 테이블**

```text
        IS     IX      S      X
IS      가능   가능    가능   불가
IX      가능   가능    불가   불가
S       가능   불가    가능   불가
X       불가   불가    불가   불가
```

- 이 표에서 가장 중요한 것은 `IS` 와 `IX` 가 서로 공존 가능하다는 점이다
- 이것이 일반적인 읽기와 쓰기가 같은 database/collection 아래에서 동시에 진행할 수 있게 해준다
- 또 하나 중요한 것은 `IX` 와 `S` 가 충돌한다는 점이다
- `S`는 해당 리소스 자체를 공유 모드로 읽겠다는 뜻이다
- 예를 들어 컬렉션 전체에 대해 일관된 공유 접근이 필요한 작업이 컬렉션에 `S`를 잡았는데 동시에 누군가 그 컬렉션 아래 document를 쓰겠다고 `IX`를 잡으면, 컬렉션 전체를 안정적으로 읽는다는 보장이 깨질 수 있다
- 그래서 `S` 와 `IX` 는 공존하지 않는다
- 마지막으로 `X` 는 모든 것과 충돌한다
- `X` 는 해당 리소스를 배타적으로 소유하겠다는 의미이기 때문에, 그 아래에서 읽으려는 의도도, 쓰려는 의도도, 해당 리소스를 실제 읽는 작업도 모두 막아야 한다

</br>

## intent lock 이 없다면?

- 사용자 A 의 요청이 `receipts` 컬렉션 안의 `receipt-1` 문서를 수정하고 있다
- 이 작업은 `document-level lock` 또는 WiredTiger 의 OCC만 사용한다고 가정한다
    - `WiredTiger OCC` : OCC (Optimistic Concurrency Control, 낙관적 동시성 제어)
        - lock 대기를 줄이고 동시성을 높임, 트랜잭션 진행 중에는 충돌을 검사하지 않고 데이터를 변경하다, 커밋 시점에 write-write 충돌이 발생했는지 확인하며 충돌이 있으면 트랜잭션을 중단하고 재시도한다
- 동시에 관지가 작업이 `receipts` 컬렉션을 drop하려고 한다
- 이때 drop 작업은 컬렉션 전체를 지워도 되는지 판단해야 한다
- 그런데 document-level 잠금만 있다면 drop 작업은 컬렉션 아래의 모든 document에 누가 작업 중인지 확인해야 한다
- 컬렉션에 document가 수천만 개라면 이 것은 불가능에 가깝다
- 또는 drop 작업이 document-level 작업을 모르고 그냥 진행하면, 한쪽은 document를 수정 중인데 다른 쪽은 컬렉션을 지우는 모순이 생긴다
- intent lock은 이 문제를 계층적으로 해결한다
- document update 작업은 상위 collection에 `IX`를 남긴다
- drop collection 작업은 collection 에 `X`를 잡으려 한다
- lock manager 는 `IX` 와 `X`가 충돌한다는 것만 보면 된다
- 하위 document를 일일이 확인하지 않아도 된다
- 즉 intent lock은 하위 리소스 잠금 상태를 상위 리소스에 요약해서 표시하는 장치이다
- 이 장치 덕분에 MongoDB는 document-level concurrency 와 collection/database-level 관리 작업을 동시에 안전하게 조율할 수 있다
