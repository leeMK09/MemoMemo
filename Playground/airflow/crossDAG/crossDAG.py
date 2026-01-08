from airflow.sensors.external_task import ExternalTaskSensor
from airflow.operators.trigger_dagrun import TriggerDagRunOperator
from airflow.operators.python import PythonOperator
from airflow import Dataset

# --ExternalTaskSensor--
# 다른 DAG의 완료를 기다리는 센서 
wait_for_data_processing = ExternalTaskSensor(
    task_id='wait_for_data_processing',
    external_dag_id='data_processing_dag' # 기다릴 DAG 이름 
    external_task_id='final_processing', # 기다릴 작업 이름 
    timeout=600, # 10분 대기
    poke_interval=60 # 1분마다 확인
)

# 센서가 완료되면 실행할 작업 
start_analysis = PythonOperator(
    task_id='start_analysis',
    python_callable=working_function # 실행할 함수
)

# 의존성 설정
wait_for_data_processing >> start_analysis


# --TriggerDanRunOperator--
process_data = PythonOperator(
    task_id='process_data',
    python_callable=working_function # 실행할 함수
)

trigger_analysis = TriggerDagRunOperator(
    task_id='trigger_analysis',
    trigger_dag_id='customer_analysis_dag', # 실행할 DAG
    conf={'source': 'daily_batch'}, # 전달할 설정
    wait_for_completion=True # 완료까지 대기 → 해당 설정에 따라 비동기/동기 전환이 가능함
)

# 순서 설정
process_data >> trigger_analysis


# --Dataset Dependencies--
# 데이터 셋 정의 
customer_data = Dataset("s3://bucket/customer-data/")
sales_data = Dataset("s3://bucket/sales-data/")

# 데이터를 생성하는 DAG
producer_dag = DAG('data_producer', schedule_interval'0 1 * * *')

update_customer_data = PythonOperator(
    task_id='update_customer_data',
    python_callable=update_customers,
    outlets=[customer_data], # 해당 데이터셋을 업데이트한다
    dag=producer_dag
)

# 데이터를 소비하는 DAG
consumer_dag = DAG(
    'data_consumer',
    schedule=[customer_data], # customer_data 업데이트 시 자동으로 실행 
    catchup=False
)

analyze_customers = PythonOperator(
    task_id='analyze_customers',
    python_callable=run_customer_analysis,
    dag=consumer_dag
)