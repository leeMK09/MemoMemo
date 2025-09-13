## System.out.println 과 메모리 가시성의 관계

사실 `println` 은 락을 사용한다

- `System.out` 은 `PrintStream` 이다
- `PrintStream.println(...)` 종류의 메서드는 내부적으로 **동기화(synchronized)** 를 사용해 같은 스트림에 찍는 여러 스레드의 출력이 뒤섞이지 않도록 한다
- 바이트 쓰기/개행/flush 경로 어딘가에 `monitorenter` / `monitorexit` (모니터 락) 이 존재한다

</br>
</br>

모니터락은 메모리 배리어 효과가 있다

- JMM 에서 락 해제 (unlock, `monitorexit`) 는 release / 락 획득 (lock, `monitorenter`) 은 acquire 의 의미론을 가진다
- 결과적으로 같은 모니터 (= 같은 락 객체) 를 기준으로
  - 어떤 스레드가 락 안에서 값을 쓰고 풀면 (release)
  - 이후 다른 스레드가 같은 락을 획득하면 (acquire)
  - 앞서의 쓰기들이 가시됩니다 (= happens-before 성립)

</br>
</br>

## println 을 쓰면 무조건 메인 메모리에서 읽는가?

부분적으로 맞고, 일반화하면 오해이다

- 맞는 부분
  - `println` 이 락을 잡고/풀기 때문에 같은 락으로 동기화되는 코드 사이에서는 메모리 가시성이 확보된다
  - 이 락 경계는 JIT 재배치를 막고, 필요한 펜스를 유도한다
- 틀린 부분
  - `println` 을 호출했다고 해서 프로그램의 모든 공유 변수가 항상 "메인 메모리에서 다시 읽히는 것"은 아니다
  - 가시성 보장은 같은 락을 공유하는 코드들 사이에서만 성립한다
    - 다른 락을 쓰거나 아예 락 없이 읽는 코드에는 아무 보장도 없다

</br>

**왜 `println` 을 넣으면 동시성 버그가 사라졌다 라는 사례가 종종 보일까?**

`println` 이 락 + flush/시스템 콜 을 동반하여 스케줄링/타이밍 을 바꾸고, 메모리 배리어까지 끼어들기 때문에 우연히 레이스가 덜 터져 보이는 Heisenbug 를 초래할 수 있다

하지만 이는 완화일 뿐, 명확한 원인에 의한 치료가 아니다

- `volatile` / `synchronized` / `AtomicXxx` 등 명시적 동기화, 가시성을 통해 설계해야 한다

</br>
</br>

## 언제 volatile / 언제 synchronized ?

`volatile` 에 적합

- 단일 플래그/상태 게시 (publish)
  - 예시 → `isRunnint` , `initialized` , 구성을 읽기 전용으로 공개 등
- 여러 필드의 스냅샷을 게시할 때
  - 쓰는 쪽이 일반 필드들을 업데이트 → 마지막에 `volatile` 플래그/버전 필드 write
  - 읽는 쪽이 그 `volatile` 을 read 한 뒤 일반 필드들을 읽음 (가시성 확보)

</br>

`synchronized` 에 적합

- 원자적 복합 연산 (read-modify-write) 필요
- 불변성/불변조건 (invariant) 을 지켜야 하는 임계구역 보호
- 동일 락을 공유하는 코드 경계에서 가시성 + 상호배제 동시 충족
