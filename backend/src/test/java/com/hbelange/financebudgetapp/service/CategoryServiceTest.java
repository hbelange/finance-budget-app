package com.hbelange.financebudgetapp.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.BudgetCategoryRequest;
import com.hbelange.financebudgetapp.dto.CategoryGroupDTO;
import com.hbelange.financebudgetapp.dto.CategoryGroupRequest;
import com.hbelange.financebudgetapp.dto.SortItem;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;
import com.hbelange.financebudgetapp.repository.AccountRepository;
import com.hbelange.financebudgetapp.repository.BudgetAllocationRepository;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryGroupRepository categoryGroupRepository;
    @Mock private BudgetCategoryRepository budgetCategoryRepository;
    @Mock private BudgetAllocationRepository budgetAllocationRepository;
    @Mock private AccountRepository accountRepository;

    @InjectMocks
    private CategoryService categoryService;

    private static final String USER_SUB = "auth0|test-user";
    private static final String OTHER_SUB = "auth0|other-user";

    private UUID groupId;
    private UUID categoryId;
    private CategoryGroup group;
    private BudgetCategory category;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        group = new CategoryGroup();
        group.setId(groupId);
        group.setName("Housing");
        group.setSortOrder(1);
        group.setUserSub(USER_SUB);

        category = new BudgetCategory();
        category.setId(categoryId);
        category.setGroup(group);
        category.setName("Rent");
        category.setSortOrder(1);
    }

    @Test
    void findAllGroups_returnsMappedDTOs() {
        when(categoryGroupRepository.findAllByUserSubOrderBySortOrderAsc(USER_SUB)).thenReturn(List.of(group));
        when(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group)).thenReturn(List.of());

        List<CategoryGroupDTO> result = categoryService.findAllGroups(USER_SUB);

        assertEquals(1, result.size());
        assertEquals(groupId, result.get(0).id());
        assertEquals("Housing", result.get(0).name());
    }

    @Test
    void createGroup_savesAndReturnsDTO() {
        when(categoryGroupRepository.findTopByUserSubOrderBySortOrderDesc(USER_SUB)).thenReturn(Optional.empty());
        when(categoryGroupRepository.save(any())).thenReturn(group);

        CategoryGroupDTO result = categoryService.createGroup(new CategoryGroupRequest("Housing"), USER_SUB);

        assertEquals("Housing", result.name());
        verify(categoryGroupRepository).save(any());
    }

    @Test
    void createGroup_assignsNextSortOrder_whenGroupsExist() {
        CategoryGroup existing = new CategoryGroup();
        existing.setSortOrder(2);
        when(categoryGroupRepository.findTopByUserSubOrderBySortOrderDesc(USER_SUB)).thenReturn(Optional.of(existing));
        when(categoryGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        categoryService.createGroup(new CategoryGroupRequest("New Group"), USER_SUB);

        verify(categoryGroupRepository).save(argThat(g -> g.getSortOrder() == 3));
    }

    @Test
    void createGroup_assignsSortOrderZero_whenNoGroupsExist() {
        when(categoryGroupRepository.findTopByUserSubOrderBySortOrderDesc(USER_SUB)).thenReturn(Optional.empty());
        when(categoryGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        categoryService.createGroup(new CategoryGroupRequest("First Group"), USER_SUB);

        verify(categoryGroupRepository).save(argThat(g -> g.getSortOrder() == 0));
    }

    @Test
    void addCategory_throwsNotFound_whenGroupMissing() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.addCategory(groupId, new BudgetCategoryRequest("Rent"), USER_SUB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void addCategory_throwsForbidden_whenGroupBelongsToOtherUser() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.addCategory(groupId, new BudgetCategoryRequest("Rent"), OTHER_SUB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void addCategory_savesAndReturnsUpdatedGroup() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(budgetCategoryRepository.findTopByGroupOrderBySortOrderDesc(group)).thenReturn(Optional.empty());
        when(budgetCategoryRepository.save(any())).thenReturn(category);
        when(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group)).thenReturn(List.of(category));

        CategoryGroupDTO result = categoryService.addCategory(groupId, new BudgetCategoryRequest("Rent"), USER_SUB);

        assertEquals(1, result.categories().size());
        assertEquals("Rent", result.categories().get(0).name());
    }

    @Test
    void renameCategory_throwsNotFound_whenCategoryMissing() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.renameCategory(categoryId, new BudgetCategoryRequest("New Name"), USER_SUB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void renameCategory_throwsForbidden_whenCategoryBelongsToOtherUser() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.renameCategory(categoryId, new BudgetCategoryRequest("New Name"), OTHER_SUB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void renameCategory_updatesName() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(budgetCategoryRepository.save(any())).thenReturn(category);

        categoryService.renameCategory(categoryId, new BudgetCategoryRequest("New Name"), USER_SUB);

        assertEquals("New Name", category.getName());
        verify(budgetCategoryRepository).save(category);
    }

    @Test
    void reorderGroups_updatesAllSortOrders() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        CategoryGroup g1 = new CategoryGroup();
        g1.setId(id1);
        g1.setSortOrder(0);
        g1.setUserSub(USER_SUB);
        CategoryGroup g2 = new CategoryGroup();
        g2.setId(id2);
        g2.setSortOrder(1);
        g2.setUserSub(USER_SUB);

        when(categoryGroupRepository.findAllById(any())).thenReturn(List.of(g1, g2));

        categoryService.reorderGroups(List.of(new SortItem(id1, 1), new SortItem(id2, 0)), USER_SUB);

        assertEquals(1, g1.getSortOrder());
        assertEquals(0, g2.getSortOrder());
        verify(categoryGroupRepository).saveAll(List.of(g1, g2));
    }

    @Test
    void reorderGroups_throwsForbidden_whenAnyGroupBelongsToOtherUser() {
        UUID id1 = UUID.randomUUID();
        CategoryGroup g1 = new CategoryGroup();
        g1.setId(id1);
        g1.setUserSub(OTHER_SUB);

        when(categoryGroupRepository.findAllById(any())).thenReturn(List.of(g1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.reorderGroups(List.of(new SortItem(id1, 0)), USER_SUB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void renameGroup_throwsNotFound_whenGroupMissing() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.renameGroup(groupId, new CategoryGroupRequest("New Name"), USER_SUB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void renameGroup_throwsForbidden_whenGroupBelongsToOtherUser() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.renameGroup(groupId, new CategoryGroupRequest("New Name"), OTHER_SUB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void renameGroup_updatesNameAndReturnsDTO() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(categoryGroupRepository.save(any())).thenReturn(group);
        when(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group)).thenReturn(List.of(category));

        CategoryGroupDTO result = categoryService.renameGroup(groupId, new CategoryGroupRequest("New Name"), USER_SUB);

        assertEquals("New Name", group.getName());
        assertEquals(1, result.categories().size());
        verify(categoryGroupRepository).save(group);
    }

    @Test
    void deleteGroup_throwsNotFound_whenGroupMissing() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.deleteGroup(groupId, USER_SUB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteGroup_throwsForbidden_whenGroupBelongsToOtherUser() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.deleteGroup(groupId, OTHER_SUB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void deleteGroup_throwsConflict_whenTransactionsExist() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(budgetCategoryRepository.existsTransactionsByGroupId(groupId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.deleteGroup(groupId, USER_SUB));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(categoryGroupRepository, never()).deleteById(any());
    }

    @Test
    void deleteGroup_deletesAllocationsAndCategoriesAndGroup() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(budgetCategoryRepository.existsTransactionsByGroupId(groupId)).thenReturn(false);

        categoryService.deleteGroup(groupId, USER_SUB);

        verify(budgetAllocationRepository).deleteByGroupId(groupId);
        verify(budgetCategoryRepository).deleteByGroupId(groupId);
        verify(categoryGroupRepository).deleteById(groupId);
    }

    @Test
    void deleteCategory_throwsNotFound_whenCategoryMissing() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.deleteCategory(categoryId, USER_SUB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteCategory_throwsForbidden_whenCategoryBelongsToOtherUser() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.deleteCategory(categoryId, OTHER_SUB));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void deleteCategory_throwsConflict_whenTransactionsExist() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(budgetCategoryRepository.existsTransactionsByCategoryId(categoryId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.deleteCategory(categoryId, USER_SUB));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(budgetCategoryRepository, never()).deleteById(any());
    }

    @Test
    void deleteCategory_deletesSuccessfully() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(budgetCategoryRepository.existsTransactionsByCategoryId(categoryId)).thenReturn(false);

        categoryService.deleteCategory(categoryId, USER_SUB);

        verify(budgetCategoryRepository).deleteById(categoryId);
    }

    @Test
    void deleteCategory_throwsConflict_whenSystemManaged() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(accountRepository.existsByCcPaymentCategoryId(categoryId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.deleteCategory(categoryId, USER_SUB));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(budgetCategoryRepository, never()).deleteById(any());
    }

    @Test
    void renameCategory_throwsConflict_whenSystemManaged() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(accountRepository.existsByCcPaymentCategoryId(categoryId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.renameCategory(categoryId, new BudgetCategoryRequest("New Name"), USER_SUB));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(budgetCategoryRepository, never()).save(any());
    }

    @Test
    void deleteGroup_throwsConflict_whenGroupContainsSystemManagedCategory() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(budgetCategoryRepository.findByGroupOrderBySortOrderAsc(group)).thenReturn(List.of(category));
        when(accountRepository.existsByCcPaymentCategoryId(categoryId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.deleteGroup(groupId, USER_SUB));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(categoryGroupRepository, never()).deleteById(any());
    }
}
