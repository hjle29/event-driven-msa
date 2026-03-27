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

    @Test
    @DisplayName("refreshToken: 유효한 토큰이면 새 accessToken + 기존 refreshToken 반환")
    void refreshToken_success() {
        given(jwtUtil.isValid("valid-refresh")).willReturn(true);
        given(jwtUtil.getMemberId("valid-refresh")).willReturn(1L);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:1")).willReturn("valid-refresh");
        given(jwtUtil.getEmail("valid-refresh")).willReturn("test@email.com");
        given(jwtUtil.generateAccessToken(1L, "test@email.com")).willReturn("new-access");

        RefreshTokenRequest request = new RefreshTokenRequest();
        setField(request, "refreshToken", "valid-refresh");

        TokenResponse result = memberService.refreshToken(request);

        assertThat(result.getAccessToken()).isEqualTo("new-access");
        assertThat(result.getRefreshToken()).isEqualTo("valid-refresh");
    }

    @Test
    @DisplayName("refreshToken: Redis에 저장된 토큰 없으면 INVALID_TOKEN 예외")
    void refreshToken_noStoredToken() {
        given(jwtUtil.isValid("valid-refresh")).willReturn(true);
        given(jwtUtil.getMemberId("valid-refresh")).willReturn(1L);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:1")).willReturn(null);

        RefreshTokenRequest request = new RefreshTokenRequest();
        setField(request, "refreshToken", "valid-refresh");

        assertThatThrownBy(() -> memberService.refreshToken(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    @DisplayName("refreshToken: Redis 저장 토큰과 불일치하면 INVALID_TOKEN 예외")
    void refreshToken_tokenMismatch() {
        given(jwtUtil.isValid("incoming")).willReturn(true);
        given(jwtUtil.getMemberId("incoming")).willReturn(1L);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:1")).willReturn("different");

        RefreshTokenRequest request = new RefreshTokenRequest();
        setField(request, "refreshToken", "incoming");

        assertThatThrownBy(() -> memberService.refreshToken(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_TOKEN));
    }

    @Test
    @DisplayName("getMyProfile: 정상 조회 시 MemberResponse 반환")
    void getMyProfile_success() {
        Member member = Member.builder().id(1L).email("test@email.com").nickname("nick")
                .provider(MemberProvider.EMAIL).build();

        given(jwtUtil.getMemberId("access-token")).willReturn(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        MemberResponse result = memberService.getMyProfile("access-token");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("test@email.com");
        assertThat(result.getNickname()).isEqualTo("nick");
        assertThat(result.getProvider()).isEqualTo(MemberProvider.EMAIL);
    }

    @Test
    @DisplayName("getMyProfile: 존재하지 않는 회원이면 MEMBER_NOT_FOUND 예외")
    void getMyProfile_memberNotFound() {
        given(jwtUtil.getMemberId("access-token")).willReturn(99L);
        given(memberRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMyProfile("access-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    @DisplayName("socialLoginApple: 신규 유저면 회원 저장 후 토큰 반환")
    void socialLoginApple_newUser() {
        Member saved = Member.builder().id(2L).email("apple_stub@placeholder.com").nickname("AppleUser")
                .provider(MemberProvider.APPLE).build();

        given(memberRepository.findByProviderAndProviderId(eq(MemberProvider.APPLE), any())).willReturn(Optional.empty());
        given(memberRepository.save(any())).willReturn(saved);
        given(jwtUtil.generateAccessToken(2L, "apple_stub@placeholder.com")).willReturn("apple-access");
        given(jwtUtil.generateRefreshToken(2L, "apple_stub@placeholder.com")).willReturn("apple-refresh");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        SocialLoginRequest request = new SocialLoginRequest();
        setField(request, "token", "apple-id-token");

        TokenResponse result = memberService.socialLoginApple(request);

        assertThat(result.getAccessToken()).isEqualTo("apple-access");
        then(memberRepository).should().save(any());
    }

    @Test
    @DisplayName("socialLoginApple: 기존 유저면 저장 없이 토큰 반환")
    void socialLoginApple_existingUser() {
        Member existing = Member.builder().id(3L).email("apple_existing@placeholder.com").nickname("AppleUser")
                .provider(MemberProvider.APPLE).build();

        given(memberRepository.findByProviderAndProviderId(eq(MemberProvider.APPLE), any())).willReturn(Optional.of(existing));
        given(jwtUtil.generateAccessToken(3L, "apple_existing@placeholder.com")).willReturn("apple-access");
        given(jwtUtil.generateRefreshToken(3L, "apple_existing@placeholder.com")).willReturn("apple-refresh");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        SocialLoginRequest request = new SocialLoginRequest();
        setField(request, "token", "apple-id-token");

        TokenResponse result = memberService.socialLoginApple(request);

        assertThat(result.getAccessToken()).isEqualTo("apple-access");
        then(memberRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("socialLoginGoogle: 신규 유저면 회원 저장 후 토큰 반환")
    void socialLoginGoogle_newUser() {
        Member saved = Member.builder().id(4L).email("google_stub@placeholder.com").nickname("GoogleUser")
                .provider(MemberProvider.GOOGLE).build();

        given(memberRepository.findByProviderAndProviderId(eq(MemberProvider.GOOGLE), any())).willReturn(Optional.empty());
        given(memberRepository.save(any())).willReturn(saved);
        given(jwtUtil.generateAccessToken(4L, "google_stub@placeholder.com")).willReturn("google-access");
        given(jwtUtil.generateRefreshToken(4L, "google_stub@placeholder.com")).willReturn("google-refresh");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        SocialLoginRequest request = new SocialLoginRequest();
        setField(request, "token", "google-id-token");

        TokenResponse result = memberService.socialLoginGoogle(request);

        assertThat(result.getAccessToken()).isEqualTo("google-access");
        then(memberRepository).should().save(any());
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
