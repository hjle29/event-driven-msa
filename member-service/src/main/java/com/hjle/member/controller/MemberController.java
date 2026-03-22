package com.hjle.member.controller;

import com.hjle.common.dto.response.ApiResponse;
import com.hjle.common.exception.BusinessException;
import com.hjle.common.exception.ErrorCode;
import com.hjle.common.security.JwtUtil;
import com.hjle.member.dto.*;
import com.hjle.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final JwtUtil jwtUtil;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(memberService.signup(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(memberService.login(request)));
    }

    @PostMapping("/social/apple")
    public ResponseEntity<ApiResponse<TokenResponse>> appleLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(memberService.socialLoginApple(request)));
    }

    @PostMapping("/social/google")
    public ResponseEntity<ApiResponse<TokenResponse>> googleLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(memberService.socialLoginGoogle(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(memberService.refreshToken(request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponse>> getMyProfile(
            @RequestHeader("Authorization") String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        return ResponseEntity.ok(ApiResponse.success(memberService.getMyProfile(token)));
    }

    private String extractBearerToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return header.substring(7);
    }
}
