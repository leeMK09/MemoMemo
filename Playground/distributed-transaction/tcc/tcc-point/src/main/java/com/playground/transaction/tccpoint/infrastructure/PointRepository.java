package com.playground.transaction.tccpoint.infrastructure;

import com.playground.transaction.tccpoint.domain.Point;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointRepository extends JpaRepository<Point, Long> {
    Point findByUserId(Long userId);
}
