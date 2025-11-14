package com.playground.inventory;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class TestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);

    // TPS 60 * 60s = 3,600 맞춤
    private static final int HOT_QTY  = 240; // productId 1..5 → 1,200
    private static final int COLD_QTY = 160; // productId 6..20 → 2,400

    private final JdbcTemplate jdbc;
    private final InventoryRepository repo;

    public TestRunner(JdbcTemplate jdbc, InventoryRepository repo) {
        this.jdbc = jdbc;
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        jdbc.execute("TRUNCATE TABLE inventory RESTART IDENTITY");

        List<Inventory> list = new ArrayList<>();
        for (long pid = 1; pid <= 5; pid++) {
            list.add(new Inventory(pid, HOT_QTY));
        }
        for (long pid = 6; pid <= 20; pid++) {
            list.add(new Inventory(pid, COLD_QTY));
        }
        repo.saveAll(list);

        Integer sum = jdbc.queryForObject("SELECT COALESCE(SUM(count),0) FROM inventory", Integer.class);
        log.info("Seeded for TPS=60. HOT={} (x5), COLD={} (x15), SUM(count)={}",
                HOT_QTY, COLD_QTY, sum);
    }
}
