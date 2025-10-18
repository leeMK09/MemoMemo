package com.playground.transaction.tccpoint.infrastructure;

import com.playground.transaction.tccpoint.domain.PointReservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointReservationRepository extends JpaRepository<PointReservation, Long> {
    PointReservation findByRequestId(String requestId);
}
