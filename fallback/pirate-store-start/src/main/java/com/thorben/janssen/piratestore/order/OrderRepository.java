package com.thorben.janssen.piratestore.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findByOwnerKeyAndOrderNumber(UUID ownerKey, String orderNumber);

    Optional<CustomerOrder> findByOrderNumber(String orderNumber);

    long countByOrderNumberStartingWith(String prefix);
}
