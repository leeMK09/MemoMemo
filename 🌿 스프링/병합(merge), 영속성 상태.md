## 엔티티의 생명 주기 4단계

- New 비영속
  - 객체는 있지만 DB에 저장되지 않음 
- Managed 영속 
  - 영속성 컨텍스트에 등록됨 
- Detached 준영속
  - 영속성 컨텍스트에서 분리됨
- Remove 
  - 삭제 예약 상태 

</br>

## merge: Detached -> Managed 

User detachedUser = new User()
detachedUser.setId(1L); // 이미 DB 에 존재 
detachedUser.setName("Updated");

em.merge(detachedUser); // select + update 

-> 기존 DB 상태를 select




