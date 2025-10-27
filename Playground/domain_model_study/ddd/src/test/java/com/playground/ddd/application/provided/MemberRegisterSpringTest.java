package com.playground.ddd.application.provided;

import com.playground.ddd.MemberTestConfiguration;
import com.playground.ddd.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(MemberTestConfiguration.class)
public class MemberRegisterSpringTest {
    @Autowired
    private MemberRegister memberRegister;

    @Test
    void register() {
        Member member = memberRegister.register(MemberFixture.createMemberRegisterRequest("test@test.com"));

        assertThat(member.getId()).isNotNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.PENDING);
    }
}
