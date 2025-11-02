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

    // 고정 시드 값 (TPS 1200 * 60s = 72,000 맞춤)
    private static final int HOT_QTY  = 5400; // productId 1..5 (각 5400) → 27,000
    private static final int COLD_QTY = 3000; // productId 6..20 (각 3000) → 45,000

    private final JdbcTemplate jdbc;
    private final InventoryRepository repo;

    public TestRunner(JdbcTemplate jdbc, InventoryRepository repo) {
        this.jdbc = jdbc;
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        // 1) 깨끗하게 초기화 + IDENTITY 리셋 (PostgreSQL)
        jdbc.execute("TRUNCATE TABLE inventory RESTART IDENTITY");

        // 2) 시드 (id는 1..20 자동 생성, 조회는 id 기준으로 진행)
        List<Inventory> list = new ArrayList<>();
        // HOT: productId 1..5
        for (long pid = 1; pid <= 5; pid++) {
            list.add(new Inventory(pid, HOT_QTY));
        }
        // COLD: productId 6..20
        for (long pid = 6; pid <= 20; pid++) {
            list.add(new Inventory(pid, COLD_QTY));
        }
        repo.saveAll(list);

        // 3) 확인 로그
        Integer sum = jdbc.queryForObject("SELECT COALESCE(SUM(count),0) FROM inventory", Integer.class);
        log.info("Seeded. HOT={} (x5), COLD={} (x15), SUM(count)={}", HOT_QTY, COLD_QTY, sum);
    }
}
