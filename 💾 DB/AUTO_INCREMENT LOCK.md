## AUTO_INCREMENT LOCK

- 여러 트랜잭션이 동시에 `INSERT` 할 때 `AUTO_INCREMENT` 값을 중복 없이 일관되게 부여하기 위해 잡는 "전역 수준의 잠금"
- InnoDB 는 내부적으로 `auto-inc` 잠금 (`auto-inc lock`) 을 사용 
    - 현재 테이블의 `AUTO_INCREMENT` 카운터를 읽고 
    - 필요한 개수만큼 값을 할당 
    - 다음 시작 값을 갱신하는 과정 전체를 원자적으로 처리 
- 잘 못 사용할 경우 **동시성 병목의 원인**이 된다 

</br>

## InnoDB 의 AUTO_INCREMENT 동작 구조 

- 테이블의 메타데이터(데이터 딕셔너리)에 캐시되어 있음 
- 서버 재기동 시에 디스크에 저장된 정보(최대 값 스캔 등)을 기반으로 다시 계산되기도 함 
- 동시에 여러 트랜잭션이 이 값을 읽고/증가시키기 때문에, race condition 을 막기 위한 잠금이 필요하다 

</br>

## AUTO_INCREMENT 잠금의 특징 

- 트랜잭션 격리 수준과 관계가 거의 없음 
    - 해당 잠금은 일반적인 `Record Lock` / `Gap Lock` / `Next-Key Lock` 이 아닌 **테이블 단위의 특별한 잠금(`auto-inc lock`)**
    - 그래서 `READ COMMITED`, `REPEATABLE READ` 같은 트랜잭션 격리 수준과 별개로 동작 
    - 목적은 정합성(중복 없는 ID) 보장을 위한 것 
- 필요한 수만큼 미리 ID 블록을 예약하는 방식 
    - InnoDB 는 한 번 잠금을 잡은 상태에서 
    - 필요한 행 수를 보고 연속된 ID를 통째로 예약해둠 
    - 이 때문에 **중간에 실패하거나 롤백되더라도 그 ID 들은 건너뛰고 구멍이 날 수 있음**

</br>

## 어떤 경우 병목 상황이 발생하는가?

- `INSERT` 문 자체가 느리면(대량 INSERT, 외래키 검증 등)
- 트랜잭션이 오래 걸려서 **auto-inc lock 을 잡은 채로 롤백/커밋을 기다리는 경우**
- 그 동안 같은 테이블에 `INSERT` 들어오는 다른 세션들은 해당 잠금이 풀리기를 기다려야 함 → 병목 
- 각 트랜잭션이 INSERT 를 시도할 때마다 AUTO_INCREMENT 잠금을 잡아야 다음 ID 를 가져갈 수 있음 

</br>

## ID Gap 

- 여러 행을 한 번에 INSERT 하거나, INSERT 중에 에러/롤백/서버 재시작 등으로 인해 예약된 ID 블록 일부 혹은 전체가 실제로 커밋되지 않음 
- ID가 튀는 구간 "비어있는 숫자"들이 생기게 됨 
- AUTO_INCREMENT 만 믿고는 연속성을 보장할 수 없음 
- Replication 시, 서버 재시작하는 과정에서 `AUTO_INCREMENT` 값이 약간 점프하거나 예상보다 크게 튀는 현상이 있을 수 있음 

</br>
</br>

## 어떻게 해결해야하는걸까?

### 모드 변경 

- 엄격한 `auto-inc lock` 방식 과 새로운 방식(`innodb_autoinc_lock_mode`)를 설정할 수 있음 
    - 주요 값은 0/1/2 이다
    - 0: 전통적인 모드 
        - 항상 강한 `auto-inc lock` 사용 
    - 1: 연속 모드 
        - 대부분의 단순 INSERT 는 경량 잠금으로 처리, 다만 bulk insert 같은 경우 여전히 강한 auto-inc lock 사용 
    - 2: 교대 모드
        - 여러 세션의 INSERT 가 ID 를 섞어서 가져갈 수 있음 
        - 그 대신 `INSERT ... SELECT` 의 결과 ID 가 연속이 아닐 수 있음 

### 정책 변경 

- 별도의 ID 생성기 도입 
    - Snowflake 같은 알고리즘을 통해 중복 없는 ID 생성 (Time + WorkerId + Sequence)
- UUID 사용 
- 샤딩 + 각 샤드별 AUTO_INCREMENT
    - 테이블을 물리적으로 분할하고, 각 샤드가 독립적인 AUTO_INCREMENT 사용 
- INSERT 패턴 성능 최적화 
    - 불필요하게 긴 트랜잭션 줄이기 / 대량 INSERT 와 일반 INSERT 분리 / 불필요한 secondary index 줄이기 
- "ID 는 연속적이어야 한다" 는 요구사항의 대응 
    - 별도의 시퀀스 테이블/발급 로직을 두고 애플리케이션 레벨에서 트랜잭션 단위로 관리하는 쪽이 좋다 
