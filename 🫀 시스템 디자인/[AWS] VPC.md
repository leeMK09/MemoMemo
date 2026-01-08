## VPC (Virtual Private Cloud)

- AWS 인프라의 모든 트래픽 기준이 되는 논리적인 네트워크 모델
- AWS 리소스들이 어떤 경로, 어떤 조건 등에서 통신할 수 있는지를 결정하는 네트워크 정책의 최상위 컨테이너
  - EC2, ECS, RDS, ALB, NAT, Lambda 등
- 특징
  - 패킷의 흐름을 결정하는 정책 집합의 역할을 가짐
  - 정의
    - 주소 체계 (CIDR)
    - 경로 선택 (Route Table)
    - 접근 제어 (Security Group / NACL)
    - 외부 연결 여부 (IGW / NAT / Endpoint)

</br>

## VPC 내부 트래픽이 흐르는 순서

- EC2/ECS Task 가 외부로 요청이 나갈 때
  - [Application] → [ENI] → [Security Group] (허용/차단) → [Route Table](어디로 보낼지) → [NAT Gateway or IGW] → [Internet or AWS Service]

</br>

## Subnet 은 단순히 IP 분할을 위한 것은 아니다

- 흔히들 AZ별로 IP 범위를 나누는 용도, Private/Public 나누는 용도로 이해한다
- 실제 본질은 Subnet = Route Table 을 적용하는 최소 단위이다
  - Subnet 은 IP 대역을 쪼개는 것이 1차 기능이지만 AWS 에서는 사실상 어떤 길로 나가고 들어오는지를 결정하는 정책 적용 단위 (= Route Table)
  - 보안, 장애격리를 위해서 서브넷을 구분한다
  - Public Subnet 이 적용되는 이유: `0.0.0.0/0 → Internet Gateway`
  - Private Subnet 이 적용되는 이유: `0.0.0.0/0 → NAT Gateway`
  - 같은 VPC 라도 Subnet이 다르다면 완전히 다른 네트워크 성격을 가질 수 있음

</br>

## Route Table

- 해당 패킷들이 어디로 가야하는지에 대한 정보가 표시

예시)

| Destination | Target   |
| ----------- | -------- |
| 10.0.0.0/16 | local    |
| 0.0.0.0/0   | igw-xxxx |

- `local` → VPC 내부 통신
- `igw` → 인터넷
- `nat` → 프라이빗에서 외부로 통신
- VPC 내부 통신(local)은 항상 암묵적으로 열려 있다
  - Security Group 에서 막지 않는 한 열려있음

</br>

## Security Group

- 단순히 방화벽 또는 인바운드/아웃바운드 롤의 기능도 있지만 **Security Group은 상태를 기억하는(Stateful) 성격의 연결 정책**이다
- 인바운드 허용 → 자동으로 인바운드 허용
  - 즉 Request를 열어놓으면 Response 는 자동으로 나감
- 연결 단위로 허용/차단을 판단함
- 리소스에 붙음
  - EC2, ENI, ALB, ECS Task 등
- 보안 그룹은 ECS awsvpc 에서 Task 단위 보안을 처리함
- 해당 서비스는 특정 서비스만 호출 가능하도록 설계가 가능함
- 인스턴스 레벨 보안
  - 인스턴스에 붙는 가상 방화벽의 개념, 인스턴스의 네트워크 인터페이스(ENI)에 적용됨
  - 하나에 보안 그룹에 여러개의 EC2 연결 가능, 하나의 EC2 에 여러개의 보안그룹 연결 가능
- 기본적으로 보안그룹은 Inbound 트래픽은 Deny 되어있음
  - 명시적으로 허락되지 않는이상 허용되지 않음
- 대상 지정 가능
  - IP (CIDR)
  - 다른 보안그룹

</br>

## NACL (Network ALC)

- 서브넷 단위의 네트워크 방화벽
- 해당 서브넷에 속한 모든 리소스에 적용됨
- **Stateless(무상태)** 이며 인바운드 규칙을 허용하면 이에 따라 아웃바운드 규칙도 별도로 허용 규칙을 정의해야함
  - 응답 트래픽도 직접 열어야 함
- Allow / Deny 모두 존재함
  - 명시적 차단 가능 및 네트워크 공격 차단에 유리
- 특정 IP 대역 전체 차단 / 보안 사고 시 즉각적인 네트워크 차단 / 규정상 네트워크 레벨 통제가 필요한 경우 사용

</br>

## IGW vs NAT

- Internet Gateway (IGW)
  - 일반적으로 Public 통신을 위해 사용
  - 양방향 통신, Public IP 필수
- NAT Gateway
  - Outbound only
  - Private Subnet 전용 및 외부에서 접근 불가
  - 외부 패키지 설치 혹은 요청에 대한 응답을 처리할때 사용

</br>

## VPC Endpoint

- 아무런 설정이 없다면 NAT 비용은 S3, ECR, DynamoDB 접근을 할때에도 NAT 를 타게되며 이때 비용이 발생함
- VPC Endpoint 를 사용하면 AWS 내부 네트워크로 바로 연결
  - VPC 의 서브넷안에 ENI 생성 → 해당 ENI 에 사설 IP 가 할당됨
  - Private Subnet 통신시 VPC 사설 ENI 로 라우팅하여 ECR, S3 등 접근
- 즉 보안 및 비용 최적화시 사용
