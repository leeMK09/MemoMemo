## AsyncLocalStorage

- Node.js 애플리케이션에서 인증된 사용자 정보, 요청 ID, 트랜잭션 ID, 로깅용 ID 같은 값을 매 함수마다 인자로 넘기기 시작하면 코드가 금방 지저분해진다
- 예를 들어 컨트롤러가 서비스로 값을 넘기고, 서비스가 레포지토리로 넘기고, 레포지토리가 외부 API 클라이언트로 넘기는 구조가 생기면 실제 비즈니스 로직과 상관없이 `requestId`, `user`, `traceId` 같은 값이 함수 시그니처를 계속 오염시킨다
- 이 문제를 단순히 해결하려고 한다면 아래와 같다

```javascript
let currentUser = null;

function middleware(req, res, next) {
    currentUesr = req.user;
    next();
}
```

- 이 방식은 요청이 하나만 들어올 때에 우연히 동작하게 된다
- 실제 서버에서는 요청 A 가 처리되는 도중에 요청 B 가 들어오고, 요청 A 가 기다리던 비동기 작업의 콜백이 나중에 실행될 수 있기 때문에, 전역 변수 하나에 현재 사용자를 저장해두면 요청 A 가 참조해야 할 값을 요청 B 가 덮어써버릴 수 있다
- 즉 문제는 Node.js 가 싱글 스레드라서 안전한 것이 아니라, 하나의 스레드 안에서 여러 비동기 흐름이 교차 실행되기 때문에 오히려 전역 상태가 위험하다는 데 있다
- `AsyncLocalStorage` 는 바로 이 문제를 해결하기 위해 존재한다

</br>

### AsyncLocalStorage 는 무엇을 기준으로 값을 분리하나

- Java 의 ThreadLocal 은 스레드를 기준으로 값을 분리한다
    - 즉 스레드 A 안에서 저장한 값은 스레드 B 에서 보이지 않는다
- 그런데 Node.js 는 일반적인 요청 처리 모델에서 Java 서버처럼 요청마다 전용 스레드 하나를 배정하는 구조가 아니다
- Node.js 는 이벤트 루프를 중심으로 하나의 스레드가 수많은 비동기 작업을 번갈아 처리한다
- 그래서 Node.js 에서는 스레드 기준 격리가 아니라 비동기 실행 흐름 기준 격리가 필요하다
- `AsyncLocalStorage` 는 정확히 이 역할을 수행한다
- 즉 이 요청에서 시작된 콜백, Promise, 타이머, IO 작업, 그 후속 콜백들을 하나의 체인으로 보고, 그 체인 전체에 같은 저장소를 연결한다
- 따라서 `AsyncLocalStorage` 를 이해할 때는 비동기 호출 체인 로컬이라고 이해해야 한다

</br>

### 내부적인 격리

- 핵심은 Node.js 의 `async_hooks` 이다
- Node.js 런타임은 비동기 작업이 생성될 때마다 내부적으로 그 작업을 식별할 수 있는 정보를 관리한다
- 대표적으로 `asyncId` 와 `triggerAsyncId` 라는 개념이 있다
    - `asyncId` 는 비동기 작업 자신의 식별자를 의미한다
    - `triggerAsyncId` 는 비동기 작업을 만든 부모 작업의 식별자를 의미한다
- 즉 Node.js 는 비동기 작업을 단순히 흩어진 콜백 묶음으로 보지 않고, 누가 누구를 만들었는가 라는 인과관계를 내부적으로 추적한다
- 예를 들어 요청 A 를 처리하는 과정에서 DB 조회 Promise 가 생성되면, 런타임은 그 Promise 작업이 요청 A 의 실행 흐름에서 파생되었다는 사실을 기억한다
- 요청 B 에서 생성된 Promise 는 요청 B 의 흐름에서 파생된 것으로 따로 기록하게 된다
- 그러면 `AsyncLocalStorage` 는 이 연결 관계를 이용해서 다음과 같이 동작한다
- 어떤 시점에 `run(store, callback)` 이 호출되면 `AsyncLocalStorage` 는 지금부터 이 callback 을 루트로 해서 파생되는 비동기 작업들이 이 store 를 상속받는다 라고 표시해둔다
- 이후 callback 내부에서 Promise 를 만들든, `setTimeout` 을 등록하든, 파일 IO 를 호출하든, DB 쿼리를 날리든, 그 후속 비동기 작업들을 부모 체인을 따라 같은 store 를 참조할 수 있게 된다
- 즉 값이 전역 변수에 저장되는 것이 아닌 비동기 리소스의 연결 그래프 위에 매달려서 전파되는 것이다

**요청 A 와 요청 B 가 서로 간섭하지 않는가?**

- 예를 들어 동시에 두 요청이 들어온다고 가정해보자
- 요청 A 가 들어왔을 때 서버는 다음과 같이 동작한다

