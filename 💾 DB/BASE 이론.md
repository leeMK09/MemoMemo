# 결론

**ACID 를 포기하고 성능을 우선하는 경우는 BASE 를 고려하는 것도 하나의 방법이다**

</br>

---

ACID 를 다 지키려면 항상 비용이 든다

- Undo Log, Redo Log, 락 관리, 버전 관리 등등

비용이 들기 때문에 성능 이슈가 생길 수 있는 부분들이 존재한다

대규모 트래픽이 필요한 시스템에서는 ACID 를 완전히 지키는 대신 "잠깐의 데이터 불일치를 감수하는" 전략을 쓰기도 한다

---

</br>

## BASE

| 항목 | 의미                                               |
| ---- | -------------------------------------------------- |
| B    | Basically Available (기본적인 가용성)              |
| A    | Soft sate (상태가 변할 수 있음)                    |
| SE   | Eventually Consistent (언젠가는 일관성에 도달한다) |

</br>

**Basically Available**

- 장애가 나더라도 서비스 자체는 계속 살아있게 하자

**Soft state**

- 데이터는 잠깐동안 불일치할 수 있다

**Eventually Consistent**

- 시간이 지나면 언젠가는 데이터가 일관되게 맞춰질 거야

→ 즉 "지금 당장은 불일치해도 좀 나중에 맞추자" 라는 전략의 성격이 강하다

</br>

### 예시

**NoSQL**

- Cassandra, DynamoDB, Couchbase 같은 NoSQL DB 들이 BASE 기반
- 예를 들면 Amazon DynamoDB 에서는 "Eventually Consistent Read" 를 선택할 수 있다
  → 읽으면 최신 데이터가 아닐 수 있다

대신

- 초고속 쓰기/읽기 퍼포먼스를 달성할 수 있다

즉 ACID 를 포기하고 BASE 전략을 써서 성능과 기용성을 더 중요하게 생각한다
