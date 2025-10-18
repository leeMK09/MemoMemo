package com.playground.transaction.tccorder.infrastructure.product;

import com.playground.transaction.tccorder.infrastructure.product.dto.ProductReserveApiRequest;
import com.playground.transaction.tccorder.infrastructure.product.dto.ProductReserveApiResponse;
import com.playground.transaction.tccorder.infrastructure.product.dto.ProductReserveCancelApiRequest;
import com.playground.transaction.tccorder.infrastructure.product.dto.ProductReserveConfirmApiRequest;
import org.springframework.web.client.RestClient;

public class ProductApiClient {
    private final RestClient restClient;

    public ProductApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public ProductReserveApiResponse reserveProduct(ProductReserveApiRequest request) {
        return restClient.post()
                .uri("/product/reserve")
                .body(request)
                .retrieve()
                .body(ProductReserveApiResponse.class);
    }

    public void confirmProduct(ProductReserveConfirmApiRequest request) {
        restClient.post()
                .uri("/product/confirm")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void cancelProduct(ProductReserveCancelApiRequest request) {
        restClient.post()
                .uri("/product/cancel")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
