## Object declaration

- 싱글톤 패턴을 더 쉽게 사용하기 위해 코틀린에서 제공하는 일종의 객체 선언 키워드

```kotlin
object Logger {
    fun log(msg: String) = println(msg)
}
```

특징

- 싱글톤 형태
- thread-safe
  - 멀티스레드 환경에서 일반적으로 어떤 함수나 변수, 혹은 객체가 여러 스레드로부터 동시에 접근이 이루어져도 데이터 일관성을 유지함
- lazy-initialized
  - 외부에서 객체가 사용되는 시점에 초기화가 이루어진다
- Object 키워드가 선언된 클래스는 주/부 생성자를 사용할 수 없다
  - `object` 는 프로그램 전체에서 단 하나의 인스턴스만 존재해야 한다
  - 생성자 호출을 통해 여러 인스턴스를 만들 수 있는 여지를 막기 위해 생성자를 금지한 것 이다

</br>
</br>

## object 는 왜 스레드 세이프한가?

```kotlin
public final class Logger {
    public static final Logger INSTANCE;

    static {
        INSTANCE = new Logger(); // 클래스 로딩 시 단 한 번 실행됨 (JVM 보장)
    }

    private Logger() {} // 외부에서 생성 못함

    public final void log(@NotNull String msg) {
        System.out.println(msg);
    }
}
```

- `object` 를 자바의 정적 초기화(static initializer) 블록을 활용해서 구현한다
  - Java SE 17 기준 공식 문서에서 `static` 블록은 `<clinit>` 이라는 특수 메서드로 컴파일 됨
  - JVM이 클래스 초기화를 최초 1회, 단일 스레드에서 처리
  - 따라서 static 블록에서 초기화하는 싱글톤 객체는 스레드 세이프하게 생성됨
- 즉 `INSTANCE` 는 스레드 간 race condition 없이 안전하게 초기화된다

</br>
</br>

## object 는 lazy-loaded singleton 이다

```kotlin
object Logger {
    fun log(msg: String) = println(msg)
}
```

이 `Logger` 는 프로그램 시작시 바로 초기화되지 않고, 처음 `Logger.log(...)` 같은 접근이 발생할때 JVM 이 클래스 로딩과 동시에 초기화한다

</br>
</br>

## companion object

- 클래스 내부의 객체 선언을 위한 object 키워드
- 한마디로 클래스 내부에서 싱글톤 패턴을 구현하기 위해 사용

```kotlin
class User private constructor(val name: String) {
    companion object {
        fun create(name: String): User = User(name)
    }
}
```

- 클래스 내부에서 선언되는 싱글톤
- 클래스 수준에서 `static` 처럼 사용 가능
- 팩토리 메서드, 상수, 클래스 레벨 함수 보관
- 동반객체, 동행객체라고 불리며 하나의 객체로 간주된다
- 이름을 붙일 수 있고 interface 를 구현할 수도 있다
- 사실 이름이 없다면 `Companion` 이라는 이름이 생략된 것 이다

</br>
</br>

## object 와 다른점은

- object 키워드를 사용하면 객체 내 `static {}` 에서 new 를 이용한 객체생성과 변수 할당이 이루어진다
- 하지만 companion object 를 사용하면 new 를 이용한 객체 생성과 변수 할당이 객체의 외부, 클래스의 내부에서 진행된다

```java
public final class User {
    private final String name;

    // 생성자
    public User(String name) {
        this.name = name;
    }

    public final String getName() {
        return this.name;
    }

    // static 필드로 Companion 객체가 저장됨
    public static final User.Companion Companion = new User.Companion();

    // Companion 내부 클래스
    public static final class Companion {
        public final User create(String name) {
            return new User(name);
        }
    }
}
```
