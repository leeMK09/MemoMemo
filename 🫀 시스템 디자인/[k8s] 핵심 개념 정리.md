## 핵심 개념 정리: Pod, Service, Deployment, ReplicaSet, ConfigMap, Secret, Ingress, StatefulSet, PV/PVC, Job, Resource Request/Limit

- Pod: 컨테이너 실행 단위 (가장 작은 배포 단위)
- ReplicaSet: Pod 를 원하는 개수로 유지 
- Deployment: ReplicaSet을 이용한 업데이트/롤백 등 배포전략을 제공 
- Service: Pod 집합에 대한 고정된 접근 주소 + 로드밸런싱 

</br>

### 1. Pod: 쿠버네티스가 다루는 최소 실행 단위 

- Pod 는 쿠버네티스에서 컨테이너를 실행하는 가장 작은 단위 
- 보통은 컨테이너 1개이지만 필요하면 여러 컨테이너를 한 Pod 에 묶을 수 있다 (Sidecar 패턴)

**특징**

- IP 를 가진다 (Pod 마다 할당)
- 같은 Pod 안 컨테이너들은 
    - 네트워크 네임스페이스 공유 → localhost 로 통신 가능 
    - 스토리지(Volume) 공유 가능 
- Pod 는 잠깐 존재하는 존재, 죽으면 다른 노드에 새로운 Pod 로 재생성될 수 있고 IP 도 바뀔 수 있다 
- 즉 Pod 는 불변 인프라가 아닌 언제든 교체 가능한 휘발성 인스턴스로 보는게 맞다 

</br>

### 2. ReplicaSet: Pod 를 원하는 개수로 유지하는 컨트롤러 

- Pod 를 n개로 유지하도록 보장하는 컨트롤러 
- 예를들어 `replicas=3` 이라면 
    - Pod 하나가 죽으면 즉시 새로운 Pod 를 만들어 3개를 맞춘다 
    - 노드 장애가 발생해도 스케줄러가 다른 노드에 Pod를 띄워서 개수를 복구한다 
- ReplicaSet을 직접 만들기보다는 Deployment가 자동으로 만드는 ReplicaSet을 쓰는 구조가 일반적이다 
- 즉 ReplicaSet 은 Deployment 의 내부 구현 요소로 자주 등장한다 

</br>

### 3. Deployment: 배포(업데이트/롤백)의 표준 

- Deployment는 ReplicaSet을 관리하면서 다음을 처리한다 
    - 롤링 업데이트 (무중단 배포에 가까운 방식)
    - 롤백 
    - 버전 히스토리 관리    
    - 선언형 업데이트 (원하는 상태를 선언하면, 그 상태로 맞춰줌)
- 롤링 업데이트시 동작
    - 새 버전 배포시 새로운 ReplicaSet 생성 
    - 새로운 ReplicaSet 의 Pod 를 조금씩 늘리고 
    - 기존 ReplicaSet 의 Pod 를 조금씩 줄인다 (설정 가능)

</br>

### 4. Service: Pod는 바뀌니 고정된 입구가 필요하다 

- Pod는 죽었다 살아나면 IP가 바뀐다 
- 그럼 외부(또는 다른 서비스)에서 어디로 요청을 보내야 하는지가 변경된다 
- Service 는 이러한 문제를 해결한다 
    - 이 라벨을 가진 Pod 들로 트래픽을 보내라 + 고정된 접근 지점을 제공하라 
- Service 가 하는 일 
    - Pod 셀렉터(label selector) 로 대상 Pod 집합을 찾는다 
    - 그 집합에 대해 로드밸런싱을 제공한다 
    - (클러스터 내부/외부) 접근 방식을 정의한다 
- Service Type
    - ClusterIP: 클러스터 내부에서만 접근 (기본 설정)
    - NodePort: 각 노드의 특정 포트를 열어 외부 접근 처리 
    - LoadBalancer: 클라우드 LB를 붙여서 외부 접근 처리 (AWS/GCP 등)
    - Headless(None): 로드밸런싱 없이 Pod 엔드포인트를 직접 노출 (Stateful 에서 자주 사용)

</br>

### 5. ConfigMap/Secret: 설정과 시크릿을 따로 구성 

- 컨테이너 이미지는 변경할 때 배포를 통해 처리하며 달라지는 설정 및 시크릿 값들은 따로 관리하여 변경한다 
- ConfigMap 은 두 가지 방식으로 주입처리
    - 환경변수 → Pod 시작 시점에 kubelet 이 컨테이너 런타임에 넘겨줌 / 컨테이너 프로세스 입장에선 그냥 환경변수 
    - 볼륨으로 마운트 → ConfigMap 을 파일 형태로 마운트 
