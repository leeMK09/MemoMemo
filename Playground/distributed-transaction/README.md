## 분산 트랜잭션 맛보기

- TCC, SAGA 패턴 맛보기

</br>
</br>

### TCC 패턴

- TCC(Try-Confirm-Cancel) 는 분산 시스템에서 데이터 정합성을 보장하기 위해 사용하는 분산 트랜잭션 처리 방식
- 트랜잭션을 애플리케이션 레이어에서 논리적으로 관리한다
- 세 단계로 나뉨
  - Try : 필요한 리소스를 점유할 수 있는지 검사하고 임시로 예약
  - Confirm : 실제 리소스를 확정 처리하여 DB 에 반영
  - Cancel : 문제가 생긴 경우, 예약 상태를 취소하여 원복한다

**코드 예시**

- 주문 + 결제 요청 시 OrderService 가 Coordinator 가 되는 상황

![order_service_tcc](./imgs/order_service_tcc.png)

**Entity**

- Point
  - Point / PointReservation
- Product
  - Product / ProductReservation

**동시성 처리**

- Redis SETNX 로 Lock 획득/해제

**느낀 단점**

- 예약을 위한 엔티티를 필요한 도메인에 추가해야하므로 복잡해지며 문제가 생길때도 두 엔티티를 확인해야 함
- 네트워크 요청 문제시 이를 모두 예외처리하기 어려움
  - `OrderService` 에서 시작해서 재고 예약까지는 수행했지만 포인트 사용 예약에 실패할 경우

![timeout_error](./imgs/timeout_error.png)

- 한가지 방법은 오류 발생시 재시도 처리 → 정상 처리로 유도
  - 재시도를 안전하게 처리하려면 시스템이 반드시 멱등성있게 설계되어야 함
  - 여기서는 `requestId` 를 통해서 Lock 핸들링 + 각 요청에 대한 상태 확인
