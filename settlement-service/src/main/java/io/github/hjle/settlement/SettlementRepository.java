package io.github.hjle.settlement;

import io.github.hjle.settlement.domain.SettlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementRepository extends JpaRepository<SettlementEntity, Long> {

    Optional<SettlementEntity> findByOrderId(Long orderId);

    List<SettlementEntity> findByUserId(String userId);

    boolean existsByOrderId(Long orderId);
}
