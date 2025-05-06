## HikariCP 에서 Dead lock 이 발생하는 경우

소비자 스레드(consumer thread) 개수에 따라 HikariCP 의 maxmimum pool size 개수를 제대로 설정하지 못하는 경우 발생 !

예시를 통해 확인

Thread count 와 HikariCP maximum pool size 의 조건은 아래와 같다

- Thread Count : 1개
- HikariCP Maximum Pool Size : 1개

그 후 DB Entity 를 Save 하는 로직을 실행

```java
@Entity
class Entity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;
    private String title;
    private String contents;
}

@Transaction
public Entity save(Entity entity) {
    return repository.save(entity);
}
```

### 실행 흐름

1. Thread 가 `Repository.save(entity)` 를 실행하기 위해 `Transaction` 을 시작한다
2. Root Transaction 용 Connection 을 하나 가져온다
3. Transaction 을 시작하고 `Repository.save(entity)` 를 실행하려는데 Connection 이 하나 더 필요하다고 요청한다 !
4. Thread 는 Hikari Pool 에게 Connection 을 요청한다
   - Hikari Pool 은 전체 Connection Pool 에서 idle 상태의 Connection 을 조회한다
   - 그러나 Pool Size 는 1개 이고 그나마 있던 1개는 이미 Thread 에서 사용하고 있다
   - 사용가능한 Connection 이 없으므로 `handOffQueue` 에서 누군가 Connection 을 반납하길 기다리며 30초 동안 대기한다
   - 예상하듯 요청한 Thread 가 Connection 을 사용하므로 Thread 가 끝나지 않는 이상 반납하지 않는다
   - 결국 30초가 지나고 Connection Timeout 발생
     - `hikari-pool-1 - Connection is not available, request timed out after 30000ms.` 에러 발생
5. SQLTransientConnectionException 으로 인해 Sub Transaction 은 Rollback 이 된다
6. Sub Transaction 이 Rollback 되므로 rollbackOnly = true 가 되며 Root Transaction 또한 Rollback 됩니다
   - JpaTransactionManager 를 사용하고 있음
7. Rollback 되는 것과 동시에 Root Transaction 용 Connection 은 다시 Pool 로 반납됩니다

이렇게 Thread 내에서 필요한 Conneciton 개수가 모자라게 되면서 **Dead Lock 상태에 빠지며** 제대로 실행되지 않습니다

</br>

### 원인은 ?

위 예시의 ID 전략이 결과적으로 Thread 한개의 두개 이상의 Connection 을 요청하게 되는 원인이 된다

- [🌿 [DB Connection Pool] JPA ID 전략으로 인한 HikariCP 데드락 발생 ?](https://github.com/leeMK09/MemoMemo/blob/main/%F0%9F%8C%BF%20%EC%8A%A4%ED%94%84%EB%A7%81/%5BDB%20Connection%20Pool%5D%20JPA%20ID%20%EC%A0%84%EB%9E%B5%EC%9C%BC%EB%A1%9C%20%EC%9D%B8%ED%95%9C%20HikariCP%20%EB%8D%B0%EB%93%9C%EB%9D%BD%20%EB%B0%9C%EC%83%9D%20%3F.md)

</br>

- 참고) https://techblog.woowahan.com/2664/
