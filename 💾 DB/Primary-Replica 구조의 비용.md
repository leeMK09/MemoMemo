## 복제 지연 (Replication Lag)

- Replication 은 Primary 를 실시간으로 데이터가 동기화되지 않는다
- 특히 AWS RDS Read Replica는 비동기 복제이다
- 발생하는 경우
  - Primary 에서 커밋 시 Replica 로 변경사항 전달 및 Replica 적용
  - 이 과정에서 네트워크 지연 혹은 자원이 부족하여 지연될 가능성이 있음
- 예를들어
  - 쓰기 폭증시(대량의 INSERT/UPDATE)
  - 장시간 트랜잭션 등
- 해결방법들
  - 쓰기 직후 조회는 무조건 Primary 로 조회
  - 읽기 라우팅 정책을 분리 (read-after-write)
  - 스토리지 레벨 동기화 엔진 고려 (ex. Aurora)

</br>

## 장애 발생시 읽기 확장이 장애전파로 변함

- Primary 가 느려진다면 Replica 도 느려진다
- Replica 는 독립적인 시스템이 아니다
- 발생하는 경우
  - Replica 는 Primary 의 변경 로그를 소비해야 한다
  - Primary 가 병목 → Replica binlog 폭증
  - Primary CPU 90% 발생시 Replica lag 급증 및 읽기 트래픽이 Replica 로 몰릴경우 Replica 또한 지연
- Replica 는 성능 격리 수단이며 장애 격리 수단은 아니다
- 쓰기 병목 해결을 먼저 우선적으로 해결해야한다
- 캐시 계층을 병행하여 사용하는 방법도 고려할 필요가 있음

</br>

## Failover 의 비용

- Replica 승격은 서비스 이벤트이다
- 승격은 Primary 장애 시 Replica DB 가 승격되며 Endpoint/DNS 가 변경하며 커넥션이 전부 끊긴다
- 문제 상황
  - DB 는 살아났는데 애플리케이션 커넥션 풀은 전부 죽는다
  - 재시도 폭증시 2차 장애 발생
- 해결 방안들
  - 커넥션 풀 사이즈 혹은 timeout 조정이 필요하다
  - 중간에 Proxy 형태의 구조를 생각해볼 필요도 있다

</br>

## 쓰기 확장은 해결되지 않는다

- 쓰기 성능은 여전히 Primary 1대를 운영
- Replica가 많으면 복제 비용이 증가하며 이에 따른 Primary 부담이 증가함
- 해결 방안들
  - 특정 기준으로 데이터를 샤딩
  - 이벤트를 분리하여 로직상에서 분리
  - Aurora 등 구조 자체가 다른 엔진으로 변경

</br>

### Aurora 는 해당 문제들을 어떻게 해결하는건가

- Aurora 는 Primary-Replica 구조를 유지하면서도 복제 지연과 장애 전환 비용을 스토리지 레벨에서 제거한 DB

**AWS RDS 구조**

- Primary (Writer) → binlog / WAL → 네트워크 전송 → Replica 에서 재생
- 복제가 비동기 처리로 동작
- Replica 는 Primary 를 따라가는 입장, Lag 문제가 필연적으로 발생함

**Aurora 구조**

- Writer / Readers → 공유 분산 스토리지
- Writer 와 Reader 가 같은 스토리지를 본다
- 복제가 DB 로부터 복제가 아닌 스토리지 레벨에서 복제를 수행한다
- Writer 는 Redo log 만 스토리지에 기록, Reader 는 같은 로그를 직접 읽음

</br>

### AWS RDS Proxy 는 해당 문제들을 어떻게 해결하는건가

- Failover 시 발생하는 문제는 기존 커넥션이 모두 끊긴다는 것 이다
- 발생하는 상황
  - Primary 장애 → DB 커넥션이 전부 끊김 → 애플리케이션은 커넥션 풀 재시도 / 동시에 수백,수천 개 connect → DB 가 살아나도 연결 요청이 폭증하며 2차 장애 발생
- RDS Proxy 는 DB 앞단에 놓인 관리형 커넥션 풀러
  - 구조
  - Application → RDS Proxy (소수의 안정된 커넥션) → DB (Writer / Reader)

**RDS Proxy 가 해결한 것들**

- Failover 시 커넥션 폭발 방지
  - 앱 <-> Proxy 커넥션 유지
  - Proxy <-> DB 커넥션만 재연결
  - 앱은 끊기지 않고 유지
- 커넥션 수 감소
  - 앱 인스턴스가 100개인 경우 각자 커넥션 풀이 200개 라면 총 2,000개의 커넥션이 발생
  - Proxy 가 내부적으로 소수의 커넥션만을 유지하도록 처리
- IAM / Secret 분리
  - 앱은 DB 의 Secret 내용을 몰라도 됨 (ex. password)
  - Proxy 가 Secrets Manager 를 연동하여 처리
