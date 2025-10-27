package com.playground.ddd.application.provided;

import com.playground.ddd.application.MemberService;
import com.playground.ddd.application.required.EmailSender;
import com.playground.ddd.application.required.MemberRepository;
import com.playground.ddd.domain.Email;
import com.playground.ddd.domain.Member;
import com.playground.ddd.domain.MemberFixture;
import com.playground.ddd.domain.MemberStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class MemberRegisterTest {
    @Test
    void registerStub() {
        MemberRegister memberRegister = new MemberService(
                new MemberRepositoryStub(),
                new EmailSenderStub(),
                MemberFixture.createPasswordEncoder()
        );

        Member member = memberRegister.register(MemberFixture.createMemberRegisterRequest("test@test.com"));
        assertThat(member.getId()).isNotNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.PENDING);
    }

    @Test
    void registerMock() {
//        EmailSendMock emailSender = new EmailSendMock();
        EmailSendMock emailSender = Mockito.mock(EmailSendMock.class);
        MemberRegister memberRegister = new MemberService(
                new MemberRepositoryStub(),
                emailSender,
                MemberFixture.createPasswordEncoder()
        );

        Member member = memberRegister.register(MemberFixture.createMemberRegisterRequest("test@test.com"));
        assertThat(member.getId()).isNotNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.PENDING);

//        assertThat(emailSender.tos).isEqualTo(1);
        Mockito.verify(emailSender).send(eq(member.getEmail()), any(), any());
    }

    static class MemberRepositoryStub implements MemberRepository {
        @Override
        public Member save(Member member) {
            ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        }

        @Override
        public Optional<Member> findByEmail(Email email) {
            return Optional.empty();
        }
    }

    static class EmailSenderStub implements EmailSender {
        @Override
        public void send(Email email, String subject, String body) {

        }
    }

    static class EmailSendMock implements EmailSender {
        int tos = 0;
        @Override
        public void send(Email email, String subject, String body) {
            tos++;
        }
    }
}
