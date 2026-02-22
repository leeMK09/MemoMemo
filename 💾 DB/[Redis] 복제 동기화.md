## Redis 복제 동기화

- Redis 복제 동기화는 두 가지 정보를 관리한다
    - `replication Id(replid)` : 복제 그룹을 식별하는 ID 값
    - `offset` : 데이터 진행 상황을 나타내는 offset 값
- 복제는 기본적으로 비동기로 동작한다
    - 클라이언트 write 성공 시점과 replica 반영 시점은 다를 수 있다
- offset 은 Kafka 처럼 순차적으로 증가하는 숫자로 복제본이 주 인스턴스(master) 의 데이터를 어디까지 따라왔는지를 나타낸다
    - 정상적으로 동기화가 잘 이루어지고 있을 때는 이 offset 이 순서대로 계속 증가하게 된다
    - 예시로 어떤 복제 인스턴스의 offset 이 메인 인스턴스의 offset 보다 낮게 되어 있다면 이는 복제가 덜 된 상태라는 뜻이고 같은 ID 그룹 내에 있다면 Redis 는 이 상황을 더 동기화해야하는 정상적인 상태로 보고 추가 데이터를 보내며 sync 를 계속 진행한다
    - 즉 같은 ID 그룹 + offset 만 뒤쳐진 경우에는 점진적인 동기화로 문제를 해결한다
- 만약 복제 인스턴스가 다른 ID 그룹에 속해 있거나 메인 인스턴스가 알지 못하는 offset 을 가지고 있는 식으로 상태가 꼬여있다면 Redis 는 복잡한 불일치를 하나하나 맞추려고 하지 않는다
    - 대신 부분적으로 고치기보다는 전체를 다시 맞추는 식으로 전체 네트워크 동기화(full resync) 를 수행한다
- full resync 가 시작되면 메인 인스턴스는 즉시 RDB 방식으로 스냅샷을 생성한다
    - 이때는 설정된 스냅샷 정책 여부와 상관없이 데이터의 원자성과 일관성을 보장하기 위해 강제로 스냅샷을 떠서 복제 인스턴스로 전송한다

**동작 방식**

1. Replica 가 Primary 에 연결해서 `PSYNC <replid> <offset>` 를 보낸다
2. Primary 는 요청한 `replid` 가 내가 아는 복제 인스턴스인지, 요청 `offset` 이후 데이터가 나의 `repl-backlog` 에 아직 남아 있는지를 검사한다
3. 조건이 맞다면 `+FULLRESYNC <new_replid> <offset>` 로 전체 재동기화를 수행한다
4. 전체 재동기화시 Primary 는 복제용 RDB 스냅샷을 만들고 (설정된 주기적 save 와는 별개로), Replica 에 전송한다
5. RDB 전송 중 돌아온 신규 write 는 복제 버퍼에 쌓아뒀다가 RDB 로드 이후 이어서 보내어 정합성을 맞춘다

> repl-backlog
>
> - Primary 가 최근 write 명령 스트림을 버퍼로 보관하는 공간

**복제 동기화 설계 의도**

- Redis 는 정합성 회복 속도를 우선시 한다
- 복잡한 diff 계산으로 상태를 맞추기보다는 `replid + offset + backlog` 로 빠르게 판단하고 불가능하면 전체 스냅샷으로 단순하게 적용한다
- 복구 경로를 예측 가능하게 만든 설계 의도

**트레이드 오프**

- 장점
    - 구현/운영이 단순하고 장애 후 복구 경로가 명확하다
    - 짧은 네트워크 단절은 partial sync 로 매우 저비용 복구가 가능하다
- 단점
    - backlog 범위를 넘어가거나 계보가 바뀌면 full sync 가 발생해 CPU/메모리/디스크/네트워크 부하가 크다
    - 복제 자체가 비동기라 장애 타이밍에 따라 최근 write 유실 가능성이 있다

**문제 시나리오와 완화 방법**

- 네트워크 단절이 길어지면 backlog 를 초과, full sync 발생
    - `repl-backlog-size` 를 트래픽 피크 기준으로 충분히 키워 partial sync 성공률을 높인다
- Primary 재시작/승격으로 계보가 바뀌면 full sync 가능성 증가
    - Sential/Cluster failover 설계와 메모리 여유를 확보하고 재동기화 폭주 시간대를 피한다
- full sync 중 `BGSAVE` fork + Copy on Write 로 메모리 + CPU 스파이크 발생
    - 메모리 확보, write 폭주 시간대 분산, 필요시 diskless replication (`repl-diskless-sync`) 검토
- 쓰기 성공 직후 Primary 장래로 replica 미반영 → 데이터 유실
    - 강한 보장이 필요한 상황이라면 `WAIT` (필요 replica 수까지 ack 대기) 또는 정책적으로 `min-replicas-to-write` / `min-replicas-max-lag` 를 사용한다
