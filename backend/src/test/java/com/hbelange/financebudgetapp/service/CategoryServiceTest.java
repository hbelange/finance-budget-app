package com.hbelange.financebudgetapp.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;
import com.hbelange.financebudgetapp.repository.BudgetAllocationRepository;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryGroupRepository categoryGroupRepository;
    @Mock private BudgetCategoryRepository budgetCategoryRepository;
    @Mock private BudgetAllocationRepository budgetAllocationRepository;

    @InjectMocks
    private CategoryService categoryService;

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

        category = new BudgetCategory();
        category.setId(categoryId);
        category.setGroupId(groupId);
        category.setName("Rent");
        category.setSortOrder(1);
    }

    @Test
    void findAllGroups_returnsMappedDTOs() {
        when(categoryGroupRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(group));

        List<CategoryGroupDTO> result = categoryService.findAllGroups();

        assertEquals(1, result.size());
        assertEquals(groupId, result.get(0).id());
        assertEquals("Housing", result.get(0).name());
    }

    @Test
    void createGroup_savesAndReturnsDTO() {
        when(categoryGroupRepository.save(any())).thenReturn(group);

        CategoryGroupDTO result = categoryService.createGroup(new CategoryGroupRequest("Housing"));

        assertEquals("Housing", result.name());
        verify(categoryGroupRepository).save(any());
    }

    @Test
    void addCategory_throwsNotFound_whenGroupMissing() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.addCategory(groupId, new BudgetCategoryRequest("Rent")));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void addCategory_savesAndReturnsUpdatedGroup() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(budgetCategoryRepository.save(any())).thenReturn(category);
        when(budgetCategoryRepository.findByGroupIdOrderBySortOrderAsc(groupId)).thenReturn(List.of(category));

        CategoryGroupDTO result = categoryService.addCategory(groupId, new BudgetCategoryRequest("Rent"));

        assertEquals(1, result.categories().size());
        assertEquals("Rent", result.categories().get(0).name());
    }

    @Test
    void renameCategory_throwsNotFound_whenCategoryMissing() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.renameCategory(categoryId, new BudgetCategoryRequest("New Name")));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void renameCategory_updatesName() {
        when(budgetCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(budgetCategoryRepository.save(any())).thenReturn(category);

        categoryService.renameCategory(categoryId, new BudgetCategoryRequest("New Name"));

        assertEquals("New Name", category.getName());
        verify(budgetCategoryRepository).save(category);
    }

    @Test
    void deleteCategory_throwsNotFound_whenCategoryMissing() {
        when(budgetCategoryRepository.existsById(categoryId)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.deleteCategory(categoryId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteCategory_throwsConflict_whenTransactionsExist() {
        when(budgetCategoryRepository.existsById(categoryId)).thenReturn(true);
        when(budgetCategoryRepository.existsTransactionsByCategoryId(categoryId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.deleteCategory(categoryId));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(budgetCategoryRepository, never()).deleteById(any());
    }

    @Test
    void renameGroup_throwsNotFound_whenGroupMissing() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.renameGroup(groupId, new CategoryGroupRequest("New Name")));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void renameGroup_updatesNameAndReturnsDTO() {
        when(categoryGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(categoryGroupRepository.save(any())).thenReturn(group);
        when(budgetCategoryRepository.findByGroupIdOrderBySortOrderAsc(groupId)).thenReturn(List.of(category));

        CategoryGroupDTO result = categoryService.renameGroup(groupId, new CategoryGroupRequest("New Name"));

        assertEquals("New Name", group.getName());
        assertEquals(1, result.categories().size());
        verify(categoryGroupRepository).save(group);
    }

    @Test
    void deleteGroup_throwsNotFound_whenGroupMissing() {
        when(categoryGroupRepository.existsById(groupId)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.deleteGroup(groupId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void deleteGroup_throwsConflict_whenTransactionsExist() {
        when(categoryGroupRepository.existsById(groupId)).thenReturn(true);
        when(budgetCategoryRepository.existsTransactionsByGroupId(groupId)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> categoryService.deleteGroup(groupId));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(categoryGroupRepository, never()).deleteById(any());
    }

    @Test
    void deleteGroup_deletesAllocationsAndCategoriesAndGroup() {
        when(categoryGroupRepository.existsById(groupId)).thenReturn(true);
        when(budgetCategoryRepository.existsTransactionsByGroupId(groupId)).thenReturn(false);

        categoryService.deleteGroup(groupId);

        verify(budgetAllocationRepository).deleteByGroupId(groupId);
        verify(budgetCategoryRepository).deleteByGroupId(groupId);
        verify(categoryGroupRepository).deleteById(groupId);
    }

    @Test
    void deleteCategory_deletesSuccessfully() {
        when(budgetCategoryRepository.existsById(categoryId)).thenReturn(true);
        when(budgetCategoryRepository.existsTransactionsByCategoryId(categoryId)).thenReturn(false);

        categoryService.deleteCategory(categoryId);

        verify(budgetCategoryRepository).deleteById(categoryId);
    }
}
