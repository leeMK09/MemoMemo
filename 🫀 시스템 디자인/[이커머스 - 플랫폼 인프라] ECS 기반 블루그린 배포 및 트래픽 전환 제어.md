## ECS 기반 Blue/Green 배포에서 트래픽 전환 제어 및 운영 가시성 문제

### 문제

- 도메인 서비스가 다수 (Auth, Order, Payment 등)인 환경
- 각 서비스가 독립적으로 배포되지만, 트래픽 전환은 운영 전체에 영향을 미침
- 자동 Blue/Green 전환 시 헬스체크만 통과하면 즉시 트래픽 전환
  - 비즈니스 로직 오류, 의존성 문제는 전환 이후에야 발견 가능
- 여러 도메인이 동시에 배포될 경우
  - 누가 언제 트래픽을 전환했는지 파악이 어려움
  - 장애 발생 시 롤백 판단과 책임 경계가 모호해짐
- 배포 직후 → 외부 API 연동 오류 / 캐시, DB 스키마 불일치 / 특정 기능 경로만 오류 발생 파악 불가능
- 헬스체크는 통과하지만 주문 생성, 결제 승인 같은 핵심 플로우는 판단불가
- 자동 전환 구조에서는 이미 트래픽이 넘어간 뒤에야 문제 인지 및 즉각적인 롤백이 어려움

</br>

### 기존 접근 방식의 한계

- ECS 기본 롤링 배포 시
  - 점진적 교체는 가능하지만 명확한 전환 시점 인지는 불가능
- CodeDeploy 자동 Blue/Green 트래픽 전환 시
  - 헬스체크 통과 즉시 전환 및 기술적 정상과 서비스 정상을 구분하지 못함
- 결과적으로 예상하지 못한 오류가 트래픽 전환과 동시에 사용자에게 노출됨, 배포 성공/실패 판단이 코드레벨 기준에서 머무름

</br>

### 개선

- 트래픽 전환을 단일 기준(Control Plane)으로 통제
- 도메인 단위 배포이지만, 전환 판단은 중앙에서 일관되도록 관리
- 전환 시점을 명확히 인지하고 팀 내 공유 가능
- 향후 Canary / 단계적 전환으로 확장 가능성 구조

</br>

### 구조 개요

**전체 배포 흐름**

```text
GitHub Actions

1. ECS Task Definition 등록
2. CodeDeploy 배포 생성
3. Green Task Set 생성
4. 헬스체크 대기
5. BeforeAllowTraffic Hook
  5-1. Lambda (트래픽 전환 승인/거절 Slack 메시지 발송)
6. 승인시 트래픽 전환 (Slack)
7. API Gateway
8. Callback Lambda
  8-1. CodeDeploy 상태 전달
```

</br>

**Internal ALB + 포트 기반 Listener 분리**

- Internal ALB 사용
- 도메인 별 포트 할당
  - 각 포트는 하나의 도메인 서비스와 1:1 매핑
- 왜 path 기반이 아닌 포트 기반인가?
  - path 기반은 Listener 1개 + 다수 Rule
  - priority 충돌 및 룰 누락 위험
  - 전환 단위가 불명확함
- 포트 기반
  - 도메인 == Listener == Target Group Pair
  - Blue/Green 전환 경계가 명확

</br>

**CodeDeploy 를 단일 트래픽 컨트롤 플레인으로 사용**

- ECS Service 는 모두 아래로 처리

```hcl
deployment_controller {
  type = "CODE_DEPLOY"
}
```

- Github Actions 는 ECS Service 를 직접 업데이트하지 않음
- 오직 CodeDeploy 만 Task Set 생성 및 Target Group 전환을 담당
- 트래픽 전환의 단일 진실 소스 (Single Source of Truth) 확보

</br>

**BeforeAllowTraffic 승인 훅 도입**

- CodeDeploy 의 Lifecycle Hook 활용
  - 헬스체크 통과 이후 실제 트래픽 전환 직전에 개입
- 승인 흐름
  1. CodeDeploy → BeforeAllowTraffic Hook 호출
  2. 승인 요청 Lambda 실행
  3. Slack 으로 승인/거절 버튼 전송
  4. 운영자가 판단 후 클릭
  5. Callback Lambda 가 CodeDeploy 에 상태 전달 (승인: `Succeeded` / 거절: `Failed`)

</br>

### 결론

- 자동 전환시 놓칠 수 있는 오류를 사전에 차단
  - 헬스체크는 기술적 정상만 보장
  - 비즈니스 관점 검증은 사람의 판단이 필요
- 향후 Canary + Blue/Green 병행을 고려한 확장성
  - 현재는 All-or-Nothing 배포 파이프라인
  - 이후 일부 트래픽만 전환하여 승인 → Canary → 전체 전환으로 구성을 생각
  - 승인 훅은 그대로 재사용 가능
- 트래픽 전환 시점을 명확히 공유
  - Slack 승인 메시지 → 전환 예정 알림
  - 누가 언제 승인했는지 기록 가능
  - 장애 발생 시 전환 시점과 원인 추적이 쉬워짐
- 적용 : [KLP Logistics 블루/그린 배포 수동 전환 PR](https://github.com/Kim-Lee-Park/KimLeePark-Logistics/pull/217)
