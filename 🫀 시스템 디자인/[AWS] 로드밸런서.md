## AWS 로드 밸런서

**Classic Load Balancer (CLB)**

- 구형(레거시), L4/L7 혼합 지원이지만 기능이 제한적이고 ALB/NLB 로 대체되는 흐름
- 신규 설계에서는 보통 선택하지 않음

**Application Load Balancer (ALB)**

- L7 (HTTP/HTTPS)
- 요청을 내용 기반으로 라우팅
  - Host 기반, Path 기반, Header/Query 기반, 우선순위 규칙 등
- Target Group 단위로 라우팅/헬스체크/트래픽 분산을 관리
- ECS (Fargate/EC2) 와 가장 잘 맞는 기본 선택지 (일반적인 웹 API/웹 서비스)

**Network Load Balancer (NLB)**

- L4 (TCP/UDP/TLS)
- 초고성능/초저지연 및 정적 IP(EIP) 사용 가능
- HTTP 라우팅 규칙(패스/호스트)은 없음
- TCP 레벨에서의 로드밸런싱
  - gRPC, TLS passthrought 등에서 선택

</br>

### ALB 의 핵심 리소스 관계 (Listener → Rule → Target Group → Target)

**Listener**

- ALB 가 어떤 포트/프로토콜로 요청을 받는지 정의 (예시: 80/HTTP, 443/HTTPS)

**Listener Rule**

- Listener 에 붙는 라우팅 규칙
- 예시) `api.example.com` + `/orders/*` → `order-service TG`
- 예시) `/inventory/*` → `hub-service TG`

**Target Group**

- ALB 가 요청을 어디로 보낼지를 아는 논리적인 묶음
- 타겟 등록/해제, 헬스체크, 드레이닝(연결 정리) 같은 운영 정책이 여기에 붙음
- ECS 에서는 보통 `target-type = ip` (awsvpc 모드라서 Task ENI IP 로 타게팅)

**Target (ECS Task)**

- 실제 트래픽을 받는 대상 (컨테이너/태스크)
- 오토스케일, 재배포, 재시작으로 IP가 바뀌는 존재라서 고정 엔드포인트가 필요하고 그게 ALB 의 역할

</br>

### Health Check

**Container Health Check**

- 컨테이너 레벨의 헬스체크
- 목적
  - 프로세스 생존/기본 동작 확인 (간단한 체크)
- 컨테이너 내부에서 돌아가는 헬스체크, 외부 트래픽 관점 준비 여부를 100% 해결하지는 못함
- 예시) 프로세스가 띄워져 있는지, 기본 endpoint 가 200으로 응답하는지 등

**ALB Health Check**

- 로드밸런서 레벨의 헬스체크
- 목적
  - 트래픽을 받을 준비가 되어있는지 확인
- Target Group 에 정의됨
- ALB 가 주기적으로 HTTP 요청을 날려서 성공/실패로 타겟 상태를 관리
- 실패하면 해당 Task 로 트래픽 전송을 중단, 정상화되면 다시 포함

**Health Check Grace Period (ECS Service 설정)**

- `healthCheckGracePeriodSeconds`
- Task 시작 직후에는 앱 기동/웜업 때문에 헬스 체크가 실패할 수 있음
- 해당 시간 동안 실패를 무시하여 기동 중인데 헬스 체크를 하는 비정상 상황을 줄임

</br>

### Draining / Deregistration Delay

- 종료되는 Task 는 새 요청을 받지 않도록 하는 것

**Connection Draining (ALB 관점)**

- 종료 예정 타겟은 새 연결(새 요청)을 받지 않게 하고 이미 들어온 요청은 마저 처리하도록 시간을 줌

**Deregistration Delay (Target Group 속성)**

- Target Group 에 있는 등록 해제 지연 시간
- 기본 300초
- Task 가 종료될 때
  - 타겟을 바로 제외한다면 → 진행 중 요청이 끊어질 수 있음
  - 너무 길다면 → 배포/스케일 인 시간이 느려짐
- 그래서 서비스 성격(요청 평균 처리 시간, 롱폴링 여부 등)에 맞게 조정하는게 핵심
