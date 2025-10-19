package com.playground.transaction.sgorder.infrastructure;

import com.playground.transaction.sgorder.domain.CompensationRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompensationRegistryRepository extends JpaRepository<CompensationRegistry, Long> {
}
