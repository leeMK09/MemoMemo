## AWS 오케스트레이션

- 대표적으로 쿠버네티스, ECS, EKS 등이 있음
- 하는 일
  - 컨테이너가 죽을때 설정한 기본 컨테이너 수만큼 다시 재시작
  - 자동 확장
  - 새 버전 배포 시 다운타임 없이 배포
  - 수십개의 컨테이너 배치
  - 즉 여러 개의 컨테이너를 어디에 배치할지, 몇 개를 돌릴지, 죽으면 재시작할지 자동으로 관리하는 시스템

## ECS

- AWS 리소스내의 컨테이너를 위한 운영 시스템
- 단순히 컨테이너 실행 도구가 아닌 컨테이너의 생명주기 전체를 관리하는 운영 플랫폼
- ECS 가 제공하는 것
  - Task/Service 관리 : 컨테이너를 단일 명령으로 실행하고 관리하고 싶다
  - Fargate (서버리스) : 서버 인프라 관리를 줄이고 싶다
  - Auto Scaling & Load Balancing : 컨테이너 수평 확장이 필요하다
  - Rolling Update, Blue/Gree : 변경된 버전 배포를 안전하게 하고 싶다
- 무엇을 실행할 것 인가? -> `Task Definition`
- 어떻게 실행할 것 인가? -> `Service`
- 어디에서 실행할 것 인가? -> `Cluster`

### Task Definition

- 컨테이너 실행을 위한 도면, 설계도
- 왜 필요한가?
  - 사람이 매번 수동으로 설정하면 실수 발생
  - 자동 복구 시 동일한 환경 재현 필요
  - 배포 자동화의 기반
- 구성
  - 컨테이너 띄울 이미지 버전
  - CPU 메모리, 환경변수
  - 어떤 포트, 로그는 어떻게

테라폼 구성 예시 (Auth 서버 + Otel Collector [사이드카 방식])

```terraform
resource "aws_ecs_task_definition" "auth" {
  family                   = "${local.project}-auth"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = local.ecs_services.auth.cpu
  memory                   = local.ecs_services.auth.memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([
    {
      name      = "auth"
      image     = "${data.aws_ecr_repository.service["auth"].repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]

      dependsOn = [
        {
          containerName = "otel-collector"
          condition     = "START"
        }
      ]

      environment = [
        {
          name  = "SPRING_PROFILES_ACTIVE",
          value = var.environment
        },
        {
          name  = "CONFIG_SERVER_URL",
          value = "http://config.klp.local:8888"
        },
        {
          name  = "AUTH_DOMAIN_NAME",
          value = "auth.klp.local"
        },
        {
          name  = "AUTH_SERVICE_PORT",
          value = "8080"
        },
        {
          name  = "AUTH_DB_DRIVER",
          value = "org.postgresql.Driver"
        },
        {
          name  = "AUTH_DB_URL",
          value = local.db_urls.auth
        },
        {
          name  = "INTERNAL_ALB_HOST",
          value = aws_lb.internal_alb.dns_name
        },
        {
          name  = "USER_SERVICE_PORT",
          value = "8010"
        },
        {
          name  = "KAFKA_BOOTSTRAP_SERVERS",
          value = local.kafka_bootstrap
        },
        {
          name  = "SERVER_URL",
          value = local.alb_server_url
        },
        {
          name  = "JWT_ACCESS_EXPIRATION",
          value = "3600000"
        },
        {
          name  = "JWT_REFRESH_EXPIRATION",
          value = "604800000"
        },
        {
          name  = "OTEL_EXPORTER_OTLP_ENDPOINT",
          value = "http://localhost:4318"
        },
        {
          name  = "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT",
          value = "http://localhost:4318/v1/traces"
        },
        {
          name  = "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT",
          value = "http://localhost:4318/v1/logs"
        },
        {
          name  = "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT",
          value = "http://localhost:4318/v1/metrics"
        },
        {
          name  = "OTEL_TRACES_EXPORTER",
          value = "otlp"
        },
        {
          name  = "OTEL_METRICS_EXPORTER",
          value = "otlp"
        },
        {
          name  = "OTEL_LOGS_EXPORTER",
          value = "otlp"
        },
        {
          name  = "OTEL_SERVICE_NAME",
          value = "klp-logistics-auth"
        },
        {
          name  = "OTEL_RESOURCE_ATTRIBUTES",
          value = "service.namespace=klp"
        },
        {
          name  = "OTEL_INSTRUMENTATION_HTTP_SERVER_EXCLUDE_PATTERNS",
          value = "/actuator/.*,/swagger-ui/.*,/v3/api-docs/.*,/v1/api-docs/.*"
        }
      ]

      secrets = [
        {
          name      = "AUTH_DB_USERNAME"
          valueFrom = data.aws_secretsmanager_secret.db_username.arn
        },
        {
          name      = "AUTH_DB_PASSWORD"
          valueFrom = data.aws_secretsmanager_secret.db_password.arn
        },
        {
          name      = "JWT_ACCESS_SECRET"
          valueFrom = data.aws_secretsmanager_secret.jwt_access_secret.arn
        },
        {
          name      = "JWT_REFRESH_SECRET"
          valueFrom = data.aws_secretsmanager_secret.jwt_refresh_secret.arn
        }
      ],
    },
    {
      name      = "otel-collector"
      image     = local.otel_image
      essential = false

      portMappings = [
        {
          containerPort = 4317
          protocol      = "tcp"
        },
        {
          containerPort = 4318
          protocol      = "tcp"
        },
        {
          containerPort = 9464,
          protocol      = "tcp"
        }
      ]

      environment = [
        {
          name  = "AWS_REGION"
          value = var.aws_region
        },
        {
          name  = "OTEL_LOG_LEVEL"
          value = "error"
        },
        {
          name  = "TEMPO_HOST"
          value = "tempo.klp.local"
        },
        {
          name  = "LOKI_HOST"
          value = "loki.klp.local"
        },
        {
          name  = "OTEL_RESOURCE_ATTRIBUTES"
          value = "service.namespace=klp,service.name=otel-collector"
        }
      ]
    }
  ])
}
```

