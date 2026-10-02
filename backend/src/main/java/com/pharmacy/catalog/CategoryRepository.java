package com.pharmacy.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByNameIgnoreCase(String name);

    List<Category> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
