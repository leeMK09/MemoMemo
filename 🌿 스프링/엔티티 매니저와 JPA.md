1. EntityManager 와 트랜잭션 바운더리 (재사용 시점) 에 대해
2. EntityManager VS EntityManagerFactory 관계에 대해
3. Spring이 어떻게 EntityManager 를 프록시로 감싸 관리하는가

</br>
</br>

## 1. EntityManager 는 트랜잭션 바운더리에 따라 재사용된다

- `@Transactional` 이 적용된 메서드가 시작되면, Spring은 현재 트랜잭션 컨텍스트가 존재하는지 확인한다
- 존재하면 → 그 트랜잭션에 묶여 있는 `EntityManager` 를 재사용한다
- 존재하지 않는다면 → 새 트랜잭션 생성 → 새로운 `EntityManager` 생성

</br>

### 내부 흐름 (Spring TransactionManager 기준)

1. `JpaTransactionManager.getTransaction()` 호출
2. 내부에서 `TransactionSynchronizationManager.hasResource(entityManagerFactory)` 확인
3. 존재한다면 → `EntityManagerHolder` 에서 기존 EntityManager 를 꺼냄
4. 존재하지 않는다면 → 새로운 EntityManager 생성 후 `ThreadLocal` 에 저장 (`bindResource()`)

```java
EntityManager em = entityManagerFactory.createEntityManager();
TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(em));
```

→ 스레드 단위로 묶여 있으므로 동일 스레드 내 트랜잭션에서는 동일한 EntityManager 가 재사용된다

</br>
</br>

## 2. EntityManager VS EntityManagerFactory 의 관계

| 구분                   | 역할                                                                  |
| ---------------------- | --------------------------------------------------------------------- |
| `EntityManagerFactory` | 하나의 DB, 하나의 persistence-unit에 대한 **"설정/공장" 객체**        |
| `EntityManager`        | **DB 작업 단위** (쿼리, persist, remove 등) 담당. 트랜잭션마다 생성됨 |

- `EntityManagerFactory` 는 singleton 으로 애플리케이션 전체에서 하나만 생성
- `EntityManager` 는 트랜잭션마다 새로 생성되거나 재사용됨

> DB 커넥션 = HikariCP 등 커넥션 풀에서 꺼내는 구조이므로, EntityManager 가 직접 커넥션을 새로 만들지 않음

</br>
</br>

## 3. Spring 이 어떻게 EntityManager 를 프록시로 감싸 관리하는가?

먼저 왜 프록시가 필요한가 ?

- 개발자는 `@PersistenceContext` 또는 생성자 주입으로 EntityManager 를 DI 받지만
- 그 EntityManager 는 진짜가 아니라 프록시입니다

</br>

이유는

- 트랜잭션이 시작된 시점마다 실제 사용할 EntityManger 가 달라질 수 있기 때문에 이를 Spring 이 관리하게 끔 되어있다
  - 개발 편의성 증가

</br>

### 프록시 동작 구조

Spring은 내부적으로 `SharedEntityManagerCreator` 라는 클래스를 통해 트랜잭션에 따라 실제 EntityManager 를 동적으로 교체해주는 프록시를 생성합니다

```java
@Bean
public EntityManager entityManager(EntityManagerFactory emf) {
    return SharedEntityManagerCreator.createSharedEntityManager(emf);
}
```

이 프록시는 `invoke()` 에서 아래처럼 동작합니다

```java
EntityManager target = EntityManagerFactoryUtils.doGetTransactionalEntityManager(emf);
// ThreadLocal 에서 현재 트랜잭션에 연결된 EntityManager 를 꺼냄
return target.method(args);
```

→ 즉 실제 EntityManager 가 아닌, "실제 EntityManager를 꺼내서 위임해주는 프록시" 가 주입됩니다

</br>

예시)

```java
@Service
class UserService {
    private final EntityManager em;

    @Transactional
    public void save() {
        em.persist(User(...)); // 프록시가 진짜 EntityManager 를 찾아서 위임
    }
}
```

