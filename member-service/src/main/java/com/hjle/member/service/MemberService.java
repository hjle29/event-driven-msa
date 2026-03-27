package com.hjle.member.service;

import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import com.hjle.common.security.JwtUtil;
import com.hjle.member.domain.Member;
import com.hjle.member.domain.MemberProvider;
import com.hjle.member.dto.*;
import com.hjle.member.repository.MemberRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.refresh-token-expiry:604800000}")
    private long refreshTokenExpiry;

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATION);
        }
        Member member = Member.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .provider(MemberProvider.EMAIL)
                .build();
        return issueTokens(memberRepository.save(member));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        return issueTokens(member);
    }

    @Transactional
    @CircuitBreaker(name = "socialLogin")
    public TokenResponse socialLoginApple(SocialLoginRequest request) {
        // TODO: Apple identity token 검증 (Apple public key로 JWT 서명 확인)
        // TODO: 검증 후 token에서 sub(providerId), email 추출
        String providerId = "apple_stub_" + request.getToken().hashCode();
        String email = "apple_" + providerId + "@placeholder.com";
        return processSocialLogin(MemberProvider.APPLE, providerId, email, "AppleUser");
    }

    @Transactional
    @CircuitBreaker(name = "socialLogin")
    public TokenResponse socialLoginGoogle(SocialLoginRequest request) {
        // TODO: Google id_token 검증 (Google tokeninfo endpoint 또는 google-auth-library 활용)
        // TODO: 검증 후 token에서 sub(providerId), email, name 추출
        String providerId = "google_stub_" + request.getToken().hashCode();
        String email = "google_" + providerId + "@placeholder.com";
        return processSocialLogin(MemberProvider.GOOGLE, providerId, email, "GoogleUser");
    }

    public TokenResponse refreshToken(RefreshTokenRequest request) {
        String incomingToken = request.getRefreshToken();
        if (!jwtUtil.isValid(incomingToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        Long memberId = jwtUtil.getMemberId(incomingToken);
        String storedToken = redisTemplate.opsForValue().get("refresh:" + memberId);
        if (storedToken == null || !storedToken.equals(incomingToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        String email = jwtUtil.getEmail(incomingToken);
        return TokenResponse.builder()
                .accessToken(jwtUtil.generateAccessToken(memberId, email))
                .refreshToken(incomingToken)
                .build();
    }

    @Transactional(readOnly = true)
    public MemberResponse getMyProfile(String token) {
        Long memberId = jwtUtil.getMemberId(token);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return MemberResponse.from(member);
    }

    private TokenResponse processSocialLogin(MemberProvider provider, String providerId,
                                              String email, String defaultNickname) {
        Member member = memberRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> memberRepository.save(Member.builder()
                        .email(email)
                        .nickname(defaultNickname)
                        .provider(provider)
                        .providerId(providerId)
                        .build()));
        return issueTokens(member);
    }

    private TokenResponse issueTokens(Member member) {
        String accessToken = jwtUtil.generateAccessToken(member.getId(), member.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(member.getId(), member.getEmail());
        redisTemplate.opsForValue().set(
                "refresh:" + member.getId(), refreshToken, Duration.ofMillis(refreshTokenExpiry));
        return TokenResponse.builder().accessToken(accessToken).refreshToken(refreshToken).build();
    }
}
