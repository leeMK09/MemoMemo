자바는 1.0 부터 존재한 `synchronized` 와 `BLOCKED` 상태를 통한 임계 영역 관리의 한계를 극복하기 위해 자바 1.5 부터 `Lock` 인터페이스와 `ReentrantLock` 구현체를 제공한다

</br>

**synchronized 단점**

- **무한대기**
  - `BLOCKED` 상태의 스레드는 락이 풀릴 때 까지 무한 대기한다
  - 특정 시간까지만 대기하는 타임아웃이 없다
  - 중간에 인터럽트 실행시에도 반응 X
- **공정성**
  - 락이 돌아왔을때 `BLOCKED` 상태의 여러 스레드 중에 어떤 스레드가 락을 획득할 지 알 수 없다
  - 최악의 경우 특정 스레드가 너무 오랜기간 락을 획득하지 못할 수 있다

</br>

**Lock 인터페이스**

```java
package java.util.concurrent locks;

public interface Lock {
		void lock();

		void lockInterruptibly() throws InterruptedException;

		boolean tryLock();

		boolean tryLock(long time, TimeUnit unit) throws InterruptedException;

		void unlock();

		Condition newCondition();
}
```

`Lock` 인터페이스는 동시성 프로그래밍에서 쓰이는 안전한 임계 영역을 위한 락을 구현하는데 사용된다

`Lock` 인터페이스는 다음과 같은 메서드를 제공한다. 대표적인 구현체로 `ReentrantLock` 이 있다

</br>

`void lock()`

- 락을 획득한다
  - 만약 다른 스레드가 이미 락을 획득했다면, 락이 풀릴 때까지 현재 스레드는 대기(`WAITING`) 한다
  - 이 메서드는 인터럽트에 응답하지 않는다
  - 예시) 맛집에 한번 줄을 서면 끝까지 기다린다. 친구가 다른 맛집을 찾았다고 중간에 연락해도 포기하지 않고 기다린다

**주의!**

여기서 사용하는 락은 객체 내부에 있는 모니터락이 아니다 !

`Lock` 인터페이스와 `ReentrantLock` 이 제공하는 기능이다

모니터 락과 `BLOCKED` 상태는 `synchonized` 에서만 사용된다

</br>

`void lockInterruptibly()`

- 락 획득을 시도하되, 다른 스레드가 인터럽트할 수 있도록 한다 → 대기 중 인터럽트 발생 시 락 획득포기
  - 만약 다른 스레드가 이미 락을 획득했다면 현재 스레드는 락을 획득할 때까지 대기한다
  - 대기 중에 인터럽트가 발생하면 `InterruptedException` 이 발생하며 락 획득을 포기한다
  - 예시) 맛집에 한 번 줄을 서서 기다린다. 다만 친구가 다른 맛집을 찾았다고 중간에 연락하면 포기한다

</br>

`boolean tryLock()`

- 락 획득을 시도하고 즉시 성공 여부를 반환한다 → 대기하지 않음
  - 만약 다른 스레드가 이미 락을 획득했다면 `false` 를 반환하고, 그렇지 않으면 락을 획득하고 `true` 를 반환한다
  - 예시) 맛집에 대기 줄이 없으면 바로 들어가고, 대기 줄이 있으면 즉시 포기한다

</br>

`boolean tryLock(long time, TimeUnit unit)`

- 주어진 시간 동안 락 획득을 시도한다 → 특정 시간동안만 대기 혹은 인터럽트 발생시 락 획득 포기
  - 주어진 시간 안에 락을 획득하면 `true` 를 반환한다
  - 주어진 시간이 지나도 락을 획득하지 못한 경우 `false` 를 반환한다
  - 이 메서드는 대기 중 인터럽트가 발생하면 `InterruptedException` 이 발생하며 락 획득을 포기한다
  - 예시) 맛집에 줄을 서지만 특정 시간만큼만 기다린다. 특정 시간이 지나도 계속 줄을 서야 한다면 포기한다. 또한 친구가 다른 맛집을 찾았다고 중간에 연락하면 포기한다

</br>

`void unlock()`

- 락을 해제한다
  - 락을 해제하면 락 획득을 대기 중인 스레드 중 하나가 락을 획득할 수 있게 된다
  - **락을 획득한 스레드가 호출해야 하며, 그렇지 않으면 `IllegalMonitorStateException` 이 발생할 수 있다**
  - 예시) 식당안에 있는 손님이 밥을 먹고 나간다. 식당에 자리가 하나 난다. 기다리는 손님께 이런 사실을 알려주어야 한다. 기다리던 손님 중 한 명이 식당에 들어간다

</br>

`Condition newCondition()`

- `Condition` 객체를 생성하여 반환한다
  - `Condition` 객체는 락과 결합되어 사용되며 스레드가 특정 조건을 기다리거나 신호를 받을 수 있도록 한다
  - 이는 `Object` 클래스의 `wait`, `notify`, `notifyAll` 메서드와 유사한 역할을 한다

</br>

이 메서드들을 사용하면 고수준의 동기화 기법을 구현할 수 있다

