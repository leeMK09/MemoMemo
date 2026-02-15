## 몽고 DB 의 배열 필드의 멀티키 (multikey) 인덱스

- MongoDB 에서 어떤 필드에 인덱스를 만들었는데 해당 필드 값이 배열이라면 MongoDB 는 해당 인덱스를 자동으로 멀티 키 인덱스로 취급한다
- 즉 문서 하나가 `tags: ["a", "b", "c"]` 처럼 여러 값을 가지면 MongoDB는 한 문서에 대해 인덱스 엔트리를 배열 원소 개수만큼 만들어서 저장한다
    - 배열 값을 담는 필드에 인덱스를 만들면 MongoDB 가 자동으로 multikey 를 설정한다
- 이 구조를 역인덱스(inverted index) 라고 부른다 - 값(토큰) → 그 값을 가진 문서 ID 로 빠르게 매핑을 수행하는 인덱스 테이블

예시)

| 토큰(token) | 도큐먼트 id |
| ----------- | ----------- |
| Apple       | [1, 2, 3]   |
| 2025        | [1]         |
| 맥북        | [1]         |
| 에어        | [2]         |
| 13          | [3]         |
| M4          | [3]         |

- 중요한 건 배열의 각 원소를 인덱스 키로 쪼개서 문서를 가리키는 엔트리를 여러 개 만든다는 점이다
- 그래서 결과적으로 원소 값 하나로 해당 원소를 포함한 문서들을 빠르게 찾는 패턴이 가능해진다
- 또한 배열이 스칼라 배열이든(문자/숫자), 임베디드 도큐먼트 배열이든 멀티키 인덱스를 만들 수 있고 배열 안에 같은 값이 여러 번 반복되면 인덱스에는 그 값 엔트리를 중복으로 넣지 않는다고 설명한다

**중첩 (배열 안의 배열) 과 데이터가 큰 경우 멀티키에 문제가 되는 이유**

- 멀티 키의 핵심 비용은 인덱스 엔트리 수가 문서 수가 아닌 배열 원소 수에 비례해서 커진다는 점이다
- 문서가 1천만 건이어도 배열이 평균 3개면 인덱스 엔트리가 대략 3천만 개가 되고 평균 100개면 10억 개로 증가한다
- 이렇게 되면 인덱스 저장 공간이 커지고 해당 인덱스를 효율적으로 쓰려면 작업 집합이 메모리에 더 많이 올라와야 하므로 캐시 미스가 증가하고 지연이 커질 수 있다
- 그리고 쓰기 비용이 커진다.
    - 문서 한 건 insert/update 가 배열 원소 개수만큼 인덱스 구조를 갱신해야 하므로 배열이 큰 문서는 쓰기 비용이 커진다
- 특정 쿼리는 인덱스로 후보를 찾는 것까지만 빨라지고 최종적으로 도큐먼트를 가져와 추가 필터링을 해야 하면서 병목이 남을 수 있다
    - 예를 들어 배열 전체가 정확히 같은지 비교하는 쿼리는 인덱스로는 배열의 첫 요소를 포함한 후보를 찾고 나머지는 도큐먼트를 읽어 필터링한다고 되어있다
- 중첩을 실무에서 조심해야 하는 이유는 배열의 원소 수가 늘어나면 인덱스 엔트리 폭증이 더 쉽게 발생하고 쿼리 플랜이 인덱스를 타더라도 결과적으로 너무 많은 후보를 만들기 쉬워서 성능이 불안정해지기 때문이다
- 설계적으로 배열을 크게 만들지 말고 질의 패턴에 맞춰 분리 및 정규화하여 컬렉션으로 분할하는 모델링 선택지도 존재한다

**복합 인덱스에서 두 개의 배열 필드를 같이 인덱싱하지 않는 이유**

