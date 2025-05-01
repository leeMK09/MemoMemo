# 참고

`EntityManagerFactory` / `PlatformTransactionManager` / `Repository` / `TransactionSynchronizationManager` / `TransactionManager` / `EntityManager`

위 객체 간의 협업에 대한 설명입니다

---

# 1. 스프링 부트 실행 (`애플리케이션 시작`)

**빈(Bean) 등록**

스프링 부트가 실행되면 먼저 **다음과 같은 빈들을 등록**한다

| 빈                                                     | 역할                        | 생성 타이밍               |
| ------------------------------------------------------ | --------------------------- | ------------------------- |
| `EntityManagerFactory`                                 | EntityManager를 찍어낼 공장 | 애플리케이션 시작 시 생성 |
| `PlatformTransactionManager` (`JpaTransactionManager`) | 트랜잭션 관리               | 애플리케이션 시작 시 생성 |
| `Repository` (ex. UserRepository)                      | DB 접근용 DAO               | 애플리케이션 시작 시 생성 |

```java
@SpringBootApplication
-> @EnableAutoConfiguration
-> JpaRepositoriesAutoConfiguration
-> TransactionManager, Repository 생성
```

`EntityManagerFactory` , `TransactionManager` , `Repository` 는 모두 이 시점에 싱글톤 빈으로 만들어 관리되어 진다

</br>

더불어서 빈 으로 등록은 되지 않지만 `TransactionSynchronizationManager` 또한 싱글톤으로 관리된다.

```java
public abstract class TransactionSynchronizationManager {
    private static final ThreadLocal<Map<Object, Object>> resources = new NamedThreadLocal<>("Transactional resources");
}
```

</br>

> 참고
>
> `TransactionSynchronizationManager`
>
> - 한 스레드 마다 고유한 `Map` 을 가지고 있다
> - 여기에 “현재 스레드가 쓰는 `EntityManager` 를 저장해놓고 꺼내서 사용한다
>   (`TreadLocal` 을 통해 스레드별로 트랜잭션 리소스 (`EntityManager` 등) 를 관리한다

<br>

---

</br>

# 2. HTTP 요청이 들어오면 (`요청 시작`)

**스레드 하나를 배정**

- 톰캣 (NIO 기반) → 요청 하나당 스레드 하나 할당
- 이 스레드로 요청 처리 시작

```java
[HTTP 요청] → [스레드 배정] → [DispatcherServlet 진입]
```

</br>

---

</br>

# 3. `@Transactional` 이 걸린 서비스 메서드 호출

**프록시 객체 진입**

- 서비스 빈 (예: `UserService`) 은 사실 프록시 객체
  - `@Transactional` 이 적용되었기 때문에 프록시 객체로 관리되어진다
- 프록시가 동작해서 트랜잭션을 시작해야 하는지 판단

이떄, 프록시는 내부적으로 `TransactionInterceptor` 를 호출한다

</br>

---

</br>

# 4. `TransactionManager` 동작

**`TransactionManager.getTransaction()` 호출**

- `TransactionManager.getTransaction(TransactionDefinition)` 을 실행
- 여기서 트랜잭션을 시작할지 여부를 판단

**동작 과정**

1. 현재 스레드에 트랜잭션이 있는지 `TransactionSynchronizationManager` 를 통해 확인
2. 없으면 새롭게 트랜잭션을 시작

**새로운 트랜잭션 시작할 때**

- `EntityManagerFactory` 를 통해 `EntityManager` 를 생성한다
- 생성된 `EntityManager` 가져온 `EntityManager` 를 `TransactionSynchronizationManager` 에 스레별 (`ThreadLocal`) 로 저장한다
  - `TransactionSynchronizationManager` 가 현재 스레드에 `EntityManager` 를 보관하게 된다

</br>

---

</br>

# 5. Repository 사용

**Repository 가 EntityManager (프록시) 를 통해 DB 작업**

- Service → Repository 호출
- `Repository` 는 `EntityManasger` 를 사용해서 `find` , `persist` , `merge` 같은 메서드 호출

여기서 중요한건

- `Repository` 에 주입된 `EntityManager` 는 프록시 객체이다
- 프록시가 `TransactionSynchronizationManager` 를 통해 **현재 스레드에 저장된** 진짜 `EnttyManager` 를 찾아서 반환한다

</br>

```java
Repository (싱글톤)
 └─ EntityManager (프록시)
      └─ TransactionSynchronizationManager에서 현재 스레드 EntityManager 조회
```

</br>

---

</br>

# 6. 비즈니스 로직 종료

서비스 메서드가 끝나면 `TransactionInterceptor` 가 `TransactionManager.commit()` 호출

**`commit()` 이 진행되는 과정**

1. `EntityManager.flush()` 호출

   → 영속성 컨텍스트에 쌓인 변경사항을 DB 에 반영

2. `EntityManagaer.commit()` 호출

   → JDBC 레벨에서 트랜잭션 COMMIT 실행

3. `EntityManager.close`

   → DB 커넥션 반환

4. `TransactionSynchroniizationManager` 의 스레드-로컬 제거

   → 스레드에 묶어놨던 `EntityManager / 커넥션` 등을 다 없애준다

</br>

---

</br>

# 7. 요청 완료

모든 처리가 끝나면 톰캣 스레드 풀로 스레드가 반환된다

EntityManager도 close됐고, ThreadLocal도 정리됐기 때문에 메모리 누수 걱정은 없다.

</br>
</br>

### 정리 및 시간 순서 요약

```java
[애플리케이션 시작 시]
  - EntityManagerFactory 생성
  - TransactionManager(JpaTransactionManager) 생성
  - Repository 빈 생성

[요청 시작]
  - 톰캣이 스레드 하나 할당

[@Transactional 메서드 진입]
  - 프록시가 TransactionManager.getTransaction() 호출
  - EntityManagerFactory로 EntityManager 생성
  - EntityManager.getTransaction().begin()
  - TransactionSynchronizationManager에 EntityManager 저장

[Service → Repository 호출]
  - Repository가 EntityManager 프록시를 통해
  - TransactionSynchronizationManager에서 진짜 EntityManager 조회
  - DB 조회/저장 수행

[Service 메서드 종료]
  - TransactionManager.commit() 호출
  - EntityManager.flush()
  - EntityTransaction.commit()
  - EntityManager.close()
  - TransactionSynchronizationManager 리소스 정리

[요청 종료]
  - 스레드 반환
```
