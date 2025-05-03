일반적으로 자바에서는 체크 예외가 발생할 경우 아래 처럼 `throws` 를 하거나 `try - catch` 로 예외를 핸들링할 수 있다

```java
// throws
public class Main {
    public static void main(String[] args) throws Exception {
        throw new Exception();
    }
}

// try - catch
public class Main {
        public static void main(String[] args) {
        try {
            throw new Exception();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

그러나 Thread 의 작업을 생성할 수 있는 `Runnable` 에서는 무조건 `try - catch` 로 예외를 잡아야 한다

```java
// throws
class MyRunnable implements Runnable {
    @Override
    public void run() throws Exception { // 불가능, 컴파일 에러가 발생
        throw new Exception();
    }
}

// try - catch
class MyRunnable implements Runnable {
    @Override
    public void run() {
        try {
            throw new Exception(); // 무조건 try - catch 로 처리해야함
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

<br>

---

<br>

### 🤔 왜 `Runnable` 은 `try - catch` 로 무조건 예외를 핸들링해야할까 ?

먼저 `Runnable` 인터페이스는 다음과 같이 정의되어 있다

```java
public interface Runnable {
		void run();
}
```

자바에서는 메서드를 재정의할 때 지켜야할 예외와 관련된 규칙이 존재한다.

- 체크 예외
  - 부모 메서드가 체크 예외를 던지지 않는 경우, 재정의된 자식 메서드도 체크 예외를 던질 수 없다 !
  - 자식 메서드는 부모 메서드가 던질 수 있는 체크 예외의 하위 타입만 던질 수 있다 !
- 언체크 (런타임) 예외
  - 규칙 없음

`Runnable` 인터페이스의 `run()` 메서드는 아무런 체크 예외를 던지지 않는다.
즉, `Runnable` 인터페이스의 `run()` 메서드를 재정의하는 곳에서는 체크 예외를 밖으로 던질 수 없다 !

체크 예외를 던지도록 재정의하는 경우 컴파일 오류가 발생한다.

</br>

---

</br>

### 🤔 자바는 왜 이런 제약을 두는 것 일까?

부모 클래스의 메서드를 호출하는 클라이언트 코드는 부모 메서드가 던지는 특정 예외만을 처리하도록 작성된다

자식 클래스가 더 넓은 범위의 예외를 던지면 해당 코드는 예외를 처리할 수 조차 없어진다

이는 예외 처리의 일관성을 해치고, 예상하지 못한 런타임 오류를 초래할 수 있다

```java
class Parent {
    void method() throws InterruptedException {
        // ...
    }
}

class Child extends Parent {
    @Override
    void method() throws Exception {
        // ...
    }
}

public class Test {
    public static void main(String[] args) {
        Parent p = new Child();
        try {
            p.method();
        } catch (InterruptedException e) {
            // InterruptedException 처리
        }
    }
}
```

위 예시는 실제로 동작하는 코드가 아니다.
그러나 위 예시처럼 부모의 메서드를 재정의할 때 예외를 상위 예외로 던진다고 한다면, 사용할때 문제가 발생한다.

사용하는 클라이언트 코드에서는 `Parent p = new Child();` 을 통해 자식 인스턴스를 부모의 타임으로 사용하고 있다

이후 부모의 `InterruptedException` 예외를 보며 사용하는 곳에서 `try - catch` 를 통해 에러 핸들링을 처리했다

컴파일러 더불어 개발자 또한 문제 없는 코드로 보며 사용하고 있는데 `Child` 는 `Exception` 예외를 던지므로 해당 에러 핸들링이 동작하지 않는 문제가 발생한다.
더불어서 `Exception` 예외는 체크 예외이기 때문에 에러 핸들링 코드가 필요하다 그러나 컴파일러는 이를 모른다.

이것은 확실하게 모든 예외를 체크하는 체크 예외의 규칙에 맞지 않다.
따라서 자바에서 체크 예외의 메서드 재정의는 다음과 같은 규칙을 갖는다.

</br>

**체크 예외 재정의 규칙**

- 자식 클래스에 재정의된 메서드는 부모 메서드가 던질 수 있는 체크 예외의 하위 타입만을 던질 수 있다
- 원래 메서드가 체크 예외를 던지지 않는 경우, 재정의된 메서드도 체크 예외를 던질 수 없다

</br>

**안전한 예외처리**

체크 예외를 `run()` 메서드에서 던질 수 없도록 강제함으로써, 개발자는 반드시 체크 예외를 `try - catch` 블록내에서 처리하게 된다
이는 예외 발생 시 예외가 적절히 처리되지 않아서 프로그램이 비정상 종료되는 상황을 방지할 수 있다.

특히 멀티 스레드 환경에서는 예외처리를 강제함으로써 스레드의 안정성과 일관성을 유지할 수 있다.

더불어 `try - catch` 로 예외를 핸들링함으로써 개발자가 예외가 발생한다는 것을 인지할 수 있게된다