- Secret 은 민감한 정보를 취급하며 ConfigMap 과 권한을 다르게 설정할 수 있다 
    - ConfigMap 과 거의 동일하게 값을 주입한다 

</br>

### 6. Ingress: Service 를 넘어서 HTTP 라우팅/보안등을 다루는 계층 

- Service는 Pod 집합에 대한 L4 접근 제어 
    - 포트 기반, 단순 LB
- Ingress 는 L7(HTTP) 라우팅 규칙을 제어 
    - 도메인/경로 기반 라우팅 
    - TLS 종료(HTTPS)
    - 인증/리다이렉트/헤더 정책 같은 것들 (컨트롤러에 따라 정의)
- Client → (Cloud LB) → Ingress Controller → Service → Pod 
    - 보통 위 흐름으로 외부 트래픽을 처리 
- Ingress 리소스 자체는 규칙이고 실제로 트래픽을 받아 처리하는 것은 Ingress Controller 이다 
    - AWS 라면: AWS Load Balancer Controller (ALB Ingress)
    - 일반 클러스터: NGINX Ingress Controller, Traefix 등 
- Ingress Controller 는 Ingress 리소스를 watch
    - 규칙을 반영하고 실제 LB 설정/리버스 프록시 설정을 갱신 
- AWS ALB Ingress 를 사용한다면 
    - Ingress 생성 → 컨트롤러가 자동으로 ALB 생성/리스너/룰/타겟그룹 구성 
    - path `/api` → svc-a
    - path `/auth` → svc-b
    - host `api.example.com` → svc-a
- **Ingress 설계에서의 이슈들**
    - Readiness 와 연동: readiness 실패한 Pod 는 endpoints 에서 빠지며 결과적으로 트래픽이 안감 
    - 경로 라우팅 우선 순위: `/` 와 `/api` 룰 우선순위 꼬이면 문제 발생 
    - WebSocket/HTTP2: 컨트롤러별 버전 지원 차이 
    - 대규모 룰: 룰이 많아진다면 ALB 제한 및 운영 복잡도 증가 

</br>

### 7. StatefulSet: 상태 있는 워크로드의 정체성과 스토리지 보장 

- Deployment 는 기본적으로 Pod 가 죽으면 새로운 Pod 를 띄우고 Pod identity 가 변경되어도 상관없다는 전체 → Stateless
- DB/Kafka 등은 특정 인스턴스가 특정 데이터/디스크를 가져야하고 네트워크 Identity 도 어느 정도 예측 가능해야 한다 → Stateful
- StatefulSet 이 보장하는 것 
    - 안정적인 Pod 이름/Identity
        - `db-0`, `db-1` 같이 ordinal 으로 고정, 재시작해도 같은 이름을 사용 
    - Pod 별 전용 스토리지
        - StatefulSet 은 Pod 마다 PVC 를 자동으로 생성, 즉 `db-0` 이 죽고 다시 떠도 같은 PVC 를 다시 물고 올라온다 
- Headless Service 와 세트로 사용 
    - StatefulSet 은 보통 Headless Service(clusterIP: None) 와 같이 사용한다 
    - 로드밸런싱이 아닌 각 Pod 의 DNS 를 직접 제공 (`db-0.db-headless.default.svc.cluster.local`)
- 실무에서는 운영 DB는 RDS 같은 매니지드 서비스 사용, K8s 에서는 stateless 위주 

</br>

### 8. PV/PVC: Pod 와 스토리지를 분리하는 추상화 계층 

- Pod 는 휘발성이지만 데이터는 영속성이 필요하다 
- 또한 애플리케이션 관점에선 `EBS`, `NFS` 등의 구현을 알아야할 필요가 없음 
    - 그래서 K8s 는 스토리지를 추상화한다 
- PVC: 이 정도 스토리지가 필요하다 → 요청서 역할 
- PV: 이 디스크 할당 → 실제 자원 역할 
- StorageClass: 동적 생성 규칙 

**동작 흐름**

- PVC 생성 
    - 개발자는 20Gi, RWO, gp3 같은 요구사항을 적는다 
- 바인딩 (Binding)
    - 정적: 이미 있는 PV 중 조건 맞는 것에 바인딩 
    - 동적: StorageClass 가 있다면 PV 를 자동으로 만들어서 바인딩 
    - EKS 라면 EBS CSI Driver 가 PV 생성 (EBS 볼륨 생성) 까지 수행 
