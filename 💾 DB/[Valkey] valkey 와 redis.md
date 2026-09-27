# Redis 의 SSPL 라이센스

- Redis 는 2009년부터 BSD 라이센스 즉 오픈소스였다 그러나 2024년 3월 Redis Ltd. 가 SSPL 라이센스로 변경했다
- SSPL 은 Redis를 관리형 서비스로 제공하려면 그 서비스 스택 전체의 소스를 공개하라는 요구사항이다
    - 즉 AWS ElastiCache, Google Memorystore, Oracle, Alibaba - Redis를 관리형으로 팔던 클라우드 벤더들을 저격한 내용
- 그래서 벤더들은 기존 Redis 코어 메인테이너들이 라이선스 변경 직전 마지막 BSD 버전인 Redis 7.2.4를 포크하여 `Valkey`를 만들었다
- 후원사는 AWS, Google Cloud, Oracle, Ericsson, Snap 등이었고 그 결과 `Valkey`는 포크치고는 이례적으로 처음부터 풀타임 커밋 인력이 붙었다
- 그래서 Redis 또한 2025년 5월 AGPLv3를 다시 추가하여 Redis도 다시 OSI 오픈소스가 맞다고 명시한다
- 다만 이걸로 다시 Redis로 원복되진 않았다
    - AGPL도 BSD처럼 마음 편히 쓰는 라이선스는 아니었다
    - 더불어서 이미 1년 넘게 커뮤니티가 갈라진 뒤였고 클라우드 3사의 관리형 제품은 이미 `Valkey`로 이전하게 되었다

</br>

## Valkey 8.0

- 포크 직후의 Valkey 7.2는 Redis 7.2와 코드가 사실상 동일했다, 차별점은 라이선스밖에 없었다
- 그래서 Valkey 팀이 첫 메이저 버전에서 성능에 올인하게 된다
- 처리량
    - Valkey 7.2 : 360K RPS
    - Valkey 8.0 : 1.19M RPS (+230%)
- 평균 지연시간
    - Valkey 7.2 : 1.792 ms
    - Valkey 8.0 : 0.542 ms (-69.8%)
- 병목의 핵심은 `epoll_wait` 이었다, 소켓에 읽을 게 있는지 커널에 물어보는 시스템 콜인데 이걸 메인 스레드가 혼자 하다 보니 CPU 시간의 20% 이상을 여기에 사용하고 있었다

**Valkey는 Redis 7.2.4 직계 포크**

- 와이어 프로토콜(RESP2/RESP3) 동일, 클라이언트 라이브러리를 변경하지 않아도 된다
    - Lettuce, Jedis, redis-py, ioredis, go-redis 전부 그대로 붙는다
- 명령어 또한 동일하여 `SET`, `HSET`, `ZADD`, `EXPIRE` 전부 같다
- CLI 또한 상호 호환된다
- RDB/AOF 파일 또한 호환되며 마이그레이션 시 덤프 파일을 그대로 얹을 수 있다

</br>

## epoll_wait 20% 병목

- 1990년대 서버의 기본형은 커넥션 1개 = 스레드 1개 였다
    - 커넥션 10,000개면 스레드 10,000개
- 스레드당 스택 기본 8MB -> 10,000개면 주소공간 80GB
- 스케줄러가 1만 개를 돌려막으면 컨텍스트 스위칭 -> CPU가 일이 아니라 전환에 모두 소모됨
- 캐시 지역성 파괴
- 1999년 Dan Kegel이 이걸 "The C10K Problem"으로 정식화함
- 1만 개 커넥션 중 어느 순간 실제로 데이터가 도착해 있는 건 보통 수십 개이다, 그럼 스레드 1만 개가 아니라 지금 준비된 것이 누구냐를 물어보는 스레드 1개면 충분해보인다
- 이것이 I/O 멀티플렉싱이고 Redis/Valkey 아키텍쳐의 출발점이다

**1세대 select / poll 의 한계**

```c
int select(int nfds, fd_set *readfds, fd_set *writefds, fd_set *exceptfds, struct timeval *timeout);
```

