## Grafana Tempo

- Grafana Tempo 는 trace 를 수집해서 저장하고, 나중에 trace ID 나 조건으로 다시 조회할 수 있게 해주는 분산 추적 백엔드이다
- 그런데 Tempo 의 중요한 특징은 단순히 span 을 파일처럼 쌓아두는 저장소 뿐 아니라, 수신 경로와 저장 경로와 조회 경로가 서로 다른 컴포넌트 역할로 나뉘어 있고, 이 역할을 어떻게 한 프로세스로 묶을지 여러 프로세스로 쪼갤지를 배포 모드가 결정한다는 점이다

</br>

## Tempo 의 세 가지 배포 모드가 무엇인가

### Monolithic 모드

- Monolithic 모드는 이름 그대로 Tempo 의 모든 역할을 하나의 프로세스 안에 전부 집어넣는 방식
  - 여기서 말하는 모든 역할은 distributor, ingester, querier, compactor 같은 컴포넌트이다
- 즉 span 을 받는 역할도 같은 프로세스가 하고, 메모리와 WAL 에 쌓는 역할도 같은 프로세스가 하고, S3에 block 을 올리는 역할도 같은 프로세스가 하고, 조회 요청을 처리하는 역할도 같은 프로세스가 한다
  - `-target=all` 이 기본값이며, 하나의 프로세스 안에 모든 컴포넌트가 포함된다

**장점**

- 이 모드의 장점은 배포가 가장 쉽다는 점이다.
  - 프로세스가 하나이므로 디스커버리도 거의 신경 쓸 일이 없고, 링도 사실상 크게 의미가 없고, 초기 PoC 나 로컬 테스트에서는 가장 단순한 방식이다

**단점**

- 하지만 실제 운영에서는 치명적인 한계가 존재한다
  - 프로세스 하나가 죽으면 수신, 저장, 조회 기능이 한번에 멈춘다
  - 그리고 트래픽이 늘어나면 distributor 만 힘든 것이 아닌 같은 프로세스 내부에 있는 ingester, querier, compactor 가 서로 CPU 와 메모리를 잡아먹기 때문에, 어떤 역할이 병목인지 분리해서 대응하기가 매우 어렵다

**Monolithic 의 본질적 트레이드 오프**

- 설정은 가장 쉽지만, 장애 격리와 확장성이 가장 나쁘다
- 개발/테스트에는 좋지만 운영에서 문제가 생겼을 때 무엇이 문제인지 분리해내는 능력이 부족하다
- 이 단점을 해결하려면 결국 모드를 바꾸거나, 최소한 수신 앞단에 OTel Collector 를 두어서 Tempo 로 들어오는 부하를 다듬고, trace volume 을 줄이거나 샘플링을 걸어야 한다
- 다시 말해 Monolithic 자체를 고도화해서 해결하기보다는 보통은 Scalable Single Binary 나 Microservices 로 넘어가는 방식의 해결책을 제시한다

</br>

### Scalable Single Binary 모드

- 패키징은 Monolithic 이지만 동작은 Microservices 처럼 동작한다
- 같은 바이너리 안에 distributor, ingester, querier, compactor 가 모두 들어있기는 한데, 그 바이너리를 여러 개 띄운다
- 그러면 각 인스턴스는 모든 역할을 할 수 있는 Tempo 인스턴스가 되지만, 내부적으로 memberlist gosship 과 consistent hash ring 을 사용해서 어떤 trace 는 어느 ingester 가 맡고, 어떤 block 은 어느 compactor 가 맡고, 어떤 쿼리는 어떤 querier 가 처리할지를 분산시킨다
  - `-target=scalable-single-binary` 와 함께 동일한 바이너리를 여러 개 띄워 수평 확장하고 내부적으로 memberlist gosship 과 consistent hash ring 을 사용한다
