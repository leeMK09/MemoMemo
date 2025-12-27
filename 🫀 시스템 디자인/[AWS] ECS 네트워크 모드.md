## bridge vs awsvpc

- bridge
  - 네트워크 주체 : EC2 인스턴스가 주체
  - IP 소유 : EC2 인스턴스 IP
  - 포트 : 포트 매핑 필요
  - 보안 그룹 : EC2 단위
  - ALB 연동 : 인스턴스 기반 타겟팅
  - EC2 한정
- awsvpc
  - 네트워크 주체 : Task 가 주체
  - IP 소유 : Task 전용 ENI + VPC IP
  - 포트 : 포트 매핑 불필요
  - 보안 그룹 : Task 단위
  - ALB 연동 : IP 기반 타겟팅 (TG target-type = ip)
  - Fargate 기준 표준 및 필수

</br>

## bridge 네트워크 모드의 구조 (전통적인 Docker 방식)

- 구조
  - [Internet] → [ALB] → [EC2 인스턴스 : 10.0.1.10] → [docker0 bridge (172.17.0.0/16)(사설 IP)] → ([컨테이너 A : 172.17.0.2:8080], [컨테이너 B : 172.17.0.3:8080])
- 특징
  - 컨테이너는 사설 브리지 네트워크에 존재함
  - 외부에서 접근하려면 `EC2:80` → `컨테이너:8080` 같은 포트 매핑이 필요함
  - ALB/NLB 는 컨테이너를 직접 알지못함
    - 해당 EC2 로 요청을 보내면 그 안에서 Docker 가 알아서 라우팅

### 문제점

- **포트 충돌**
  - 같은 EC2에 컨테이너 여러 개 → 같은 포트를 못씀
- **보안 제어가 세밀하지 않음**
  - 보안 그룹이 EC2 단위로만 제어가능
  - 특정 컨테이너만 차단은 불가능
- **동적 스케일링과 궁합이 나쁨**
  - 컨테이너는 늘고 줄지만, 네트워크 관점에선 항상 EC2 까지만 볼수있음

</br>

## awsvpc 네트워크 모드의 구조 (ECS가 원하는 모델)

- 구조
  - [VPC] → (Subnet 10.0.1.0/24) [Task A ENI : 10.0.1.101], [Task B ENI : 10.0.1.102] → 각 Task 의 컨테이너 : 8080
- 특징
  - Task 마다 ENI (Elastic Network Interface) 를 직접 가짐
  - Task 는 아래 네트워크 구조로 정의됨
    - VPC IP
    - Subnet
    - Security Group
    - Route Table
  - 포트 매핑이 불필요
  - 모든 Task 가 같은 포트(ex: 8080) 사용 가능함
  - ALB 가 IP 기반 (Target Group type = ip) 으로 Task 를 직접 타겟팅
  - 보안 그룹을 Task 단위로 제어 가능

</br>

## 왜 ECS 에서는 awsvpc 만 사실상 표준이 되었을까?

1. Fargate

- Fargate 는 EC2 가 보이지 않는 서버리스 런타임 환경
- EC2 기반 bridge 모델은 인스턴스에 포트 매핑 및 docker0 bridge 관리 필요 → Fargate 철학과 충돌
- 그래서 Fargate = awsvpc 강제

2. ALB + 동적 스케일링과의 궁합

- ECS 의 기본 전제 : Task는 자주 생성 및 삭제 / IP는 계속 바뀜 / 수평 확장이 기본 철학
- ALB 가 좋아하는 방식은
  - Target Group 에 IP를 등록 및 해제
  - 헬스체크 실패한 IP는 즉시 제외함
- 즉 awsvpc 는 ALB 와 맞는 모델철학을 가짐

3. 보안 모델의 정합성

- 보안그룹은 1급 보안 단위
- awsvpc 는 Task 마다 Security Group(SG) 부여 가능
  - 서비스별/역할별 최소 권한 네트워크 구성 가능
- bridge 는 SG 가 EC2 단위로 할당되며 컨테이너 별 격리가 불가능함
