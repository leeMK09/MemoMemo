```sql
SELECT id, subjects, writer, regit
FROM article
ORDER BY id DESC
LIMIT 10 OFFSET 99990;
```

이 쿼리를 실행할 때 99,991 번째 ID 부터 바로 조회하면 좋겠지만 DB 는 어떤 ID 값이 99,991 번째인지 알지 못한다

그래서 DB 는 역순(`ORDER BY id DESC` 이므로) 으로 id 를 99,990개 세고 나서 10개 데이터를 조회한다

데이터를 세는 시간만큼 실행 시간이 증가하는 것 이다

```sql
SELECT id, subejcts, wirter, regit
FROM article
WHERE deleted = false
ORDER BY id DESC
LIMIT 10 OFFSET 99990;
```

위 쿼리에도 문제가 있다

"deleted 컬럼이 인덱스에 포함되어 있지 않다면 어떻게 될까?"

주요 키를 이용해서 id 컬럼을 역순으로 차례대로 세는 과정은 동일하다

하지만 deleted 값이 false 인지 판단해야 하기 때문에 **DB는 각 행의 deleted 값을 읽어야 한다**

실제 데이터를 읽어오므로 실행 시간은 더 길어진다

또한 deleted 값이 true 인 데이터도 존재하므로 deleted 값이 false 인 데이터 99,990 개를 세기 위해 조회하는 데이터 수는 99,990 개를 넘기게 된다

지정된 오프셋으로 이동하기 위해 데이터를 세는 시간을 줄이는 방법은 특정 ID 를 기준으로 조회하는 것 이다

**커서 기반 페이징(keyset pagination)**

```sql
-- 마지막으로 본 id 를 기억하고, 그 이후 (더 작은) id 를 가져오기
SELECT id, subjects, writer, regit
FROM article
WHERE id < :last_seen_id
ORDER BY id DESC
LIMIT 10;
```

이렇게 하면 "앞에서부터 몇개를 스킵"이 아닌 "해당 id 보다 작은 것부터 10개" 라서 성능이 훨씬 좋다

만약 `WHERE id < 100000` 이라면

- 정렬된 순서에서 id < 100000 인 것 중에서 상위 10개를 바로 찾는다
- 앞의 99,990 개를 건너뛸 필요가 없음
- `id` 컬럼에 인덱스가 있다면 `WHERE id < ? ORDER BY id DESC LIMIT 10` 은 인덱스를 타서 바로 원하는 위치에서 10개만 가져옴 → 랜덤 접근 + 짧은 범위 스캔
- 페이지가 깊어져도 성능이 일정하며 빠르고 스캔 양이 적다
- 단점은 단순히 "페이지 번호"로 접근하기 힘듦 → 커서(마지막 ID) 를 클라이언트에서 저장해 두어야 하며, 특정 N 번째 페이지로 곧바로 이동하기 어렵다

</br>
</br>

관련된 블로그

- [향로님블로그\_NoOffset](https://jojoldu.tistory.com/528)
