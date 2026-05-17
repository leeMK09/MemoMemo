# MongoDB 트랜잭션은 어떻게 원자적으로 처리되는가?

- MongoDB에서 원자성을 이해하려면 먼저 **단일 문서 원자성**과 **multi-document transaction 원자성**을 구분해야 한다
- MongoDB는 기본적으로 하나의 document를 수정하는 작업에 대해서는 원자성을 보장한다
  - 예를 들어 하나의 사용자 문서 안에 `point`, `receiptCount`, `lastRewardedAt` 같은 필드가 있고, 하나의 update 명령으로 이 필드들을 함께 바꾸면 MongoDB는 이 변경을 중간 상태로 노출하지 않는다
  - 애플리케이션 관점에서는 그 문서가 수정되었거나, 수정되지 않았거나 둘 중 하나로 보인다
  - MongoDB 공식 문서도 하나의 document를 수정하는 write operation은 여러 필드를 변경하더라도 single-document level에서 atomic 하다고 설명한다
- 그런데 어떻게 원자성을 보장하는지에 대하여는 자세히 모른다, 이에 대해 자세히 알아보자

**사용자가 `updateOne()`을 호출하면?**

- MongoDB 서버는 먼저 해당 요청을 처리할 operation context를 만들고, WiredTiger 스토리지 엔진은 그 작업이 바라볼 데이터의 스냅샷을 만든다
- WiredTiger는 MVCC를 사용하기 때문에 어떤 작업이 시작될 때 그 시점의 일관된 뷰를 제공한다
  - 즉 다른 요청이 동시에 데이터를 바꾸고 있어도 현재 작업은 자기에게 허용된 시점의 데이터 버전을 기준으로 읽고 판단한다는 뜻이다
  - MongoDB 공식 문서에서도 WiredTiger 가 MVCC를 사용하고, operation 시작 시점에 point-in-time snapshot을 제공한다고 설명한다
- 여기서 중요한 점은 트랜잭션이 하나의 스레드 안에서만 순차 처리되기 때문에 안전하다가 아니라는 점이다
- MongoDB는 동시에 많은 요청을 처리한다, 다만 WiredTiger는 대부분의 read/write operation에서 optimistic concurrency control을 사용하고, 전역/DB/컬렉션 레벨에서 intent lock을 사용한다
  - 즉 MongoDB는 모든 작업을 큰 락으로 막아놓고 순서대로 처리하는 방식이 아니라, 여러 작업이 동시에 진행되게 두되, 실제 같은 데이터 버전을 서로 충돌되게 바꾸려는 순간 write conflic 을 감지한다
  - WiredTiger 가 대부분의 read/write operation에서 optimistic concurrency control을 사용하며, storage engine 이 충돌을 감지하면 한 작업이 write conflic를 겪고 MongoDB 가 해당 작업을 투명하게 재시도할 수 있다
- 단일 문서 `update` 는 대략 이런 흐름으로 이해하면 된다
  - 클라이언트가 `update` 요청을 보내면 MongoDB는 먼저 query filter 에 맞는 document를 찾는다
  - 그다음 해당 document의 현재 버전이 update 조건과 여전히 맞는지 확인한다
  - 조건이 맞으면 WiredTiger는 메모리 상의 page/cache에 변경 내용을 반영할 준비를 하고, 변경을 영구화할 수 있도록 journal에 기록할 내용을 준비한다
  - 이후 write concern 조건에 따라 클라이언트에게 성공 응답을 줄 시점이 달라진다
  - `w:1` 이면 Primary 가 자기 쪽에 적용한 뒤 응답할 수 있고, `w: "majority"` 이면 Primary는 다수의 data-bearing member 가 해당 write 를 받아 적용했다고 acknowledge할 때까지 기다린다

**journal, checkpoint**