- Pod 가 PVC 를 마운트 
    - Pod 가 뜨면 kubelet 이 해당 볼륨을 노드에 attach, mount 이후 컨테이너에 전달함 
    - VolumeClaimTemplates (= StatefulSet 과 연결)
        - StatefulSet 이 Pod 마다 PVC 를 만들어주는 메커니즘이 바로 volumeClaimTemplates

**Access Mode 가 설계에 미치는 영향**

- RWO (ReadWriteOnce): 한 노드에만 붙을 수 있다 (EBS 가 대표적)
    - 그래서 StatefulSet 에서는 흔하다 
- RWX (ReadWriteMany): 여러 노드에서 동시에 mount 가능 (NFS/EFS 등)

</br>

### 9. Job/CronJob: 서버가 아닌 작업 실행을 쿠버네티스에서 관리 

- 한 번 실행하고 끝나는 작업은 Job 이 담당 
- Job 의 동작 방식 
    - completions: 총 몇 번 성공해야 완료로 처리하는지 
    - parallelism: 동시에 몇 개 실행하는지 
    - backoffLimit: 실패 시 몇 번 재시도할지 
    - restartPolicy: 실패 시 Pod 재시작 정책 
- 즉 Job 은 Pod 를 생성해서 성공할 때까지 관리한다 라고 이해하면 된다 
- CronJob 
    - 스케줄러 + Job 생성기 
    - cron 시간마다 Job 을 생성, 생성된 Job이 실행된다 
    - concurrencyPolicy
        - Allow(기본): 겹쳐서 실행될 수 있음 
        - Forbid: 이전 작업이 끝나야 다음 작업을 실행 
        - Replace: 새 실행이 오면 이전 실행을 중단 
    - successfulJobsHistoryLimit / failedJobsHistoryLimit
        - 설정하지 않으면 Job 이 계속 쌓이게 되고 리소스/관리 이슈가 발생함 

</br>

### 0. HPA: Pod 수를 자동으로 늘리고 줄이는 방식 

- HPA 가 하는 일 
    - 특정 메트릭 목표치를 넘어가면 replicas 를 늘리고, 내려가면 줄인다 
- 대상은 보통 Deployment/StatefulSet 이고, 실무에서는 Deployment autoscaling 으로 사용한다 
- HPA 가 메트릭을 가져오는 구조 
    - metrics-server: CPU/메모리 메트릭 수집 
    - HPA Controller: 주기적으로 조회하고 replicas 변경 
    - CPU 기반은 특히 request 를 기준으로 판단한다는 게 중요하다 

예시)

- `targetAverageUtilization: 60%`
- 각 Pod 의 CPU usage / CPU request 비율이 60% 를 넘으면 스케일 아웃 처리 

**커스텀 메트릭 (HPA 고도화)**

- CPU 지표만으로는 부족하며 요청수(QPS), 메시지 큐 Lag 지표, P95 수치, DB 커넥션 풀 활성/대기 수 등으로 판단한다 

</br>

### 0-1. Resource Requests / Limit: 스케줄링과 안정성의 출발점 

- Request: 이만큼 필요하다 (스케줄링 기준)
- Limit: 한계 지점 (해당 지표 이상 사용하지 말라)
- 스케줄러는 request 를 보고 해당 노드에 여유가 있다고 판단 및 배치 가능을 판단한다 
- request 를 너무 낮게 설정 
    - 노드 하나에 Pod 가 몰리는 문제 발생 (스케줄러는 여유 있다고 착각)
    - 런타임에서 CPU 경합/지연 발생 
- limit 을 너무 낮게/엄격히 설정 
    - CPU throttling (성능 저하)
    - 메모리 limit 초과 시 OOM Kill

**Node Affinity / Pod Affinity**

- Node Affinity
    - 이 Pod 는 이런 노드에서 동작해야한다 
- Pod Affinity / Anti-Affinitiy
    - 이 Pod 는 해당 Pod 랑 같이 배치하고 싶다 
    - 이 Pod 는 해당 Pod 와 떨어져서 배치하고 싶다 
- Anti-affinity 로 같은 서비스의 Pod 가 서로 다른 노드/영역에 퍼지게 구성한다 → 한 노드 장애에도 서비스를 유지하도록 구성 
- DB Primary/Replica 를 다른 AZ 에 배치 

**Taint / Toleration**

- Taint: 노드에 출입 금지 표시 처리 
- Toleration: Pod 에 출입 허가증 처리 
