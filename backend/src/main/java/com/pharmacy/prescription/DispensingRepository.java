package com.pharmacy.prescription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DispensingRepository extends JpaRepository<Dispensing, Long>, JpaSpecificationExecutor<Dispensing> {
}
