package com.playground.ddd.application.required;

import com.playground.ddd.domain.Email;
import com.playground.ddd.domain.Member;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface MemberRepository extends Repository<Member, Long> {
    Member save(Member member);

    Optional<Member> findByEmail(Email email);
}