```javascript
asyncLocalStorage.run({ user: "A" }, () => {
    handleRequestA();
});
```

- 그리고 거의 동시에 요청 B 가 들어오면 아래처럼 된다

```javascript
asyncLocalStorage.run({ user: "B" }, () => {
    handleRequestB();
});
```

- 겉보기에는 둘 다 같은 프로세스, 같은 메모리, 같은 이벤트 루프 위에서 돌아가니 충돌할 것처럼 보인다
    - 하지만 실제로는 충돌하지 않는다
- 그 이유는 요청 A 의 `run()` 이 만든 저장소와 요청 B 의 `run()` 이 만든 저장소가 서로 다른 비동기 체인에 연결되기 때문이다
    - 요청 A 안에서 생성된 Promise 는 요청 A 의 비동기 부모를 따라간다
    - 요청 B 안에서 생성된 Promise 는 요청 B 의 비동기 부모를 따라간다
- 따라서 나중에 이벤트 루프가 두 콜백을 번갈아 실행하더라도, 현재 실행 중인 콜백이 어느 비동기 체인에 속하는지를 `AsyncLocalStorage` 가 알고 있기 때문에, `getStore()` 를 호출했을 때 항상 그 체인에 연결된 저장소를 반환할 수 있다
- 즉 중요한 것은 지금 CPU 를 누가 쓰고 있는가가 아니다
- 중요한 것은 지금 실행되는 콜백이 어떤 비동기 체인에서 파생되었느냐이다

</br>

### 전역 변수와 다른 점

- 전역 변수는 말 그대로 프로세스 전체에서 하나뿐인 저장 공간이다
- 누가 마지막으로 값을 썻는지가 최종 상태를 결정한다
- 문제는 Node.js 가 싱글 스레드라서 생기는 것이 아닌 비동기 콜백이 순차적으로 실행되더라도 그 사이에 다른 요청이 얼마든지 같은 전역 상태를 바꿔버릴 수 있다는 점이다
- 반면 `AsyncLocalStorage` 는 값을 공용 박스 하나에 저장하지 않는다
- 각 요청의 비동기 체인마다 별도의 저장소를 만들고, 해당 체인에서 실행되는 콜백이 자기 저장소를 참조하도록 한다
- 그래서 요청 A 의 나중 콜백은 요청 A 저장소를 보고, 요청 B 의 나중 콜백은 요청 B 저장소를 본다

**예시**

- 인증 미들웨어가 요청을 받아 사용자 정보와 요청 ID 를 컨텍스트에 넣는다고 가정하자

```javascript
const { AsyncLocalStorage } = require("async_hooks");

const als = new AsyncLocalStorage();

function contextMiddleware(req, res, next) {
    const context = {
        requestId: req.headers["x-request-id"] || crypto.randomUUID(),
        user: req.user,
    };

    als.run(context, () => {
        next();
    });
}
```

- 이제 이후의 컨트롤러, 서비스, 레포지토리에서는 인자를 직접 받지 않아도 현재 요청 컨텍스트를 꺼낼 수 있다

```javascript
function getCurrentContext() {
    return als.getStore();
}

async function findOrders() {
    const context = getCurrentContext();
    console.log("현재 사용자:", context?.user);
    return orderRepository.findAll();
}
```

- 여기서 중요한 것은 `next()` 를 호출한 것이 아닌 `als.run(context, () => next())` 안에서 호출했다는 점이다
- 이렇게 하면 Express 가 이후 이 요청을 처리하면서 호출하는 컨트롤러, 서비스, 비동기 DB 콜백, Promise 후속 처리 등은 전부 이 `run()` 에서 시작된 비동기 체인 위에서 시작ㄷ다 된
- 즉 미들웨어가 현재 요청의 뿌리 컨텍스트를 심어주고 그 뒤의 모든 비동기 흐름이 그 컨텍스트를 이어받는 구조이다

```javascript
als.run({ requestId: "req-1" }, async () => {
    console.log("1:", als.getStore());

    await Promise.resolve();

    console.log("2:", als.getStore());

    setTimeout(() => {
        console.log("3:", als.getStore());
    }, 100);

    fs.readFile("test.txt", "utf8", () => {
        console.log("4:", als.getStore());
    });
});
```

- 위 코드에서 1, 2, 3, 4 가 모두 같은 store 를 보게 되는 이유는 각각의 비동기 작업이 전부 `run()` 내부에서 생성되었기 때문이다
    - `await` 은 내부적으로 Promise 체인을 만든다
    - `setTimeout` 은 타이머 비동기 리소스를 만든다
    - `fs.readFile` 은 IO 비동기 리소스를 만든다
- Node.js 는 이런 비동기 리소스가 생성될 때 부모 비동기 컨텍스트를 기억하고, `AsyncLocalStorage` 는 그 부모 관계를 따라 store 를 연결한다

