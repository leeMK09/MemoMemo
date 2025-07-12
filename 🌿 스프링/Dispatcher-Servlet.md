## Servlet

- Java EE (Jakarta EE) 에서 제공하는 웹 컴포넌트
- HTTP 요청과 응답을 보내는 자바 클래스
- HTTP 프로토콜 서비스를 지원하는 `javax.servlet.http.HttpServlet` 클래스를 상속받아서 구현

처리흐름

1. 사용자(클라이언트)가 URL 을 입력하면 HTTP Request 가 Servlet Container 로 전송
2. 요청을 전송받은 Servlet Container 는 HttpServletRequest, HttpServletResponse 객체를 생성
3. web.xml 을 기반으로 사용자가 요청한 URL 이 어느 서블릿에 대한 요청인지 찾음
4. 해당 서블릿에서 service 메서드를 호출한 후 클라이언트의 GET, POST 여부에 따라 doGet() 또는 doPost() 를 호출
5. doGet() or doPost() 메서드는 동적 페이지를 생성한 후 HttpServletResponse 객체에 응답을 보냄
6. 응답이 끝나면 HttpServletRequest, HttpServletResponse 두 객체를 소멸

> Java EE
>
> 기업용 웹 서비스를 만들기 위해 제공하는 자바 표준 기술 모음
> JPA, Servlet, EJB 등

</br>
</br>

## Dispatcher Servlet

- Servlet 의 일종이지만 역할이 다르다
- HTTP 의 모든 요청을 프론트 컨트롤러 역할로서 요청을 받고 공통 로직을 처리한 후 요청이 가야할 곳에 핸들러를 찾아서 요청을 위임한다
- 기존에는 모든 요청 URL 을 매핑하는 서블릿을 만들고 이를 `web.xml` 에 등록하는 과정이 있어야 했는데
  - Java EE 6 (Servlet 3.0) 부터는 어노테이션 기반으로 서블릿을 등록할 수 있음 (`@WebServlet`)
  - 톰캣 같은 서블릿 컨테이너는 자바 코드를 읽지 못하고 war 파일 안의 `web.xml` 이라는 메타정보를 통해 어느 서블릿이 어디에 매핑되어야 하는지를 알려줘야 한다
- 위 문제를 해결한 것 + 요청에 알맞은 컨트롤러를 찾고 위임 및 공통 로직을 해결한 것이 `Dispatcher-Servlet` 이다

</br>
</br>

## Handler Mapping (핸들러 매핑)

- 요청 URL 을 처리할 "컨트롤러 (=핸들러)" 를 찾아줌
- 요청 URL, HTTP 메서드(GET, POST 등) 의 정보를 기준으로 어떤 컨트롤러 또는 `@RequestMapping` 메서드가 이 요청을 처리해야할지 찾아준다
- Spring 은 `RequestMappingHandlerMapping` 이라는 구현체를 통해 이 매핑 정보를 갖고 있다가 요청시 찾아준다

> Dispatcher Servlet 은 ApplicationContext 에서 `HandlerMapping` 타입의 빈들을 자동으로 찾아서 등록한다

Spring MVC 에서는 자동으로 다음과 같은 `HandlerMapping` 들을 등록한다

- `RequestMappingHandlerMapping`
  - `@RequestMapping` , `@GetMapping` 기반의 컨트롤러

```java
// DispatcherServlet 초기화시 initStrategies() 메서드 실행
protected void initStrategies(ApplicationContext context) {
    initHandlerMapping(context);  // ← 여기서 HandlerMapping 찾음!
    initHandlerAdapters(context);
    initViewResolvers(context);
}
```

→ `initHandlerMappings()` 내부에서 ApplicationContext 찾은 후 `HandlerMapping` 타입의 빈들을 모두 가져옴

```java
private void initHandlerMappings(ApplicationContext context) {
    this.handlerMappings = new ArrayList<>();

    // ApplicationContext 내의 모든 HandlerMapping 타입 빈 검색
    Map<String, HandlerMapping> matchingBeans = BeanFactoryUtils.beansOfTypeIncludingAncestors(context, HandlerMapping.class, true, false);

    this.handlerMappings.addAll(matchingBeans.values());
}
```

</br>
</br>

## HandlerAdapter (핸들러 어댑터)

- 찾은 컨트롤러(=핸들러)를 실제로 실행시켜주는 객체
- 컨트롤러가 어떤 형태든(메서드, 클래스 등) 호출 방식이 다를 수 있으므로 이를 일관되게 실행시키는 공통 인터페이스 역할

1. DispatcherServlet 은 애플리케이션 시작 시, `HandlerAdapter` 빈들을 전부 조회해서 리스트로 보관
2. `HandlerMapping` 을 통해 핸들러(컨트롤러) 를 찾은 후
3. 그 핸들러를 실행할 수 있는 `HandlerAdapter` 를 `for` 문을 돌면서 찾는다

```java
for (HandlerAdapter adapter : this.handlerAdapters) {
    if (adapter.supports(handler)) {
        return adapter;
    }
}
```

예시

```kotlin
@Controller
class HelloController {
    @GetMapping("/hello")
    fun hello(): String = "hello"
}
```

> Spring 은 요청 정보를 통해 컨트롤러 클래스 + 메서드 정보를 리플렉션 기반으로 꺼내서 HandlerMethod 객체로 감쌈

</br>

1. `@GetMapping("/hello")` 어노테이션이 붙은 메서드는 → Spring이 애플리케이션 시작 시에 `RequestMappingHandlerMapping` 에서 전부 스캔합니다
2. 이때 `HelloController` 안의 `hello()` 메서드도 스프링이 리플렉션으로 읽고, → `HandlerMethod` 라는 객체로 래핑합니다

```java
public class HandlerMethod {
    Object bean;      // HelloController 인스턴스
    Method method;    // hello() 메서드 (java.lang.reflect.Method)
}
```

3. 요청이 들어오면 DispatcherServlet → HandlerMapping이 `HandlerMethod(bean=helloController, method=hello())` 를 반환한다
4. 이후 `RequestMappingHandlerMapping` 이 `HandlerMethod` 객체를 확인한 후 내부적으로 다음처럼 실행한다

```java
method.invoke(bean, arguments);
```

> HandlerMethod
>
> - 요청을 처리할 대상 정보
> - 컨트롤러와 메서드 객체가 있음 → 리플렉션의 메서드 객체를 invoke 한다

이후 찾은 `HandlerMethod` 를 `HandlerExecutionChanin` 으로 감쌈

```java
public final HandlerExecutionChain getHandler(HttpServletRequest request) {
    Object handler = getHandlerInternal(request); // → HandlerMehtod 반환

    HandlerExecutionChain executionChain = new HandlerExecutionChain(handler);

    // 인터셉터가 있다면 등록
    for (HandlerInterceptor interceptor : this.getAdaptedInterceptors()) {
        if (interceptor applies to this handler) {
            executionChain.addInterceptor(interceptor);
        }
    }

    return executionChain;
}
```

</br>

**HandlerExecutionChain 이란?**

```java
public class HandlerExecutionChain {
    private final Object handler; // 보통 HandlerMethod
    private final List<HandlerInterceptor> interceptors;
}
```

- 단순히 컨트롤러 메서드만 실행하면 안되기 때문에 → 인터셉터도 같이 실행해야 함
- 그래서 핸들러와 인터셉터들을 하나로 묶은 "체인 객체"를 만들어 `DispatcherServlet` 에 넘김
