package com.pharmacy.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);

    @EntityGraph(attributePaths = {"permissions"})
    List<Role> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"permissions"})
    Optional<Role> findWithPermissionsById(Long id);
}
