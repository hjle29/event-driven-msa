package io.github.hjle.order.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberResponse {
    private Long id;
    private String userId;
    private String name;
    private String email;
}