package com.pharmacy.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface MedicineRepository extends JpaRepository<Medicine, Long>, JpaSpecificationExecutor<Medicine> {

    Optional<Medicine> findByBarcode(String barcode);

    long countByLocation_Id(Long locationId);

    long countByCategory_Id(Long categoryId);

    @Override
    @EntityGraph(attributePaths = {"category", "location"})
    Page<Medicine> findAll(Specification<Medicine> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "location"})
    Optional<Medicine> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"category", "location"})
    java.util.List<Medicine> findAllByOrderByNameAsc();
}
