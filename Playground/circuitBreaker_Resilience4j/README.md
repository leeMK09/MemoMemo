## 서킷 브레이커 Resilience4j 테스트

**의존성**

- `implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'`
- `implementation 'org.springframework.boot:spring-boot-starter-aop'`

</br>

## 슬라이딩 윈도우 알고리즘

- 호출 수 누적
- `COUNT_BASED` 윈도우 / windowSize : 5
- 최소 5건 누적 후 통계 집계
- `open` 조건
  1. 실패율 >= 50%
  2. 느린 호출 비율 >= 100%
     - 느린 호출 == "60초를 초과하는 호출"
     - slowCallRate 가 "100" 이므로 최근 5건 전부가 60초 초과일 경우 느린 호출로 간주
- `open` 유지시간 : 20초
- `half-open` 에서 허용되는 탐색 호출 건 : 3건

</br>
</br>

## Closed → Open 시나리오

1. 실패율에 따른 `Open`
   - 윈도우 사이즈가 5개로 설정되어 있으므로 최소 3/5 실패 (60%) 이어야함 → >= 50%
   - 최근 호출이 4건 이하일 때는 통계 정보를 수집하지 않으므로 그대로 `Closed`
2. 느린 호출(60초 초과)에 따른 `Open`
   - 최근 5건이 모두 60초를 초과한 호출

</br>
</br>

## Open → Half-Open (탐색전환) 시나리오

- `Open` 이후 20초가 지난 뒤 다음 들어오는 요청에서 `Half-Open` 전환
  - 최대 3건만 통과시킨후 상태를 시험

</br>
</br>

## Half-Open → Closed (정상 복귀) 시나리오

- 모든 조건을 만족하면 Closed
  - 실패율 < 50% (3건 중 0 or 1만 실패)
  - 느린 호출 비율 < 100% (3건 전부가 60초 초과면 안됨)

</br>
</br>

## Half-Open → Open (재오픈) 시나리오

- 아래 중 하나라도 만족하면 다시 `Open`
  - 실패율 >= 50% (3건 중 2건 실패)
  - 느린 호출 비율 >= 100% (3건 전부 60초 초과)
