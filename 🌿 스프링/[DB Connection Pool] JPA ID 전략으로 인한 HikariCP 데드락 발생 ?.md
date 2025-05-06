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

위 와 같은 엔티티가 존재하고 비즈니스 로직(`save`) 를 실행한다고 가정할 때 데드락이 발생할 가능이 있다.

Spring Boot 2.x 부터 `hibernate.id.new_generator_mappings` 의 기본값이 `true` 로 변경되면서 `GenerationType.AUTO` 의 동작방식이 변경되었다

이로 인해 MySQL 에서도 `AUTO` 가 `IDENTITY` 가 아닌 `SequenceStyleGenerator` 를 사용하게 되었다.

## Hibernate 의 `hibernate.id.new_generator_mappins` 속성 변화

- **Spring Boot 1.5 x 이전**
  - `hibernate.id.new_generator_mappings` 기본값 : `false`
  - `GenerationType.AUTO` 사용 시 데이터베이스에 따라 전략 결정 ([Hibernate](https://discourse.hibernate.org/t/how-to-selectively-specify-an-id-creation-strategy-depending-on-the-connected-dbms-mariadb-10-3-or-above-and-below-and-oracle/10211?utm_source=chatgpt.com))
    - MySQL → `IDENTITY` 전략 사용
    - 시퀀스를 지원하는 DB → `SEQUENCE` 전략 사용
- **Spring Boot 2.x 이후**
  - `hibernate.id.new_generator_mappings` 기본값 : `true`
  - `GenerationType.AUTO` 사용 시 `SequenceStyleGenerator` 를 기본적으로 사용
    - 시퀀스를 지원하는 DB → 해당 시퀀스 테이블 사용
    - 시퀀스를 지원하는 않는 DB (MySQL 등) → `hibernate_sequence` 라는 테이블을 생성하여 시퀀스 역할 수행

이러한 변화로 인해 MySQL 에서 `Generation.AUTO` 를 사용할 경우, 예상과 달리 `IDENTITY` 가 아닌 테이블 기반 시퀀스를 사용하게 되어 혼란이 생길 수 있다

</br>

## 🤔 그래서 ID 전략에서의 DB 커넥션 데드락 발생 요인은 ?

**@GeneratedValue(strategy = Generation.AUTO) 가 원인**

내부적으로 `SequenceStyleGenerator` 로 ID 를 생성하게 되는데 MySQL 기준으로 `hibernate_sequence` 라는 테이블에 단일 Row 를 사용하여 ID 값을 생성하게 된다

여기서 `hibernate_sequence` 테이블을 조회 및 update 하면서 `Sub Transation` 이 발생한다

```sql
SELECT next_val as id_val FROM hibernate_sequence FOR UPDATE;
```

MySQL FOR UPDATE (X Lock) 쿼리는 조회한 ROW 에 Lock 을 걸어 현재 트랜잭션이 끝나기 전 까지 다른 session 접근을 막는다

동시성 제어 및 Sub Transaction 을 사용한 이유는 Root Transaction 이 끝나기 전 까지 다른 thread 에서 ID 채번을 할 수 없게 하도록 → 즉, 데이터 일관성 유지 (추측)

</br>

### 만약 데드락이 발생하려면

- Thread Count : 1개
- HikariCP Maximum Pool Size : 1개

위 조건으로 설정한 경우 혹은 많은 요청으로 인해 HikariCP Pool Size 가 버티지 못할 경우 발생할 수 있다

이유는

- 하나의 스레드 (하나의 요청) 에서 2개 이상의 DB 커넥션을 요청하므로 위와 같은 조건인 경우 Root Transaction 에서 DB Coneciton 을 가지고 이후 ID 전략으로 인해 DB Connection 을 또 요청하게 됨, 그러나 1개가 최대 Pool Size 이므로 30 초 동안 커넥션을 얻기위해 대기, 이로 인해 데드락이 발생하게 된다

</br>

### 정리

어떤 Thread는 운이 없게 DB 커넥션을 할당받지 못하여 30초 후에 `SQLTransientConnectionException` 을 던질 수도 있습니다

그렇기 때문에 최적의 Pool Size 를 설정하기 위해서는 **Dead Lock 을 피할 수 있는 pool 개수 + a** 가 되어야 한다

이에 대한 방법으로는 성능 테스트를 수행하면서 최적의 Pool Size 를 찾는 방법이 있을 것 같다.
