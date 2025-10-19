package com.playground.transaction.chpoint.application;

import com.playground.transaction.chpoint.application.dto.PointUseCancelCommand;
import com.playground.transaction.chpoint.application.dto.PointUseCommand;
import com.playground.transaction.chpoint.domain.Point;
import com.playground.transaction.chpoint.infrastructure.PointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointService {

    private final PointRepository pointRepository;

    public PointService(PointRepository pointRepository) {
        this.pointRepository = pointRepository;
    }

    @Transactional
    public void use(PointUseCommand command) {
        Point point = pointRepository.findByUserId(command.userId());

        if (point == null) {
            throw new RuntimeException("포인트가 존재하지 않습니다");
        }

        point.use(command.amount());
    }

    @Transactional
    public void cancel(PointUseCancelCommand command) {
        return;
    }
}
