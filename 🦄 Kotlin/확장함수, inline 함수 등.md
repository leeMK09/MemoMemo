## 확장함수가 나온 배경

- 코틀린은 자바와 100% 호환성을 유지하고 싶어했고 기존 자바 라이브러리에 유지보수 및 확장할 때 코틀린 코드를 덧붙이고 싶다는 니즈가 생겼다
- 이에 대한 해결방안으로 "어떤 클래스 안에 있는 메서드처럼 호출할 수 있지만 함수는 밖에 만들 수 있게 하자"라는 개념이 나오게 된다

</br>

### 확장함수

- `this` 를 통해 해당 객체의 확장이 가능하다
- `this` 를 수신객체 라고 부른다
- 확장하려는 클래스는 수신객체 타입이다
- custom getter 처럼 확장 프로퍼티도 가능하다

</br>

### 확장함수, 자바로 컴파일되면 어떻게 되는가?

- 확장함수는 코틀린에서 보기엔 인스턴스 메서드처럼 보이지만, 실제로는 정적(static) 메서드로 컴파일됩니다
- 확장함수는 "진짜 메서드 추가" 가 아닌 컴파일 시점에 `수신 객체(receiver)` 를 첫 번째 인자로 받는 "정적 (또는 멤버) 메서드" 로 바뀌는 방식이다

```kotlin
// Kotlin 확장 함수
fun String.greet(): String = "Hi, $this!"
```

→ 자바로 컴파일 한다면

```java
// Java 에서 보면 다음과 같이 정적 메서드가 생성됨
public final class StringKt {
    public static String greet(@NotNull String $this) {
        return "Hi, " + $this + "!";
    }
}
```

- 첫 번째 인자로 "수신 객체(this)" 를 전달합니다

</br>

**클래스/객체 안의 확장 함수 (멤버확장)**

```kotlin
class A {
    fun String.shout() = uppercase()
}
```

자바로는 A 의 인스턴스 메서드가 되고, 확장 수신은 첫 번째 인자를 받음

```java
public final class A {
    public final String shout(@NotNull String $this) {
        return $this.toUpperCase();
    }
}
```

- 자바에서 호출시 `new A().shout("hi");`

</br>

**`object` 혹은 `companion object` 안에서 사용할 경우 기본적으로 그 객체의 멤버함수가 된다 (메서드)**

```kotlin
object Util {
    fun String.box() = "[$this]"
}
```

자바

```java
public final class Util {
    public static final Util INSTANCE = new Util();

    public final String box(@NotNull String $this) {
        return "[" + $this + "]";
    }
}

// 사용 → Util.INSTANCE.box("x")
```

</br>

**확장 프로퍼티**

```kotlin
val String.first: Char
    get() = this[0]

val String.tag: String
    get() = "tag:$this"
    set(v) {
        // field 가 존재하지 않음 → backing field X
        // 무언가를 기록하는 용도로 사용하기도 함
    }
```

- 백킹 필드가 없다
  - 단지 `getXxx(수신)` / `setXxx(수신, 값)` 메서드로만 컴파일된다
- 자바에서 호출시
  - `StringsKt.getFirst("abc")` / `StringsKt.setTag("abc", "t")`

</br>

**널 처리**

- 수신 타입이 `String` (non-null) 이면 `Intrinsics.checkNotNullParameter` 가 들어가 **런타임에 null 체크**를 한다
- 수신 타입이 `String?` 이면 파라미터 애노테이션이 `@Nullable` 이고 내부에서 직접 null 을 다뤄야 한다 (자동 체크 없음)

</br>

**디스패치 (바인딩) 성질**

- 확장 함수는 정적으로 결정된다. 즉 가상 디스패치(오버라이드) 와 다르다
- 즉 **컴파일 시점에 타입** 으로 어떤 확장을 부를지 결정하게 된다 → 코드상에서 타입이 중요하다, 타입을 통해 확장 함수를 호출 (정적 바인딩)

**특징**

- 정적 바인딩
  - 런타임이 아닌 컴파일 타임에 어떤 함수가 호출될지 결정됨 (→ 다형성 X)
- 디스패치 불가
  - 확장 함수는 멤버 함수와 달리 오버라이딩되지 않음
- 내부는 static
  - 실제로는 정적 메서드 형태로 구현됨 (`클래스명Kt.method(...)`)
- 가독성 증가
  - 마치 객체 메서드처럼 사용 가능해서 코드가 간결해짐

</br>

**주의할점**

- 확장함수가 `public` 이고 확장함수에서 수신객체클래스의 `private` 함수를 가져오면 캡슐화가 깨지는 것 아닌가?
  - 애당초 확장함수는 클래스에 있는 `private`, `protected` 멤버는 가져올 수 없다