- `select`는 감시할 fd 집합을 매 호출마다 통째로 넘긴다, 그러나 문제는 존재한다
    - FD_SETSIZE 1024 상한 (비트마스크가 컴파일 타임에 고정됨)
    - 매 호출마다 전체 집합을 유저 스페이스에서 커널 스페이스로 복사
    - 커널이 전부 순회 O(n)
    - 리턴 후 유저도 전부 순회해서 누가 준비되었는지 찾아야 함 O(n)
    - 집합이 호출 중 파괴되어 매번 재구성

**epoll**

- 핵심 아이디어는 관심 목록을 매번 넘기지 말고, 커널이 기억하게 하자

```c
// 관심 fd를 한번만 등록
epoll_ctl(epfd, EPOLL_CTL_ADD, fd, &event);
// 준비된 것만 받아온다
int n = epoll_wait(epfd, events, maxevents, timeout);
```

- 커널 내부
    - 관심 목록 -> 레드블랙 트리에 보관 O(log n)
    - 준비 목록 -> 연결 리스트, 네트워크 인터럽트가 발생하는 그 시점에 커널이 직접 여기에 넣어둠
    - epoll_wait은 그 준비 목록을 꺼내오기만 하면 됨 O(준비된 개수)
- 즉 1만 개를 스캔해서 3개를 찾는 것 보다 이미 담겨 있는 3개를 꺼내는 것, 커넥션 수와 비용이 분리됨

**epoll_wait이 병목된 이유**

- epoll이 없앤 것은 커넥션 수에 비례하는 비용이며 epoll이 없애지 못한 것은 호출 1회당 고정 비용이다
- 시스템 콜 1회의 고정 비용
    - 유저 모드 -> 커널 모드 전환 (레지스터 저장/복구, 스택 전환 등)
    - 권한 검사, 인자 복사
    - Spectre/Meltdown 완화 이후 추가된 비용, 주소 공간 전환 및 TLB 플러시
- 그리고 Redis/Valkey 이벤트 루프의 구조

```javascript
while (!stop) {
    beforeSleep(); // 지연된 쓰기 flush 및 만료 처리
    numevents = epoll_wait(...); // 루프 한 바퀴에 반드시 1회 처리
    for (j = 0; j < numevents; j++) {
        readQueryFromClient(); // 소켓 read
        processInputBuffer(); // RESP 파싱
        call(cmd); // 명령 실행
        addReply(); // 응답 버퍼에 쌓기
    }
}
```

- 루프 한 바퀴 = epoll_wait 한번
- 이 루프를 도는 스레드가 곧 명령을 실행하는 바로 그 스레드
- 초당 100만 요청을 처리하려면 이 루프는 초당 수만~수십만 바퀴 돌려야 한다, 그만큼 epoll_wait을 호출하고 그 시간 동안 명령 실행은 0이다
- 즉 명령 실행에 써야할 CPU 의 1/5을 누가 말을 걸었는가 확인하는 곳에 사용하고 있었다

**Redis 6.0 (2020)에도 io-threads가 존재했다**

- Redis 6 방식 (동기 fan-out)
    - 메인 스레드: pending client 목록을 io-threads 개수로 분할
    - 메인 스레드: 각 io 스레드에 할당 -> 다 끝날 때까지 스핀하며 대기
    - 메인 스레드: 전원 완료 -> 명령 실행
    - 메인 스레드: 응답을 다시 분할 -> 할당 -> 다시 대기
- 이 구조의 결함은 아래와 같다
    - 메인이 모든 io 스레드를 기다린다, 8개 중 7개가 끝나도 가장 느린 1개가 전체를 멈춤 -> 동기 배리어
    - 일 없는 io 스레드가 sleep 전까지 최대 100만 회 가량 스핀 -> busy-poll CPU 낭비
    - pending client 수가 io-threads \* 2 미만이면 io 스레드를 아예 안쓴다 -> 활성화 임계값
    - epoll_wait은 여전히 메인 스레드가 한다
- 즉 Redis 6의 io-threads는 병렬 처리가 아닌 메인 스레드가 잠깐 도와달라고 부르고 다시 기다리는 구조