- 운영에서 해당 모드를 자주 사용하는데 이유는 예를 들어 ECS Fargate 에서 task 를 2개, 3개, 4개 정도 두고 운영한다고 가정하면, Microservices 처럼 distributor 서비스, ingester 서비스, querier 서비스, compactor 서비스를 각각 따로 서비스화하고 오토스케일링 포인트를 전부 나누는 것은 꽤 부담스럽다. 그런데 Monolithic 은 너무 운영하기에 장애 격리가 어렵고 그 사이에서 Scalable Single Binary 는 복잡도를 과하게 늘리지 않으면서도 장애를 한 프로세스에 몰아넣지 않는 절충안이 된다
  - 공식 문서에서도 ECS task 2 ~ 5개 규모에 현실적인 모드라고 정의되어 있다

**내부 동작**

- OTel Collector 가 ALB 를 통해 Tempo 인스턴스 중 아무곳으로 span 을 보낸다고 가정해보자
- 그 인스턴스의 distributor 가 span 을 받는다
- 그런데 그 distributor 가 자기 자신이 무조건 저장하는 것은 아니다
- distributor 는 traceID 를 hash 해서 ring 을 조회하고, 그 결과에 따라 실제로 데이터를 맡아야 할 ingester 를 결정한다
- 그래서 ALB 가 Tempo-1 으로 요청을 보냈더라도, ring 결과상 Tempo-2 의 ingester 가 담당자라면 내부적으로 Tempo-2 로 넘길 수 있다
  - 공식 문서에도 ALB 가 Tempo-1 에 요청을 보내도 ring 결과에 따라 Tempo-2 ingester 가 담당할 수 있다고 설명한다

**장점**

- 수신은 어느 인스턴스나 할 수 있고, 저장 책임은 ring 이 균등하게 나눠 갖는다
- 따라서 ALB 가 유입 트래픽을 분산하고, ring 이 저장 책임을 분산하는 2단계 분산 구조가 된다
- 이 구조 덕분에 distributor 가 붙어 있는 어느 노드가 요청을 받더라도 클러스터 전체 관점에서는 trace 를 비교적 균등하게 분산할 수 있다

**트레이드 오프**

- 같은 바이너리 안에 모든 컴포넌트가 같이 들어있기 때문에, 하나의 인스턴스에서 쿼리 부하가 치솟으면 그 인스턴스의 CPU 와 메모리를 수신/저장 경로와 공유하게 된다
- 즉 Microservices 처럼 distributor 만 따로 늘리고, querier 만 따로 늘리는 식의 역할별 독립 스케일링은 불가능하다
- 여전히 모든 역할이 한 프로세스에서 공존한다는 제약이 남아 있으며 조회가 많은 시간대와 수집이 많은 시간대가 겹치면, 간접적으로 서로 방해가 될 수 있다
- 이 문제를 완하는 방법은 여러개가 있다
  - 첫 번째는 앞단에 OTel Collector 를 두고 batch, memory limiter, retry queue 를 적극적으로 사용하는 것 이다
  - 그러면 Tempo 쪽 distributor 가 순간적으로 튀는 트래픽에 덜 직접적으로 맞게 된다
  - 두 번째는 샘플링이나 attribute 정리를 통해 trace volume 자체를 줄이는 것 이다
  - 세 번째는 조회 부하가 커지는 경우 Grafana 쿼리 패턴을 조정하고, retention 이나 block 크기, compaction 정책을 손봐서 read path 의 효율을 높이는 것 이다
  - 네 번째는 결국 한계에 도달하면 Microservices 로 넘어가는 것이다 즉 Scalable Single Binary 는 운영 복잡도를 낮춘 대가로 역할별 스케일링 자유도를 일부 포기한 모드라고 생각해도 된다

</br>

## Microservices 모드

- distributor, ingester, querier, compactor 를 정말로 별도 프로세스, 별도 배포 단위로 분리하는 방식이다
- 대규모 프로덕션에 권장된다

**장점**

- 역할별로 따로 늘릴 수 있다는 점이다
- 예를 들어 수집 트래픽이 갑자기 늘어서 distributor 와 ingester 가 바쁘다면 그쪽만 증설하면 된다
- 반대로 Grafana 에서 사람들이 trace 검색을 많이 해서 querier 가 바쁘면 querier 만 늘리면 된다
- compactor 가 block 병합 때문에 뒤처지면 compactor 만 늘릴 수도 있다
- 장애 도메인도 분리되며 compactor 가 이상해졌다고 distributor 수신이 같이 죽어야 할 이유가 없고, querier 가 과부하하고 ingester 가 같이 불안정해질 필요는 없다

