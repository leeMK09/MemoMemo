# VPC Endpoint

- VPC Endpoint 는 VPC 내부 리소스가 AWS 서비스 (S3, DynamoDB, SQS, Secrets Manager 등) 에 접근할 때 인터넷을 거치지 않고 완전히 Private 네트워크로 연결하게 해주는 기능이다
- 즉 Internet Gateway, NAT Gateway, Public IP 가 불필요하다
- AWS 내부 네트워크를 통해 직접 연결하며 보안 강화 및 비용 절감에 용이하다

예시)

- Private Subnet 안에서 EC2/ECS/Lambda 가 S3 접근, Secrets Manager 조회, SQS 메시지 송수신시 인터넷 없이 수행 가능하다

</br>

## VPC Endpoint 의 두 가지 종류

1. Gateway Endpoint
2. Interface Endpoint (AWS PrivateLink)

### Gateway Endpoint

- Route Table 에 엔드포인트를 추가하여 트래픽을 S3 또는 DynamoDB 로 직접 라우팅하는 방식
- 연결방식은 위처럼 Route Table 을 통해 제어한다
- 비용은 무료이며 **S3, DynamoDB 만 가능하다**
- ENI 를 생성하지 않고 NAT 도 불필요하다

**동작 방식**

1. Private Subnet 의 EC2 가 S3 로 요청
2. Route Table 에 설정된 Gateway Endpoint 경로로 이동
3. AWS 내부 네트워크로 S3 접근

즉 IP 기반 제어가 아닌 라우팅 기반 제어 방식

- 대표적인 사용 예시
  - Private Subnet EC2 → S3 파일 다운로드
  - ECS Task → DynamoDB 접근

### Interface Endpoint (AWS PrivateLink)

- VPC 내부 Subnet 에 ENI (Elastic Network Interface) 를 생성해서 AWS 서비스와 사설 IP 기반으로 연결하는 방식
- 연결방식은 ENI + Private IP 기반 제어를 수행한다
- 지원 서비스는 거의 모든 AWS 서비스를 지원한다
- 비용은 시간당 요금 + 데이터 전송량에 따라 다르다
- AZ 단위별로 생성하며 보안그룹 설정이 가능하다

**동작 방식**

1. Subnet 에 ENI 생성 (사설 IP 할당)
2. 서비스 도메인(ecr.amazonaws.com) 이 사설 IP로 해석된다
3. 트래픽은 VPC 내부에서만 흐른다

- 대표적인 사용 예시
  - DNS + ENI + Security Group 조합으로 구성한다
  - SQS, SNS, Secrets Manager, KMS, CloudWatch, Lambda, ECR, STS 를 지원한다

</br>

### 비용 관점

```text
Private Subnet → NAT Gateway → Internet → AWS API
```

- 위 구조에서 NAT 와 Interface Endpoint(PrivateLink) 비교
- NAT Gateway
  - 데이터를 얼마나 보내는가 기준으로 요금 (GB 기준, $0.045 / GB)
- Interface Endpoint
  - NAT 와 달리 고정된 비용 계산 → 시간당 요금 존재
  - 1GB 기준으로는 $0.01 ~ $0.02 / GB 로 NAT 보다 비용이 싸다 그러나 시간당 비용이 지속적으로 발생

</br>

### 고려사항

**Interface VPC Endpoint(AWS PrivateLink) 는 아웃바운드 차단이 목적**

- Inbound 의 보안, 비용이 아닌 외부 인터넷으로 나가는 경로 자체를 제거하는 개념이다

**ECR, STS, CloudWatch 는 Interface VPC Endpoint(AWS PrivateLink) or NAT Gateway 없으면 장애 발생**

- Private Subnet + ECS 환경에서 자주 터지는 문제들은 아래와 같습니다
  - ECR pull 실패 → ECS 가 컨테이너를 실행할 때 ECR API 호출, Docker 이미지 레이어 다운로드를 수행하며 이 둘은 모두 AWS API 호출
  - STS AssumeRole 실패 → STS 시 IAM Role 에 대한 AWS API 호출
  - CloudWatch 로그가 안찍힘 → CloudWatch Logs 또한 AWS API 호출
- 즉 내부적인 AWS API 호출시 NAT Gateway 를 구성하거나 Interface VPC Endpoint 를 구성하여 문제를 해결할 수 있다 → 두 워크로드 모두 미구성시 장애발생

**Interface VPC Endpoint 는 AZ 마다 생성 필요**

- Interface VPC Endpoint 는 ENI 기준으로 제어를 수행한다
  - 즉 Endpoint 생성시 특정 Subnet 안에 ENI 하나가 생성되며 해당 ENI 는 하나의 AZ 에만 속하게 된다
- Interface VPC Endpoint 는 리전 전체에 하나가 아닌 AZ 단위로 존재하는 네트워크 장치를 의미한다
- Endpoint 를 1a Subnet 에만 구성한다면
  - 1a 에서 실행된 ECS Task 는 정상일지라도 1b / 1c Subnet 에서 수행된 ECS Task 는 실패한다
  - 1b, 1c에는 ENI가 없으며 같은 AZ 내부에서 접근 가능한 사설 IP 경로가 존재하지 않음

**Gateway VPC Endpoint 는 Route Table 이 필수**

- GatewayEndpoint 는 네트워크 장치가 아닌 특정 목적지(S3, DynamoDB) 로 가는 경로를 Route Table 에 추가하는 개념이다
- 즉 Subnet 에 붙는 장치가 아닌 Route Table 에 경로를 붙여주는 기능이다 → 그러므로 Route Table 구성이 필수이다

**Interface VPC Endpoint 의 DNS 옵션 (Enable Private DNS)**

- Enable Private DNS 의 의미

```text
ecr.amazonaws.com
sts.amazonaws.com
logs.amazonaws.com
```

- 위와 같이 AWS 서비스는 보통 이런 도메인을 사용한다
- 만약 Enable Private DNS = ON 이라면 해당 도메인이 Public IP가 아닌 Interface Endpoint 의 ENI 사설 IP로 해석한다
- 즉 `ecr.amazonaws.com` → 10.0.12.34 (VPC 사설 IP)
- 이렇게 되어야 Interface Endpoint 를 통해 통신하며 OFF 시 해당 도메인은 그대로 Public IP 로 해석하며 VPC Endpoint 를 사용하지 않는다
