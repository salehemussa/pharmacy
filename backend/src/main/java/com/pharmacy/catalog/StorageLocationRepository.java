package com.pharmacy.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {

    Optional<StorageLocation> findByCodeIgnoreCase(String code);

    List<StorageLocation> findAllByOrderByCodeAsc();

    boolean existsByParent_Id(Long parentId);

    boolean existsByParent_IdAndActiveTrue(Long parentId);
}
