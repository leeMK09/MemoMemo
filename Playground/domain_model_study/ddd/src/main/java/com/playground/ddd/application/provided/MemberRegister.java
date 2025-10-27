package com.playground.ddd.application.provided;

import com.playground.ddd.domain.Member;
import com.playground.ddd.domain.MemberRegisterRequest;

public interface MemberRegister {
    Member register(MemberRegisterRequest registerRequest);
}
