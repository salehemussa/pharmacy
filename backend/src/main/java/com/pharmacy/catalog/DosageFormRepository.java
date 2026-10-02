package com.pharmacy.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DosageFormRepository extends JpaRepository<DosageForm, Long> {

    List<DosageForm> findAllByOrderByNameAsc();

    Optional<DosageForm> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
