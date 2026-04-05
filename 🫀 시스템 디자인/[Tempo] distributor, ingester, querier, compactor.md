## distributor

- span 요청을 처음 받아주는 입구
- 애플리케이션이나 OTel Collector 가 OTLP, Jaeger Zipkin 같은 프로토콜로 trace 데이터를 보내면 Tempo 안에서 가장 먼저 그 요청을 받는 쪽이 distributor 이다
  - write path 가 OTel Collector → ALB → Tempo 인스턴스의 Distributor 가 수신
- 그러나 distributor 가 span 을 받아놓고 자기 메모리에 직접 오래 들고 있는 것은 아니다
- distributor 의 핵심 역할은 요청으로 들어온 trace 를 어느 ingester 가 담당해야 하는가를 결정해서 넘기는 역할을 수행한다
  - 즉 distributor 는 storage 엔진이라기보다 라우터에 가깝다

**동작 방식**

- 애플리케이션에서 어떤 요청 하나가 들어와서 span 여러 개가 생성되었다고 가정해보자
- 이 span 들은 traceID 를 공유한다
- distributor 는 이 traceID 를 보고 hash 를 계산한다
- 그 다음 ingester ring 을 조회한다
- 그리고 해당 traceID 는 ingester-A 가 담당자다 혹은 replication factor 가 2라면 ingester-A 와 ingester-B 가 같이 담당자라고 판단한다
- 그 뒤 distributor 는 그 span 을 해당 ingester 들에게 전달한다
  - `hash(traceID)` 후 ring 조회를 통해 담당 ingester 를 찾는다
- 즉 distributor 는 trace 데이터를 처음 받아서, 적절한 ingester 에게 분배하는 진입점이자 라우팅 계층이다

**장점**

- ALB 가 Tempo 인스턴스 아무 곳으로 보내더라도 distributor 가 내부 규칙에 따라 올바른 ingester 에게 보내주기 때문에, 클라이언트는 어드 노드가 실제 저장 담당인지를 몰라도 된다

**단점**

- distributor 자체가 바빠지면 수신 지점이 병목이 될 수 있다
- 그래서 앞단에 OTel Collector 를 두어 batch, retry, queue, memory limiter 로 distributor 가 순간 트래픽을 그대로 맞지 않게 완충하는 구성이 자주 사용된다

</br>

## ingester

- 실제로 trace 데이터를 받아서 메모리와 디스크와 object storage 를 이어지게 만드는 저장 담당 컴포넌트이다
- 쉽게 말하면 distributor 가 누가 맡을지 정한다면 ingester 는 실제로 맡아서 쌓는다
- 먼저 LiveTrace 라는 메모리 버퍼에 trace 데이터를 둔다
- 그 다음 조건이 맞으면 WAL 에 데이터를 쓴다
- 그 다음 Complete Block 을 만들고, 최종적으로 S3 에 flush 한다

**동작 방식**

- trace 는 span 하나로 끝나지 않는 경우가 많다
  - 예를 들어 어떤 API 요청이 들어오면 Controller span, Service span, DB Query span, 외부 API call span 이 순서대로 조금씩 생길 수 있다
  - 이 span 들이 거의 동시에 올 수도 있지만, 약간의 시간차를 두고 들어올 수도 있다
- 그래서 ingester 는 span 이 도착할 때마다 바로 S3 에 파일처럼 써버리지 않는다
- 그렇게 해버리면 같은 trace 가 너무 잘게 쪼개져서 나중에 조회가 비효율적이기 때문이다
- 대신 ingester 는 먼저 메모리에서 같은 traceID 를 가진 span 들을 모은다
  - 이 상태가 LiveTrace 이다
- 그리고 일정 시간 동안 추가 span 이 안 들어오면 이 trace 는 일단 현재 시점 기준으로 마무리됐다고 보자라고 판단해서 WAL 쪽으로 내린다
- 또는 너무 오래 메모리에 들고 있지 않도록 최대 보유 시간이 지나면 강제로 WAL 로 내린다
  - 공식 문서에는 `trace_idle_period`, `trace_live_period` 가 바로 이 설정이다
- 그 다음 WAL 에 기록한다. WAL 은 재시작이나 크래시 상황에서 아직 S3 로 완전히 넘어가지 않은 최신 데이터를 보호하기 위한 디스크 계층이다
- 즉 ingester 가 죽어도 메모리에만 있던 trace 를 통째로 잃지 않도록 하는 안전장치이다 → WAL 의 목적은 데이터 손실 방지
- 그 뒤 블록 크기나 시간이 일정 기준을 넘으면 ingester 는 Complete Block 을 만든다
- 이때 Parquet 형태로 정리하고, bloom filter 와 index 도 같이 만든다
- 그리고 이 block 을 S3 같은 object storage 에 올린다
- 즉 ingester 는 단순히 메모리에 쥐고 있는 역할이 아니라, 실시간 유입 데이터를 장기 보관 가능한 block 구조로 변환해주는 중간 계층이다
  - ingester 는 distributor 가 넘긴 span 을 실제로 받아서, 메모리에 모으고, WAL 에 안전하게 기록하고, block 으로 변환해서 object storage 에 저장하는 저장 핵심 컴포넌트

