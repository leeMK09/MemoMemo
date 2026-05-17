MySQL (InnoDB) 에서 **같은 컬럼에 대해 UPDATE 와 SELECT (READ) 가 동시에 들어오는 경우**

어떤 일이 발생하는지를 정확히 보려면

1. 트랜잭션 유무
2. 격리 수준
3. 락 획득 여부

에 따라 다르게 동작합니다

</br>

---

# 기본 전체 정리

- **InnoDB 스토리지 엔진 기준으로 설명**
- SELCT 는 일반적인 `SELCT * FROM ... WHERE ...` 로, 별도 락 (`FOR UPDATE`, `FOR SHARE`) 없이 읽는 경우
- UPDATE는 특정 ROW 를 변경 (`UPDATE ... WHERE id = 1`) 하는 일반적인 형태

</br>

---

## 상황 1 - 트랜잭션 없는 단순 READ vs WRITE

예시)

```sql

-- 세션 1
UPDATE account SET balance = balance - 100 WHERE id = 1;

-- 세션 2
SELECT * FROM account WHERE id = 1;
```

실행 결과

- **UPDATE 쿼리는 row-level X Lock (배타 락)** 을 겁니다
- **SELECT 쿼리는 MVCC 를 통해 undo log 를 읽습니다**
- 따라서 **락을 기다리지 않고, 커밋되지 않은 이전 값을 snapshot 으로 조회합니다**

즉, **SELCT 는 non-blocking 이고, 커밋 이전의 데이터는 무시됩니다**

</br>

---

## 상황 2 - 트랜잭션 안에서 동시에 발생 (트랜잭션 격리 수준 영향)

예시 (트랜잭션 격리 수준: `REPEATABLE READ` - MySQL 기본)

```sql

-- 세션 1
START TRANSACTION;
UPDATE account SET balance = 900 WHERE id = 1;
-- 아직 COMMIT 안함

-- 세션 2
START TRANSACTION;
SELECT * FROM account WHERE id = 1;
```

실행 결과

- 세션 1 : UPDATE 는 id = 1 인 row 에 **X Lock 을 겁니다**
- 세션 2 : SELECT 는 **락을 걸지 않음 (MVCC 기반 스냅샷 읽기)**
- 세션 2는 undo log 를 통해 **변경되기 전 버전을 조회함**

즉, **락 대기 없이 조회 가능**

단, **변경된 값은 조회되지 않음 (아직 커밋 안 됐기 때문)**

</br>

---

## 상황 3 - SELECT 에 `FOR UPDATE` 가 붙은 경우

```sql

-- 세션 1
START TRANSACTION;
UPDATE account SET balance = 900 WHERE id = 1;

-- 세션 2
START TRANSACTION;
SELECT * FROM account WHERE id = 1 FOR UPDATE;
```

실행 결과

- 세션 1 의 UPDATE 가 `id = 1` 에 **X Lock 을 보유중**
- 세션 2 의 `FOR UPDATE` 는 해당 row 에 또 다른 **X Lock 을 걸기 위해 대기 상태로 진입**
- 세션 1 이 COMMIT 또는 ROLLBACK 해야 세션 2가 락을 얻을 수 있음

**이때는 대기 발생 → 데드락 주의 필요**

</br>

### 정리

| 상황                        | 동작 방식                                        | 결과                                |
| --------------------------- | ------------------------------------------------ | ----------------------------------- |
| 일반 SELECT vs UPDATE       | MVCC 기반 읽기                                   | SELECT는 대기하지 않고 이전 값 조회 |
| SELECT FOR UPDATE vs UPDATE | 둘 다 X Lock 요청                                | 대기 발생                           |
| 트랜잭션 격리 수준 영향?    | 있음 (`READ COMMITTED` 이상이면 undo log 따라감) |                                     |
| 커밋 여부 영향?             | SELECT는 커밋된 버전 기준으로 동작               |                                     |

MySQL (InnoDB) 는 MVCC 구조를 통해 UPDATE 와 SELECT 간 락 충돌을 피하며, 일반 SELECT 는 undo log 를 이용해 **변경 전의 데이터(snapshot)** 을 조회하므로 **동시 접근 시에도 SELECT 는 블로킹되지 않고 동작합니다**