이때 `em.persist(...)` 는 실제로 아래처럼 동작합니다

1. 프록시가 현재 트랜잭션을 확인
2. `TransactionSynchronizationManager.getResource(emf)` 로 EntityManager 를 찾음
3. `em.persist(...)` 호출 위임

**중요 포인트 정리**

| 포인트                                           | 설명                              |
| ------------------------------------------------ | --------------------------------- |
| EntityManager는 트랜잭션마다 새로 생성 or 재사용 | ThreadLocal 기반                  |
| EntityManagerFactory는 단 하나                   | Singleton으로 동작                |
| Spring은 EntityManager를 프록시로 감싸서 주입    | 트랜잭션에 따라 진짜 EM 찾아 위임 |
| 성능 문제 거의 없음                              | 커넥션은 pool, EM은 lightweight   |

</br>
</br>

### 참고 = "self-invocation" 문제

"self-invocation 문제"는 Spring AOP 기반의 `@Transactional` (또는 `@Cacheable`, `@Asyn` 등) 를 사용할 때 개발자가 모르고 빠지기 쉬운 대표적인 함정 중 하나 입니다

즉, 자기 자신의 내부 메서드를 this 로 호출하면 Spring 의 프록시 (AOP) 가 동작하지 않는다는 문제입니다

- `@Transactional` 이 붙어 있어도 내부 호출이면 트랜잭션이 적용되지 않습니다.

</br>

**왜 이런 문제가 생기는건가?**

Spring 은 `@Transactional`, `@Async`, `@Cacheable` 등의 기능을 "프록시 기반 AOP" 로 구현합니다

- 클래스에 프록시 객체를 만들어놓고
- 외부에서 호출될 때만 프록시가 개입해서 트랜잭션을 시작하거나 로그를 찍는 구조입니다

</br>

**그런데 내부 호출은?**

```java
@Service
class UserService {
    void outer() {
        inner(); // 여기서 직접 this.inner() 호출 → 프록시 안탐
    }

    @Transactional
    void inner() {
        // 트랜잭션 걸리지 않음
    }
}
```

→ `inner()` 가 아무리 `@Transactional` 이어도 `outer()` 에서 자기자신(this) 를 호출하면 프록시를 우회해서 트랜잭션 적용되지 않음

</br>

**해결 방안 정리**

| 방법                               | 설명                                                                                                |
| ---------------------------------- | --------------------------------------------------------------------------------------------------- |
| 클래스 분리                        | `UserService` → `UserTransactionalService`로 나눠서 외부 호출로 만듦                                |
| 프록시 꺼내기                      | `AopContext.currentProxy()`로 자기 자신 프록시 호출 (`@EnableAspectJAutoProxy(exposeProxy = true)`) |
| 이벤트 방식 분리                   | 트랜잭션 분리를 위해 `ApplicationEventPublisher` 사용하여 비동기 또는 분리 처리                     |
| AOP 방식 대신 트랜잭션 템플릿 사용 | `TransactionTemplate` 또는 `PlatformTransactionManager` 직접 사용                                   |

</br>

**실무 예시**

```java
@Transactional
void saveParent() {
    saveChild(); // 이 메서드는 새 트랜잭션으로 실행되길 기대하지만
}

@Transactional(propagation = REQUIRES_NEW)
void saveChild() {
    // 별도 트랜잭션으로 기대했지만 내부 호출이라 무시된다
}
```

→ `REQUIRES_NEW` 무시된다. 트랜잭션 분리 안되고 같은 트랜잭션으로 묶임

rollback 등도 전파되어 예상외 동작 실행

중요 오해: private 이면 안되고 public 접근제어면 가능한가?

→ 아닙니다. 문제는 접근 제어자와 관계없고, 호출이 `this.saveChild()` 인 것이 문제입니다

**왜 문제가 생기는가?**

Spring 의 `@Transactional` 은 프록시 객체를 통해 동작한다

