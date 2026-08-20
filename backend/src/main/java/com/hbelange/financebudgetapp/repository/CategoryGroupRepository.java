package com.hbelange.financebudgetapp.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import com.hbelange.financebudgetapp.entity.CategoryGroup;

@Repository
public interface CategoryGroupRepository extends JpaRepository<CategoryGroup, UUID> {
    List<CategoryGroup> findAllByUserSubOrderBySortOrderAsc(String userSub);
    Optional<CategoryGroup> findTopByUserSubOrderBySortOrderDesc(String userSub);
    Optional<CategoryGroup> findByUserSubAndName(String userSub, String name);

    // See TransactionRepository.deleteByAccount_UserSub for why this is a hand-written bulk
    // delete rather than a plain derived one.
    @Modifying
    @Query("DELETE FROM CategoryGroup cg WHERE cg.userSub = :userSub")
    void deleteByUserSub(@Param("userSub") String userSub);
}
