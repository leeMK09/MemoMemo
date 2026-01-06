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
