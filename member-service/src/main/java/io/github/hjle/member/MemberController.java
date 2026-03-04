package io.github.hjle.member;

import com.hjle.common.dto.response.ApiResponse;
import io.github.hjle.member.dto.request.SignUpRequest;
import io.github.hjle.member.dto.response.MemberResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/member")
public class MemberController {
    private final MemberService memberService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemberResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        return ApiResponse.success(MemberResponse.from(memberService.signUp(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<MemberResponse> getMember(@PathVariable String id) {
        return ApiResponse.success(MemberResponse.from(memberService.getMember(id)));
    }
}
