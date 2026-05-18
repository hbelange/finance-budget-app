package com.hbelange.financebudgetapp.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import com.hbelange.financebudgetapp.entity.CategoryGroup;

@Repository
public interface CategoryGroupRepository extends JpaRepository<CategoryGroup, UUID> {
    List<CategoryGroup> findAllByUserSubOrderBySortOrderAsc(String userSub);
    Optional<CategoryGroup> findTopByUserSubOrderBySortOrderDesc(String userSub);
}