### Task

- Task Definition 을 기반으로 실행된 컨테이너 그룹
- 하나의 Task = 하나 이상의 컨테이너 묶음

### Service

- Task 가 항상 N개 실행되도록 유지하는 관리자
- 왜 필요한가?
  - 컨테이너는 언제든 죽을 수 있음
  - 트래픽 변동에 따라 개수 조절 필요
  - 무중단 배포 필요
- 하는 일
  - 지정된 개수의 Task 를 항상 유지 > Desired Count = 3 이면, 죽을때 자동으로 다시 띄움
  - Auto Scaling 연동 > CPU 70% 넘으면 자동으로 Task 추가
  - Load Balancer 와 연결 > 새로 생성된 Task 를 ALB 에 자동 등록
  - 롤링 업데이트 > 새 버전 배포시 하나씩 교체
  - Health Check > 비정상 Task 는 자동 제거 후 재생성
- **핵심은 ECS 의 자가 치유(Self-Healing) 기능**

테라폼 예시 (Auth ECS Service + Internal ALB [블루/그린 배포])

```terraform
resource "aws_ecs_service" "auth" {
  name            = "${local.project}-auth-svc"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.auth.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  deployment_controller {
    type = "CODE_DEPLOY"
  }

  network_configuration {
    subnets = [aws_subnet.private_app_az1.id, aws_subnet.private_app_az2.id]
    security_groups = [aws_security_group.ecs_service.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.internal_blue["auth"].arn
    container_name   = "auth"
    container_port   = 8080
  }

  service_registries {
    registry_arn = aws_service_discovery_service.ecs["auth"].arn
  }

  depends_on = [
    aws_lb_target_group.internal_blue["auth"],
    aws_lb_target_group.internal_green["auth"]
  ]
}
```

### Cluster

- Task 를 실행할 인프라 환경
- 왜 필요한가?
  - 컨테이너를 어딘가에 올려야 함
  - CPU/메모리 자원을 어디서 가져올지 결정
- 두 가지 방식
  - EC2 기반 Cluster
    - 세밀한 제어 가능, 더 다양한 인스턴스 타입 선택
    - EC2 관리 필요(패치, AMI 업데이트), 용량 계획 필요
  - Fargate 기반 Cluster
    - 서버 관리 불필요, 사용한 만큼만 과금, 보안 강화
    - EC2 보다 약간 비쌈, CPU/Memory 조합 제한

### Scheduler

- 컨테이너를 어디에 배치할지 자동으로 결정
- 배치 전략 (EC2 모드)
  - binpack
    - CPU 나 메모리를 최대한 활용
    - 인스턴스 수 최소화
    - 비용 최적화에 유리
  - spread
    - 지정된 값(AZ, 인스턴스ID) 에 균등 분산
    - 가용성 향상
    - 한 AZ 장애 시 영향 최소화
  - random
    - 무작위 배치
- 특별한 요구사항이 없다면 Fargate 는 spread 전략을 자동으로 사용

### 네트워크 구조

- 문제 상황
  - 옛날 방식 : EC2 하나에 여러개의 컨테이너 -> 포트 충돌
  - 보안 그룹을 EC2 단위로만 설정 -> 세밀한 제어 불가
- 네트워크 모드 awsvpc
  - 각 Task 가 ENI 를 가짐
  - Task 마다 Private IP 할당
  - 보안 그룹을 Task 단위로 설정 가능
- 비교
  - bridge 모드 (옛날 방식)
    - EC2 하나의 보안 그룹 할당
    - Container1 (포트 8080)
    - Container2 (포트 8081) <- 포트 매핑 필요
    - 모두 같은 보안 정책 사용
  - awsvpc 모드 (지금 방식)
    - Task1 (독립된 ENI, 독립된 보안그룹 할당, 10.0.1.10:8080)
    - Task2 (독립된 ENI, 독립된 보안그룹 할당, 10.0.2.10:8080)
    - 포트 충돌이 없음
- awsvpc 제약사항
  - ENI 개수 제한 (서브넷의 IP 개수 제한)
  - Task 당 하나의 ENI 소모
  - 큰 클러스터는 충분한 IP 주소 확보 필요
