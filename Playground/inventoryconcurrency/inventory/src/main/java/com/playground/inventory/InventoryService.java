package com.playground.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final InventoryRepository inventoryRepository;

    private Logger log = LoggerFactory.getLogger(InventoryService.class);

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional
    public void increase(Long inventoryId, Integer quantity) {
        log.info("Start Inventory increase");
        var inventory = inventoryRepository.findById(inventoryId).orElseThrow();
        inventory.increase(quantity);
        log.info("Inventory increase end");
    }

    @Transactional
    public void decrease(Long inventoryId, Integer quantity) {
        log.info("Start Inventory decrease");
        var inventory = inventoryRepository.findByIdForUpdate(inventoryId).orElseThrow();
        inventory.decrease(quantity);
        log.info("Inventory decrease end");
    }
}
