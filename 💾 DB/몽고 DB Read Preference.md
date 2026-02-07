## 몽고 DB Read Preference

- 클라이언트가 읽기 요청을 어떤 멤버(Primary 또는 Secondary)로 보낼지 결정하는 규칙
- 읽기 부하 분산, 지연시간과 일관성 사이의 트레이드 오프를 어떻게 선택할 것인지의 옵션

</br>

### PRIMARY

- PRIMARY 옵션을 사용하면 클라이언트는 항상 Primary 노드로만 읽기 요청을 전송한다
- MongoDB 에서 Primary 는 모든 쓰기를 처리하는 노드이기 때문에 이 옵션을 사용할 경우 클라이언트는 가장 최신 상태의 데이터를 읽게 된다
- 이 설정에서 MongoDB 클라이언트는 읽기 요청을 만들 때 현재 Replica Set 의 멤버 중 Primary 역할을 수행 중인 노드를 확인하고 해당 노드로만 쿼리를 전달하며 Secondary 가 아무리 많아도 읽기 요청의 라우팅 대상이 되지 않는다
- 이로 인해 읽기 일관성은 가장 강하지만 읽기 트래픽이 모두 Primary 로 집중되며 Primary 의 부하가 커질 수 있다
- Primary 장래 시에는 다른 Primary 가 선출될 때까지 읽기 자체가 실패할 수 있다

</br>

### SECONDARY

- SECONDARY 옵션을 사용하면 클라이언트는 Primary 를 제외한 Secondary 노드들 중 하나로만 읽기 요청을 전송한다
    - MongoDB 는 Secondary 들의 상태를 확인한 뒤 읽기 가능한 Secondary 를 선택하여 쿼리를 전달한다
- Secondary 는 Primary 의 oplog 를 비동기적으로 복제받은 후 데이터에 반영하기 때문에 읽기 시점에 복제 지연(replication lag) 이 존재할 수 있다
    - 아직 Secondary 에 반영되지 않은 상태
- 해당 옵션을 통해 읽기 트래픽이 Primary 에서 분산되기 때문에 읽기 부하 분산에는 유리하지만 데이터 일관성이 보장되지 않는 읽기를 감수해야 한다
- 또한 Replica Set 에서 Secondary 가 장애가 나거나 없다면 읽기 자체를 실패하게 된다

</br>

### PRIMARY_PREFERRED

- 해당 옵션은 Primary 를 우선적으로 사용하되 Primary 가 장애가 발생하면 Secondary 로 읽기를 대체한다
- 클라이언트는 먼저 Primary 의 가용성을 확인하고 Primary 가 정상 상태라면 PRIMARY 옵션과 동일하게 Primary 로 읽기 요청을 전송한다
- 그러나 Primary 장애, 네트워크 단절, 선출 중인 상태 등으로 Primary 가 읽기에 응답할 수 없는 상황이 되면 클라이언트는 Secondary 를 fallback 대상으로 사용하여 읽기를 수행한다
- 장점은 평상시에는 강한 일관성을 유지하며 장애 상황에서는 가용성을 통해 서비스 지속성을 유지한다
- 다만 Primary 장애 시 Secondary 로 읽게 되는 순간에 SECONDARY 옵션과 동일하게 데이터의 최신성은 보장되지 않을 수 있다

</br>

### SECONDARY_PREFERRED

- 해당 옵션은 PRIMARY_PREFERRED 의 반대 성격을 가진다
- 즉 클라이언트는 Secondary 를 우선적으로 선택하여 읽기 요청을 보내고 Secondary 가 모두 사용 불가능한 경우에만 Primary 로 읽기 요청을 전송한다
- MongoDB 클라이언트는 먼저 Secondary 들의 상태를 확인하고 하나라도 읽기 가능한 Secondary 가 있다면 그 중 하나로 쿼리를 전달
    - Secondary 모두 장애 시 Primary 를 사용하여 읽기 수행
- 목적은 읽기 트래픽을 최대한 Primary 에게 분리하려는 것, 다만 기본적으로 Secondary 를 사용하므로 항상 복제 지연에 따른 stale read 가능성이 존재한다

</br>

### NEAREST

- NEAREST 옵션은 Primary 와 Secondary 를 구분하지 않고 네트워크 관점에서 가장 지연 시간이 짧은 노드를 선택하여 읽기 요청을 전송한다
- MongoDB 클라이언트는 Replica Set 멤버들에 대해 주기적으로 ping(왕복 지연 시간, RTT)을 측정하고 이 값이 가장 작은 노드를 선택한다
- 그 결과 상황에 따라 Primary 가 선택될 수 있고 Secondary 가 선택될 수도 있다
- 해당 옵션은 지리적으로 분산된 환경, 멀티 리전 구성에 유용하며 클라이언트가 있는 리전에 가장 가까운 노드로 읽기 요청이 전달되므로 읽기 지연 시간이 최소화된다
- 하지만 읽기 대상이 Secondary 일 수 있으며 데이터 최신성은 보장되지 않는다 또한 어떤 요청은 Primary 로 가기도 하고 어떤 요청은 Secondary 로 가게 되며 읽기 결과의 일관성 예측이 어렵다

</br>
</br>

## 복제 지연 (Replication Lag)

- Read Preference 에서 Secondary 노드에서 읽기를 수행할 때 보장되지 않는 문제는 복제 지연이다

**문제 상황**