- MongoDB의 WiredTiger는 checkpoint와 journal을 함께 사용한다
- checkpoint는 특정 시점의 데이터 파일이 일관된 상태라는 기준점을 만들어 준다
- 하지만 checkpoint는 매 write마다 즉시 데이터 파일 전체를 안정적으로 디스크에 반영하는 방식은 아니다
- 그래서 MongoDB 프로세스나 서버가 갑자기 죽으면 마지막 checkpoint 이후에 성공했던 write 가 데이터 파일에는 아직 완전히 반영되지 않았을 수 있다
- 이때 WiredTiger journal, 즉 write-ahead log 가 필요하다
- WiredTiger가 checkpoint 를 사용해 disk 에 있는 데이터의 일관된 view를 제공하고, 예상치 못한 종료가 checkpoint 사이에 발생하면 마지막 checkpoint 이후의 정보를 복구하기 위해 journaling 을 하게 된다

**Write-Ahead Log**

- 이름 그대로 원칙은 데이터 파일을 먼저 고치는 것이 아니라 복구에 필요한 로그를 먼저 남긴다는 원칙이다
- OS/하드웨어 레벨까지 내려가면 MongoDB 프로세스는 데이터를 수정할 때 우선 메모리의 WiredTiger cache에서 변경을 만들고, 그 변경을 복구할 수 있는 journal record를 생성한다
- 그 journal record는 운영체제의 page cache 를 거쳐 디스크 장치로 flush 된다
- 이때 실제 SSD/NVMe 장치는 내부적으로 flash translation layer, controller cache, block erase/write 단위 같은 복잡한 구조를 갖고 있기 때문에 애플리케이션이 write 시스템콜을 호출했다와 전원이 나가도 물리적으로 안전하다는 같은 말이 아니다
- 그래서 데이터베이스는 fsync 또는 그에 준하는 flush 정책, journal commit interval, storage device 의 flush semantics에 의존한다
- 장애 대응에서는 이 지점이 중요하며 EBS, 로컬 NVMe, RAID controller, 파일시스템, 커널 page cache 설정에 따라 MongoDB가 성공 응답을 줬는데 장애 후 어디까지 살아남는가의 체감이 달라질 수 있기 때문이다

**multi-document transaction**

- 예를 들어 사용자가 영수증을 제출하면 `receipts` 컬렉션에 영수증을 저장하고 `users` 컬렉션의 포인트를 증가시키고, `point_histories` 컬렉션에 이력을 남겨야 한다고 가정해보자
- 이 세 작업이 서로 다른 document 또는 collection 에 걸쳐 있다면 단일 문서 원자성만으로는 부족하다
- 이때 MongoDB transaction 은 session 과 transaction number 를 기반으로 여러 작업을 하나의 논리적 단위로 묶는다
- 트랜잭션 내부의 쓰기들은 바로 외부에 최종 확정된 변경으로 보이는 것이 아니라, 트랜잭션이 commit되기 전까지 해당 transaction context 안에서 관리된다
- 애플리케이션이 `commitTransaction()` 을 호출하면 MongoDB 는 이 transaction의 변경들이 모두 커밋 가능한지 확인하고, 커밋 상태를 기록한 뒤 외부에서 관찰 가능한 상태로 만들게 된다
- 반대로 `abortTransaction()` 이 호출되거나 commit 전에 장애가 발생하면, MongoDB는 해당 transaction의 변경을 최종 데이터로 확정하지 않는다
  - 즉 `abortTransaction()` 은 multi-document transaction 을 종료하고 그 안의 데이터 변경을 저장하지 않는다
- 이 지점에서 **실패하면 로그 파일을 삭제하는가?** 라는 것에 대해서는 데이터베이스는 보통 실패한 작업의 로그 파일을 단순 삭제해서 롤백하지는 않는다
- 로그는 삭제 대상이라기보다 복구 판단의 근거가 된다
- 커밋되지 않은 변경은 commit record 또는 commit decision 이 없기 때문에 복구 과정에서 최종 반영 대상으로 간주되지 않는다
- 반대로 commit이 완료된 트랜잭션은 checkpoint 전에 서버가 죽었더라도 journal을 통해 복구될 수 있다
  - 즉 장애 복구의 핵심은 로그를 없애는 것이 아니라 어떤 로그가 commit된 상태까지 도달했는지 판단하고, commit된 변경은 redo하며, commit되지 않은 변경은 최종 상태로 노출하지 않는 것이다
