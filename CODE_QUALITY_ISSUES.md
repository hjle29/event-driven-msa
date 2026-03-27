# Code Quality Issues - Pending

> Last updated: 2026-03-25

Based on code review from 2026-03-10 (`feature/settlement-rest-api` branch).

---

## Important (권장 수정)

### I-1. SettlementEntity가 dto 패키지에 위치 — 레이어 컨벤션 위반

- **파일**: `settlement-service/src/main/java/io/github/hjle/settlement/dto/SettlementEntity.java`
- **문제**: JPA `@Entity`는 `domain` 패키지에 위치해야 함
- **컨벤션**: `controller / service / repository / domain / dto`
- **수정**: `dto` → `domain` 패키지로 이동

### I-2. completeSettlement 엔드포인트에 접근 제어 없음

- **파일**: `settlement-service/src/main/java/io/github/hjle/settlement/SettlementController.java:27`
- **문제**: 내부 정산 전용 API임에도 인증/인가 없이 누구나 호출 가능
- **수정**: 내부 서비스 시크릿 헤더 검증 또는 Spring Security 역할 제한 적용

### I-3. SETTLEMENT_ALREADY_COMPLETED 에러명이 FAILED 상태에도 사용됨

- **파일**: `settlement-service/src/main/java/io/github/hjle/settlement/SettlementService.java:75`
- **문제**: `status != READY` 조건에서 `FAILED` 상태도 "already completed" 에러를 던짐 — 의미 불일치
- **수정**: `SETTLEMENT_NOT_IN_READY_STATE` 에러코드 분리 또는 상태별 가드 분리

### I-4. SettlementResponse.status 필드가 String 타입

- **파일**: `settlement-service/src/main/java/io/github/hjle/settlement/dto/SettlementResponse.java:18`
- **문제**: 엔티티는 `SettlementStatus` enum을 사용하는데 DTO에서 `String`으로 변환 — 타입 안전성 손실
- **수정**: `SettlementStatus` enum 타입 사용

### I-5. Kafka 의존성 제거 여부 확인 필요

- **파일**: `settlement-service/build.gradle.kts`
- **문제**: PR #5에서 추가한 Kafka 컨슈머가 현재 브랜치에서 삭제됨. 의도적 변경인지 확인 필요

---

## Suggestions (선택적 개선)

### S-1. SettlementStatus.PENDING이 데드코드

- **파일**: `settlement-service/src/main/java/io/github/hjle/settlement/domain/SettlementStatus.java`
- **문제**: 어느 코드도 `PENDING`을 할당하지 않음
- **수정**: 비즈니스 의미가 없다면 제거 권장

### S-2. @PrePersist null 체크 불필요

- **파일**: `settlement-service/src/main/java/io/github/hjle/settlement/domain/SettlementEntity.java`
- **문제**: `runSettlement()`에서 빌더로 항상 `READY`를 명시 설정하므로 null 체크는 실행되지 않음
- **수정**: `@Builder.Default` 사용으로 대체 권장

### S-3. 와일드카드 import 제거

- **파일**: 여러 파일
- **문제**: `import org.springframework.web.bind.annotation.*;` 형태의 와일드카드 import
- **수정**: 명시적 import 사용

### S-4. SETTLEMENT_NOT_FOUND 도메인 전용 에러코드 추가

- **파일**: `common/src/main/java/com/hjle/common/exception/ErrorCode.java`
- **문제**: 현재 `ENTITY_NOT_FOUND(C003)` 사용 중
- **수정**: 도메인 특화 코드 `S002` 추가 권장

---

## 수정 현황

| 이슈 | 우선순위 | 상태 |
|------|----------|------|
| I-1. Entity 패키지 위치 | Important | 🔲 미수정 |
| I-2. 접근 제어 부재 | Important | 🔲 미수정 |
| I-3. 에러코드 의미 불일치 | Important | 🔲 미수정 |
| I-4. DTO 타입 안전성 | Important | 🔲 미수정 |
| I-5. Kafka 의존성 확인 | Important | 🔲 미수정 |
| S-1. PENDING 데드코드 | Suggestion | 🔲 미수정 |
| S-2. @PrePersist 불필요 | Suggestion | 🔲 미수정 |
| S-3. 와일드카드 import | Suggestion | 🔲 미수정 |
| S-4. 도메인 에러코드 | Suggestion | 🔲 미수정 |

---

## 참고

- 원본 리뷰: `ISSUE_20260310.md`
- 대상 브랜치: `feature/settlement-rest-api`
- 수정 완료된 이슈: C-1 (GlobalExceptionHandler HTTP 상태), C-2 (단위 테스트)