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
