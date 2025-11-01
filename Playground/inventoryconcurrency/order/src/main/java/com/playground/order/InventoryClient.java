package com.playground.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "inventoryClient", url = "${clients.inventory.base-url}")
public interface InventoryClient {
    @PostMapping("/inventory/increase")
    void increase(@RequestBody Request request);

    @PostMapping("/inventory/decrease")
    void decrease(@RequestBody Request request);
}
