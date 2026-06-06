## WiredTiger 의 MVCC

- WiredTiger의 MVCC는 하나의 key/document에 대해 여러 시점의 value 버전을 유지하고, 각 읽기 작업이 자기 transaction snapshot 또는 read timestamp 기준으로 보여도 되는 버전만 선택해서 읽도록 만드는 동시성 제어 방식이다
- WiredTiger는 MVCC를 사용하며, operation 시작 시점에 point-in-time-snapshot을 제공한다
- 이 snapshot은 메모리상의 데이터에 대해 일관된 view를 제공하고, WiredTiger는 checkpoint를 만들때도 특정 snapshot의 데이터를 여러 data file에 일관되게 기록한다
- 핵심은 읽기 작업이 쓰기를 막지 않는다는 것 이다
- RDB에서 흔히 lock 기반으로 생각하면, 어떤 writer가 document를 수정하는 동안 reader가 기다릴 것 같지만, MVCC에서는 reader가 굳이 최신 값을 기다리지 않는다
- reader는 내가 읽기 시작한 시점에 보였어야 하는 과거 버전을 찾아 읽는다
- 그래서 writer는 새 버전을 만들고, reader는 자기 snapshot 기준으로 적절한 옛 버전을 고른다
- 다만 lock이 아예 없다는 의미는 아니며 MongoDB 문서에 따르면 WiredTiger는 write operation에서 document level concurrency control을 사용하고, 대부분의 read/write operation에서 optimistic concurrency control을 사용한다
- Global, database, collection 레벨에서는 intent lock을 사용하며, storage engine이 두 작업의 충돌을 감지하면 하나의 작업이 write conflict를 겪고 MongoDB가 해당 작업을 재시도할 수 있다
- 즉 WiredTiger는 큰 범위의 lock으로 모든 작업을 순서대로 세우는 방식이 아닌 가능한 한 여러 작업을 동시에 진행시키고 실제 같은 document/version을 동시에 바꾸려는 순간에 충돌을 감지하는 방식이다

</br>

### WiredTiger 에서 버전은 어디에 존재하는가?

- MVCC를 이해할 때 가장 먼저 봐야 하는 것은 여러 버전이 실제로 어디에 저장되느냐이다
- WiredTiger에서 어떤 key의 버전은 크게 세 위치에 존재할 수 있다
- 첫 번째는 in-memory update chain이다
    - WiredTiger는 어떤 key에 대한 최신 변경들을 메모리 안에서 linked list 형태로 들고 있을 수 있다
    - WiredTiger 구조의 문서에 따르면 key를 읽을 때 WiredTiger는 먼저 그 key의 in-memory update들을 순회하고, 이 in-memory update들은 최신 update가 head에 있는 singly linked list, 즉 update chain으로 구성한다
- 두 번째는 disk image에 있는 버전이다
    - update chain에서 현재 reader에게 보이는 버전을 못 찾으면 WiredTiger는 disk page에 기록된 버전을 본다
    - 이 disk image는 이전 reconciliation 과정에서 디스크에 쓰기로 선택된 버전이다
- 세 번째는 history store 이다
    - reader가 아주 오래된 snapshot을 읽고 있는데, 그 reader에게 필요한 옛 버전이 현재 메모리 update chain에 없고 disk image에도 없을 수 있다
    - 이때 WiredTiger는 history store를 조회한다
    - snapshot history가 `WiredTigerHS.wt` 파일에 유지되며 history window를 늘리면 오래된 수정 값들을 더 오래 유지해야 하므로 disk 사용량이 증가한다

> 정리: 하나의 key/document에 대한 버전 탐색 순서
>
> 1. 메모리 update chain
>
> - new update → older update → older update ...
>
> 2. disk image
>
> - 마지막 reconciliation/checkpoint 과정에서 disk page에 남은 버전
>
> 3. history store
>
> - 오래된 snapshot reader를 위해 별도로 보관된 과거 버전

**예시: point 누적**

- 예를 들어 `user:100` 문서의 포인트가 원래 100점이었고, 이후 120점, 150점으로 바뀌었다고 가정해보자
- 최신 reader는 150점을 읽어야 한다
- 하지만 어떤 오래된 transaction은 자신이 시작된 시점의 snapshot 때문에 100점 또는 120점을 읽어야 할 수 있다
- WiredTiger는 이때 key 하나에 대해 최신 값 하나만 덮어쓰기 하지 않고, 여러 버전 중 reader에게 visible한 버전을 골라준다
- 중요한 점은 MongoDB의 document는 결국 WiredTiger 내부에서는 BSON value를 가진 record로 저장되고, WiredTiger는 record/key 단위로 version visibility를 판단한다는 것 이다
- MongoDB 레벨에서는 document를 읽는다고 이해하지만 storage engine 레벨에서는 B-tree page 안의 key/value record에 대해 현재 transaction이 볼 수 있는 update를 찾는다에 가깝다
