## 엔티티의 생명 주기 4단계

- New 비영속
  - 객체는 있지만 DB에 저장되지 않음
- Managed 영속
  - 영속성 컨텍스트에 등록됨
- Detached 준영속
  - 영속성 컨텍스트에서 분리됨
- Remove
  - 삭제 예약 상태

</br>

## merge: Detached → Managed

```java
User detachedUser = new User()
detachedUser.setId(1L); // 이미 DB 에 존재
detachedUser.setName("Updated");

em.merge(detachedUser); // select + update
```

→ 기존 DB 상태를 `SELECT` → 병합 → 변경 감지 → `UPDATE`

</br>
</br>

## persist: New → Managed

```java
User newUser = new User("Alice");
em.persist(newUser); // INSERT
```

→ DB `INSET` 쿼리 예정

</br>
</br>

**merge VS persist 언제 쓰는것인가?**

- 새 객체 저장시에는 ?
  - `persist`
- Detached 객체를 갱신하는 경우는 ?
  - `merge`
- 영속화된 객체를 수정하는 경우는 ?
  - `setter` 로 변경한 후 `Dirty Checking`

</br>

**flush() VS clear() 언제 쓰는것인가?**

- `flush()`
  - 변경사항이 있을 경우 DB 에 SQL 반영 (커밋X)
- `clear()`
  - 영속성 컨텍스트 초기화 (컨테이너안에 있는 객체는 모두 detach 됨)

```java
em.flush(); // Dirty Checking → UPDATE
em.clear(); // 이후 em.find() 하면 다시 SELECT 쿼리 발생
```

</br>
</br>

## 영속성 컨텍스트에서 준영속(Detached) 상태가 되는 방법

준영속이란?

- 한때 영속 상태였지만, 지금은 영속성 컨텍스트에 의해 관리되지 않는 객체를 의미합니다

</br>

**준영속이 되는 방법들**

- `em.detach(entity)`
  - 명시적으로 해당 객체 하나만 detach
- `em.clear()`
  - 전체 영속성 컨텍스트 비우기 → 모든 객체 준영속됨
- `em.close()`
  - 영속성 컨텍스트 종료 → 포함된 모든 엔티티 준영속
- 트랜잭션 종료
  - 트랜잭션 스코프에서 관리되던 영속 객체 → 컨텍스트 없어짐 → 준영속됨

</br>

**clear() 시 준영속 상태가 되는것인가**

- `clear()` == 영속성 컨텍스트에 있는 객체들을 전부 `detach()` 하는 것
- 그래서 `clear()` 이후 객체는 **비영속이 아니라 준영속**이 맞다

> 비영속은 처음부터 `persist()` 도 안된 객체
> 준영속은 한 번 `persist` 또는 `find` 되어 관리되다가 빠진 객체

</br>
</br>

## merge 는 언제, 어떻게 사용되는가?

```java
User u = new User();
u.setId(1L);
u.setName("changed");

User mergedUser = em.merge(u);
```

- `u` 는 준영속 상태거나, 비영속 상태
- `merge(u)` 는 DB 에서 `id = 1` 을 찾아서 복사본을 만든 후, `changed` 를 적용
- 반환값인 `mergedUser` 가 영속 상태
- `u`는 여전히 준영속 (관리안됨)

</br>

**Spring Data JPA 에서는 `merge()` 를 직접 호출하지 않는다**

- 내부적으로 `save()` 메서드가 상황에 따라 `persist()` OR `merge()` 를 호출한다

```java
// 영속성 컨텍스트에 없는 id=1 객체를 save()
User u = new User();
u.setId(1L);
u.setName("abc");

userRepository.save(u); // 내부적으로 merge 동작
```

즉 `save()` 는 `id != null` 일 경우 → 존재하는 줄 알고 `merge()` 호출

</br>
</br>

## merge 후 더티체킹은 어떻게 되는가?

```java
User u = new User();
u.setId(1L);
u.setName("changed");

User managed = em.merge(u);
managed.setName("changed2");
```

managed 객체는 영속 상태 → `flush()` 또는 트랜잭션 커밋 시 **Dirty Checking 발생**

반면 `u` 는 여전히 준영속 → `u.setName("changed2")` 는 아무 의미 없음

</br>
</br>

### 더티체킹 발생 조건

| 조건       | 설명                                           |
| ---------- | ---------------------------------------------- |
| 영속 상태  | 반드시 영속성 컨텍스트에 관리되는 객체여야 함  |
| 변경 발생  | 필드 값이 기존과 달라져야 함                   |
| flush 발생 | 커밋 전이든, 수동 flush든 호출돼야 UPDATE 발생 |
