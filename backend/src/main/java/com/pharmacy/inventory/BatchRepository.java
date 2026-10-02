package com.pharmacy.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long>, JpaSpecificationExecutor<Batch> {

    boolean existsByMedicine_IdAndBatchNumberIgnoreCase(Long medicineId, String batchNumber);

    Optional<Batch> findByPurchaseItem_Id(Long purchaseItemId);

    long countByLocation_IdAndQuantityOnHandGreaterThan(Long locationId, int quantity);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select batch from Batch batch where batch.id = :id")
    Optional<Batch> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select batch from Batch batch
            where batch.medicine.id = :medicineId
              and batch.quantityOnHand > 0
              and batch.expiryDate >= :today
            order by batch.expiryDate asc, batch.id asc
            """)
    List<Batch> lockSellable(@Param("medicineId") Long medicineId, @Param("today") LocalDate today);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select batch from Batch batch
            where batch.medicine.id = :medicineId
              and batch.quantityOnHand > 0
            order by batch.expiryDate asc, batch.id asc
            """)
    List<Batch> lockAllWithStock(@Param("medicineId") Long medicineId);

    @Query("""
            select sum(batch.quantityOnHand) from Batch batch
            where batch.medicine.id = :medicineId and batch.expiryDate < :today and batch.quantityOnHand > 0
            """)
    Long expiredOnHand(@Param("medicineId") Long medicineId, @Param("today") LocalDate today);

    @Query("""
            select batch.medicine.id, sum(batch.quantityOnHand),
                   sum(case when batch.expiryDate >= :today then batch.quantityOnHand else 0 end),
                   sum(case when batch.expiryDate < :today then batch.quantityOnHand else 0 end),
                   sum(batch.purchasePrice * batch.quantityOnHand),
                   sum(case when batch.expiryDate >= :today then batch.purchasePrice * batch.quantityOnHand else 0 end)
            from Batch batch
            group by batch.medicine.id
            """)
    List<Object[]> aggregateByMedicine(@Param("today") LocalDate today);

    @Query("""
            select distinct batch.medicine.id from Batch batch
            where batch.quantityOnHand > 0 and batch.expiryDate >= :today and batch.expiryDate <= :until
            """)
    List<Long> findNearExpiryMedicineIds(@Param("today") LocalDate today, @Param("until") LocalDate until);

    @Query("""
            select batch from Batch batch join fetch batch.medicine medicine
            where batch.quantityOnHand > 0 and batch.expiryDate < :today
            order by batch.expiryDate asc, medicine.name asc
            """)
    List<Batch> findExpired(@Param("today") LocalDate today);

    @Query("""
            select batch from Batch batch join fetch batch.medicine medicine
            where batch.quantityOnHand > 0 and batch.expiryDate >= :today and batch.expiryDate <= :until
            order by batch.expiryDate asc, medicine.name asc
            """)
    List<Batch> findNearExpiry(@Param("today") LocalDate today, @Param("until") LocalDate until);
}
