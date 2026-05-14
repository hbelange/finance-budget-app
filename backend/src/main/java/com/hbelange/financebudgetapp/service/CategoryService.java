package com.hbelange.financebudgetapp.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.BudgetCategoryDTO;
import com.hbelange.financebudgetapp.dto.BudgetCategoryRequest;
import com.hbelange.financebudgetapp.dto.CategoryGroupDTO;
import com.hbelange.financebudgetapp.dto.CategoryGroupRequest;
import com.hbelange.financebudgetapp.dto.SortItem;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;
import com.hbelange.financebudgetapp.repository.BudgetAllocationRepository;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;

@Service
public class CategoryService {

    private final CategoryGroupRepository categoryGroupRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final BudgetAllocationRepository budgetAllocationRepository;

    @Autowired
    public CategoryService(CategoryGroupRepository categoryGroupRepository,
                           BudgetCategoryRepository budgetCategoryRepository,
                           BudgetAllocationRepository budgetAllocationRepository) {
        this.categoryGroupRepository = categoryGroupRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.budgetAllocationRepository = budgetAllocationRepository;
    }

    public List<CategoryGroupDTO> findAllGroups() {
        return categoryGroupRepository.findAllByOrderBySortOrderAsc().stream()
            .map(g -> {
                List<BudgetCategoryDTO> categories = budgetCategoryRepository
                    .findByGroupIdOrderBySortOrderAsc(g.getId()).stream()
                    .map(c -> new BudgetCategoryDTO(c.getId(), c.getName()))
                    .toList();
                return new CategoryGroupDTO(g.getId(), g.getName(), categories);
            })
            .toList();
    }

    public CategoryGroupDTO createGroup(CategoryGroupRequest req) {
        int nextOrder = categoryGroupRepository.findTopByOrderBySortOrderDesc()
            .map(g -> g.getSortOrder() + 1)
            .orElse(0);
        CategoryGroup group = new CategoryGroup();
        group.setName(req.name());
        group.setSortOrder(nextOrder);
        group = categoryGroupRepository.save(group);
        return new CategoryGroupDTO(group.getId(), group.getName(), List.of());
    }

    public CategoryGroupDTO addCategory(UUID groupId, BudgetCategoryRequest req) {
        CategoryGroup group = categoryGroupRepository.findById(groupId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category group not found"));

        int nextOrder = budgetCategoryRepository.findTopByGroupIdOrderBySortOrderDesc(groupId)
            .map(c -> c.getSortOrder() + 1)
            .orElse(0);
        BudgetCategory category = new BudgetCategory();
        category.setGroupId(groupId);
        category.setName(req.name());
        category.setSortOrder(nextOrder);
        budgetCategoryRepository.save(category);

        List<BudgetCategoryDTO> categories = budgetCategoryRepository
            .findByGroupIdOrderBySortOrderAsc(groupId).stream()
            .map(c -> new BudgetCategoryDTO(c.getId(), c.getName()))
            .toList();

        return new CategoryGroupDTO(group.getId(), group.getName(), categories);
    }

    public void renameCategory(UUID id, BudgetCategoryRequest req) {
        BudgetCategory category = budgetCategoryRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        category.setName(req.name());
        budgetCategoryRepository.save(category);
    }

    public void reorderGroups(List<SortItem> items) {
        Map<UUID, Integer> orderMap = items.stream()
            .collect(Collectors.toMap(SortItem::id, SortItem::sortOrder));
        List<CategoryGroup> groups = categoryGroupRepository.findAllById(orderMap.keySet());
        groups.forEach(g -> g.setSortOrder(orderMap.get(g.getId())));
        categoryGroupRepository.saveAll(groups);
    }

    public void reorderCategories(List<SortItem> items) {
        Map<UUID, Integer> orderMap = items.stream()
            .collect(Collectors.toMap(SortItem::id, SortItem::sortOrder));
        List<BudgetCategory> categories = budgetCategoryRepository.findAllById(orderMap.keySet());
        categories.forEach(c -> c.setSortOrder(orderMap.get(c.getId())));
        budgetCategoryRepository.saveAll(categories);
    }

    public CategoryGroupDTO renameGroup(UUID id, CategoryGroupRequest req) {
        CategoryGroup group = categoryGroupRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category group not found"));
        group.setName(req.name());
        group = categoryGroupRepository.save(group);
        List<BudgetCategoryDTO> categories = budgetCategoryRepository
            .findByGroupIdOrderBySortOrderAsc(id).stream()
            .map(c -> new BudgetCategoryDTO(c.getId(), c.getName()))
            .toList();
        return new CategoryGroupDTO(group.getId(), group.getName(), categories);
    }

    @Transactional
    public void deleteGroup(UUID id) {
        if (!categoryGroupRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category group not found");
        }
        if (budgetCategoryRepository.existsTransactionsByGroupId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Group contains categories with existing transactions");
        }
        budgetAllocationRepository.deleteByGroupId(id);
        budgetCategoryRepository.deleteByGroupId(id);
        categoryGroupRepository.deleteById(id);
    }

    @Transactional
    public void deleteCategory(UUID id) {
        if (!budgetCategoryRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
        }
        if (budgetCategoryRepository.existsTransactionsByCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category has existing transactions");
        }
        budgetAllocationRepository.deleteByCategoryId(id);
        budgetCategoryRepository.deleteById(id);
    }
}
