package com.pharmacy.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>, JpaSpecificationExecutor<UserAccount> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserAccount> findByUsername(String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserAccount> findWithRolesById(Long id);

    boolean existsByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Override
    @EntityGraph(attributePaths = {"roles"})
    Page<UserAccount> findAll(Specification<UserAccount> spec, Pageable pageable);

    @Query("select count(distinct u) from UserAccount u join u.roles r where r.name = 'ADMINISTRATOR' and u.active = true and u.id <> :userId")
    long countOtherActiveAdministrators(@Param("userId") Long userId);
}
