## data class 란

- `data class` 는 데이터를 담기 위한 용도의 클래스입니다
- POJO (Plain Old Java Object) 같은 개념으로, 주로 DTO (Data Transfer Object), VO (Value Object) 처럼 값만 저장하는 클래스를 간단히 선언할 수 있게 해줍니다

```kotlin
data class User(
    val name: String,
    val age: Int
)
```

</br>
</br>

## 왜 나왔을까?

- 자바에서는 단순히 데이터를 담는 클래스임에도 불구하고 `equals`, `hashCode`, `toString`, `copy`, `getter`, `setter`, `constructor` 등을 직접 구현해야 했습니다
- 이런 **보일러플레이트 코드(쓸데없이 반복적인 코드)** 를 제거하고자 Kotlin 에서는 `data class` 가 도입되었습니다

</br>
</br>

## Kotlin 에서 어떤 역할을 할까?

`data class` 를 사용하면 다음과 같은 함수들이 자동 생성됩니다

| 함수명         | 설명                                               |
| -------------- | -------------------------------------------------- |
| `equals()`     | 값 비교를 위한 함수                                |
| `hashCode()`   | 해시 값 계산 (Map의 키 등에서 사용됨)              |
| `toString()`   | `User(name=홍길동, age=30)` 형식 출력              |
| `copy()`       | 일부 속성만 바꾸고 객체 복사 가능                  |
| `componentN()` | 구조 분해 선언에 사용됨 (`val (name, age) = user`) |

```kotlin
val user1 = User("이명규", 30)
val user2 = user1.copy(age = 31)
val (name, age) = user1 // 구조 분해 가능
```

</br>
</br>

## 자바로 검파일되면 어떻게 될까?

Kotlin → Java 로 컴파일되면 일반 클래스 형태로 변환되며, 위에서 언급한 메서드들이 자동으로 생성된 형태로 보입니다

```kotlin
data class User(
    val name: String,
    val age: Int
)
```

**Java 컴파일 결과**

```java
public final class User {
    private final String name;
    private final int age;

    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public final String getName() { return name; }
    public final int getAge() { return age; }

    public final User copy(String name, int age) {
        return new User(name, age);
    }

    public String toString() {
        return "User(name=" + name + ", age=" + age + ")";
    }

    public boolean equals(Object o) { ... } // 값 비교
    public int hashCode() { ... } // 해시 계산

    public void component1() { return name; }
    public void component2() { return age; }
}
```

</br>
</br>

## 제한사항

- 기본 생성자에 1개 이상의 파라미터가 있어야 함
- 기본 생성자의 파라미터가 `val` 또는 `var` 로 선언해야 함
- 다른 클래스를 상속받을 수 없음 (슈퍼 클래스를 가질 수 없음)
  - 단, `sealed` 클래스는 상속받을 수 있으며 인터페이스는 구현할 수 있음 (v1.1 이후 기준)
- `abstract`, `open`, `sealed`, `inner` 등 키워드를 붙일 수 없음
- 자동으로 생성한 메서드를 오버라이딩할 경우, 오버라이드 된 메서드 사용

</br>
</br>

## Java 의 Record Class 란

- Java14 에서 Preview, Java 16 에서 정식 도입된 **불변 데이터 전용 클래스**입니다
- Kotlin 의 `data class` 와 유사하게 값을 담는 용도로 보일러플레이트 없이 만들 수 있도록 설계되었습니다

```java
public record User(String name, int age) {}
```

</br>
</br>

## 왜 나왔는가?

Java 는 오랫동안 DTO 나 VO 를 만들때 너무 많은 코드가 필요했습니다

```java
public class User {
    private final String name;
    private final int age;

    // 생성자, getter, setter, hasCode, toString 등 직접 작성해야 함
}
```

이런 불편함을 해소하고자 `record` 를 도입해, 다음과 같은 메서드를 자동 생성합니다

- 생성자
- 모든 필드의 `getter` (이름 그대로 `name()`, `age()`)
- `equals()`, `hashCode()`, `toString()`

</br>
</br>

## 사용예시

```java
public record User(String name, int age) {}

User u = new User("이명규", 30);
System.out.println(u.name()); // 이명규
```

</br>
</br>

## Kotlin 의 Data Class VS Java 의 Record 비교

| 항목             | Kotlin `data class`                             | Java `record`                                                             |
| ---------------- | ----------------------------------------------- | ------------------------------------------------------------------------- |
| 목적             | 불변 객체 표현 + 편의 메서드                    | 불변 데이터 표현 최적화                                                   |
| 생성자           | 반드시 `val` or `var` 필요                      | 필드는 모두 `final` (불변)                                                |
| 메서드 자동 생성 | equals, hashCode, toString, copy, componentN 등 | equals, hashCode, toString, constructor, getter                           |
| 불변성           | `val` 사용 시 불변, `var`도 가능                | 항상 불변 (`final` 필드)                                                  |
| 구조 분해 지원   | `componentN()` 메서드로 지원                    | 구조 분해 없음 (단순 getter만 제공)                                       |
| `copy()` 지원    | 있음                                            | 없음 (직접 생성자 사용)                                                   |
| JVM 버전         | Kotlin은 Java 6 이상 가능                       | Java 16 이상에서 사용 가능                                                |
| 직렬화/호환성    | 기존 클래스와 동일                              | 특별한 형태의 클래스 (JVM 내부적으로 `final`, `extends java.lang.Record`) |

</br>

**Data Class (Kotlin) VS Record Class(Java)**

- `copy()` 메서드는 코틀린의 Data Class만 제공해준다
- 자바의 Record Class 는 모든 필드가 `private` + `final` 인 반면, 코틀린의 Data Class 는 선언 방식에 따라 필드값 변경도 가능하다
- 코틀린의 Data Class 는 데이터 클래스 그 자체인데, 자바의 Record Class 는 `Record` 라는 베이스 클래스를 상속받은 형태이다
- 가장 큰 차이점은 인스턴스 변수를 정의할 수 있냐 없냐의 차이이다. Data Class 는 인스턴스 변수를 만들 수 있지만 Record Class 는 인스턴스 변수를 허용하지 않는다

</br>
</br>

## 철학적 차이

- Kotlin 의 `data class` 는 "실용성" 과 "유연성" 을 모두 추구합니다
  - 필요하면 `var` 도 허용, `copy()` 로 일부 값만 바꿔 복사 가능, 구조 분해도 지원
- Java 의 `record` 는 "불변성" 과 "간결성"에 집중합니다
  - 오로지 데이터를 표현하는 목적 `final`, `extends java.lang.Record`, 성능 개선 목적까지 포함
