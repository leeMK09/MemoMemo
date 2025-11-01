package com.playground.inventory;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    public record Request(
            Long inventoryId,
            Integer quantity
    ) {}

    @PostMapping("/increase")
    public void increase(@RequestBody Request request) {
        inventoryService.increase(request.inventoryId, request.quantity);
    }

    @PostMapping("/decrease")
    public void decrease(@RequestBody Request request) {
        inventoryService.decrease(request.inventoryId, request.quantity);
    }
}
