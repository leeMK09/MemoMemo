## 트랜잭션 관리의 전체 흐름

트랜잭션이 시작되어 데이터베이스에 쿼리가 실행되기까지의 흐름

1. 클라이언트의 메서드 호출
   - 클라이언트가 `@Transactional` 이 붙은 메서드를 호출합니다
2. 프록시에서 호출 가로채기
   - 프록시는 호출을 가로채어 `TransactionInterceptor` 를 실행합니다
3. 트랜잭션 시작
   - `TransactionInterceptor` 는 `PlatformTransactionManager` 의 구현체 (예: `JpaTransactionManager`) 를 통해 트랜잭션을 시작합니다
4. 비즈니스 로직 실행
   - 실제 메서드가 실행되어, 이 과정에서 데이터베이스 접근이 이루어집니다
5. 트랜잭션 종료
   - 메서드 실행이 완료되면, `TransactionInterceptor` 는 트랜잭션을 커밋하거나 롤백합니다

</br>
</br>

## 각 컴포넌트의 역할

- `PlatformTransactionManager`
  - 트랜잭션의 시작, 커밋, 롤백 등의 핵심 메서드를 정의한 인터페이스입니다
- 구현체들 (`JapTransactionManager`, `HibernateTransactionManager` 등)
  - 각각의 영속성 기술(JPA, Hibernate 등)에 맞게 트랜잭션을 구체적으로 관리합니다
- `TransactionInterceptor`
  - AOP를 활용하여 트랜잭션의 시작과 종료를 관리하는 역할을 합니다
- 프록시
  - 메서드 호출을 가로채어 `TransactionInterceptor`를 실행하는 역할을 합니다

</br>
</br>

## @Transactional 어노테이션의 내부 동작 과정

`@Transactional` 어노테이션은 스프링에서 선언적 트랜잭션 관리를 지원하기 위한 핵심요소이다.

메서드 호출 시점부터 트랜잭션이 관리되는 전체 흐름

1. 프록시 생성 및 메서드 호출 인터셉트
   - 프록시 생성 : 스프링은 AOP 를 활용하여 `@Transactional` 이 선언된 빈에 대한 프록시 객체를 생성한다
   - 메서드 호출 인터셉트 : 클라이언트가 해당 메서드를 호출하면, 실제 객체가 아닌 프록시 객체가 호출을 가로챈다
2. `TransactionInterceptor` 의 동작
   - 트랜잭션 매니저 조회 : `TransactionInterceptor` 는 `PlatformTransactionManager` 를 조회한다
   - 트랜잭션 속성 파악 : 해당 메서드에 적용된 트랜잭션 속성 (전파 수준, 격리 수준 등) 을 확인한다
3. 트랜잭션 시작
   - 기존 트랜잭션 확인 : 현재 스레드에 이미 진행 중인 트랜잭션이 있는지 확인한다
   - 새 트랜잭션 생성 : 없다면 새로운 트랜잭션을 시작하고, `TransactionSynchronizationManager` 를 통해 현재 스레드에 바인딩한다
4. 실제 비즈니스 로직 실행
   - 메서드 실행 : 트랜잭션이 시작된 상태에서 실제 비즈니스 로직이 수행된다
5. 트랜잭션 종료
   - 정상 완료 시 커밋 : 메서드가 예외 없이 종료되면 트랜잭션을 커밋한다
   - 예외 발생 시 롤백 : 선택적
   - ThreadLocal 정리 : 현재 스레드에 바인딩된 트랜잭션 정보를 제거하여 메모리 누수를 방지한다

</br>
</br>

## ThreadLocal 에 저장되는 정보들

- 트랜잭션 동기화 여부 : 현재 스레드에서 트랜잭션 동기화가 활성화되었는지 여부
- 리소스 맵 : 데이터소스나 세션 팩토리와 같은 트랜잭션에 관련된 리소스들을 매핑하여 저장한다
- 트랜잭션 동기화 콜백 : 트랜잭션 완료 시 실행될 콜백들을 저장한다

