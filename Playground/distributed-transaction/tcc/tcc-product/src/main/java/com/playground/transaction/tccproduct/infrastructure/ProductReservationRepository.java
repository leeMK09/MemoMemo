package com.playground.transaction.tccproduct.infrastructure;

import com.playground.transaction.tccproduct.domain.ProductReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductReservationRepository extends JpaRepository<ProductReservation, Long> {
    List<ProductReservation> findAllByRequestId(String requestId);
}
