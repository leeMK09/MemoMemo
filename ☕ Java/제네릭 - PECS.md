## PECS 원칙이란 ?

> Producer - Extends / Consumer - Super

- `extends` 는 값을 꺼낼 때 (**Producer**)
- `super`는 값을 넣을 때 (**Consumer**)

즉

- `? extends Type` → 타입을 생산만 함 (읽기 위주)
- `? super Type` → 타입을 소비만 함 (쓰기 위주)

</br>

예제)

```java
class Animal {
    public void speak() {
        System.out.printl("Animal speaks");
    }
}

class Dog extends Animal {
    public void speak() {
        System.out.println("Dog barks");
    }
}

class Cat extends Animal {
    public void speak() {
        System.out.prinln("Cat meows");
    }
}
```

</br>
</br>

### 1. <? extends Animal> → 읽기 전용 (Producer)

```java
List<? extends Animal> animals = List.of(new Dog(), new Cat());

Animal a = animals.get(0); // 읽을 수 없다
// animals.add(new Dog()); → 컴파일 오류
```

</br>

**왜 `add()`가 안될까?**

- animals 리스트는 `List<? extends Animal>` 이지만, 실제 타입이 `List<Dog>`, `List<Cat>`, `List<Animal>` 중 무엇일지 모른다
- 그래서 컴파일러는 안전을 위해 아무것도 추가 못하게 한다
- 하지만 `Animal`로 꺼내는 건 안전하므로 허용된다

그래서 이건 값을 꺼내기만 할 수 있으니까 `Producer`

</br>
</br>

### 2. <? super Animal> → 쓰기 전용 (Consumer)

```java
List<? super Animal> animals = new ArrayList<Object>();

animals.add(new Dog()); // 추가 가능함
animals.add(new Cat()); // 추가 가능함

// Animal a = animals.get(0) 컴파일 오류 → (Object로만 꺼낼 수 있다)
Object obj = animals.get(0); // 가능함
```

</br>

**왜 `get()` 이 안될까?**

- 리스트 타입이 `List<Animal>`, `List<Object>` 등 Animal 의 상위타입이다
- 꺼내면 정확한 타입을 알 수 없어서 `Object` 로만 꺼낼 수 있다
- 하지만 `Animal` 또는 그 하위 클래스는 안전하게 넣을 수 있다

그래서 이건 값을 넣기만 할 수 있으므로 `Consumer`

</br>
</br>

### 그럼 왜 <T super Animal> 은 안될까 ?

제네릭 타입을 선언할 때 컴파일러는 T 의 정확한 타입을 알아야 하고, 그 타입을 기반으로 코드를 생성한다

```java
<T super Animal> // 이런 문법은 안된다
```

`super` 는 정확한 타입이 뭔지 모르게 만드는 개념이라서, 컴파일러가 안정적으로 제네릭 타입 코드를 생성할 수 없다

그래서 제네릭 타입 선언부에서는 `extends` 만 허용한다

</br>
</br>

### 이런 문법은 안되나요 ?

```java
List<? super Animal> animals = new ArrayList<? super Animal>(); // 컴파일 오류
```

자바에서 제네릭 타입 인스턴스를 생성할 때는 타입 파라미터가 명확해야 한다

`? super Animal` 은 "하한 와일드 카드" 이며 구체적인 타입이 아니기 때문에 new 할 수 없다

\*\*와일드 카드는 "참조"에는 사용할 수 있지만 "생성"에는 사용할 수 없다

```java
List<? super Animal> animals; // OK → 참조 선언 가능
animals = new ArrayList<Animal>(); // OK
animals = new ArrayList<Object>(); // OK
animals = new ArrayList<Dog>(); // Dog 는 Animal 의 하위 → 하한 조건 위반

-------------------------------------

List<? super Animal> animals = new ArrayList<Animal>();
animals.add(new Dog()); // 가능
animals.add(new Cat()); // 가능
```

왜냐하면 `List<? super Animal>` 은 `Animal`, `Object`, ... 등 Animal 의 상위 타입만 허용하므로 `new ArrayList<Animal>()` 나 `new ArrayList<Object>()` 는 가능하지만 `new ArrayList<? super Animal>()` 은 불가능하다

- `? super Animal` 은 "Animal 이상은 모두 받는다" 라는 규칙 이지만
- `new ArrayList<? super Animal>` 는 "그중 어떤 타입으로 만들건지 모른다" 라고 선언한 것 이다

즉 컴파일러는 "그럼 뭘 new 로 해야할지 모르겠다" 라고 거절한다

</br>

### 정리

| 문법                 | 설명                              | 읽기                 | 쓰기 |
| -------------------- | --------------------------------- | -------------------- | ---- |
| `<? extends Animal>` | Animal 또는 하위 타입 (읽기 전용) | ✅                   | ❌   |
| `<? super Animal>`   | Animal 또는 상위 타입 (쓰기 전용) | ❌ (`Object`만 가능) | ✅   |
| `<T extends Animal>` | T는 Animal 또는 하위 타입         | ✅                   | ✅   |
| `<T super Animal>`   | ❌ 문법적으로 안 됨               | -                    | -    |

</br>
</br>

| 코드                                         | 가능 여부 | 설명                          |
| -------------------------------------------- | --------- | ----------------------------- |
| `List<? super Animal> animals;`              | ✅        | 하한 와일드카드로 선언 가능   |
| `animals = new ArrayList<Animal>();`         | ✅        | Animal은 자신이니까 하한 OK   |
| `animals = new ArrayList<Object>();`         | ✅        | Object는 Animal의 상위니까 OK |
| `animals = new ArrayList<? super Animal>();` | ❌        | 구체적인 타입 아님, 생성 불가 |
