# DDL 은 트랜잭션에서 자동으로 커밋된다

MySQL 에서 DDL 은 실행 즉시 자동 커밋이 발생합니다

```sql
START TRANSACTION;
INSERT INTO user VALUES(1, '철수');
ALTER TABLE user ADD COLUMN emal VARCHAR(255);
ROLLBACK;
```

→ `ALTER TABLE` 실행 순간, 이전 `INSERT` 도 함께 커밋되며 이후 `ROLLBACK`은 무의해집니다

> 이유
>
> DDL 은 테이블의 메타데이터를 변경하며, 이는 여러 세션과 공유되므로 롤백이 허용되지 않습니다
> [공식 문서 보기](https://dev.mysql.com/doc/refman/8.0/en/implicit-commit.html)

</br>

# DDL 중 Lock Timeout → Matadata Lock (MDL)

**실제 발생 사례**

- `ALTER TABLE` 실행 중 `lock wait timeout exceeded` 발생
- `SHOW FULL PROCESSLIST` 확인 → Sleep 상태 커넥션 존재
- 해당 커넥션 `KILL` 후 재시도 → 정상 처리됨

</br>

**원인 분석**

- DDL 은 `MDL_EXCLUSIVE` 락을 필요로 함
- 반면, `SELECT` 등은 `MDL_SHARED` 락을 보유함
- 다른 커넥션이 `MDL_SHARED` 락을 보유한 채 Sleep 상태였고, DDL 이 `MDL_EXCLUSIVE` 를 얻지 못해 Timeout 발생

</br>

**대응 방안**

- 구조 변경 전 `SHOW PROCESSLIST` 및 `performance_schema.metadata_locks` 조회
- 필요 시 `performance_shema.thread` 로 실행 시점 확인
- 불필요한 Sleep 세션은 `KILL`

</br>

## 왜 EXCUSIVE 락을 얻어야 하는데 SHARED 락에 대한 대기가 이루어지는가 ?

이전에 원인 분석에서 `DDL` 이 `MDL_EXCLUSIVE` 락을 얻고 싶어하는데 이미 누군가 `MDL_SHARED` 락을 가지고 있기 때문에 충돌(conflic) 이 발생한다고 이야기했다

왜 X Lock 을 획득하는데 S Lock 이 걸려있으므로 Lock 대기 및 Lock 충돌이 발생하게 되는 걸까 ?

`MDL_EXCLUSIVE` 락은 어떤 락도 존재하지 않아야만 획득이 가능하다

현재 발생한 상황에서는 다른 트랜잭션이 `MDL_SHARED` 를 쥐고 있으므로 → 현재 `DDL` 을 실행하는 세션은 대기 상태에 진입하게 된다

그러므로 `MDL_SHARED` 락을 해제하지 않는다면 계속 대기상태에 존재하게 됨 이후 대기시간을 초과하여 `lock wait timeout exceeded` 에러가 발생하게 된다

쉽게 비유하자면

- `MDL_SHARED` : "책 도서관에서 읽는 중" → 여러 명이 동시에 읽을 수 있음
- `MDL_EXCLUSIVE` : "책을 버리고 새로 쓰는 중" → 아무도 읽고 있으면 안됨

즉, **읽고 있는 사람 (SELECT) 이 있으면 책을 바꾸려는 사람(DDL)은 기다려야 하며** 이게 오래 걸리면 timeout 이 발생하게 된다
