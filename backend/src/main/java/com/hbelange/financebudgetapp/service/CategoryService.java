package com.hbelange.financebudgetapp.service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.hbelange.financebudgetapp.dto.BudgetCategoryDTO;
import com.hbelange.financebudgetapp.dto.BudgetCategoryRequest;
import com.hbelange.financebudgetapp.dto.CategoryGroupDTO;
import com.hbelange.financebudgetapp.dto.CategoryGroupRequest;
import com.hbelange.financebudgetapp.entity.BudgetCategory;
import com.hbelange.financebudgetapp.entity.CategoryGroup;
import com.hbelange.financebudgetapp.repository.BudgetCategoryRepository;
import com.hbelange.financebudgetapp.repository.CategoryGroupRepository;

@Service
public class CategoryService {

    private final CategoryGroupRepository categoryGroupRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;

    @Autowired
    public CategoryService(CategoryGroupRepository categoryGroupRepository,
                                BudgetCategoryRepository budgetCategoryRepository) {
        this.categoryGroupRepository = categoryGroupRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
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
        CategoryGroup group = new CategoryGroup();
        group.setName(req.name());
        group.setSortOrder(0);
        group = categoryGroupRepository.save(group);
        return new CategoryGroupDTO(group.getId(), group.getName(), List.of());
    }

    public CategoryGroupDTO addCategory(UUID groupId, BudgetCategoryRequest req) {
        CategoryGroup group = categoryGroupRepository.findById(groupId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category group not found"));

        BudgetCategory category = new BudgetCategory();
        category.setGroupId(groupId);
        category.setName(req.name());
        category.setSortOrder(0);
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

    public void deleteCategory(UUID id) {
        if (!budgetCategoryRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
        }
        if (budgetCategoryRepository.existsTransactionsByCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category has existing transactions");
        }
        budgetCategoryRepository.deleteById(id);
    }
}
