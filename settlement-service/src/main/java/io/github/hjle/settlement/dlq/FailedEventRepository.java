package io.github.hjle.settlement.dlq;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEventEntity, Long> {
}
