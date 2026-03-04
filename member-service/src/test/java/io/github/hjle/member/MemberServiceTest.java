package io.github.hjle.member;

import io.github.hjle.member.dto.MemberEntity;
import io.github.hjle.member.dto.request.SignUpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;  // 이거 추가

    @Test
    @DisplayName("회원가입 성공")
    void signUp_success() {
        // given
        SignUpRequest request = createSignUpRequest();
        MemberEntity savedEntity = MemberEntity.builder()
                .id(1L)
                .userId("testUser")
                .email("test@email.com")
                .name("테스트")
                .build();

        given(passwordEncoder.encode(any(String.class))).willReturn("encodedPassword");  // 이것도 추가
        given(memberRepository.save(any(MemberEntity.class))).willReturn(savedEntity);

        // when
        MemberEntity result = memberService.signUp(request);

        // then
        assertThat(result.getUserId()).isEqualTo("testUser");
        assertThat(result.getEmail()).isEqualTo("test@email.com");
        assertThat(result.getName()).isEqualTo("테스트");
        verify(memberRepository).save(any(MemberEntity.class));
        verify(passwordEncoder).encode("password123");  // 이것도 추가하면 좋음
    }

    @Test
    @DisplayName("회원 조회 성공")
    void getMember_success() {
        // given
        String userId = "testUser";
        MemberEntity entity = MemberEntity.builder()
                .id(1L)
                .userId(userId)
                .email("test@email.com")
                .name("테스트")
                .build();

        given(memberRepository.findByUserId(userId)).willReturn(Optional.of(entity));

        // when
        MemberEntity result = memberService.getMember(userId);

        // then
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo("test@email.com");
        verify(memberRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("존재하지 않는 회원 조회 시 예외 발생")
    void getMember_notFound_throwsException() {
        // given
        String userId = "unknownUser";
        given(memberRepository.findByUserId(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> memberService.getMember(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 사용자가 존재하지 않습니다");
    }

    private SignUpRequest createSignUpRequest() {
        return SignUpRequest.builder()
                .email("test@email.com")
                .userId("testUser")
                .password("password123")
                .name("테스트")
                .build();
    }
}
