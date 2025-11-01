package com.playground.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TestRunner {

    private Logger log = LoggerFactory.getLogger(TestRunner.class);

    @Bean
    public CommandLineRunner initInventory(InventoryRepository inventoryRepository) {
        return args -> {
            if (inventoryRepository.count() > 0) {
                log.info("Inventory already initialized, skipping insert.");
                return;
            }

            Inventory inventory = new Inventory(1L, 1000);
            inventoryRepository.save(inventory);

            log.info("Initialized inventory with productId=1, count=1000");
        };
    }
}
