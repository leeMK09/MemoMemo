package com.playground.transaction.tccpoint.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "point_reservations")
public class PointReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    private String requestId;

    private Long pointId;

    private Long reservedAmount;

    @Enumerated(EnumType.STRING)
    private PointReservationStatus status;

    public PointReservation() {
    }

    public PointReservation(String requestId, Long pointId, Long reservedAmount) {
        this.requestId = requestId;
        this.pointId = pointId;
        this.reservedAmount = reservedAmount;
        this.status = PointReservationStatus.RESERVED;
    }

    public Long getPointId() {
        return pointId;
    }

    public PointReservationStatus getStatus() {
        return status;
    }

    public Long getReservedAmount() {
        return reservedAmount;
    }

    public void confirm() {
        if (this.status == PointReservationStatus.CANCELLED) {
            throw new RuntimeException("취소된 예약은 확정할 수 없습니다.");
        }

        this.status = PointReservationStatus.CONFIRMED;
    }

    public void cancel() {
        if (this.status == PointReservationStatus.CONFIRMED) {
            throw new RuntimeException("확정된 예약은 취소할 수 없습니다.");
        }

        this.status = PointReservationStatus.CANCELLED;
    }
}
