package com.pharmacy.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, Long> {

    List<UnitOfMeasure> findAllByOrderByNameAsc();

    Optional<UnitOfMeasure> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
