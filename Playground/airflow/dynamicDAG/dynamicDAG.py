from airflow import DAG
from airflow.operators.python import PythonOperator
from datetime import datetime

# 코드를 활용한 DAG 생성 

tables = [
    { "name": "userA", "schedule": "0 2 * * *"},
    { "name": "userB", "schedule": "0 5 * * *"},
    { "name": "userC", "schedule": "0 3 * * *"},
]

for table in tables:
    dag = DAG(
        dag_id=f'sync_{table["name"]}',
        schedule_interval=table["schedule"],
        start_date=datetime(2025, 1, 1)
    )

    task = PythonOperator(
        task_id=f'sync_{table["name"]}_task',
        python_callable=working_function, # 실행할 함수
        dag=dag
    )

    globals()[f'{table["name"]}_dag'] = dag


# DB 데이터를 읽어와서 DAG 생성 

def get_configs_from_db():
    # SQL: SELECT name, schedule FROM pipelines WHERE active = true
    return [
        { "name": "customer_sync", "schedule": "0 1 * * *" },
        { "name": "sales_sync", "schedule": "0 2 * * *" }
    ]

configs = get_configs_from_db()

for config in configs:
    dag = DAG(
        dag_id=f'db_{table["name"]}',
        schedule_interval=table["schedule"],
        start_date=datetime(2025, 1, 1)
    )

    task = PythonOperator(
        task_id=f'db_{table["name"]}_task',
        python_callable=working_function, # 실행할 함수
        dag=dag
    )

    globals()[f'{table["name"]}_dag'] = dag