- MongoDB 는 복합키 인덱스(compound multikey index)에서 인덱스에 포함된 필드들 중 배열이 될 수 있는 필드는 문서당 최대 1개만 허용된다
- 따라서 인덱스 스펙에 배열 필드가 2개 이상이면 복합 멀티키 인덱스를 만들 수 없다
- 이유는 복합 인덱스는 일반적으로 `(A, B)` 형태로 정렬된 키 공간을 만든다
- 그런데 A도 배열이고 B도 배열이면 문서 하나가 사실상 `(a1, b1) (a1, b2) ... (a2, b1) ...` 처럼 카다시안 곱 형태로 엄청난 조합을 만들어낼 수 있고 그렇게 되면 인덱스 엔트리 폭증이 통제 불가능해집니다
- MongoDB 는 이 폭발을 구조적으로 막기 위해 복합 인덱스 안에 배열 필드 2개를 금지한 것 이다
- 추가로 이미 그런 복합 멀티키 인덱스가 존재하는 상태에서 두 필드가 모두 배열이 되는 문서를 insert 하려고 하면 insert 자체가 실패한다

</br>

## TTL 인덱스

- TTL 은 시간이 지나면 의미가 없어지는 데이터를 컬렉션에서 자동으로 정리하기 위한 장치이다
- 대표적으로 로그인 세션, 비밀번호 재설정 토큰, OTP, 단기 캐시성 도큐먼트, 감사/추적 로그 중 보관 기간이 짧은 것 등 에서 자주 사용한다
- 즉 애플리케이션에서 배치 삭제 등 직접 삭제할 필요없이 DB 레벨에서 만료 정책을 강제한다는 점이다

```javascript
db.users.createIndex({ createdAt: 1 }, { expireAfterSeconds: 3600 });
```

**Date 타입 필드**

- TTL 인덱스 키 필드는 Date 타입이거나 Date 타입 값을 담은 배열이어야 한다

**실시간 미반영**

- TTL 은 만료 시점 즉시 삭제를 보장하지 않는다
- MongoDB 는 TTL 삭제를 전용 백그라운드 작업(TTL monitor thread)이 수행하고 이 작업이 60초 주기로 돌아가므로 만료되었지만 최대 0 ~ 60초 (+ 서버 부하에 따른 지연 등) 정도 남아있을 수 있다
- 또한 TTL 삭제는 복제 구성에서 Primary 에서만 실제 삭제가 실행되고 Secondary 는 해당 삭제를 복제로 따라간다
- 즉 TTL 인덱스는 결국 delete 오퍼레이션을 발생시키는 장치이며 TTL 삭제가 많아지면 delete 부하와 스토리지 단편화 같은 2차 효과도 고려해야 하며 대량 삭제가 성능에 영향을 줄 수도 있다

**TTL 인덱스의 제약**

- TTL 인덱스는 단일 필드 인덱스만 허용되며 복합 인덱스는 TTL 을 지원하지 않으며 `expireAfterSeconds` 를 무시한다
- 즉 createdAt + status 로 복합 인덱스 요구사항이 있다면 TTL 로는 직접 못하고 별도의 모델링(만료용 별도 필드 분리)로 해결해야 한다

</br>

## Text 인덱스

- Text 인덱스는 문자열 필드 안의 단어들을 토큰화(tokenize)해서 특정 단어가 포함된 문서를 빠르게 찾도록 만드는 인덱스이다
- MongoDB 문서는 text 인덱스가 문자열 콘텐트에 대한 텍스트 검색 쿼리를 지원하고 특정 단어/문자열을 찾을 때 성능을 개선한다
- 각 문서의 각 인덱싱 필드에서 stemming(어간 처리) 이후의 유니크 단어마다 인덱스 엔트리가 생긴다
    - 그 때문에 RAM 과 저장공간을 많이 차지하고 쓰기 성능에도 영향을 준다

**생성 및 기본 제약**

- 한 컬렉션은 text 인덱스를 최대 하나만 가질 수 있고 그 하나의 text 인덱스에 여러 필드를 포함할 수 있다

```javascript
db.articles.createIndex({
    title: "text",
    body: "text",
});
```

**언어, stemming, stop word(불용어)**

- Text 검색은 언어 설정에 따라 불용어 목록과 stemming 규칙이 달라진다
- 문서는 `default_language` 를 `none` 으로 주면 불용어 제거도 하지 않고 stemming 도 하지 않는 단순 토큰화로 동작한다
- 즉 자연어 기반 검색이라고 할 때 MongoDB text 인덱스가 해주는 자연어 처리는 범용 검색엔진 수준의 문맥 이해라기보다 언어별 형태 단순화(어간 처리) + 불용어 제거 + 토큰 단위 매칭에 가깝다

**$text 쿼리 문법**

