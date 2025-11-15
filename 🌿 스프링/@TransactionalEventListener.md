# @TransactionalEventListener

```java
// 예시
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

**Phase**

- `BEFORE_COMMIT`
    - 커밋 이전에 실행 
- `AFTER_COMMIT`
    - default 옵션 
    - 커밋 이후에 실행 
- `AFTER_ROLLBACK`
    - 롤백 이후에 실행 
- `AFTER_COMPLETION`
    - 커밋 혹은 롤백되었을때 이후에 실행 

</br>

일반적으로 트랜잭션이 제대로 커밋된 이후 무언가 후속 작업을 해야하는 상황에서 자주 사용한다.

이벤트는 모두 **동기로 동작하게 된다** → `EventListener`, `TransactionalEventListener`

- `EventListener` 와 다른점은 이벤트 발행을 호출한 메서드 지점에서 계속 대기를 하게 되고 
- `TransactionalEventListener` 는 비즈니스 로직은 모두 실행이 완료되고 메서드가 콜 스택에서 빠져나갈 때 `@Transactional` AOP 에 의해서 트랜잭션을 마무리하는 작업에서 이벤트가 트리거 된다 (= AFTER_COMMIT 기본옵션)
    - 즉, `@Transactional` AOP 를 사용하면 프록시 객체가 트랜잭션을 핸들링하는데 이때 옵션에 따라 해당 이벤트를 발행하는 시점이 프록시 객체에 의해 수행된다 

</br>
</br>

## TransactionalEventListener 에서 영속화 객체에 대한 행위를 수행할 때 문제점 

```java 
@Slf4j
@Service
@RequiredArgsConstructor
public class HelloService {

    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    @Transactional
    public void hello(String name) {
        log.info("hello");
        User user = new User(name);
        userRepository.save(user);

        eventPublisher.publishEvent(new CreatedEvent(name));
    }
}

@Slf4j
@Component
@RequiredArgsConstructor
public class LogEventListener {

    private final LogRepository logRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(CreatedEvent event) {
        logRepository.save(new Log(event.name());
    }
}
```

- 이 경우 하위 트랜잭션이 로그에 대한 `save` 를 하게되고 상위 트랜잭션이 `User` 에 대한 `save` 를 수행하게 된다 
- 이때 문제는 하위 트랜잭션의 로그 엔티티 `save` 는 수행되지 않는다 
- **이유**
    - **모든 이벤트는 기본적으로 동기로 동작하고 트랜잭션은 같은 트랜잭션을 사용하게 된다**
    - **하나의 트랜잭션은 딱 한번만 커밋을 수행할 수 있다**
    - **이미 커밋이 되었기 때문에 이후에 같은 트랜잭션으로 어떤 행위를 하려고 하니까 반영이 안된 것 이다**

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

- 위 문제를 해결하기 위해 트랜잭션의 전파레벨을 격리하여 새로운 트랜잭션을 열도록 하면 로그 엔티티를 저장할 수 있다

</br>
</br>

## 이벤트에 Entity 식별자가 아닌 엔티티를 필드로 가지고 있을때 발생하는 문제 

```java
@Entity
@Table(name = "logs")
public class Log {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @OneToMany(mappedBy = "log", cascade = {CascadeType.MERGE, CascadeType.PERSIST})
    private List<ChildEntity> childEntities = new ArrayList<>();

    public void setName(name) {
        this.name = name;
    }

    public void mutateChild() {
        childEntities.forEach(childEntity -> childEntity.setName("mutated");
    }
}

---
@Getter
public class CreatedEvent {
    private final Log log;

    public CreatedEvent(Log log) {
        this.log = log;
    }
}

---
@Async
@Transactional
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handle(CreatedEvent event) {
    Log log = event.getLog();
    log.mutateChild();
    logRepository.save(log);
}
```

- `TransactionalEventListener` 에서 이벤트안에 엔티티를 가져와 해당 엔티티의 객체 그래프 엔티티들을 수정하는 로직 
- 문제 시나리오 
    1. 클라이언트가 `Log` 에 이름을 변경하는 API 호출
    1. 후처리 작업(`@TransactionalEventListener`) 에는 꽤 무거운 작업을 수행한다 → 비동기 작업으로 분리하여 수행
    1. **무거운 작업이 수행되는 중간에 다시 `Log` 에 이름을 변경하는 API 를 클라이언트가 호출한다**
    1. **해당 작업으로 인해 `Log` 에는 변경된 이름이 조회된다 
    1. **이후 후처리 작업이 모두 완료되어 `save` 를 수행할때 `Log` 의 객체 그래프에 대한 변경과 이전의 이름을 가지고 있는 `Log` 또한 `save` 를 수행하게 된다**
    - 즉, 후처리 작업이 오래 걸리므로 엔티티의 이전 값으로 이름이 덮어씌워진다 
- **이벤트 뿐만 아닌 다른 트랜잭션을 사용할 때는 이전에 사용했던 엔티티를 사용하지 않는 것이 좋다**

**해결하려면?**

- 이벤트에는 엔티티 필드를 가지고 있지 말고 식별자를 가진 후 직접 조회를 통해서 사용해야 한다 
- 낙관적 락(Optimistic Lock) 을 통해서 동시 업데이트 문제를 방지할 수 있다 
- `@DynamicUpdate` 를 사용하여 변경된 필드만 업데이트하도록 처리할 수 있다