**단점**

- 이 모드의 대가는 복잡성이다
- 서비스 수가 늘어나고 디스커버리 대상도 많아지고, 각 컴포넌트의 리소스 특성이 다르기 때문에 CPU/메모리/네트워크 튜닝 포인트도 늘어나게 된다
- 운영자는 Tempo 를 하나 운영하는 것이 아닌 사실상 분산된 여러 역할의 하위 시스템 묶음을 운영하게 된다
- 따라서 관찰 포인트도 많아지고, 배포 전략도 더 섬세해야 하며 ECS 든 Kubernetes 든 인프라 계층이 더 중요해진다

</br>

## Scalable Single Binary 가 실제로 클러스터처럼 동작하는 이유

- Scalable Single Binary 가 단순히 같은 바이너리 여러 개가 아닌 실제로 분산 시스템처럼 동작하려면, 각 인스턴스가 서로 알아야 하고, 어떤 데이터의 책임이 누구에게 있는지를 알아야 한다
- 이때 필요한 것이 `memberlist gosship` 과 `consistent hash ring` 이다

### Memberlist의 역할

- Memberlist 는 외부 KeyValue store 없이 Tempo 인스턴스들이 서로의 존재를 발견하고 상태를 교환하는 메커니즘이다
- 각 프로세스는 시작 시 `join_members` 에 지정된 주소로 접속하고, 서로 살아 있는지 heartbeat 를 주고받으며, 누가 새로 들어왔고 누가 사라졌는지를 gossip 방식으로 전파한다
- 여기서 중요한 것은 memberlist 가 저장 자체를 담당하는 것이 아닌 클러스터의 멤버십 정보와 상태 공유를 담당한다는 점이다
- 즉 누가 클러스터에 참여 중인지, 누가 빠졌는지를 Tempo 인스턴스들이 서로 아는 것 이다
- 이 기반이 있어야 링을 유지할 수 있다
- ECS Fargate 환경에서는 이 부분이 꽤 중요하다, task IP 가 바뀔 수 있고, 정적인 호스트 목록을 수동 관리하기 어렵기 때문이다
- 그래서 Cloud Map DNS 를 `join_members` 에 둔 예시들이 있으며 ALB 용 private hosted zone 과 별개로 구분한다
  - ALB DNS 는 클라이언트가 어디로 접속할지를 위한 것이고 Cloud Map 은 Tempo 인스턴스들이 서로를 어떻게 찾을지를 위한 것, 둘은 용도가 다르다
- 이 구조의 장점은 외부 Consul/etcd 를 따로 두지 않아도 된다는 점이다
  - 즉 운영 요소를 줄일 수 있다
- 그러나 단점은 gosship 기반 멤버십의 수렴 시간이 있어야 하며, 네트워크 파티션이나 일시적인 불안정이 있을 때 클러스터 멤버 상태가 순간적으로 일관되지 않을 가능성을 운영자가 이해하고 있어야 한다는 점이다
  - 아주 큰 규모이거나 네트워크 제어를 더 엄격히 하고 싶다면 외부 KeyValue 기반 구성이 더 잘 맞을 수 있다
  - 하지만 Fargate 기반 중간 규모 운영에서는 memberlist 가 단순성과 실용성 측면에서 충분히 매력적이다

### Consistent Hash Ring 의 역할

- Memberlist 가 누가 살아 있는지를 알려준다면, ring 은 어떤 trace 를 누가 맡을 것인가를 결정한다
- Tempo 내부에는 ingester ring, metrics-generator ring, compactor ring, distributor ring 이라는 독립 링이 존재한다
- 이 중에서 가장 핵심은 ingester ring 이다
- distributor 가 span 을 어느 ingester 로 보내야 할지 결정하기 때문이다
- compactor ring 은 어떤 compactor 가 어떤 block 을 병합할지 정하는 데 사용된다
- 동작 원리
  - 링은 개념적으로 원형 해시 공간이고, 각 Tempo 인스턴스는 그 공간에 여러 개의 토큰을 가지게 된다
  - traceID 가 들어오면 `hash(traceID)` 로 해시 공간의 한 지점을 계산하고, 시계 방향으로 가장 가까운 토큰의 소유자가 담당자가 된다
  - 인스턴스마다 토큰을 여러 개 두는 이유는 분산 균형을 맞추기 위해서이다
  - 토큰이 적으면 특정 구간이 한 노드에 크게 몰릴 수 있지만, 토큰이 많으면 해시 공간이 잘게 잘려서 더 균등하게 나뉘게 된다
  - 대략적으로 `num_tokens: 512` 와 함께 토큰 수가 많을수록 부하가 더 균등해진다
