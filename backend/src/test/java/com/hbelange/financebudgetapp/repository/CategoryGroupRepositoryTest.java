package com.hbelange.financebudgetapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.hbelange.financebudgetapp.entity.CategoryGroup;

@DataJpaTest
class CategoryGroupRepositoryTest {

    @Autowired
    private CategoryGroupRepository categoryGroupRepository;

    private static final String USER_A = "auth0|user-a";
    private static final String USER_B = "auth0|user-b";

    @BeforeEach
    void setUp() {
        saveGroup("Housing", USER_A, 0);
        saveGroup("Food", USER_A, 1);
        saveGroup("Transport", USER_B, 0);
    }

    // --- findAllByUserSubOrderBySortOrderAsc ---

    @Test
    void findAllByUserSubOrderBySortOrderAsc_returnsOnlyGroupsForThatUser() {
        List<CategoryGroup> result = categoryGroupRepository.findAllByUserSubOrderBySortOrderAsc(USER_A);
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(g -> g.getUserSub().equals(USER_A));
    }

    @Test
    void findAllByUserSubOrderBySortOrderAsc_isSortedBySortOrder() {
        List<CategoryGroup> result = categoryGroupRepository.findAllByUserSubOrderBySortOrderAsc(USER_A);
        assertThat(result.get(0).getSortOrder()).isEqualTo(0);
        assertThat(result.get(1).getSortOrder()).isEqualTo(1);
    }

    @Test
    void findAllByUserSubOrderBySortOrderAsc_returnsEmpty_forUnknownUser() {
        List<CategoryGroup> result = categoryGroupRepository.findAllByUserSubOrderBySortOrderAsc("auth0|unknown");
        assertThat(result).isEmpty();
    }

    // --- findTopByUserSubOrderBySortOrderDesc ---

    @Test
    void findTopByUserSubOrderBySortOrderDesc_returnsGroupWithHighestSortOrder() {
        Optional<CategoryGroup> result = categoryGroupRepository.findTopByUserSubOrderBySortOrderDesc(USER_A);
        assertThat(result).isPresent();
        assertThat(result.get().getSortOrder()).isEqualTo(1);
        assertThat(result.get().getName()).isEqualTo("Food");
    }

    @Test
    void findTopByUserSubOrderBySortOrderDesc_returnsEmpty_whenNoGroupsForUser() {
        Optional<CategoryGroup> result = categoryGroupRepository.findTopByUserSubOrderBySortOrderDesc("auth0|unknown");
        assertThat(result).isEmpty();
    }

    @Test
    void findTopByUserSubOrderBySortOrderDesc_doesNotReturnOtherUsersGroups() {
        Optional<CategoryGroup> result = categoryGroupRepository.findTopByUserSubOrderBySortOrderDesc(USER_A);
        assertThat(result).isPresent();
        assertThat(result.get().getUserSub()).isEqualTo(USER_A);
    }

    private CategoryGroup saveGroup(String name, String userSub, int sortOrder) {
        CategoryGroup g = new CategoryGroup();
        g.setName(name);
        g.setUserSub(userSub);
        g.setSortOrder(sortOrder);
        return categoryGroupRepository.save(g);
    }
}