</br>

### 전파

- 원칙적으로 많은 표준 비동기 API 에서 잘 전파되지만 아무 상황에서나 무조건 자동으로 되진 않는다
- 이유는 `AsyncLocalStorage` 는 Node.js 런타임이 추적할 수 있는 비동기 경계 위에서 동작하기 때문이다
- 따라서 어떤 라이브러리가 비표준적인 방식으로 콜백을 다루거나, 네이티브 애드온이 `async context` 를 올바르게 연결하지 않거나, 아예 다른 실행 단위로 작업을 넘겨버리면 전파가 기대와 다를 수 있다
- 대표적으로 주의할 점은 아래와 같다
    - `worker_threads` 는 같은 프로세스 안에 있어도 별도의 스레드와 별도의 실행 컨텍스트를 가지므로, 메인 스레드의 `AsyncLocalStorage` 저장소가 자동으로 넘어가지 않는다, 즉 워커로 작업을 보낼 때는 필요한 값을 메시지로 직접 전달해야 한다
    - `child_process` 나 외부 프로세스는 당연히 다른 프로세스이므로 컨텍스트가 공유되지 않는다. 이 경우에도 필요한 값은 IPC 메시지나 환경 변수, 명시적 파라미터로 전달해야 한다
    - 일부 오래된 라이프러리나 커스텀 비동기 래퍼는 컨텍스트 전파를 끊을 수 있다. 이럴 때는 `AsyncResource` 를 사용해서 직접 비동기 경계를 감싸줘야 하는 경우가 있다
- 즉 `AsyncLocalStorage` 는 강력하지만 Node.js 가 추적할 수 있는 비동기 체인 안에서 보장된다고 이해해야 한다

</br>

### `run()`

- `run(store, callback)` 은 단순히 callback 실행 전에 전역 상태를 잠깐 바꾼다는 개념이 아니다
- 이 함수의 역할은 callback 을 새로운 async context 의 시작점으로 선언한다
- 즉 `run()` 여기서 파생되는 비동기 흐름 전체가 이 store 를 이어받는다는 루트 설정 작업이다
- 그래서 실무에서는 보통 HTTP 요청의 가장 바깥 경계에서 `run()` 을 호출한다
- Express 라면 미들웨어 초입, NextJS 라면 인터셉트나 미들웨어 초입, Fastify 라면 request lifecycle hook 초입 같은 곳이 된다
- 반대로 너무 늦은 시점에 `run()` 을 호출하면 이미 만들어진 비동기 작업에는 적용되지 않을 수 있다
- 컨텍스트는 생성된 뒤에 과거로 거슬러 올라가 붙는 것이 아닌 그 시점부터 만들어지는 하위 비동작 작업들에 전파되는 것이다

**`enterWith()`**

- AsyncLocalStorage 에는 `run()` 외에 `enterWith()` 도 있다
- `run()` 은 callback 범위를 명확히 주고, 그 범위에서 생성되는 비동기 작업에 store 를 연결하는 방식이다
- 반면 `enterWith()` 는 현재 동기 실행 컨텍스트에 store 를 진입시키는 방식이라서, 사용 시점과 범위를 잘못 잡으면 예상 보다 넓게 영향을 줄 수 있다
- 실무에서는 대부분 `run()` 이 더 안전하다
    - 이유는 요청 처리의 시작점에서 이 요청의 컨텍스트는 여기부터 여기까지다 라고 감싸는 구조가 명확하기 때문이다
    - 더불어 AsyncLocalStorage 는 실무에서 로깅 혹은 추적에서 훨씬 자주 사용한다
        - 예를 들어 요청마다 `requestId` 를 생성해서 AsyncLocalStorage 에 넣어두면, 컨트롤러, 서비스, DB 레이어, 외부 API 클라이언트 어디서 로그를 찍더라도 같은 `requestId` 를 자동으로 붙일 수 있다

**ThreadLocal 과 비교**

- Java 의 ThreadLocal 은 스레드가 저장소의 키이다
- 그래서 같은 스레드 안에서는 어디서든 같은 값을 보고, 다른 스레드는 다른 값을 본다
- Node.js AsyncLocalStorage 는 비동기 실행 체인이 저장소의 키이다
- 그래서 같은 요청의 Promise 후속 작업, 타이머 콜백, IO 콜백은 같은 값을 보고 다른 요청의 비동기 체인은 다른 값을 본다
- 즉 Java 에서는 스레드가 작업의 경계이지만 Node.js 에서는 비동기 체인이 작업의 경계이다
    - 정확히는 현재 실행 중인 비동기 컨텍스트에 매핑된 저장소를 조회하는 API 이다
    - 즉 `getStore()` 를 통해서는 프로세스 전체의 유일한 store 하나를 반환하는 것이 아닌 현재 콜백이 속한 async context 에 연결된 store 를 반환하게 된다