- 이 ring 구조의 장점은 노드 수가 변할 때 전체 데이터의 담당 관계가 일부만 이동한다는 점이다
- 모든 trace 가 다시 전부 다른 노드로 재배치되는 것이 아니라, 링 구조상 필요한 일부 영역만 새 노드에게 넘어간다
- 그래서 수평 확장과 축소에 잘 맞는다
- 다만 트레이드 오프가 있다
  - 노드가 추가되거나 제거될 때 ring이 재구성되면서 담당 범위가 바뀌므로, 그 순간의 flush 되지 않은 데이터나 in-flight 요청 처리에 대한 이해가 필요하다
  - 따라서 replication_factor 를 어떻게 줄지, WAL 을 어디에 둘지, restart 시 어떻게 복구할지를 같이 봐야한다
  - 즉 ring 하나만 잘 넣는다고 끝나는 것이 아닌 ring 변경 시 데이터 보전 전략이 같이 있어야 한다

</br>

## 요청 하나가 어떻게 저장되는가

- OTel Collector 와 AWS 환경의 자원들을 사용한다면 OTel Collector 가 OTLP/HTTP 로 데이터를 보내고, ALB 가 아무 Tempo 인스턴스의 distributor 로 보낸다 이후 distributor 가 ring 을 조회하여 담당 ingester 를 찾고, ingester 가 메모리와 WAL 을 거쳐 complete block 을 만들고, 최종적으로 S3 에 저장한다

**좀 더 자세한 내부 동작 과정**

1. 애플리케이션 -> OTel Collector -> Tempo

- 먼저 애플리케이션은 보통 직접 Tempo 로 보내지 않고 OTel Collector 로 span 을 보낸다
- Otel Collector 는 span 을 모아서 batch 처리하고 재시도도 하고, 필요하면 memory limiter 도 걸 수 있다. 이 단계는 Tempo 앞단의 완충기 역할을 한다
- 이후 ALB 는 요청을 Tempo 인스턴스 중 하나로 전달하며 여기까지가 일반적인 로드밸런싱 작업이다

2. Tempo -> ingester (Tempo 내부 컴포넌트)

- 그런데 이 지점이 최종 저장 담당자를 결정하는 지점은 아니다, 요청을 받은 인스턴스의 distributor 는 span 에서 traceID 를 보고 해시를 계산한 뒤, ingester ring 을 조회해서 실제 담당 ingester 를 찾는다
- 그래서 ALB 가 무작위로 분산한 결과와 상관없이, 같은 traceID 는 ring 규칙에 따라 항상 같은 ingester 계열로 모이게 된다
- 이것이 중요한 이유는 같은 trace 의 span 들이 여러 인스턴스들로 흩어져 버리면 later join 이나 complete trace 조회가 비효율적이기 때문이다
- ring 은 이 trace locality 를 어느 정도 유지하려고 하며 hash(traceID) 후 replication_factor 개수의 ingester 를 결정한다
- 담당 ingester 가 span 을 받으면 우선 메모리의 LiveTrace 버퍼에 넣는다.
- 이 단계는 아직 추척이 진행 중인 trace 를 메모리에 유지하는 단계이다
- trace 는 span 이 순차적으로 조금씩 들어올 수 있으므로 너무 빨리 디스크 block 을 굳혀 버리면 조각이 지나치게 많이 생길 수 있다
  - 그래서 `trace_idel_period` 와 `trace_idel_period` 가 중요하다
- 일정 시간 동안 추가 span 이 안 오면 idle 로 판단해서 WAL HeadBlock 을 내리고, 너무 오래 메모리에만 두지 않도록 최대 live 기반이 지나면 강제로 WAL 로 내린다