</br>
</br>

## 전체 흐름 요약

1. 프록시 객체 생성 : `@Transactional` 이 선언된 빈에 대한 프록시가 생성된다
2. 메서드 호출 인터셉트 : 프록시가 메서드 호출을 가로채고, `TransactionInterceptor` 를 호출한다
3. 트랜잭션 시작 : `TransactionInterceptor` 는 트랜잭션 매니저를 통해 트랜잭션을 시작하고, 관련 정보를 `ThreadLocal` 에 저장한다
4. 비즈니스 로직 실행 : 실제 메서드가 실행된다
5. 트랜잭션 종료 : 메서드 실행 결과에 따라 트랜잭션을 커밋하고 롤백하고, `ThreadLocal` 을 정리한다

</br>
</br>

## 핵심 구성요소

- `@Transactional`
  - AOP 트랜잭션 적용 대상 마킹
- `TransactionInterceptor`
  - AOP Advice
  - 트랜잭션 시작/커밋/롤백 제어
- `BeanPostProcessor`
  - 프록시 생성
- `PlatformTransactionManager`
  - 실제 DB 트랜잭션 관리

</br>
</br>

## 언제 TransactionInterceptor 가 등장하는가?

핵심 시점

- 빈 등록 시점에 AOP 프록시가 생성되면서 등록된다

스프링은 `@Transactional` 붙은 클래스를 빈 등록 과정에서 분석해서 프록시를 만들고, 그 프록시가 내부에 `TransactionInterceptor` 를 장착하게 된다

</br>

**상세 흐름**

1. `@EnableTransactionManager` 선언
   - 이 어노테이션이 있으면 스프링은 트랜잭션 AOP 설정을 활성화한다
   - 내부적으로 `TransactionManagemenetConfigurationSelector` 를 통해 다음 구성 클래스를 등록한다
   ```java
   ProxyTransactionManagemenetConfiguration
   ```

</br>

2. `TransactionInterceptor` Bean 등록
   - 위 구성 클래스 안에 여러 Bean 등록이 있다
   ```java
   @Bean
   public TransactionInterceptor transactionInterceptor(...) {
       return new TransactionInterceptor(transactionManager, transactionAttributeSource);
   }
   ```

→ 여기서 등장

→ 이 Interceptor 는 나중에 프록시 메서드 실행 시 AOP 로 호출된다

</br>

3. `AutoProxyCreator` 가 트랜잭션 대상 클래스를 감지한다
   - `@Transactional` 이 붙은 클래스를 감지하면
   - `BeanPostProcessor` 가 해당 클래스를 프록시로 감쌈
     - JDK 동적 프록시 : 인터페이스가 존재할 때
     - CGLIB 프록시 : 인터페이스가 없을때
   ```java
   proxy = ProxyFactory.createProxy(target, [TransactionInterceptor]);
   ```

→ 즉 `TransactionInterceptor` 는 AOP Advice 로써 프록시의 메서드 호출 시 삽입됨

</br>

4. 실제 실행 시점

```java
// 클라이언트 코드
myService.doSomething(); // 프록시 객체의 메서드 호출
```

이때 프록시는 내부적으로 다음 흐름으로 동작한다

```plain
→ 프록시 : invoke()
    → TransactionInterceptor.invoke()
        → txManager.getTransaction() // 트랜잭션 시작
        → 실제 메서드 실행
        → 예외 없으면 commit(), 있다면 rollback() // 선택적
```

</br>

```plain
@Bean 등록 시
┌──────────────┐
│@Transactional│
└─────┬────────┘
      │
      ▼
@EnableTransactionManagement
  └── ProxyTransactionManagementConfiguration 등록
        └── TransactionInterceptor 등록됨
             ↑
프록시 생성 시 AOP Advice로 장착됨
             ↓
클라이언트 호출 → 프록시 intercept → TransactionInterceptor 작동
```
