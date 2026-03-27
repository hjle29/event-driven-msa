package com.payment.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("SELECT o FROM OutboxEntity o WHERE o.status IN ('PENDING', 'FAILED') AND o.retryCount < :maxRetries ORDER BY o.createdAt ASC")
    List<OutboxEntity> findRetryableWithLock(@Param("maxRetries") int maxRetries);
}