- `UserService` 를 Spring 이 `UserService$$EnchancerBySpringCGLIB` 같은 프록시 객체로 감쌈
- 트랜잭션을 적용하려면 반드시 프록시를 통해서 호출되어야 `@Transactional` 이 작동함

그런데 같은 클래스 안의 메서드를 `this.saveChild()` 로 호출하면 → 프록시를 거치지 않음, 결과적으로 `@Transactional` 이 동작하지 않음

</br>
</br>

### 참고 = JPA 와 Spring 을 함께 사용할 때 발생할 수 있는 프록시 기반의 제약사항들

**1. final 키워드 : 프록시 생성 불가**

이유

- Hibernate 는 JPA 엔티티의 프록시 객체를 만들기 위해 엔티티를 상속하여 서브 클래스를 생성합니다
- 그런데 `final class`, `final method`, `final field` 는 오버라이딩이 불가능하므로 프록시 생성이 실패합니다

```java
@Entity
final class User { ... } // X → class 에 final

@Entity
class User {
    final void getName() { ... } // X → method 에 final
}
```

- 런타임 오류 발생 (프록시 생성 실패)
- Lazy 로딩, 변경 감지 기능 등 모두 비활성화 됨

</br>

**2. private 생성자 또는 기본 생성자 없음**

이유

- JPA 는 리플렉션(Reflection) 으로 엔티티 인스턴스를 생성합니다
- 이를 위해 기본 생성자 (인자가 없는 생성자)가 필요하며, `private` 이면 인스턴스 생성 불가합니다

