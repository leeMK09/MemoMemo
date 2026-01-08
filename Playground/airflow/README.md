## Airflow

- 정기적이거나 의존성이 있는 작업 흐름을 코드로 정의하고 안정적으로 실행 및 관리하는 워크플로우 엔진
- 단순 배치, 단순 크론 작업 보다는 의존성이 있는 작업이 있고 재시도 + 모니터링 + 확장성이 있는 작업에 유의미하다

</br>

### DAG (Directed Acyclic Graph)

- 작업 흐름의 설계도를 의미함
- 어떤 작업을 어떤 순서로 언제 실행할지를 정의한다
- 언제 사용하는가?
  - ETL 파이프라인 / 데이터 집계 / 정기 리포트 생성 / 외부 API 동기화 / ML 파이프라인 등
- DAG 자체는 실행하는 작업이 아님
- DAG 파일은 Scheduler 가 주기적으로 읽기만 한다

ex)

```python
with DAG(
    dag_id="example_dag",
    start_date=datetime(2024, 1, 1),
    schedule="@daily",
    catchup=False,
) as dag:
    ...
```

- 위 코드는 실행가능한 코드가 아닌 설명서 같은 역할을 한다

</br>

### Task

- 실제 실행되는 최소 단위
- DAG 안에 들어가는 실행 노드
- 언제 사용하는가?
  - Python 함수 실행 / Shell 명령 실행 / Spark Job 실행 / HTTP API 호출 등
- Task 만 실제 실행되는 작업 단위

ex) PythonOperator

```python
def my_task():
    print("hello airflow")

task1 = PythonOperator(
    task_id="print_hello",
    python_callable=my_task,
)
```

</br>

### Operator

- Task 를 어떻게 실행할지 정의한 클래스
- Task = Operator 의 인스턴스
  - PythonOperator, BashOperator, HttpOperator 등이 있음
  - Task = Operator + 설정값

ex)

```python
task = BashOperator(
    task_id="list_files",
    bash_command="ls -al",
)
```

</br>

### Scheduler

- DAG를 읽고 지금 실행해야 하는건지 판단
- 하는 일
  - DAG 파일 파싱
  - Schedule 확인
  - DagRun 생성
  - 실행 가능한 Task 선별
  - Executor 에게 넘김
- Scheduler 는 Task를 실행하지 않음
- 언제 실행할지만 판단한다
- DAG 파일 → (읽기) → Scheduler → (실행 판단) → DagRun 생성

</br>

### Executor

- Task 를 어디서, 어떻게 실행할지 결정하는 전략
- Executor 종류
  - `SequentialExecutor` : 하나씩 실행 (테스트 용으로 자주 사용)
  - `LocalExecutor` : 로컬 병렬 실행
  - `CeleryExecutor` : 분산 실행
  - `KubernetesExecutor` : Pod 단위 실행

ex) docker-compose

```yaml
---
AIRFLOW__CORE__EXECUTOR: LocalExecutor
```

</br>

### Worker

- Task 를 실제로 실행하는 주체
- Python 함수, Bash 명령 실행을 담당한다
- Worker 는 DAG 구조를 모르고 그냥 Task를 실행한다
- Scheduler → (task 실행해) → Executor → (여기서 실행해라) → Worker → python_callable() 실행

</br>

### DagRun / TaskInstance

- DagRun
  - DAG 1회 실행, 날짜별 실행 단위
- TaskInstance
  - Task 의 특정 DagRun 에서의 실행, 상태를 가진다
  - success / failed / retry

</br>

### 전체 구조

```text
DAG (설계도)
→ Task A
→ Task B
→ Task C

Scheduler
→ 언제 실행할지 판단

Executor
→ 실행 방식 결정

Worker
→ 실제 코드 실행
```

</br>

## Dynamic DAG

- 데이터 소스가 생성될 때마다 수동으로 DAG 를 생성 및 관리하는 건 비효율적임
- 동적으로 DAG 를 생성하며 설정에 따라서 자동으로 생성이 가능한 형태를 의미
  - 설정 파일, DB, 외부 API 를 통해서 동적으로 코드를 통해 DAG 를 생성 → Factory 패턴 같은 개념
- 예시) 서비스에서 각 고객사 별로 동일한 데이터 처리가 필요함 → 자동으로 DAG 를 만들어서 처리
  - 고객사 정보를 기반으로 자동으로 DAG 를 생성
  - Customer B Pipeline / Customer C Pipeline / Customer D Pipeline ...
- 고려해야할 점
  - 값을 파싱하는 부분이 중요 → DAG 에서 복잡한 외부 API 요청등이 있다면 파싱시간이 늘어남
  - 파싱하고자 하는 DAG 값을 가져올 때 캐싱을 통해 외부 API 요청을 최소화하는 방향도 있다
  - `dag_id` 의 명명규칙이 중요, 중복이 되면 안됨

</br>

## Cross DAG

- 실제 실무에서는 하나의 DAG 로 모든 작업을 처리할 순 없다
- 예를들어 고객 데이터 DAG 가 처리된 후 매출 분석 DAG 가 실행되어야 하는 등의 의존성
- Cross DAG : 서로 다른 DAG 간의 의존관계를 의미한다
  - A DAG 가 완료되어야 B DAG 가 실행되는 등의 관계를 의미
- 하나의 DAG 가 거대해지면 자원 측면에서 비효율적이며 이를 각각의 세부화된 DAG 로 나눌 필요가 있다
- Dependency Management 종류
  - `ExternalTaskSensor` : 다른 DAG 의 작업이 완료되는 것을 기다리는 가장 일반적인 형태, 동기 방식을 생각하면 되고 다른 DAG 의 작업의 완료를 기다리면서 폴링하고 완료이후 동작
  - `TriggerDanRunOperator` : 다른 DAG 를 직접 호출하는 방식, 능동적으로 다음 단계를 시작할 수 있는 형태, DAG 를 모듈화할 수 있는 특지을 가지고 있으며 트리거 시점에 인자를 전달할 수 있다, 비동기/동기 모두 지원한다
  - `Dataset Dependencies` : 일반적인 EDA 기반의 아키텍쳐같이 특정 데이터가 업데이트된다면 자동으로 실행, DAG 간의 커플링도 줄어들고 불필요한 자원을 절약, 실무에서 잘 활용하는 형태
