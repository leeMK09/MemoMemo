package com.playground.transaction.chpoint.infrastructure;

import com.playground.transaction.chpoint.domain.Point;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointRepository extends JpaRepository<Point, Long> {
    Point findByUserId(Long userId);
}
