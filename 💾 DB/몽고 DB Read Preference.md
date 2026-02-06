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
