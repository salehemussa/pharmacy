package com.pharmacy.prescription;

import com.pharmacy.common.PrescriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long>, JpaSpecificationExecutor<Prescription> {

    long countByStatusIn(Collection<PrescriptionStatus> statuses);

    long countByCustomerId(Long customerId);
}
