package com.playground.ddd.domain;

public record MemberRegisterRequest(String email, String nickname, String password) {
}