**Valkey 8의 해법**

- 메인과 io 스레드가 동시에 돈다, 메인은 일을 넘기고 기다리지 않고 바로 명령 실행으로 넘어간다 -> 비동기화
- 소켓 read, RESP 파싱, 응답 write, epoll_wait, 메모리 해제(free)까지 메인 스레드에는 명령 실행만 남는다 -> io 스레드 일감 확대
- 여러 스레드가 동시에 epoll_wait을 부르면 thundering herd와 이벤트 중복 배분 문제가 발생한다, Valkey는 실행 주체는 옮기되 동시 실행은 1개로 제한해서 복잡도를 피했다 -> 동시성 제약
- io 스레드가 파싱한 명령을 배치로 모아 메인에 넘긴다, 루프 오버헤드가 배치 크기로 나눠지게 된다
- 진짜 최종 병목은 메모리
    - IO를 걷어냈더니 메인 스레드 CPU가 100%를 찍는데도 처리량이 기대만큼 안 올라갔다
    - 메인 스레드는 일하는 중이 아닌 메모리를 기다리는 중이었다
    - DRAM 접근은 L1 캐시보다 약 50배 느리다
    - lookupKey 함수 하나가 전체 실행 시간의 40% 이상을 먹고 있었다
    - 해시 테이블 조회는 각 단계의 다음 주소가 이전 단계를 읽어봐야 하는 구조이다, CPU의 실행과 하드웨어 프리패치는 다음에 읽을 주소를 예측해서 미리 당겨오는 장치인데, 포인터 체인(해시 테이블 조회 구조)은 예측이 원리적으로 불가능하다 그래서 매 단계 캐시 미스시 수백 사이클 stall이 직렬로 쌓이게 된다
- 해법: Memory Access Amortization (인터리빙)
    - 한 키를 끝까지 따라간 뒤 다음 키로 가는게 아니라 배치 안의 여러 키를 한 단계씩 번갈아 따라간다
    - 그러면 키 A의 메모리 대기 시간 동안 키 B,C,D 의 접근이 진행되어 대기가 서로 겹친다
    - Valkey 적용 -> 메인 스레드가 명령 배치를 받으면 실행 전에 `dictPrefetch` 로 배치에 등장하는 모든 키의 캐시 라인을 미리 당겨온다
    - prefetch는 한 번에 여러 키를 미리 아는 상태에서만 가능하며 그 상태를 만들어준 게 IO 스레딩의 배칭이다

</br>

## 명령 실행은 단일 스레드 구조

- `main thread`
    - 이벤트 루프 조율
    - 명령 실행 (call)
    - 키 만료 (active expire cycle)
    - maxmemory 축출 (eviction)
    - fork() 호출 (BGSAVE / AOF rewrite)
- `io threads (io-threads N)`
    - 소켓 read / RESP 파싱
    - 응답 write
    - TCP epoll_wait (Valkey 8 ~)
    - 메모리 해제
- `bio threads (오래전부터 존재)`
    - fsync (AOF)
    - lazy free (UNLINK, FLUSHALL ASYNC)
    - 파일 close
- 별도 프로세스 : RDB/AOF 자식, fork 산물

</br>

## 장/단점

- 장점
    - 원자성이 공짜 : `INCR`, `LPUSH`, `SETNX`, `HINCRBY` 가 락 없이 원자적이다, 실행 스레드가 하나뿐
    - 락 비용 X : 멀티스레드였다면 mutex 획득/해제 같은 락 비용 과 경합시 컨텍스트 스위칭, 캐시 라인 핑퐁, 데드락 순서 등의 비용이 없다
- 단점
    - 코어 1개가 상한
        - io-threads를 늘리면 네트워크 처리는 분산됨
        - 그래서 Valkey는 스케일업이 아닌 스케일 아웃(샤딩/클러스터)으로 확장
    - Head-of-Line Blocking
        - 느린 명령 하나가 전부 세운다
        - 큐가 하나뿐이라 앞의 명령이 오래 걸리면 뒤가 전부 대기