</br>

## querier

- 저장된 trace 를 다시 찾아서 읽어오는 역할을 수행한다
  - 즉 수집 경로의 핵심이 distributor 와 ingester 라면 조회 경로의 핵심은 querier 이다
- Grafana 에서 어떤 traceID 를 조회하거나, 특정 조건으로 trace 를 검색했다고 가정해보자
- 이 요청은 query frontend 를 거쳐 querier 에게 전달된다
- querier 는 그 요청을 받아서 이 trace 가 최신 데이터인가, 이미 S3 block 에 들어간 오래된 데이터인가를 판단한다
- ingester 와 object storage 를 함께 조회한다.
  - 공식 문서에서는 read path 에서 querier 가 ingester 와 S3 를 함께 보고 결과를 병합한다고 되어 있다

**왜 굳이 둘다(S3, ingester) 조회하는 가**

- 최신 trace 는 아직 ingester 메모리나 최근 block 에만 남아 있고, 완전히 장기 저장 구조로 정리되지 않았을 수 있다
- 반면 조금 시간이 지난 trace 는 이미 S3 block 안에 있다
- 사용자는 그냥 trace 보여줘 라고만 요청했지, 그 trace 가 어디에 저장돼 있는지를 신경 쓰지 않는다
- 그래서 querier 가 이 두 계층을 모두 조회해서 하나의 결과로 합쳐줘야 한다
- 그리고 S3 block 을 읽을 때는 아무 block 이나 다 조회하지 않는다
  - 공식 문서에도 bloom filter 와 index 를 사용해서 이 block 에는 이 trace 가 없다고 확실히 말할 수 있는가를 먼저 빠르게 확인한다
  - 없다고 판단되면 그 block 은 건너뛰고, 있을 가능성이 있는 block 만 더 깊이 읽는다. 이 과정을 querier 가 수행한다
- 즉 querier 는 단순히 DB select 실행기가 아닌 최신 데이터 계층과 장기 저장 계층을 동시에 조회하고, 필요한 block 만 선택적으로 열고, 결과를 병합하고, 중복 제거까지 수행하는 조회 엔진이다
  - querier 는 ingester 와 object storage 를 조회하여 사용자가 요청한 trace 를 찾아오고, 여러 소스의 결과를 합쳐 최종 응답을 만드는 조회 담당 컴포넌트이다

</br>

## compactor

- 이미 저장된 block 들을 주기적으로 정리하고 병합하는 역할을 한다
- ingester는 실시간 수집을 우선하기 때문에 block 을 처음 만들 때는 상대적으로 작고 많은 block 이 생길 수 있다
- 이 상태가 오래 누적되면 S3 object 수가 많아지고, querier 가 bloom filter 와 index 를 확인해야 할 block 수도 늘어나서 조회 비용과 시간이 함께 증가할 수 있다
  - 공식 문서에서도 ingester 가 처음 flush 하는 block 은 작고 많으며, compactor 가 이를 더 큰 block 으로 병합한다고 한다
- compactor 는 주기적으로 S3 block 목록을 확인한다
- 그 다음 같은 테넌트, 비슷한 시간 범위의 block 들을 그룹화한다
- 그리고 compactor ring 을 조회해서 이 block 묶음은 어떤 compactor 가 담당하는가를 결정한다
- 담당자로 정해진 compactor 만 그 block 들을 다운로드하고, Parquet 단위로 병합하고, 새 blockID 로 다시 업로드한다
- 이후 예전 block 들은 compacted 상태로 표시하거나 삭제 대상이 된다.
  - compactor 는 단순히 정리만 하는 것이 아닌 조회 비율과 비용을 같이 다듬는다
- block 수가 줄어들면 querier 가 봐야 할 object 수가 줄어든다
- S3 LIST/GET 비용도 줄 수 있다
- 중복 span 도 정리할 수 있다
- retention 이 지난 데이터 삭제도 compactor 단계와 연결된다.
- 즉 compactor 는 장기적으로 Tempo 저장소를 조회 친화적으로 유지하는 백그라운드 정리 엔진이다
  - compactor 는 작은 block 들을 더 큰 block 으로 병합하고, object 수를 줄이고, 중복을 정리하고, retention 정책을 적용하여 저장소를 효율적으로 유지하는 컴포넌트
