package io.github.hjle.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<OrderBaseEntity, Long> {
    List<OrderBaseEntity> findByUserId(String userId);
}