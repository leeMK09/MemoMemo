## @EventListener

```java
private final ApplicationEventPublisher eventPublisher;

// Event Publishser
@Transactional
public String hello(String name) {
		log.info("hello);
		eventPublisher.publishEvent(new CreatedEvent());
}

// EventListener
@EventListener(CreatedEvent.class)
public void handle(CreatedEvent event) {
		log.info("Created Event : {}", event);
}
```

- 특징은 동기적으로 동작 한다
  - 그러므로 이벤트 리스너가 무거운 작업을 하게될 경우 호출하는 곳에서 해당 작업이 끝날때까지 기다리게 된다
  - 이를 방지하기 위해 `@Async` 를 사용해서 비동기 처리를 구성하기도 한다
- 예외가 전파된다
  - 동기로 동작하므로 이벤트 리스너에서 예외가 발생하게 되면 호출한 곳까지 예외가 전파된다
  - 호출한 곳에서 `try - catch` 로 처리하지 않는다면 트랜잭션이 롤백될 우려가 있다
- 동기적으로 동작하므로 `@Transactional` 기본 전파 레벨을 사용한다면 이벤트 리스너에서 트랜잭션을 선언하지 않아도 처리된다

</br>

## 이벤트 리스너에서 `@Transactional` 처리시 문제점

```java
private final ApplicationEventPublisher eventPublisher;

@Transactional
public String hello(String name) {
		log.info("hello);
		try {
				eventPublisher.publishEvent(new CreatedEvent());
		} catch(Exception e) {
				log.info("Exception);
		}
}

@Transactional
@EventListener(CreatedEvent.class)
public void handle(CreatedEvent event) {
		log.info("Event");
		throw new RuntimeException();
}
```

- 위와 같은 코드를 작성하게 되면 → 이벤트 리스너에서 트랜잭션 선언 후 예외 발생
- 이벤트를 발행한 곳에서 영속화된 객체 변경 혹은 `save` 쿼리들이 수행되지 않는다
- `JDBC transaction marked for rollback-only`
- 상위 트랜잭션과 하위 트랜잭션으로 나뉠때 하위 트랜잭션에서 예외가 발생하게 되면 트랜잭션이 닫힐때 롤백 마킹을 하게된다 → 상위 트랜잭션은 하위 트랜잭션이 롤백 마킹이 되어 있으므로 트랜잭션이 깨지게 된다
  - 만약 이벤트 리스너에서 영속화된 객체에 대해서 무언가를 수행해야 한다면
  - 트랜잭션 전파 레벨을 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 로 선언하여 처리할 수 있다