`Lock` 인터페이스는 `synchronized` 블록보다 더 많은 유연성을 제공하며, 특히 락을 특정 시간만큼만 시도하거나, 인터럽트 가능한 락을 사용할 때 유용하다

이 메서드들을 보면 알겠지만 다양한 메서드를 통해 `synchronized` 의 단점인 무한 대기 문제도 깔끔하게 해결할 수 있다

**참고: `lock()` 메서드는 인터럽트에 응하지 않는다고 되어있다. 이 메서드의 의도는 인터럽트가 발생해도 무시하고 락을 기다리는 것 이다**

</br>

**앞서 대기(`WAITING`) 상태의 스레드에 인터럽트가 발생하면 대기 상태를 빠져나온다고 배웠다**

**그런데 `lock()` 메서드의 설명을 보면 대기(`WAITING`) 상태인데 인터럽트에 응하지 않는다고 되어있다. 어떻게 된 것 일까?**

**`lock()` 을 호출해서 락을 얻기 위해 대기중인 스레드에 인터럽트가 발생하면 순간 대기 상태를 빠져나오는 것은 맞다**

**그래서 아주 짧지만 `WAITING` → `RUNNABLE` 이 된다**

**그런데 `lock()` 메서드 안에서 해당 스레드를 다시 `WAITING` 상태로 강제로 변경해버린다.**

**이런 원리로 인터럽트를 무시하는 것 이다**

**참고로 인터럽트가 필요하면 `lockInterruptibly()` 를 사용하면 된다**

**새로운 `Lock` 은 개발자에게 다양한 선택권을 제공한다**

</br>
</br>

## 공정성

`Lock` 인터페이스가 제공하는 다양한 기능 덕분에 `synchronized` 의 단점인 무한 대기 문제가 해결되었다

그런데 공정성에 대한 문제가 남아있다

`synchronized` 단점

- 공정성
  - 락이 돌아왔을때 `BLOCKED` 상태의 여러 스레드 중에 어떤 스레드가 락을 획득할지 알 수 없다. 최악의 경우 특정 스레드가 너무 오랜기간 락을 획득하지 못할 수도 있다

`Lock` 인터페이스의 대표적인 구현체로 `ReentrantLock` 이 있는데, 이 클래스는 스레드가 공정하게 락을 얻을 수 있는 모드를 제공한다

즉 `Lock` 인터페이스는 공정성 기능을 제공하지 않지만 `Lock` 인터페이스의 구현체인 `ReentrantLock` 은 공정성 모드가 지원된다

```java
public class ReentrantLockEx {

    // 비공정 모드 락
    private final Lock nonFairLock = new ReentrantLock();

    // 공정 모드 락
    private final Lock fairLock = new ReentrantLock(true);

    public void nonFairLockTest() {
        nonFairLock.lock();

        try {
            // 임계 영역
        } finally {
            nonFairLock.unlock();
        }
    }

    public void fairLockTest() {
        fairLock.lock();
        try {
            // 임계 영역
        } finally {
            fairLock.unlock();
        }
    }
}
```

`ReentrantLock` 락은 공정성 (fairness) 모드와 비공정(non-fair) 모드로 설정할 수 있으며, 이 두 모드는 락을 획득하는 방식에서 차이가 있다

</br>
</br>

### 비공정 모드 (Non-fair mode)

비공정 모드는 `ReentrantLock` 의 기본 모드이다

이 모드에서는 락을 먼저 요청한 스레드가 락을 먼저 획득한다는 보장이 없다

락을 풀었을 때, 대기 중인 스레드 중 아무나 락을 획득할 수 있다

이는 락을 빨리 획득할 수 있지만, 특정 스레드가 장기간 락을 획득하지 못할 가능성이 있다

</br>

**비공정 모드 특정**

- 성능 우선
  - 락을 획득하는 속도가 빠르다
- 선점 가능
  - 새로운 스레드가 기존 대기 스레드 보다 먼저 락을 획득할 수 있다
- 기아 현상 가능성
  - 특정 스레드가 계속해서 락을 획득하지 못할 수 있다

</br>
</br>

### 공정 모드 (Fair mode)

생성자에서 `true` 를 전달하면 된다 예시) `new ReentrantLock(true)`

공정 모드는 락을 요청한 순서대로 스레드가 락을 획득할 수 있게 한다

이는 먼저 대기한 스레드가 먼저 락을 획득하게 되어 스레드 간의 공정성을 보장한다

그러나 이로 인해 성능이 저하될 수 있다

</br>

**공정 모드 특징**

- 공정성 보장
  - 대기 큐에서 먼저 대기한 스레드가 락을 먼저 획득한다
- 기아 현상 없앰
  - 모든 스레드가 언젠가 락을 획득할 수 있게 보장한다
- 성능 저하
  - 락을 획득하는 속도가 느려질 수 있다

</br>

비공정, 공정 모드

- 비공정 모드는 성능을 중시하고, 스레드가 락을 빨리 획득할 수 있지만, 특정 스레드가 계속해서 락을 획득하지 못할 수 있다
- 공정 모드는 스레드가 락을 획득하는 순서를 보장하며 공정성을 중시하지만, 성능이 저하될 수 있다