- `$text` 쿼리는 다음 형태로 실행되고 `$search` 문자열은 기본적으로 단어들을 OR 로 해석한다
- MongoDB 는 `$search` 의 term 들에 대해 기본적으로 logical OR 쿼리를 수행한다

```javascript
db.users.find({
    $text: { $search: "mongo" },
});
```

- 여기서 `$search` 문자열 처리 규칙 중 중요한 건 다음과 같다

```javascript
db.articles.find({ $text: { $search: "bake coffee cake" } }); // 단어 여러개는 OR 처리

db.articles.find({ $text: { $search: '"ssl cerificate"' } }); // 따옴표 (escape) 로 정확한 구문을 만들 수 있다

db.articles.find({ $text: { $search: "Coffee -shop" } }); // 하이픈 은 단어 제외로 동작한다
```

- 이 규칙들은 대부분의 문장부호를 delimiter 로 취급하되 `-` 는 단어 제외(negative), `\` 는 exact string 을 의미한다
- 또한 `$text` 는 결과에 관련된 점수(text Score) 를 부여하고 `{ $meta: "textScore" }` 로 projection 하거나 정렬할 수 있다

**Text 인덱스의 한계**

- Text 인덱스는 한 컬렉션 당 1개만 가질 수 있는 제약이 있고 정렬 성능을 근본적으로 개선하지 못하며 인덱스 자체가 단어 단위로 저장되므로 단어 간 거리(proximity) 같은 정보는 저장하지 않는다
- 이런 제약 때문에 검색 기능이 제품 경쟁력인 서비스는 MongoDB 내장 text 보다 Atlas Search (Lucene 기반) 로 넘어가는 경우가 많다

</br>

### Atlas Search (아틀라스 서치)

- Atlas Search 는 Atlas 에서 제공하는 `$search` aggregation stage 기반의 검색 기능이고 `$text` 의 `$search` 필드와 Atlas Search 의 `$search` stage 는 이름은 같지만 다르다
- 즉 `db.collection.find({ $text: { $search: "..." }})` 는 self-managed 포함 MongoDB text 인덱스 기반 검색, Atlas Search 는 `aggregate([{ $search: ... }, ...])` 파이프라인으로 동작한다

**compound, should, must, filter**

- Atlas Search 의 `compound` 는 불리언 쿼리 조합을 표현하는 연산자, `must`, `mustNot`, `should`, `filter` 절은 배열로 받는다
    - 각 절은 subclause 배열을 가진다
- 또한 `should` 를 여러 개 쓰면 `minimumShouldMatch` 로 최소 몇 개의 should 가 만족해야 결과에 포함할지를 설정할 수 있고 기본값은 0이다
- 그리고 성능 관점에서 중요한 규칙은 scoring 이 필요 없는 조건(정확한 일치, 범위, in 같은 검색)은 `filter` 에 두어 불필요한 점수 계산을 줄이도록 한다

```javascript
db.fruit.aggregate([
    {
        $search: {
            compound: {
                must: [{ text: { query: "varieties", path: "description" } }],
                should: [{ text: { query: "banana", path: "description" } }],
                filter: [{ equals: { path: "category", value: "nonorganic" } }],
            },
        },
    },
    { $limit: 20 },
]);
```

- 위 예시는 설명(description) 에 varieties 가 반드시 들어가야 한다 (must)
- banana 가 들어가면 더 선호하되(should), category 같은 조건은 점수와 무관하니 filter 로 처리

**서치 쿼리**

- MongoDB 에서 검색이라고 부를 때 두 갈래로 나뉜다
- 첫째 `$text` 는 MongoDB 의 text 인덱스를 기반으로 `find` 에서 바로 쓰는 방식이고 검색 문자열은 기본 OR 이며 `-` 로 제외, `\"...\"` 로 구문 검색을 표현한다
- 둘째 Atlas Search 는 `aggregate` 의 `$search` stage 로 들어가며 `compound` 로 must/should/filter 로 조합하고 `minimumShouldMatch` 같은 검색엔진식 기능을 제공한다
- 즉 간단한 키워드 검색이면 `$text` 로도 가능, 검색 품질/스코어링/복잡한 조건 조합/다중 인덱스/분석기(analyzer) 튜닝이 중요해지면 Atlas Search 로 사용
