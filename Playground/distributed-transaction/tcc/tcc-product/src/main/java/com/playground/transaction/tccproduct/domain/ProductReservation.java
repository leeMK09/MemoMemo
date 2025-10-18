package com.playground.transaction.tccproduct.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "product_reservations")
public class ProductReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String requestId;

    private Long productId;

    private Long reservedQuantity;

    private Long reservedPrice;

    @Enumerated(EnumType.STRING)
    private ProductReservationStatus status;

    public ProductReservation() {
    }

    public ProductReservation(String requestId, Long productId, Long reservedQuantity, Long reservedPrice) {
        this.requestId = requestId;
        this.productId = productId;
        this.reservedQuantity = reservedQuantity;
        this.reservedPrice = reservedPrice;
        status = ProductReservationStatus.RESERVED;
    }

    public Long getReservedPrice() {
        return reservedPrice;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getReservedQuantity() {
        return reservedQuantity;
    }

    public ProductReservationStatus getStatus() {
        return status;
    }

    public void confirm() {
        if (this.status == ProductReservationStatus.CANCELLED) {
            throw new RuntimeException("이미 취소된 예약입니다.");
        }

        this.status = ProductReservationStatus.CONFIRMED;
    }

    public void cancel() {
        if (this.status == ProductReservationStatus.CONFIRMED) {
            throw new RuntimeException("이미 확정된 예약입니다.");
        }

        this.status = ProductReservationStatus.CANCELLED;
    }
}
