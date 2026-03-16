package com.hjle.member.service;

import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import com.hjle.common.security.JwtUtil;
import com.hjle.member.domain.Member;
import com.hjle.member.domain.MemberProvider;
import com.hjle.member.dto.*;
import com.hjle.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("signup: 정상 회원가입 시 토큰 반환")
    void signup_success() {
        Member saved = Member.builder().id(1L).email("test@email.com").nickname("nickname").build();

        given(memberRepository.existsByEmail("test@email.com")).willReturn(false);
        given(passwordEncoder.encode(any())).willReturn("encoded");
        given(memberRepository.save(any())).willReturn(saved);
        given(jwtUtil.generateAccessToken(1L, "test@email.com")).willReturn("access");
        given(jwtUtil.generateRefreshToken(1L, "test@email.com")).willReturn("refresh");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        SignupRequest request = new SignupRequest();
        setField(request, "email", "test@email.com");
        setField(request, "password", "password");
        setField(request, "nickname", "nickname");

        TokenResponse result = memberService.signup(request);

        assertThat(result.getAccessToken()).isEqualTo("access");
        assertThat(result.getRefreshToken()).isEqualTo("refresh");
    }

    @Test
    @DisplayName("signup: 중복 이메일이면 EMAIL_DUPLICATION 예외")
    void signup_duplicateEmail() {
        given(memberRepository.existsByEmail("dup@email.com")).willReturn(true);

        SignupRequest request = new SignupRequest();
        setField(request, "email", "dup@email.com");
        setField(request, "password", "password");
        setField(request, "nickname", "nick");

        assertThatThrownBy(() -> memberService.signup(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_DUPLICATION));
    }

    @Test
    @DisplayName("login: 정상 로그인 시 토큰 반환")
    void login_success() {
        Member member = Member.builder().id(1L).email("test@email.com").password("encoded").build();

        given(memberRepository.findByEmail("test@email.com")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("password", "encoded")).willReturn(true);
        given(jwtUtil.generateAccessToken(1L, "test@email.com")).willReturn("access");
        given(jwtUtil.generateRefreshToken(1L, "test@email.com")).willReturn("refresh");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        LoginRequest request = new LoginRequest();
        setField(request, "email", "test@email.com");
        setField(request, "password", "password");

        TokenResponse result = memberService.login(request);

        assertThat(result.getAccessToken()).isEqualTo("access");
    }

    @Test
    @DisplayName("login: 존재하지 않는 회원이면 MEMBER_NOT_FOUND 예외")
    void login_memberNotFound() {
        given(memberRepository.findByEmail("none@email.com")).willReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        setField(request, "email", "none@email.com");
        setField(request, "password", "password");

        assertThatThrownBy(() -> memberService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    @DisplayName("login: 비밀번호 불일치 시 INVALID_PASSWORD 예외")
    void login_wrongPassword() {
        Member member = Member.builder().id(1L).email("test@email.com").password("encoded").build();

        given(memberRepository.findByEmail("test@email.com")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        LoginRequest request = new LoginRequest();
        setField(request, "email", "test@email.com");
        setField(request, "password", "wrong");

        assertThatThrownBy(() -> memberService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_PASSWORD));
    }

    @Test
    @DisplayName("refreshToken: 유효하지 않은 토큰이면 INVALID_TOKEN 예외")
    void refreshToken_invalidToken() {
        given(jwtUtil.isValid("bad-token")).willReturn(false);

        RefreshTokenRequest request = new RefreshTokenRequest();
        setField(request, "refreshToken", "bad-token");

        assertThatThrownBy(() -> memberService.refreshToken(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    private void setField(Object obj, String fieldName, Object value) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