- MongoDB Replica Set
    - Primary 1 대 + Secondary 2대
- 애플리케이션 서버는 다음과 같은 로직이 존재한다
    - 애플리케이션이 사용자 요청을 받는다
    - MongoDB 에서 `insert` 또는 `save` 를 수행한 다음
    - 곧바로 방금 저장한 데이터를 다시 `find` 로 조회해서
    - 그 결과를 기반으로 후속 로직을 수행하거나 응답을 반환한다
- 이 때 설정은 아래와 같다
    - Write Concern : primary
    - Read Preference : secondary

**문제점**

- 위 상황에서는 복제 지연 문제가 발생하게 된다
- 문서를 저장할 때 Primary 로 먼저 전달되고 Primary 는 메모리와 로그에 해당 문서를 기록한 뒤 즉시 성공 응답을 반환한다
- 이 시점에서 Secondary 에는 해당 데이터는 존재하지 않는다
    - 이유는 Secondary 는 Primary 의 oplog 를 비동기적으로 복제하기 때문이다
- 그 다음 로직에서 바로 Secondary 로 조회하게 되면 Secondary 에는 아직 해당 oplog 를 적용하지 않은 상태이므로 결과적으로 조회 결과가 없거나 이전 상태의 데이터가 반환될 수 있다

</br>

### 해결 방안들

- 복제 지연을 해결할 수 있는 방법은 아래와 같다

**Read Preference 를 PRIMARY 로 통일**

- 직관적으로 해결할 수 있는 방법이며 읽기 요청의 Read Preference 를 `primary` 로 설정하면 쓰기 직후의 읽기 요청은 항상 Primary 로 전달된다
- Primary 는 방금 전 쓰기를 처리한 노드이므로 해당 데이터는 반드시 존재한다
- 다만 읽기 트래픽이 모두 Primary 로 집중되기 때문에 읽기 부하 분산이라는 이점은 포기해야한다

**Write Concern 을 MAJORITY 로 상향**

- Write Concern 을 `w: "majority"` 로 설정하면 Primary 는 단순히 자기 자신에게 쓰는 것만으로는 성공 응답을 주지 않고 Replica Set 과반수 노드에 oplog 가 적용될 때까지 대기한다
- 그 결과 write 요청이 성공으로 반환되는 시점에는 최소한 과반수 Secondary 에도 데이터가 복제된 상태이다
- 이 상태에서 Read Preference 를 `secondary` 로 유지하더라도 대부분의 경우 Secondary 에서 조회 시 데이터가 이미 존재한다
- 다만 해당 방법은 쓰기 지연이 증가하고 Replica Set 구성이나 네트워크 상태에 따라 쓰기 성능이 크게 영향을 받을 수 있다

**Read Preference 를 MAJORITY 와 함께 사용**

- Read Preference 와 별도로 Read Concern 이라는 개념이 존재한다
- Read Concern 을 `majority` 로 설정하면 MongoDB 는 Secondary 에서 읽기를 수행하더라도 과반수에 복제 완료된 데이터만 읽도록 보장한다
- 즉 Secondary 가 아직 최신 oplog 를 적용하지 않았다면 MongoDB 는 해당 데이터를 아직 읽기 가능한 상태로 노출하지 않는다
- 이 경우 읽기 요청이 내부적으로 대기(block)될 수 있고 응답 시간이 길어질 수 있다

**트랜잭션 분리**

- MongoDB 에서 트랜잭션을 시작하면 해당 트랜잭션은 하나의 Logical Session 에 묶이게 된다
- 이 세션 안에서 수행되는 모든 읽기와 쓰기는 MongoDB 내부 규칙에 따라 Primary 에서만 수행된다
- 즉 트랜잭션을 사용하면 Read Preference 를 `secondary` 로 설정해두었더라도 트랜잭션 내부의 읽기는 강제로 Primary 에서 실행된다
- 장시간 트랜잭션이나 대규모 트랜잭션에는 부적합하다

> 트랜잭션에서 Secondary 읽기가 허용되지 않는 이유
>
> - MongoDB 는 트랜잭션에서 아래 요구사항을 만족해야 한다
>     - 같은 세션안에서 하나의 일관된 스냅샷 기준으로 여러 읽기/쓰기를 원자적으로 처리
> - 중요한 개념은 스냅샷 일관성(snapshot consistency) 이다
> - 트랜잭션은 여러 연산이 같은 시점의 데이터 상태를 기준으로 보인다는 것을 전제로 한다
> - 이를 위해 MongoDB 는 트랜잭션이 시작되는 시점에 논리적인 스냅샷 시점(cluster time)을 정하고 그 이후의 모든 읽기는 해당 스냅샷 기준으로만 이루어진다
> - Secondary 의 경우 Primary 의 oplog 를 비동기적으로 적용한다
> - 즉 같은 세션 같은 트랜잭션 안에서 Primary 와 Secondary 를 섞어 읽으면 같은 트랜잭션인데 서로 다른 과거 시점의 데이터를 보게된다는 모순이 발생하게 된다
> - 이 때문에 트랜잭션에서는 아래 제약이 존재한다
>     - 트랜잭션은 Replica Set 의 Primary 에서만 실행된다
>     - 트랜잭션 내부의 읽기/쓰기는 모두 Primary 로 라우팅된다
>     - 트랜잭션 내에서는 Read Preference 가 `primary` 가 아니면 에러가 발생한다 → 드라이버 레벨에서 Primary 로 강제한다
