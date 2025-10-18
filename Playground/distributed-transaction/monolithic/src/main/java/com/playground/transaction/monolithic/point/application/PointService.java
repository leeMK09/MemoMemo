package com.playground.transaction.monolithic.point.application;

import com.playground.transaction.monolithic.point.domain.Point;
import com.playground.transaction.monolithic.point.infrastructure.PointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointService {

    private final PointRepository pointRepository;

    public PointService(PointRepository pointRepository) {
        this.pointRepository = pointRepository;
    }

    @Transactional
    public void use(Long userId, Long amount) {
        Point point = pointRepository.findByUserId(userId);

        if (point == null) {
            throw new RuntimeException("포인트가 존재하지 않습니다.");
        }

        point.use(amount);
        pointRepository.save(point);
    }
}
