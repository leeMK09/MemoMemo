package com.playground.transaction.sgorder.application;

import com.playground.transaction.sgorder.application.dto.OrderDto;
import com.playground.transaction.sgorder.application.dto.PlaceOrderCommand;
import com.playground.transaction.sgorder.infrastructure.CompensationRegistryRepository;
import com.playground.transaction.sgorder.infrastructure.point.PointApiClient;
import com.playground.transaction.sgorder.infrastructure.point.PointUseApiRequest;
import com.playground.transaction.sgorder.infrastructure.point.PointUseCancelApiRequest;
import com.playground.transaction.sgorder.infrastructure.product.*;
import org.springframework.stereotype.Component;

@Component
public class OrderCoordinator {

    private final OrderService orderService;
    private final ProductApiClient productApiClient;
    private final PointApiClient pointApiClient;
    private final CompensationRegistryRepository compensationRegistryRepository;

    public OrderCoordinator(OrderService orderService, ProductApiClient productApiClient, PointApiClient pointApiClient, CompensationRegistryRepository compensationRegistryRepository) {
        this.orderService = orderService;
        this.productApiClient = productApiClient;
        this.pointApiClient = pointApiClient;
        this.compensationRegistryRepository = compensationRegistryRepository;
    }

    public void placeOrder(PlaceOrderCommand command) {
        orderService.request(command.orderId());
        OrderDto orderDto = orderService.getOrder(command.orderId());

        try {
            ProductBuyApiRequest productByApiRequest = new ProductBuyApiRequest(
                    command.orderId().toString(),
                    orderDto.orderItems().stream()
                            .map(item -> new ProductBuyApiRequest.ProductInfo(item.productId(), item.quantity()))
                            .toList()
            );

            ProductBuyApiResponse buyApiResponse = productApiClient.buy(productByApiRequest);

            PointUseApiRequest pointUseApiRequest = new PointUseApiRequest(
                    command.orderId().toString(),
                    1L,
                    buyApiResponse.totalPrice()
            );

            pointApiClient.use(pointUseApiRequest);

            orderService.complete(command.orderId());

        } catch (Exception e) {
            ProductBuyCancelApiRequest productBuyCancelApiRequest = new ProductBuyCancelApiRequest(command.orderId().toString());

            ProductBuyCancelApiResponse productBuyCancelApiResponse = productApiClient.cancel(productBuyCancelApiRequest);

            if (productBuyCancelApiResponse.totalPrice() > 0) {
                PointUseCancelApiRequest pointUseCancelApiRequest = new PointUseCancelApiRequest(command.orderId().toString());

                pointApiClient.cancel(pointUseCancelApiRequest);
            }

            orderService.fail(command.orderId());
        }
    }
}