- 멤버함수와 확장함수의 시그니처가 같다면?
  - 확장함수 보다 멤버함수가 우선
  - 확장함수를 만들었지만 다른 기능의 똑같은 멤버함수가 생기면, 멤버함수가 우선이므로 사용하는 곳에서 오류가 발생할 수도 있다
- 다형성이 적용되지 않는다
  - 확장함수는 오버라이드 되지 않고, 정적 바인딩이 된다
  - 해당 변수의 현재 타입 즉, 정적인 타입에 의해 어떤 확장함수가 호출될지 결정된다
- 자바에서는 확장함수를 가져다 사용할 수 있나?
  - 자바에서는 정적 메서드를 부르는 것 처럼 사용 가능하다

</br>
</br>

## 중위함수 (infix 함수)

- 새로운 함수의 종류가 아닌 함수를 호출하는 새로운 방법
- `downTo`, `step`, `to` 같은 함수 → 중위 호출 함수
- 변수.함수이름(인자) → 변수 함수이름 인자
  - 위 형태로도 함수 호출이 가능하다
- 멤버 함수에도 `infix` 키워드를 붙이면 중위 함수 호출이 가능하다

```kotlin
class Person(val name: String) {
    infix fun likes(other: Person): Boolean {
        println("${this.name} likes ${other.name}")
        return true
    }
}

fun main() {
    val a = Person("Alice")
    val b = Person("Bob")

    a likes b   // 중위 호출
    a.likes(b)  // 일반 함수 호출
}
```

```kotiln
infix fun Int.times(str: String): String = str.repeat(this)

fun main() {
    println(2 times "Hi ")    // → "Hi Hi "
    println(2.times("Hi "))   // 동일
}
```

자바로 컴파일된다면 ?

- `infix` 는 문법적 표현일 뿐, 자바에서는 그냥 일반 메서드나 `static` 함수로 컴파일됩니다
- 예를들어 위 예제는 자바에서 다음처럼 사용합니다

```java
KotlinCodeKt.times(2, "Hi ");
```

즉, `infix` 는 코틀린에서만 의미있는 문법입니다

</br>
</br>

## inline 함수

- 함수가 호출되는 대신, 함수를 호출하는 지점에 함수 본문을 그대로 복붙하고 싶은 경우
- 기본적으로 함수를 파라미터로 전달할 때 오버헤드를 줄일 수 있다
- 하지만 `inline` 함수의 사용은 성능 측정과 함께 신중하게 사용되어야 한다
  - 코틀린 라이브러리에서는 최적화를 어느정도 해뒀기 때문에 적절하게 `inline` 함수가 붙어있다

</br>

### 왜 나왔는가?

- Kotlin 의 `inline` 함수는 주로 람다와 고차함수로 인해 발생하는 오버헤드를 줄이기 위해 등장했습니다
- Kotlin 은 함수를 변수로 다루거나, 람다를 전달하는 일이 많습니다 (→ 고차함수)
- 그런데 이런 람다는 "익명 클래스(Closure)" 로 컴파일되고, 객체 생성 + 메서드 호출이 발생하므로 런타임 오버헤드가 생깁니다

→ 그래서 `inlin` 키워드를 붙이면, 함수 호출 시점에 해당 함수 본문을 그대로 복사(인라인)하여 오버헤드를 줄입니다

마치 자바에서 코드를 삽입하는 느낌

</br>

### 자바로 컴파일되면 어떻게 되는가?

- `inline` 함수는 아예 메서드 호출이 사라지고 → 해당 함수의 코드가 호출 지점에 복사되어 들어갑니다

```kotlin
inline fun runTwice(block: () -> Unit) {
    block()
    block()
}

fun main() {
    runTwice { println("Hello") }
}
```

→ 자바 코드로 변환시

```java
public static void main() {
    System.out.println("Hello")
    System.out.println("Hello")
}
```

`runTwice` 함수는 호출조차 되지 않고, `block()` 의 내용이 직접 삽입됨

이것이 바로 "인라인(inline)" 의 핵심입니다 → 함수호출시 코드복사

</br>

**특징**

- 목적
  - 고차 함수의 성능 오버헤드 제거
- 실제 동작
  - 함수 호출 없이 본문이 그대로 복사됨
- 대상
  - 고사 함수 또는 일반 함수
- 함수 타입 전달시
  - 람다 객체가 생성되지 않음
- Crossinline / Noinline
  - 제약이 필요한 경우 제어 키워드 사용 가능

</br>
</br>

## 지역함수

- 함수 안에 함수를 선언할 수 있다
- "함수로 추출하면 좋을 것 같은데" 혹은 "이 함수를 지금 함수 내에서만 사용하고 싶을떄" 사용한다

**단점**

- depth 가 깊어져 코드 읽기가 어렵고 코드가 장황해진다
