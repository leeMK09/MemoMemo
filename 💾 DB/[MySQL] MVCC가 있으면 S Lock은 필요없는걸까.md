MVCC 는 "잠금 없는 읽기" 가 가능하다고 설명되지만,
그렇다고 해서 항상 **S Lock(Shared Lock)** 이 필요 없는 건 아닙니다.

<br>

> 요약
>
> - **MVCC는 일반적인 "SELECT" 에서 일관된 읽기를 보장하며 잠금이 필요 없다.**
>
> - 하지만 **특정한 목적이 있는 SELECT** 는 여전히 **S Lock** 이 필요하다.
>
> - **의도적인 잠금 (쓰기 방지), 외래키 제약조건 보장 을 위해 S Lock 은 필요합니다**
>
> → 특히 "락 기반의 동시성 제어"가 필요한 경우!

</br>

## 🤔 MVCC 에서 S Lock 이 필요 없는 경우

MVCC 가 도입된 목적은 바로 **잠금 경합 없이 읽기 처리 성능을 높이기 위함** 이예요 </br>
그래서 일반적인 `SELECT` 는 다음 조건이면 **락을 걸지 않습니다**

- 트랜잭션 격리 수준이 `REPEATABLE READ` 이상 이면서
- 단순 조회용 `SELECT` 이면서
- `FOR UPDATE` , `LOCK IN SHARE MODE` , `FOR SHARE` 같은 키워드가 없을때

이때는 **undo log** 기반의 **snapshot version** 을 따라가므로 **락을 안 걸고 읽기 가능합니다**

</br>

---

## 그런데 왜 S Lock이 필요할까?

### 1. 의도적 잠금을 통해 읽기 일관성 + 쓰기 방지 목적

```mysql
SELECT * FROM product WHERE id = 10 LOCK IN SHARE MODE;

또는

SELECT * FROM product WHERE id = 10 FOR SHARE;
```

이건 단순히 읽는 것이 아닌 </br>

"지금 이 데이터를 내가 참조하고 있으니, 다른 트랜잭션이 수정하지 말라" 는 **읽기 잠금 목적(S Lock)** 입니다 </br>
즉, 자기 자신은 읽을 수 있지만, 다른 트랜잭션의 **X Lock (Exclusive Lock)** 은 막기 위함 입니다

이건 MVCC 가 보장해주는 snapshot read 와는 다르게 **현재 상태를 보호하는 락** 이예요

> Snapshot Read 는 읽을 때 `trx_id` 를 통해 해결
>
> Shared Lock은 실제 충돌 방지 목적이 있음 (예: 외래키 체크, 업데이트 전 읽기 등)

</br>

### 2. 외래키 제약조건 보장

외래키 (foreign key) 제약은 참조 무결성을 지켜야 하므로, 참조하는 쪽 (부모 row)이 삭제되거나 수정되면 안됩니다.

```mysql
SELECT id FROM parent WHERE id = 1 FOR SHARE;
```

- 이럴 때 **S Lock 을 걸어놔야** 다른 트랜잭션이 해당 데이터를 `DELETE` 하지 못함
- MVCC 만으로는 이런 제약을 보장하지 못하므로 락을 활용해야 함

</br>

### 3. Serializable 격리 수준에서는 MVCC 대신 락 기반 제어 사용

`SERIALIZABLE` 모드에서는 **모든 SELECT 가 S Lock 을 동반** 합니다

이건 Snapshot 만으로는 순차 실행처럼 보장할 수 없기 때문에 실제 다른 트랜잭션이 접근 못 하도록 S Lock 을 강제합니다

</br>

---

## 🌟 결론

- MVCC 는 기본 SELECT 에서 S Lock 을 사용하지 않음 (잠금 없는 일관된 읽기 보장)
- 하지만 "현재 데이터의 상태 보호" 가 필요할 때는 S Lock 을 명시적으로 사용해야 함
- 외래키, 참조 무결성, Serializable 모드 등은 MVCC 로 대체할 수 없기 때문에 락이 필요