```java
@Entity
class User {
    @Id
    private Long id;

    private String name;

    // JPA 가 사용하는 기본 생성자 (반드시 존재해야함)
    protected User() {
        this.name = "";
    }

    // 우리가 사용하는 주 생성자
    public User(String name) {
        this.name = name;
    }

    // Getter/Setter (Lombok 쓰지 않을 경우 필수)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

해결방안

- 기본 생성자는 반드시 `public` 또는 `protected` 로 선언해야 함

</br>

**3. 필드 접근 제한자 : `private` 만 쓰면 변경 감지 안되나?**

이유

- JPA 는 필드 접근 방식(field access) 또는 메서드 접근 방식 (property access) 중 하나를 선택한다
- 접근 전략에 따라 `set` 메서드가 없거나 private 일 경우 변경 감지가 안 되기도 한다

| 방식                    | 특징                                                   |
| ----------------------- | ------------------------------------------------------ |
| **field access (기본)** | `@Id`가 필드에 붙어 있으면 필드를 직접 조작            |
| **property access**     | `@Id`가 getter에 붙어 있으면 getter/setter를 통해 접근 |
| 혼용 시                 | 예측 불가능한 동작, 변경 감지 안 됨 가능성 있음        |

</br>

**4. 초기화 블록 또는 생성자에서 컬렉션 초기화**

JPA 는 프록시 생성을 위해 생성자 호출 이후에 프록시 주입을 합니다

그런데 초기화 블록에서 컬렉션 필드를 직접 초기화하면 프록시가 무시된다

```java
@Entity
class Team {
    List<Member> members = listOf(); // 직접 초기화 X → Hibernate가 프록시를 바꾸지 못함
}
```

권장 방식

```java
@OneToMany(mappedBy = "team")
members: MutableList<Member>
```

또는 `@PostLoad` 이후 초기화가 필요하면 그때 수행

</br>

**5. 프록시 초기화 안 된 상태에서 JSON 직렬화**

이유

- Lazy 로딩된 프록시 객체를 Jackson 으로 JSON 직렬화하려 하면 `LazyInitializationException` 또는 StackOverflow 발생 가능

해결 방안

| 방법                    | 설명                                           |
| ----------------------- | ---------------------------------------------- |
| DTO로 변환              | Entity → DTO로 변환 후 전달                    |
| `@JsonIgnore`           | 프록시 필드 직렬화 대상에서 제외               |
| `Hibernate5Module` 등록 | Jackson에 Hibernate 모듈 추가해 지연 로딩 무시 |

</br>

\*\*6. `@EqualsAndHashCode` , `@ToString` 자동 생성시 무한 루프

이유

- 연관관계 양방향으로 걸려 있을때, 자동생성된 `toString`, `equals` 등이 서로 참조하여 무한 루프 발생

해결

- Lombok 사용 시 `@ToString(exclude = ["team"])`, `@EqualsAndHashCode(exclude = [...])` 명시
- DTO 분리 및 순환참조 제거

</br>
</br>

1. JPA 는 리플렉션으로 엔티티를 생성한다는데, 그럼 CGLIB 으로 프록시를 생성하는 것과 뭐가 다른가?
2. JPA 는 왜 프록시 객체를 사용하나?
3. 리플렉션 VS 프록시(CGLIB) 는 각각 어떤 역할인가?

개념 정리

| 개념                         | 설명                                                                    | JPA에서의 사용 위치                    |
| ---------------------------- | ----------------------------------------------------------------------- | -------------------------------------- |
| **리플렉션 (Reflection)**    | 클래스의 생성자, 필드, 메서드 등에 런타임에 접근하고 인스턴스 생성 가능 | `newInstance()`로 엔티티 인스턴스 생성 |
| **CGLIB / ByteBuddy 프록시** | 실제 클래스를 상속하여 프록시 서브클래스를 런타임에 생성                | Lazy 로딩, 변경 감지, 동적 위임 등     |

</br>

### 1. JPA 가 리플렉션을 사용하는 이유

JPA 는 DB 에서 데이터를 조회해 객체로 매핑할 때 정확한 생성자 호출을 알 수 없기 때문에 기본생성자 + 리플렉션(`newInstance()`) 으로 인스턴스를 생성합니다

```java
// 내부적으로 이런 식
Constructor<?> ctor = User.class.getDeclaredConstructor();
ctor.setAccessible(true);
Object user = ctor.newInstance();
```

조건

- 기본 생성자(인자 없는 생성자) 필요
- `private` 이면 `setAccessible(true)` 필요
- JPA 스펙 상 `public` 또는 `protected` 기본 생성자 요구

</br>

### 2. CGLIB 프록시는 언제, 왜 사용되나?

Hibernate(JPA 구현체) 는 Lazy Loading 이나 변경 감지(Dirty Checking)를 위해 프록시 객체를 생성합니다

```java
User user = entityManager.getReference(User.class, 1L);
```

이때 `User` 가 아니라

```java
class User$HibernateProxy extends User implements HibernateProxy {
    // 내부적으로 DB 조회 로직이 지연되어 들어 있음
}
```

- 프록시 객체는 실제 필드를 갖고 있지 않고, 필요한 시점에 DB 조회
- 또는 값이 변경되었는지 추적할 수 있는 로직을 덧붙임

| 구분      | 리플렉션                  | CGLIB/프록시                             |
| --------- | ------------------------- | ---------------------------------------- |
| 용도      | 엔티티 인스턴스 생성      | Lazy 로딩, 변경 감지, 가짜 객체          |
| 언제 사용 | DB 조회 후 객체 생성할 때 | `getReference()`, 지연 로딩 시           |
| 대상      | **진짜 엔티티**           | **프록시 서브클래스**                    |
| 사용 기술 | Java Reflection API       | CGLIB, ByteBuddy 등 바이트코드 조작 기술 |

</br>

### 그럼 왜 프록시 객체를 써야 하는가? (JPA 의 목적)

목적 1 - Lazy Loading (지연 로딩)

- 연관 엔티티가 실제로 필요한 시점까지 DB 조회를 미룬다
- 메모리 절약 + 성능 최적화

```java
Order order = em.find(Order.class, 1L);
Member member = order.getMember(); // 아직 DB 조회 안됨
member.getName(); // 이때 조회
```

목적 2 - Dirty Checking (변경 감지)

- 프록시 객체 내부에 스냅샷 또는 추적 로직을 심어둠
- 커밋 시점에 값이 바뀌었는지 자동 추적 → update 쿼리 생성
