package com.hjle.member.repository;

import com.hjle.member.domain.Member;
import com.hjle.member.domain.MemberProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<Member> findByProviderAndProviderId(MemberProvider provider, String providerId);
}
