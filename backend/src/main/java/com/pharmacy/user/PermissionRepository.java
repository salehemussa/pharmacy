package com.pharmacy.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findAllByOrderByModuleAscCodeAsc();

    List<Permission> findByCodeIn(Collection<String> codes);
}
