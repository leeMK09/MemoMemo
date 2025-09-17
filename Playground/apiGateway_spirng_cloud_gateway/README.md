## API Gateway 테스트 (Spring Cloud Gateway)

**구조**

![gw](./img/gw_playgroud.png)

- Client 는 `/order`, `/product` 를 통해서 요청
- API 게이트웨이는 요청 URL 을 통해 라우팅
- 각 서버는 `Eureka` 에 등록되어 현재 살아있는 인스턴스가 해당 요청을 수행 및 응답
