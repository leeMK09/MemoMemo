# 3 가지 관점에서

1. AutoCommit = false
2. AutoCommit = true
3. Select For Update 쿼리일 때

예시.

같은 Row 혹은 컬럼에 대해 한 세션에서 `UPDATE name = 'lmk'`을 수행중이고, 다른 세션에서 동시에 `SELECT name` 을 수행할 때 어떻게 되는것인가?

→ 이를 다음 **3가지 관점**에서 설명해보자

(InnoDB, 기본 격리 수준: `REPEATABLE READ` 기준)

- 테이블 : `user(id INT, name VARCHAR)`
- 초기값 : `id = 1, name ='test'`
- 세션 1에서
  - `UPDATE user SET name = 'lmk' WHERE id = 1;`
- 세션 2에서
  - `SELECT name FROM user WHERE id = 1;`

</br>

## [1] 트랜잭션을 열고 수행한 경우

**세션1**

```sql
START TRANSACTION;
UPDATE user SET name = 'lmk' WHERE id = 1;

-- COMMIT 안함
```

</br>

**세션2**

```sql
START TRANSACTION;
SELECT name FROM user WHERE id = 1;
```

</br>

### 결과

- 세션 1은 `X LOCK` 보유 중 (배타 락)
- 세션 2는 snapshot 기반 읽기 (MVCC 사용)
- → 세션 2는 **undo log 기반으로 name = 'test' 조회**

결과 → 'test'

이유

- `REPEATABLE READ` 격리 수준에서는 트랜잭션 시작 시점의 snapshot을 사용해서 커밋되지 않은 변경사항은 보이지 않는다

</br>
</br>

## [2] 트랜잭션 열지 않고 기본 AutoCommit 모드일 때

> AutoCommit = 1 상태 (기본값)
>
> 쿼리 하나 실행할 때마다 자동으로 트랜잭션 열고 바로 커밋

**세션 2**

```sql
SELECT name FROM user WHERE id = 1;
```

</br>

결과

- 세션 1은 여전히 `X Lock` 보유 중
- 세션 2의 SELECT 는 snapshot 기반 읽기 수행
- → 락을 기다리지 않고 바로 **undo log에 조회**

결과 → 'test'

이유

- 트랜잭션이 짧게 열린 뒤 snapshot 기반으로 바로 읽고 커밋
- MVCC 가 있어서 커밋되지 않은 변경은 제외하고 조회한다

</br>
</br>

## [3] SELECT ... FOR UPDATE 사용할 때

**세션 2**

```sql
START TRANSACTION;
SELECT name FROM user WHERE id = 1 FOR UPDATE;
```

</br>

결과

- 세션 1은 `id = ` 에 대해 `X Lock` 보유 중
- 세션 2는 **같은 Row에 대해 X Lock 요청**
- → InnoDB 는 **락 충돌로 세션 2를 대기 상태로 둔다**

→ 세션 2는 세션 1이 COMMIT 또는 ROLLBACK 할 때까지 대기하게 된다

</br>
</br>

### 정리

| 구분                        | 동작                   | 결과                         |
| --------------------------- | ---------------------- | ---------------------------- |
| [1] 트랜잭션 열고 SELECT    | MVCC snapshot 읽기     | `'test'` (기존값)            |
| [2] 오토 커밋 모드          | snapshot 읽기          | `'test'` (기존값)            |
| [3] `SELECT ... FOR UPDATE` | X Lock 요청, 대기 발생 | **세션 1이 커밋해야 진행됨** |
