package com.hjle.member.dto;

import com.hjle.member.domain.Member;
import com.hjle.member.domain.MemberProvider;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberResponse {
    private Long id;
    private String email;
    private String nickname;
    private MemberProvider provider;

    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .provider(member.getProvider())
                .build();
    }
}