3. ingester (Tempo 내부 컴포넌트) -> WAL

- 그 다음 WAL 단게로 가게 된다
- WAL 은 디스크에 쓰는 write-ahead log 이다
- 아직 최종 block 으로 굳어서 S3 에 올라가진 않았지만, 최소한 프로세스가 죽더라도 메모리의 trace 를 통째로 날리지는 않겠다는 안전장치이다
- 재시작 시 WAL 에서 reload 해서 복구한다

4. WAL -> Complete Block

- WAL 에 쌓인 데이터는 일정 크기나 일정 시간이 되면 complete block 으로 전환된다
- `max_block_bytes` 나 `max_block_duration` 조건을 넘으면 Apache Parquet 형식의 block 을 만들고, 이때 bloom filter 와 index 도 같이 생성한다
- 이 단계가 중요한데 이유는 조회 시 S3 에 있는 모든 데이터를 다 훓는 것이 아니라, block 단위로 이 block 에 찾는 trace 가 있을 가능성이 있는지를 먼저 판단하기 때문이다
- bloom filter 와 index 가 그 조회 효율을 담당하게 된다

5. Complete Block -> S3

- 마지막으로 complete block 이 S3 에 flush 된다
- 그런데 flush 했다고 해서 ingester 가 즉시 잊어버리는 것은 아니다
- `complete_block_timeout` 동안 ingester 가 block 을 계속 보유하고 querier 가 최신 데이터를 직접 조회할 수 있다
- 이것은 막 저장된 최신 데이터는 아직 object storage 만 확인하는 것보다 ingester 에서 바로 보는 편이 빠를 수 있다는 운영적 현실을 반영한 것 이다
- 즉 read path 는 S3 만 확인하는 것이 아닌 최신 데이터는 ingester 도 함께 본다

</br>

## Read Path 는 왜 더 복합적인가

- Tempo 의 read path 는 단순히 S3 에서 파일 하나 읽기가 아닌 대시보드(ex: Grafana) -> Query Frontend -> Querier x N -> Ingester 와 S3 를 함께 조회한 뒤 결과를 병합하고 deduplication 하는 흐름이다

**좀 더 자세한 구조의 의미**

- Grafana 사용자가 trace 조회를 요청하면, query frontend 가 그 요청을 받아 적절히 나누고, querier 들이 병렬로 처리한다
- 이때 querier 는 두 군데를 볼 수 있는데, 하나는 아직 S3 에 완전히 반영되지 않았거나 최신 상태를 들고 있는 ingester 이고 다른 하나는 이미 object storage 에 저장된 block 들이다
- 최신 데이터와 장기 저장 데이터가 경계 없이 이어져야 하기 때문에, read path 는 이 두 계층을 동시에 조회해서 결과를 합쳐야 한다
  - 결과 병합과 deduplication 을 적용
- 여기서 bloom filter 의 존재가 매우 중요하며 bloom filter 는 이 block 에 해당 traceID 가 절대 없는지를 빠르게 판정하기 위한 구조이다
  - false positive 는 있을 수 있지만 false negative 는 없다 즉 bloom filter 가 없다고 하면 안봐도 되고, 있을 수도 있다고 하면 index 와 Parquet 까지 더 들어가서 확인해야 한다
  - 이 구조가 없다면 querier 는 S3 block 을 훨씬 더 많이 열어봐야 하고, query latency 와 object storage 비용 모두 올라가게 된다
- read path 의 트레이드 오프는 성능과 비용, 그리고 최신성 사이에 있다
  - 최신 trace 를 빨리 보고 싶으면 ingester 가 최근 block 을 좀 더 오래 들고 있어야 한다
  - 하지만 너무 오래 들고 있으면 메모리 사용량이 커진다
  - S3 block 을 크게 만들면 object 수가 줄고 조회 overhead 도 줄 수 있지만, block 하나를 여는 비용과 특정 trace 찾기까지의 scan 단위가 커질 수 있다
  - 반대로 block 을 너무 작게 만들면 object 수가 많아지고 compaction 부담이 커지게 된다
  - 즉 write path 와 read path 는 block 크기, flush 주기, complete_block_timeout 같은 설정으로 서로 영향을 주고 받게 된다
