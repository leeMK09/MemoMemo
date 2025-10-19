package com.playground.transaction.sgpoint.infrastructure;

import com.playground.transaction.sgpoint.domain.Point;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointRepository extends JpaRepository<Point, Long> {
    Point findByUserId(Long userId);
}
