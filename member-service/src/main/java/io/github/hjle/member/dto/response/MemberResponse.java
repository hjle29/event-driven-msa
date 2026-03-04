package io.github.hjle.member.dto.response;

import io.github.hjle.member.dto.MemberEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberResponse {
    private Long id;
    private String userId;
    private String email;
    private String name;

    public static MemberResponse from(MemberEntity entity) {
        return MemberResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .email(entity.getEmail())
                .name(entity.getName())
                .build();
    }
}