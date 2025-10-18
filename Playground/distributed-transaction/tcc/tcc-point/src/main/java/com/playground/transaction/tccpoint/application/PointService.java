package com.playground.transaction.tccpoint.application;

import com.playground.transaction.tccpoint.application.dto.PointReserveCancelCommand;
import com.playground.transaction.tccpoint.application.dto.PointReserveCommand;
import com.playground.transaction.tccpoint.application.dto.PointReserveConfirmCommand;
import com.playground.transaction.tccpoint.domain.Point;
import com.playground.transaction.tccpoint.domain.PointReservation;
import com.playground.transaction.tccpoint.domain.PointReservationStatus;
import com.playground.transaction.tccpoint.infrastructure.PointRepository;
import com.playground.transaction.tccpoint.infrastructure.PointReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointService {
    private final PointRepository pointRepository;
    private final PointReservationRepository pointReservationRepository;

    public PointService(PointRepository pointRepository, PointReservationRepository pointReservationRepository) {
        this.pointRepository = pointRepository;
        this.pointReservationRepository = pointReservationRepository;
    }

    @Transactional
    public void tryReserve(PointReserveCommand command) {
        PointReservation reservation = pointReservationRepository.findByRequestId(command.requestId());

        if (reservation != null) {
            return;
        }

        Point point = pointRepository.findByUserId(command.userId());
        point.reserve(command.reserveAmount());
        pointReservationRepository.save(
                new PointReservation(
                        command.requestId(),
                        point.getId(),
                        command.reserveAmount()
                )
        );
    }

    @Transactional
    public void confirmReserve(PointReserveConfirmCommand command) {
        PointReservation reservation = pointReservationRepository.findByRequestId(command.requestId());

        if (reservation == null) {
            throw new RuntimeException("예약내역이 존재하지 않습니다");
        }

        if (reservation.getStatus() == PointReservationStatus.CONFIRMED) {
            return;
        }

        Point point = pointRepository.findById(reservation.getPointId()).orElseThrow();
        point.confirm(reservation.getReservedAmount());
        reservation.confirm();

        pointRepository.save(point);
        pointReservationRepository.save(reservation);
    }

    @Transactional
    public void cancelReserve(PointReserveCancelCommand command) {
        PointReservation reservation = pointReservationRepository.findByRequestId(command.requestId());

        if (reservation == null) {
            throw new RuntimeException("예약내역이 존재하지 않습니다");
        }

        if (reservation.getStatus() == PointReservationStatus.CANCELLED) {
            return;
        }

        Point point = pointRepository.findById(reservation.getPointId()).orElseThrow();
        point.cancel(reservation.getReservedAmount());
        reservation.cancel();

        pointRepository.save(point);
        pointReservationRepository.save(reservation);
    }
}
